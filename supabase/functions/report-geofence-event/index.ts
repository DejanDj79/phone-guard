import { withSupabase } from "npm:@supabase/server@1.7.1";
import { sendFirebaseMessage } from "../_shared/firebase.ts";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EVENT_TYPES = new Set(["ENTER", "EXIT"]);
const RETAINED_EVENTS_PER_DEVICE = 100;

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
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

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
    const zoneId =
      typeof payload.zoneId === "string" ? payload.zoneId.trim() : "";
    const eventType =
      typeof payload.eventType === "string"
        ? payload.eventType.trim().toUpperCase()
        : "";
    const occurredAtMillis =
      typeof payload.occurredAtMillis === "number"
        ? Math.trunc(payload.occurredAtMillis)
        : 0;

    if (!UUID_PATTERN.test(deviceId)) return json({ error: "invalid_device_id" }, 400);
    if (!UUID_PATTERN.test(zoneId)) return json({ error: "invalid_zone_id" }, 400);
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (!EVENT_TYPES.has(eventType)) return json({ error: "invalid_event_type" }, 400);
    if (occurredAtMillis <= 0) return json({ error: "invalid_occurred_at" }, 400);

    const occurredAt = new Date(occurredAtMillis);
    if (Number.isNaN(occurredAt.getTime())) {
      return json({ error: "invalid_occurred_at" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id, display_name, parent_fcm_token")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (deviceError) return json({ error: "database_error" }, 500);
    if (!device) return json({ error: "device_auth_failed" }, 403);

    const { data: zone, error: zoneError } = await ctx.supabaseAdmin
      .from("geofence_zones")
      .select("zone_id, name")
      .eq("zone_id", zoneId)
      .eq("device_id", deviceId)
      .maybeSingle();

    if (zoneError) return json({ error: "database_error" }, 500);
    if (!zone) return json({ error: "zone_not_found" }, 404);

    const now = new Date().toISOString();

    const { data: eventRow, error: insertError } = await ctx.supabaseAdmin
      .from("geofence_events")
      .insert({
        device_id: deviceId,
        zone_id: zoneId,
        zone_name: zone.name,
        event_type: eventType,
        occurred_at: occurredAt.toISOString(),
        received_at: now,
      })
      .select("event_id")
      .single();

    if (insertError || !eventRow) {
      return json({ error: "event_store_failed" }, 500);
    }

    const { data: excess } = await ctx.supabaseAdmin
      .from("geofence_events")
      .select("event_id")
      .eq("device_id", deviceId)
      .order("occurred_at", { ascending: false })
      .range(RETAINED_EVENTS_PER_DEVICE, 999);

    if ((excess ?? []).length > 0) {
      await ctx.supabaseAdmin
        .from("geofence_events")
        .delete()
        .in("event_id", (excess ?? []).map((row) => row.event_id));
    }

    let pushSent = false;
    const parentToken =
      typeof device.parent_fcm_token === "string"
        ? device.parent_fcm_token.trim()
        : "";

    if (parentToken) {
      try {
        await sendFirebaseMessage({
          token: parentToken,
          data: {
            type: "GEOFENCE_EVENT",
            event_id: eventRow.event_id,
            event_type: eventType,
            zone_id: zoneId,
            zone_name: zone.name,
            device_id: deviceId,
            display_name:
              typeof device.display_name === "string" && device.display_name.trim()
                ? device.display_name.trim()
                : "Child device",
            occurred_at: occurredAt.toISOString(),
          },
          ttl: "3600s",
        });
        pushSent = true;
      } catch (error) {
        console.error("Geofence Parent push failed", error);
      }
    }

    return json({
      ok: true,
      eventId: eventRow.event_id,
      eventType,
      zoneId,
      zoneName: zone.name,
      occurredAt: occurredAt.toISOString(),
      pushSent,
    });
  }),
};
