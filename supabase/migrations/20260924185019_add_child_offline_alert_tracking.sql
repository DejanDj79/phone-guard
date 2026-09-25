create extension if not exists pg_cron;
create extension if not exists pg_net;

alter table public.child_devices
  add column if not exists offline_alert_sent_at timestamptz;

update public.child_devices
set offline_alert_sent_at = now()
where last_seen_at is not null
  and last_seen_at < now() - interval '5 minutes'
  and offline_alert_sent_at is null;
