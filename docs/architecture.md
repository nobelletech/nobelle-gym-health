# Nobelle Gym & Health — Architecture

Baseline decisions follow §12 of the build brief. The **mandatory** constraint
(§24): all personal application data lives only in the signed-in user's own
Google Drive (`appDataFolder`, scope `drive.appdata`). There is no Nobelle
backend, Firebase, Firestore, or developer-owned store. Personal-data AI runs
on-device only (§26).

## Module map (`§17` layout)

| Path | Type | Responsibility |
| --- | --- | --- |
| `android/phone` | Android app (Compose M3, Kotlin) | Today / Coach / My Health / Progress / Profile |
| `android/wear` | Wear OS app | Quick start, live session, gym check-in, fasting controls; local outbox |
| `android/core-model` | Kotlin/JVM | All shared data contracts (§13, §20 GymVisit, §21 Fasting, §26 InsightRecord) |
| `android/core-data` | Android lib | Room durable sessions, outbox, tombstones; DataStore preferences |
| `android/core-health` | Android lib | Health Connect client, runtime `CapabilityProbe`, source registry |
| `android/core-sync` | Android lib | Phone-side sync engine, WorkManager scheduling, watch Data Layer bridge |
| `android/core-coaching` | Kotlin/JVM | Deterministic planning engine, attendance accounting, progression rules |
| `drive-sync` | Kotlin/JVM | `DriveStore` interface, `SyncEvent` chunk codec, opaque naming, paging/dedup |
| `web` | React + TS (Vite) | Dashboard: history, plan editing, reports; Drive access via GIS token |
| `content` | assets | Reviewed exercise definitions, tips library, licences |
| `docs` | docs | This file, capability matrix, runbook |
| `tests` | test assets | Reference datasets for analytics/sync verification |

## Data flow (§12)

```
Watch session -> durable watch record -> authenticated phone transfer ->
Room (phone) -> outbox -> Drive appDataFolder (immutable event chunks) ->
other devices / web
```

- `SyncEvent` files are small, immutable, opaque-named; dedup by
  `operationUuid`. No single mutable database file (§24).
- Tombstones prevent deleted data resurrecting from stale devices.
- Watch → phone uses the Wear Data Layer; a Data Layer ack is **not** a cloud
  ack — phone transfer and Drive upload are tracked independently.

## Identity

Google sign-in (Credential Manager / GIS). Owner identity = `issuer + subject`
(`OwnerId`), never email alone. Drive authorization is a separate grant.
Watch enrollment is phone-mediated, expiring, per-install, revocable (§2).

## Capability-first rule

Every sensor/integration claim is verified at runtime (`CapabilityReport`)
before UI promises it. Unavailable features produce honest empty states, never
mocked readings.
