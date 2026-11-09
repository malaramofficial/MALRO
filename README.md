# MALRO Customer App

Android application for customers in Barmer.

## MVP screens
1. Splash, language selection and onboarding
2. Phone sign-in / account
3. Home with local categories, search and serviceability
4. Product list, filters and product details
5. Cart and address selection
6. Checkout and payment hand-off
7. Order tracking and order history
8. Profile, help and support

## Rules
- Show products only for serviceable localities.
- Confirm stock and price on the server at checkout.
- Never mark an order paid based only on a client response.
- Ask for location permission only when the user chooses location-based address or tracking.
- Provide clear empty, loading, offline and error states.

## Implementation
Kotlin + Jetpack Compose. Shared UI and domain contracts are coordinated through `main`.
