# Integration & Capability Matrix (M0 living document)

Checked against the brief §9/§19/§25/§28 on 2026-10-16. **Nothing below is a
capability claim until the "Verified on" column names real hardware.** Update
on every probe.

| Capability | Route | Status | Verified on | Notes |
| --- | --- | --- | --- | --- |
| Health Connect availability | `HealthConnectClient.getSdkStatus` | Implemented and hardware-tested | Samsung SM-S928N and Galaxy S25 Ultra SM-S938W, Android 16, 2026-10-18 | Built-in provider reported available on both devices |
| Steps/activity import | Health Connect read | Implemented and hardware-tested | Galaxy S25 Ultra SM-S938W, Android 16, 2026-10-18 | Steps-only grant imported 2,540 real 30-day source records; immediate repeat imported 0 changes from the same 2,540 records |
| Samsung Health records | Samsung Health → Health Connect | Designed | — pending | Sharing must be enabled in Samsung Health |
| Live watch heart rate | Wear OS Health Services | Implemented diagnostic and hardware-tested | Galaxy Watch8 SM-L330, Wear OS 6.0, 2026-10-18 | Firmware `L330XXS2AZF4`; explicit live test returned 86 bpm and did not save the sample |
| Watch GPS | Health Services capability + device GPS feature | Capability reported; live fix pending | Galaxy Watch8 SM-L330, Wear OS 6.0, 2026-10-18 | Running location and GPS hardware reported supported; outdoor fix still required |
| Watch haptics | Android vibrator | Implemented diagnostic and hardware-tested | Galaxy Watch8 SM-L330, Wear OS 6.0, 2026-10-18 | Explicit 150 ms pulse was physically felt |
| Watch offline shell | Local Wear app | Hardware-tested with limitations | Galaxy Watch8 SM-L330, Wear OS 6.0, 2026-10-18 | Diagnostic launched and ran with 0 reachable phone nodes; recording/persistence not implemented yet |
| Passive monitoring | Health Services passive listener | Designed | — pending | Battery measured before claims |
| Samsung Health Data SDK | Approved partnership | Approval-dependent | — | Optional adapter; app ships without it |
| Samsung Sensor SDK | Approved partnership | Approval-dependent | — | Optional adapter |
| OneTouch Reveal | OneTouch → Health Connect | Designed (Canada docs) | — pending | New readings only; no backfill fabricated |
| OMRON connect | OMRON → Health Connect | Unverified region variant | — pending | CA-installed variant must be verified |
| 1byone Health | 1byone → Health Connect | Unverified | — pending | Google Fit bridge may be stale |
| Google Fit APIs | — | Not used | — | Deprecated end of 2026; via Health Connect only |
| Drive sync | non-sensitive `drive.appdata` scope | Web REST and Android appDataFolder adapters implemented | Web and Android upload/read/verify/delete round trips verified; Android browser-PKCE + Drive diagnostic verified on Galaxy S25 Ultra SM-S938W 2026-10-17; network capture pending | No backend, billing or broad Drive scope |
| Stable account identity | Google OIDC issuer + subject | Implemented | Samsung SM-S928N, Android 16, 2026-10-18 | Separate identity and Drive authorization requests; encrypted owner identity; email is not used as owner UID |
| Encrypted local workout store | SQLCipher Room + Android Keystore | Implemented and hardware-tested | Samsung SM-S928N, Android 16, 2026-10-18 | Keystore-wrapped random database passphrase; no destructive fallback |
| Offline manual workout | Owner-scoped Room repositories | Implemented and hardware-tested | Samsung SM-S928N, Android 16, 2026-10-18 | Start, set logging, pause, force-stop recovery, resume and finish verified; automatic Drive outbox upload remains pending |
| Exercise selection and set defaults | Local reviewed preset catalogue | Implemented and hardware-tested | Samsung SM-S928N, Android 16, 2026-10-18 | 105 searchable A-Z presets; editable convenience defaults; load is never guessed; manual entry remains available |
| Gym attendance | Owner-scoped encrypted Room repository | Implemented and hardware-tested | Samsung SM-S928N, Android 16, 2026-10-18 | Log now, end, backdate, same-day deduplication and deletion persistence verified; category/date/time pickers and manual category supported |
| Optional fasting tracker | Owner-scoped encrypted Room repository | Implemented and hardware-tested | Samsung SM-S928N, Android 16, 2026-10-18 | Explicit opt-in; schedule persistence, manual start, restart recovery, cancel, 12-hour backdate and deletion verified; no automatic start/end |
| On-device AI insights | Google AI Edge / MediaPipe LLM | Designed | — pending supported phone | Deterministic template fallback required |
| ECG / BP (Health Monitor) | — | Not assumed | — | Proprietary; no API assumed |
| Camera pose cues | On-device pose (ML Kit/LiteRT) | Experimental | — | Separate consent + indicator (§8D) |

## Owner actions that block verification

1. Installed health-source app versions on Galaxy S25 Ultra SM-S938W (Android 16).
3. Google Play developer account (release only; not needed for development).
4. Installed OneTouch/OMRON/1byone regional app versions on the test phone.
