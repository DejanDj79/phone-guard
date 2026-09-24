alter table public.child_devices
  add column if not exists parent_fcm_token text;

create table if not exists public.time_requests (
  request_id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.child_devices(device_id) on delete cascade,
  requested_minutes integer not null check (requested_minutes between 1 and 120),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED')),
  created_at timestamptz not null default now(),
  resolved_at timestamptz null
);

alter table public.time_requests enable row level security;

create index if not exists time_requests_device_created_idx
  on public.time_requests(device_id, created_at desc);

create unique index if not exists one_pending_time_request_per_device
  on public.time_requests(device_id)
  where status = 'PENDING';
