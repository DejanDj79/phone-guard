alter table public.child_devices
  add column if not exists installed_apps jsonb not null default '[]'::jsonb,
  add column if not exists installed_apps_updated_at timestamptz,
  add column if not exists allowed_apps jsonb not null default '[]'::jsonb,
  add column if not exists allowed_apps_version bigint not null default 0,
  add column if not exists allowed_apps_updated_at timestamptz;

alter table public.child_devices
  drop constraint if exists child_devices_installed_apps_is_array,
  add constraint child_devices_installed_apps_is_array
    check (jsonb_typeof(installed_apps) = 'array'),
  drop constraint if exists child_devices_allowed_apps_is_array,
  add constraint child_devices_allowed_apps_is_array
    check (jsonb_typeof(allowed_apps) = 'array'),
  drop constraint if exists child_devices_allowed_apps_version_check,
  add constraint child_devices_allowed_apps_version_check
    check (allowed_apps_version >= 0);

alter table public.device_commands
  drop constraint if exists device_commands_command_check,
  add constraint device_commands_command_check
    check (
      command in (
        'LOCK',
        'UNLOCK',
        'BONUS_TIME',
        'SYNC_SCHEDULE',
        'SYNC_ALLOWED_APPS'
      )
    );
