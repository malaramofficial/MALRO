-- MALRO auth foundation: default customer profiles and server-controlled account roles.
do $$ begin
  create type public.account_role as enum ('customer','seller_pending','seller','delivery','admin');
exception when duplicate_object then null;
end $$;

alter table public.profiles
  add column if not exists account_role public.account_role not null default 'customer';

create schema if not exists private;
revoke all on schema private from public;

create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, full_name, phone)
  values (
    new.id,
    nullif(new.raw_user_meta_data ->> 'full_name', ''),
    new.phone
  )
  on conflict (id) do update
    set phone = coalesce(public.profiles.phone, excluded.phone),
        updated_at = now();
  return new;
end;
$$;

revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created_malro on auth.users;
create trigger on_auth_user_created_malro
after insert on auth.users
for each row execute function private.handle_new_auth_user();

insert into public.profiles (id, full_name, phone)
select
  u.id,
  nullif(u.raw_user_meta_data ->> 'full_name', ''),
  u.phone
from auth.users u
on conflict (id) do nothing;

-- A user may edit only their own display fields; account_role is server-controlled.
revoke insert, update on public.profiles from authenticated;
grant update (full_name, phone) on public.profiles to authenticated;

-- Only users moved into seller onboarding by trusted server code may register a shop.
drop policy if exists "seller creates own shop" on public.shops;
create policy "seller onboarding creates own shop" on public.shops
for insert to authenticated
with check (
  owner_id = (select auth.uid())
  and exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.account_role in ('seller_pending','seller')
  )
);

-- Product creation requires an approved shop and a server-approved seller role.
drop policy if exists "seller creates products in own shop" on public.products;
create policy "approved seller creates products" on public.products
for insert to authenticated
with check (
  exists (
    select 1 from public.shops s
    join public.profiles p on p.id = (select auth.uid())
    where s.id = shop_id
      and s.owner_id = (select auth.uid())
      and s.status = 'approved'
      and p.account_role = 'seller'
  )
);

drop policy if exists "seller updates products in own shop" on public.products;
create policy "approved seller updates own products" on public.products
for update to authenticated
using (
  exists (
    select 1 from public.shops s
    join public.profiles p on p.id = (select auth.uid())
    where s.id = shop_id
      and s.owner_id = (select auth.uid())
      and p.account_role = 'seller'
  )
  or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
)
with check (
  exists (
    select 1 from public.shops s
    join public.profiles p on p.id = (select auth.uid())
    where s.id = shop_id
      and s.owner_id = (select auth.uid())
      and p.account_role = 'seller'
  )
  or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
);
