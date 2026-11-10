# MALRO Delivery App

Android application for MALRO's own delivery operations in Barmer.

## MVP screens
1. Delivery partner sign-in and onboarding status
2. Online / offline availability
3. Assigned delivery queue
4. Pickup details and navigation hand-off
5. Pickup confirmation
6. Customer hand-off with OTP / proof of delivery
7. Delivery history and earnings summary
8. Help and incident reporting

## Rules
- Location is collected only while the partner is online or handling an active job, with clear disclosure.
- Assignment and status changes are validated by the backend.
- Customer phone/address details are revealed only for the assigned delivery and only as needed.
- Proof-of-delivery data is access-controlled and audited.

## Implementation
Kotlin + Jetpack Compose. Shared UI and domain contracts are coordinated through `main`.

## Web testing rollout

This branch is the live browser testing client for the MALRO role. It shares the MALRO Supabase project with the other role clients; native APK release is intentionally deferred until end-to-end testing is complete.
