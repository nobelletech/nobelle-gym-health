import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DriveFile } from "../google/drive";
import { operationUuidFromFilename } from "./model";
import { resetRestoreState, restoreFromDrive, tombstoneKey } from "./restore";
import type { DriveEventSource } from "./restore";

const OWNER = "accounts.google.com:subject-a";

const encode = (value: unknown) =>
  new TextEncoder().encode(typeof value === "string" ? value : JSON.stringify(value));

function syncEvent(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: 1,
    accountNamespace: OWNER,
    installationId: "install-1",
    entityId: "e1",
    entityType: "gym_visit",
    operationUuid: "op-1",
    revision: 1,
    parentRevision: null,
    op: "UPSERT",
    payloadJson: "{}",
    utc: "2024-01-01T00:00:00Z",
    ...overrides,
  };
}

function gymVisit(id: string, overrides: Record<string, unknown> = {}) {
  return JSON.stringify({
    id,
    ownerUID: OWNER,
    startInstant: "2024-01-01T10:00:00Z",
    originalTimeZone: "America/Toronto",
    localAttendanceDate: "2024-01-01",
    confirmed: true,
    revision: 1,
    ...overrides,
  });
}

function tombstone(entityType: string, entityId: string, operationUuid: string) {
  return JSON.stringify({
    entityId,
    entityType,
    ownerUID: OWNER,
    deletedUtc: "2024-01-02T00:00:00Z",
    operationUuid,
  });
}

function eventFile(event: Record<string, unknown>, id?: string): { file: DriveFile; body: Uint8Array } {
  const op = event.operationUuid as string;
  return {
    file: { id: id ?? `file-${op}`, name: `ev_install-1_1700000000000_${op}.json` },
    body: encode(event),
  };
}

function fakeDrive(pages: { files: DriveFile[]; nextPageToken?: string }[], bodies: Map<string, Uint8Array>) {
  const list = vi.fn(async (pageToken?: string) => pages[pageToken ? Number(pageToken) : 0]);
  const read = vi.fn(async (fileId: string) => {
    const body = bodies.get(fileId);
    if (!body) throw new Error(`no body for ${fileId}`);
    return body;
  });
  const client: DriveEventSource = { list, read };
  return { client, list, read };
}

beforeEach(() => resetRestoreState());

describe("operationUuidFromFilename", () => {
  it("extracts the segment between the last underscore and .json", () => {
    expect(operationUuidFromFilename("ev_inst_1700000000000_op-abc.json")).toBe("op-abc");
    expect(operationUuidFromFilename("ev_a_b_c.json")).toBe("c");
    expect(operationUuidFromFilename("notes.txt")).toBeNull();
    expect(operationUuidFromFilename("other_op.json")).toBeNull();
    expect(operationUuidFromFilename("ev_.json")).toBeNull();
  });
});

describe("restoreFromDrive", () => {
  it("restores templates by revision and prevents resurrection after delete", async () => {
    const template = { id: "t1", ownerUID: OWNER, name: "Upper", category: "Strength", exercises: [{ exerciseId: "push-up", sets: 3, reps: 8 }], revision: 3 };
    const events = [
      syncEvent({ entityType: "workout_template", entityId: "t1", operationUuid: "new", revision: 3, payloadJson: JSON.stringify(template) }),
      syncEvent({ entityType: "workout_template", entityId: "t1", operationUuid: "stale", revision: 2, utc: "2024-01-02T00:00:00Z", payloadJson: JSON.stringify({ ...template, name: "Stale", revision: 2 }) }),
    ].map((event) => eventFile(event));
    const restore = (files: typeof events) => restoreFromDrive(fakeDrive([{ files: files.map((entry) => entry.file) }], new Map(files.map((entry) => [entry.file.id, entry.body]))).client);
    expect((await restore(events)).workoutTemplates.get("t1")?.name).toBe("Upper");
    const deleted = eventFile(syncEvent({ entityType: "workout_template", entityId: "t1", operationUuid: "delete", op: "DELETE", revision: 4 }));
    expect((await restore([deleted])).workoutTemplates.size).toBe(0);
    const resurrect = eventFile(syncEvent({ entityType: "workout_template", entityId: "t1", operationUuid: "resurrect", revision: 5, payloadJson: JSON.stringify({ ...template, revision: 5 }) }));
    const snapshot = await restore([resurrect]);
    expect(snapshot.workoutTemplates.size).toBe(0);
    expect(snapshot.tombstones.has(tombstoneKey("workout_template", "t1"))).toBe(true);
  });

  it("restores training plans by revision and tombstones them", async () => {
    const plan = {
      id: "p1", ownerUID: OWNER, weekStartDate: "2024-01-01", generatedBy: "rules-v1",
      plannedSessions: [
        { id: "ps1", ownerUID: OWNER, localDate: "2024-01-01", templateId: null, category: "full_body", status: "PLANNED", revision: 1 },
        { id: "ps2", ownerUID: OWNER, localDate: "2024-01-03", templateId: null, category: "mobility", status: "SKIPPED", revision: 1 },
      ],
      revision: 2,
    };
    const events = [
      syncEvent({ entityType: "training_plan", entityId: "p1", operationUuid: "new", revision: 2, payloadJson: JSON.stringify(plan) }),
      syncEvent({ entityType: "training_plan", entityId: "p1", operationUuid: "stale", revision: 1, utc: "2024-01-02T00:00:00Z", payloadJson: JSON.stringify({ ...plan, weekStartDate: "2024-01-08", revision: 1 }) }),
    ].map((event) => eventFile(event));
    const restore = (files: typeof events) => restoreFromDrive(fakeDrive([{ files: files.map((entry) => entry.file) }], new Map(files.map((entry) => [entry.file.id, entry.body]))).client);
    const snapshot = await restore(events);
    expect(snapshot.trainingPlans.get("p1")?.weekStartDate).toBe("2024-01-01");
    expect(snapshot.trainingPlans.get("p1")?.plannedSessions.length).toBe(2);
    // Mismatched identity (foreign session owner) stays unapplied.
    const bad = eventFile(syncEvent({ entityType: "training_plan", entityId: "p2", operationUuid: "bad", payloadJson: JSON.stringify({ ...plan, id: "p2", plannedSessions: [{ ...plan.plannedSessions[0], ownerUID: "other" }] }) }));
    expect((await restore([bad])).trainingPlans.has("p2")).toBe(false);
    const deleted = eventFile(syncEvent({ entityType: "training_plan", entityId: "p1", operationUuid: "delete", op: "DELETE", revision: 3 }));
    expect((await restore([deleted])).trainingPlans.size).toBe(0);
    const resurrect = eventFile(syncEvent({ entityType: "training_plan", entityId: "p1", operationUuid: "resurrect", revision: 5, payloadJson: JSON.stringify({ ...plan, revision: 5 }) }));
    const after = await restore([resurrect]);
    expect(after.trainingPlans.size).toBe(0);
    expect(after.tombstones.has(tombstoneKey("training_plan", "p1"))).toBe(true);
  });

  it("pages through the whole file list and applies every event", async () => {
    const a = eventFile(syncEvent({ entityId: "v1", operationUuid: "op-a", payloadJson: gymVisit("v1") }));
    const b = eventFile(syncEvent({ entityId: "v2", operationUuid: "op-b", payloadJson: gymVisit("v2") }));
    const { client, list } = fakeDrive(
      [
        { files: [a.file], nextPageToken: "1" },
        { files: [b.file] },
      ],
      new Map([
        [a.file.id, a.body],
        [b.file.id, b.body],
      ]),
    );

    const snapshot = await restoreFromDrive(client);

    expect(list).toHaveBeenCalledTimes(2);
    expect(snapshot.filesScanned).toBe(2);
    expect(snapshot.applied).toBe(2);
    expect(snapshot.skipped).toBe(0);
    expect(snapshot.gymVisits.size).toBe(2);
    expect(snapshot.gymVisits.get("v1")?.localAttendanceDate).toBe("2024-01-01");
    expect(snapshot.ownerNamespace).toBe(OWNER);
  });

  it("applies the newest revision regardless of listing order", async () => {
    const newer = eventFile(
      syncEvent({
        entityId: "v1",
        operationUuid: "op-new",
        revision: 2,
        utc: "2024-01-02T00:00:00Z",
        payloadJson: gymVisit("v1", { localAttendanceDate: "2024-01-02", revision: 2 }),
      }),
    );
    const older = eventFile(
      syncEvent({
        entityId: "v1",
        operationUuid: "op-old",
        revision: 1,
        utc: "2024-01-01T00:00:00Z",
        payloadJson: gymVisit("v1", { localAttendanceDate: "2024-01-01", revision: 1 }),
      }),
    );
    const { client } = fakeDrive(
      [{ files: [newer.file, older.file] }],
      new Map([
        [newer.file.id, newer.body],
        [older.file.id, older.body],
      ]),
    );

    const snapshot = await restoreFromDrive(client);

    expect(snapshot.gymVisits.size).toBe(1);
    expect(snapshot.gymVisits.get("v1")?.localAttendanceDate).toBe("2024-01-02");
  });

  it("DELETE removes the entity and the tombstone blocks later upserts", async () => {
    const upsert = eventFile(
      syncEvent({ entityId: "v1", operationUuid: "op-up", utc: "2024-01-01T00:00:00Z", payloadJson: gymVisit("v1") }),
    );
    const del = eventFile(
      syncEvent({
        entityId: "v1",
        operationUuid: "op-del",
        op: "DELETE",
        utc: "2024-01-02T00:00:00Z",
        payloadJson: tombstone("gym_visit", "v1", "op-del"),
      }),
    );
    const lateUpsert = eventFile(
      syncEvent({ entityId: "v1", operationUuid: "op-late", utc: "2024-01-03T00:00:00Z", payloadJson: gymVisit("v1") }),
    );
    const { client } = fakeDrive(
      [{ files: [upsert.file, del.file, lateUpsert.file] }],
      new Map([
        [upsert.file.id, upsert.body],
        [del.file.id, del.body],
        [lateUpsert.file.id, lateUpsert.body],
      ]),
    );

    const snapshot = await restoreFromDrive(client);

    expect(snapshot.gymVisits.size).toBe(0);
    expect(snapshot.tombstones.has(tombstoneKey("gym_visit", "v1"))).toBe(true);
    // The tombstone-blocked upsert still counts as processed.
    expect(snapshot.applied).toBe(3);
  });

  it("skips malformed event files and keeps going", async () => {
    const good = eventFile(
      syncEvent({ entityId: "v1", operationUuid: "op-good", payloadJson: gymVisit("v1") }),
    );
    const bad: DriveFile = { id: "file-bad", name: "ev_install-1_1_op-bad.json" };
    const { client } = fakeDrive(
      [{ files: [bad, good.file] }],
      new Map([
        [bad.id, encode("not json{{{")],
        [good.file.id, good.body],
      ]),
    );

    const snapshot = await restoreFromDrive(client);

    expect(snapshot.applied).toBe(1);
    expect(snapshot.skipped).toBe(1);
    expect(snapshot.gymVisits.size).toBe(1);
  });

  it("counts unknown entity types as skipped, not applied, and does not dedup them", async () => {
    const future = eventFile(
      syncEvent({ entityId: "x1", entityType: "future_entity", operationUuid: "op-future" }),
    );
    const { client, read } = fakeDrive(
      [{ files: [future.file] }],
      new Map([[future.file.id, future.body]]),
    );

    const first = await restoreFromDrive(client);
    expect(first.applied).toBe(0);
    expect(first.skipped).toBe(1);
    expect(first.gymVisits.size).toBe(0);
    expect(read).toHaveBeenCalledTimes(1);

    // Not marked applied → a later restore (e.g. newer build) re-fetches it.
    await restoreFromDrive(client);
    expect(read).toHaveBeenCalledTimes(2);
  });

  it("does not re-fetch bodies already applied in this session", async () => {
    const entry = eventFile(
      syncEvent({ entityId: "v1", operationUuid: "op-v1", payloadJson: gymVisit("v1") }),
    );
    const { client, read } = fakeDrive(
      [{ files: [entry.file] }],
      new Map([[entry.file.id, entry.body]]),
    );

    const first = await restoreFromDrive(client);
    expect(read).toHaveBeenCalledTimes(1);
    expect(first.gymVisits.size).toBe(1);

    const second = await restoreFromDrive(client);
    expect(read).toHaveBeenCalledTimes(1);
    // Snapshot still rebuilt fully from the persistent record map.
    expect(second.gymVisits.size).toBe(1);
    expect(second.applied).toBe(0);
    expect(second.filesScanned).toBe(1);
  });

  it("lands watch workouts with epoch-millisecond times", async () => {
    const payload = {
      schemaVersion: 1,
      id: "w1",
      activity: "run",
      startedUtc: 1_700_000_000_000,
      endedUtc: 1_700_003_600_000,
      durationSec: 3600,
      hrSampleCount: 12,
      avgBpm: 140.5,
      maxBpm: 171,
    };
    const entry = eventFile(
      syncEvent({
        entityId: "w1",
        entityType: "watch_workout",
        operationUuid: "op-w1",
        payloadJson: JSON.stringify(payload),
      }),
    );
    const { client } = fakeDrive([{ files: [entry.file] }], new Map([[entry.file.id, entry.body]]));

    const snapshot = await restoreFromDrive(client);
    const workout = snapshot.watchWorkouts.get("w1");

    expect(snapshot.applied).toBe(1);
    expect(workout?.startedUtc).toBe(1_700_000_000_000);
    expect(workout?.endedUtc).toBe(1_700_003_600_000);
    expect(workout?.durationSec).toBe(3600);
    expect(workout?.avgBpm).toBe(140.5);
    expect(workout?.maxBpm).toBe(171);
  });

  it("lands outdoor sessions with their route payload", async () => {
    const payload = {
      id: "o1",
      ownerUID: OWNER,
      activity: "run",
      startedUtc: "2024-01-01T10:00:00Z",
      endedUtc: "2024-01-01T10:30:00Z",
      durationSec: 1800,
      distanceMeters: 4210.5,
      route: [
        { lat: 43.6532, lon: -79.3832, epochMs: 1_704_110_400_000, accuracyM: 4.5 },
        { lat: 43.654, lon: -79.384, epochMs: 1_704_110_402_000 },
      ],
      pointCount: 2,
      createdUtc: "2024-01-01T10:30:00Z",
      revision: 1,
    };
    const entry = eventFile(
      syncEvent({
        entityId: "o1",
        entityType: "outdoor_session",
        operationUuid: "op-o1",
        payloadJson: JSON.stringify(payload),
      }),
    );
    const { client } = fakeDrive([{ files: [entry.file] }], new Map([[entry.file.id, entry.body]]));

    const snapshot = await restoreFromDrive(client);
    const session = snapshot.outdoorSessions.get("o1");

    expect(snapshot.applied).toBe(1);
    expect(session?.activity).toBe("run");
    expect(session?.distanceMeters).toBe(4210.5);
    expect(session?.route?.length).toBe(2);
    expect(session?.pointCount).toBe(2);
  });
});
