# PhoneGuard Free v1 Release Checklist

_Last updated: 2026-09-26_

This checklist is the final release-hardening plan for the PhoneGuard Free v1 scope.

The Free feature set and primary UI/UX are considered complete. This document is for regression testing, reliability verification, accessibility/small-screen review and final release preparation. New Free features should not be added during this phase unless required to fix a regression, safety issue or Play Store requirement.

Related product source of truth: `docs/PRODUCT_ROADMAP.md`.

---

## How to use this checklist

For each test:

- [ ] Run the exact scenario.
- [ ] Mark it PASS only when the expected result is observed on a physical device.
- [ ] If it fails, record the issue before changing unrelated UI or behavior.
- [ ] Re-run the failed scenario after the fix.
- [ ] Re-run the relevant neighboring scenarios when a fix touches shared code.

Recommended test setup:

- One Parent Android phone
- One Child Android phone
- Optional second Child phone for multi-device testing
- Wi-Fi and mobile data available
- Ability to temporarily disable network access
- Ability to reboot the Child phone
- Access to Android Accessibility, Battery optimization, App info and Alarms & reminders settings

---

# 1. Build and install sanity

## 1.1 Debug build

- [ ] Run:

```powershell
./gradlew :app:assembleDebug :parent:assembleDebug
```

PASS when:

- [ ] Child debug APK builds without compilation errors.
- [ ] Parent debug APK builds without compilation errors.
- [ ] No new blocking Gradle errors are present.
- [ ] Known deprecation warnings do not prevent build completion.

## 1.2 Install both APKs

- [ ] Install Child APK.
- [ ] Install Parent APK.
- [ ] Launch both apps.

PASS when:

- [ ] Both apps start successfully.
- [ ] No startup crash occurs.
- [ ] Parent root gradient and current UI theme render correctly.
- [ ] Child root gradient and current UI theme render correctly.

---

# 2. Parent security and authentication

## 2.1 Existing Parent session

- [ ] Open Parent app with an existing signed-in account.

PASS when:

- [ ] Parent security screen appears before dashboard access.
- [ ] PIN input is centered.
- [ ] PIN field width matches the biometric action width.
- [ ] Correct PIN unlocks the app.
- [ ] Incorrect PIN stays locked and shows an error.

## 2.2 Biometrics

When biometrics are enabled and available:

- [ ] Tap the biometric unlock action.
- [ ] Complete biometric authentication.
- [ ] Cancel biometric authentication once and verify fallback behavior.

PASS when:

- [ ] Native biometric prompt opens only after explicit biometric action.
- [ ] Successful biometric authentication unlocks Parent.
- [ ] Cancelling biometric authentication does not bypass Parent security.
- [ ] PIN remains available as fallback.

## 2.3 Parent relock behavior

- [ ] Open Parent and unlock it.
- [ ] Put the app in background longer than the configured relock interval.
- [ ] Return to Parent.

PASS when:

- [ ] Parent security is required again according to the configured relock rule.

---

# 3. Clean-install onboarding

This section should be run at least once with fresh app data on both Parent and Child.

## 3.1 Parent clean start

- [ ] Clear Parent app data or install fresh.
- [ ] Launch Parent.
- [ ] Create/sign in to Parent account.
- [ ] Create local Parent PIN.
- [ ] Enable biometrics if desired.

PASS when:

- [ ] Authentication completes.
- [ ] Local PIN is created successfully.
- [ ] Parent reaches the add-child flow without stale device data.

## 3.2 Child clean start

- [ ] Clear Child app data or install fresh.
- [ ] Launch Child.
- [ ] Complete local Parent PIN setup if shown.
- [ ] Complete Accessibility protection step.
- [ ] Complete battery/background protection step.
- [ ] Complete precise timing step.
- [ ] Reach Parent pairing step.

PASS when:

- [ ] Setup wizard progresses correctly.
- [ ] Completed checks remain recognized when returning to the wizard.
- [ ] Child does not require its own email/password account.
- [ ] Pairing code is shown and can be used by Parent.

---

# 4. Pairing and multi-device management

## 4.1 First Child pairing

- [ ] Start pairing from fresh Parent state.
- [ ] Enter the temporary 6-character Child pairing code.
- [ ] Complete pairing.

PASS when:

- [ ] Pairing succeeds.
- [ ] Parent displays the paired Child.
- [ ] Child reports Parent connection as healthy.
- [ ] Control token/device relationship survives Parent restart.

## 4.2 Invalid pairing code

- [ ] Enter an invalid or expired pairing code.

PASS when:

- [ ] Pairing is rejected.
- [ ] Existing paired devices are not affected.
- [ ] User receives a clear error message.

## 4.3 Add another Child

- [ ] Open Devices.
- [ ] Tap Add Child.
- [ ] Verify pairing opens as a bottom sheet over Devices.
- [ ] Pair a second Child if a second physical device is available.

PASS when:

- [ ] Devices screen remains visible behind the pairing sheet.
- [ ] Existing Child stays paired.
- [ ] New Child is added successfully.
- [ ] Parent can switch between paired Children.

## 4.4 Rename Child

- [ ] Rename a paired Child from Device management.

PASS when:

- [ ] New name appears across Parent UI.
- [ ] Name survives Parent restart.

## 4.5 Unpair Child

- [ ] Unpair one Child.

PASS when:

- [ ] Only the selected Child is removed.
- [ ] Other paired Children remain available.
- [ ] Parent no longer controls the removed Child.

---

# 5. Parent Home regression

## 5.1 Child identity and presence

- [ ] Open Home with Child online.

PASS when:

- [ ] Child name is shown.
- [ ] Battery icon and percentage appear beside the Child name.
- [ ] Last-seen state is sensible.
- [ ] Online/offline state matches actual device availability.

## 5.2 Header and notch scrolling

- [ ] Scroll Home upward through several cards.

PASS when:

- [ ] Content moves underneath the top header.
- [ ] Content is visually clipped/masked by the exact lower header border.
- [ ] Content passes around the left/right shoulders and central notch.
- [ ] Content does not draw over the header title/icon/account/test controls.
- [ ] No rectangular clipping edge appears below the header.

Repeat on:

- [ ] Home
- [ ] Schedule
- [ ] Apps
- [ ] Device
- [ ] Settings if scrollable

## 5.3 Header navigation

- [ ] Swipe horizontally between Home, Schedule, Apps and Device.
- [ ] Tap bottom navigation destinations.
- [ ] Open Settings through account menu.

PASS when:

- [ ] Correct page title and icon appear.
- [ ] Four page-position dots show the correct active page for Home/Schedule/Apps/Device.
- [ ] Bottom navigation selected indicator moves correctly.
- [ ] Settings has no incorrect primary-nav dot state.

## 5.4 Connection test

- [ ] Run TEST CONNECTION while Child is online.

PASS when:

- [ ] Header action shows loading state.
- [ ] Success state appears after full Parent -> backend -> Child -> ACK path succeeds.
- [ ] Action returns to idle afterward.

- [ ] Repeat while Child is offline.

PASS when:

- [ ] Failure/timeout state appears.
- [ ] Parent does not falsely report successful end-to-end communication.

---

# 6. Lock / unlock control

## 6.1 Lock now

- [ ] With Child unlocked, send LOCK NOW.

PASS when:

- [ ] Command is accepted.
- [ ] Child becomes locked.
- [ ] Parent updates to locked state.
- [ ] Child lock screen appears as designed.

## 6.2 Unlock

- [ ] With Child locked, send UNLOCK.

PASS when:

- [ ] Child becomes available.
- [ ] Parent reflects unlocked/available state.

## 6.3 Accessibility lock overlay

- [ ] Trigger a locked state while Accessibility enforcement is active.

PASS when:

- [ ] Accessibility overlay and Compose lock screen remain visually aligned.
- [ ] Both use the current Child palette.
- [ ] No bypass is possible through normal navigation.

---

# 7. Bonus time

## 7.1 Manual bonus time

- [ ] Lock Child.
- [ ] Grant bonus time from Parent.

PASS when:

- [ ] Child becomes temporarily allowed.
- [ ] Parent shows:
  - [ ] Approved X minutes
  - [ ] Left Y minutes
- [ ] Remaining minutes decrease over time.

## 7.2 Parent restart during active bonus

- [ ] While bonus time is active, fully close Parent.
- [ ] Reopen Parent immediately.

PASS when:

- [ ] Temporary-access state is still shown.
- [ ] Approved amount is still shown.
- [ ] Remaining amount is still shown.
- [ ] Status is not replaced by a transient-only message.

## 7.3 Bonus expiration

- [ ] Let temporary access expire.

PASS when:

- [ ] Child returns to the correct enforced state.
- [ ] Parent no longer shows stale Approved/Left temporary-access status.

## 7.4 Lock / unlock clears temporary grant state

- [ ] Grant bonus time.
- [ ] Send LOCK or UNLOCK according to the current control flow.

PASS when:

- [ ] Obsolete temporary-grant metadata is cleared when it should no longer apply.

---

# 8. Request More Time

## 8.1 Child sends request

- [ ] From Child lock screen, submit Request More Time.

PASS when:

- [ ] Parent receives the pending request.
- [ ] Correct Child and requested duration are shown.

## 8.2 Approve request

- [ ] Approve the request.

PASS when:

- [ ] Child receives temporary access.
- [ ] Parent immediately shows Approved/Left state.
- [ ] Closing and reopening Parent preserves the status.

## 8.3 Reject request

- [ ] Submit another request.
- [ ] Reject it.

PASS when:

- [ ] Child remains locked.
- [ ] Request is resolved and no longer remains pending.

---

# 9. Weekly lock schedule

## 9.1 Schedule editor UI

- [ ] Open Schedule > Edit Schedule.
- [ ] Select multiple days.
- [ ] Change FROM and TO values.

PASS when:

- [ ] Day selection works.
- [ ] FROM/TO selection works.
- [ ] Time picker uses the current Parent visual treatment.
- [ ] Time picker background gradient renders correctly.
- [ ] Active and inactive time-picker elements remain readable.

## 9.2 Save schedule

- [ ] Configure a valid schedule and save it.

PASS when:

- [ ] Saved values return correctly when editor is reopened.
- [ ] Child receives the schedule.
- [ ] Child locks/unlocks according to schedule timing.

## 9.3 Inactive day

- [ ] Set a day to 00:00 – 00:00.

PASS when:

- [ ] Day is treated as inactive.
- [ ] UI clearly shows inactive state.

## 9.4 Invalid equal times

- [ ] Attempt to save the same non-zero start/end time.

PASS when:

- [ ] Validation prevents invalid schedule save.
- [ ] Clear error is shown.

---

# 10. Daily screen-time limit

## 10.1 Set daily limit

- [ ] Set a daily limit.

PASS when:

- [ ] Limit persists.
- [ ] Used and remaining values update.
- [ ] Child becomes restricted when the limit is reached.

## 10.2 Change daily limit

- [ ] Change an existing daily limit.

PASS when:

- [ ] New value replaces previous value.
- [ ] Parent and Child remain synchronized.

## 10.3 Remove daily limit

- [ ] Remove the limit.

PASS when:

- [ ] Parent shows no daily limit.
- [ ] Child no longer enforces the removed daily total limit.

## 10.4 Allowed-app usage exclusion

- [ ] While Child is locked, use an allowed app.

PASS when:

- [ ] Allowed-app usage does not count toward normal daily screen-time total, according to current product behavior.

---

# 11. Allowed apps

## 11.1 Inventory load

- [ ] Open Apps.

PASS when:

- [ ] Installed apps load.
- [ ] Real launcher icons display when available.
- [ ] Fallback icon displays when no icon is available.

## 11.2 Change allowed apps

- [ ] Open Allowed Apps editor.
- [ ] Enable one app and disable another.
- [ ] Save.

PASS when:

- [ ] Changes persist.
- [ ] Child receives the updated allowed-app list.

## 11.3 Locked behavior

- [ ] Lock Child.
- [ ] Open an allowed app.
- [ ] Attempt to open a non-allowed app.

PASS when:

- [ ] Allowed app can be used.
- [ ] Non-allowed app is blocked according to current lock behavior.

---

# 12. App usage and Recent Activity

## 12.1 Today

- [ ] Use several apps on Child while effectively unlocked.
- [ ] Open Parent Home > Usage overview > Today.

PASS when:

- [ ] Total usage is plausible.
- [ ] Top apps are ordered correctly.
- [ ] App icons and durations render correctly.
- [ ] Active filter uses transparent background with cyan border.

## 12.2 Yesterday

- [ ] Verify Yesterday when historical data is available.

PASS when:

- [ ] Correct date bucket is shown.
- [ ] Today data is not incorrectly mixed into Yesterday.

## 12.3 Seven days

- [ ] Open 7 days.

PASS when:

- [ ] Weekly bar chart renders.
- [ ] Top app data corresponds to available weekly data.
- [ ] Layout does not overflow.

## 12.4 Recent Activity

- [ ] Generate multiple unlocked app sessions.
- [ ] Return to Home.

PASS when:

- [ ] Recent Activity shows newest sessions first.
- [ ] Up to six recent sessions are shown.
- [ ] UNLOCKED PHONE and TODAY remain on the same row.
- [ ] App list spacing is visually balanced.
- [ ] App start time and duration are shown.
- [ ] Locked-only activity is not incorrectly reported as unlocked-phone activity.

---

# 13. Battery / presence / last seen

## 13.1 Battery refresh

- [ ] Compare Parent battery percentage with Child system battery percentage.

PASS when:

- [ ] Values are reasonably aligned.
- [ ] Battery value refreshes through heartbeat/device status.
- [ ] Missing battery data does not crash or break layout.

## 13.2 Offline Child

- [ ] Disable Child network access.
- [ ] Wait for presence timeout.

PASS when:

- [ ] Parent eventually reports offline/last-seen state.
- [ ] UI remains stable.

## 13.3 Online recovery

- [ ] Restore Child network.

PASS when:

- [ ] Parent returns to online state automatically.
- [ ] Battery/status refresh resumes.

---

# 14. Offline command recovery

## 14.1 Queue command while Child is offline

- [ ] Disable Child network.
- [ ] Send LOCK NOW or another supported remote command from Parent.
- [ ] Restore Child network.

PASS when:

- [ ] Command is queued/accepted according to current backend semantics.
- [ ] Child applies the command after connectivity returns.
- [ ] Child app does not require manual launch.
- [ ] Parent eventually reflects the applied state.

---

# 15. Reboot recovery

## 15.1 Reboot while protected

- [ ] Ensure Child is fully configured.
- [ ] Reboot Child.
- [ ] Do not manually open PhoneGuard.

PASS when:

- [ ] PhoneGuard background/reboot recovery starts correctly.
- [ ] Parent eventually sees Child online again.
- [ ] TEST CONNECTION succeeds after recovery.
- [ ] Remote LOCK NOW works after reboot.

## 15.2 Reboot during active rules

Where practical:

- [ ] Reboot while a schedule/daily restriction is relevant.

PASS when:

- [ ] Protection state is restored correctly after boot.

---

# 16. Protection controls

## 16.1 Accessibility disabled

- [ ] Disable PhoneGuard Accessibility service on Child.

PASS when:

- [ ] Child detects the problem.
- [ ] Parent protection status updates.
- [ ] Parent receives the configured protection alert.
- [ ] Protection History records the event.

- [ ] Re-enable Accessibility.

PASS when:

- [ ] Protection status recovers.
- [ ] Command handling returns to normal.

## 16.2 Battery optimization changed

- [ ] Remove unrestricted/background-protected state.

PASS when:

- [ ] Parent receives the correct protection warning.
- [ ] Device protection status reflects the issue.
- [ ] Protection History records it.

## 16.3 App Info

- [ ] Open PhoneGuard App Info.

PASS when:

- [ ] Event is detected.
- [ ] Ordinary Settings navigation does not create a false App Info event.
- [ ] Returning after cancelling does not create duplicate events.

## 16.4 Clear data / Clear storage

- [ ] Navigate to the real Clear data / Clear storage action.
- [ ] Test detection without actually destroying required test state where possible.
- [ ] Cancel confirmation.

PASS when:

- [ ] Real attempt is detected.
- [ ] Cancel does not create duplicate App Info events.

## 16.5 Force stop

- [ ] Navigate to Force stop.
- [ ] Trigger/cancel according to existing physical-test procedure.

PASS when:

- [ ] Real Force stop attempt is detected.
- [ ] Cancelling does not create duplicate events.

## 16.6 Precise timing

- [ ] Review Precise timing status on the test Child device.

PASS when:

- [ ] PhoneGuard reports Android capability state consistently with `AlarmManager.canScheduleExactAlarms()`.
- [ ] UI does not incorrectly assume the visible OEM settings switch is the sole source of truth.

---

# 17. Protection History

## 17.1 Preview

- [ ] Generate more than five protection events.

PASS when:

- [ ] Device screen preview shows no more than five events.

## 17.2 Full history

- [ ] Open full Protection History.

PASS when:

- [ ] Full list opens.
- [ ] Back returns correctly.
- [ ] Scrolling works.

## 17.3 Clear history

- [ ] Clear Protection History.

PASS when:

- [ ] History clears successfully.
- [ ] Parent refresh reflects empty/new state.

---

# 18. Parent notifications

## 18.1 Time requests category

- [ ] Enable Time request notifications.
- [ ] Send a request from Child.

PASS when:

- [ ] Notification is delivered.

- [ ] Disable the category and repeat.

PASS when:

- [ ] Notification behavior follows the configured category setting.

## 18.2 Protection alerts category

- [ ] Enable Protection alerts.
- [ ] Trigger a protection problem.

PASS when:

- [ ] Notification is delivered.

- [ ] Disable the category and repeat.

PASS when:

- [ ] Notification behavior follows the configured category setting.

---

# 19. Parent app restart / process-death resilience

Run these independently:

- [ ] Restart Parent while Child is online.
- [ ] Restart Parent while Child is offline.
- [ ] Restart Parent while Child is locked.
- [ ] Restart Parent during active bonus time.
- [ ] Restart Parent with pending time request.
- [ ] Restart Parent after schedule change.
- [ ] Restart Parent after allowed-app change.

PASS when:

- [ ] Paired devices remain available.
- [ ] Current Child selection is sensible.
- [ ] Local account-scoped pairing data is intact.
- [ ] Device state refreshes from backend.
- [ ] No stale transient UI replaces real persisted state.

---

# 20. Child app restart / process recovery

- [ ] Close Child app from recent apps where allowed.
- [ ] Reopen Child.
- [ ] Repeat while locked.
- [ ] Repeat during temporary access.

PASS when:

- [ ] Setup state remains intact.
- [ ] Pairing remains intact.
- [ ] Lock state remains correct.
- [ ] Temporary allowance remains correct.
- [ ] Background protection resumes.

---

# 21. Update-install regression

This test protects existing users when upgrading from an older build.

## 21.1 Parent update

- [ ] Install new Parent APK with `adb install -r` over an existing configured installation.

PASS when:

- [ ] Account session remains valid where expected.
- [ ] Parent PIN remains valid.
- [ ] Biometric preference remains valid.
- [ ] Existing paired devices remain present.
- [ ] Existing settings survive.

## 21.2 Child update

- [ ] Install new Child APK with `adb install -r` over an existing configured installation.

PASS when:

- [ ] Pairing remains valid.
- [ ] Protection settings remain recognized.
- [ ] Existing schedule/daily/allowed-app state remains functional.
- [ ] No setup wizard unexpectedly resets.

## 21.3 Temporary access compatibility

- [ ] Start a new bonus grant with current Parent/Child builds.
- [ ] Verify `temporaryAccessGrantedMinutes` and remaining time survive Parent restart.

PASS when:

- [ ] Persistent Approved/Left status works end-to-end.

Note: bonus sessions that started before the granted-minutes persistence feature may not know the original approved amount. This is acceptable only for pre-upgrade in-progress sessions; new grants must populate it correctly.

---

# 22. Small-screen layout pass

Test at least one smaller Android screen or emulator profile.

Check:

- [ ] Parent security screen
- [ ] Home
- [ ] Schedule
- [ ] Time picker
- [ ] Apps
- [ ] Allowed Apps editor
- [ ] Device
- [ ] Protection History
- [ ] Settings
- [ ] Devices
- [ ] Pair Child bottom sheet
- [ ] Child setup wizard
- [ ] Child dashboard
- [ ] Child lock screen

PASS when:

- [ ] No text is clipped.
- [ ] No buttons are pushed off-screen without scroll access.
- [ ] Bottom navigation does not cover required controls.
- [ ] Header notch does not cover essential content.
- [ ] Dialogs/sheets remain usable.

---

# 23. Accessibility pass

## 23.1 Touch targets

- [ ] Verify primary interactive controls meet practical touch-target sizing.

PASS when:

- [ ] Main actions are easy to tap.
- [ ] Small icon buttons do not require precision tapping.

## 23.2 TalkBack / content descriptions

Check:

- [ ] Header account action
- [ ] Back
- [ ] Connection test
- [ ] Bottom navigation icons
- [ ] Lock/unlock controls
- [ ] Bonus time
- [ ] Device/protection actions
- [ ] Child setup actions

PASS when:

- [ ] Important icon-only actions have meaningful descriptions.
- [ ] Decorative icons are not announced unnecessarily.

## 23.3 Text scaling

- [ ] Increase Android font size.
- [ ] Review major Parent and Child screens.

PASS when:

- [ ] Critical text remains readable.
- [ ] Controls remain usable.
- [ ] Layout does not overlap catastrophically.

## 23.4 Contrast

Review the final palette:

- [ ] `#EFF6FF` text on `#8194B5` cards
- [ ] `#EFF6FF` text on gradient backgrounds
- [ ] `#69DEFF` active borders/icons
- [ ] disabled states
- [ ] error/warning states

PASS when:

- [ ] Important content remains clearly readable in normal device conditions.

---

# 24. Visual consistency pass

Parent:

- [ ] Root gradient matches final palette.
- [ ] Header icon color is consistent.
- [ ] Card colors are consistent.
- [ ] Action corner radius is consistent.
- [ ] Active cyan usage is consistent.
- [ ] No old orange/white Warm Modern styling remains in active screens.
- [ ] No unexpected black text remains.
- [ ] Usage filters are transparent with active cyan border.
- [ ] Recent Activity spacing remains balanced.
- [ ] Schedule time picker matches the final palette.
- [ ] Pairing sheet matches Parent styling.

Child:

- [ ] Setup screens match final palette.
- [ ] Dashboard matches final palette.
- [ ] Compose lock screen matches final palette.
- [ ] Accessibility overlay visually matches Compose lock screen.
- [ ] No obsolete Child color scheme remains.

---

# 25. Navigation and Back behavior

Test:

- [ ] Home -> Schedule -> Back
- [ ] Home -> Apps -> Allowed Apps -> Back
- [ ] Home -> Device -> Protection History -> Back
- [ ] Home -> Devices -> Device management -> Back
- [ ] Account menu -> Settings -> Back
- [ ] Pair Child bottom sheet -> dismiss/back
- [ ] Android system Back from Home

PASS when:

- [ ] Back returns to the expected Parent screen.
- [ ] No unexpected app exit occurs from nested screens.
- [ ] Home remains the final in-app destination before normal app exit.
- [ ] Account overlay closes before navigation changes when appropriate.

---

# 26. Data/account isolation

If two Parent accounts are available:

- [ ] Sign in with Parent account A and pair/use Child data.
- [ ] Sign out.
- [ ] Sign in with Parent account B.

PASS when:

- [ ] Local Child pairing data is scoped correctly.
- [ ] Account B does not accidentally inherit account A's local device list.
- [ ] Returning to A restores A-scoped local state where supported.

---

# 27. Backend smoke test

Verify core live flows still work against the deployed backend:

- [ ] Pair Child
- [ ] Heartbeat/device status
- [ ] Send command
- [ ] Command status
- [ ] Respond to time request
- [ ] App inventory sync
- [ ] App usage sync/fetch
- [ ] Schedule save/fetch
- [ ] Daily limit save/fetch
- [ ] Allowed apps save/fetch
- [ ] Protection history fetch/clear

PASS when:

- [ ] No production Edge Function schema mismatch appears.
- [ ] Parent and Child models parse current responses.
- [ ] No recently added field causes backward-compatibility crashes.

---

# 28. Release configuration

Do not mark this section complete until an actual release candidate is being prepared.

## 28.1 Versioning

- [ ] Set intended Free v1 `versionCode`.
- [ ] Set intended Free v1 `versionName`.

## 28.2 Signing

- [ ] Confirm release keystore strategy.
- [ ] Confirm release signing works.
- [ ] Keep debug/shared development keystore separate from release signing.

## 28.3 Production configuration

- [ ] Verify production Supabase URL/project.
- [ ] Verify Firebase Parent configuration.
- [ ] Verify Firebase Child configuration if applicable.
- [ ] Verify no local/test endpoints remain.

## 28.4 Release build

- [ ] Build release AAB/APK.

PASS when:

- [ ] Release build completes successfully.
- [ ] Release build launches.
- [ ] Parent authentication works.
- [ ] Pairing works.
- [ ] TEST CONNECTION works.
- [ ] LOCK NOW works.
- [ ] No debug-only dependency is required for normal behavior.

---

# 29. Play Store preparation

- [ ] Final app names
- [ ] Final launcher icons
- [ ] Store screenshots
- [ ] Short description
- [ ] Full description
- [ ] Privacy Policy
- [ ] Data Safety form
- [ ] Permissions justification
- [ ] Accessibility-service justification
- [ ] Child-safety / parental-control policy review
- [ ] Content rating
- [ ] Target audience configuration
- [ ] Contact/support information
- [ ] Font license documentation
- [ ] Icon/source/license/attribution documentation
- [ ] Internal/Closed testing track setup

---

# 30. Premium boundary verification

Before Free v1 release:

- [ ] Per-app daily limits are not accidentally exposed as completed Free functionality.
- [ ] New-app-installed alerts are not accidentally exposed as completed Free functionality.
- [ ] No unfinished Premium controls appear in Free dashboard.
- [ ] Existing Free features remain fully usable without purchase.

Reserved Premium features:

1. Per-app daily limits
2. New-app-installed alerts

Future candidates remain undecided unless separately approved in `docs/PRODUCT_ROADMAP.md`.

---

# 31. Final release gate

PhoneGuard Free v1 is ready for a release candidate only when:

- [ ] All critical functional sections above are PASS.
- [ ] No known blocker crash remains.
- [ ] Pairing is reliable.
- [ ] Remote control path is reliable.
- [ ] Offline recovery is reliable.
- [ ] Reboot recovery is reliable.
- [ ] Protection alerts are reliable.
- [ ] Parent restart does not lose important state.
- [ ] Child restart does not lose protection state.
- [ ] Small-screen pass is acceptable.
- [ ] Accessibility pass is acceptable.
- [ ] Release build is signed and smoke-tested.
- [ ] Play Store policy/privacy requirements are prepared.

When all release gates pass, create a stable release tag, for example:

```text
free-v1.0-feature-complete
```

or, preferably once the exact store candidate build is fixed:

```text
v1.0.0
```

After that point, Premium development should move to a separate branch so the Free v1 release line remains stable.
