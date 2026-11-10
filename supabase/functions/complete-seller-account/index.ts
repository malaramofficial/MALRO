import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
const hashSecret = async (value: string) => {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
};
const sameHash = (a: string, b: string) => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
};
const clean = (v: unknown, max: number) => typeof v === "string" ? v.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) return json({ error: "Server configuration missing" }, 500);

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const applicationCode = clean(body.application_code, 20).toUpperCase();
  const claimSecret = clean(body.claim_secret, 100).toUpperCase();
  const sellerCode = clean(body.seller_code, 30).toUpperCase();
  const password = typeof body.password === "string" ? body.password : "";
  if (!/^MLR-[A-F0-9]{10}$/.test(applicationCode) || !/^[A-F0-9]{48}$/.test(claimSecret) ||
      !/^MALRO-S-[A-F0-9]{10}$/.test(sellerCode)) {
    return json({ error: "आवेदन कोड, सुरक्षा कोड या Seller ID सही नहीं है।" }, 400);
  }
  if (password.length < 8 || password.length > 72) {
    return json({ error: "पासवर्ड कम से कम 8 और अधिकतम 72 अक्षरों का होना चाहिए।" }, 400);
  }

  const admin = createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });
  const { data: application, error: lookupError } = await admin.from("seller_applications")
    .select("id,application_code,claim_secret_hash,full_name,phone,shop_name,description,address,locality,status,seller_code,auth_user_id,setup_completed_at,setup_claimed_at")
    .eq("application_code", applicationCode).maybeSingle();
  if (lookupError || !application) return json({ error: "आवेदन नहीं मिला।" }, 404);
  const suppliedHash = await hashSecret(claimSecret);
  if (!sameHash(suppliedHash, application.claim_secret_hash || "")) return json({ error: "आवेदन कोड या सुरक्षा कोड गलत है।" }, 404);
  if (application.status !== "approved" || application.seller_code !== sellerCode) {
    return json({ error: "Admin की स्वीकृति और सही Seller ID आवश्यक है।" }, 403);
  }
  if (application.setup_completed_at || application.auth_user_id) {
    return json({ error: "इस Seller ID का अकाउंट पहले ही बनाया जा चुका है। अब ID और Password से Login करें।" }, 409);
  }

  const staleBefore = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const { data: claim, error: claimError } = await admin.from("seller_applications")
    .update({ setup_claimed_at: new Date().toISOString() })
    .eq("id", application.id).eq("status", "approved").is("setup_completed_at", null)
    .or("setup_claimed_at.is.null,setup_claimed_at.lt." + staleBefore)
    .select("id").maybeSingle();
  if (claimError || !claim) return json({ error: "अकाउंट सेटअप पहले से चल रहा है। कुछ मिनट बाद फिर प्रयास करें।" }, 409);

  const email = "seller-" + sellerCode.toLowerCase() + "@malro.app";
  let userId: string | null = null;
  let shopId: string | null = null;
  try {
    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email, password, email_confirm: true,
      user_metadata: { full_name: application.full_name },
      app_metadata: { role: "seller", seller_code: sellerCode },
    });
    if (createError || !created.user) throw createError ?? new Error("Seller account नहीं बन पाया।");
    userId = created.user.id;

    const { error: profileError } = await admin.from("profiles").update({
      full_name: application.full_name,
      phone: application.phone,
      account_role: "seller",
      updated_at: new Date().toISOString(),
    }).eq("id", userId);
    if (profileError) throw profileError;

    const { data: shop, error: shopError } = await admin.from("shops").insert({
      owner_id: userId, name: application.shop_name,
      description: application.description, phone: application.phone,
      address: application.address, locality: application.locality,
      status: "approved",
    }).select("id").single();
    if (shopError || !shop) throw shopError ?? new Error("दुकान अकाउंट नहीं बन पाया।");
    shopId = shop.id;

    const { data: finished, error: finishError } = await admin.from("seller_applications")
      .update({ auth_user_id: userId, shop_id: shopId, setup_completed_at: new Date().toISOString(), setup_claimed_at: null })
      .eq("id", application.id).is("setup_completed_at", null)
      .select("id").maybeSingle();
    if (finishError || !finished) throw finishError ?? new Error("अकाउंट सेटअप पूरा नहीं हो सका।");

    return json({ ok: true, seller_code: sellerCode, message: "Seller अकाउंट बन गया। अब Seller ID और Password से Login करें।" });
  } catch (error) {
    if (shopId) await admin.from("shops").delete().eq("id", shopId);
    if (userId) await admin.auth.admin.deleteUser(userId);
    await admin.from("seller_applications").update({ setup_claimed_at: null })
      .eq("id", application.id).is("setup_completed_at", null);
    return json({ error: error instanceof Error ? error.message : "अकाउंट सेटअप असफल रहा।" }, 400);
  }
});