# Integration & Capability Matrix (M0 living document)

Checked against the brief §9/§19/§25/§28 on 2026-10-16. **Nothing below is a
capability claim until the "Verified on" column names real hardware.** Update
on every probe.

| Capability | Route | Status | Verified on | Notes |
| --- | --- | --- | --- | --- |
| Health Connect availability | `HealthConnectClient.getSdkStatus` | Implemented probe | — pending device | `CapabilityProbe` in `core-health` |
| Steps/activity import | Health Connect read | Designed | — pending | Per-category permission |
| Samsung Health records | Samsung Health → Health Connect | Designed | — pending | Sharing must be enabled in Samsung Health |
| Live watch heart rate | Wear OS Health Services | Designed | — pending Galaxy Watch model | Model unknown — **owner action** |
| Watch GPS | Health Services ExerciseClient | Designed | — pending | Watch-dependent |
| Passive monitoring | Health Services passive listener | Designed | — pending | Battery measured before claims |
| Samsung Health Data SDK | Approved partnership | Approval-dependent | — | Optional adapter; app ships without it |
| Samsung Sensor SDK | Approved partnership | Approval-dependent | — | Optional adapter |
| OneTouch Reveal | OneTouch → Health Connect | Designed (Canada docs) | — pending | New readings only; no backfill fabricated |
| OMRON connect | OMRON → Health Connect | Unverified region variant | — pending | CA-installed variant must be verified |
| 1byone Health | 1byone → Health Connect | Unverified | — pending | Google Fit bridge may be stale |
| Google Fit APIs | — | Not used | — | Deprecated end of 2026; via Health Connect only |
| Drive sync | `drive.appdata` scope | Architecture done | — pending OAuth client | Google Cloud project needed — **owner action** |
| On-device AI insights | Google AI Edge / MediaPipe LLM | Designed | — pending supported phone | Deterministic template fallback required |
| ECG / BP (Health Monitor) | — | Not assumed | — | Proprietary; no API assumed |
| Camera pose cues | On-device pose (ML Kit/LiteRT) | Experimental | — | Separate consent + indicator (§8D) |

## Owner actions that block verification

1. Google Cloud project: Drive API, OAuth clients (Android + web), consent screen.
2. Exact Galaxy Watch model + OS.
3. Test Google account and (optionally) Play developer account.
4. Installed OneTouch/OMRON/1byone regional app versions on the test phone.
