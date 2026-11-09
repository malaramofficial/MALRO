# MALRO shared API contract (draft)

All four apps use one Supabase project. The Android clients must use only the project URL and publishable key. Privileged actions run in Edge Functions or other trusted server code.

## Roles
- `customer`: browse approved shops/products, manage own profile, view own orders.
- `seller`: manage only their own shop/products and act on orders for that shop.
- `delivery`: view only jobs assigned to them and submit permitted delivery progress.
- `admin`: approve/suspend shops, assign delivery, resolve exceptions and inspect audit history.

Role claims are written to JWT `app_metadata` only by trusted server-side tooling. A user cannot choose their own role in a signup form.

## Server functions
| Function | Caller | Required server checks |
|---|---|---|
| `create-order` | customer | Verify auth, approved shop, active products, current prices, stock, address and idempotency key; reserve/decrement stock atomically |
| `payment-webhook` | payment provider | Verify provider signature; idempotently record payment result; never trust client success callbacks |
| `seller-order-action` | seller | Verify shop ownership and allowed transition (accept/reject/packed) |
| `assign-delivery` | admin | Verify admin claim, active delivery partner, order state; write assignment audit event |
| `delivery-action` | assigned delivery | Verify assignment, allowed transition, pickup/delivery OTP and proof requirements |
| `approve-shop` | admin | Verify admin claim, required seller documents and decision; write audit event |
| `cancel-order` | customer/admin | Enforce cancellation window, payment/refund rules and stock restoration atomically |

## Canonical order lifecycle
`placed → accepted → packed → assigned → picked_up → delivered`

Allowed exception paths:
- `placed → cancelled`
- `accepted → cancelled` only when server policy permits
- `packed → cancelled` only by admin and with stock/payment reconciliation
- `delivered → refunded` only through an audited refund workflow

No Android client may directly update `orders.status`, `payment_status`, totals, assignment, inventory reservations or audit events.

## API response convention
Successful functions return JSON with `data` and a stable `request_id`. Errors return `error.code`, a safe human-readable `error.message`, and `request_id`. Never return stack traces, provider secrets, private documents or another user's personal data.

## Launch scope
Initial serviceability is limited to Barmer district/locality configuration. Delivery is operated by MALRO's own delivery partners. Category and product catalogue remain open to all supported legal product categories, subject to applicable rules and moderation.
