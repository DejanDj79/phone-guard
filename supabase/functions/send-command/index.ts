import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const VALID_COMMANDS = new Set(["LOCK", "UNLOCK", "BONUS_TIME"]);
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);

  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/g, "");
}

function base64UrlJson(value: unknown): string {
  return base64UrlBytes(
    new TextEncoder().encode(JSON.stringify(value)),
  );
}

function pemToPkcs8Bytes(pem: string): Uint8Array {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replaceAll(/\s/g, "");

  const binary = atob(base64);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function createServiceAccountJwt(
  serviceAccount: ServiceAccount,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = serviceAccount.token_uri || DEFAULT_TOKEN_URI;

  const header = base64UrlJson({
    alg: "RS256",
    typ: "JWT",
  });

  const payload = base64UrlJson({
    iss: serviceAccount.client_email,
    scope: FCM_SCOPE,
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  });

  const unsignedToken = header + "." + payload;

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8Bytes(serviceAccount.private_key),
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(unsignedToken),
  );

  return unsignedToken + "." + base64UrlBytes(new Uint8Array(signature));
}

async function getGoogleAccessToken(
  serviceAccount: ServiceAccount,
): Promise<string> {
  const tokenUri = serviceAccount.token_uri || DEFAULT_TOKEN_URI;
  const assertion = await createServiceAccountJwt(serviceAccount);

  const response = await fetch(tokenUri, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  const body = await response.json();

  if (!response.ok || typeof body.access_token !== "string") {
    throw new Error("firebase_oauth_failed");
  }

  return body.access_token;
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
    const command =
      typeof payload.command === "string"
        ? payload.command.trim().toUpperCase()
        : "";
    const bonusMinutes =
      typeof payload.bonusMinutes === "number"
        ? Math.trunc(payload.bonusMinutes)
        : null;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }
    if (!VALID_COMMANDS.has(command)) {
      return json({ error: "invalid_command" }, 400);
    }
    if (
      command === "BONUS_TIME" &&
      (bonusMinutes === null || bonusMinutes < 1 || bonusMinutes > 1440)
    ) {
      return json({ error: "invalid_bonus_minutes" }, 400);
    }
    if (command !== "BONUS_TIME" && bonusMinutes !== null) {
      return json({ error: "unexpected_bonus_minutes" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name, fcm_token")
      .eq("device_id", deviceId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }
    if (!device.fcm_token) {
      return json({ error: "device_has_no_fcm_token" }, 409);
    }

    const serviceAccountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!serviceAccountRaw) {
      return json({ error: "firebase_not_configured" }, 503);
    }

    let serviceAccount: ServiceAccount;
    try {
      serviceAccount = JSON.parse(serviceAccountRaw) as ServiceAccount;
    } catch {
      return json({ error: "firebase_credentials_invalid" }, 500);
    }

    if (
      !serviceAccount.project_id ||
      !serviceAccount.client_email ||
      !serviceAccount.private_key
    ) {
      return json({ error: "firebase_credentials_invalid" }, 500);
    }

    let accessToken: string;
    try {
      accessToken = await getGoogleAccessToken(serviceAccount);
    } catch {
      return json({ error: "firebase_oauth_failed" }, 502);
    }

    const data: Record<string, string> = {
      command,
    };
    if (command === "BONUS_TIME") {
      data.bonus_minutes = String(bonusMinutes);
    }

    const fcmResponse = await fetch(
      "https://fcm.googleapis.com/v1/projects/" +
        encodeURIComponent(serviceAccount.project_id) +
        "/messages:send",
      {
        method: "POST",
        headers: {
          Authorization: "Bearer " + accessToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: device.fcm_token,
            data,
            android: {
              priority: "high",
              ttl: "0s",
            },
          },
        }),
      },
    );

    const fcmBody = await fcmResponse.text();

    if (!fcmResponse.ok) {
      return json(
        {
          error: "fcm_send_failed",
          status: fcmResponse.status,
          details: fcmBody.slice(0, 500),
        },
        502,
      );
    }

    const now = new Date();
    let accessState = "ALLOWED";
    let temporaryAllowUntil: string | null = null;

    if (command === "LOCK") {
      accessState = "LOCKED";
    } else if (command === "BONUS_TIME") {
      accessState = "TEMPORARILY_ALLOWED";
      temporaryAllowUntil =
        new Date(
          now.getTime() + (bonusMinutes ?? 0) * 60_000,
        ).toISOString();
    }

    const { error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        access_state: accessState,
        temporary_allow_until: temporaryAllowUntil,
        updated_at: now.toISOString(),
      })
      .eq("device_id", deviceId);

    if (updateError) {
      return json({ error: "status_update_failed" }, 500);
    }

    return json({
      ok: true,
      device: {
        deviceId: device.device_id,
        displayName: device.display_name,
        state: accessState,
        temporaryAccessMinutesRemaining:
          accessState === "TEMPORARILY_ALLOWED"
            ? bonusMinutes
            : null,
      },
    });
  }),
};
