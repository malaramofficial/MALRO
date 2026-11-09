# MALRO Architecture & Delivery Plan

## Product boundary
MALRO is a Barmer-first multi-vendor marketplace. The first release supports general product categories, seller-managed catalogs, platform-managed order orchestration and own delivery operations.

## Four independently shipped Android apps
| App | Primary roles | Core responsibilities |
|---|---|---|
| Customer | Customer | Browse/search, product details, cart, checkout, orders, address book, support |
| Seller | Seller/shop staff | Onboarding, KYC review status, catalog, pricing, stock, order accept/pack/handover |
| Delivery | Delivery partner | Availability, assigned jobs, pickup, navigation, OTP/proof of delivery, earnings |
| Admin | Platform operator | Seller approval, category/catalog moderation, order oversight, delivery assignment, refunds/disputes, reports, configuration |

## Shared platform contracts
- Identity and role authorization are enforced server-side; hiding UI is not authorization.
- One canonical order state machine is shared by all apps.
- Inventory changes must be atomic to prevent overselling.
- Payment status comes from verified provider webhooks, not client claims.
- Delivery status transitions are audited with actor, timestamp and evidence.
- Personal data is minimized; precise location is collected only for an active delivery purpose.
- Admin actions require role checks and audit logs.

## Initial order lifecycle
`CART → PLACED → PAYMENT_PENDING/PAID → ACCEPTED → PACKED → ASSIGNED → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED`
Exception states: `REJECTED`, `CANCELLED`, `PAYMENT_FAILED`, `RETURN_REQUESTED`, `REFUNDED`. Final transitions and payment handling must be specified before implementation.

## Suggested repository layout
```
customer-app/       Android customer application
seller-app/         Android seller application
delivery-app/       Android delivery application
admin-app/          Android admin application
shared/
  core-model/
  core-network/
  core-database/
  design-system/
backend/
docs/
.github/workflows/
```

## Branch ownership
- `app/customer`: customer app only
- `app/seller`: seller app only
- `app/delivery`: delivery app only
- `app/admin`: admin app only
- `main`: shared contracts, integration and release coordination

## Delivery phases
1. Repository and architecture baseline.
2. Shared design system, authentication and API contracts.
3. MVP workflow for each app with mock/test backend.
4. Backend persistence, role security and payment integration.
5. End-to-end tests across customer → seller → delivery → admin.
6. Barmer pilot, monitoring, backups and operational readiness.

## Not yet decided / must not be guessed
Production backend host, database project, payment provider credentials, maps provider, legal entity/KYC policy, delivery fee rules, tax/invoice policy and exact launch localities.
