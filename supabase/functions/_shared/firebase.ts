const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

function decodeBase64Utf8(value: string): string {
  const binary = atob(value.trim());
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function loadFirebaseServiceAccount(): ServiceAccount {
  const encoded = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_B64");
  const raw = encoded
    ? decodeBase64Utf8(encoded)
    : Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");

  if (!raw) throw new Error("firebase_not_configured");

  const account = JSON.parse(raw) as ServiceAccount;
  if (!account.project_id || !account.client_email || !account.private_key) {
    throw new Error("firebase_credentials_invalid");
  }

  return account;
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
  const normalized = pem
    .replaceAll("\\r\\n", "\n")
    .replaceAll("\\n", "\n")
    .replaceAll("\\r", "\n");

  const beginMarker = "-----BEGIN PRIVATE KEY-----";
  const endMarker = "-----END PRIVATE KEY-----";
  const begin = normalized.indexOf(beginMarker);
  const end = normalized.indexOf(endMarker);

  if (begin < 0 || end <= begin) {
    throw new Error("firebase_private_key_pem_invalid");
  }

  const body = normalized.slice(begin + beginMarker.length, end);
  const base64 = body.replace(/[^A-Za-z0-9+/=]/g, "");

  if (!base64 || base64.length % 4 !== 0) {
    throw new Error("firebase_private_key_base64_invalid");
  }

  const binary = atob(base64);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function getAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = account.token_uri || DEFAULT_TOKEN_URI;

  const header = base64UrlJson({ alg: "RS256", typ: "JWT" });
  const payload =
    base64UrlJson({
      iss: account.client_email,
      scope: FCM_SCOPE,
      aud: tokenUri,
      iat: now,
      exp: now + 3600,
    });
  const unsignedToken = header + "." + payload;

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8Bytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(unsignedToken),
  );

  const assertion =
    unsignedToken + "." + base64UrlBytes(new Uint8Array(signature));

  const response = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
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

export async function sendFirebaseMessage(args: {
  token: string;
  data: Record<string, string>;
  title?: string;
  body?: string;
  collapseKey?: string;
  ttl?: string;
}): Promise<void> {
  const account = loadFirebaseServiceAccount();
  const accessToken = await getAccessToken(account);

  const message: Record<string, unknown> = {
    token: args.token,
    data: args.data,
    android: {
      priority: "high",
      ttl: args.ttl ?? "300s",
      ...(args.collapseKey ? { collapse_key: args.collapseKey } : {}),
    },
  };

  if (args.title || args.body) {
    message.notification = {
      title: args.title ?? "PhoneGuard",
      body: args.body ?? "",
    };
  }

  const response = await fetch(
    "https://fcm.googleapis.com/v1/projects/" +
      encodeURIComponent(account.project_id) +
      "/messages:send",
    {
      method: "POST",
      headers: {
        Authorization: "Bearer " + accessToken,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ message }),
    },
  );

  if (!response.ok) {
    const details = (await response.text()).slice(0, 500);
    throw new Error("fcm_send_failed:" + response.status + ":" + details);
  }
}
