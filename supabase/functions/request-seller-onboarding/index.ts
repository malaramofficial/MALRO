import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: corsHeaders });
const hashSecret = async (value: string) => {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
};
const sameHash = (a: string, b: string) => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
};
const randomHex = (size: number) => {
  const bytes = crypto.getRandomValues(new Uint8Array(size));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("").toUpperCase();
};
const clean = (value: unknown, max: number) =>
  typeof value === "string" ? value.trim().slice(0, max) : "";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) return json(500, { error: "server_configuration_missing" });

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return json(400, { error: "invalid_json" }); }
  const action = body.action;
  const admin = createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });

  try {
    if (action === "submit") {
      const full_name = clean(body.full_name, 100);
      const shop_name = clean(body.shop_name, 120);
      const description = clean(body.description, 1000);
      const address = clean(body.address, 500);
      const locality = clean(body.locality, 100) || "Barmer";
      let phone = clean(body.phone, 20).replace(/[\s-]/g, "");
      if (phone && /^[6-9][0-9]{9}$/.test(phone)) phone = "+91" + phone;
      if (phone && !/^\+91[6-9][0-9]{9}$/.test(phone)) {
        return json(400, { error: "मोबाइल नंबर वैकल्पिक है; यदि भरें तो सही 10 अंकों का भारतीय नंबर दें।" });
      }
      if (full_name.length < 2 || shop_name.length < 2 || address.length < 5) {
        return json(400, { error: "विक्रेता का नाम, दुकान का नाम और पूरा पता भरना आवश्यक है।" });
      }

      const application_code = "MLR-" + randomHex(5);
      const claim_secret = randomHex(24);
      const claim_secret_hash = await hashSecret(claim_secret);
      const { data, error } = await admin.from("seller_applications").insert({
        application_code, claim_secret_hash, full_name, phone: phone || null,
        shop_name, description: description || null, address, locality, status: "pending",
      }).select("id,application_code").single();
      if (error || !data) return json(400, { error: error?.message ?? "आवेदन जमा नहीं हो सका।" });
      return json(200, {
        ok: true, application_id: data.id, application_code,
        claim_secret, status: "pending",
        message: "आवेदन जमा हो गया है। आवेदन कोड और सुरक्षा कोड सुरक्षित रखें।",
      });
    }

    if (action === "status") {
      const application_code = clean(body.application_code, 20).toUpperCase();
      const claim_secret = clean(body.claim_secret, 100).toUpperCase();
      if (!/^MLR-[A-F0-9]{10}$/.test(application_code) || !/^[A-F0-9]{48}$/.test(claim_secret)) {
        return json(400, { error: "आवेदन कोड या सुरक्षा कोड सही नहीं है।" });
      }
      const { data: application, error } = await admin.from("seller_applications")
        .select("application_code,status,seller_code,claim_secret_hash,setup_completed_at,admin_note")
        .eq("application_code", application_code).maybeSingle();
      if (error || !application) return json(404, { error: "आवेदन नहीं मिला।" });
      const suppliedHash = await hashSecret(claim_secret);
      if (!sameHash(suppliedHash, application.claim_secret_hash || "")) {
        return json(404, { error: "आवेदन कोड या सुरक्षा कोड सही नहीं है।" });
      }
      return json(200, {
        ok: true, status: application.status, seller_code: application.seller_code,
        setup_completed: Boolean(application.setup_completed_at),
        admin_note: application.status === "rejected" ? application.admin_note : null,
      });
    }

    return json(400, { error: "unsupported_action" });
  } catch {
    return json(500, { error: "unexpected_server_error" });
  }
});