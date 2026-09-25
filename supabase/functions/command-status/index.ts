import { withSupabase } from "npm:@supabase/server@1.7.1";
import { requireParentUserId } from "../_shared/parent-auth.ts";

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

    const parentUserId = await requireParentUserId(req, ctx);
    if (!parentUserId) return json({ error: "parent_auth_required" }, 401);

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
    const commandId =
      typeof payload.commandId === "string" ? payload.commandId.trim() : "";

    if (!UUID_PATTERN.test(deviceId) || !UUID_PATTERN.test(commandId)) {
      return json({ error: "invalid_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name, access_state, temporary_allow_until, accessibility_enabled, precise_timing_enabled, battery_unrestricted")
      .eq("device_id", deviceId)
      .eq("parent_user_id", parentUserId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const { data: command, error: commandError } = await ctx.supabaseAdmin
      .from("device_commands")
      .select("command_id, status, error_code, sent_at, applied_at")
      .eq("command_id", commandId)
      .eq("device_id", deviceId)
      .maybeSingle();

    if (commandError) {
      return json({ error: "database_error" }, 500);
    }
    if (!command) {
      return json({ error: "command_not_found" }, 404);
    }

    const temporaryAccessMinutesRemaining =
      device.access_state === "TEMPORARILY_ALLOWED" &&
        typeof device.temporary_allow_until === "string"
        ? Math.max(
            0,
            Math.ceil(
              (new Date(device.temporary_allow_until).getTime() - Date.now()) /
                60_000,
            ),
          )
        : null;

    return json({
      ok: true,
      commandId: command.command_id,
      status: command.status,
      errorCode: command.error_code,
      sentAt: command.sent_at,
      appliedAt: command.applied_at,
      device: {
        deviceId: device.device_id,
        displayName: device.display_name,
        state: device.access_state,
        temporaryAccessMinutesRemaining,
        protection: {
          accessibilityEnabled:
            typeof device.accessibility_enabled === "boolean"
              ? device.accessibility_enabled
              : null,
          preciseTimingEnabled:
            typeof device.precise_timing_enabled === "boolean"
              ? device.precise_timing_enabled
              : null,
          batteryUnrestricted:
            typeof device.battery_unrestricted === "boolean"
              ? device.battery_unrestricted
              : null,
        },
      },
    });
  }),
};
