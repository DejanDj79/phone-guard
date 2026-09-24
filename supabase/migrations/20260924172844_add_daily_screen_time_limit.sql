alter table public.child_devices
  add column if not exists daily_limit_minutes integer,
  add column if not exists daily_limit_version bigint not null default 0,
  add column if not exists daily_limit_updated_at timestamptz,
  add column if not exists daily_usage_date date,
  add column if not exists daily_usage_seconds integer not null default 0;

alter table public.child_devices
  drop constraint if exists child_devices_daily_limit_minutes_check,
  add constraint child_devices_daily_limit_minutes_check
    check (daily_limit_minutes is null or daily_limit_minutes between 1 and 1440),
  drop constraint if exists child_devices_daily_limit_version_check,
  add constraint child_devices_daily_limit_version_check
    check (daily_limit_version >= 0),
  drop constraint if exists child_devices_daily_usage_seconds_check,
  add constraint child_devices_daily_usage_seconds_check
    check (daily_usage_seconds >= 0);

alter table public.device_commands
  drop constraint if exists device_commands_command_check,
  add constraint device_commands_command_check
    check (
      command in (
        'LOCK',
        'UNLOCK',
        'BONUS_TIME',
        'SYNC_SCHEDULE',
        'SYNC_ALLOWED_APPS',
        'SYNC_DAILY_LIMIT'
      )
    );
