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
        'SYNC_GEOFENCES',
        'REQUEST_LOCATION'
      )
    );

create table if not exists public.geofence_zones (
  zone_id uuid primary key default gen_random_uuid(),
  device_id uuid not null
    references public.child_devices(device_id) on delete cascade,
  name text not null
    check (char_length(name) between 1 and 60),
  latitude double precision not null
    check (latitude between -90 and 90),
  longitude double precision not null
    check (longitude between -180 and 180),
  radius_meters integer not null
    check (radius_meters between 100 and 5000),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists geofence_zones_device_id_idx
  on public.geofence_zones(device_id);

create table if not exists public.geofence_events (
  event_id uuid primary key default gen_random_uuid(),
  device_id uuid not null
    references public.child_devices(device_id) on delete cascade,
  zone_id uuid
    references public.geofence_zones(zone_id) on delete set null,
  zone_name text not null,
  event_type text not null
    check (event_type in ('ENTER', 'EXIT')),
  occurred_at timestamptz not null,
  received_at timestamptz not null default now()
);

create index if not exists geofence_events_device_time_idx
  on public.geofence_events(device_id, occurred_at desc);

alter table public.geofence_zones enable row level security;
alter table public.geofence_events enable row level security;

revoke all on table public.geofence_zones from anon, authenticated;
revoke all on table public.geofence_events from anon, authenticated;

grant select, insert, update, delete
  on table public.geofence_zones
  to service_role;

grant select, insert, update, delete
  on table public.geofence_events
  to service_role;
