import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const allowedOrigin = "https://malro-admin-web.onrender.com";
const cors = {
  "Access-Control-Allow-Origin": allowedOrigin,
  "Access-Control-Allow-Headers": "apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });

async function sha256(value: string) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map(x => x.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);
  if (req.headers.get("origin") !== allowedOrigin) return reply({ error: "Origin not allowed" }, 403);

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !serviceKey) return reply({ error: "Server configuration incomplete" }, 500);

  let body: { password?: unknown };
  try { body = await req.json(); } catch { return reply({ error: "Invalid request" }, 400); }
  const supplied = typeof body.password === "string" ? body.password : "";
  if (supplied.length < 5 || supplied.length > 100) return reply({ error: "पासवर्ड गलत है।" }, 401);

  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim()
    || req.headers.get("x-real-ip") || "unknown";
  const ipHash = await sha256(ip);
  const headers = {
    apikey: serviceKey,
    Authorization: "Bearer " + serviceKey,
    "Content-Type": "application/json",
  };

  const insertAttempt = await fetch(url + "/rest/v1/malro_admin_login_attempts", {
    method: "POST", headers: { ...headers, Prefer: "return=minimal" },
    body: JSON.stringify({ ip_hash: ipHash }),
  });
  if (!insertAttempt.ok) return reply({ error: "Login service unavailable" }, 503);

  const since = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const attemptsRes = await fetch(
    url + "/rest/v1/malro_admin_login_attempts?select=id&ip_hash=eq." +
    encodeURIComponent(ipHash) + "&created_at=gte." + encodeURIComponent(since),
    { headers }
  );
  if (!attemptsRes.ok) return reply({ error: "Login service unavailable" }, 503);
  const attempts = await attemptsRes.json();
  if (attempts.length > 5) return reply({ error: "बहुत अधिक प्रयास। 10 मिनट बाद फिर कोशिश करें।" }, 429);

  const verify = await fetch(url + "/rest/v1/rpc/verify_malro_admin_base_password", {
    method: "POST", headers, body: JSON.stringify({ candidate: supplied }),
  });
  if (!verify.ok || await verify.json() !== true) return reply({ error: "पासवर्ड गलत है।" }, 401);

  const adminEmail = "malro-admin@malro.app";
  const usersRes = await fetch(url + "/auth/v1/admin/users?page=1&per_page=1000", { headers });
  if (!usersRes.ok) return reply({ error: "Admin account lookup failed" }, 503);
  const usersData = await usersRes.json();
  let user = (usersData.users ?? []).find((u: { email?: string; app_metadata?: { role?: string } }) =>
    u.email?.toLowerCase() === adminEmail
  );

  if (user && user.app_metadata?.role !== "admin") {
    return reply({ error: "Admin account security check failed" }, 403);
  }

  if (!user) {
    const randomPassword = Array.from(crypto.getRandomValues(new Uint8Array(32)))
      .map(x => x.toString(16).padStart(2, "0")).join("");
    const createRes = await fetch(url + "/auth/v1/admin/users", {
      method: "POST", headers,
      body: JSON.stringify({
        email: adminEmail, password: randomPassword, email_confirm: true,
        app_metadata: { role: "admin" }, user_metadata: { display_name: "MALRO Admin" },
      }),
    });
    if (!createRes.ok) return reply({ error: "Admin account could not be created" }, 503);
  }

  const linkRes = await fetch(url + "/auth/v1/admin/generate_link", {
    method: "POST", headers,
    body: JSON.stringify({ type: "magiclink", email: adminEmail }),
  });
  const linkData = await linkRes.json();
  const tokenHash = linkData?.properties?.hashed_token ?? linkData?.hashed_token;
  if (!linkRes.ok || !tokenHash) return reply({ error: "Secure login session could not be issued" }, 503);

  return reply({ token_hash: tokenHash, type: "magiclink" });
});
