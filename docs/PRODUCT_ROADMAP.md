# PhoneGuard Product Roadmap

_Last updated: 2026-09-25 — modern UI direction approved_

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


## UI / UX direction and implementation status

**Design direction approved:** modern, minimalist Android UI rather than the current prototype/admin-panel appearance.

### Global Parent app shell

The Parent app should use:

- A clean Material 3 top app bar.
- Hamburger/menu icon on the left.
- Current Child device context visible in the top bar.
- Parent account/avatar icon on the right.
- A navigation drawer instead of the current top tab row.
- Main drawer destinations:
  - Home
  - Schedule
  - Apps
  - Device
  - Settings
- Account menu should expose the signed-in Parent identity and account actions such as Settings and Sign out.
- Child switching remains a separate device-selection action and should not be confused with Parent account switching.

### Parent Home

Home is the main command center and should prioritize only information that matters now:

1. Child status hero area
   - Child name
   - Online / last seen
   - Available / locked / bonus-time state
2. Primary controls
   - Lock now or Unlock, depending on current state
   - Add bonus time
3. Compact daily summary
   - Screen time today
   - Daily limit / remaining time
4. Contextual attention card
   - Only shown when the Child is offline for a meaningful period or a protection capability needs attention
5. Recent / relevant activity
   - Time requests, recent control feedback and important protection events
6. Detailed usage remains available without overwhelming the first viewport.

### Visual rules

- Prefer whitespace and typography over many bordered boxes.
- Avoid repeating the same device status in multiple cards.
- Keep only one obvious primary action in a section.
- Use status colors only when they carry meaning.
- Hide healthy-state warnings instead of showing permanent "all good" panels.
- Avoid technical/backend language in normal product UI.
- Keep Free and Premium visual treatment consistent; do not advertise unfinished Premium features in the Free dashboard.

### UI implementation tracker

- [x] Parent authentication / account-aware local storage
- [x] Parent pairing flow redesign
- [x] Child five-step setup wizard
- [x] Clarified Child Change Parent flow
- [ ] Parent modern app shell: drawer/hamburger replaced by floating icon-only bottom navigation with Home, Schedule, Apps and Device. Parent menu lives in the top account icon and contains Manage children, Settings and Sign out. Chrome now follows the Warm Modern light-surface + orange-accent direction. Awaiting final physical UI approval
- [ ] Parent Home final minimalist redesign — Warm Modern palette + Michroma/Manrope typography pass implemented; awaiting final physical UI approval
- [ ] Schedule screen redesign — overview implemented; editor uses one Mon–Sun strip and a compact selected-day Material 3 dial picker integrated directly into the selected-day card. There is no separate day enable switch: `00:00–00:00` means inactive; changing either time activates the day. Awaiting physical review
- [ ] Apps screen redesign — overview and allowed-app editor implemented in new visual system; awaiting physical review
- [ ] Device / protection screen redesign — implemented in new visual system; awaiting physical review
- [ ] Settings screen redesign — implemented in new visual system; awaiting physical review
- [ ] Pairing/setup visual polish pass after the main design system is established
- [ ] Final accessibility, small-screen and dark-theme pass

The current implementation should be updated incrementally against this tracker. When a UI phase is completed and physically reviewed, mark it complete here rather than relying only on chat history.


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
- The earlier prototype/admin-style dashboard was rejected in favor of a modern minimalist visual direction with a top app bar, navigation drawer and account menu.
- Parent and Child will remain separate Android apps; there will be no Parent/Child role picker inside one APK.
- Parent authentication is required and will support Google plus email/password through Supabase Auth.
- Child devices will not have independent user accounts; they are enrolled through Parent pairing.
- Parent onboarding/authentication is being implemented before the final Parent dashboard redesign.
- Parent top tabs will be removed; the approved navigation direction is a hamburger drawer plus a Parent account/avatar action in the top app bar.
- `docs/PRODUCT_ROADMAP.md` is the persistent source of truth for UI phases and should be updated as each phase is completed.
- First physical review of the new Parent shell confirmed the navigation direction is better, but the Home screen still felt too repetitive and admin-like.
- Follow-up design requirements: centered page title; no Child subtitle in the top bar; Child name and last-seen aligned in the hero; avoid duplicate Available/Online messaging; replace command-status text with inline button progress indicators. Earlier warm yellow, cyan/teal, cyan+purple and indigo/blush experiments were rejected or superseded. Current visual experiment uses **Michroma** for display/header/label styles, **Manrope** for body text, a subtle `#F2F2EF → #E4E4E0` background gradient, warm near-white surfaces and `#E83E1D` orange accents.

- Android system Back navigation now follows Parent UI state: detail/edit screens return to their previous Parent screen, non-Home drawer sections return to Home first, and only Home without an open child screen exits normally.
- Main Parent content and full-screen editors now use more top spacing / status-bar-aware padding.

- Detail-page headers use the same animated teal surface with a circular Back control; the header slides in from the top when entering Schedule editor, Allowed Apps, Devices, and Device management.

- Animated non-Home/detail headers keep the rounded Back pattern but now use soft light surfaces; orange `#E83E1D` is reserved for icon/accent states.

- Bottom navigation is reserved for the four primary Parent destinations: Home, Schedule, Apps and Device. Settings is intentionally secondary and is opened from the Parent account menu.

- Bottom navigation height was physically reviewed on-device and accepted; keep the current height unless later layout changes require adjustment.

- Parent account menu remains compact and shows Parent identity plus the currently managed Child, with Manage children, Settings and Sign out actions; its button now follows the light-surface/orange-accent theme.

- Parent bottom navigation is floating, rounded and icon-only with side/bottom breathing room; the bar is now a soft light surface and the selected circular icon uses `#E83E1D`. Android's persistent navigation bar/buttons remain hidden with transient swipe access. Awaiting physical review.

- Apps overview now lists allowed apps directly with today's real usage time on the right. Child inventory carries a compact real launcher icon (`iconBase64`) through `sync-app-inventory`; Parent renders it with a fallback icon. `sync-app-inventory` v2 is deployed. Awaiting physical review after updating both Child and Parent APKs.

- Allowed Apps editor now keeps `SAVE` pinned in the lower-right corner while the app list scrolls underneath, with reserved bottom space so content is never covered. Top section/detail titles are now smaller and uppercase for a cleaner header hierarchy. Awaiting physical review.

- Allowed Apps save action remains a circular floating check button pinned bottom-right; it now inherits the orange accent theme, with an inline spinner while saving.

- Device overview received a second consumer-style redesign: white device hero with compact action tiles, three visual protection status tiles, and a denser Protection history card. Awaiting physical review.

- The indigo/blush trial was superseded by a MetroPulse-inspired **Warm Modern** direction: very subtle vertical background gradient `#F8F8F6 → #EEEEEB`, warm near-white surfaces, dark graphite text and orange accent `#E83E1D`. Awaiting physical approval.

- Home now includes a Recent activity timeline for unlocked-phone usage only. Child records individual app sessions (start/end/duration) only while PhoneGuard is effectively unlocked, heartbeat stores those sessions inside the existing daily app-usage JSON, and Parent shows up to six newest sessions with app icons, start time and duration. Existing aggregate Today/Yesterday/7 days view remains as Usage overview. Awaiting physical review with updated Child + Parent APKs.

- Usage overview now presents the top five apps as individual cards with real app icons and usage duration for Today/Yesterday. The 7-day view adds a real Compose bar chart plus the same top-five cards; the previous text-based bar visualization is superseded. Awaiting physical review.

- Bottom navigation labels were removed. The floating bar now contains four circular icon-only destinations; selected destination uses a white circle with indigo icon, unselected destinations use translucent white circles with white icons. Awaiting physical review.

- Michroma was bundled from the official Google Fonts repository with its OFL license. It is used for display/header/label typography; Manrope remains the body font for readability.

- Warm Modern refinement: default actions are outline-only with neutral graphite text/borders; orange `#E83E1D` is reserved for active/selected states such as selected schedule day/time target, selected usage filter, checked switches, selected relock option and selected bottom-nav destination.

- The floating bottom navigation base is transparent and contributes only a floating shadow; only the four circular icon buttons are visible. The background gradient was darkened slightly to `#F2F2EF → #E4E4E0` after physical feedback.

- Parent background now uses a center-lit horizontal gradient rather than a vertical one: darker `#DEDEDA` edges, a lighter `#F3F3F0` center, and intermediate `#E7E7E3` stops. Main and detail headers are transparent so the same root gradient continues behind Back/account controls.

- Bottom navigation now has exactly one visible rounded surface with shadow; the Scaffold/outer area remains transparent so there is no second gray strip behind the shadow. Icons remain label-free, with orange used only for the selected circular destination.
