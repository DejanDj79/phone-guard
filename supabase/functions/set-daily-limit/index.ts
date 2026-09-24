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
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

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
    const rawMinutes = payload.dailyLimitMinutes;
    const dailyLimitMinutes =
      rawMinutes === null || rawMinutes === undefined
        ? null
        : typeof rawMinutes === "number"
          ? Math.trunc(rawMinutes)
          : Number.NaN;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }
    if (
      dailyLimitMinutes !== null &&
      (!Number.isInteger(dailyLimitMinutes) ||
        dailyLimitMinutes < 1 ||
        dailyLimitMinutes > 1440)
    ) {
      return json({ error: "invalid_daily_limit" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, daily_limit_version")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);

    const currentVersion =
      typeof device.daily_limit_version === "number"
        ? device.daily_limit_version
        : 0;
    const nextVersion = currentVersion + 1;
    const now = new Date().toISOString();

    const { data: updated, error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        daily_limit_minutes: dailyLimitMinutes,
        daily_limit_version: nextVersion,
        daily_limit_updated_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .eq("daily_limit_version", currentVersion)
      .select("device_id")
      .maybeSingle();

    if (updateError) return json({ error: "daily_limit_update_failed" }, 500);
    if (!updated) return json({ error: "daily_limit_version_conflict" }, 409);

    let commandId: string | null = null;
    let pushAccepted = false;

    try {
      const baseUrl = Deno.env.get("SUPABASE_URL");
      if (!baseUrl) throw new Error("supabase_url_missing");

      const sendResponse = await fetch(baseUrl + "/functions/v1/send-command", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId,
          controlToken,
          command: "SYNC_DAILY_LIMIT",
        }),
      });

      const body = await sendResponse.json().catch(() => ({}));
      if (sendResponse.ok) {
        commandId =
          typeof body.commandId === "string" ? body.commandId : null;
        pushAccepted = true;
      }
    } catch {
      // Fallback queue below handles reconnect delivery.
    }

    if (!pushAccepted) {
      const { data: queued, error: queueError } = await ctx.supabaseAdmin
        .from("device_commands")
        .insert({
          device_id: deviceId,
          command: "SYNC_DAILY_LIMIT",
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
            error: "daily_limit_saved_but_sync_queue_failed",
            dailyLimitVersion: nextVersion,
          },
          500,
        );
      }
      commandId = queued.command_id as string;
    }

    return json({
      ok: true,
      dailyLimitMinutes,
      dailyLimitVersion: nextVersion,
      commandId,
      deliveryStatus: "SENT",
      pushAccepted,
    });
  }),
};
