# MALRO Seller App

Android application for local Barmer merchants.

## MVP screens
1. Seller sign-in and shop onboarding
2. Shop profile and operating area
3. KYC / verification status
4. Product catalog, photos, variants and pricing
5. Inventory and stock availability
6. New orders and accept/reject
7. Packing and ready-for-pickup workflow
8. Sales summary and support

## Rules
- Seller access is granted only after server-side approval.
- Seller can modify only their own shop, products and orders.
- Price and inventory changes are audited.
- Order acceptance and stock reservation must be transactional.

## Implementation
Kotlin + Jetpack Compose. Shared UI and domain contracts are coordinated through `main`.
