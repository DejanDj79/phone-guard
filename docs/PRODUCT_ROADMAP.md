# PhoneGuard Product Roadmap

_Last updated: 2026-09-25_

This document tracks product decisions for the future Play Store release, especially which features should remain available in the free version and which features are candidates for a paid/premium version.

## Product direction

PhoneGuard should have a useful free version that covers the core parental-control workflow, while more advanced controls and convenience features can be reserved for a paid version.

The exact subscription / one-time-purchase model is **not decided yet**.


## App roles, accounts and onboarding

**Decision:** Parent and Child remain separate Android applications.

### Parent

- A Parent account is required before Parent controls are available.
- Supported sign-in methods:
  - Continue with Google
  - Continue with email and password
- Supabase Auth owns the Parent cloud session.
- A local 4–6 digit Parent PIN and optional biometrics remain a second, device-local security layer.
- Local Child pairing data is scoped to the authenticated Parent account.
- Existing prototype pairing data is migrated to the first Parent account used after the auth upgrade.
- Long term, the Parent account will own its Child-device relationships in the backend so account recovery can restore access on a replacement Parent phone.

Initial Parent flow:

1. Welcome
2. Continue with Google or Continue with email
3. Create/sign in to Parent account
4. Create/unlock local Parent PIN
5. Add or manage Child devices
6. Parent dashboard

### Child

- Child devices do **not** have independent email/password or social-login accounts.
- A Child device is enrolled through secure pairing with an authenticated Parent account.
- Child setup continues into the protection/permissions wizard after pairing.

Initial Child flow:

1. Welcome
2. Connect to Parent by pairing code / QR
3. Grant required protection permissions
4. Verify Parent connection
5. Setup complete / Child dashboard


## Free version — current foundation

The current implementation already includes the main foundation that should remain available in the base product:

- Parent / Child pairing
- Multiple Child devices
- Remote lock / unlock
- Bonus time
- Weekly lock schedule
- Daily total screen-time limit
- Allowed apps while the phone is locked
- Request More Time flow
- Protection status
- Protection alerts
- Protection history
- Clear protection history
- Device presence / last seen / possible shutdown state
- App usage dashboard
  - Today
  - Yesterday
  - 7 days
  - Top apps
  - Daily usage overview
- Child Setup / Protection wizard
  - Screen protection check
  - Background protection check
  - Exact timing check
  - Parent connection check
  - Re-runnable setup check from Child dashboard
- Parent end-to-end connection test
- Parent notification category settings
  - Time requests
  - Protection alerts
- Reboot recovery / background protection handling

The visual design and final UX are still to be redesigned before release.

## Reliability / protection validation

The current prototype has also passed the following physical reliability checks:

- Reboot recovery
  - Child recovers without manually opening PhoneGuard.
  - Parent `TEST CONNECTION` succeeds after reboot.
  - Remote `LOCK NOW` still works after reboot.
- Offline command recovery
  - A Parent command sent while the Child is offline is applied after network connectivity returns.
  - No manual Child app launch is required.
- Accessibility protection
  - Disabling Accessibility is detected and reported to Parent.
  - Restoring Accessibility recovers normal command handling.
- App Info protection
  - Opening PhoneGuard App Info is detected.
  - Opening ordinary Android Settings no longer creates a false App Info / Clear data alert.
  - Returning to App Info after cancelling a dialog does not create a duplicate App Info event.
- Clear data protection
  - A real Clear data / Clear storage attempt is detected.
  - Cancelling the confirmation does not create a duplicate App Info event.
- Force stop protection
  - A real Force stop attempt is detected.
  - Cancelling the confirmation does not create a duplicate App Info event.
- Battery optimization protection
  - Removing PhoneGuard from unrestricted/background-protected mode is detected.
  - Parent notification, status and Protection History update correctly.
- Parent protection refresh
  - Parent protection status/history polling was shortened from 15 seconds to 5 seconds while the relevant screen is open.
- Parent connection test semantics
  - `TEST CONNECTION` verifies the Parent -> backend -> Child -> ACK communication path.
  - It can still return OK when an individual protection capability is disabled; protection capability state is reported separately.

### Exact alarm / precise timing note

Physical testing on the current Xiaomi/HyperOS Child device showed that the visible Android `Alarms & reminders` switch and AppOps output do not always map one-to-one to the effective result returned by Android's exact-alarm capability API.

PhoneGuard therefore treats `Precise timing` as a capability status reported by the Child (`AlarmManager.canScheduleExactAlarms()`), rather than mirroring the visible Settings switch position.

During ADB diagnostics, package and UID AppOps could show different values, and the package permission still reported `SCHEDULE_EXACT_ALARM: granted=true`. This device-specific behavior should be kept in mind during future OEM testing rather than treating the Settings toggle itself as the source of truth.


## Paid / Premium candidates

### 1. Per-app daily limits

**Status:** Reserved for paid version.

Allow the Parent to set an individual daily limit for selected apps.

Examples:

- TikTok — 45 min/day
- YouTube — 60 min/day
- Games — 30 min/day
- Chrome — no limit

Expected behavior:

- PhoneGuard tracks usage per app.
- When an app reaches its individual limit, only that app is blocked.
- Other apps and the phone itself remain usable unless another PhoneGuard rule is active.
- Parent can see used / remaining time for each limited app.
- Parent can optionally add bonus time to a specific app.
- Limits reset daily.

Technical foundation already exists through the current foreground app-usage tracking.

### 2. New app installed alerts

**Status:** Reserved for paid version.

Notify the Parent when a new application appears on a Child device.

Expected behavior:

- Detect newly installed apps.
- Send a Parent notification with the app name and Child device.
- Show the new app in the Parent app list.
- Allow the Parent to immediately review its settings.
- Later this can connect directly to per-app limits and other app-specific rules.

Existing app inventory synchronization should provide part of the technical foundation for this feature.

## Future premium candidates — not decided

These are ideas to evaluate later and are **not yet assigned to free or paid**:

- Location tracking
- Geofencing
- Location history
- More detailed usage analytics
- Longer usage-history retention
- Advanced reports
- Scheduled per-app rules
- App categories / category limits
- Web filtering / content filtering
- Parent activity reports / weekly summaries
- Additional Parent accounts / family sharing
- Cloud backup / restore of Parent rules

Do not implement or paywall these until we explicitly decide their product tier.

## Play Store / monetization tasks for later

Before publication we still need to decide:

- Free vs Premium feature matrix
- Subscription vs one-time purchase
- Trial period, if any
- Google Play Billing integration
- Upgrade / restore-purchase flow
- What happens to premium rules when a subscription expires
- Privacy policy
- Data Safety form
- Permissions justification
- Child-safety / parental-control Play Store policy compliance
- Store listing, screenshots and onboarding
- Final UI/UX redesign

## Decision log

### 2026-09-25

- App usage tracking was completed as part of the base product.
- Child Setup / Protection wizard was completed and physically tested.
- Parent end-to-end connection test was completed and physically tested.
- Parent notification category controls were completed and physically tested.
- App Info protection detection was hardened for Android/MIUI variants after physical testing.
- Reboot recovery and offline command recovery were physically tested successfully.
- Accessibility disable/restore protection was physically tested successfully.
- App Info / Clear data / Force stop protection false positives were fixed and physically retested.
- Battery optimization protection notification, status and history were physically tested successfully.
- Parent protection status/history refresh was reduced to 5 seconds while the relevant screen is open.
- Exact-alarm / Precise timing behavior on Xiaomi/HyperOS was documented as an Android capability check rather than a direct mirror of the visible Settings toggle.
- The current reliability pass was completed before starting the planned Parent UI redesign.
- **Per-app daily limits** were intentionally postponed and reserved as a Premium candidate.
- **New app installed alerts** were intentionally postponed and reserved as a Premium candidate.
- UI appearance will be redesigned later; current screens are functional prototypes.
- Parent and Child will remain separate Android apps; there will be no Parent/Child role picker inside one APK.
- Parent authentication is required and will support Google plus email/password through Supabase Auth.
- Child devices will not have independent user accounts; they are enrolled through Parent pairing.
- Parent onboarding/authentication is being implemented before the final Parent dashboard redesign.
