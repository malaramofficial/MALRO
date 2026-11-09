-- MALRO performance follow-up: cover foreign keys and avoid per-row JWT parsing.
create index if not exists delivery_jobs_assigned_by_idx on public.delivery_jobs(assigned_by);
create index if not exists order_events_actor_id_idx on public.order_events(actor_id);
create index if not exists order_events_order_id_idx on public.order_events(order_id);
create index if not exists order_items_order_id_idx on public.order_items(order_id);
create index if not exists order_items_product_id_idx on public.order_items(product_id);

drop policy if exists "approved shops public read" on public.shops;
create policy "approved shops public read" on public.shops for select to anon, authenticated
  using (status = 'approved' or owner_id = (select auth.uid()) or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin');

drop policy if exists "seller updates own shop" on public.shops;
create policy "seller updates own shop" on public.shops for update to authenticated
  using (owner_id = (select auth.uid()) or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin')
  with check (owner_id = (select auth.uid()) or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin');

drop policy if exists "active products public read" on public.products;
create policy "active products public read" on public.products for select to anon, authenticated
  using (
    (status = 'active' and exists (select 1 from public.shops s where s.id = shop_id and s.status = 'approved'))
    or exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid()))
    or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
  );

drop policy if exists "seller updates products in own shop" on public.products;
create policy "seller updates products in own shop" on public.products for update to authenticated
  using (exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid())) or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin')
  with check (exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid())) or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin');

drop policy if exists "order parties and admin read" on public.orders;
create policy "order parties and admin read" on public.orders for select to authenticated
  using (
    customer_id = (select auth.uid())
    or exists (select 1 from public.shops s where s.id = shop_id and s.owner_id = (select auth.uid()))
    or exists (select 1 from public.delivery_jobs d where d.order_id = id and d.delivery_user_id = (select auth.uid()))
    or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
  );

drop policy if exists "order items visible to order parties" on public.order_items;
create policy "order items visible to order parties" on public.order_items for select to authenticated
  using (exists (
    select 1 from public.orders o
    left join public.shops s on s.id = o.shop_id
    left join public.delivery_jobs d on d.order_id = o.id
    where o.id = order_id and (
      o.customer_id = (select auth.uid()) or s.owner_id = (select auth.uid())
      or d.delivery_user_id = (select auth.uid())
      or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
    )
  ));

drop policy if exists "assigned delivery job read" on public.delivery_jobs;
create policy "assigned delivery job read" on public.delivery_jobs for select to authenticated
  using (
    delivery_user_id = (select auth.uid())
    or exists (select 1 from public.orders o where o.id = order_id and o.customer_id = (select auth.uid()))
    or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
  );

drop policy if exists "order events parties read" on public.order_events;
create policy "order events parties read" on public.order_events for select to authenticated
  using (exists (
    select 1 from public.orders o
    left join public.shops s on s.id = o.shop_id
    left join public.delivery_jobs d on d.order_id = o.id
    where o.id = order_id and (
      o.customer_id = (select auth.uid()) or s.owner_id = (select auth.uid())
      or d.delivery_user_id = (select auth.uid())
      or ((select auth.jwt()) -> 'app_metadata' ->> 'role') = 'admin'
    )
  ));
