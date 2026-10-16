# Nobelle Gym & Health — Devin Build Brief
Prepared 15 October 2026. Product owner: Collins Anoban. Brand: Nobelle Technologies Inc.

**Mandatory storage policy:** All Nobelle personal cloud data must stay in the signed-in user's own Google Drive account. No developer-owned Firebase/Firestore, cloud database, object bucket, analytics store or personal-data AI service. See section 24 for the complete architecture and acceptance requirements.

## 1. Instruction to Devin
Build Nobelle Gym & Health: a native Android fitness coach, installable Wear OS Galaxy Watch companion, and responsive web dashboard using one Google identity. Deliver working software in milestones, with a polished design and real integrations. This document is the product specification and execution prompt.

The two primary destinations must be visually and functionally distinct:
- **Coach:** exercise demonstrations, guided workouts, outdoor activity, plans and performance.
- **My Health:** personal health records, wearable trends, lab history, sleep and recovery.

Make ordinary workouts reliable before adding generative AI or experimental detection. Do not claim every exercise or health marker is automatically available. Clearly distinguish implemented, verified-on-device, experimental, approval-dependent and unavailable features. Never populate real dashboards with fake health readings.

Start by creating the architecture, screen designs, integration capability matrix, repository and buildable phone/watch skeleton. Then implement the milestones below. Proceed with sensible reversible choices; ask Collins only for credentials, accounts, actual watch model, budget-sensitive provisioning or other blocking decisions. Do not request secret keys in a public issue or commit.

## 2. Platforms, identity and delivery
- Android phone app, adaptive layouts for Android tablets.
- Separate installable Wear OS app for compatible Galaxy Watches. Initial target: Galaxy Watch4 or newer running Wear OS, subject to current SDK requirements and actual capability checks. Older Tizen watches are not an assumed target.
- Responsive web dashboard for viewing synced history, editing plans and reviewing reports on a laptop or other device. Browser cannot directly read the phone's Health Connect store or Galaxy Watch sensors.
- Google sign-in through Android Credential Manager and supported Google Identity/OAuth flows on web, without Firebase Authentication. Request Google Drive authorization separately. Use a stable Google account subject plus issuer as owner identity, never email alone; ownerUID fields below refer to that scoped identity.
- Watch pairing and authentication: authenticated, expiring phone-mediated enrollment, per-install identity and revocation. Do not copy long-lived credentials into unprotected Data Layer messages. Validate the current supported wearable authentication pattern before implementation.
- Same account sees the same workouts, plans, preferences and health data selected for cloud sync. Health permissions remain local and must be granted on each collecting phone.
- Delivery: source repo, installable phone and watch APKs for testing, release AABs, hosted private web preview when credentials permit, setup/runbook, CI and test evidence. Never report emulator-only results as hardware verification.

## 3. Experience and navigation
Phone bottom navigation: **Today | Coach | My Health | Progress | Profile**.

Visual direction: premium, calm, readable; charcoal/navy, white, teal and restrained blue. Coach screens use exercise imagery, timers and large controls. My Health uses clean charts and a clinical-style hierarchy without implying diagnosis. Both support light/dark themes, large fonts, screen readers, reduced motion and colour-independent status indicators.

### Today
Today's workout, start/resume button, watch connection, recent sync, activity summary and optional check-in. Keep medical details private on lock-screen notifications. Display health-derived suggestions only after the user enables that use.

### Coach
Searchable exercise library; muscle/equipment/difficulty filters; workout builder; weekly plan; guided session; run/walk tracker; rest/recovery sessions. Include gym and home alternatives.

### My Health
Overview cards grouped into Activity, Heart & Vitals, Sleep & Recovery, Body, Nutrition, Labs & Records. Each marker opens a dedicated history screen with units, date range, source, last update, gaps and export.
Include a **Connections & Data** page showing watch status, permissions by category, cloud upload choices, sync errors, delayed data and unavailable metrics.

### Progress
Strength/volume charts, running pace, walking consistency, personal bests, body trends, calendar and weekly/monthly reports. Separate training progress from medical interpretation.

### Profile
Goals, experience, equipment, schedule, preferred units, voice, accessibility, connections, notification settings, privacy, export, account deletion and help.

## 4. Onboarding
1. Google sign-in and clear explanation of data use.
2. Goals: general fitness, strength, muscle building, endurance, mobility, weight management or custom.
3. Optional age, height, weight, experience, available equipment, preferred days and session length.
4. Optional limitations/injuries and health considerations entered by the user; do not preload historical personal medical information.
5. Pair watch and run capability check; phone-only use remains available.
6. Request permissions contextually: activity, heart rate/health data, notifications, location for routes. Ask only for categories used by enabled features.
7. Separate controls for device access, cloud sync, routes, AI processing and optional experimental sensor recording.
8. Preview a suggested week; allow full editing before adoption.

Use kg/lb, km/mi, mmol/L or mg/dL as appropriate with explicit, tested conversions. Default timezone from the device; preserve original time offsets through travel.

## 5. Fitness and audiovisual coaching
Include walking, running, run/walk intervals, treadmill, cycling, indoor cycling, elliptical, rowing, cardio circuits, strength, weightlifting, bodyweight, core/abs, mobility, stretching, warm-up, cooldown, balance, yoga and recovery. Swimming and other sports can be logged; live watch metrics are capability-dependent.

Each exercise record: stable ID, name, muscle groups, equipment, difficulty, demonstration asset, text instructions, breathing cues, common mistakes, easier/harder alternatives, contraindication notes, content author/review date and media licence.

Launch content target: 60 reviewed exercise demonstrations and 12 editable workout templates covering full body, upper/lower body, beginner strength, core, low-impact cardio, mobility and run/walk. Use properly licensed video/animation assets and reviewed text; track provenance. Temporary placeholders must be visibly identified and replaced before release.

Guided session:
- Demonstration clip/animation, captions, step-by-step instructions and offline text.
- Text-to-speech cues: exercise name, next move, countdown, rest, pace/interval prompts and session summary.
- Optional spoken prompts such as “Next: goblet squat, three sets of ten.” Avoid constant chatter.
- Large start, pause, resume, skip, substitute and stop controls.
- Sets/reps/load, exercise duration, tempo, RPE/RIR, rest timer, notes and pain/discomfort flag.
- Supersets, circuits, drop-set logging and user-defined exercises.
- Audio focus handling: duck music where supported, respect calls, mute and quiet settings; route to headphones or watch speaker only when supported.
- Cache selected workouts, cues and demonstrations for offline gym use.
- Video demonstrates technique; it is not live assessment of the user's form.

Planning engine: deterministic, editable rules using goals, availability, equipment, experience and recent training. Include rest days, gradual progression, deload options, substitutions and user override. Never automatically increase load solely from estimated repetitions or inferred recovery.

Required on-device Nobelle Health AI explains health data and plans and suggests alternatives from the reviewed catalogue. See section 26 for scope, grounding, privacy and validation. It must not invent sensor readings, diagnose illness, prescribe medication changes or silently rewrite an active workout. Personal-data cloud AI is disabled under the Google-account-only requirement. Use on-device coaching and reviewed content; core workouts and voice work without cloud AI.

## 6. Outdoor tracking
Start/stop on phone or watch; GPS route, elapsed/active time, distance, pace, splits, available heart rate and elevation. Live screen and audio interval cues. Auto-pause only where validated for the activity/device, with manual override.

Watch-only workouts must work without the phone: record locally, use watch GPS if available, provide cached cues/haptics and sync later. Do not promise phone-free capabilities the particular watch lacks.

GPS should be active only for selected features. Handle signal loss, indoor mode, low battery, implausible points and explicit route deletion. Offer private start/end trimming for exported or shared routes. Sharing is opt-in; no public route feed by default.

## 7. Galaxy Watch app
Screens: quick start, today's plan, live metrics, current exercise/set, rest timer, pause/finish and recent sessions.
- Big touch targets, haptics, glanceable layout and water-lock guidance where relevant.
- Quick set completion, editable reps and load; sync exercise sequence with phone.
- Tiles/complications where supported for quick start and daily progress.
- Durable local session state and outbox; show connection and pending upload.
- Phone/watch controls operate on one canonical session; duplicate finish taps must not create two workouts.
- Cache selected plan and cues before disconnecting.
- Show sensor quality/unavailable indicators; no heart rate from stale samples.
- Recover after process loss where supported; a user force-stop, reboot, revoked permission or empty battery can interrupt tracking. Explain recovery state honestly.

## 8. Automatic exercise recognition — staged capability
The product goal is “as automatic as the watch can reliably support,” not universal recognition.

### A. Import existing records
Read workouts that Samsung Health or other authorized sources expose through Health Connect, or the approved Samsung connector. Preserve any automatic/manual recording metadata exposed by the source. Imported workouts may arrive after the workout and are not a live sensor feed.

### B. Activity suggestions
Use supported passive activity signals and a tested low-power motion classifier to suggest walking/running sessions. Passive activity state does not guarantee exact exercise classification or universal automatic start. Capability-probe platform APIs; record what is actually available on the test watch.
- Confidence threshold, persistence window, cooldown and user confirmation.
- “Looks like a walk. Start recording?” with accept/dismiss.
- Optional opt-in auto-log only after real-device validation; review/edit/delete afterwards.
- Detecting the start late must not fabricate earlier GPS or heart-rate samples.
- Measure false positives from driving, desk movement and household tasks.

### C. Assisted strength recognition
Inside a selected strength session, experiment with wrist accelerometer/gyroscope models for a limited named set of exercises and repetition counting. Include watch side, fit and calibration guidance.
- Wrist data cannot reliably identify every machine, leg exercise, load or technique.
- The planned exercise is the default; show inferred reps as suggestions.
- Require user confirmation and support corrections on watch/phone.
- Do not infer weight lifted. User enters load.
- Store model version, confidence and confirmed label; optional training-data collection requires separate consent.
- Unsupported movements use manual logging immediately.

### D. Optional phone camera phase
On-device pose cues for a small reviewed exercise list, framing guidance and confidence indicators. Separate permission and visible recording indicator; do not save/upload video by default. Do not claim injury prevention or clinical form assessment.

Validation target, not a current capability claim: test at least 10 consenting participants across watch wrists and exercise styles; report per-class precision/recall, false prompts per hour and rep count error. Suggested release gate for each promoted detector: precision >=90%, recall >=80% on held-out participants, with error analysis. Retain manual flow if unmet. Universal automatic recognition is outside launch acceptance.

## 9. Health integrations and feasibility
Use **Health Connect on Android** as the primary phone data exchange. It is an on-device store, not a Google cloud account or global sync service. Direct client-to-user Google Drive synchronization provides cross-device sync; no Nobelle data backend is permitted.

Use **Wear OS Health Services** for supported live exercise metrics and passive monitoring. Check capabilities at runtime; data availability differs by device, OS, SDK, permissions and activity.

Treat “Google health app” as Health Connect plus compatible Google health apps initially. If Collins means Fitbit or a separate Google Health account/cloud integration, verify current Google Health API access, OAuth scopes, review and terms before adding it. Do not build a new dependency on legacy Google Fit APIs: current Google guidance states support through the end of 2026.

Samsung flow:
- Galaxy Watch -> Samsung Health phone app -> authorized Health Connect data -> Nobelle.
- Live Nobelle workout -> watch Health Services -> local watch session -> Nobelle phone -> opted-in cloud.
- Optional Samsung Health Data SDK adapter for approved access to extra Samsung records.
- Optional Samsung Health Sensor SDK adapter for advanced direct signals. Raw sensor access is different from access to Samsung's processed health history.

Direct Samsung SDK distribution requires Samsung partnership approval. Samsung Health Data SDK writes also require the documented access code. Developer mode is only for developer testing, not a user installation workaround. Keep these adapters optional so standard functionality ships without approval.

Do not assume access to Samsung Health Monitor ECG/BP records, sleep-apnea outputs, proprietary Energy Score, stress algorithms, AGE index, vascular/antioxidant scores or other branded metrics. Verify each requested record independently. Region/model support in a Samsung app does not prove a third-party API can export it.

Background/history reads, routes and other sensitive data may require additional supported permissions. Absence of an API, sharing source or permission must produce a useful empty state.

## 10. Health marker catalogue
Implement an extensible catalogue, not a promise that the watch measures every item. Label every value **measured/imported**, **manually entered** or **estimated**.

| Group | Desired markers | Expected route and constraint |
| --- | --- | --- |
| Daily activity | Steps, distance, active minutes, exercise sessions, floors/elevation | Authorized imports; live metrics only when supported |
| Energy | Active and total calories, basal metabolic rate | Available records or clearly labelled estimates; distinguish total from active |
| Heart | Heart rate, resting heart rate, heart-rate zones, HRV | Supported imports/sensors; HRV method and sample context required |
| Cardiorespiratory | Respiratory rate, SpO2, VO2 max | Source-dependent; estimates must name their method/source |
| Sleep | Duration, stages, timing, consistency | Supported source records; naps and overlapping intervals reconciled |
| Temperature | Body or skin temperature | Keep measurement type/site/baseline distinct; capability-dependent |
| Body | Weight, height, BMI, body fat, lean mass, waist, skeletal muscle/body water | Imports/manual; BIA features only when actually exposed and supported |
| Blood pressure | Systolic, diastolic, pulse, measurement context | Approved source, cuff/device connector or manual; no inference from wrist motion |
| Glucose | Blood glucose, meal context; CGM series if accessible | Approved source/device API or manual; Galaxy Watch is not assumed to measure glucose |
| Nutrition | Water, energy intake, protein, carbs, fat, fibre, sodium | User logs or authorized source; distinguish missing from zero |
| Recovery | Soreness, fatigue, mood, stress, readiness | User check-ins or available source; derived scores use transparent formula |
| Labs | HbA1c, lipids, creatinine/eGFR and other user-added lab results | Manual/confirmed document entry; not watch-generated |
| Records | Medications, allergies, conditions, notes and attachments | Optional user-managed records; no medication dosing automation |
| Optional categories | Cycle/reproductive data, mindfulness | Disabled initially; explicit contextual consent and verified source support |
| Restricted/advanced | ECG waveforms, BIA, raw PPG, proprietary Samsung markers | Only verified and approved adapters; otherwise explain unavailability |

For every marker: unit, source app/device, source record ID, timestamps, timezone offset, recording method, freshness, quality flag and import/update history. Never display “normal” merely because data is missing. A lab report reference interval belongs to its laboratory/context; do not invent universal ranges.

Allow manual entry, edits and correction history. Optional lab PDF/photo import comes later with local OCR where feasible and mandatory confirmation of value, unit and date. Preserve original document only with explicit user choice.

My Health views: 7/30/90/365-day charts, comparison periods, source filters, daily/weekly summaries and export to CSV/PDF. Correlations such as sleep versus training are descriptive, not causal. Health insights must identify stale inputs and uncertainty.

## 11. Safety and privacy by design
- Wellness coaching; no diagnostic claims, drug adjustments or automated treatment decisions.
- Optional injury/condition notes and clinician-provided limits; do not create personal medical thresholds from an LLM.
- If a user reports chest pain, fainting or severe breathing difficulty during training, pause coaching and show urgent-help guidance. Do not rely on wearable alerts as emergency monitoring.
- No generic health risk score presented as a diagnosis. Recovery score is optional, explained and user-overridable.
- No automatic calorie restriction or medication-linked exercise changes.
- No advertising SDKs or sale of health data.
- Separate consent for personal Google Drive health sync, routes and local experimental recordings. No personal-data cloud AI or external research upload. Default raw-waveform upload off.
- Google sign-in does not itself authorize health access.
- Use HTTPS to the user's Drive and Google-provided storage encryption; protect necessary local sensitive caches with platform-backed keys. Use account-scoped OAuth authorization, no public sharing, and no developer-held service-account access.
- Do not call ordinary transport/storage encryption “end-to-end encryption.” If E2EE is later required, specify recovery and multi-device keys separately.
- Redact telemetry/crash reports: no raw health records, routes, tokens or private notes.
- Export, per-category deletion, device revocation, sign-out and complete account deletion. Explain backup retention and any limits before release.
- Audit applicable Canadian privacy obligations, current Google Play Health Apps/Health Connect policies and Samsung terms before public distribution. Do not claim legal compliance from architecture alone.

## 12. Recommended implementation
Use native Kotlin because Android health, foreground exercise tracking and Wear OS integration are central.

| Layer | Proposal |
| --- | --- |
| Phone | Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Hilt |
| Watch | Kotlin, Compose for Wear OS, Health Services, Wear OS Data Layer |
| Device storage | Room for durable sessions/outbox; DataStore for non-sensitive preferences |
| Health exchange | Health Connect; isolated optional Samsung SDK adapters |
| Audio/video | Android TTS, Media3, cached licensed media |
| Optional motion ML | On-device LiteRT model with versioned inference and confidence |
| Identity | Google Identity/Credential Manager plus separate user-authorized Drive OAuth |
| Sync/drive-sync | Direct Google Drive API, user-owned appDataFolder, client-side sync engine |
| Files | User's Google Drive only for personal attachments and backups; no developer-owned bucket |
| Web | React/TypeScript, responsive layout, same authentication and data contracts |
| Scheduling | WorkManager for deferred phone sync; supported passive listeners for watch monitoring |
| Tests/CI | JVM/unit tests, Android instrumentation, Drive authorization/isolation tests, web tests, GitHub Actions |

Select current stable versions at implementation and pin dependencies. Do not choose Python for the native watch runtime. Python is optional for offline ML/data analysis.

Google Cloud developer project, Drive API enablement, OAuth client IDs/consent configuration and Android signing fingerprints need setup. This project configures API access; it must not store the user's application records. Document Drive quota, storage consumption and public-content hosting costs. Do not provision paid services without an agreed budget.

### Data flow
Watch measures during active session -> durable watch record -> authenticated paired-phone transfer -> durable phone database -> direct authorized upload to the user's Google Drive -> other signed-in devices/web.
Health Connect or approved Samsung imports -> normalization/provenance -> canonical records -> direct authorized upload to the user's Google Drive.
Cloud data must not automatically write back into every device's Health Connect store. Default imported health data read-only. Only export Nobelle-created workouts with explicit write permission.

## 13. Data contracts and synchronization
Core entities:
- UserProfile, Device, ConsentGrant, DataSource, CapabilityReport.
- ExerciseDefinition, MediaAsset, WorkoutTemplate, TrainingPlan.
- WorkoutSession, ExerciseSet, Interval, RoutePoint, MetricSample.
- HealthObservation, SleepSession, NutritionEntry, LabResult, Attachment.
- DetectionEvent, UserCorrection, SyncOperation, DeletionTombstone.

Use stable UUIDs, owner UID, device ID, created/modified UTC times, source time offset, schema version and revision on mutable records. Session state: planned -> active -> paused -> completed/discarded; reconcile phone/watch commands with versioned sequence numbers. A session lease/active owner prevents conflicting live controllers.

Keep source records separate from canonical summaries. Identity key includes source namespace, source record ID and origin; also retain import route so Samsung -> Health Connect duplicates can be linked. Never add together overlapping step totals or calorie totals from multiple sources. Prefer supported Health Connect aggregate APIs for aggregate metrics and document source precedence.

Idempotent upserts, retry/backoff, bounded paging, resumable cursors and deletion handling. If a change token expires, safely rescan the permitted window. Exclude Nobelle's own exported records from re-import loops. Source deletions must reconcile to cloud copies according to the documented retention policy; user erasure must propagate to every replica.

Workout sessions are append-oriented; user corrections have revisions. Profile edits use versioned client-side conflict resolution over Drive records; timestamps alone must not silently overwrite conflicting medical entries. Present material conflicts for review. Tombstones prevent deleted data from reappearing after offline devices reconnect.

Use durable outboxes with acknowledgements. A successful Data Layer transfer is not proof of cloud upload. Show phone transfer and cloud sync independently. Separate high-rate samples from summaries; chunk storage and apply retention/downsampling limits. Use chunked Drive files rather than rewriting an entire account database on every sensor sample.

Account switching clears scoped caches and cancels pending prior-account transfers. An old paired watch must not upload into the newly signed-in account. Permission revocation stops new reads and offers deletion of retained copies; revocation alone is not silently treated as deletion.

## 14. Background operation
Active exercise uses the appropriate foreground service and visible ongoing activity, with current OS health/location permissions. Passive monitoring should use supported battery-efficient APIs. WorkManager handles eventual sync, not second-by-second tracking.
- Persist periodically and on important state changes.
- Test process death, disconnected phone, screen off, battery saver, permission loss and reboot.
- Show interrupted-session recovery and last confirmed timestamp.
- Never promise an “unkillable” app; respect force-stop and platform restrictions.
- Avoid simultaneous recording conflicts with Samsung Health; detect existing exercise state where supported and offer importing or ending the other recording rather than silently taking over.
- Record battery impact for idle monitoring and one-hour GPS workouts. Establish a baseline on Collins's actual watch before promising endurance.

## 15. Delivery milestones
### M0 — Design and feasibility
Produce polished key screens, architecture, runtime capability probe and supported-data matrix. Verify one real Health Connect import and one live watch heart-rate session on hardware. Identify exact phone/watch models and blocked approvals. If hardware is unavailable, label those checks pending and continue independent work.

### M1 — Complete core journey
Google sign-in, phone/watch enrollment, offline workout logging, reviewed demonstration subset, voice/rest guidance, run/walk tracking, manual health entries and consented cross-device sync. Coach and My Health must already be distinct working destinations. Deliver phone/watch APKs.

### M2 — Health dashboard and content
Authorized Health Connect history, source/freshness displays, trends, manual labs, export and deletion. Complete reviewed launch exercise/template content. Responsive web dashboard. Test duplicate imports and multi-device conflicts.

### M3 — Detection and personalization
Validated walking/running suggestions, limited assisted rep counting, editable progression rules, weekly reports and required on-device AI explanations. Ship weak detectors as disabled experiments or omit them.

### M4 — Optional advanced integrations
Samsung approved SDK features, Google cloud health integration if needed/approved, Bluetooth chest strap/scale/cuff adapters, CGM integration where access permits, camera pose feedback and confirmed OCR. None blocks standard launch.

### M5 — Release readiness
Accessibility, battery/privacy checks, real-device regression, production permissions, store disclosures, signing, documented deployment, cost controls and owner handover.

## 16. Acceptance tests
1. Same Google account on two Android devices and web sees a completed workout exactly once after reconnection.
2. Phone and watch can start/pause/resume/finish the same session without duplicate sessions or lost sets.
3. Watch records a phone-free 30-minute supported workout and syncs once after reconnecting.
4. Force-close/process loss, Bluetooth interruption and network failure produce honest recovery states and no fabricated gap samples.
5. Revoked/partial health permissions produce useful empty states; manual workouts still work.
6. Importing overlapping Samsung/Health Connect records does not inflate steps, calories or sessions; corrections and deletions reconcile.
7. My Health shows source, units, timestamps, freshness and missing-data gaps for every available marker.
8. Voice works with cached workouts, respects mute/calls and has caption/text equivalents.
9. Existing Samsung exercise recording does not silently get overwritten by Nobelle.
10. Experimental detection reports measured held-out accuracy and allows immediate correction.
11. Export contains correct units/provenance. Deletion propagates and offline reconnection does not restore erased records.
12. Drive OAuth account isolation denies cross-account reads/writes, including files; account switching cannot leak previous data.
13. Complete phone/watch journey works without direct Samsung SDK approvals.
14. Routes are private by default; cloud/AI receive only authorized categories.
15. Tested timezone changes, daylight-saving days, unit conversions and unavailable sensors.

For every milestone provide: implementation summary, screenshots, build/install steps, automated checks, actual hardware used, known limitations and remaining owner actions. Do not mark a blocked integration as complete using mocks.

## 17. Repository layout
/android/phone
/android/wear
/android/core-model
/android/core-data
/android/core-health
/android/core-sync
/android/core-coaching
/web
/drive-sync
/content
/docs
/tests

Keep optional vendor APIs behind interfaces. Include environment example files without secrets, local development instructions, Drive scope configuration and sync/security tests, seed content tooling, migrations and data retention configuration.

## 18. Owner setup checklist
Google developer project with Drive API and OAuth configuration (no Firebase data storage); Git repository; test Android phone and exact Galaxy Watch model/OS; Samsung Health sharing permissions; Google Play developer account for release; app signing configuration; licensed exercise media; optional Samsung partnership applications; optional on-device model setup; production privacy/support contact. No Samsung or Google account password should be collected by Nobelle.

## 19. Verified reference baseline
These are platform references checked for the design; Devin must recheck current contracts and production eligibility during implementation.
- Health Connect overview: https://developer.android.com/health-and-fitness/health-connect
- Health Connect types/permissions: https://developer.android.com/health-and-fitness/health-connect/data-types
- Google Fit migration and current alternatives: https://developer.android.com/health-and-fitness/health-connect/migration/fit
- Wear OS Health Services: https://developer.android.com/health-and-fitness/health-services
- Active exercise: https://developer.android.com/health-and-fitness/health-services/active-data
- Passive monitoring: https://developer.android.com/health-and-fitness/health-services/monitor-background
- Samsung Health Data SDK: https://developer.samsung.com/health/data/overview.html
- Samsung data SDK distribution approval: https://developer.samsung.com/health/data/process.html
- Samsung sensor SDK: https://developer.samsung.com/health/sensor/overview.html
- Samsung sensor SDK distribution approval: https://developer.samsung.com/health/sensor/process.html

All feature choices, UI, architecture, phased scope and validation targets above are proposed Nobelle product requirements, not claims that platform vendors provide them out of the box.

## 20. Gym attendance calendar — required addition
Add a prominent Calendar button on Today and Coach, with month, week and agenda views. Keep the existing primary navigation uncluttered. The calendar is an in-app feature; external Google Calendar access is not required.

- One-tap **Log gym visit** on phone and watch, even without recording exercises. Support “Now” and a past date/time.
- Record start/end or optional duration, gym/location label entered by the user, workout category, linked workout IDs and notes.
- Allow multiple visits per day, corrections, deletion and backdated entries. Future entries are plans, never completed attendance.
- Separate planned sessions, confirmed gym attendance, completed home/outdoor workouts and rest days with icons and text as well as colours.
- When completing a workout tagged “Gym,” offer to create/link a visit. Link it to an existing visit where appropriate; multiple exercises and workouts within one visit must not inflate attendance.
- Imported or automatically detected activity must not silently count as gym attendance. Ask for confirmation unless the user already explicitly tagged the session as a gym visit.
- Show weekly gym-day goal, unique attendance days, total visits, training minutes and monthly history. Distinguish unique gym days from multiple visits on the same day.
- Optional gentle reminders for planned sessions, weekly summaries and rest days. Avoid guilt-based notifications or incentives to train through pain.
- Include an attendance heatmap and calendar filters for gym, home, outdoor, rest and fasting. Selecting a date opens visits, workouts, notes and fasting intervals in separate groups.
- Offline check-in and later cross-device sync; stable IDs prevent duplicate logs. No location permission is needed for manual check-in. Do not add geofencing by default.
- Optional user-initiated ICS export for planned sessions. Any later Google Calendar integration requires separate OAuth consent, narrow scopes and duplicate/update handling.
- Use visit start's local date for attendance; retain original timezone. An overnight visit counts as one visit, not two. Show its full time span.

Data: GymVisit {id, ownerUID, startInstant, endInstant?, originalTimeZone, localAttendanceDate, gymLabel?, workoutIDs[], category?, notes?, revision, deletedAt?}. PlannedSession remains separate from confirmed attendance.

## 21. Intermittent fasting and eating-window tracker — required addition
Add a **Fasting** section in My Health and a compact optional Today card. The user explicitly enables it. This tracks self-selected eating windows; it is not a prescribed weight-loss or diabetes treatment.

### Main screen
Show current state (**Not fasting / Fasting / Eating window**), elapsed fasting time, chosen target, planned next eating window and start/end controls. Use a readable circular timer with an equivalent text display.
- Optional templates: 12:12, 14:10, 16:8 and a custom daily window. Explain that the first number is fasting hours and the second is eating hours; these are selectable examples, not recommendations for everyone.
- No default enrollment, automatic progression to longer fasts, multi-day challenges or rewards for exceeding a target.
- Example preview only: 8 p.m.–8 a.m. fasting and 8 a.m.–8 p.m. eating for 12:12. Let the user change times and days.
- Separate planned windows from actual fasting logs. Schedule boundaries do not prove the user fasted or ate.
- **Start now**, **Start at a past time**, **End fast now**, edit start/end, cancel/discard and add a past completed fast.
- A target reached notification must never silently end or extend the actual log. User confirms when they eat/end the fast.
- Ending early is a normal completion state with optional notes, not a failure. No pause button that hides eating periods inside a supposedly continuous fast; support separate intervals instead.
- Optional recurring schedule by day of week, flexible one-day exceptions, reminders before start/end, hydration reminders and quiet hours.
- Notifications are neutral: “Your selected eating window begins in 30 minutes.” Do not pressure the user to continue.
- Optional hunger/energy/mood/symptom check-in, notes and links to workout/health records. Do not infer ketosis, autophagy, blood-glucose status or fat burning from elapsed time.
- Fasting history in calendar and list views; actual duration, user-selected target, eating-window schedule and notes. Fasting is a private category and excluded from externally shared calendars by default.
- Watch: view timer, start/end with deliberate confirmation, haptic reminders and offline logging. Web: schedule/history and synchronized timer.
- Workouts during a logged fast may carry a user-visible “during fasting” tag. Do not prescribe fasted training or automatically increase workout intensity.
- Timer derives from persisted instants, not a continuously running service. Survive screen-off, restart and offline use; reconcile conflicting phone/watch changes. Do not promise exact alarm delivery when OS restrictions apply.
- Overnight fasting uses true elapsed time. Preserve start/end time zones and explicitly distinguish recurring local-time schedules from actual duration across daylight-saving changes.
- One actual active fast per account. Overlapping offline logs require resolution rather than double-counting. Device clock anomalies should be flagged; timestamps are user-editable with revision history.
- Include fasting logs in export, account deletion and per-category cloud-sync controls. Do not assume a Health Connect fasting record exists; use Nobelle's own data model unless a verified supported standard is available.

### Health considerations
Before enabling fasting reminders, provide a short suitability notice and optional health-context questions. For people reporting diabetes, glucose-lowering medication, pregnancy/breastfeeding, eating-disorder history or other relevant health concerns, advise individualized professional guidance before starting/changing a fasting routine. Do not expose this context in notifications.
- Do not suggest medication changes or timing adjustments. Offer recording of clinician-provided instructions only.
- Fasting with diabetes can need a clinician-agreed glucose-monitoring and medication plan. No imported reading or missing-data state should “clear” the user to fast.
- Always keep **End fast** immediately available. If the user reports feeling unwell or low-glucose symptoms, encourage ending the fast and following their clinician's treatment plan; severe symptoms trigger urgent-help guidance.
- Keep calorie restriction, dehydration/dry fasting and longer-is-better messaging out of the coaching engine.
- Do not prescribe universal fluid quantities; accommodate clinician-provided fluid limits.
- No claim that fasting cures diabetes or is required for fitness.

Data: FastingSchedule {id, ownerUID, enabled, fastingHours, eatingHours, startLocalTime, daysOfWeek[], timeZonePolicy, exceptions[], reminderPreferences, revision}; FastingSession {id, ownerUID, actualStartInstant, actualEndInstant?, originalStartZone, originalEndZone?, targetDurationSnapshot, scheduleID?, status, notes?, checkIns[], revision, deletedAt?}. Status: active/completed/cancelled. Preserve the target snapshot if schedule changes mid-fast.

## 22. Tips and education — required addition
Add a **Tips** library available from Coach and Fasting, plus an optional daily tip on Today. Group by exercise technique, workout planning, recovery, hydration, nutrition, fasting and using the watch/app.

- Short text plus optional captioned demonstration/audio. Save favourites, search, hide topics and download selected tips.
- Content is curated, reviewed and source-linked; each tip includes author/reviewer, last-reviewed date, topic and audience exclusions. AI may simplify reviewed text, not invent clinical instructions.
- Contextual tips use only authorized data and explain their trigger. No sensitive information in notification previews.
- Display practical planning suggestions without guaranteed outcome claims. Examples: “Put your planned gym days on the calendar”; “Log reps and load after each set”; “Use a comfortable starting load while learning a movement”; “Check the demonstration before a new exercise”; “Include warm-up and cooldown time”; “Record fatigue or discomfort and adjust your plan.”
- Fasting topics: understanding fasting:eating notation; choosing a manageable schedule with suitable guidance; planning balanced meals; hydration within personal limits; exercise timing and symptom awareness; ending a fast early; and interpreting actual logs versus planned schedules.
- Clinical tips must be checked against current authoritative references before release. Do not send a generic fasting recommendation to a user whose health context calls for professional guidance.
- Provide a non-judgmental “How did today feel?” check-in. No weight-loss guarantees, shame, competitive fasting streaks or encouragement to compensate for missed sessions.

Health-education reference baseline for fasting (review again before publication):
- NIDDK, Fasting Safely with Diabetes: https://www.niddk.nih.gov/health-information/professionals/diabetes-discoveries-practice/fasting-safely-with-diabetes
- NIDDK, Low Blood Glucose: https://www.niddk.nih.gov/health-information/diabetes/overview/preventing-problems/low-blood-glucose-hypoglycemia
- NIDDK, What Can You Tell Your Patients About Intermittent Fasting?: https://www.niddk.nih.gov/health-information/professionals/diabetes-discoveries-practice/patients-intermittent-fasting

## 23. Delivery and acceptance updates
These additions are required scope, not optional future ideas.
- M0: design calendar, gym check-in, fasting timer/schedule/history and tips screens.
- M1: manual/backdated gym visits, calendar, offline fasting timer/logging and reviewed starter tips on phone; watch check-in and start/end fasting controls.
- M2: web/calendar/fasting views, cloud synchronization, reminders, reports, exports and deletion.
- M3: richer contextual tips only after permission and safety behaviour are verified.

Additional acceptance checks:
1. Log a visit without a workout, backdate it, edit it and see the same entry on another device.
2. Multiple workouts linked to one visit produce one visit; two visits on one date produce two visits but one attendance day. Home workouts do not count as gym days.
3. Future plans and imported unconfirmed workouts never inflate attendance.
4. A fasting log survives restart and offline watch use, with actual elapsed time and no dependence on a permanently running service.
5. Validate overnight sessions, daylight-saving changes, travel and a schedule edit during an active fast.
6. Conflicting offline start/end actions reconcile to one reviewed session; repeated taps do not create duplicates.
7. Schedule reminders do not create actual fasting/eating logs or automatically extend/end a fast.
8. Ending early remains available, records the actual duration and produces no failure/shaming message.
9. Fasting privacy settings, notification redaction, export and deletion work across devices.
10. Health-context prompts and tips do not issue drug advice, glucose guarantees or encouragement to ignore symptoms.


## 24. Google-account-only personal cloud storage — mandatory architecture
This owner requirement applies to the entire product, including all earlier calendar, fasting, coaching, watch and health features. It replaces the earlier Firebase-backed design.

### Ownership and boundaries
- The signed-in user's **own Google Drive** is the only permitted cloud destination for Nobelle personal application data: health observations, workouts, attendance calendar, fasting, notes, plans/preferences, routes, user-created media, lab attachments and backups.
- A developer-owned Google Cloud/Firebase project is not the user's personal Google account storage. Do not use Firestore, Firebase Authentication, Cloud Storage, Supabase, AWS, a Nobelle database or a server proxy to retain personal data or tokens.
- Google login and Drive access are separate grants. Explain the selected Google account and where the data is stored before initial sync.
- Phone and watch require encrypted local working data and an offline outbox. These are necessary device copies, not additional cloud storage. Show their size and provide local-cache controls. Do not purge unuploaded workouts without an explicit data-loss confirmation.
- Exclude Nobelle private databases, health files, credentials and attachment caches from Android/Wear OS automatic backup/device-transfer mechanisms that could copy them outside this explicit Drive sync design. Verify platform-specific behaviour.
- Samsung Health, Samsung Health Monitor, Health Connect and original device vendors retain their own source records under their own settings. Nobelle can control only the copies it creates; this requirement does not relocate or erase those source systems' data.
- Public exercise demonstrations, reviewed tips, code and static app assets may be distributed separately. Do not send personal profile, route, workout or health data to content hosts. Bundle public media where feasible and document unavoidable network metadata; never promise zero service-level metadata at Google or a web host.

### Google Drive integration
- Prefer Drive's hidden application-data space (`appDataFolder`) with the narrow `https://www.googleapis.com/auth/drive.appdata` scope. It is app-managed storage in the user's Drive, not an ordinary visible folder; explain this in Connections & Data.
- Configure phone and web OAuth clients in the same Google project and prove they access the same application's data for that account. Use supported Google sign-in and authorization libraries; no embedded client secret in phone/watch/web code.
- Do not request access to all Drive files. If user-readable exports in a visible “Nobelle Gym & Health” folder are later enabled, request the narrow additional `drive.file` authorization separately and write only user-requested export files.
- Google token storage stays in supported platform credential facilities. Do not upload tokens to Drive files, logs, a Nobelle server or public configuration.
- Phone is the watch's Drive sync gateway at launch. Watch-only exercise recording remains local until the enrolled phone reconnects. Independent watch-to-Drive OAuth is a later capability only if implemented securely; it is not required for phone-free recording.
- Static web client accesses Drive directly using user-granted OAuth. Keep web tokens in memory and require sign-in again when needed; avoid long-lived browser persistence for sensitive data. No health records in server-side rendering, server logs, URLs, analytics or service-worker caches.
- No periodic server job is needed to process personal data. Sync occurs from authorized clients when network/OS permissions allow; Drive is file storage, not a real-time database or sensor streaming service.
- Handle API limits, storage exhaustion, revoked consent, expired credentials, deletion and offline access with clear pending/error states. Never silently fall back to another cloud provider.

### Files and conflict handling
Use versioned schemas and small immutable event/chunk files partitioned by installation and time. Each event includes account namespace, installation ID, entity ID, operation UUID, revision/parent revision, timestamps and deletion state. Use random/opaque filenames without medical labels.
- An operation ID is idempotent even if a retry creates duplicate Drive file objects; deduplicate by operation UUID during ingestion.
- Rebuild a local index from authorized remote files. Incremental listing/change tracking is an optimization, not the sole recovery mechanism. Paginate and safely reconcile after token expiry.
- Avoid a single shared mutable “whole database” file. Per-device journals reduce simultaneous-write collisions; conflicting edits retain both versions until resolved. Do not claim Drive supplies transactional database locks.
- Immutable snapshots may accelerate restore. Include schema version, record counts and checksums; retain source events until snapshot completeness is verified. Avoid a single concurrently overwritten manifest as the only discovery path.
- Two disconnected devices cannot enforce a globally exclusive live fasting/workout session. Permit local operation, detect conflicts after sync, ask which overlapping records to retain, and deduplicate confirmed results.
- Deletion tombstones prevent stale devices restoring erased entries. Define event, attachment, snapshot and backup compaction so old copies containing deleted data are removed. After complete erasure, previously enrolled offline devices must require explicit reenrollment before any new upload.
- Attachments, snapshots and personal exports stay in the same user's Google Drive. Do not retain duplicate backups on an operator account.
- Google storage encryption plus protected local caches is the initial baseline; do not market this as end-to-end encryption. Any future application-level encryption must include a reviewed cross-device key/recovery plan before implementation.

### User controls and acceptance
Connections & Data must display: connected Google account, “Stored in your Google Drive,” last successful sync, pending records, permission state, Drive storage/error information when available, export, disconnect, local-data controls and delete-Nobelle-data.
Disconnecting stops sync; deletion is a separate explicit action. Deleting Nobelle data removes its app-data records and known personal exports after authorization, with a clear completion/failure report. Source Samsung/Health Connect records remain separately controlled.

Required checks:
1. Complete gym, fasting, health import and attachment flows while network capture confirms personal payloads go only to the authorized user's Google Drive endpoint, apart from necessary local watch/phone transfer.
2. A new authorized phone and web client restore from the same Drive data without contacting a Nobelle personal-data backend.
3. Revoke Drive permission: local workouts remain available, sync stops clearly and no alternate destination is used.
4. Switch Google accounts: clear account-scoped state and never upload pending old-account records into the new account.
5. Exercise concurrent/offline edits, duplicate upload retries, interrupted snapshots, quota errors and deletion/reconnection.
6. Inspect build/configuration for forbidden database, cloud AI, telemetry and automatic-backup paths.
7. No personal-data external AI, crash-reporting payloads, public links or developer access credentials.
8. Explain and verify that original health apps keep their own independently managed source data.

Platform references:
- App-specific Google Drive storage: https://developers.google.com/workspace/drive/api/guides/appdata
- OAuth scope selection: https://developers.google.com/workspace/drive/api/guides/api-specific-auth


## 25. Connected health apps and permission-based collection — required
Provide a dedicated **My Health -> Connections** screen for Samsung Health, Google Health/Google Fit, 1byone Health, OneTouch Reveal and OMRON connect. These are explicit requested integrations, subject to verified vendor access. “Google gym health” is treated as Google Health and/or Google Fit; show the actual supported app name and let the user choose rather than inventing a connector.

### Permission and connection flow
1. Tap a source card -> see data categories, purpose, connection route and limitations.
2. Guide the user to enable that source app's sharing to Health Connect where supported. Use documented settings intents/deep links only; otherwise show current step-by-step instructions and an Open App action.
3. Request Nobelle's read permission for the chosen Health Connect types through the official system permission screen. The source app must be permitted to write and Nobelle must be permitted to read.
4. Offer application-level source selection and a preview of available records. Health Connect system grants are generally by data type, not a magical per-vendor login; enforce Nobelle's additional source allowlist in its own query/ingestion path where provenance permits.
5. Separately request permission to analyze selected categories and sync those Nobelle copies to the user's own Google Drive. Permissions may be granted partially or refused; no feature should demand unrelated categories.
6. Run a real sample import before labelling a category **Receiving data**. Distinguish permission granted, awaiting first record, importing, current, delayed, limited history, needs setup, approval pending and unsupported.
7. Show latest available measurement time separately from last successful sync attempt. A recent attempt with no records does not make old readings fresh.
8. Allow per-category/source pause, disconnect, permission management and deletion of Nobelle copies. Explain that stopping collection is separate from deleting prior data or stopping the source app's own collection.

Do not request the user's Samsung, OneTouch, OMRON or 1byone password. No screen scraping, accessibility-based extraction, private app-database access or reverse-engineered authentication. A user grant cannot create an API a vendor has not provided.

### Connection matrix and fallback
| Source | Preferred supported route | Target data | Important limits |
| --- | --- | --- | --- |
| Samsung Health | Samsung Health -> Health Connect -> Nobelle; optional approved Samsung Data SDK | Shared activity, exercise, sleep, heart/body/vitals records | Category/version/device dependent; Samsung proprietary metrics and Health Monitor outputs are not assumed |
| Google Health / Google Fit | App -> Health Connect -> Nobelle | Records the specific Google app actually writes | A displayed metric is not proof it is exported. Do not depend on new legacy Google Fit API integration |
| OneTouch Reveal | Reveal -> Health Connect -> Nobelle | Shared blood-glucose measurements and available metadata | Canadian support documents this route and says Health Connect receives readings taken after connection; do not promise automatic historical backfill |
| OMRON connect | Exact installed regional app's verified Health Connect route; documented bridge only if necessary | Blood pressure, pulse and any actually shared scale/body records | Several regional apps/packages exist. Verify Canada's installed variant, category and export behaviour; another region's listing does not establish compatibility |
| 1byone Health | Verify actual current sharing settings and end-to-end arrival through Health Connect; Google Fit bridge only if still working | Weight/body composition and other actually shared measurements | Publisher listing advertises Google Fit sync. Direct Health Connect support, full metrics and current bridge reliability are not confirmed by that claim |

For every connector, record exact app package/version, country, device model, read/write direction, available categories, oldest accessible reading and last verified date. Do not install a different regional medical app just to obtain a connection.

**Fallbacks:**
- If a vendor offers a documented consumer API/SDK, integrate only after confirming eligibility, terms and user OAuth support. LifeScan advertises an API; developer enrollment and access requirements must be verified. If it requires a server to retain user records/tokens, leave it disabled under the current Google-Drive-only policy.
- The owner allows selected source services to supply existing records; this does not authorize Nobelle to write personal data back into those vendor clouds or collect unrelated vendor data.
- If official CSV or other structured export exists, support user-selected file import with a format-specific parser, preview, validation, source label and deduplication. Do not advertise CSV export for an app before checking that its actual version offers it.
- PDF/image reports may use local OCR with required user confirmation, including dates and units. Never call OCR-only reports an automatic connection.
- Manual entry is always available.
- Direct Bluetooth collection from a meter/cuff/scale is separate from connecting its app; implement only for a documented compatible device/protocol/SDK after model verification. No universal Bluetooth compatibility claim.
- Fallback requiring an additional third-party bridge app must be clearly described and separately chosen; do not silently route personal data through another service.

### Import and data-quality rules
Read-only collection by default. Preserve source record IDs, origin app, original device if exposed, measurement and sync timestamps, units, quality flags and available context such as fasting/meal timing or BP position.
- Multi-user scales/cuffs may contain other household members' measurements. Use verified profile association or a selection/confirmation step; do not import every device reading as Collins's data.
- Do not attribute an intermediary record to OMRON or 1byone unless metadata establishes the original source. Show “via Google Health/Health Connect; original device unavailable” when necessary.
- A single reading relayed through multiple apps is one observation. Use original IDs when preserved and cautious time/value/context duplicate candidates otherwise; never automatically collapse distinct repeated cuff/glucose readings solely because values match.
- Keep conflicting readings visible for review; no silent averaging across different methods or measurement contexts.
- Normalize units with tested conversions while retaining raw value/unit. Flag impossible, future-dated and suspicious readings; do not invent corrected values.
- Fingerstick glucose is intermittent sampling, not a continuous glucose stream. Do not compute CGM time-in-range, overnight glucose or glucose variability as if dense CGM data existed. HbA1c is a lab result; do not substitute an unvalidated estimate from sparse measurements.
- Weight/body-composition values derived by a scale are labelled device estimates; do not present proprietary “body age” or visceral-fat indices as universally comparable clinical measurements.
- Display import history, permission changes and source lineage. Recompute affected summaries after edits/deletions and flag prior insights as outdated.

## 26. Nobelle Health AI — required explanations, insights and recommendations
Add a clearly labelled **Ask Nobelle Health AI** entry in My Health and an **Explain this** button on marker charts. Keep the existing Coach interface focused on exercise; health explanations live primarily in My Health.

### User experience
- Plain-language text with optional on-device read-aloud and captions.
- Suggested questions: “Explain my latest readings”; “How did my recorded blood pressure change this month?”; “What does HbA1c mean?”; “How consistent have my gym visits been?”; “Is my sleep different on training days?”; “What information should I discuss with my clinician?”
- User selects a date range, categories and optional context. Show what data will be used before analysis.
- Responses use four compact parts: **What the records show**, **What it may mean**, **Practical next steps**, and **Limits/data gaps**.
- Tappable evidence links open the exact readings/charts, source and measurement context. Educational claims link to curated authoritative references with review dates.
- Weekly/monthly summaries cover activity, sleep, recorded vitals, body trends and fasting history where enabled. Include “not enough comparable data” when appropriate.
- Suggested actions are editable proposals: schedule gym time, set a measurement reminder, review technique, plan recovery, log meal/symptom context, improve sleep routine or prepare questions for a professional. Never change the user's plan automatically.
- Tips/recommendations must be relevant to the user's explicitly entered goals and constraints. Include food/activity education from reviewed content without prescribing treatment or making guaranteed claims.
- “Questions for my clinician” and a downloadable report require user action; no automatic emailing, clinician sharing or external upload.

### Privacy-preserving execution
The prior **personal Google Drive only** cloud-storage rule remains in force.
- Run personal-data analytics and AI inference on the phone using a validated on-device model/runtime, such as a current supported Google AI Edge implementation. Verify model licence, distribution rules, hardware/RAM compatibility, download size, offline behaviour, latency and thermal/battery impact.
- Downloading a public model is allowed; uploading personal prompts, embeddings, readings or responses to an external model endpoint is not.
- Do not use Gemini API, Vertex AI, OpenAI API or another remote inference service for personal records under this policy, even if the service is hosted by Google. Personal Google Drive storage is not a private AI execution environment.
- Conversation history is optional, deletable and stored only in protected device storage and, when selected, the user's Drive. Embeddings and derived summaries are also personal data and follow the same rules.
- On unsupported phones, offer deterministic numerical analysis plus reviewed explanation templates. Clearly label this fallback instead of pretending a generative model ran. Never silently fall back to cloud AI.
- Browser may run a validated local inference model where supported or show insights already generated on the phone and synced through Drive. Watch shows brief cached insights; no watch LLM dependency.
- Core analytics and safe explanations must remain available if the optional model download fails. The requested AI model path must be implemented and validated on at least one supported phone before declaring the AI feature complete.

### Grounded analytics and clinical boundaries
Compute aggregates with deterministic code before the language model sees them. The model explains verified facts; it must not perform unverified arithmetic or invent missing values.
- Define denominators: e.g., BP average of eligible recorded measurements, number of measurement days, date range and measurement context; not “your average all-day blood pressure.”
- Compare like-for-like measurements and expose sample counts, gaps, source changes and method changes. Only describe correlations when data supports them; do not infer cause from a correlation.
- Separate facts, plausible explanations and general educational suggestions. Never diagnose diabetes complications, hypertension, kidney disease or heart conditions from app data.
- Do not declare the user safe to fast/exercise from old readings, absent records or a recovery score.
- No medication initiation, discontinuation, dose/timing adjustments or unverified supplement recommendations. A recommendation can be to seek individual professional advice.
- Do not replace a measured laboratory result with an AI estimate or contradict a clinician's documented limits.
- General medical education uses a bundled, versioned and professionally reviewed knowledge collection from authoritative sources. Clinical reference ranges/alerts must identify jurisdiction, source and context; no universal thresholds invented by the model.
- Urgent symptom/critical-reading routing uses separately reviewed deterministic logic and user guidance, outside the generative model. No promise of real-time emergency surveillance from delayed imports.
- Screen imported notes/reports as untrusted data, never as instructions to change system rules, leak records or call tools. The AI has no autonomous account, sharing or medication actions.
- Store an InsightRecord with source observation IDs, computation/knowledge/model versions, generation time and date range. Invalidate or regenerate after source edits/deletions, rather than displaying stale conclusions as current.

Suggested components: HealthConnectorRegistry, ConsentManager, ProvenanceResolver, UnitNormalizer, DataQualityEngine, LocalAnalyticsEngine, ReviewedKnowledgeStore, OnDeviceInsightEngine, SafetyRouter and InsightEvidenceView.

## 27. Connection and AI milestones / acceptance
- M0: add five source cards, permission screens, evidence-linked AI mockups and a verified Canadian app/device support matrix. Record missing device/app identifiers as owner setup actions; continue unblocked work.
- M1: implement Health Connect read permissions, Samsung/Google shared-data ingestion and connection diagnostics with genuine sample records where hardware is available. No mocked connected badges.
- M2: OneTouch/OMRON/1byone guided setup, actual supported categories, fallback imports, lineage/deduplication and source-level deletion. A blocked vendor API remains visibly pending with a documented functional fallback.
- M3: deterministic analytics, reviewed explanations, locally running AI on a supported phone, evidence links, optional voice and privacy/security regression. This is required product scope.

Acceptance:
1. Grant some categories and deny others; only authorized/selected records are ingested, analyzed and synced.
2. Complete a real measurement -> source app -> supported exchange -> Nobelle -> own Drive test per available source. Identify exact versions, units, timestamps and metadata. Never claim success for untested categories or regional variants.
3. OneTouch new-reading-only history restriction is visible; unsupported backfill is not fabricated.
4. A 1byone record routed through an intermediary is not duplicated or falsely attributed.
5. Source disconnect stops collection; deletion invalidates related AI summaries and removes selected Nobelle/Drive copies.
6. Compare analytics against fixed reference datasets, including units, sparse glucose, irregular BP timing, conflicting sources, timezone changes and missing records.
7. With network access blocked after model download, generate a grounded AI explanation on a supported phone.
8. Network inspection confirms no personal prompts, embeddings, insights or health records reach remote inference/analytics endpoints.
9. Prompt-injection text in imported reports does not change permissions, reveal other data or initiate actions.
10. Numeric statements match deterministic outputs and evidence IDs; answers with inadequate data say so. Evaluate clinically reviewed cases for inappropriate diagnosis, medication advice, false reassurance and unsafe fasting/exercise guidance.
11. Validate model failure, unsupported hardware, overheating and low-memory fallback; clearly distinguish model output from reviewed templates.
12. Source applications' own cloud policies remain explained separately from Nobelle's Google-account-only storage.

## 28. Additional reference baseline for integrations and local AI
Checked 16 October 2026; recheck version/region specifics when building. Publisher documentation establishes a potential route, not successful verification on Collins's devices.
- Samsung Health Connect setup/FAQ: https://developer.samsung.com/health/health-connect-faq.html
- Google Health and Health Connect: https://support.google.com/googlehealth/answer/14506680?hl=en
- Google Fit and Health Connect: https://support.google.com/fit/answer/12830119?hl=en
- OneTouch Canadian Android app sharing: https://support.onetouch.com/articles/en_US/Knowledge/OneTouch-Reveal-Mobile-App-Android-Apps-canada
- LifeScan developer access: https://www.onetouch.com/developer
- OMRON Canadian app information: https://omronhealthcare.com/en-ca/omron-connect-app
- OMRON regional Android listing documenting Health Connect (not proof for Canada's installed variant): https://play.google.com/store/apps/details?id=jp.co.omron.healthcare.omron_connect
- 1byone publisher Android listing: https://play.google.com/store/apps/details?id=com.quhwa.health
- Google AI Edge on-device inference: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
