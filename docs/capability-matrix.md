# Integration & Capability Matrix (M0 living document)

Checked against the brief §9/§19/§25/§28 on 2026-10-16. **Nothing below is a
capability claim until the "Verified on" column names real hardware.** Update
on every probe.

| Capability | Route | Status | Verified on | Notes |
| --- | --- | --- | --- | --- |
| Health Connect availability | `HealthConnectClient.getSdkStatus` | Implemented probe | — pending device | `CapabilityProbe` in `core-health` |
| Steps/activity import | Health Connect read | Designed | — pending | Per-category permission |
| Samsung Health records | Samsung Health → Health Connect | Designed | — pending | Sharing must be enabled in Samsung Health |
| Live watch heart rate | Wear OS Health Services | Designed | Galaxy Watch8 SM-L330; hardware test pending | Firmware/build `L330XXS2AZF4`; Wear OS version still to confirm |
| Watch GPS | Health Services ExerciseClient | Designed | Galaxy Watch8 SM-L330; hardware test pending | Capability probe required |
| Passive monitoring | Health Services passive listener | Designed | — pending | Battery measured before claims |
| Samsung Health Data SDK | Approved partnership | Approval-dependent | — | Optional adapter; app ships without it |
| Samsung Sensor SDK | Approved partnership | Approval-dependent | — | Optional adapter |
| OneTouch Reveal | OneTouch → Health Connect | Designed (Canada docs) | — pending | New readings only; no backfill fabricated |
| OMRON connect | OMRON → Health Connect | Unverified region variant | — pending | CA-installed variant must be verified |
| 1byone Health | 1byone → Health Connect | Unverified | — pending | Google Fit bridge may be stale |
| Google Fit APIs | — | Not used | — | Deprecated end of 2026; via Health Connect only |
| Drive sync | non-sensitive `drive.appdata` scope | Web REST adapter implemented; Android adapter pending | Owner-account web OAuth, listing, and 148-byte upload/read/SHA-256/delete round trip verified 2026-10-16; network-capture inspection pending | No backend, billing or broad Drive scope |
| On-device AI insights | Google AI Edge / MediaPipe LLM | Designed | — pending supported phone | Deterministic template fallback required |
| ECG / BP (Health Monitor) | — | Not assumed | — | Proprietary; no API assumed |
| Camera pose cues | On-device pose (ML Kit/LiteRT) | Experimental | — | Separate consent + indicator (§8D) |

## Owner actions that block verification

1. Galaxy Watch8 SM-L330 Wear OS version (firmware/build recorded as `L330XXS2AZF4`).
2. Installed health-source app versions on Galaxy S25 Ultra SM-S938W (Android 16).
3. Google Play developer account (release only; not needed for development).
4. Installed OneTouch/OMRON/1byone regional app versions on the test phone.
