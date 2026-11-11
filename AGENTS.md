# AGENTS.md — Instructions for all coding agents

Read `CLAUDE.md` before planning or changing this repository. These rules apply to every agent, coding assistant and human contributor.

## Product context
MALRO is a Barmer-first multi-vendor marketplace with Customer, Seller, Delivery and Admin experiences. Preserve the existing role boundaries, branch strategy and locality-first rollout. The repository is evolving; do not describe it as production-ready without verified evidence.

## Approved technology baseline
- Android: Kotlin + Jetpack Compose + Gradle + Java 17.
- Web: React 18 + TypeScript 5 + Vite 6 + Supabase JS.
- Backend: Supabase PostgreSQL + RLS + Auth + trusted TypeScript/Deno Edge Functions.
- Web hosting: existing Render services. No Vercel deployment without explicit approval.
- Money: integer paise.
- Authorization: trusted server-side claims and resource-level checks; never trust client role flags or user-editable metadata.

Do not introduce a new framework, database, queue, cache, payment provider, maps provider, notification provider or observability vendor without a documented decision and product-owner approval.

## Mandatory engineering rules
- Plan before coding; review the plan before implementation.
- Work on a separate task branch and respect app/web branch ownership.
- Never force-push or rewrite shared history.
- Never commit secrets, tokens, service-role keys, populated environment files or production personal data.
- Every endpoint/function requires input validation, authentication where applicable, resource authorization, safe errors and tests.
- The backend is the source of truth for all business-critical state.
- RLS must remain enabled on protected tables. Test tenant isolation and IDOR boundaries.
- Tenant-owned data must be scoped by the approved canonical `organization_id`/tenant model. Do not invent that model or blindly retrofit global tables; settle it in Layer 1 first.
- Every DB change requires a reviewed migration in `backend/migrations/`; include indexes, UUIDs, integer money, ownership, and soft-delete design where applicable.
- Private files require private buckets and least-privilege storage policies.
- Keep location collection explicit, purpose-limited and time-bounded.
- Never disable, delete, weaken or rewrite tests to hide a failure.
- Never claim lint, typecheck, tests, deployment or device verification unless the command/action actually succeeded.

## Required task workflow
1. Plan scope, risks, affected branches/files, migration/API impact and checks.
2. Review plan against this file and `CLAUDE.md`.
3. Implement in small commits on an isolated branch.
4. Run available lint, typecheck, tests and build; add missing coverage as part of the approved scope.
5. Review the complete diff for security, secrets, data migration safety and unrelated changes.
6. Report exact commands, actual outputs, remaining failures and what is/isn't deployed.

## Known commands
Web (from the relevant `web/` directory): `npm install`, `npm run dev`, `npm run build`, `npm run preview`.
Android (from an app branch root): `gradle --no-daemon :app:assembleDebug` (CI installs Java 17 and Gradle 8.9).
Database migrations are SQL files under `backend/migrations/`; Edge Functions are under `supabase/functions/`. No standard migration, Deno lint, test or Supabase CLI command has yet been verified repository-wide. Inspect configuration before running or documenting additional commands.
