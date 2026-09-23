alter table public.child_devices
  add column if not exists schedule_config text,
  add column if not exists schedule_version bigint not null default 0,
  add column if not exists schedule_updated_at timestamptz;

alter table public.child_devices
  drop constraint if exists child_devices_schedule_version_check;

alter table public.child_devices
  add constraint child_devices_schedule_version_check
  check (schedule_version >= 0);

alter table public.device_commands
  drop constraint if exists device_commands_command_check;

alter table public.device_commands
  add constraint device_commands_command_check
  check (command = any (array[
    'LOCK'::text,
    'UNLOCK'::text,
    'BONUS_TIME'::text,
    'SYNC_SCHEDULE'::text
  ]));
