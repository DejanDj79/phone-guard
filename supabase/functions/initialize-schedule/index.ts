import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

function normalizeSchedule(value: string): string | null {
  const parts = value.split(";");
  if (parts.length !== DAYS.length) return null;

  const parsed = new Map<string, [string, number, number]>();

  for (const item of parts) {
    const fields = item.split(",");
    if (fields.length !== 4) return null;

    const [day, enabledRaw, startRaw, endRaw] = fields;
    if (!DAYS.includes(day as (typeof DAYS)[number])) return null;
    if (parsed.has(day)) return null;
    if (enabledRaw !== "0" && enabledRaw !== "1") return null;

    const start = Number(startRaw);
    const end = Number(endRaw);
    if (!Number.isInteger(start) || start < 0 || start > 1439) return null;
    if (!Number.isInteger(end) || end < 0 || end > 1439) return null;

    parsed.set(day, [enabledRaw, start, end]);
  }

  if (parsed.size !== DAYS.length) return null;

  return DAYS.map((day) => {
    const [enabled, start, end] = parsed.get(day)!;
    return [day, enabled, start, end].join(",");
  }).join(";");
}

export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    let payload: Record<string, unknown>;
    try {
      payload = await req.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const deviceId =
      typeof payload.deviceId === "string" ? payload.deviceId.trim() : "";
    const deviceSecret =
      typeof payload.deviceSecret === "string" ? payload.deviceSecret : "";
    const scheduleRaw =
      typeof payload.scheduleConfig === "string" ? payload.scheduleConfig : "";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }

    const scheduleConfig = normalizeSchedule(scheduleRaw);
    if (!scheduleConfig) {
      return json({ error: "invalid_schedule" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("schedule_config, schedule_version")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    if (
      typeof device.schedule_config === "string" ||
      (typeof device.schedule_version === "number" && device.schedule_version > 0)
    ) {
      return json({
        ok: true,
        initialized: false,
        version:
          typeof device.schedule_version === "number"
            ? device.schedule_version
            : 0,
      });
    }

    const now = new Date().toISOString();

    const { data: updated, error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        schedule_config: scheduleConfig,
        schedule_version: 1,
        schedule_updated_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .eq("schedule_version", 0)
      .is("schedule_config", null)
      .select("device_id")
      .maybeSingle();

    if (updateError) {
      return json({ error: "schedule_initialize_failed" }, 500);
    }

    if (!updated) {
      const { data: current, error: currentError } = await ctx.supabaseAdmin
        .from("child_devices")
        .select("schedule_version")
        .eq("device_id", deviceId)
        .eq("device_secret_hash", deviceSecretHash)
        .maybeSingle();

      if (currentError || !current) {
        return json({ error: "database_error" }, 500);
      }

      return json({
        ok: true,
        initialized: false,
        version:
          typeof current.schedule_version === "number"
            ? current.schedule_version
            : 0,
      });
    }

    return json({
      ok: true,
      initialized: true,
      version: 1,
    });
  }),
};
