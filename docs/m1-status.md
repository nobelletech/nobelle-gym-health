# M1 status — 2026-10-18

## Implemented and verified

- Stable owner identity uses the Google OIDC issuer and subject, never email.
- Google identity sign-in and the `drive.appdata` grant use separate AppAuth
  authorization-code + PKCE requests.
- The owner identity is encrypted with an Android Keystore AES-GCM key. Access
  and ID tokens remain memory-only.
- Room schema v2 scopes all records and queries by owner, adds durable workout
  sets, uses composite owner keys, and includes a data-preserving v1-to-v2
  migration.
- Workout, gym visit, fasting, health observation, outbox and tombstone
  repositories are owner-scoped. Completed workouts enqueue immutable,
  idempotent sync events; automatic Drive upload is not implemented yet.
- Personal local data uses SQLCipher with a random 32-byte passphrase wrapped
  by Android Keystore. Missing-key situations fail closed; no destructive
  database fallback is configured.
- Manual workouts support start, pause, resume, finish, discard and set logging
  for reps, load, duration, rest, RPE, RIR, tempo, notes, pain, set type,
  grouping and substitutions.
- The set logger includes 105 searchable, alphabetically sorted exercise
  presets with editable convenience defaults and manual entry. Presets never
  guess the user's load.
- Gym attendance supports log-now, calendar/clock backdating, selectable or
  manual categories, edit, end and deletion with tombstones. Attendance counts
  distinct visit-start local dates rather than raw visits or linked workouts.
- The optional fasting tracker requires explicit enablement, persists schedule
  and actual instants, derives elapsed time without a service, and supports
  manual start/end/cancel, calendar/clock backdating and deletion. It never
  starts, ends or extends a fast automatically.

## Hardware evidence

- Verified on Samsung `SM-S928N`, Android 16, on 2026-10-18.
- Separate identity and Drive authorization completed successfully.
- Started a workout, logged one Bench Press set, paused it, force-stopped the
  app, and recovered the paused session with one set from encrypted Room.
- Resumed and finished the workout. The active session cleared, the completion
  message confirmed the durable outbox write, and the database reopened after
  another force-stop without an Android runtime crash.
- Scanned the encrypted database and WAL bytes for the test exercise text,
  account email and completion text; none appeared in plaintext. This is a
  local-at-rest check, not an independent network-capture inspection.
- Logged and ended a `Test Gym` Strength visit, force-stopped the app and
  recovered the same end time. Added a second Cardio visit on the same local
  date and verified `1 attendance day · 2 visits` before and after restart.
  Deleted the Cardio visit and verified after another restart that it remained
  absent while the first visit remained unchanged.
- Enabled a 12:12 fasting schedule and verified it survived restart without
  automatically starting a fast. Started a short test fast, recovered the same
  active start instant after force-stop, then cancelled it and verified the
  neutral cancelled history state. Backdated an exact 12-hour completed fast
  and verified it survived restart.
- A double-submission produced two identical backdated fasts during testing.
  The repository now treats an exact completed interval as canonical and the
  dialog has a synchronous submit guard. One duplicate was explicitly deleted
  by the owner; exactly one completed 12-hour record remained after restart.
- Health Connect reported available on Samsung `SM-S928N`, Android 16. The user
  selected only Steps and granted that read permission; Resting heart rate,
  Weight and Sleep remained denied. A manual 30-day import completed without a
  crash but found zero source records. This verifies availability, contextual
  permission and an honest empty-data result, not a successful record import.
- Health Connect also reported available on Galaxy S25 Ultra `SM-S938W`,
  Android 16. With only Steps selected and granted, a manual 30-day import read
  and encrypted 2,540 real source records. Displayed records included counts,
  measured timestamps, UTC offsets, source package and imported provenance.
  The reported source namespace was
  `com.android.healthconnect.phone.j4538d18bb8713d1a94dacd6321e1e6b6`;
  it is not relabelled as Samsung Health without stronger lineage evidence.
- Repeating the same import reported 0 new or updated records from the same
  2,540 source records, verifying deterministic IDs and idempotent local/outbox
  writes.
- Installed the Wear debug APK on Galaxy Watch8 `SM-L330`, Wear OS 6 / API 36,
  firmware `L330XXS2AZF4`. Health Services reported measure and exercise heart
  rate, running exercise and running location support; the device reported GPS
  hardware and haptics.
- After explicit granular heart-rate consent, a live Health Services test
  returned 86 bpm. The user physically confirmed the explicit haptic pulse.
  The live BPM was displayed only and not stored or transmitted.
- After the user explicitly enabled watch Location and granted fine-location
  access, an outdoor Android GPS-provider diagnostic acquired a real fix. The
  provider supplied no accuracy estimate. The diagnostic never read, displayed,
  stored, logged or transmitted coordinates.
- The Wear diagnostic ran with 0 reachable phone nodes, which verifies only
  that the shell can run disconnected. Phone enrollment, recording and durable
  watch persistence remain unimplemented.
- Android hardware tests passed: 3 encrypted-key tests and 17 Room migration,
  owner-isolation, transition, outbox and tombstone tests. Fourteen phone JVM
  tests also passed.

## Pending

- Automatic Drive outbox upload, acknowledgements, retry/backoff and restore.
- Live watch GPS-fix verification, phone enrollment and durable watch
  recording/sync.
- Independent network-capture inspection.
