import { describe, expect, it } from "vitest";
import { attendanceWeeks, durationSeconds, fastingCompletions, healthHistory, localDate, weekStart } from "./history";
import type { FastingSession, GymVisit, HealthObservation } from "./model";

const observation = (id: string, overrides: Partial<HealthObservation> = {}): HealthObservation => ({
  id, ownerUID: "owner", marker: "steps", value: 100, unit: "count",
  measuredUtc: "2025-01-05T12:00:00Z", provenance: "MEASURED_IMPORTED", ...overrides,
});
const fast = (id: string, overrides: Partial<FastingSession> = {}): FastingSession => ({
  id, ownerUID: "owner", status: "COMPLETED", actualStartInstant: "2025-01-05T12:00:00Z",
  actualEndInstant: "2025-01-06T04:00:00Z", ...overrides,
});

describe("ISO calendar weeks", () => {
  it("handles Sunday, Monday, year boundaries and invalid dates", () => {
    expect(weekStart("2025-01-05")).toBe("2024-12-30");
    expect(weekStart("2025-01-06")).toBe("2025-01-06");
    expect(weekStart("2025-02-30")).toBeNull();
    expect(weekStart(null)).toBeNull();
  });
  it("uses supplied device timezone across midnight", () => {
    expect(localDate("2025-01-06T00:30:00Z", "America/New_York")).toBe("2025-01-05");
    expect(localDate("invalid", "UTC")).toBeNull();
  });
});

describe("health history", () => {
  it("sums daily steps, keeps provenance, real zeroes and missing days distinct", () => {
    const result = healthHistory([
      observation("a"), observation("b", { value: 50, provenance: "MANUALLY_ENTERED" }),
      observation("zero", { value: 0, measuredUtc: "2025-01-07T00:00:00Z" }),
      observation("deleted", { deletedUtc: "2025-01-08T00:00:00Z" }),
      observation("old", { measuredUtc: "2024-12-01T00:00:00Z" }),
      observation("invalid", { value: NaN }),
    ], 7, "2025-01-08T12:00:00Z", "UTC");
    expect(result[0].points.map((p) => [p.date, p.value])).toEqual([["2025-01-05", 150], ["2025-01-07", 0]]);
    expect(result[0].points[0]).toMatchObject({ manual: 50, imported: 100 });
  });
  it("does not sum point measurements or mix units; input order does not matter", () => {
    const records = [observation("a", { marker: "weight", unit: "kg", value: 80 }),
      observation("b", { marker: "weight", unit: "kg", value: 81 }),
      observation("c", { marker: "weight", unit: "lb", value: 180 })];
    const result = healthHistory(records, 7, "2025-01-08T00:00:00Z", "UTC");
    expect(result.map((s) => s.points.length)).toEqual([2, 1]);
    expect(healthHistory([...records].reverse(), 7, "2025-01-08T00:00:00Z", "UTC")).toEqual(result);
  });
});

describe("fasting completion and attendance", () => {
  it("counts completed fasts by end date, regardless of reaching target", () => {
    expect(fastingCompletions([fast("a", { endedEarly: true }), fast("b", { status: "CANCELLED" }),
      fast("c", { status: "ACTIVE", actualEndInstant: null }), fast("d", { actualEndInstant: null }),
      fast("e", { deletedUtc: "2025-01-07T00:00:00Z" })], "UTC"))
      .toEqual([{ date: "2025-01-06", value: 1 }]);
    expect(fastingCompletions([fast("a")], "America/New_York"))
      .toEqual([{ date: "2024-12-30", value: 1 }]);
  });
  it("counts distinct recorded attendance dates without timezone conversion", () => {
    const visit = (date: string, deletedUtc?: string): GymVisit => ({
      id: date, ownerUID: "owner", startInstant: "", localAttendanceDate: date, deletedUtc,
    });
    expect(attendanceWeeks([visit("2025-01-05"), visit("2025-01-05"), visit("2025-01-06"),
      visit("invalid"), visit("2025-01-07", "deleted")])).toEqual([
      { date: "2024-12-30", value: 1 }, { date: "2025-01-06", value: 1 },
    ]);
  });
  it("does not invent durations for missing or invalid endpoints", () => {
    expect(durationSeconds("2025-01-01T00:00:00Z", "2025-01-01T01:00:00Z")).toBe(3600);
    expect(durationSeconds("2025-01-01T00:00:00Z")).toBeNull();
    expect(durationSeconds("2025-01-02", "2025-01-01")).toBeNull();
  });
});
