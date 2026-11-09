# MALRO — Barmer Marketplace

MALRO is a locally focused, multi-vendor marketplace for Barmer, Rajasthan.

## Product apps
- **Customer** — discover products, cart, checkout, orders and support.
- **Seller** — shop onboarding, catalog, inventory and order fulfilment.
- **Delivery** — assigned deliveries, navigation, proof of delivery and earnings.
- **Admin** — marketplace operations, approvals, service areas, disputes and reporting.

## Repository strategy
- `main`: shared architecture, product contracts, backend contracts and integration.
- `app/customer`, `app/seller`, `app/delivery`, `app/admin`: isolated app work branches.
- Each app branch must be integrated through reviewed pull requests; do not force-push or overwrite another app's work.

## Initial technical direction
- Android apps: Kotlin + Jetpack Compose.
- Shared Android code: Gradle modules for design system, networking, models and common utilities.
- Backend: API-first service with role-based authorization. The backend is the source of truth for orders, inventory, payments and delivery state.
- Payments: use a compliant payment provider; never store raw card or UPI credentials.
- Location: request only when needed for delivery workflows and with clear user permission.

## Local-first rollout
Launch in Barmer first. Keep serviceability, delivery charges, catalog visibility and operating hours configurable by locality. Avoid building nationwide complexity before the local workflow is proven.

## Build status
Repository setup started. The app branches and product specifications are being established; this is not yet a production-ready application.
