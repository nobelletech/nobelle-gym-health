import type { ReactNode } from "react";
import { durationSeconds, localDate, weekStart } from "../sync/history";
import { formatDurationSec, formatInstant, formatMeters } from "../sync/format";
import { useSnapshot } from "../sync/store";
import { RoutePolyline } from "./Progress";

type Entry = { key: string; date: string | null; time: number; content: ReactNode };

export default function Workouts() {
  const { snapshot } = useSnapshot();
  const entries: Entry[] = [];
  for (const { session, sets } of snapshot?.workouts.values() ?? []) {
    if (session.deletedUtc || session.state !== "COMPLETED") continue;
    const instant = session.endedUtc ?? session.startedUtc;
    entries.push({ key: `workout:${session.id}`, date: localDate(instant), time: Date.parse(instant ?? ""), content: <>
      <h3>Workout · {session.category}</h3>
      <p>{formatInstant(session.startedUtc)} → {formatInstant(session.endedUtc)} · {formatDurationSec(durationSeconds(session.startedUtc ?? "", session.endedUtc))}</p>
      <details><summary>{sets.length} sets — expand set table</summary>
        {!sets.length ? <p>No set data.</p> : <div className="table-scroll"><table>
          <thead><tr><th>Exercise ID</th><th>Set</th><th>Reps</th><th>Load (kg)</th><th>RPE</th></tr></thead>
          <tbody>{[...sets].sort((a, b) => a.exerciseId.localeCompare(b.exerciseId) || a.setIndex - b.setIndex || a.id.localeCompare(b.id)).map((set) =>
            <tr key={set.id}><td>{set.exerciseId}</td><td>{set.setIndex}</td><td>{set.reps ?? "—"}</td><td>{set.loadKg ?? "—"}</td><td>{set.rpe ?? "—"}</td></tr>)}</tbody>
        </table></div>}
      </details>
    </> });
  }
  for (const watch of snapshot?.watchWorkouts.values() ?? []) {
    if (watch.deletedUtc) continue;
    entries.push({ key: `watch:${watch.id}`, date: localDate(watch.startedUtc), time: watch.startedUtc, content: <>
      <h3>Watch · {watch.activity ?? "workout"}</h3>
      <p>{formatInstant(localDate(watch.startedUtc) ? new Date(watch.startedUtc).toISOString() : null)} · {formatDurationSec(watch.durationSec)} · average {watch.avgBpm ?? "—"} bpm · max {watch.maxBpm ?? "—"} bpm · HR samples {watch.hrSampleCount ?? "—"}</p>
    </> });
  }
  for (const outdoor of snapshot?.outdoorSessions.values() ?? []) {
    if (outdoor.deletedUtc) continue;
    entries.push({ key: `outdoor:${outdoor.id}`, date: localDate(outdoor.startedUtc), time: Date.parse(outdoor.startedUtc), content: <>
      <h3>Outdoor · {outdoor.activity}</h3>
      <p>{formatInstant(outdoor.startedUtc)} · {formatDurationSec(outdoor.durationSec)} · {formatMeters(outdoor.distanceMeters)} · GPS points {outdoor.pointCount ?? outdoor.route?.length ?? "—"}</p>
      <RoutePolyline points={outdoor.route ?? []} />
    </> });
  }
  entries.sort((a, b) => (Number.isFinite(b.time) ? b.time : 0) - (Number.isFinite(a.time) ? a.time : 0) || a.key.localeCompare(b.key));
  const weeks = new Map<string, Entry[]>();
  for (const entry of entries) {
    const key = weekStart(entry.date) ?? "Date not recorded";
    weeks.set(key, [...(weeks.get(key) ?? []), entry]);
  }
  return <>
    <h1>Workout history</h1>
    <div className="card"><p>ISO weeks run Monday–Sunday in your browser’s local timezone. Completed workouts use their end date (start date if no end is recorded); watch and outdoor sessions use their start date. Sources are shown separately, not deduplicated.</p></div>
    {!entries.length && <div className="card"><p>No workout data. Restore from Drive on Connections.</p></div>}
    {[...weeks].sort(([a], [b]) => b.localeCompare(a)).map(([week, items]) => <section className="card" key={week}>
      <h2>{week === "Date not recorded" ? week : `Week of ${week}`}</h2>
      {items.map((entry) => <article className="history-entry" key={entry.key}>{entry.content}</article>)}
    </section>)}
  </>;
}
