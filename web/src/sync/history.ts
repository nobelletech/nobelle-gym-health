import type { FastingSession, GymVisit, HealthObservation } from "./model";

const DAY = 86_400_000;

/** Calendar dates, not UTC instants. Strict validation avoids rolling invalid dates forward. */
export function validDate(date: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date;
}

/** Android ProgressSummary uses the caller's device zone for instant-based records. */
export function localDate(instant: string | number | null | undefined, zone?: string): string | null {
  if (instant == null) return null;
  const date = new Date(instant);
  if (!Number.isFinite(date.getTime())) return null;
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(date);
  const part = (type: string) => parts.find((p) => p.type === type)?.value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

/** ISO Monday-start week key; UTC arithmetic here operates on calendar dates only. */
export function weekStart(date: string | null): string | null {
  if (!date || !validDate(date)) return null;
  const ms = Date.parse(date);
  return new Date(ms - ((new Date(ms).getUTCDay() + 6) % 7) * DAY).toISOString().slice(0, 10);
}

export type CountPoint = { date: string; value: number };

/** Only observed weeks are emitted. Missing weeks are not asserted to be zero. */
export function weeklyCounts(dates: Iterable<string | null>): CountPoint[] {
  const counts = new Map<string, number>();
  for (const date of dates) {
    const week = weekStart(date);
    if (week) counts.set(week, (counts.get(week) ?? 0) + 1);
  }
  return [...counts].sort(([a], [b]) => a.localeCompare(b)).map(([date, value]) => ({ date, value }));
}

export function fastingCompletions(sessions: Iterable<FastingSession>, zone?: string): CountPoint[] {
  return weeklyCounts([...sessions]
    .filter((s) => !s.deletedUtc && s.status === "COMPLETED")
    .map((s) => localDate(s.actualEndInstant, zone)));
}

export function attendanceWeeks(visits: Iterable<GymVisit>): CountPoint[] {
  // Android counts distinct attendance days, not duplicate visits on the same date.
  return weeklyCounts(new Set([...visits].filter((v) => !v.deletedUtc)
    .map((v) => v.localAttendanceDate).filter(validDate)));
}

export function durationSeconds(start: string, end?: string | null): number | null {
  if (!end) return null;
  const seconds = (Date.parse(end) - Date.parse(start)) / 1000;
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : null;
}

export type ObservationSource = "manual" | "imported" | "unknown";
export function observationSource(observation: HealthObservation): ObservationSource {
  if (observation.provenance === "MANUALLY_ENTERED") return "manual";
  return observation.provenance === "MEASURED_IMPORTED" ? "imported" : "unknown";
}

export type HealthPoint = {
  date: string;
  ms: number;
  value: number;
  source?: ObservationSource;
  manual?: number;
  imported?: number;
  unknown?: number;
};
export type HealthSeries = { marker: string; unit: string; points: HealthPoint[] };

/** Daily additive step totals retain provenance contributions. Other markers keep each sample.
 * Units are never combined. Window is anchored to the supplied snapshot instant, not a live clock.
 */
export function healthHistory(
  observations: Iterable<HealthObservation>, days: number, anchor: string, zone?: string,
): HealthSeries[] {
  const endDate = localDate(anchor, zone);
  if (!endDate) return [];
  const startDate = new Date(Date.parse(endDate) - (days - 1) * DAY).toISOString().slice(0, 10);
  const series = new Map<string, HealthSeries>();
  const ordered = [...observations].sort((a, b) => a.measuredUtc.localeCompare(b.measuredUtc) || a.id.localeCompare(b.id));
  for (const obs of ordered) {
    if (obs.deletedUtc || typeof obs.value !== "number" || !Number.isFinite(obs.value)) continue;
    const date = localDate(obs.measuredUtc, zone);
    if (!date || date < startDate || date > endDate) continue;
    const unit = obs.unit || "unit not recorded";
    const key = JSON.stringify([obs.marker, unit]);
    let group = series.get(key);
    if (!group) {
      group = { marker: obs.marker, unit, points: [] };
      series.set(key, group);
    }
    const source = observationSource(obs);
    if (obs.marker === "steps") {
      let point = group.points.find((p) => p.date === date);
      if (!point) {
        point = { date, ms: Date.parse(date), value: 0 };
        group.points.push(point);
      }
      point.value += obs.value;
      point[source] = (point[source] ?? 0) + obs.value;
    } else {
      group.points.push({ date, ms: Date.parse(obs.measuredUtc), value: obs.value, source });
    }
  }
  return [...series.values()].sort((a, b) => a.marker.localeCompare(b.marker) || a.unit.localeCompare(b.unit))
    .map((s) => ({ ...s, points: s.points.sort((a, b) => a.ms - b.ms || a.value - b.value) }));
}
