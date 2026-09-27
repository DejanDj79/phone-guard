alter table public.child_devices
  add column if not exists parent_user_id uuid
  references auth.users(id)
  on delete set null;

create index if not exists child_devices_parent_user_id_idx
  on public.child_devices(parent_user_id)
  where parent_user_id is not null;

comment on column public.child_devices.parent_user_id is
  'Supabase Auth user that owns this Child device. Nullable during legacy pairing migration.';
