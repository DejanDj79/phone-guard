import { withSupabase } from "npm:@supabase/server@1.7.1";

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

export default {
  fetch: withSupabase({ auth: "none" }, async (req) => {
    if (req.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    return json({
      ok: true,
      disabled: true,
      reason: "heartbeat_only_offline_alerts_disabled",
    });
  }),
};
