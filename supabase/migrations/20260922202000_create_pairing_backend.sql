create table if not exists public.child_devices (
  device_id uuid primary key,
  display_name text not null default 'Child device',
  pairing_code_hash text not null unique,
  pairing_expires_at timestamptz not null,
  device_secret_hash text not null,
  control_token_hash text,
  fcm_token text,
  access_state text not null default 'ALLOWED'
    check (access_state in ('ALLOWED', 'LOCKED', 'TEMPORARILY_ALLOWED', 'OFFLINE')),
  temporary_allow_until timestamptz,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.child_devices enable row level security;
revoke all on table public.child_devices from anon, authenticated;
grant select, insert, update, delete on table public.child_devices to service_role;

create index if not exists child_devices_pairing_expires_idx
  on public.child_devices (pairing_expires_at);

create index if not exists child_devices_last_seen_idx
  on public.child_devices (last_seen_at desc);

create table if not exists public.pairing_attempts (
  id bigint generated always as identity primary key,
  ip_address text not null,
  attempted_at timestamptz not null default now()
);

alter table public.pairing_attempts enable row level security;
revoke all on table public.pairing_attempts from anon, authenticated;
grant select, insert, delete on table public.pairing_attempts to service_role;

create index if not exists pairing_attempts_ip_time_idx
  on public.pairing_attempts (ip_address, attempted_at desc);
