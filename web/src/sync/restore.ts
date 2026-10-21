import type { AppDataDriveClient, DriveFile } from "../google/drive";
import {
  decodeEvent,
  ENTITY_TYPES,
  KNOWN_ENTITY_TYPES,
  operationUuidFromFilename,
} from "./model";
import type {
  FastingSchedule,
  FastingSession,
  GymVisit,
  HealthObservation,
  OutdoorSession,
  SyncEvent,
  TrainingPlan,
  WatchWorkoutPayload,
  WorkoutSessionPayload,
  WorkoutTemplate,
} from "./model";

/**
 * Downloads every SyncEvent file from the user's Drive appDataFolder and
 * folds them into an in-memory DashboardSnapshot. Apply semantics mirror
 * Android's RemoteEventApplier (§13):
 *  - dedup by operationUuid (parsed from the `ev_*_{uuid}.json` filename, so
 *    already-applied bodies are never re-fetched within the session),
 *  - deterministic order: sort each batch by (utc, revision, operationUuid),
 *    last write wins per (entityType, entityId),
 *  - a DELETE writes a tombstone that blocks any later-arriving UPSERT for
 *    the same entity — deleted data must not reappear,
 *  - unknown entity types and malformed payloads count as skipped and are
 *    NOT recorded as applied, so a later build can still process them.
 *
 * The applied-operation set and the per-entity record map live in module
 * state so a second restore only fetches new files; snapshot Maps are
 * rebuilt from the record map on every call.
 */

/** Structural surface of AppDataDriveClient — lets tests fake Drive. */
export type DriveEventSource = Pick<AppDataDriveClient, "list" | "read">;

export interface RestoreProgress {
  filesScanned: number;
  filesFetched: number;
  applied: number;
  skipped: number;
}

export interface DashboardSnapshot {
  workouts: Map<string, WorkoutSessionPayload>;
  workoutTemplates: Map<string, WorkoutTemplate>;
  trainingPlans: Map<string, TrainingPlan>;
  watchWorkouts: Map<string, WatchWorkoutPayload>;
  outdoorSessions: Map<string, OutdoorSession>;
  gymVisits: Map<string, GymVisit>;
  fastingSessions: Map<string, FastingSession>;
  fastingSchedules: Map<string, FastingSchedule>;
  healthObservations: Map<string, HealthObservation>;
  /** tombstoneKey(entityType, entityId) for every deleted entity. */
  tombstones: Set<string>;
  applied: number;
  skipped: number;
  filesScanned: number;
  restoredUtc: string;
  /** accountNamespace seen on applied events — display only, no enforcement. */
  ownerNamespace?: string;
}

export function tombstoneKey(entityType: string, entityId: string): string {
  return `${entityType}${entityId}`;
}

type StoredRecord = {
  entityType: string;
  entityId: string;
  /** Ordering key of the last applied UPSERT, for last-writer-wins. */
  utcMs: number;
  revision: number;
  value: unknown;
};

const appliedOps = new Set<string>();
const records = new Map<string, StoredRecord>();
const tombstones = new Set<string>();
let ownerNamespace: string | undefined;

/** Clears all in-memory restore state (disconnect, tests). */
export function resetRestoreState(): void {
  appliedOps.clear();
  records.clear();
  tombstones.clear();
  ownerNamespace = undefined;
}

function utcMillis(iso: string): number {
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? 0 : ms;
}

function isNewer(incoming: { utcMs: number; revision: number }, stored: StoredRecord): boolean {
  if (incoming.utcMs !== stored.utcMs) return incoming.utcMs > stored.utcMs;
  return incoming.revision >= stored.revision;
}

type DecodedPayload = { ok: true; value: unknown } | { ok: false };

function decodePayload(event: SyncEvent): DecodedPayload {
  let parsed: unknown;
  try {
    parsed = JSON.parse(event.payloadJson);
  } catch {
    return { ok: false };
  }
  if (!parsed || typeof parsed !== "object") return { ok: false };
  switch (event.entityType) {
    case ENTITY_TYPES.WORKOUT_SESSION: {
      const p = parsed as WorkoutSessionPayload;
      if (!p.session || typeof p.session !== "object" || p.session.id !== event.entityId) {
        return { ok: false };
      }
      const sets = Array.isArray(p.sets) ? p.sets : [];
      if (sets.some((s) => !s || s.sessionId !== p.session.id)) return { ok: false };
      return { ok: true, value: { session: p.session, sets } };
    }
    case ENTITY_TYPES.WORKOUT_TEMPLATE: {
      const template = parsed as WorkoutTemplate;
      if (template.id !== event.entityId || template.ownerUID !== event.accountNamespace ||
          typeof template.name !== "string" || typeof template.category !== "string" ||
          !Array.isArray(template.exercises) ||
          (template.revision ?? 1) !== event.revision ||
          template.exercises.some((exercise) => !exercise || typeof exercise.exerciseId !== "string" ||
            !Number.isInteger(exercise.sets) || exercise.sets <= 0)) return { ok: false };
      return { ok: true, value: template };
    }
    case ENTITY_TYPES.TRAINING_PLAN: {
      const plan = parsed as TrainingPlan;
      if (plan.id !== event.entityId || plan.ownerUID !== event.accountNamespace ||
          typeof plan.weekStartDate !== "string" ||
          !Array.isArray(plan.plannedSessions) ||
          (plan.revision ?? 1) !== event.revision ||
          plan.plannedSessions.some((session) => !session ||
            session.ownerUID !== event.accountNamespace ||
            typeof session.localDate !== "string" ||
            typeof session.category !== "string")) return { ok: false };
      return { ok: true, value: plan };
    }
    case ENTITY_TYPES.GYM_VISIT:
    case ENTITY_TYPES.FASTING_SESSION:
    case ENTITY_TYPES.FASTING_SCHEDULE:
    case ENTITY_TYPES.HEALTH_OBSERVATION:
    case ENTITY_TYPES.WATCH_WORKOUT:
    case ENTITY_TYPES.OUTDOOR_SESSION: {
      const entity = parsed as { id?: unknown };
      if (entity.id !== event.entityId) return { ok: false };
      return { ok: true, value: parsed };
    }
    default:
      // Forward compat: an entity type this build does not know stays
      // unapplied so a later version can still process it.
      return { ok: false };
  }
}

/** Returns true when the op counts as applied (and may join the dedup set). */
function applyEvent(event: SyncEvent, counters: RestoreProgress): boolean {
  const key = tombstoneKey(event.entityType, event.entityId);
  if (event.op === "DELETE") {
    if (!KNOWN_ENTITY_TYPES.has(event.entityType)) {
      counters.skipped++;
      return false;
    }
    tombstones.add(key);
    records.delete(key);
    if (event.accountNamespace) ownerNamespace = event.accountNamespace;
    counters.applied++;
    return true;
  }

  // A tombstone wins over any later-arriving UPSERT, but the op itself
  // still counts as processed.
  if (tombstones.has(key)) {
    if (event.accountNamespace) ownerNamespace = event.accountNamespace;
    counters.applied++;
    return true;
  }
  const decoded = decodePayload(event);
  if (!decoded.ok) {
    counters.skipped++;
    return false;
  }
  const incoming = { utcMs: utcMillis(event.utc), revision: event.revision };
  const existing = records.get(key);
  const revisionFirst =
    event.entityType === ENTITY_TYPES.WORKOUT_TEMPLATE ||
    event.entityType === ENTITY_TYPES.TRAINING_PLAN;
  const newer = revisionFirst && existing
    ? incoming.revision >= existing.revision
    : !existing || isNewer(incoming, existing);
  if (newer) {
    records.set(key, {
      entityType: event.entityType,
      entityId: event.entityId,
      utcMs: incoming.utcMs,
      revision: event.revision,
      value: decoded.value,
    });
  }
  if (event.accountNamespace) ownerNamespace = event.accountNamespace;
  counters.applied++;
  return true;
}

function buildSnapshot(counters: RestoreProgress): DashboardSnapshot {
  const snapshot: DashboardSnapshot = {
    workouts: new Map(),
    workoutTemplates: new Map(),
    trainingPlans: new Map(),
    watchWorkouts: new Map(),
    outdoorSessions: new Map(),
    gymVisits: new Map(),
    fastingSessions: new Map(),
    fastingSchedules: new Map(),
    healthObservations: new Map(),
    tombstones: new Set(tombstones),
    applied: counters.applied,
    skipped: counters.skipped,
    filesScanned: counters.filesScanned,
    restoredUtc: new Date().toISOString(),
    ownerNamespace,
  };
  for (const record of records.values()) {
    switch (record.entityType) {
      case ENTITY_TYPES.WORKOUT_TEMPLATE:
        snapshot.workoutTemplates.set(record.entityId, record.value as WorkoutTemplate);
        break;
      case ENTITY_TYPES.TRAINING_PLAN:
        snapshot.trainingPlans.set(record.entityId, record.value as TrainingPlan);
        break;
      case ENTITY_TYPES.WORKOUT_SESSION:
        snapshot.workouts.set(record.entityId, record.value as WorkoutSessionPayload);
        break;
      case ENTITY_TYPES.WATCH_WORKOUT:
        snapshot.watchWorkouts.set(record.entityId, record.value as WatchWorkoutPayload);
        break;
      case ENTITY_TYPES.OUTDOOR_SESSION:
        snapshot.outdoorSessions.set(record.entityId, record.value as OutdoorSession);
        break;
      case ENTITY_TYPES.GYM_VISIT:
        snapshot.gymVisits.set(record.entityId, record.value as GymVisit);
        break;
      case ENTITY_TYPES.FASTING_SESSION:
        snapshot.fastingSessions.set(record.entityId, record.value as FastingSession);
        break;
      case ENTITY_TYPES.FASTING_SCHEDULE:
        snapshot.fastingSchedules.set(record.entityId, record.value as FastingSchedule);
        break;
      case ENTITY_TYPES.HEALTH_OBSERVATION:
        snapshot.healthObservations.set(record.entityId, record.value as HealthObservation);
        break;
    }
  }
  return snapshot;
}

export async function restoreFromDrive(
  client: DriveEventSource,
  onProgress?: (progress: RestoreProgress) => void,
): Promise<DashboardSnapshot> {
  const counters: RestoreProgress = { filesScanned: 0, filesFetched: 0, applied: 0, skipped: 0 };
  const report = () => onProgress?.({ ...counters });

  // 1. Page through the whole appDataFolder listing.
  const files: DriveFile[] = [];
  let pageToken: string | undefined;
  do {
    const page = await client.list(pageToken, 100);
    files.push(...page.files);
    counters.filesScanned += page.files.length;
    report();
    pageToken = page.nextPageToken;
  } while (pageToken);

  // 2. Fetch and decode bodies not already applied this session.
  const fresh: { event: SyncEvent; fileUuid: string | null }[] = [];
  for (const file of files) {
    const fileUuid = operationUuidFromFilename(file.name);
    if (fileUuid && appliedOps.has(fileUuid)) continue;
    try {
      const bytes = await client.read(file.id);
      counters.filesFetched++;
      fresh.push({ event: decodeEvent(bytes), fileUuid });
    } catch {
      counters.skipped++;
    }
    report();
  }

  // 3. Deterministic apply order: (utc, revision, operationUuid).
  fresh.sort(
    (a, b) =>
      utcMillis(a.event.utc) - utcMillis(b.event.utc) ||
      a.event.revision - b.event.revision ||
      a.event.operationUuid.localeCompare(b.event.operationUuid),
  );

  // 4. Apply onto the persistent record map; applied ops join the dedup set
  //    under both the filename uuid and the event's own operationUuid.
  for (const { event, fileUuid } of fresh) {
    if (appliedOps.has(event.operationUuid)) continue;
    if (applyEvent(event, counters)) {
      appliedOps.add(event.operationUuid);
      if (fileUuid) appliedOps.add(fileUuid);
    }
    report();
  }

  return buildSnapshot(counters);
}
