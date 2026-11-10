create or replace function public.cancel_customer_order(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_customer_id uuid := auth.uid();
  v_order public.orders%rowtype;
  v_item record;
begin
  if v_customer_id is null then
    raise exception 'Login required' using errcode = '28000';
  end if;
  select * into v_order
  from public.orders
  where id = p_order_id and customer_id = v_customer_id
  for update;
  if not found then raise exception 'Order not found'; end if;
  if v_order.status <> 'placed' or v_order.payment_status <> 'pending' then
    raise exception 'This order can no longer be cancelled';
  end if;

  for v_item in
    select product_id, quantity
    from public.order_items
    where order_id = p_order_id
    order by product_id
  loop
    update public.products
    set stock_quantity = stock_quantity + v_item.quantity,
        updated_at = pg_catalog.now()
    where id = v_item.product_id;
  end loop;

  update public.orders
  set status = 'cancelled', updated_at = pg_catalog.now()
  where id = p_order_id;

  insert into public.order_events (order_id, actor_id, event_type, note)
  values (p_order_id, v_customer_id, 'cancelled_by_customer', 'Customer cancelled before shop acceptance');
  return true;
end;
$function$;

revoke all on function public.cancel_customer_order(uuid) from public;
revoke all on function public.cancel_customer_order(uuid) from anon;
grant execute on function public.cancel_customer_order(uuid) to authenticated;
