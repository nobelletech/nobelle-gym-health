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
- Android hardware tests passed: 3 encrypted-key tests and 17 Room migration,
  owner-isolation, transition, outbox and tombstone tests. Fourteen phone JVM
  tests also passed.

## Pending

- Automatic Drive outbox upload, acknowledgements, retry/backoff and restore.
- Fasting UI, Health Connect import and watch enrollment.
- Independent network-capture inspection.
