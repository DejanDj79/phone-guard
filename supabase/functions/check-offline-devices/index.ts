import { withSupabase } from "npm:@supabase/server@1.7.1";
import { sendFirebaseMessage } from "../_shared/firebase.ts";

const OFFLINE_AFTER_MS = 5 * 60_000;
const MAX_DEVICES_PER_RUN = 100;

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    const cutoff = new Date(Date.now() - OFFLINE_AFTER_MS).toISOString();

    const { data: candidates, error: candidatesError } =
      await ctx.supabaseAdmin
        .from("child_devices")
        .select(
          "device_id, display_name, parent_fcm_token, last_seen_at",
        )
        .lt("last_seen_at", cutoff)
        .is("offline_alert_sent_at", null)
        .not("parent_fcm_token", "is", null)
        .limit(MAX_DEVICES_PER_RUN);

    if (candidatesError) {
      console.error("Offline candidate query failed", candidatesError);
      return json({ error: "database_error" }, 500);
    }

    let alertsSent = 0;
    let failed = 0;

    for (const candidate of candidates ?? []) {
      const token =
        typeof candidate.parent_fcm_token === "string"
          ? candidate.parent_fcm_token.trim()
          : "";
      if (!token) continue;

      const claimedAt = new Date().toISOString();
      const { data: claimed, error: claimError } =
        await ctx.supabaseAdmin
          .from("child_devices")
          .update({ offline_alert_sent_at: claimedAt })
          .eq("device_id", candidate.device_id)
          .lt("last_seen_at", cutoff)
          .is("offline_alert_sent_at", null)
          .select("device_id")
          .maybeSingle();

      if (claimError) {
        console.error(
          "Offline alert claim failed for " + candidate.device_id,
          claimError,
        );
        failed += 1;
        continue;
      }
      if (!claimed) continue;

      try {
        await sendFirebaseMessage({
          token,
          data: {
            type: "PROTECTION_ALERT",
            alert: "CHILD_OFFLINE",
            device_id: candidate.device_id,
            display_name:
              typeof candidate.display_name === "string" &&
                  candidate.display_name.trim()
                ? candidate.display_name.trim()
                : "Child device",
          },
          collapseKey: "phoneguard-offline-" + candidate.device_id,
          ttl: "3600s",
        });
        alertsSent += 1;
      } catch (error) {
        console.error(
          "Parent offline alert push failed for " + candidate.device_id,
          error,
        );
        failed += 1;

        await ctx.supabaseAdmin
          .from("child_devices")
          .update({ offline_alert_sent_at: null })
          .eq("device_id", candidate.device_id)
          .eq("offline_alert_sent_at", claimedAt);
      }
    }

    return json({
      ok: true,
      checked: candidates?.length ?? 0,
      alertsSent,
      failed,
      cutoff,
    });
  }),
};
