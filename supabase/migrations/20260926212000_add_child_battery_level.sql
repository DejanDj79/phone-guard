alter table public.child_devices
  add column battery_level_percent smallint
  check (
    battery_level_percent is null
    or battery_level_percent between 0 and 100
  );
