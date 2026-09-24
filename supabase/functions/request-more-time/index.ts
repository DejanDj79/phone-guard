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
    const deviceSecret =
      typeof payload.deviceSecret === "string" ? payload.deviceSecret : "";
    const requestedMinutes =
      typeof payload.requestedMinutes === "number"
        ? Math.trunc(payload.requestedMinutes)
        : 0;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (requestedMinutes < 1 || requestedMinutes > 120) {
      return json({ error: "invalid_requested_minutes" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name, control_token_hash, parent_fcm_token")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);
    if (!device.control_token_hash) return json({ error: "child_not_paired" }, 409);

    const expiryCutoff = new Date(Date.now() - 30 * 60_000).toISOString();
    await ctx.supabaseAdmin
      .from("time_requests")
      .update({
        status: "EXPIRED",
        resolved_at: new Date().toISOString(),
      })
      .eq("device_id", deviceId)
      .eq("status", "PENDING")
      .lt("created_at", expiryCutoff);

    const { data: existing } = await ctx.supabaseAdmin
      .from("time_requests")
      .select("request_id, requested_minutes, created_at")
      .eq("device_id", deviceId)
      .eq("status", "PENDING")
      .maybeSingle();

    if (existing) {
      return json({
        ok: true,
        requestId: existing.request_id,
        requestedMinutes: existing.requested_minutes,
        createdAt: existing.created_at,
        alreadyPending: true,
        pushSent: false,
      });
    }

    const { data: requestRow, error: insertError } = await ctx.supabaseAdmin
      .from("time_requests")
      .insert({
        device_id: deviceId,
        requested_minutes: requestedMinutes,
        status: "PENDING",
      })
      .select("request_id, requested_minutes, created_at")
      .single();

    if (insertError || !requestRow) {
      return json({ error: "request_create_failed" }, 500);
    }

    let pushSent = false;
    if (typeof device.parent_fcm_token === "string" && device.parent_fcm_token) {
      try {
        await sendFirebaseMessage({
          token: device.parent_fcm_token,
          title: "More time requested",
          body:
            device.display_name +
            " is asking for " +
            requestedMinutes +
            " more minutes.",
          data: {
            type: "TIME_REQUEST",
            request_id: requestRow.request_id,
            device_id: deviceId,
            display_name: device.display_name,
            requested_minutes: String(requestedMinutes),
          },
          collapseKey: "phoneguard-time-request-" + deviceId,
          ttl: "1800s",
        });
        pushSent = true;
      } catch (error) {
        console.error("Parent push failed", error);
      }
    }

    return json({
      ok: true,
      requestId: requestRow.request_id,
      requestedMinutes,
      createdAt: requestRow.created_at,
      alreadyPending: false,
      pushSent,
    });
  }),
};
