import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const RECENT_CHECKIN_THRESHOLD_MS = 30 * 60_000;

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
    const controlToken =
      typeof payload.controlToken === "string" ? payload.controlToken : "";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .select(
        "device_id, display_name, access_state, temporary_allow_until, last_seen_at, accessibility_enabled, precise_timing_enabled, battery_unrestricted, protection_updated_at, daily_limit_minutes, daily_usage_date, daily_usage_seconds",
      )
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (error) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
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

    const dailyLimitMinutes =
      typeof device.daily_limit_minutes === "number"
        ? device.daily_limit_minutes
        : null;
    const dailyUsageSeconds =
      typeof device.daily_usage_seconds === "number"
        ? Math.max(0, device.daily_usage_seconds)
        : 0;
    const dailyRemainingMinutes =
      dailyLimitMinutes === null
        ? null
        : Math.max(
            0,
            Math.ceil((dailyLimitMinutes * 60 - dailyUsageSeconds) / 60),
          );

    const lastSeenMillis =
      typeof device.last_seen_at === "string"
        ? new Date(device.last_seen_at).getTime()
        : Number.NaN;
    const isOnline =
      Number.isFinite(lastSeenMillis) &&
      Date.now() - lastSeenMillis <= RECENT_CHECKIN_THRESHOLD_MS;

    return json({
      ok: true,
      device: {
        deviceId: device.device_id,
        displayName: device.display_name,
        state: device.access_state,
        temporaryAccessMinutesRemaining,
        isOnline,
        lastSeenAt: device.last_seen_at,
        dailyScreenTime: {
          limitMinutes: dailyLimitMinutes,
          usedSeconds: dailyUsageSeconds,
          remainingMinutes: dailyRemainingMinutes,
          usageDate: device.daily_usage_date,
          limitReached:
            dailyLimitMinutes !== null &&
            dailyUsageSeconds >= dailyLimitMinutes * 60,
        },
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
          updatedAt: device.protection_updated_at,
        },
      },
    });
  }),
};
