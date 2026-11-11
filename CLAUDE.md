# CLAUDE.md — MALRO Project Memory

## Product
MALRO is a Barmer-first, multi-vendor marketplace for Rajasthan. It has four role-specific experiences:
- Customer: discover approved shops/products, cart, checkout, order tracking and support.
- Seller: shop onboarding, catalog, inventory, order acceptance and packing.
- Delivery partner: availability, assigned jobs, pickup, navigation, proof of delivery and earnings.
- Admin/operator: shop and delivery-partner approvals, catalog moderation, delivery assignment, disputes and operations.

The current launch intent is locality-configurable and Barmer-first. Do not introduce nationwide operational complexity without an explicit product decision. This repository is actively evolving and is **not yet production-ready**.

## Repository and branch boundaries
- Repository: `malaramofficial/MALRO`.
- `main`: shared contracts, architecture, backend migrations and integration documentation.
- `app/customer`, `app/seller`, `app/delivery`, `app/admin`: role-specific Android work.
- `web/customer`, `web/seller`, `web/delivery`, `web/admin`: role-specific web work.
- `admin-web`: legacy/alternate admin web branch; inspect before changing.
- Keep work on a task-specific branch. Do not force-push, rewrite history, or commit app-specific work to `main`. Integrate through reviewed pull requests.
- Preserve the existing independent app branch ownership. A web branch may contain shared repository files; inspect its tree before editing.

## Strict stack choices (current baseline)
Do not replace these technologies without an approved Architecture Decision Record:
- Android: Kotlin, Jetpack Compose, Gradle, Java 17 toolchain.
- Web: React 18, TypeScript 5, Vite 6, `@supabase/supabase-js` 2.
- Backend: Supabase PostgreSQL, Row Level Security (RLS), Supabase Auth, trusted Supabase Edge Functions (TypeScript/Deno).
- Hosting: existing web services are deployed on Render. Do not deploy to Vercel unless the product owner explicitly changes this decision.
- Database money values: integer paise, never floating-point rupees.
- Identity: trusted server-side role claims in JWT `app_metadata`; never authorize from user-editable `user_metadata`.
- Android clients may contain only the public Supabase URL and publishable/anon key, protected by RLS. Never embed service-role keys or other privileged credentials.
- Payments provider, maps provider, Redis, Sentry, FCM and background-job provider are not approved/confirmed by this baseline. Do not add them as assumed dependencies.

## Product and security invariants
1. The server/database is the source of truth for roles, permissions, prices, stock, totals, payment state, order state, delivery assignment and audit history.
2. Every endpoint/function must validate its input, authenticate the caller where required, authorize the requested action/resource server-side, return safe errors, and have tests before production use.
3. Prevent IDOR and cross-user/cross-shop access. RLS is a security boundary, not a substitute for explicit server-side authorization in privileged operations.
4. Tenant isolation is mandatory for tenant-owned data. The canonical organization/tenant entity and the meaning of `organization_id` must be decided in Layer 1 before adding or retrofitting columns. Do not blindly add an organization column to global/platform data or equate a shop with an organization without approval. Once decided, every tenant-owned query and write must be scoped and tested.
5. Every database schema change must be a reviewed, ordered SQL migration under `backend/migrations/`; do not make undocumented dashboard-only schema changes. Include rollback/forward-recovery notes where practical.
6. New tenant-owned records should use UUID primary keys, explicit ownership, indexes for real query paths, and soft-delete semantics where appropriate. Existing production tables must not be destructively reshaped without a migration and data/backfill plan.
7. Store money in integer paise. Keep private documents and proof-of-delivery files in private storage with narrow policies.
8. Collect precise location only for a disclosed, user-permitted delivery purpose; stop collection when the purpose ends.
9. Record sensitive state transitions in an audit/event trail. Never expose another user's phone, address, location or proof unnecessarily.
10. Never commit secrets, access tokens, service-role keys, signing keys, private credentials, production data, or populated local environment files. Use placeholders in `.env.example`; rotate any credential that is exposed.
11. Never weaken RLS, disable tests, hide build failures, or claim a feature is verified without actual evidence.

## Working loop — required for every task
1. **Plan first:** write a short implementation plan with scope, files/systems, risks, migration/API implications and verification steps. Do not start implementation before reviewing the plan.
2. **Review plan:** check it against product invariants, existing architecture and branch ownership. Ask the product owner only for decisions that materially change behavior or data/security design.
3. **Isolate work:** use a task-specific branch; make small, reviewable commits. Do not modify unrelated apps/branches.
4. **Implement incrementally:** schema changes first through migrations; backend validation/authorization before UI wiring; preserve compatibility.
5. **Verify:** run available lint, typecheck, tests and builds. Add missing checks rather than pretending they exist. Never skip or alter tests to make a task pass.
6. **Review diff:** inspect changed files, generated artifacts, permissions, secrets, migration order and unintended changes before opening/merging a PR.
7. **Report proof:** show exact commands and actual outputs, identify checks that could not run, and distinguish committed, deployed and device-tested states.

## Verified repository commands (as of 2026-11-11)
### Web client
Run from the role-specific branch's `web/` directory:
- `npm install`
- `npm run dev`
- `npm run build` (runs `tsc -b && vite build`)
- `npm run preview`

The inspected delivery web package currently defines no dedicated lint or test script. Do not claim those checks ran. Add project-approved lint/test tooling in the appropriate layer before relying on it.

### Android app
Run from an Android app branch root where `:app` is included:
- `gradle --no-daemon :app:assembleDebug`

The current GitHub Actions workflow installs Gradle 8.9 and Java 17 before running this command. No Gradle wrapper was present in the inspected delivery app root. Do not assume a wrapper exists on every branch.

### Database and Edge Functions
- SQL migration source lives under `backend/migrations/`.
- Edge Function source lives under `supabase/functions/<function-name>/`.
- No repository-wide Supabase CLI, Deno, migration-apply, lint or test command was verified in the inspected root. Do not invent one or apply SQL directly to production as a substitute for a reviewed migration.
- Before running backend commands, inspect the relevant branch, installed toolchain and project configuration; document the exact verified command.

## Current known limitations / unresolved decisions
- The live Supabase project and current production schema may be ahead of the `main` branch migration history. Inspect both before database work; reconcile drift rather than assuming Git is the full truth.
- Existing order/role flows have evolved beyond the initial schema/docs. Re-read current database definitions and the target branch before modifying them.
- No payment provider, maps provider, FCM setup, Redis, Sentry, or production background-job service is confirmed by this baseline.
- The exact tenancy model, organization ownership, multiple shops per seller, seller staff, delivery-partner affiliation, retention/deletion policy, launch scale/SLOs and compliance policy require Layer 1 decisions.
- Existing UI and API implementations may be partial/demo in some branches. Verify end-to-end behavior before describing it as production-ready.
