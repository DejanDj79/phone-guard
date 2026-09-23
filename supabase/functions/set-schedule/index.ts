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
    const controlToken =
      typeof payload.controlToken === "string" ? payload.controlToken : "";
    const scheduleRaw =
      typeof payload.scheduleConfig === "string" ? payload.scheduleConfig : "";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const scheduleConfig = normalizeSchedule(scheduleRaw);
    if (!scheduleConfig) {
      return json({ error: "invalid_schedule" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, schedule_version")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const currentVersion =
      typeof device.schedule_version === "number"
        ? device.schedule_version
        : 0;
    const nextVersion = currentVersion + 1;
    const now = new Date().toISOString();

    const { data: updated, error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        schedule_config: scheduleConfig,
        schedule_version: nextVersion,
        schedule_updated_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .eq("schedule_version", currentVersion)
      .select("device_id")
      .maybeSingle();

    if (updateError) {
      return json({ error: "schedule_update_failed" }, 500);
    }
    if (!updated) {
      return json({ error: "schedule_version_conflict" }, 409);
    }

    let commandId: string | null = null;
    let deliveryStatus = "SENT";
    let pushAccepted = false;

    try {
      const baseUrl = Deno.env.get("SUPABASE_URL");
      if (!baseUrl) throw new Error("supabase_url_missing");

      const sendResponse = await fetch(
        baseUrl + "/functions/v1/send-command",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            deviceId,
            controlToken,
            command: "SYNC_SCHEDULE",
          }),
        },
      );

      const sendBody = await sendResponse.json().catch(() => ({}));

      if (sendResponse.ok) {
        commandId =
          typeof sendBody.commandId === "string" ? sendBody.commandId : null;
        deliveryStatus =
          typeof sendBody.deliveryStatus === "string"
            ? sendBody.deliveryStatus
            : "SENT";
        pushAccepted = true;
      }
    } catch {
      // A queued fallback below guarantees reconnect delivery.
    }

    if (!pushAccepted) {
      const { data: queued, error: queueError } = await ctx.supabaseAdmin
        .from("device_commands")
        .insert({
          device_id: deviceId,
          command: "SYNC_SCHEDULE",
          bonus_minutes: null,
          status: "SENT",
          sent_at: now,
          error_code: "push_not_confirmed",
        })
        .select("command_id")
        .single();

      if (queueError || !queued) {
        return json(
          {
            error: "schedule_saved_but_sync_queue_failed",
            scheduleVersion: nextVersion,
          },
          500,
        );
      }

      commandId = queued.command_id as string;
      deliveryStatus = "SENT";
    }

    return json({
      ok: true,
      scheduleVersion: nextVersion,
      commandId,
      deliveryStatus,
      pushAccepted,
    });
  }),
};
