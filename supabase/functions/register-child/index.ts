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
    const displayName =
      typeof payload.displayName === "string"
        ? payload.displayName.trim().slice(0, 80)
        : "Child device";
    const pairingCode =
      typeof payload.pairingCode === "string"
        ? payload.pairingCode.trim().toUpperCase()
        : "";
    const deviceSecret =
      typeof payload.deviceSecret === "string" ? payload.deviceSecret : "";
    const fcmToken =
      typeof payload.fcmToken === "string" && payload.fcmToken.length > 0
        ? payload.fcmToken.slice(0, 4096)
        : null;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (!PAIRING_CODE.test(pairingCode)) {
      return json({ error: "invalid_pairing_code" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);
    const pairingCodeHash = await sha256Hex(pairingCode);
    const now = new Date();
    const newPairingExpiresAt = new Date(now.getTime() + PAIRING_TTL_MS);

    const { data: existing, error: readError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_secret_hash, control_token_hash, pairing_expires_at")
      .eq("device_id", deviceId)
      .maybeSingle();

    if (readError) {
      return json({ error: "database_error" }, 500);
    }

    if (existing && existing.device_secret_hash !== deviceSecretHash) {
      return json({ error: "device_auth_failed" }, 403);
    }

    let writeError;
    let effectivePairingExpiresAt = newPairingExpiresAt.toISOString();
    let paired = false;

    if (existing) {
      paired = Boolean(existing.control_token_hash);

      const update: Record<string, unknown> = {
        display_name: displayName || "Child device",
        last_seen_at: now.toISOString(),
        updated_at: now.toISOString(),
      };

      if (!paired) {
        update.pairing_code_hash = pairingCodeHash;
        update.pairing_expires_at = newPairingExpiresAt.toISOString();
      } else {
        effectivePairingExpiresAt =
          typeof existing.pairing_expires_at === "string"
            ? existing.pairing_expires_at
            : now.toISOString();
      }

      if (fcmToken !== null) {
        update.fcm_token = fcmToken;
      }

      const result = await ctx.supabaseAdmin
        .from("child_devices")
        .update(update)
        .eq("device_id", deviceId)
        .eq("device_secret_hash", deviceSecretHash);

      writeError = result.error;
    } else {
      const result = await ctx.supabaseAdmin.from("child_devices").insert({
        device_id: deviceId,
        display_name: displayName || "Child device",
        pairing_code_hash: pairingCodeHash,
        pairing_expires_at: newPairingExpiresAt.toISOString(),
        device_secret_hash: deviceSecretHash,
        fcm_token: fcmToken,
        access_state: "ALLOWED",
        last_seen_at: now.toISOString(),
        updated_at: now.toISOString(),
      });

      writeError = result.error;
    }

    if (writeError) {
      if (writeError.code === "23505") {
        return json({ error: "pairing_code_collision" }, 409);
      }
      return json({ error: "database_error" }, 500);
    }

    return json({
      ok: true,
      deviceId,
      paired,
      pairingExpiresAt: effectivePairingExpiresAt,
    });
  }),
};
