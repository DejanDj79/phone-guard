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

    const { data: device, error: readError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (readError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const now = new Date().toISOString();

    const { error: commandError } = await ctx.supabaseAdmin
      .from("device_commands")
      .update({
        status: "FAILED",
        error_code: "device_unpaired",
      })
      .eq("device_id", deviceId)
      .in("status", ["PENDING", "SENT"]);

    if (commandError) {
      return json({ error: "command_cleanup_failed" }, 500);
    }

    const { data: updated, error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        control_token_hash: null,
        pairing_expires_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .select("device_id")
      .maybeSingle();

    if (updateError) {
      return json({ error: "database_error" }, 500);
    }
    if (!updated) {
      return json({ error: "device_auth_failed" }, 403);
    }

    return json({
      ok: true,
      deviceId: updated.device_id,
      paired: false,
    });
  }),
};
