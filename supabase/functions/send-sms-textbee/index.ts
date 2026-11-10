import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

type SmsHookPayload = {
  user?: { phone?: string };
  sms?: { otp?: string };
};

const json = (status: number, body: Record<string, string>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json(405, { error: "method_not_allowed" });
  }

  const hookSecret = Deno.env.get("SEND_SMS_HOOK_SECRET");
  const apiKey = Deno.env.get("TEXTBEE_API_KEY");
  const deviceId = Deno.env.get("TEXTBEE_DEVICE_ID");

  if (!hookSecret || !apiKey || !deviceId) {
    return json(500, { error: "sms_gateway_not_configured" });
  }

  try {
    const rawBody = await req.text();
    const secret = hookSecret.replace(/^v1,whsec_/, "");
    const webhook = new Webhook(secret);
    const payload = webhook.verify(
      rawBody,
      Object.fromEntries(req.headers.entries()),
    ) as SmsHookPayload;

    const phone = payload.user?.phone;
    const otp = payload.sms?.otp;
    if (!phone || !/^\+[1-9]\d{7,14}$/.test(phone) || !otp || !/^\d{4,8}$/.test(otp)) {
      return json(400, { error: "invalid_sms_hook_payload" });
    }

    const smsResponse = await fetch(
      "https://api.textbee.dev/api/v1/gateway/send-sms",
      {
        method: "POST",
        headers: {
          "x-api-key": apiKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          deviceId,
          recipients: [phone],
          message: `MALRO verification code: ${otp}. Do not share this code with anyone.`,
        }),
        signal: AbortSignal.timeout(15000),
      },
    );

    if (!smsResponse.ok) {
      // Do not log phone numbers, OTPs, API keys, or provider response bodies.
      return json(502, { error: "sms_provider_rejected_request" });
    }

    // A successful TextBee response means accepted/queued, not handset delivery.
    return new Response("{}", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch {
    // Never expose OTPs, phone numbers, secrets, or provider internals in errors.
    return json(400, { error: "sms_hook_verification_or_delivery_failed" });
  }
});
