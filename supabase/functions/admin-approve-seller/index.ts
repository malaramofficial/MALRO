import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
const clean = (v: unknown, max: number) => typeof v === "string" ? v.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) return json({ error: "Server configuration missing" }, 500);
  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!token) return json({ error: "Admin login required" }, 401);

  const admin = createClient(supabaseUrl, serviceKey, { auth: { autoRefreshToken: false, persistSession: false } });
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  const adminUser = authData?.user;
  if (authError || !adminUser || adminUser.app_metadata?.role !== "admin") {
    return json({ error: "केवल अधिकृत Admin यह कार्य कर सकता है" }, 403);
  }

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const applicationId = clean(body.application_id, 64);
  const decision = body.decision === "rejected" ? "rejected" : "approved";
  const adminNote = clean(body.admin_note, 500) || null;
  if (!applicationId) return json({ error: "आवेदन ID आवश्यक है" }, 400);

  const { data: application, error: lookupError } = await admin.from("seller_applications")
    .select("id,application_code,status,seller_code")
    .eq("id", applicationId).maybeSingle();
  if (lookupError || !application) return json({ error: "आवेदन नहीं मिला" }, 404);
  if (application.status !== "pending") return json({ error: "यह आवेदन पहले ही संसाधित हो चुका है" }, 409);

  const sellerCode = decision === "approved"
    ? "MALRO-S-" + Array.from(crypto.getRandomValues(new Uint8Array(5)), b => b.toString(16).padStart(2, "0")).join("").toUpperCase()
    : null;

  const update = await admin.from("seller_applications").update({
    status: decision,
    seller_code: sellerCode,
    admin_note: adminNote,
    reviewed_at: new Date().toISOString(),
    reviewed_by: adminUser.id,
  }).eq("id", application.id).eq("status", "pending")
    .select("id,status,seller_code").maybeSingle();

  if (update.error || !update.data) return json({ error: update.error?.message ?? "आवेदन अपडेट नहीं हो सका" }, 409);
  return json({
    ok: true, status: decision, application_code: application.application_code,
    seller_code: update.data.seller_code,
    message: decision === "approved" ? "आवेदन स्वीकृत। Seller ID जारी कर दी गई है।" : "आवेदन अस्वीकृत कर दिया गया है।",
  });
});