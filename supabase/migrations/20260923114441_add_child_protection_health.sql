alter table public.child_devices
  add column if not exists accessibility_enabled boolean,
  add column if not exists precise_timing_enabled boolean,
  add column if not exists battery_unrestricted boolean,
  add column if not exists protection_updated_at timestamptz;
