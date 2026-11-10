import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

const json = (status: number, error: string) =>
  new Response(JSON.stringify({ error }), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json(405, "method_not_allowed");
  }

  const hookSecret = Deno.env.get("SEND_SMS_HOOK_SECRET");
  const apiKey = Deno.env.get("TEXTBEE_API_KEY");
  const deviceId = Deno.env.get("TEXTBEE_DEVICE_ID");

  if (!hookSecret || !apiKey || !deviceId) {
    console.error("sms_config_missing");
    return json(500, "sms_gateway_not_configured");
  }

  let payload: Record<string, any>;

  try {
    const rawBody = await req.text();
    const secret = hookSecret.replace(/^v1,whsec_/, "");
    const webhook = new Webhook(secret);

    payload = webhook.verify(
      rawBody,
      Object.fromEntries(req.headers.entries()),
    ) as Record<string, any>;
  } catch {
    console.error("sms_hook_signature_verification_failed");
    return json(401, "sms_hook_signature_invalid");
  }

  const rawPhone =
    payload?.user?.phone ??
    payload?.sms?.phone;

  const otp = payload?.sms?.otp;

  if (
    typeof rawPhone !== "string" ||
    (typeof otp !== "string" && typeof otp !== "number")
  ) {
    console.error("sms_payload_missing_fields");
    return json(400, "invalid_sms_hook_payload");
  }

  let phone = rawPhone.trim().replace(/[\s()-]/g, "");

  if (/^\d{10}$/.test(phone)) {
    phone = "+91" + phone;
  } else if (/^91\d{10}$/.test(phone)) {
    phone = "+" + phone;
  } else if (/^0\d{10}$/.test(phone)) {
    phone = "+91" + phone.slice(1);
  }

  const otpText = String(otp);

  if (
    !/^\+[1-9]\d{7,14}$/.test(phone) ||
    !/^\d{4,8}$/.test(otpText)
  ) {
    console.error("sms_payload_format_invalid", JSON.stringify({
      phone_length: phone.length,
      phone_format_valid: /^\+[1-9]\d{7,14}$/.test(phone),
      otp_length: otpText.length,
    }));
    return json(400, "invalid_phone_or_otp_format");
  }

  try {
    const response = await fetch(
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
          message: `MALRO login code: ${otpText}. Do not share this code with anyone.`,
        }),
        signal: AbortSignal.timeout(15000),
      },
    );

    if (!response.ok) {
      console.error("textbee_rejected_request", response.status);
      return json(502, "sms_provider_rejected_request");
    }

    console.log("sms_provider_accepted_request");
    return new Response(JSON.stringify({}), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });

  } catch {
    console.error("textbee_network_error");
    return json(502, "sms_provider_unreachable");
  }
});
