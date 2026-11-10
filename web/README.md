# MALRO Admin Web

Independent responsive web client for the MALRO admin role.

## Run
1. Copy `.env.example` to `.env.local`.
2. Set the Supabase project URL and the public publishable/anon key. Never use a service-role key in this browser app.
3. Run `npm install` and `npm run dev`.
4. Deploy this branch as a separate Vercel project with Root Directory `web` and the same three environment variables.

This client enforces role checks in the UI, but database RLS and trusted Edge Functions remain the actual security boundary. Realtime updates require the relevant public tables to be enabled in the Supabase Realtime publication.
