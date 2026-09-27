alter table public.child_devices
  add column temporary_allow_granted_minutes integer
  check (
    temporary_allow_granted_minutes is null
    or temporary_allow_granted_minutes between 1 and 1440
  );
