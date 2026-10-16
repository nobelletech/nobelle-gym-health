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

- Google Cloud project with Drive API enabled and OAuth clients for the
  Android signing fingerprint and the web origin. Nothing is stored in that
  project; it only gates API access (§12).
- No secrets are committed. Credentials are entered at runtime via Google
  sign-in.

## Repo hygiene

- `local.properties`, keystores, `google-services`-style configs: never commit.
- Licensed exercise media lives in `content/` with provenance; placeholders
  must be marked and replaced before release (§5).
