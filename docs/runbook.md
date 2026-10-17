# Runbook

## Prerequisites

- JDK 17 (Temurin works), Android SDK at `sdk.dir` in `local.properties`
  (this machine: `D:/dev/android-sdk`), Node 20+.
- Gradle is provided by the wrapper (`./gradlew`), version 8.13.

## Build

```bash
./gradlew :android:phone:assembleDebug      # phone APK -> android/phone/build/outputs/apk/debug/
./gradlew :android:wear:assembleDebug     # watch APK -> android/wear/build/outputs/apk/debug/
./gradlew :drive-sync:build :android:core-coaching:build   # JVM modules incl. tests
cd web && npm install && npm run build    # web dashboard -> web/dist/
```

## Test

```bash
./gradlew test                 # all unit tests (model, coaching, drive codec)
```

Instrumentation/on-device tests require a connected device — never report
emulator results as hardware verification (§2).

## Install

```bash
adb install android/phone/build/outputs/apk/debug/phone-debug.apk
adb -s <watch> install android/wear/build/outputs/apk/debug/wear-debug.apk
```

## Required owner configuration before sync works

- Google Cloud project with Drive API enabled. No billing account or paid
  service is required for the current architecture.
- OAuth consent uses only `https://www.googleapis.com/auth/drive.appdata`, a
  non-sensitive scope. While publishing status is **Testing**, add the owner's
  Google email under **Audience → Test users**.
- Android debug OAuth client for package `com.nobelle.gymhealth.debug` and the
  SHA-1 of the certificate that actually signs `phone-debug.apk`:
  `32:75:42:E3:8A:6A:CE:BF:51:AD:77:27:91:30:DC:B4:D8:7E:35:3E`.
  This was verified from the APK with `apksigner verify --print-certs`.
- Web OAuth client authorized JavaScript origin: `http://localhost:5173`.
  The implementation uses Google Identity Services' browser token-client popup,
  so it requires **no authorized redirect URI**. A deployed production origin
  will be registered separately after a host is selected.
- Put the web client ID in `web/.env.local` as `VITE_GOOGLE_CLIENT_ID=...`.
  Client IDs are identifiers, not secrets. No client secret is placed in the
  phone or static web app; access tokens remain in browser memory only.

## Repo hygiene

- `local.properties`, keystores, `google-services`-style configs: never commit.
- Licensed exercise media lives in `content/` with provenance; placeholders
  must be marked and replaced before release (§5).
