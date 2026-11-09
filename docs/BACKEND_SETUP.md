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

The Supabase migration history confirms both migrations were applied. The seven application tables have RLS enabled. Privileged writes (orders, payment state, delivery assignment and audit events) remain unavailable to Android clients and must be implemented through trusted Edge Functions.

## Android integration status
All four app branches (`app/customer`, `app/seller`, `app/delivery`, `app/admin`) now include:
- The same MALRO Supabase project URL.
- A public anon API key, suitable for client apps and constrained by RLS.
- A startup connectivity check that reads only approved shop IDs from the public REST API.
- No service-role key or privileged database credentials.

This is a public read/connectivity integration only. It does **not** yet implement user authentication, private seller/delivery/admin data, product CRUD, order creation, payments, delivery GPS, or admin actions. The four apps still contain local demo interactions until those modules are implemented.

## Required next steps
1. Configure and test an authentication provider. Phone OTP requires an SMS provider and credentials; do not assume SMS is configured.
2. Implement sign-in/session handling. Customer and seller can use user sign-up; delivery and admin accounts must be provisioned and assigned trusted roles by an administrator.
3. Build and deploy authenticated Edge Functions for create-order, seller-order-action, assign-delivery, delivery-action, approve-shop, cancel-order and payment-webhook.
4. Add private storage buckets and narrowly scoped storage policies for seller documents and delivery proof.
5. Replace sample data in each app with authenticated Supabase reads and server-mediated writes.
6. Test RLS and workflow transitions using separate customer, seller, delivery and admin test accounts before any public launch.

## Launch safeguards
- All monetary amounts are stored as integer paise.
- The client cannot set its own role, payment success, order total, order status, stock reservation or delivery assignment.
- Prices and inventory must be revalidated atomically at checkout.
- GPS is shared only while a delivery partner is online or handling an active job.
- Barmer remains the initial service area; MALRO operates its own delivery partners.
