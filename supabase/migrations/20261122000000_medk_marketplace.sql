-- =====================================================================
-- MED-K marketplace schema — ADDITIVE & IDEMPOTENT.
-- * Never drops or recreates tables. Uses CREATE ... IF NOT EXISTS and
--   ADD COLUMN IF NOT EXISTS so it can be applied to an existing project.
-- * Identities come from Firebase Authentication (Supabase "Third-party
--   auth" → Firebase). The user id is the Firebase UID: auth.jwt()->>'sub'.
-- * Review against your existing MED-K tables BEFORE applying in production.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tables
create table if not exists public.profiles (
  id text primary key,                         -- Firebase UID
  email text,
  full_name text,
  avatar_url text,
  role text not null default 'customer',
  is_active boolean not null default true,
  can_manage_products boolean not null default false,
  can_update_orders boolean not null default true,
  created_at timestamptz not null default now()
);
alter table public.profiles add column if not exists can_manage_products boolean not null default false;
alter table public.profiles add column if not exists can_update_orders boolean not null default true;
alter table public.profiles add column if not exists is_active boolean not null default true;
do $$ begin
  alter table public.profiles add constraint profiles_role_chk check (role in ('customer','staff','owner'));
exception when duplicate_object then null; end $$;

create table if not exists public.categories (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  description text,
  color_key text,
  icon_key text,
  sort_order int not null default 0,
  is_active boolean not null default true
);

create table if not exists public.sections (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.categories(id) on delete cascade,
  name text not null,
  description text,
  image_url text,
  assigned_staff_id text references public.profiles(id) on delete set null,
  sort_order int not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.categories(id) on delete cascade,
  section_id uuid references public.sections(id) on delete set null,
  name text not null,
  description text,
  price_usd numeric(12,2) not null default 0,
  price_dzd numeric(14,2) not null default 0,
  stock int,                                   -- null = unlimited (digital)
  is_available boolean not null default true,
  images text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.staff_sections (
  staff_id text not null references public.profiles(id) on delete cascade,
  section_id uuid not null references public.sections(id) on delete cascade,
  primary key (staff_id, section_id)
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  reference text unique not null default ('MK-' || upper(substr(replace(gen_random_uuid()::text,'-',''),1,8))),
  customer_id text not null references public.profiles(id),
  staff_id text references public.profiles(id) on delete set null,
  section_id uuid references public.sections(id) on delete set null,
  status text not null default 'open',
  total_usd numeric(12,2) not null default 0,
  total_dzd numeric(14,2) not null default 0,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  paid_at timestamptz,
  delivered_at timestamptz,
  closed_at timestamptz
);
do $$ begin
  alter table public.orders add constraint orders_status_chk check (status in ('open','paid','delivered','closed'));
exception when duplicate_object then null; end $$;

create table if not exists public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  product_id uuid references public.products(id) on delete set null,
  product_name text not null,
  image_url text,
  quantity int not null check (quantity > 0),
  unit_price_usd numeric(12,2) not null,
  unit_price_dzd numeric(14,2) not null
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  customer_id text not null references public.profiles(id),
  staff_id text references public.profiles(id) on delete set null,
  order_id uuid references public.orders(id) on delete set null,
  product_id uuid references public.products(id) on delete set null,
  section_id uuid references public.sections(id) on delete set null,
  subject text,
  last_message text,
  last_message_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id text not null references public.profiles(id),
  body text,
  attachment_path text,
  attachment_type text check (attachment_type in ('image','video','pdf')),
  attachment_name text,
  created_at timestamptz not null default now()
);

create table if not exists public.favorites (
  user_id text not null references public.profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, product_id)
);

create table if not exists public.banners (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  subtitle text,
  image_url text,
  category_slug text,
  sort_order int not null default 0,
  is_active boolean not null default true
);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id text not null references public.profiles(id) on delete cascade,
  title text not null,
  body text,
  conversation_id uuid,
  order_id uuid,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.p2p_assets (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  name text not null,
  network text,
  buy_price_dzd numeric(14,2) not null default 0,   -- customer buys from us
  sell_price_dzd numeric(14,2) not null default 0,  -- customer sells to us
  min_amount numeric(14,2),
  max_amount numeric(14,2),
  is_active boolean not null default true,
  sort_order int not null default 0,
  updated_at timestamptz not null default now()
);

create index if not exists idx_products_section on public.products(section_id);
create index if not exists idx_products_category on public.products(category_id);
create index if not exists idx_orders_customer on public.orders(customer_id);
create index if not exists idx_orders_staff on public.orders(staff_id);
create index if not exists idx_conv_customer on public.conversations(customer_id);
create index if not exists idx_conv_staff on public.conversations(staff_id);
create index if not exists idx_messages_conv on public.messages(conversation_id, created_at);
create index if not exists idx_notif_user on public.notifications(user_id, created_at desc);

-- ---------------------------------------------------------- helpers
create or replace function public.medk_uid() returns text
language sql stable as $$ select nullif(auth.jwt()->>'sub','') $$;

create or replace function public.medk_role() returns text
language sql stable security definer set search_path = public as $$
  select role from public.profiles where id = public.medk_uid() and is_active
$$;

create or replace function public.is_owner() returns boolean
language sql stable as $$ select coalesce(public.medk_role() = 'owner', false) $$;

create or replace function public.is_staff() returns boolean
language sql stable as $$ select coalesce(public.medk_role() in ('staff','owner'), false) $$;

create or replace function public.staff_has_section(p_section uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_owner() or (public.is_staff() and (
    exists(select 1 from staff_sections where staff_id = public.medk_uid() and section_id = p_section)
    or exists(select 1 from sections where id = p_section and assigned_staff_id = public.medk_uid())))
$$;

create or replace function public.staff_can_manage_products(p_section uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_owner() or (public.staff_has_section(p_section) and exists(
    select 1 from profiles where id = public.medk_uid() and can_manage_products and is_active))
$$;

create or replace function public.can_access_conversation(p_conv uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists(select 1 from conversations c where c.id = p_conv and (
    c.customer_id = public.medk_uid()
    or (c.staff_id = public.medk_uid() and public.is_staff())
    or public.is_owner()))
$$;

-- ------------------------------------------------------------ triggers
create or replace function public.protect_profile_fields() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(auth.role(), '') <> 'service_role' and not public.is_owner() then
    new.role := old.role;
    new.is_active := old.is_active;
    new.can_manage_products := old.can_manage_products;
    new.can_update_orders := old.can_update_orders;
    new.email := old.email;
  end if;
  return new;
end $$;
drop trigger if exists trg_protect_profile on public.profiles;
create trigger trg_protect_profile before update on public.profiles
for each row execute function public.protect_profile_fields();

create or replace function public.orders_before_update() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_owner() and coalesce(auth.role(), '') <> 'service_role' then
    -- staff may only change status/note
    new.customer_id := old.customer_id; new.staff_id := old.staff_id;
    new.total_usd := old.total_usd; new.total_dzd := old.total_dzd;
    new.reference := old.reference; new.section_id := old.section_id;
  end if;
  new.updated_at := now();
  if new.status <> old.status then
    if new.status = 'paid' and new.paid_at is null then new.paid_at := now(); end if;
    if new.status = 'delivered' then
      new.delivered_at := coalesce(new.delivered_at, now());
      new.paid_at := coalesce(new.paid_at, now());
    end if;
    if new.status = 'closed' then new.closed_at := now(); end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_orders_before_update on public.orders;
create trigger trg_orders_before_update before update on public.orders
for each row execute function public.orders_before_update();

create or replace function public.orders_after_update() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_label text;
begin
  if new.status <> old.status then
    v_label := case new.status when 'paid' then 'تم الدفع' when 'delivered' then 'تم التسليم'
                               when 'closed' then 'مغلق' else 'مفتوح' end;
    insert into notifications(user_id, title, body, order_id)
    values (new.customer_id, 'تحديث الطلب ' || new.reference, 'حالة طلبك الآن: ' || v_label, new.id);
  end if;
  if new.staff_id is distinct from old.staff_id then
    update conversations set staff_id = new.staff_id where order_id = new.id;
    if new.staff_id is not null then
      insert into notifications(user_id, title, body, order_id)
      values (new.staff_id, 'طلب جديد مُسند إليك', 'الطلب ' || new.reference, new.id);
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_orders_after_update on public.orders;
create trigger trg_orders_after_update after update on public.orders
for each row execute function public.orders_after_update();

create or replace function public.messages_after_insert() returns trigger
language plpgsql security definer set search_path = public as $$
declare c record; v_preview text; v_target text;
begin
  select * into c from conversations where id = new.conversation_id;
  v_preview := coalesce(nullif(new.body,''),
    case new.attachment_type when 'image' then '📷 صورة' when 'video' then '🎬 فيديو' when 'pdf' then '📄 ملف PDF' else '' end);
  update conversations set last_message = left(v_preview, 200), last_message_at = new.created_at
  where id = new.conversation_id;
  v_target := case when new.sender_id = c.customer_id then c.staff_id else c.customer_id end;
  if v_target is not null and v_target <> new.sender_id then
    insert into notifications(user_id, title, body, conversation_id)
    values (v_target, 'رسالة جديدة', left(v_preview, 140), new.conversation_id);
  end if;
  return new;
end $$;
drop trigger if exists trg_messages_after_insert on public.messages;
create trigger trg_messages_after_insert after insert on public.messages
for each row execute function public.messages_after_insert();

create or replace function public.touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at := now(); return new; end $$;
drop trigger if exists trg_products_touch on public.products;
create trigger trg_products_touch before update on public.products for each row execute function public.touch_updated_at();
drop trigger if exists trg_p2p_touch on public.p2p_assets;
create trigger trg_p2p_touch before update on public.p2p_assets for each row execute function public.touch_updated_at();

-- ------------------------------------------------------------------ RPCs
-- Creates one order per assigned sales representative. Prices are read
-- from the database, never trusted from the client.
create or replace function public.create_orders_from_cart(p_items jsonb, p_note text default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_uid text := public.medk_uid();
  v_group record; v_order uuid; v_ref text; v_conv uuid; v_result jsonb := '[]'::jsonb;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;
  if not exists(select 1 from profiles where id = v_uid and is_active) then raise exception 'profile inactive'; end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then raise exception 'empty cart'; end if;

  create temp table _cart on commit drop as
    select p.id as product_id, p.name, p.images[1] as image, p.price_usd, p.price_dzd, p.section_id,
           greatest(1, (i->>'quantity')::int) as qty, s.assigned_staff_id as staff_id, s.name as section_name
    from jsonb_array_elements(p_items) i
    join products p on p.id = (i->>'product_id')::uuid and p.is_available
    left join sections s on s.id = p.section_id;

  if not exists(select 1 from _cart) then raise exception 'no available products'; end if;
  if exists(select 1 from _cart c join products p on p.id = c.product_id where p.stock is not null and p.stock < c.qty)
    then raise exception 'insufficient stock'; end if;

  for v_group in
    select staff_id, (array_agg(distinct section_id))[1] as section_id, count(distinct section_id) as n_sections,
           string_agg(distinct section_name, '، ') as sections,
           sum(price_usd*qty) as usd, sum(price_dzd*qty) as dzd
    from _cart group by staff_id
  loop
    insert into orders(customer_id, staff_id, section_id, total_usd, total_dzd, note)
    values (v_uid, v_group.staff_id, case when v_group.n_sections = 1 then v_group.section_id end,
            v_group.usd, v_group.dzd, p_note)
    returning id, reference into v_order, v_ref;

    insert into order_items(order_id, product_id, product_name, image_url, quantity, unit_price_usd, unit_price_dzd)
    select v_order, product_id, name, image, qty, price_usd, price_dzd from _cart
    where staff_id is not distinct from v_group.staff_id;

    update products p set stock = p.stock - c.qty from _cart c
    where p.id = c.product_id and p.stock is not null and c.staff_id is not distinct from v_group.staff_id;

    insert into conversations(customer_id, staff_id, order_id, section_id, subject)
    values (v_uid, v_group.staff_id, v_order, case when v_group.n_sections = 1 then v_group.section_id end,
            'طلب ' || v_ref || coalesce(' — ' || v_group.sections, ''))
    returning id into v_conv;

    insert into messages(conversation_id, sender_id, body)
    values (v_conv, v_uid, 'مرحباً، أرغب في إتمام الطلب ' || v_ref || '. الإجمالي: ' ||
            v_group.usd || ' USD / ' || v_group.dzd || ' DZD');

    if v_group.staff_id is not null then
      insert into notifications(user_id, title, body, order_id, conversation_id)
      values (v_group.staff_id, 'طلب جديد', 'الطلب ' || v_ref, v_order, v_conv);
    end if;

    v_result := v_result || jsonb_build_object('order_id', v_order, 'conversation_id', v_conv, 'reference', v_ref);
  end loop;
  return v_result;
end $$;

-- Opens (or reuses) a conversation with the seller of a product/section,
-- or with customer support when both are null.
create or replace function public.start_conversation(p_product uuid default null, p_section uuid default null, p_subject text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_uid text := public.medk_uid(); v_section uuid := p_section; v_staff text; v_conv uuid;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;
  if p_product is not null then select section_id into v_section from products where id = p_product; end if;
  if v_section is not null then select assigned_staff_id into v_staff from sections where id = v_section; end if;

  select id into v_conv from conversations
  where customer_id = v_uid and order_id is null
    and product_id is not distinct from p_product and section_id is not distinct from v_section
  order by created_at desc limit 1;
  if v_conv is not null then return v_conv; end if;

  insert into conversations(customer_id, staff_id, product_id, section_id, subject)
  values (v_uid, v_staff, p_product, v_section, coalesce(p_subject, 'الدعم الفني'))
  returning id into v_conv;
  return v_conv;
end $$;

create or replace function public.dashboard_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid text := public.medk_uid(); v_owner boolean := public.is_owner();
begin
  if not public.is_staff() then raise exception 'forbidden'; end if;
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

grant execute on function public.create_orders_from_cart(jsonb, text) to authenticated;
grant execute on function public.start_conversation(uuid, uuid, text) to authenticated;
grant execute on function public.dashboard_stats() to authenticated;

-- ------------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.categories enable row level security;
alter table public.sections enable row level security;
alter table public.products enable row level security;
alter table public.staff_sections enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.favorites enable row level security;
alter table public.banners enable row level security;
alter table public.notifications enable row level security;
alter table public.p2p_assets enable row level security;

do $$ declare r record; begin
  for r in select policyname, tablename from pg_policies where schemaname = 'public' and policyname like 'medk_%' loop
    execute format('drop policy %I on public.%I', r.policyname, r.tablename);
  end loop;
end $$;

-- profiles
create policy medk_profiles_select on public.profiles for select to authenticated
  using (id = public.medk_uid() or public.is_staff()
         or exists(select 1 from conversations c where c.staff_id = profiles.id and c.customer_id = public.medk_uid()));
create policy medk_profiles_update_self on public.profiles for update to authenticated
  using (id = public.medk_uid()) with check (id = public.medk_uid());
create policy medk_profiles_update_owner on public.profiles for update to authenticated
  using (public.is_owner()) with check (public.is_owner());

-- catalog (public read of active rows; staff see everything)
create policy medk_categories_read on public.categories for select to anon, authenticated using (is_active or public.is_staff());
create policy medk_categories_write on public.categories for all to authenticated using (public.is_owner()) with check (public.is_owner());
create policy medk_sections_read on public.sections for select to anon, authenticated using (is_active or public.is_staff());
create policy medk_sections_write on public.sections for all to authenticated using (public.is_owner()) with check (public.is_owner());
create policy medk_products_read on public.products for select to anon, authenticated using (is_available or public.is_staff());
create policy medk_products_insert on public.products for insert to authenticated with check (public.staff_can_manage_products(section_id));
create policy medk_products_update on public.products for update to authenticated
  using (public.staff_can_manage_products(section_id)) with check (public.staff_can_manage_products(section_id));
create policy medk_products_delete on public.products for delete to authenticated using (public.staff_can_manage_products(section_id));
create policy medk_banners_read on public.banners for select to anon, authenticated using (is_active or public.is_staff());
create policy medk_banners_write on public.banners for all to authenticated using (public.is_owner()) with check (public.is_owner());
create policy medk_p2p_read on public.p2p_assets for select to anon, authenticated using (is_active or public.is_staff());
create policy medk_p2p_write on public.p2p_assets for all to authenticated using (public.is_owner()) with check (public.is_owner());

create policy medk_staff_sections_read on public.staff_sections for select to authenticated using (staff_id = public.medk_uid() or public.is_owner());
create policy medk_staff_sections_write on public.staff_sections for all to authenticated using (public.is_owner()) with check (public.is_owner());

-- orders (inserted only by create_orders_from_cart)
create policy medk_orders_read on public.orders for select to authenticated
  using (customer_id = public.medk_uid() or public.is_owner() or (staff_id = public.medk_uid() and public.is_staff()));
create policy medk_orders_update on public.orders for update to authenticated
  using (public.is_owner() or (staff_id = public.medk_uid() and public.is_staff()
         and exists(select 1 from profiles where id = public.medk_uid() and can_update_orders)))
  with check (public.is_owner() or staff_id = public.medk_uid());
create policy medk_order_items_read on public.order_items for select to authenticated
  using (exists(select 1 from orders o where o.id = order_id and (o.customer_id = public.medk_uid() or public.is_owner()
         or (o.staff_id = public.medk_uid() and public.is_staff()))));

-- conversations & messages
create policy medk_conv_read on public.conversations for select to authenticated using (public.can_access_conversation(id));
create policy medk_conv_assign on public.conversations for update to authenticated using (public.is_owner()) with check (public.is_owner());
create policy medk_msg_read on public.messages for select to authenticated using (public.can_access_conversation(conversation_id));
create policy medk_msg_insert on public.messages for insert to authenticated
  with check (sender_id = public.medk_uid() and public.can_access_conversation(conversation_id));

create policy medk_fav_all on public.favorites for all to authenticated
  using (user_id = public.medk_uid()) with check (user_id = public.medk_uid());
create policy medk_notif_read on public.notifications for select to authenticated using (user_id = public.medk_uid());
create policy medk_notif_update on public.notifications for update to authenticated
  using (user_id = public.medk_uid()) with check (user_id = public.medk_uid());
create policy medk_notif_delete on public.notifications for delete to authenticated using (user_id = public.medk_uid());

-- --------------------------------------------------------------- storage
insert into storage.buckets (id, name, public) values ('product-images', 'product-images', true) on conflict (id) do nothing;
insert into storage.buckets (id, name, public) values ('chat-attachments', 'chat-attachments', false) on conflict (id) do nothing;

drop policy if exists medk_product_images_read on storage.objects;
drop policy if exists medk_product_images_write on storage.objects;
drop policy if exists medk_product_images_update on storage.objects;
drop policy if exists medk_product_images_delete on storage.objects;
drop policy if exists medk_chat_read on storage.objects;
drop policy if exists medk_chat_insert on storage.objects;
create policy medk_product_images_read on storage.objects for select to anon, authenticated using (bucket_id = 'product-images');
create policy medk_product_images_write on storage.objects for insert to authenticated with check (bucket_id = 'product-images' and public.is_staff());
create policy medk_product_images_update on storage.objects for update to authenticated using (bucket_id = 'product-images' and public.is_staff());
create policy medk_product_images_delete on storage.objects for delete to authenticated using (bucket_id = 'product-images' and public.is_staff());
-- chat files are stored as <conversation_id>/<file>
create policy medk_chat_read on storage.objects for select to authenticated
  using (bucket_id = 'chat-attachments' and public.can_access_conversation(((storage.foldername(name))[1])::uuid));
create policy medk_chat_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'chat-attachments' and public.can_access_conversation(((storage.foldername(name))[1])::uuid));

-- -------------------------------------------------------------- realtime
do $$ begin
  begin alter publication supabase_realtime add table public.messages; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.conversations; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.notifications; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.orders; exception when duplicate_object then null; end;
end $$;

-- ------------------------------------------- structural seed (no products)
insert into public.categories (slug, name, description, color_key, icon_key, sort_order) values
  ('gaming', 'شحن الألعاب', 'جواهر، شدات، عملات داخل الألعاب', 'orange', 'gaming', 1),
  ('subscriptions', 'اشتراكات التطبيقات', 'Netflix - YouTube - Spotify وأكثر', 'blue', 'subscriptions', 2),
  ('accounts', 'شراء الحسابات', 'حسابات الألعاب والتطبيقات', 'purple', 'accounts', 3),
  ('p2p', 'شراء وبيع العملات الرقمية P2P', 'USDT والعملات الرقمية المدعومة', 'green', 'p2p', 4),
  ('other', 'خدمات أخرى', 'بطاقات الهدايا، تراخيص البرامج، VPN والخدمات الرقمية المتنوعة', 'cyan', 'other', 5)
on conflict (slug) do nothing;

insert into public.sections (category_id, name, sort_order)
select c.id, s.name, s.ord from (values
  ('gaming','Free Fire',1),('gaming','PUBG Mobile',2),('gaming','Call of Duty Mobile',3),('gaming','Roblox',4),('gaming','ألعاب أخرى',5),
  ('subscriptions','Netflix',1),('subscriptions','YouTube Premium',2),('subscriptions','Spotify',3),('subscriptions','تطبيقات أخرى',4),
  ('accounts','حسابات الألعاب',1),('accounts','حسابات التطبيقات',2),
  ('p2p','العملات الرقمية',1),
  ('other','بطاقات الهدايا',1),('other','Google Play',2),('other','Apple Gift Card',3),('other','تراخيص البرامج',4),('other','اشتراكات VPN',5),('other','منتجات رقمية أخرى',6)
) as s(slug, name, ord)
join public.categories c on c.slug = s.slug
where not exists (select 1 from public.sections x where x.category_id = c.id and x.name = s.name);
