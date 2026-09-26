import { withSupabase } from "npm:@supabase/server@1.7.1";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PACKAGE_PATTERN =
  /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;

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

type InstalledApp = {
  packageName: string;
  label: string;
  iconBase64?: string;
};

function normalizeApps(value: unknown): InstalledApp[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;

  const unique = new Map<string, InstalledApp>();

  for (const raw of value) {
    if (!raw || typeof raw !== "object") return null;

    const item = raw as Record<string, unknown>;
    const packageName =
      typeof item.packageName === "string" ? item.packageName.trim() : "";
    const label =
      typeof item.label === "string" ? item.label.trim().slice(0, 120) : "";
    const rawIcon =
      typeof item.iconBase64 === "string" ? item.iconBase64.trim() : "";
    const iconBase64 =
      rawIcon &&
      rawIcon.length <= 16_384 &&
      /^[A-Za-z0-9+/]+={0,2}$/.test(rawIcon)
        ? rawIcon
        : undefined;

    if (!PACKAGE_PATTERN.test(packageName) || !label) return null;
    if (rawIcon && !iconBase64) return null;

    unique.set(
      packageName,
      iconBase64
        ? { packageName, label, iconBase64 }
        : { packageName, label },
    );
  }

  return Array.from(unique.values()).sort((a, b) =>
    a.label.localeCompare(b.label, undefined, { sensitivity: "base" })
  );
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
    const apps = normalizeApps(payload.apps);

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (!apps) {
      return json({ error: "invalid_apps" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);
    const now = new Date().toISOString();

    const { data: updated, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        installed_apps: apps,
        installed_apps_updated_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .select("device_id")
      .maybeSingle();

    if (error) {
      return json({ error: "database_error" }, 500);
    }
    if (!updated) {
      return json({ error: "device_auth_failed" }, 403);
    }

    return json({ ok: true, count: apps.length, updatedAt: now });
  }),
};
