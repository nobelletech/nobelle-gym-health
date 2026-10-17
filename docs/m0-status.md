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

- Verified 2026-10-16: owner test account authorized the non-sensitive
  `drive.appdata` scope from `http://localhost:5173`, and the web client listed
  the empty user-owned `appDataFolder`. Upload/read/delete remains pending.

## Pending / blocked on owner (§18)

- Exact Galaxy Watch model/OS for capability probing.
- Real Health Connect import test — requires physical phone.
- Licensed exercise media (60 demos / 12 templates target).
- On-device AI model selection + hardware validation.
