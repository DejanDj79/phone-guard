import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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
    const accessState =
      typeof payload.accessState === "string" ? payload.accessState : "";
    const temporaryAllowUntil =
      typeof payload.temporaryAllowUntil === "string"
        ? payload.temporaryAllowUntil
        : null;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (!["ALLOWED", "LOCKED", "TEMPORARILY_ALLOWED"].includes(accessState)) {
      return json({ error: "invalid_access_state" }, 400);
    }
    if (
      accessState === "TEMPORARILY_ALLOWED" &&
      temporaryAllowUntil === null
    ) {
      return json({ error: "temporary_allow_until_required" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);
    const now = new Date().toISOString();

    const { data: device, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        access_state: accessState,
        temporary_allow_until:
          accessState === "TEMPORARILY_ALLOWED" ? temporaryAllowUntil : null,
        last_seen_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .select("device_id, last_seen_at")
      .maybeSingle();

    if (error) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    return json({
      ok: true,
      lastSeenAt: device.last_seen_at,
    });
  }),
};
