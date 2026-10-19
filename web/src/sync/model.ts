/**
 * TypeScript mirror of the Android sync wire model (§24).
 * Source of truth: android/core-model/.../Sync.kt, Coaching.kt, GymVisit.kt,
 * Fasting.kt, Health.kt, WatchSync.kt. Index signatures on every payload
 * interface tolerate unknown extra fields so newer app builds still decode.
 */

export const ENTITY_TYPES = {
  WORKOUT_SESSION: "workout_session",
  GYM_VISIT: "gym_visit",
  FASTING_SESSION: "fasting_session",
  FASTING_SCHEDULE: "fasting_schedule",
  HEALTH_OBSERVATION: "health_observation",
  WATCH_WORKOUT: "watch_workout",
} as const;

export type EntityType = (typeof ENTITY_TYPES)[keyof typeof ENTITY_TYPES];

export const KNOWN_ENTITY_TYPES: ReadonlySet<string> = new Set(Object.values(ENTITY_TYPES));

export type SyncOp = "UPSERT" | "DELETE";

/** Immutable event stored as a small file in the Drive appDataFolder. */
export interface SyncEvent {
  schemaVersion: number;
  accountNamespace: string;
  installationId: string;
  entityId: string;
  entityType: string;
  operationUuid: string;
  revision: number;
  parentRevision?: number | null;
  op: SyncOp;
  /** Domain payload encoded as a JSON *string* — requires a second JSON.parse. */
  payloadJson: string;
  /** ISO-8601 instant. */
  utc: string;
}

export interface SourceKey {
  sourceNamespace: string;
  sourceRecordId: string;
  originDevice?: string | null;
  importRoute?: string | null;
  [key: string]: unknown;
}

export interface WorkoutSession {
  id: string;
  ownerUID: string;
  templateId?: string | null;
  category: string;
  state: string; // PLANNED | ACTIVE | PAUSED | COMPLETED | DISCARDED
  startedUtc?: string | null;
  endedUtc?: string | null;
  startTimeZone?: string | null;
  sourceOffsetMinutes?: number | null;
  originDeviceId: string;
  leaseOwnerDeviceId?: string | null;
  sequence?: number;
  gymVisitId?: string | null;
  duringFast?: boolean;
  source?: SourceKey | null;
  revision?: number;
  deletedUtc?: string | null;
  [key: string]: unknown;
}

export interface ExerciseSet {
  id: string;
  sessionId: string;
  exerciseId: string;
  setIndex: number;
  reps?: number | null;
  loadKg?: number | null;
  rpe?: number | null;
  rir?: number | null;
  durationSec?: number | null;
  completedUtc?: string | null;
  inferredReps?: number | null;
  inferenceConfidence?: number | null;
  painFlag?: boolean;
  notes?: string | null;
  restSec?: number | null;
  tempo?: string | null;
  setType?: string;
  groupId?: string | null;
  substitutedForExerciseId?: string | null;
  revision?: number;
  [key: string]: unknown;
}

/** workout_session payloadJson shape: {session, sets}. */
export interface WorkoutSessionPayload {
  session: WorkoutSession;
  sets: ExerciseSet[];
  [key: string]: unknown;
}

export interface GymVisit {
  id: string;
  ownerUID: string;
  startInstant: string;
  endInstant?: string | null;
  originalTimeZone?: string;
  localAttendanceDate: string;
  gymLabel?: string | null;
  workoutIds?: string[];
  category?: string | null;
  notes?: string | null;
  confirmed?: boolean;
  revision?: number;
  deletedUtc?: string | null;
  [key: string]: unknown;
}

export interface FastingCheckIn {
  utc: string;
  hunger?: number | null;
  energy?: number | null;
  mood?: number | null;
  symptoms?: string | null;
  [key: string]: unknown;
}

export interface FastingSession {
  id: string;
  ownerUID: string;
  actualStartInstant: string;
  actualEndInstant?: string | null;
  originalStartZone?: string;
  originalEndZone?: string | null;
  targetDurationSec?: number;
  scheduleId?: string | null;
  status: string; // ACTIVE | COMPLETED | CANCELLED
  endedEarly?: boolean;
  notes?: string | null;
  checkIns?: FastingCheckIn[];
  revision?: number;
  deletedUtc?: string | null;
  [key: string]: unknown;
}

export interface FastingSchedule {
  id: string;
  ownerUID: string;
  enabled?: boolean;
  fastingHours?: number;
  eatingHours?: number;
  startLocalTime?: string;
  daysOfWeek?: number[];
  timeZonePolicy?: string;
  exceptions?: string[];
  revision?: number;
  [key: string]: unknown;
}

export interface HealthObservation {
  id: string;
  ownerUID: string;
  marker: string;
  group?: string;
  value?: number;
  unit?: string;
  rawValue?: number | null;
  rawUnit?: string | null;
  measuredUtc: string;
  timeZoneOffsetSec?: number | null;
  provenance?: string;
  method?: string;
  source?: SourceKey | null;
  qualityFlag?: string;
  context?: Record<string, string>;
  importedUtc?: string | null;
  revision?: number;
  deletedUtc?: string | null;
  [key: string]: unknown;
}

/**
 * watch_workout payloadJson shape. Unlike every other entity, startedUtc and
 * endedUtc are epoch MILLISECONDS (numbers), not ISO instants.
 */
export interface WatchWorkoutPayload {
  schemaVersion?: number;
  id: string;
  activity?: string;
  startedUtc: number;
  endedUtc: number;
  durationSec: number;
  hrSampleCount?: number;
  avgBpm?: number | null;
  maxBpm?: number | null;
  [key: string]: unknown;
}

/** DELETE op payloadJson shape. */
export interface DeletionTombstone {
  entityId: string;
  entityType: string;
  ownerUID: string;
  deletedUtc: string;
  operationUuid: string;
  [key: string]: unknown;
}

/**
 * Event filenames are `ev_{installationId}_{epochMs}_{operationUuid}.json`.
 * The uuid is the segment between the LAST `_` and `.json`; anything not
 * matching the `ev_*.json` shape returns null.
 */
export function operationUuidFromFilename(name: string): string | null {
  if (!name.startsWith("ev_") || !name.endsWith(".json")) return null;
  const stem = name.slice(0, -".json".length);
  const sep = stem.lastIndexOf("_");
  if (sep < 0) return null;
  const uuid = stem.slice(sep + 1);
  return uuid.length > 0 ? uuid : null;
}

/** Decode one event file body; throws on malformed JSON or missing fields. */
export function decodeEvent(bytes: Uint8Array): SyncEvent {
  const parsed = JSON.parse(new TextDecoder().decode(bytes)) as Partial<SyncEvent> | null;
  if (
    !parsed ||
    typeof parsed !== "object" ||
    typeof parsed.entityId !== "string" ||
    typeof parsed.entityType !== "string" ||
    typeof parsed.operationUuid !== "string" ||
    typeof parsed.payloadJson !== "string" ||
    (parsed.op !== "UPSERT" && parsed.op !== "DELETE") ||
    typeof parsed.utc !== "string" ||
    typeof parsed.revision !== "number"
  ) {
    throw new Error("Malformed sync event");
  }
  return parsed as SyncEvent;
}
