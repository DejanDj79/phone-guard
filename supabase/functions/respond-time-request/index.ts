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
    const requestId =
      typeof payload.requestId === "string" ? payload.requestId.trim() : "";
    const decision =
      typeof payload.decision === "string"
        ? payload.decision.trim().toUpperCase()
        : "";

    if (!UUID_PATTERN.test(deviceId) || !UUID_PATTERN.test(requestId)) {
      return json({ error: "invalid_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }
    if (decision !== "APPROVE" && decision !== "DENY") {
      return json({ error: "invalid_decision" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name, fcm_token")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);

    const { data: requestRow, error: requestError } = await ctx.supabaseAdmin
      .from("time_requests")
      .select("request_id, requested_minutes, status")
      .eq("request_id", requestId)
      .eq("device_id", deviceId)
      .maybeSingle();

    if (requestError) return json({ error: "database_error" }, 500);
    if (!requestRow) return json({ error: "request_not_found" }, 404);
    if (requestRow.status !== "PENDING") {
      return json({ error: "request_already_resolved" }, 409);
    }

    if (decision === "DENY") {
      const { error } = await ctx.supabaseAdmin
        .from("time_requests")
        .update({
          status: "DENIED",
          resolved_at: new Date().toISOString(),
        })
        .eq("request_id", requestId)
        .eq("status", "PENDING");

      if (error) return json({ error: "database_error" }, 500);

      if (device.fcm_token) {
        try {
          await sendFirebaseMessage({
            token: device.fcm_token,
            data: {
              type: "TIME_REQUEST_RESULT",
              decision: "DENIED",
              request_id: requestId,
              requested_minutes: String(requestRow.requested_minutes),
            },
            collapseKey: "phoneguard-time-request-result",
            ttl: "1800s",
          });
        } catch (pushError) {
          console.error("Child denial feedback push failed", pushError);
        }
      }

      return json({
        ok: true,
        decision: "DENIED",
        requestedMinutes: requestRow.requested_minutes,
      });
    }

    if (!device.fcm_token) {
      return json({ error: "device_has_no_fcm_token" }, 409);
    }

    const { data: commandRow, error: commandError } = await ctx.supabaseAdmin
      .from("device_commands")
      .insert({
        device_id: deviceId,
        command: "BONUS_TIME",
        bonus_minutes: requestRow.requested_minutes,
        status: "PENDING",
      })
      .select("command_id")
      .single();

    if (commandError || !commandRow) {
      return json({ error: "command_create_failed" }, 500);
    }

    try {
      await sendFirebaseMessage({
        token: device.fcm_token,
        data: {
          command: "BONUS_TIME",
          command_id: commandRow.command_id,
          bonus_minutes: String(requestRow.requested_minutes),
        },
        collapseKey: "phoneguard-control",
        ttl: "60s",
      });
    } catch (error) {
      await ctx.supabaseAdmin
        .from("device_commands")
        .update({
          status: "FAILED",
          error_code: "fcm_send_failed",
        })
        .eq("command_id", commandRow.command_id);

      console.error("Child FCM failed", error);
      return json({ error: "fcm_send_failed" }, 502);
    }

    const now = new Date().toISOString();

    const { error: sentError } = await ctx.supabaseAdmin
      .from("device_commands")
      .update({
        status: "SENT",
        sent_at: now,
        error_code: null,
      })
      .eq("command_id", commandRow.command_id);

    if (sentError) {
      return json({ error: "command_status_update_failed" }, 500);
    }

    const { error: resolveError } = await ctx.supabaseAdmin
      .from("time_requests")
      .update({
        status: "APPROVED",
        resolved_at: now,
      })
      .eq("request_id", requestId)
      .eq("status", "PENDING");

    if (resolveError) {
      return json({ error: "request_status_update_failed" }, 500);
    }

    return json({
      ok: true,
      decision: "APPROVED",
      requestedMinutes: requestRow.requested_minutes,
      commandId: commandRow.command_id,
      deliveryStatus: "SENT",
    });
  }),
};
