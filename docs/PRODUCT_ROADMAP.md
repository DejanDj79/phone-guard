# PhoneGuard Product Roadmap

_Last updated: 2026-09-26 — Warm Modern Parent UI in final polish phase_

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

The current approved Parent shell uses:

- A transparent rounded top header that lets the center-lit horizontal root gradient continue behind it.
- A thin darker header outline; no separate filled header panel.
- Home: Parent/account control on the left and connection-test status action on the right.
- Schedule / Apps / Device: Back control on the left and Parent/account control on the right.
- Parent account menu is an in-window overlay rather than a Material popup. It contains:
  - Parent identity
  - currently managed Child
  - Manage children
  - Settings
  - Sign out
- Primary navigation is a floating, icon-only bottom bar with:
  - Home
  - Schedule
  - Apps
  - Device
- The selected bottom destination uses one orange circular indicator that slides with a spring/bounce animation.
- Settings is intentionally secondary and is opened from the Parent account menu.
- Android native navigation controls remain hidden during normal use; the floating Parent navigation is fixed and no longer follows navigation-bar inset changes.
- Current top and bottom navigation structure has been physically reviewed and accepted.

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

#### Completed / physically accepted

- [x] Parent authentication / account-aware local storage
- [x] Parent pairing foundation
- [x] Child five-step setup / protection wizard
- [x] Clarified Child Change Parent flow
- [x] Warm Modern Parent design system
  - center-lit horizontal gradient
  - warm near-white surfaces
  - graphite text
  - orange active-state accent
  - Michroma display/labels + Manrope body
- [x] Parent top header and floating bottom navigation
- [x] Account menu converted to an in-window overlay; repeated account-icon tap closes it correctly
- [x] Bottom-navigation jump caused by Android navigation-bar insets fixed
- [x] Parent action-button system
  - 52dp minimum height
  - content-width standalone actions
  - centered standalone actions
  - 12dp corner radius
  - paired/segmented controls keep shared-row width
- [x] Startup Parent security / biometric screen redesigned in the Warm Modern style
  - custom fingerprint/lock screen first
  - native Android BiometricPrompt opens only after explicit biometric action
  - PIN remains fallback
- [x] Parent connection-test action moved to Home header with idle/loading/success/failure visual states
- [x] Device Protection history preview limited to five events, with dedicated full History screen

#### Implemented — keep under physical review while polishing

- [ ] Parent Home final visual pass
  - Recent unlocked activity timeline
  - Usage overview Today / Yesterday / 7 days
  - top-five real app cards
  - real weekly bar chart
- [ ] Schedule overview/editor final visual review
- [ ] Apps overview / Allowed Apps editor final visual review
- [ ] Device / protection page final visual review
- [ ] Settings page final visual review
- [ ] Devices / Device management secondary-screen visual review

#### Icon system

- [x] User-supplied custom outline SVG icon set converted to Android VectorDrawable resources and integrated across most Parent UI.
- [x] Text action buttons intentionally remain text-only; button icons were removed where not needed.
- [ ] Replace the three remaining temporary Material icons when matching custom SVGs are supplied:
  - Protection history
  - Edit schedule / calendar
  - Notifications / bell
- [ ] Before release, record Flaticon/source/license/attribution metadata for every externally supplied icon as required.

#### Still to do before release

- [ ] Pairing / authentication / setup visual polish pass using the finalized Warm Modern system
- [ ] Small-screen layout pass
- [ ] Accessibility pass
  - touch targets
  - TalkBack/content descriptions
  - contrast
  - text scaling
- [ ] Decide whether a dark theme is required for v1; if yes, implement and physically review it
- [ ] Full Parent + Child regression test after the UI/icon pass
- [ ] Final Free vs Premium feature gating implementation
  - Per-app daily limits remain Premium candidate
  - New-app-installed alerts remain Premium candidate
- [ ] Release preparation
  - icon/font/license credits
  - versioning/signing verification
  - Play Store assets/listing/privacy requirements
  - release build smoke test

The tracker above is the current source of truth. Older entries in the Decision log are retained as history and may describe directions that were later superseded.



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

- Top headers now retain the root gradient while adding a thin darker outline and rounded lower corners so their shape remains visible without introducing a separate fill layer.

- Bottom navigation is now a true overlay instead of a Scaffold bottomBar, so no reserved background strip sits behind its shadow. Main scroll content keeps internal bottom breathing room only. The selected orange circle is a single shared indicator that slides between icons using a spring/bounce animation.

- Home connection test moved into the top-right header as a compact status action: neutral gray refresh icon when idle, spinner while testing, green check on confirmed success, red X on failure/timeout, then automatic reset to idle after about 3 seconds. Textual Test Connection controls/results were removed from Home content. Awaiting physical review.

- Usage overview filters now use custom equal-width centered segments so Today/Yesterday/7 days fit cleanly with Michroma. Top-five app cards no longer show rank numbers. Recent activity's UNLOCKED PHONE label is neutral and the app timeline starts with more breathing room below the header. Awaiting physical review.

- Bottom navigation icons now use rounded modern variants: Home, CalendarMonth, GridView and Smartphone. Awaiting physical review.

- Device Protection history preview is capped at 5 events. Longer history opens in a dedicated Protection history screen with its own Back header and clear-history action. Awaiting physical review.

- Floating bottom-nav position is no longer tied to navigation-bar insets. The Parent account dropdown is non-focusable so opening it should not surface Android native navigation controls or make the floating bar jump vertically. Awaiting physical verification.

- Parent buttons now use a taller content-sized action style: standard Button/OutlinedButton/TextButton controls target a 52dp minimum height and no longer stretch full-width unless they are intentional paired/segmented controls. Awaiting physical review.

- Parent account icon now toggles its menu open/closed on repeated taps while keeping the non-focusable popup behavior that prevents native navigation controls from shifting the floating bottom bar.

- Parent account menu now renders as an in-window overlay instead of Material DropdownMenu/Popup. Repeated taps on the account icon reliably toggle it open/closed, Back closes it, and it remains inside the immersive Parent window so native navigation controls should not reappear.

- Action buttons now use a 12dp corner radius, retain the 52dp minimum height, and standalone actions are visually centered in their card/screen while staying content-width. Intentional paired controls keep their shared-row layout. Usage overview segments use the same compact radius. Awaiting physical review.

- Startup biometric unlock now has a dedicated Warm Modern screen: the shared Parent gradient remains visible, a centered bordered card presents a fingerprint/lock visual, Michroma headings and compact 12dp-radius actions. Biometric unlock is now explicitly triggered from the custom screen instead of auto-opening the native Android prompt immediately; PIN remains the secondary fallback. The native BiometricPrompt text was simplified to match. Awaiting physical review.

- Custom Parent outline icon set imported from user-supplied SVGs and converted to Android VectorDrawable resources. Header, bottom navigation, account menu, connection test states, biometric screen, protection status, Settings icons, Back controls, app fallback icon and icon-only save now use the custom set. Text action buttons were simplified to text-only. Three temporary Material icons remain until matching SVGs are supplied: Protection history, Edit schedule/calendar and Notifications/bell. Awaiting physical review.

- Before publication, record the source/license metadata for the externally supplied icon set (including any required Flaticon attribution) in the project documentation/app credits as applicable.

- Roadmap tracker was consolidated on 2026-09-26 so the current accepted Warm Modern shell, button system, biometric entry screen, custom icon migration and remaining pre-release work are visible in one authoritative checklist. Older decision-log entries remain historical and can describe superseded UI directions.
