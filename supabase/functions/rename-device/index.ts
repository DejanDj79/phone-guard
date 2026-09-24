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

function normalizeDisplayName(value: unknown): string | null {
  if (typeof value !== "string") return null;

  const normalized = value.trim().replace(/\s+/g, " ");
  if (normalized.length < 1 || normalized.length > 40) return null;
  if (/[\u0000-\u001F\u007F]/.test(normalized)) return null;

  return normalized;
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
    const displayName = normalizeDisplayName(payload.displayName);

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }
    if (!displayName) {
      return json({ error: "invalid_display_name" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);
    const now = new Date().toISOString();

    const { data: updated, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        display_name: displayName,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .select("device_id, display_name")
      .maybeSingle();

    if (error) {
      return json({ error: "database_error" }, 500);
    }
    if (!updated) {
      return json({ error: "device_auth_failed" }, 403);
    }

    return json({
      ok: true,
      deviceId: updated.device_id,
      displayName: updated.display_name,
    });
  }),
};
