create table public.protection_alert_state (
  device_id uuid not null references public.child_devices(device_id) on delete cascade,
  alert_type text not null,
  last_sent_at timestamptz not null,
  primary key (device_id, alert_type)
);

create index protection_alert_state_sent_idx
  on public.protection_alert_state (device_id, last_sent_at desc);

alter table public.protection_alert_state enable row level security;

revoke all on table public.protection_alert_state from anon, authenticated;
grant select, insert, update, delete on table public.protection_alert_state to service_role;
