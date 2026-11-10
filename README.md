# MALRO Customer App

Native Android customer marketplace app for Barmer, built with Kotlin and Jetpack Compose.

## Customer flows
- Secure Supabase phone OTP sign-in and encrypted local session storage
- Customer profile read/create/update
- Live approved-shop product catalogue, search and category filtering
- Persistent local cart, limited to one shop per checkout
- Delivery address capture and cash-on-delivery checkout
- Server-authoritative product price and stock validation
- Customer order history and order status display
- Logout and session refresh

## Backend
- Shared MALRO Supabase project
- Products are visible only when active and attached to an approved shop.
- Checkout uses public.place_customer_order; the server recalculates prices, locks stock rows, decrements stock, writes order/item snapshots and creates the initial order event in one transaction.
- Payment status remains pending for COD. The app never claims online payment is complete.
- Customer order and item/event visibility is protected by RLS.

## Current operational dependencies
- Seller/Admin apps must approve shops and publish real products before the customer catalogue can show inventory.
- Delivery fee is currently ₹0 because no fee/radius configuration exists yet.
- Online payment, delivery live tracking, cancellation/refund workflow and configured support contact are not enabled yet.
- Current Android CI produces a debug APK. A signed release build requires a release keystore and protected CI secrets.

## Build
GitHub Actions workflow: .github/workflows/android-build.yml
