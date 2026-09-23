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
    const commandId =
      typeof payload.commandId === "string" ? payload.commandId.trim() : "";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (!UUID_PATTERN.test(commandId)) {
      return json({ error: "invalid_command_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, access_state, temporary_allow_until")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const { data: commandRow, error: commandError } = await ctx.supabaseAdmin
      .from("device_commands")
      .select("command_id, command, bonus_minutes, status")
      .eq("command_id", commandId)
      .eq("device_id", deviceId)
      .maybeSingle();

    if (commandError) {
      return json({ error: "database_error" }, 500);
    }
    if (!commandRow) {
      return json({ error: "command_not_found" }, 404);
    }

    if (commandRow.status === "APPLIED") {
      return json({ ok: true, status: "APPLIED" });
    }
    if (commandRow.status === "FAILED") {
      return json({ error: "command_already_failed" }, 409);
    }

    const now = new Date();
    let accessState =
      typeof device.access_state === "string" ? device.access_state : "ALLOWED";
    let temporaryAllowUntil =
      typeof device.temporary_allow_until === "string"
        ? device.temporary_allow_until
        : null;

    if (commandRow.command === "LOCK") {
      accessState = "LOCKED";
      temporaryAllowUntil = null;
    } else if (commandRow.command === "UNLOCK") {
      accessState = "ALLOWED";
      temporaryAllowUntil = null;
    } else if (commandRow.command === "BONUS_TIME") {
      accessState = "TEMPORARILY_ALLOWED";

      const existingUntil =
        typeof device.temporary_allow_until === "string"
          ? new Date(device.temporary_allow_until).getTime()
          : 0;
      const base = Math.max(now.getTime(), existingUntil);
      const minutes =
        typeof commandRow.bonus_minutes === "number"
          ? commandRow.bonus_minutes
          : 0;

      temporaryAllowUntil =
        new Date(base + minutes * 60_000).toISOString();
    }

    const { error: deviceUpdateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        access_state: accessState,
        temporary_allow_until: temporaryAllowUntil,
        last_seen_at: now.toISOString(),
        updated_at: now.toISOString(),
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash);

    if (deviceUpdateError) {
      return json({ error: "device_status_update_failed" }, 500);
    }

    const { error: commandUpdateError } = await ctx.supabaseAdmin
      .from("device_commands")
      .update({
        status: "APPLIED",
        applied_at: now.toISOString(),
        error_code: null,
      })
      .eq("command_id", commandId)
      .eq("device_id", deviceId);

    if (commandUpdateError) {
      return json({ error: "command_status_update_failed" }, 500);
    }

    return json({
      ok: true,
      status: "APPLIED",
      device: {
        deviceId,
        state: accessState,
        temporaryAllowUntil,
      },
    });
  }),
};
