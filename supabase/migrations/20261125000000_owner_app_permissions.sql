-- =====================================================================
-- MED-K Admin (owner app) — additive & idempotent.
-- * Two new seller permissions: manage P2P prices, view statistics.
-- * Every owner is notified of every new order.
-- =====================================================================

alter table public.profiles add column if not exists can_manage_p2p boolean not null default false;
alter table public.profiles add column if not exists can_view_stats boolean not null default false;

-- Only the owner (or the service role) may change roles, activation and permissions.
create or replace function public.protect_profile_fields() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(auth.role(), '') <> 'service_role' and not public.is_owner() then
    new.role := old.role;
    new.is_active := old.is_active;
    new.can_manage_products := old.can_manage_products;
    new.can_update_orders := old.can_update_orders;
    new.can_manage_p2p := old.can_manage_p2p;
    new.can_view_stats := old.can_view_stats;
    new.email := old.email;
  end if;
  return new;
end $$;

create or replace function public.has_permission(p_perm text) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_owner() or exists(
    select 1 from profiles where id = public.medk_uid() and role = 'staff' and is_active and
      case p_perm
        when 'p2p' then can_manage_p2p
        when 'stats' then can_view_stats
        when 'products' then can_manage_products
        when 'orders' then can_update_orders
        else false end)
$$;

-- P2P rates: owner or sellers holding the P2P permission.
drop policy if exists medk_p2p_write on public.p2p_assets;
create policy medk_p2p_write on public.p2p_assets for all to authenticated
  using (public.has_permission('p2p')) with check (public.has_permission('p2p'));

-- Statistics: owner, or sellers holding the statistics permission (their own orders only).
create or replace function public.dashboard_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid text := public.medk_uid(); v_owner boolean := public.is_owner();
begin
  if not public.has_permission('stats') then raise exception 'forbidden'; end if;
  return (
    select jsonb_build_object(
      'total_orders', count(*),
      'open_orders', count(*) filter (where status = 'open'),
      'paid_orders', count(*) filter (where status = 'paid'),
      'delivered_orders', count(*) filter (where status = 'delivered'),
      'closed_orders', count(*) filter (where status = 'closed'),
      'revenue_usd', coalesce(sum(total_usd) filter (where status in ('paid','delivered','closed')), 0),
      'revenue_dzd', coalesce(sum(total_dzd) filter (where status in ('paid','delivered','closed')), 0),
      'open_conversations', (select count(*) from conversations c
          left join orders o on o.id = c.order_id
          where (v_owner or c.staff_id = v_uid) and (o.id is null or o.status <> 'closed')),
      'unassigned_orders', count(*) filter (where staff_id is null and status <> 'closed'),
      'products', (select count(*) from products),
      'staff', (select count(*) from profiles where role = 'staff'),
      'customers', (select count(*) from profiles where role = 'customer'))
    from orders where v_owner or staff_id = v_uid);
end $$;

-- Notify every active owner when an order is created.
create or replace function public.orders_notify_owners() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into notifications(user_id, title, body, order_id)
  select p.id, 'طلب جديد في المتجر', 'الطلب ' || new.reference || ' — ' || new.total_usd || ' USD', new.id
  from profiles p where p.role = 'owner' and p.is_active;
  return new;
end $$;
drop trigger if exists trg_orders_notify_owners on public.orders;
create trigger trg_orders_notify_owners after insert on public.orders
for each row execute function public.orders_notify_owners();
