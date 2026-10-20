alter table public.geofence_zones
  drop constraint if exists geofence_zones_radius_meters_check;

alter table public.geofence_zones
  add constraint geofence_zones_radius_meters_check
  check (radius_meters between 50 and 5000);
