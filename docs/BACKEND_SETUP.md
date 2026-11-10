# MALRO backend setup and integration status

## Live project
- Dedicated Supabase project: MALRO
- Region: Mumbai, India (`ap-south-1`)
- Project reference: `nwjrxnrsmfqufbpjfrya`
- API URL: `https://nwjrxnrsmfqufbpjfrya.supabase.co`
- The Android apps use only the public anon client key. Never embed a service-role or secret key in an APK.

## Database migrations applied
1. `0001_initial_schema.sql` — profiles, shops, products, orders, order_items, delivery_jobs and order_events, with initial RLS.
2. `0002_optimize_rls_and_foreign_key_indexes.sql` — foreign-key indexes and tightened read policies.
3. `20261109164454_account_roles_and_profile_bootstrap.sql` — server-controlled account roles, automatic profile creation on Auth signup, and seller/product policy restrictions.

The Supabase migration history confirms all three migrations were applied. The seven application tables have RLS enabled. Security advisor returned no security lints after the role migration. Privileged writes (orders, payment state, delivery assignment and audit events) remain unavailable to Android clients and must be implemented through trusted Edge Functions.

## Account and role model
- New verified Auth users receive a `profiles` row automatically with `account_role = customer`.
- `account_role` values: `customer`, `seller_pending`, `seller`, `delivery`, `admin`.
- Users may edit their own display name and phone only. They cannot insert or change their own role.
- Seller shop registration is limited to users whose role was assigned server-side as `seller_pending` or `seller`.
- Product writes require an approved shop and the `seller` role.
- Admin authorization relies on trusted `app_metadata.role = admin` claims. Never use user-editable `user_metadata` for authorization.
- Delivery assignment and role changes must be performed by trusted server-side code. Do not expose service-role credentials to Android clients.

## Deployed Edge Functions
- `request-seller-onboarding` — requires a valid user JWT; moves an eligible customer profile to `seller_pending`. It does not approve the shop or grant seller product-write access.
- `admin-review-shop` — requires a valid JWT whose trusted `app_metadata.role` is `admin`; approves or rejects a shop. Approval also changes the owner's profile role to `seller`.
- Both functions use server-side environment credentials only. No service-role key is present in the APKs.
- Initial admin provisioning is still a manual, trusted setup step and must be completed before admin-only function calls can succeed.

Source code is versioned under `supabase/functions/` in this repository.

## Android integration status
All four app branches (`app/customer`, `app/seller`, `app/delivery`, `app/admin`) include:
- The same MALRO Supabase project URL.
- A public anon API key, suitable for client apps and constrained by RLS.
- A startup connectivity check that reads only approved shop IDs from the public REST API.
- No service-role key or privileged database credentials.

This is a public read/connectivity integration only. It does **not** yet implement user authentication, private seller/delivery/admin data, product CRUD, order creation, payments, delivery GPS, or full admin operations. The four apps still contain local demo interactions until those modules are implemented.

## Authentication setup status
- The chosen login method is phone number + OTP for customer and seller apps.
- Customer/Seller Android OTP client code is committed. Supabase Auth phone sign-in is not yet enabled/verified in the project.
- A real SMS provider (for example Twilio, Vonage, MessageBird, or another supported provider) and its credentials are required before live SMS delivery works. Provider selection, account creation and any paid usage require the owner's explicit approval.
- The apps must send phone numbers in E.164 format (India: `+91...`), request the OTP, verify it, and store refreshable sessions securely.
- Delivery and admin accounts must be provisioned and assigned trusted roles; public self-registration must never grant these roles.

## Required next steps
1. Enable phone sign-in and configure an approved SMS provider after cost and credentials are agreed.
2. Verify the Customer/Seller OTP builds, add logout UI and role-aware seller onboarding/approval screens, then run end-to-end tests after an SMS provider is approved and configured.
3. Implement seller onboarding UI and connect it to `request-seller-onboarding`; connect the admin review UI to `admin-review-shop`.
4. Bootstrap the first admin identity through a trusted setup path, then provision delivery identities separately.
5. Build and deploy authenticated Edge Functions for create-order, seller-order-action, assign-delivery, delivery-action, cancel-order and payment-webhook.
6. Add private storage buckets and narrowly scoped storage policies for seller documents and delivery proof.
7. Replace sample data in each app with authenticated Supabase reads and server-mediated writes.
8. Test RLS and workflow transitions using separate customer, seller, delivery and admin test accounts before any public launch.

## Launch safeguards
- All monetary amounts are stored as integer paise.
- The client cannot set its own role, payment success, order total, order status, stock reservation or delivery assignment.
- Prices and inventory must be revalidated atomically at checkout.
- GPS is shared only while a delivery partner is online or handling an active job.
- Barmer remains the initial service area; MALRO operates its own delivery partners.
