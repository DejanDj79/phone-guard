create table public.app_usage_daily (
  device_id uuid not null references public.child_devices(device_id) on delete cascade,
  usage_date date not null,
  total_seconds integer not null default 0
    check (total_seconds between 0 and 172800),
  apps jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  primary key (device_id, usage_date)
);

create index app_usage_daily_device_date_idx
  on public.app_usage_daily (device_id, usage_date desc);

alter table public.app_usage_daily enable row level security;

revoke all on table public.app_usage_daily from anon, authenticated;
grant select, insert, update, delete on table public.app_usage_daily to service_role;
