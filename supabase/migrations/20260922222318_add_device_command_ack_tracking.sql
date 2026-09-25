create table public.device_commands (
  command_id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.child_devices(device_id) on delete cascade,
  command text not null check (command in ('LOCK','UNLOCK','BONUS_TIME')),
  bonus_minutes integer null check (bonus_minutes is null or bonus_minutes between 1 and 1440),
  status text not null default 'PENDING'
    check (status in ('PENDING','SENT','APPLIED','FAILED')),
  error_code text null,
  created_at timestamptz not null default now(),
  sent_at timestamptz null,
  applied_at timestamptz null
);

create index device_commands_device_created_idx
  on public.device_commands(device_id, created_at desc);

alter table public.device_commands enable row level security;

revoke all on table public.device_commands from anon, authenticated;
grant select, insert, update, delete on table public.device_commands to service_role;
