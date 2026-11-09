-- MALRO initial marketplace schema (PostgreSQL / Supabase)
-- Apply only after creating a dedicated MALRO Supabase project and reviewing this migration.
create extension if not exists pgcrypto;

create type public.shop_status as enum ('pending','approved','rejected','suspended');
create type public.product_status as enum ('draft','active','hidden','rejected');
create type public.order_status as enum ('placed','accepted','packed','assigned','picked_up','delivered','cancelled','refunded');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  phone text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shops (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id),
  name text not null,
  description text,
  phone text,
  address text not null,
  locality text not null default 'Barmer',
  status public.shop_status not null default 'pending',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.products (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops(id) on delete cascade,
  name text not null,
  description text,
  category text not null,
  price_paise bigint not null check (price_paise >= 0),
  stock_quantity integer not null default 0 check (stock_quantity >= 0),
  image_path text,
  status public.product_status not null default 'draft',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.orders (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references auth.users(id),
  shop_id uuid not null references public.shops(id),
  status public.order_status not null default 'placed',
  subtotal_paise bigint not null check (subtotal_paise >= 0),
  delivery_fee_paise bigint not null default 0 check (delivery_fee_paise >= 0),
  total_paise bigint not null check (total_paise >= 0),
  delivery_address jsonb not null,
  payment_status text not null default 'pending' check (payment_status in ('pending','authorized','paid','failed','refunded')),
  payment_provider text,
  payment_reference text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  product_id uuid not null references public.products(id),
  product_name_snapshot text not null,
  unit_price_paise bigint not null check (unit_price_paise >= 0),
  quantity integer not null check (quantity > 0),
  line_total_paise bigint not null check (line_total_paise >= 0)
);

create table public.delivery_jobs (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.orders(id) on delete cascade,
  delivery_user_id uuid references auth.users(id),
  assigned_by uuid references auth.users(id),
  pickup_at timestamptz,
  delivered_at timestamptz,
  proof_path text,
  last_latitude double precision,
  last_longitude double precision,
  last_location_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.order_events (
  id bigint generated always as identity primary key,
  order_id uuid not null references public.orders(id) on delete cascade,
  actor_id uuid references auth.users(id),
  event_type text not null,
  note text,
  created_at timestamptz not null default now()
);

create index shops_owner_idx on public.shops(owner_id);
create index products_shop_status_idx on public.products(shop_id,status);
create index orders_customer_created_idx on public.orders(customer_id,created_at desc);
create index orders_shop_created_idx on public.orders(shop_id,created_at desc);
create index delivery_jobs_assignee_idx on public.delivery_jobs(delivery_user_id);

alter table public.profiles enable row level security;
alter table public.shops enable row level security;
alter table public.products enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.delivery_jobs enable row level security;
alter table public.order_events enable row level security;

-- Authorization roles are assigned only by trusted server-side admin tooling
-- into app_metadata; never trust user-editable user_metadata for privileges.
create policy "profile read self" on public.profiles for select to authenticated
  using (id = (select auth.uid()));
create policy "profile create self" on public.profiles for insert to authenticated
  with check (id = (select auth.uid()));
create policy "profile update self" on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "approved shops public read" on public.shops for select to anon, authenticated
  using (status = 'approved' or owner_id = (select auth.uid()) or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');
create policy "seller creates own shop" on public.shops for insert to authenticated
  with check (owner_id = (select auth.uid()));
create policy "seller updates own shop" on public.shops for update to authenticated
  using (owner_id = (select auth.uid()) or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')
  with check (owner_id = (select auth.uid()) or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');

create policy "active products public read" on public.products for select to anon, authenticated
  using (
    (status = 'active' and exists (select 1 from public.shops s where s.id = shop_id and s.status = 'approved'))
    or exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid()))
    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
  );
create policy "seller creates products in own shop" on public.products for insert to authenticated
  with check (exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid()) and s.status = 'approved'));
create policy "seller updates products in own shop" on public.products for update to authenticated
  using (exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid())) or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')
  with check (exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid())) or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');

create policy "order parties and admin read" on public.orders for select to authenticated
  using (
    customer_id = (select auth.uid())
    or exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid()))
    or exists (select 1 from public.delivery_jobs d where d.order_id = id and d.delivery_user_id = (select auth.uid()))
    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
  );
-- Order creation, totals, payment confirmation and status transitions must go
-- through trusted server functions/webhooks; clients receive no direct write policy.

create policy "order items visible to order parties" on public.order_items for select to authenticated
  using (exists (
    select 1 from public.orders o
    left join public.shops s on s.id = o.shop_id
    left join public.delivery_jobs d on d.order_id = o.id
    where o.id = order_id and (
      o.customer_id = (select auth.uid()) or s.owner_id = (select auth.uid())
      or d.delivery_user_id = (select auth.uid())
      or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
  ));

create policy "assigned delivery job read" on public.delivery_jobs for select to authenticated
  using (
    delivery_user_id = (select auth.uid())
    or exists (select 1 from public.orders o where o.id = order_id and o.customer_id = (select auth.uid()))
    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
  );
-- Assignment, GPS updates and delivery completion are server-validated writes.

create policy "order events parties read" on public.order_events for select to authenticated
  using (exists (
    select 1 from public.orders o
    left join public.shops s on s.id = o.shop_id
    left join public.delivery_jobs d on d.order_id = o.id
    where o.id = order_id and (
      o.customer_id = (select auth.uid()) or s.owner_id = (select auth.uid())
      or d.delivery_user_id = (select auth.uid())
      or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
  ));
-- Event writes are restricted to trusted server code to preserve an audit trail.

grant select on public.shops, public.products to anon, authenticated;
grant select, insert, update on public.profiles to authenticated;
grant insert on public.shops to authenticated;
-- Prevent sellers from changing approval status directly.
revoke update on public.shops from authenticated;
grant update (name, description, phone, address, locality) on public.shops to authenticated;
grant insert, update on public.products to authenticated;
grant select on public.orders, public.order_items, public.delivery_jobs, public.order_events to authenticated;
