create table public.protection_events (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.child_devices(device_id) on delete cascade,
  event_type text not null
    check (
      event_type in (
        'ACCESSIBILITY_DISABLED',
        'ACCESSIBILITY_RESTORED',
        'PRECISE_TIMING_DISABLED',
        'PRECISE_TIMING_RESTORED',
        'BATTERY_UNRESTRICTED_DISABLED',
        'BATTERY_UNRESTRICTED_RESTORED',
        'APP_INFO_OPENED',
        'UNINSTALL_SCREEN_OPENED',
        'FORCE_STOP_ATTEMPT',
        'CLEAR_DATA_ATTEMPT'
      )
    ),
  created_at timestamptz not null default now()
);

create index protection_events_device_created_at_idx
  on public.protection_events (device_id, created_at desc);

alter table public.protection_events enable row level security;

revoke all on table public.protection_events from anon, authenticated;
grant select, insert, delete on table public.protection_events to service_role;
