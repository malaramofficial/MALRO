# MALRO backend setup

## Decision
The four Android clients share one backend and one canonical order lifecycle. The repository contains a Supabase/Postgres schema draft in `backend/migrations/0001_initial_schema.sql`; it has **not** been applied to any database.

## Why it is not deployed automatically
The connected Supabase account currently has other projects, but none is identified as the MALRO production backend. Reusing an unrelated project could corrupt another app's data. Creating a new project can incur charges, so the project must be explicitly selected and any quoted cost accepted first.

## Backend setup sequence
1. Create/select a dedicated MALRO Supabase project in the India region (Mumbai `ap-south-1`).
2. Review and apply the initial schema migration.
3. Configure phone OTP auth and trusted server-side role assignment using JWT `app_metadata`.
4. Add Edge Functions for checkout/order creation, payment webhook verification, seller approval, delivery assignment, OTP completion, and status transitions.
5. Configure private product/proof storage buckets and storage policies.
6. Add only the Supabase project URL and publishable key to Android build configuration. Never put a service-role/secret key in any APK.
7. Connect the four apps to the same API and test RLS with customer, seller, delivery and admin accounts.

## Important safeguards
- All money is stored as integer paise.
- Client apps cannot write payment success, order totals, order transitions, delivery assignments or audit events directly.
- Product price and stock must be revalidated server-side at checkout.
- GPS is shared only while the delivery partner is online / handling an active job.
- Admin role must be set by trusted server-side tooling, never from user-editable metadata.
- Current Android screens are interactive local prototypes and use sample data; they do not place real orders or process payments.
