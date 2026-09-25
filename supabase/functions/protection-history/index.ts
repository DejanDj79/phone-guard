import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const HISTORY_LIMIT = 30;

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
    const action =
      typeof payload.action === "string" ? payload.action.trim() : "fetch";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);
    const { data: device, error: deviceError } =
      await ctx.supabaseAdmin
        .from("child_devices")
        .select("device_id")
        .eq("device_id", deviceId)
        .eq("control_token_hash", controlTokenHash)
        .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    if (action === "clear") {
      const { error: clearError } =
        await ctx.supabaseAdmin
          .from("protection_events")
          .delete()
          .eq("device_id", deviceId);

      if (clearError) {
        return json({ error: "database_error" }, 500);
      }

      return json({ ok: true, cleared: true });
    }

    if (action !== "fetch") {
      return json({ error: "invalid_action" }, 400);
    }

    const { data: events, error } =
      await ctx.supabaseAdmin
        .from("protection_events")
        .select("event_type, created_at")
        .eq("device_id", deviceId)
        .order("created_at", { ascending: false })
        .limit(HISTORY_LIMIT);

    if (error) {
      return json({ error: "database_error" }, 500);
    }

    return json({
      ok: true,
      events:
        (events ?? []).map((event) => ({
          eventType: event.event_type,
          createdAt: event.created_at,
        })),
    });
  }),
};
