# PhoneGuard Product Roadmap

_Last updated: 2026-09-25_

This document tracks product decisions for the future Play Store release, especially which features should remain available in the free version and which features are candidates for a paid/premium version.

## Product direction

PhoneGuard should have a useful free version that covers the core parental-control workflow, while more advanced controls and convenience features can be reserved for a paid version.

The exact subscription / one-time-purchase model is **not decided yet**.

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
- Reboot recovery / background protection handling

The visual design and final UX are still to be redesigned before release.

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
- **Per-app daily limits** were intentionally postponed and reserved as a Premium candidate.
- **New app installed alerts** were intentionally postponed and reserved as a Premium candidate.
- UI appearance will be redesigned later; current screens are functional prototypes.
