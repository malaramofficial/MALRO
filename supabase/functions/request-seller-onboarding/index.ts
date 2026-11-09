const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: corsHeaders });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const baseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authorization = req.headers.get("Authorization");
  if (!baseUrl || !anonKey || !serviceKey) return json(500, { error: "server_configuration_missing" });
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "authentication_required" });

  try {
    const userResponse = await fetch(`${baseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authorization },
    });
    if (!userResponse.ok) return json(401, { error: "invalid_session" });
    const user = await userResponse.json();
    if (!user?.id) return json(401, { error: "invalid_session" });

    const adminHeaders = {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      "Content-Type": "application/json",
    };
    const profileUrl = `${baseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(user.id)}&select=account_role`;
    const profileResponse = await fetch(profileUrl, { headers: adminHeaders });
    if (!profileResponse.ok) return json(502, { error: "profile_lookup_failed" });
    const profiles = await profileResponse.json();
    if (!Array.isArray(profiles) || profiles.length !== 1) return json(409, { error: "profile_not_ready" });

    const currentRole = profiles[0].account_role;
    if (currentRole === "seller") return json(200, { status: "already_approved", role: "seller" });
    if (currentRole === "delivery" || currentRole === "admin") return json(403, { error: "role_not_eligible" });

    const updateResponse = await fetch(profileUrl, {
      method: "PATCH",
      headers: { ...adminHeaders, Prefer: "return=representation" },
      body: JSON.stringify({ account_role: "seller_pending" }),
    });
    if (!updateResponse.ok) return json(502, { error: "role_update_failed" });
    return json(200, { status: "seller_pending" });
  } catch {
    return json(500, { error: "unexpected_server_error" });
  }
});
