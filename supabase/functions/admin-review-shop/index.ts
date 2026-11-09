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
    const callerResponse = await fetch(`${baseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authorization },
    });
    if (!callerResponse.ok) return json(401, { error: "invalid_session" });
    const caller = await callerResponse.json();
    if (caller?.app_metadata?.role !== "admin") return json(403, { error: "admin_only" });

    const body = await req.json();
    const shopId = typeof body.shop_id === "string" ? body.shop_id : "";
    const status = body.status;
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(shopId)) {
      return json(400, { error: "invalid_shop_id" });
    }
    if (status !== "approved" && status !== "rejected") {
      return json(400, { error: "status_must_be_approved_or_rejected" });
    }

    const adminHeaders = {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      "Content-Type": "application/json",
    };
    const shopUrl = `${baseUrl}/rest/v1/shops?id=eq.${encodeURIComponent(shopId)}&select=id,owner_id`;
    const shopResponse = await fetch(shopUrl, { headers: adminHeaders });
    if (!shopResponse.ok) return json(502, { error: "shop_lookup_failed" });
    const shops = await shopResponse.json();
    if (!Array.isArray(shops) || shops.length !== 1) return json(404, { error: "shop_not_found" });
    const shop = shops[0];

    const updateShop = await fetch(`${baseUrl}/rest/v1/shops?id=eq.${encodeURIComponent(shopId)}`, {
      method: "PATCH",
      headers: { ...adminHeaders, Prefer: "return=minimal" },
      body: JSON.stringify({ status }),
    });
    if (!updateShop.ok) return json(502, { error: "shop_status_update_failed" });

    if (status === "approved") {
      const profileUpdate = await fetch(`${baseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(shop.owner_id)}`, {
        method: "PATCH",
        headers: { ...adminHeaders, Prefer: "return=minimal" },
        body: JSON.stringify({ account_role: "seller" }),
      });
      if (!profileUpdate.ok) return json(502, { error: "seller_role_update_failed" });
    }
    return json(200, { status: "updated", shop_id: shopId, shop_status: status });
  } catch {
    return json(500, { error: "unexpected_server_error" });
  }
});
