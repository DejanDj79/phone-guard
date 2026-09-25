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

function normalizePackages(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length > 100) return null;

  const unique = new Set<string>();
  for (const raw of value) {
    const packageName = typeof raw === "string" ? raw.trim() : "";
    if (!PACKAGE_PATTERN.test(packageName)) return null;
    unique.add(packageName);
  }

  return Array.from(unique).sort();
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
    const allowedPackages = normalizePackages(payload.allowedPackages);
    const expectedVersion =
      typeof payload.expectedVersion === "number" &&
        Number.isInteger(payload.expectedVersion) &&
        payload.expectedVersion >= 0
        ? payload.expectedVersion
        : null;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }
    if (!allowedPackages) {
      return json({ error: "invalid_allowed_apps" }, 400);
    }
    if (expectedVersion === null) {
      return json({ error: "invalid_expected_version" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, allowed_apps_version")
      .eq("device_id", deviceId)
      .eq("parent_user_id", parentUserId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const currentVersion =
      typeof device.allowed_apps_version === "number"
        ? device.allowed_apps_version
        : 0;

    if (currentVersion !== expectedVersion) {
      return json(
        { error: "allowed_apps_version_conflict", currentVersion },
        409,
      );
    }

    const nextVersion = currentVersion + 1;
    const now = new Date().toISOString();

    const { data: updated, error: updateError } = await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        allowed_apps: allowedPackages,
        allowed_apps_version: nextVersion,
        allowed_apps_updated_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("parent_user_id", parentUserId)
      .eq("control_token_hash", controlTokenHash)
      .eq("allowed_apps_version", currentVersion)
      .select("device_id")
      .maybeSingle();

    if (updateError) {
      return json({ error: "allowed_apps_update_failed" }, 500);
    }
    if (!updated) {
      return json({ error: "allowed_apps_version_conflict" }, 409);
    }

    let commandId: string | null = null;
    let deliveryStatus = "SENT";
    let pushAccepted = false;

    try {
      const baseUrl = Deno.env.get("SUPABASE_URL");
      if (!baseUrl) throw new Error("supabase_url_missing");

      const sendResponse = await fetch(
        baseUrl + "/functions/v1/send-command",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            deviceId,
            controlToken,
            command: "SYNC_ALLOWED_APPS",
          }),
        },
      );

      const sendBody = await sendResponse.json().catch(() => ({}));

      if (sendResponse.ok) {
        commandId =
          typeof sendBody.commandId === "string" ? sendBody.commandId : null;
        deliveryStatus =
          typeof sendBody.deliveryStatus === "string"
            ? sendBody.deliveryStatus
            : "SENT";
        pushAccepted = true;
      }
    } catch {
      // A queued fallback below guarantees reconnect delivery.
    }

    if (!pushAccepted) {
      const { data: queued, error: queueError } = await ctx.supabaseAdmin
        .from("device_commands")
        .insert({
          device_id: deviceId,
          command: "SYNC_ALLOWED_APPS",
          bonus_minutes: null,
          status: "SENT",
          sent_at: now,
          error_code: "push_not_confirmed",
        })
        .select("command_id")
        .single();

      if (queueError || !queued) {
        return json(
          {
            error: "allowed_apps_saved_but_sync_queue_failed",
            allowedAppsVersion: nextVersion,
          },
          500,
        );
      }

      commandId = queued.command_id as string;
      deliveryStatus = "SENT";
    }

    return json({
      ok: true,
      allowedAppsVersion: nextVersion,
      commandId,
      deliveryStatus,
      pushAccepted,
    });
  }),
};
