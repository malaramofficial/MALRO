import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string

if (!url || !key) console.warn('MALRO web env missing: VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY')

// Browser clients must only use the publishable/anon key. Never place service_role here.
export const supabase = createClient(url || 'https://example.supabase.co', key || 'public-anon-key', {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
})
