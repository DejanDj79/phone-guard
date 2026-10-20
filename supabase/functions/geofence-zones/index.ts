import { withSupabase } from "npm:@supabase/server@1.7.1";
import { requireParentUserId } from "../_shared/parent-auth.ts";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_ZONES_PER_DEVICE = 20;

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

function mapZone(row: any) {
  return {
    zoneId: row.zone_id,
    name: row.name,
    latitude: row.latitude,
    longitude: row.longitude,
    radiusMeters: row.radius_meters,
    enabled: row.enabled,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

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
    const action =
      typeof payload.action === "string"
        ? payload.action.trim().toLowerCase()
        : "list";

    if (!UUID_PATTERN.test(deviceId)) return json({ error: "invalid_device_id" }, 400);
    if (controlToken.length < 32 || controlToken.length > 256) {
      return json({ error: "invalid_control_token" }, 400);
    }

    const controlTokenHash = await sha256Hex(controlToken);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id")
      .eq("device_id", deviceId)
      .eq("parent_user_id", parentUserId)
      .eq("control_token_hash", controlTokenHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);

    if (action === "list") {
      const { data, error } = await ctx.supabaseAdmin
        .from("geofence_zones")
        .select("zone_id, name, latitude, longitude, radius_meters, enabled, created_at, updated_at")
        .eq("device_id", deviceId)
        .order("created_at", { ascending: true });

      if (error) return json({ error: "database_error" }, 500);
      return json({ ok: true, zones: (data ?? []).map(mapZone) });
    }

    if (action === "delete") {
      const zoneId =
        typeof payload.zoneId === "string" ? payload.zoneId.trim() : "";
      if (!UUID_PATTERN.test(zoneId)) return json({ error: "invalid_zone_id" }, 400);

      const { data, error } = await ctx.supabaseAdmin
        .from("geofence_zones")
        .delete()
        .eq("zone_id", zoneId)
        .eq("device_id", deviceId)
        .select("zone_id")
        .maybeSingle();

      if (error) return json({ error: "database_error" }, 500);
      if (!data) return json({ error: "zone_not_found" }, 404);

      return json({ ok: true, deletedZoneId: zoneId });
    }

    if (action !== "save") return json({ error: "invalid_action" }, 400);

    const zoneId =
      typeof payload.zoneId === "string" ? payload.zoneId.trim() : "";
    const name =
      typeof payload.name === "string" ? payload.name.trim() : "";
    const latitude =
      typeof payload.latitude === "number" ? payload.latitude : Number.NaN;
    const longitude =
      typeof payload.longitude === "number" ? payload.longitude : Number.NaN;
    const radiusMeters =
      typeof payload.radiusMeters === "number"
        ? Math.trunc(payload.radiusMeters)
        : 0;
    const enabled =
      typeof payload.enabled === "boolean" ? payload.enabled : true;

    if (zoneId && !UUID_PATTERN.test(zoneId)) {
      return json({ error: "invalid_zone_id" }, 400);
    }
    if (!name || name.length > 60) return json({ error: "invalid_name" }, 400);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      return json({ error: "invalid_latitude" }, 400);
    }
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      return json({ error: "invalid_longitude" }, 400);
    }
    if (radiusMeters < 50 || radiusMeters > 5000) {
      return json({ error: "invalid_radius" }, 400);
    }

    const now = new Date().toISOString();

    if (!zoneId) {
      const { count, error: countError } = await ctx.supabaseAdmin
        .from("geofence_zones")
        .select("zone_id", { count: "exact", head: true })
        .eq("device_id", deviceId);

      if (countError) return json({ error: "database_error" }, 500);
      if ((count ?? 0) >= MAX_ZONES_PER_DEVICE) {
        return json({ error: "zone_limit_reached" }, 409);
      }

      const { data, error } = await ctx.supabaseAdmin
        .from("geofence_zones")
        .insert({
          device_id: deviceId,
          name,
          latitude,
          longitude,
          radius_meters: radiusMeters,
          enabled,
          updated_at: now,
        })
        .select("zone_id, name, latitude, longitude, radius_meters, enabled, created_at, updated_at")
        .single();

      if (error || !data) return json({ error: "zone_save_failed" }, 500);
      return json({ ok: true, zone: mapZone(data) });
    }

    const { data, error } = await ctx.supabaseAdmin
      .from("geofence_zones")
      .update({
        name,
        latitude,
        longitude,
        radius_meters: radiusMeters,
        enabled,
        updated_at: now,
      })
      .eq("zone_id", zoneId)
      .eq("device_id", deviceId)
      .select("zone_id, name, latitude, longitude, radius_meters, enabled, created_at, updated_at")
      .maybeSingle();

    if (error) return json({ error: "zone_save_failed" }, 500);
    if (!data) return json({ error: "zone_not_found" }, 404);

    return json({ ok: true, zone: mapZone(data) });
  }),
};
