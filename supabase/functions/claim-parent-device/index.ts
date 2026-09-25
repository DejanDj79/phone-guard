import { withSupabase } from "npm:@supabase/server@1.7.1";
import { requireParentUserId } from "../_shared/parent-auth.ts";

const UUID =
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

    const parentUserId = await requireParentUserId(req, ctx);
    if (!parentUserId) {
      return json({ error: "parent_auth_required" }, 401);
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
      typeof payload.controlToken === "string" ? payload.controlToken.trim() : "";

    if (!UUID.test(deviceId) || controlToken.length < 16) {
      return json({ error: "invalid_request" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: lookupError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, parent_user_id")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (lookupError) {
      return json({ error: "database_error" }, 500);
    }

    if (!device) {
      return json({ error: "invalid_parent_pairing" }, 404);
    }

    if (device.parent_user_id === parentUserId) {
      return json({ ok: true, status: "already_owned" });
    }

    if (device.parent_user_id) {
      return json({ error: "device_owned_by_another_parent" }, 409);
    }

    const now = new Date().toISOString();
    const { data: claimed, error: claimError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        parent_user_id: parentUserId,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .is("parent_user_id", null)
      .select("device_id")
      .maybeSingle();

    if (claimError) {
      return json({ error: "database_error" }, 500);
    }

    if (!claimed) {
      return json({ error: "ownership_changed" }, 409);
    }

    return json({ ok: true, status: "claimed" });
  }),
};
