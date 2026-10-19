import { withSupabase } from "npm:@supabase/server@1.7.1";

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
    const latitude =
      typeof payload.latitude === "number" ? payload.latitude : Number.NaN;
    const longitude =
      typeof payload.longitude === "number" ? payload.longitude : Number.NaN;
    const accuracyMeters =
      typeof payload.accuracyMeters === "number"
        ? payload.accuracyMeters
        : null;
    const capturedAtMillis =
      typeof payload.capturedAtMillis === "number"
        ? Math.trunc(payload.capturedAtMillis)
        : 0;

    if (!UUID_PATTERN.test(deviceId)) {
      return json({ error: "invalid_device_id" }, 400);
    }
    if (deviceSecret.length < 32 || deviceSecret.length > 256) {
      return json({ error: "invalid_device_secret" }, 400);
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      return json({ error: "invalid_latitude" }, 400);
    }
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      return json({ error: "invalid_longitude" }, 400);
    }
    if (
      accuracyMeters !== null &&
      (
        !Number.isFinite(accuracyMeters) ||
        accuracyMeters < 0 ||
        accuracyMeters > 100000
      )
    ) {
      return json({ error: "invalid_accuracy" }, 400);
    }
    if (capturedAtMillis <= 0) {
      return json({ error: "invalid_captured_at" }, 400);
    }

    const deviceSecretHash = await sha256Hex(deviceSecret);

    const { data: device, error: deviceError } = await ctx.supabaseAdmin
      .from("child_devices")
      .select("device_id")
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash)
      .maybeSingle();

    if (deviceError) {
      return json({ error: "database_error" }, 500);
    }
    if (!device) {
      return json({ error: "device_auth_failed" }, 403);
    }

    const now = new Date().toISOString();
    const capturedAt = new Date(capturedAtMillis);

    if (Number.isNaN(capturedAt.getTime())) {
      return json({ error: "invalid_captured_at" }, 400);
    }

    const { data: location, error: locationError } = await ctx.supabaseAdmin
      .from("child_locations")
      .upsert(
        {
          device_id: deviceId,
          latitude,
          longitude,
          accuracy_meters: accuracyMeters,
          captured_at: capturedAt.toISOString(),
          received_at: now,
          updated_at: now,
        },
        { onConflict: "device_id" },
      )
      .select(
        "device_id, latitude, longitude, accuracy_meters, captured_at, received_at",
      )
      .single();

    if (locationError || !location) {
      return json({ error: "location_store_failed" }, 500);
    }

    await ctx.supabaseAdmin
      .from("child_devices")
      .update({
        last_seen_at: now,
        updated_at: now,
      })
      .eq("device_id", deviceId)
      .eq("device_secret_hash", deviceSecretHash);

    return json({
      ok: true,
      location: {
        deviceId: location.device_id,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracyMeters: location.accuracy_meters,
        capturedAt: location.captured_at,
        receivedAt: location.received_at,
      },
    });
  }),
};
