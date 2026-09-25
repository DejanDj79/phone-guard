alter table public.child_devices
  drop column if exists offline_alert_sent_at;
