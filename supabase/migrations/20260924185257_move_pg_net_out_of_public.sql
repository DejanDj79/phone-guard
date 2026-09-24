do $$
begin
  if exists (
    select 1 from cron.job
    where jobname = 'phoneguard-check-offline-devices'
  ) then
    perform cron.unschedule('phoneguard-check-offline-devices');
  end if;
end
$$;

drop extension if exists pg_net;
create extension pg_net with schema extensions;

select cron.schedule(
  'phoneguard-check-offline-devices',
  '* * * * *',
  $cron$
  select net.http_post(
    url := (
      select decrypted_secret
      from vault.decrypted_secrets
      where name = 'project_url'
    ) || '/functions/v1/check-offline-devices',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'apikey', (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'publishable_key'
      ),
      'Authorization', 'Bearer ' || (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'publishable_key'
      )
    ),
    body := jsonb_build_object('scheduled_at', now()),
    timeout_milliseconds := 10000
  ) as request_id;
  $cron$
);
