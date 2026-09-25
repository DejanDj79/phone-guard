import { withSupabase } from "npm:@supabase/server@1.7.1";
import { sendFirebaseMessage } from "../_shared/firebase.ts";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PROTECTION_HISTORY_RETAINED_PER_DEVICE = 100;
const PROTECTION_ALERT_COOLDOWN_MS = 60_000;

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
    const appUsageDate =
      typeof payload.appUsageDate === "string"
        ? payload.appUsageDate.trim()
        : null;
    const appUsageTotalSeconds =
      typeof payload.appUsageTotalSeconds === "number"
        ? Math.trunc(payload.appUsageTotalSeconds)
        : null;
    const rawAppUsage =
      Array.isArray(payload.appUsage) ? payload.appUsage : null;

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

    const hasAppUsagePayload =
      appUsageDate !== null ||
      appUsageTotalSeconds !== null ||
      rawAppUsage !== null;
    const normalizedAppUsage: Array<{
      packageName: string;
      label: string;
      seconds: number;
    }> = [];

    if (hasAppUsagePayload) {
      if (
        appUsageDate === null ||
        !/^\d{4}-\d{2}-\d{2}$/.test(appUsageDate) ||
        appUsageTotalSeconds === null ||
        appUsageTotalSeconds < 0 ||
        appUsageTotalSeconds > 172800 ||
        rawAppUsage === null ||
        rawAppUsage.length > 30
      ) {
        return json({ error: "app_usage_invalid" }, 400);
      }

      for (const rawEntry of rawAppUsage) {
        if (
          typeof rawEntry !== "object" ||
          rawEntry === null ||
          Array.isArray(rawEntry)
        ) {
          return json({ error: "app_usage_invalid" }, 400);
        }

        const entry = rawEntry as Record<string, unknown>;
        const packageName =
          typeof entry.packageName === "string"
            ? entry.packageName.trim()
            : "";
        const label =
          typeof entry.label === "string" ? entry.label.trim() : "";
        const seconds =
          typeof entry.seconds === "number"
            ? Math.trunc(entry.seconds)
            : -1;

        if (
          !packageName ||
          packageName.length > 255 ||
          !label ||
          label.length > 120 ||
          seconds < 0 ||
          seconds > 172800
        ) {
          return json({ error: "app_usage_invalid" }, 400);
        }

        normalizedAppUsage.push({
          packageName,
          label,
          seconds,
        });
      }
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

    if (hasAppUsagePayload) {
      const { error: appUsageError } = await ctx.supabaseAdmin
        .from("app_usage_daily")
        .upsert(
          {
            device_id: deviceId,
            usage_date: appUsageDate,
            total_seconds: appUsageTotalSeconds,
            apps: normalizedAppUsage,
            updated_at: now,
          },
          { onConflict: "device_id,usage_date" },
        );

      if (appUsageError) {
        console.error(
          "App usage snapshot upsert failed for " + deviceId,
          appUsageError,
        );
      } else {
        const retentionCutoff = new Date(appUsageDate + "T00:00:00Z");
        retentionCutoff.setUTCDate(retentionCutoff.getUTCDate() - 6);
        const cutoffDate = retentionCutoff.toISOString().slice(0, 10);

        const { error: appUsageRetentionError } =
          await ctx.supabaseAdmin
            .from("app_usage_daily")
            .delete()
            .eq("device_id", deviceId)
            .lt("usage_date", cutoffDate);

        if (appUsageRetentionError) {
          console.error(
            "App usage retention cleanup failed for " + deviceId,
            appUsageRetentionError,
          );
        }
      }
    }

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

    const requestedAlertTypes =
      alertTransitions
        .filter((transition) => transition.shouldSend)
        .map((transition) => transition.alert);

    const recentAlertTypes = new Set<string>();
    if (requestedAlertTypes.length > 0) {
      const cooldownCutoff =
        new Date(Date.now() - PROTECTION_ALERT_COOLDOWN_MS).toISOString();
      const { data: recentAlerts, error: recentAlertsError } =
        await ctx.supabaseAdmin
          .from("protection_alert_state")
          .select("alert_type, last_sent_at")
          .eq("device_id", deviceId)
          .in("alert_type", requestedAlertTypes)
          .gte("last_sent_at", cooldownCutoff);

      if (recentAlertsError) {
        console.error(
          "Protection alert cooldown query failed for " + deviceId,
          recentAlertsError,
        );
      } else {
        for (const alertState of recentAlerts ?? []) {
          if (typeof alertState.alert_type === "string") {
            recentAlertTypes.add(alertState.alert_type);
          }
        }
      }
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
      } else {
        const { data: excessEvents, error: excessEventsError } =
          await ctx.supabaseAdmin
            .from("protection_events")
            .select("id")
            .eq("device_id", deviceId)
            .order("created_at", { ascending: false })
            .range(PROTECTION_HISTORY_RETAINED_PER_DEVICE, 1099);

        if (excessEventsError) {
          console.error(
            "Protection history retention query failed for " + deviceId,
            excessEventsError,
          );
        } else if ((excessEvents ?? []).length > 0) {
          const { error: pruneError } = await ctx.supabaseAdmin
            .from("protection_events")
            .delete()
            .in(
              "id",
              (excessEvents ?? []).map((event) => event.id),
            );

          if (pruneError) {
            console.error(
              "Protection history retention delete failed for " + deviceId,
              pruneError,
            );
          }
        }
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
    const protectionAlertsSuppressed: string[] = [];

    if (parentToken) {
      for (const transition of alertTransitions) {
        if (!transition.shouldSend) continue;

        if (recentAlertTypes.has(transition.alert)) {
          protectionAlertsSuppressed.push(transition.alert);
          continue;
        }

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

          const { error: alertStateError } = await ctx.supabaseAdmin
            .from("protection_alert_state")
            .upsert(
              {
                device_id: deviceId,
                alert_type: transition.alert,
                last_sent_at: now,
              },
              { onConflict: "device_id,alert_type" },
            );

          if (alertStateError) {
            console.error(
              "Protection alert delivery state update failed for " +
                transition.alert,
              alertStateError,
            );
          }
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
      protectionAlertsSuppressed,
    });
  }),
};
