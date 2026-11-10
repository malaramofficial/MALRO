alter table public.seller_applications
  alter column phone drop not null;

alter table public.seller_applications
  add column if not exists seller_code text,
  add column if not exists claim_secret_hash text,
  add column if not exists setup_claimed_at timestamptz,
  add column if not exists setup_completed_at timestamptz;

create unique index if not exists seller_applications_seller_code_uidx
  on public.seller_applications (seller_code)
  where seller_code is not null;
