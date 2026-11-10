# MALRO Customer App

Native Android customer marketplace app for Barmer, built with Kotlin and Jetpack Compose.

## Customer flows
- Supabase phone OTP sign-in with encrypted local session storage and token refresh
- Customer profile read/create/update
- Live approved-shop product catalogue, search and category filtering
- Stock-aware persistent local cart, one shop per checkout
- Delivery address capture with shop-locality serviceability validation
- Cash-on-delivery checkout with server-authoritative price and stock validation
- Customer order history, status refresh and cancellation before shop acceptance
- Logout and profile management

## Backend
- Shared MALRO Supabase project
- Products are visible only when active and attached to an approved shop.
- Checkout recalculates price, locks stock rows, decrements stock, writes order/item snapshots and creates the initial order event in one transaction.
- Customers can cancel only while an order is still placed and payment is pending; cancellation restores stock transactionally.
- Customer order, item and event visibility is protected by RLS.

## Current operational dependencies and limits
- Seller/Admin apps must approve shops and publish real products before the customer catalogue can show inventory. Current product and shop tables are empty.
- Initial serviceability is an exact match between the delivery locality and the shop's configured locality.
- Delivery fee is currently ₹0 because no fee/radius configuration exists yet.
- Online payment, live delivery GPS, refund workflow and a configured support contact are not enabled yet.
- Current Android CI builds a debug APK. A signed release requires a release keystore and protected CI secrets.

## Build
GitHub Actions workflow: .github/workflows/android-build.yml

## Web testing rollout

This branch is the live browser testing client for the MALRO role. It shares the MALRO Supabase project with the other role clients; native APK release is intentionally deferred until end-to-end testing is complete.
