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
- Admin authorization continues to rely on trusted `app_metadata.role = admin` claims. Never use user-editable `user_metadata` for authorization.
- Delivery assignment and role changes must be performed by trusted server-side code. Do not expose service-role credentials to Android clients.

## Android integration status
All four app branches (`app/customer`, `app/seller`, `app/delivery`, `app/admin`) now include:
- The same MALRO Supabase project URL.
- A public anon API key, suitable for client apps and constrained by RLS.
- A startup connectivity check that reads only approved shop IDs from the public REST API.
- No service-role key or privileged database credentials.

This is a public read/connectivity integration only. It does **not** yet implement user authentication, private seller/delivery/admin data, product CRUD, order creation, payments, delivery GPS, or admin actions. The four apps still contain local demo interactions until those modules are implemented.

## Authentication setup status
- The chosen login method is phone number + OTP for customer and seller apps.
- Supabase Auth phone OTP API flow is documented, but phone sign-in is not yet enabled/verified in the project.
- A real SMS provider (for example Twilio, Vonage, MessageBird, or another supported provider) and its credentials are required before live SMS delivery works. Provider selection, account creation and any paid usage require the owner's explicit approval.
- The apps must send phone numbers in E.164 format (India: `+91...`), request the OTP, verify it, and store refreshable sessions securely.
- Delivery and admin accounts must be provisioned and assigned trusted roles; public self-registration must never grant these roles.

## Required next steps
1. Enable phone sign-in and configure an approved SMS provider after cost and credentials are agreed.
2. Implement OTP request/verification, secure session persistence and logout in customer and seller apps.
3. Implement seller onboarding and trusted admin approval; provision delivery/admin identities separately.
4. Build and deploy authenticated Edge Functions for create-order, seller-order-action, assign-delivery, delivery-action, approve-shop, cancel-order and payment-webhook.
5. Add private storage buckets and narrowly scoped storage policies for seller documents and delivery proof.
6. Replace sample data in each app with authenticated Supabase reads and server-mediated writes.
7. Test RLS and workflow transitions using separate customer, seller, delivery and admin test accounts before any public launch.

## Launch safeguards
- All monetary amounts are stored as integer paise.
- The client cannot set its own role, payment success, order total, order status, stock reservation or delivery assignment.
- Prices and inventory must be revalidated atomically at checkout.
- GPS is shared only while a delivery partner is online or handling an active job.
- Barmer remains the initial service area; MALRO operates its own delivery partners.
