import { withSupabase } from "npm:@supabase/server@1.7.1";
import { sendFirebaseMessage } from "../_shared/firebase.ts";

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
    const temporaryAllowUntilMillis =
      typeof payload.temporaryAllowUntilMillis === "number"
        ? payload.temporaryAllowUntilMillis
        : null;
    const accessibilityEnabled =
      typeof payload.accessibilityEnabled === "boolean"
        ? payload.accessibilityEnabled
        : null;
    const preciseTimingEnabled =
      typeof payload.preciseTimingEnabled === "boolean"
        ? payload.preciseTimingEnabled
        : null;
    const batteryUnrestricted =
      typeof payload.batteryUnrestricted === "boolean"
        ? payload.batteryUnrestricted
        : null;
    const protectionEvent =
      typeof payload.protectionEvent === "string"
        ? payload.protectionEvent.trim()
        : "";
    const dailyUsageDate =
      typeof payload.dailyUsageDate === "string"
        ? payload.dailyUsageDate.trim()
        : null;
    const dailyUsageSeconds =
      typeof payload.dailyUsageSeconds === "number"
        ? Math.trunc(payload.dailyUsageSeconds)
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
      (
        temporaryAllowUntilMillis === null ||
        !Number.isFinite(temporaryAllowUntilMillis) ||
        temporaryAllowUntilMillis <= Date.now()
      )
    ) {
      return json({ error: "temporary_allow_until_invalid" }, 400);
    }
    if (
      protectionEvent &&
      ![
        "APP_INFO_OPENED",
        "UNINSTALL_SCREEN_OPENED",
        "FORCE_STOP_ATTEMPT",
        "CLEAR_DATA_ATTEMPT",
      ].includes(protectionEvent)
    ) {
      return json({ error: "invalid_protection_event" }, 400);
    }

    const hasUsagePayload =
      dailyUsageDate !== null || dailyUsageSeconds !== null;
    if (
      hasUsagePayload &&
      (
        dailyUsageDate === null ||
        !/^\d{4}-\d{2}-\d{2}$/.test(dailyUsageDate) ||
        dailyUsageSeconds === null ||
        dailyUsageSeconds < 0 ||
        dailyUsageSeconds > 172800
      )
    ) {
      return json({ error: "daily_usage_invalid" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);
    const now = new Date().toISOString();

    const { data: previousDevice, error: previousDeviceError } =
      await ctx.supabaseAdmin
        .from("child_devices")
        .select(
          "device_id, display_name, accessibility_enabled, precise_timing_enabled, battery_unrestricted, parent_fcm_token",
        )
        .eq("device_id", deviceId)
        .eq("device_secret_hash", deviceSecretHash)
        .maybeSingle();

    if (previousDeviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!previousDevice) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const accessibilityJustDisabled =
      previousDevice.accessibility_enabled === true &&
      accessibilityEnabled === false;
    const preciseTimingJustDisabled =
      previousDevice.precise_timing_enabled === true &&
      preciseTimingEnabled === false;
    const batteryUnrestrictedJustDisabled =
      previousDevice.battery_unrestricted === true &&
      batteryUnrestricted === false;
    const accessibilityJustRestored =
      previousDevice.accessibility_enabled === false &&
      accessibilityEnabled === true;
    const preciseTimingJustRestored =
      previousDevice.precise_timing_enabled === false &&
      preciseTimingEnabled === true;
    const batteryUnrestrictedJustRestored =
      previousDevice.battery_unrestricted === false &&
      batteryUnrestricted === true;

    const { data: device, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        access_state: accessState,
        temporary_allow_until:
          accessState === "TEMPORARILY_ALLOWED"
            ? new Date(temporaryAllowUntilMillis!).toISOString()
            : null,
        accessibility_enabled: accessibilityEnabled,
        precise_timing_enabled: preciseTimingEnabled,
        battery_unrestricted: batteryUnrestricted,
        protection_updated_at: now,
        last_seen_at: now,
        updated_at: now,
        ...(hasUsagePayload
          ? {
              daily_usage_date: dailyUsageDate,
              daily_usage_seconds: dailyUsageSeconds,
            }
          : {}),
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

    const protectionHistoryEvents = [
      accessibilityJustDisabled ? "ACCESSIBILITY_DISABLED" : "",
      accessibilityJustRestored ? "ACCESSIBILITY_RESTORED" : "",
      preciseTimingJustDisabled ? "PRECISE_TIMING_DISABLED" : "",
      preciseTimingJustRestored ? "PRECISE_TIMING_RESTORED" : "",
      batteryUnrestrictedJustDisabled ? "BATTERY_UNRESTRICTED_DISABLED" : "",
      batteryUnrestrictedJustRestored ? "BATTERY_UNRESTRICTED_RESTORED" : "",
      protectionEvent,
    ].filter((eventType) => eventType.length > 0);

    const uniqueHistoryEvents = [...new Set(protectionHistoryEvents)];
    if (uniqueHistoryEvents.length > 0) {
      const { error: historyError } = await ctx.supabaseAdmin
        .from("protection_events")
        .insert(
          uniqueHistoryEvents.map((eventType) => ({
            device_id: deviceId,
            event_type: eventType,
            created_at: now,
          })),
        );

      if (historyError) {
        console.error(
          "Protection history insert failed for " + deviceId,
          historyError,
        );
      }
    }

    const parentToken =
      typeof previousDevice.parent_fcm_token === "string"
        ? previousDevice.parent_fcm_token.trim()
        : "";
    const displayName =
      typeof previousDevice.display_name === "string" &&
          previousDevice.display_name.trim()
        ? previousDevice.display_name.trim()
        : "Child device";

    const protectionAlerts: string[] = [];
    const alertTransitions = [
      {
        shouldSend: accessibilityJustDisabled,
        alert: "ACCESSIBILITY_DISABLED",
      },
      {
        shouldSend: preciseTimingJustDisabled,
        alert: "PRECISE_TIMING_DISABLED",
      },
      {
        shouldSend: batteryUnrestrictedJustDisabled,
        alert: "BATTERY_UNRESTRICTED_DISABLED",
      },
      {
        shouldSend: protectionEvent === "APP_INFO_OPENED",
        alert: "APP_INFO_OPENED",
      },
      {
        shouldSend: protectionEvent === "UNINSTALL_SCREEN_OPENED",
        alert: "UNINSTALL_SCREEN_OPENED",
      },
      {
        shouldSend: protectionEvent === "FORCE_STOP_ATTEMPT",
        alert: "FORCE_STOP_ATTEMPT",
      },
      {
        shouldSend: protectionEvent === "CLEAR_DATA_ATTEMPT",
        alert: "CLEAR_DATA_ATTEMPT",
      },
    ];

    if (parentToken) {
      for (const transition of alertTransitions) {
        if (!transition.shouldSend) continue;

        try {
          await sendFirebaseMessage({
            token: parentToken,
            data: {
              type: "PROTECTION_ALERT",
              alert: transition.alert,
              device_id: deviceId,
              display_name: displayName,
            },
            collapseKey:
              "phoneguard-protection-" +
              transition.alert.toLowerCase() +
              "-" +
              deviceId,
            ttl: "3600s",
          });
          protectionAlerts.push(transition.alert);
        } catch (error) {
          console.error(
            "Parent protection alert push failed for " +
              transition.alert,
            error,
          );
        }
      }
    }

    return json({
      ok: true,
      lastSeenAt: device.last_seen_at,
      protectionAlertSent: protectionAlerts.length > 0,
      protectionAlerts,
    });
  }),
};
