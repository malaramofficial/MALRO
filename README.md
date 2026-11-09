# MALRO Admin App

Restricted operations application for authorized MALRO administrators.

## MVP screens
1. Admin sign-in with strong session controls
2. Operations overview
3. Seller applications and approval
4. Category and product moderation
5. Orders and exception handling
6. Delivery partner and assignment management
7. Refunds, disputes and support cases
8. Service area, fees and operating configuration
9. Audit log and reports

## Rules
- Every privileged action requires server-side role and permission checks.
- High-impact actions require confirmation and create immutable audit events.
- Never embed admin secrets or privileged service keys in the Android app.
- Use least-privilege access and session expiry.

## Implementation
Kotlin + Jetpack Compose. Shared UI and domain contracts are coordinated through `main`.
