# M0 status — 2026-10-16

## Implemented and verified

- Multi-module Gradle build (`§17` layout), Gradle 8.13 / AGP 8.13.1 / Kotlin 2.1.20.
- Phone app shell: Material 3 theme (navy/charcoal/teal), bottom nav
  Today | Coach | My Health | Progress | Profile, first-pass screen copy that
  states capability honestly (no fake readings, pairing/sync optional).
- Wear OS app shell: quick start, gym check-in, fasting entry points.
- `core-model`: all §13 contracts plus GymVisit (§20), FastingSchedule/
  FastingSession (§21), InsightRecord (§26), provenance labels.
- `core-data`: Room schema v1 (sessions, visits, fasts, observations, outbox,
  tombstones) with exported schema.
- `core-health`: runtime `CapabilityProbe` for Health Connect.
- `drive-sync`: `DriveStore` boundary, immutable `SyncEvent` codec, opaque
  naming, paged listing contract.
- `core-coaching`: deterministic weekly planner + attendance accounting.
- Web dashboard shell (Vite/React/TS) with Today/Plans/Health/Progress/
  Connections pages.
- Unit tests: DST-safe fasting elapsed time, overnight/attendance-date rules,
  multi-visit dedup, unit conversions, event codec round-trip, opaque filenames.
- Verified: `:android:phone:assembleDebug`, `:android:wear:assembleDebug`,
  JVM module tests, `web` production build — all green on this machine.

- Verified 2026-10-16 with the owner's test account: the web client authorized
  only the non-sensitive `drive.appdata` scope, listed the user-owned
  `appDataFolder`, uploaded a 148-byte non-personal diagnostic payload, read it
  back, matched its SHA-256 checksum and deleted the temporary file.
- Verified 2026-10-17 on Galaxy S25 Ultra SM-S938W: Android browser-PKCE OAuth
  authorized only `drive.appdata`; Nobelle uploaded a non-personal `SyncEvent`,
  read it back exactly and deleted the temporary file. External network-capture
  inspection remains pending and is not implied by this functional test.

## Pending / blocked on owner (§18)

- Device supplied: Samsung Galaxy S25 Ultra SM-S938W, Android 16.
- Watch supplied: Samsung Galaxy Watch8 SM-L330, Wear OS 6.0,
  firmware/build `L330XXS2AZF4`.
- Real Health Connect and Wear Health Services tests require installing on this hardware.
- Licensed exercise media (60 demos / 12 templates target).
- On-device AI model selection + hardware validation.
