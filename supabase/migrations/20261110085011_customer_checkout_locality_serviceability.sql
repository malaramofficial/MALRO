create or replace function public.place_customer_order(
  p_shop_id uuid, p_delivery_address jsonb, p_items jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_customer_id uuid := auth.uid();
  v_order_id uuid;
  v_product public.products%rowtype;
  v_item record;
  v_subtotal bigint := 0;
  v_delivery_fee bigint := 0;
  v_count integer;
  v_unique_count integer;
begin
  if v_customer_id is null then raise exception 'Login required' using errcode = '28000'; end if;
  if p_shop_id is null then raise exception 'Select a shop'; end if;
  if pg_catalog.jsonb_typeof(p_delivery_address) <> 'object'
     or pg_catalog.length(pg_catalog.btrim(coalesce(p_delivery_address->>'name',''))) < 2
     or coalesce(p_delivery_address->>'phone','') !~ '^[+]?[0-9]{10,13}$'
     or pg_catalog.length(pg_catalog.btrim(coalesce(p_delivery_address->>'address_line',''))) < 4
     or pg_catalog.length(pg_catalog.btrim(coalesce(p_delivery_address->>'locality',''))) < 2
     or coalesce(p_delivery_address->>'pincode','') !~ '^[0-9]{6}$' then
    raise exception 'Delivery address is incomplete or invalid';
  end if;
  if not exists (
    select 1 from public.shops s
    where s.id = p_shop_id and s.status = 'approved'
      and pg_catalog.lower(pg_catalog.btrim(s.locality)) =
          pg_catalog.lower(pg_catalog.btrim(p_delivery_address->>'locality'))
  ) then raise exception 'This shop does not deliver to the selected locality'; end if;
  if pg_catalog.jsonb_typeof(p_items) <> 'array' then raise exception 'Cart items must be an array'; end if;
  select pg_catalog.count(*), pg_catalog.count(distinct (x.value->>'product_id')::uuid)
    into v_count, v_unique_count from pg_catalog.jsonb_array_elements(p_items) as x(value);
  if v_count < 1 or v_count > 30 or v_count <> v_unique_count then raise exception 'Cart is empty, too large, or contains duplicate products'; end if;
  for v_item in
    select (x.value->>'product_id')::uuid as product_id, (x.value->>'quantity')::integer as quantity
    from pg_catalog.jsonb_array_elements(p_items) as x(value) order by 1
  loop
    if v_item.quantity is null or v_item.quantity < 1 or v_item.quantity > 99 then raise exception 'Invalid product quantity'; end if;
    select p.* into v_product from public.products p join public.shops s on s.id = p.shop_id
    where p.id = v_item.product_id and p.shop_id = p_shop_id and p.status = 'active' and s.status = 'approved'
    for update of p;
    if not found then raise exception 'A product is no longer available'; end if;
    if v_product.stock_quantity < v_item.quantity then raise exception 'Insufficient stock for %', v_product.name; end if;
    v_subtotal := v_subtotal + (v_product.price_paise * v_item.quantity);
  end loop;
  insert into public.orders (customer_id, shop_id, status, subtotal_paise, delivery_fee_paise, total_paise, delivery_address, payment_status, payment_provider)
  values (v_customer_id, p_shop_id, 'placed', v_subtotal, v_delivery_fee, v_subtotal + v_delivery_fee, p_delivery_address, 'pending', 'cod')
  returning id into v_order_id;
  for v_item in
    select (x.value->>'product_id')::uuid as product_id, (x.value->>'quantity')::integer as quantity
    from pg_catalog.jsonb_array_elements(p_items) as x(value) order by 1
  loop
    select p.* into v_product from public.products p where p.id = v_item.product_id and p.shop_id = p_shop_id for update;
    update public.products set stock_quantity = stock_quantity - v_item.quantity, updated_at = pg_catalog.now() where id = v_product.id;
    insert into public.order_items (order_id, product_id, product_name_snapshot, unit_price_paise, quantity, line_total_paise)
    values (v_order_id, v_product.id, v_product.name, v_product.price_paise, v_item.quantity, v_product.price_paise * v_item.quantity);
  end loop;
  insert into public.order_events (order_id, actor_id, event_type, note)
  values (v_order_id, v_customer_id, 'placed', 'Customer placed order');
  return v_order_id;
end;
$function$;
revoke all on function public.place_customer_order(uuid, jsonb, jsonb) from public;
revoke all on function public.place_customer_order(uuid, jsonb, jsonb) from anon;
grant execute on function public.place_customer_order(uuid, jsonb, jsonb) to authenticated;
