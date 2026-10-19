alter table public.device_commands
  drop constraint if exists device_commands_command_check,
  add constraint device_commands_command_check
    check (
      command in (
        'LOCK',
        'UNLOCK',
        'BONUS_TIME',
        'SYNC_SCHEDULE',
        'SYNC_ALLOWED_APPS',
        'SYNC_DAILY_LIMIT',
        'REQUEST_LOCATION'
      )
    );

create table if not exists public.child_locations (
  device_id uuid primary key
    references public.child_devices(device_id) on delete cascade,
  latitude double precision not null
    check (latitude between -90 and 90),
  longitude double precision not null
    check (longitude between -180 and 180),
  accuracy_meters double precision
    check (
      accuracy_meters is null
      or (accuracy_meters >= 0 and accuracy_meters <= 100000)
    ),
  captured_at timestamptz not null,
  received_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.child_locations enable row level security;

revoke all on table public.child_locations from anon, authenticated;
grant select, insert, update, delete
  on table public.child_locations
  to service_role;
