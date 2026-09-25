import { withSupabase } from "npm:@supabase/server@1.7.1";

const PAIRING_CODE = /^[A-Z0-9]{6}$/;
const MAX_ATTEMPTS = 20;
const ATTEMPT_WINDOW_MS = 5 * 60 * 1000;

type ParentIdentity =
  | { kind: "none"; userId: null }
  | { kind: "invalid"; userId: null }
  | { kind: "user"; userId: string };

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function randomToken(byteLength = 32): string {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/g, "");
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

function clientIp(req: Request): string {
  const cloudflareIp = req.headers.get("cf-connecting-ip")?.trim();
  if (cloudflareIp) return cloudflareIp.slice(0, 128);

  const realIp = req.headers.get("x-real-ip")?.trim();
  if (realIp) return realIp.slice(0, 128);

  const forwardedFor = req.headers.get("x-forwarded-for") ?? "";
  return forwardedFor.split(",")[0]?.trim().slice(0, 128) || "unknown";
}

async function parentIdentity(
  req: Request,
  ctx: { supabaseAdmin: any },
): Promise<ParentIdentity> {
  const authorization = req.headers.get("authorization")?.trim();

  if (!authorization) {
    return { kind: "none", userId: null };
  }

  const match = /^Bearer\s+(.+)$/i.exec(authorization);
  const token = match?.[1]?.trim();

  if (!token) {
    return { kind: "invalid", userId: null };
  }

  const {
    data: { user },
    error,
  } = await ctx.supabaseAdmin.auth.getUser(token);

  if (error || !user) {
    return { kind: "invalid", userId: null };
  }

  return { kind: "user", userId: user.id };
}

export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    const identity = await parentIdentity(req, ctx);
    if (identity.kind === "invalid") {
      return json({ error: "parent_auth_invalid" }, 401);
    }

    const ipAddress = clientIp(req);
    const windowStart = new Date(Date.now() - ATTEMPT_WINDOW_MS).toISOString();

    const { count, error: countError } = await ctx.supabaseAdmin
      .from("pairing_attempts")
      .select("id", { count: "exact", head: true })
      .eq("ip_address", ipAddress)
      .gte("attempted_at", windowStart);

    if (countError) {
      return json({ error: "database_error" }, 500);
    }

    if ((count ?? 0) >= MAX_ATTEMPTS) {
      return json({ error: "too_many_attempts" }, 429);
    }

    await ctx.supabaseAdmin.from("pairing_attempts").insert({
      ip_address: ipAddress,
    });

    let payload: Record<string, unknown>;
    try {
      payload = await req.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const pairingCode =
      typeof payload.pairingCode === "string"
        ? payload.pairingCode.trim().toUpperCase()
        : "";

    if (!PAIRING_CODE.test(pairingCode)) {
      return json({ error: "invalid_pairing_code" }, 400);
    }

    const pairingCodeHash = await sha256Hex(pairingCode);
    const now = new Date().toISOString();

    const { data: candidate, error: lookupError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select(
        "device_id, display_name, access_state, temporary_allow_until, parent_user_id",
      )
      .eq("pairing_code_hash", pairingCodeHash)
      .gt("pairing_expires_at", now)
      .maybeSingle();

    if (lookupError) {
      return json({ error: "database_error" }, 500);
    }

    if (!candidate) {
      return json({ error: "invalid_or_expired_code" }, 404);
    }

    if (candidate.parent_user_id) {
      if (identity.kind !== "user") {
        return json({ error: "parent_sign_in_required" }, 401);
      }

      if (candidate.parent_user_id !== identity.userId) {
        return json({ error: "device_owned_by_another_parent" }, 409);
      }
    }

    const controlToken = randomToken();
    const controlTokenHash = await sha256Hex(controlToken);

    const updateValues: Record<string, unknown> = {
      control_token_hash: controlTokenHash,
      parent_fcm_token: null,
      pairing_expires_at: now,
      updated_at: now,
    };

    if (identity.kind === "user") {
      updateValues.parent_user_id = identity.userId;
    }

    let updateQuery = ctx.supabaseAdmin
      .from("child_devices")
      .update(updateValues)
      .eq("device_id", candidate.device_id)
      .eq("pairing_code_hash", pairingCodeHash)
      .gt("pairing_expires_at", now);

    if (candidate.parent_user_id) {
      updateQuery = updateQuery.eq(
        "parent_user_id",
        candidate.parent_user_id,
      );
    } else {
      updateQuery = updateQuery.is("parent_user_id", null);
    }

    const { data: pairedDevice, error: pairError } = await updateQuery
      .select(
        "device_id, display_name, access_state, temporary_allow_until, parent_user_id",
      )
      .maybeSingle();

    if (pairError) {
      return json({ error: "database_error" }, 500);
    }

    if (!pairedDevice) {
      return json({ error: "pairing_state_changed" }, 409);
    }

    await ctx.supabaseAdmin
      .from("time_requests")
      .update({
        status: "EXPIRED",
        resolved_at: now,
      })
      .eq("device_id", pairedDevice.device_id)
      .eq("status", "PENDING");

    const temporaryAccessMinutesRemaining =
      pairedDevice.access_state === "TEMPORARILY_ALLOWED" &&
      pairedDevice.temporary_allow_until
        ? Math.max(
            1,
            Math.ceil(
              (new Date(pairedDevice.temporary_allow_until).getTime() -
                Date.now()) /
                60000,
            ),
          )
        : null;

    return json({
      ok: true,
      device: {
        deviceId: pairedDevice.device_id,
        displayName: pairedDevice.display_name,
        state: pairedDevice.access_state,
        temporaryAccessMinutesRemaining,
      },
      controlToken,
    });
  }),
};
