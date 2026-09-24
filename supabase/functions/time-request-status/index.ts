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

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);

    const cutoff = new Date(Date.now() - 30 * 60_000).toISOString();
    await ctx.supabaseAdmin
      .from("time_requests")
      .update({
        status: "EXPIRED",
        resolved_at: new Date().toISOString(),
      })
      .eq("device_id", deviceId)
      .eq("status", "PENDING")
      .lt("created_at", cutoff);

    const { data: requestRow, error: requestError } = await ctx.supabaseAdmin
      .from("time_requests")
      .select("request_id, requested_minutes, created_at")
      .eq("device_id", deviceId)
      .eq("status", "PENDING")
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (requestError) return json({ error: "database_error" }, 500);

    return json({
      ok: true,
      request:
        requestRow
          ? {
              requestId: requestRow.request_id,
              deviceId,
              displayName: device.display_name,
              requestedMinutes: requestRow.requested_minutes,
              createdAt: requestRow.created_at,
            }
          : null,
    });
  }),
};
