import { withSupabase } from "npm:@supabase/server@1.7.1";
import { requireParentUserId } from "../_shared/parent-auth.ts";

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

export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    const parentUserId = await requireParentUserId(req, ctx);
    if (!parentUserId) return json({ error: "parent_auth_required" }, 401);

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

    const { data: device, error } = await ctx.supabaseAdmin
      .from("child_devices")
      .select(
        "installed_apps, installed_apps_updated_at, allowed_apps, allowed_apps_version, allowed_apps_updated_at",
      )
      .eq("device_id", deviceId)
      .eq("parent_user_id", parentUserId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (error) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    return json({
      ok: true,
      apps: {
        installed: Array.isArray(device.installed_apps) ? device.installed_apps : [],
        installedUpdatedAt: device.installed_apps_updated_at,
        allowedPackages: Array.isArray(device.allowed_apps) ? device.allowed_apps : [],
        version:
          typeof device.allowed_apps_version === "number"
            ? device.allowed_apps_version
            : 0,
        updatedAt: device.allowed_apps_updated_at,
      },
    });
  }),
};
