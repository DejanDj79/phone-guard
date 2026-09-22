import { withSupabase } from "npm:@supabase/server@1.7.1";

const PAIRING_TTL_MS = 15 * 60 * 1000;
const PAIRING_CODE = /^[A-Z0-9]{6}$/;
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
    const deviceSecret =
      typeof payload.deviceSecret === "string" ? payload.deviceSecret : "";
    const pairingCode =
      typeof payload.pairingCode === "string"
        ? payload.pairingCode.trim().toUpperCase()
        : "";

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (!PAIRING_CODE.test(pairingCode)) {
      return json({ error: "invalid_pairing_code" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);
    const pairingCodeHash = await sha256Hex(pairingCode);
    const expiresAt =
      new Date(Date.now() + PAIRING_TTL_MS).toISOString();

    const { data: device, error: readError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (readError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const { error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        control_token_hash: null,
        pairing_code_hash: pairingCodeHash,
        pairing_expires_at: expiresAt,
        updated_at: new Date().toISOString(),
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash);

    if (updateError) {
      if (updateError.code === "23505") {
        return json({ error: "pairing_code_collision" }, 409);
      }
      return json({ error: "database_error" }, 500);
    }

    return json({
      ok: true,
      deviceId,
      paired: false,
      pairingExpiresAt: expiresAt,
    });
  }),
};
