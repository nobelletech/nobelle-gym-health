import { Link } from "react-router-dom";
import { formatDurationSec, formatInstant } from "../sync/format";
import { attendanceWeeks, durationSeconds, fastingCompletions, localDate } from "../sync/history";
import HistoryChart from "./HistoryChart";
import type { OutdoorRoutePoint } from "../sync/model";
import { useSnapshot } from "../sync/store";

/**
 * Dependency-free route thumbnail: route points are projected into a fixed
 * viewBox with latitude on Y (inverted) and longitude on X, aspect-corrected
 * by cos(mean latitude) so the shape isn't stretched. No map tiles — this is
 * a shape preview, not a geographic map.
 */
export function RoutePolyline({ points }: { points: OutdoorRoutePoint[] }) {
  points = points.filter((p) => Number.isFinite(p.lat) && Number.isFinite(p.lon));
  if (points.length === 0) return null;
  const lats = points.map((p) => p.lat);
  const lons = points.map((p) => p.lon);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLon = Math.min(...lons);
  const maxLon = Math.max(...lons);
  const meanLat = (minLat + maxLat) / 2;
  const xScale = Math.cos((meanLat * Math.PI) / 180) || 1e-6;
  const spanX = Math.max((maxLon - minLon) * xScale, 1e-9);
  const spanY = Math.max(maxLat - minLat, 1e-9);
  const w = 120;
  const h = 80;
  const pad = 6;
  const scale = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
  const xy = points.map((p) => {
    const x = pad + (p.lon - minLon) * xScale * scale;
    const y = h - pad - (p.lat - minLat) * scale;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  if (xy.length === 1) {
    const [cx, cy] = xy[0].split(",").map(Number);
    return (
      <svg width={w} height={h} role="img" aria-label="Recorded route point">
        <circle cx={cx} cy={cy} r={3} fill="currentColor" />
      </svg>
    );
  }
  return (
    <svg width={w} height={h} role="img" aria-label="Recorded route shape">
      <polyline points={xy.join(" ")} fill="none" stroke="currentColor" strokeWidth={2} />
    </svg>
  );
}

export default function Progress() {
  const { snapshot } = useSnapshot();

  const workouts = [...(snapshot?.workouts.values() ?? [])].filter((w) => !w.session.deletedUtc && w.session.state === "COMPLETED");
  const fasts = [...(snapshot?.fastingSessions.values() ?? [])].filter((s) => !s.deletedUtc)
    .sort((a, b) => b.actualStartInstant.localeCompare(a.actualStartInstant) || a.id.localeCompare(b.id));
  const visits = [...(snapshot?.gymVisits.values() ?? [])].filter((v) => !v.deletedUtc)
    .sort((a, b) => b.localAttendanceDate.localeCompare(a.localAttendanceDate) || a.id.localeCompare(b.id));
  return <>
    <h1>Progress</h1>
    <div className="card">
      <h2>Training totals</h2>
      {snapshot ? <p>{workouts.length} completed workouts · {workouts.reduce((sum, w) => sum + w.sets.length, 0)} sets.</p> : <p>No data. Restore from Drive on Connections.</p>}
      <p><Link to="/workouts">View workout, watch and outdoor history →</Link></p>
      <p>Weekly charts use ISO Monday–Sunday weeks. Instant-based records use your browser’s local timezone. Missing weeks are blank, not zero.</p>
    </div>
    <section className="card">
      <h2>Fasting history</h2>
      <p>Completed counts use the recorded end date, including sessions ended early. Status comes from the synced record, not from reaching a target.</p>
      <HistoryChart points={fastingCompletions(fasts)} label="Completed fasts by week starting" unit="completed fasts / week" bars />
      {!fasts.length ? <p>No fasting data.</p> : <div className="table-scroll"><table>
        <thead><tr><th>Started</th><th>Ended</th><th>Status</th><th>Duration</th><th>Target</th></tr></thead>
        <tbody>{fasts.map((fast) => <tr key={fast.id}>
          <td>{formatInstant(fast.actualStartInstant)}</td><td>{formatInstant(fast.actualEndInstant)}</td>
          <td>{fast.status}{fast.endedEarly ? " · ended early" : ""}</td>
          <td>{formatDurationSec(durationSeconds(fast.actualStartInstant, fast.actualEndInstant))}
            {fast.status === "ACTIVE" && !fast.actualEndInstant && snapshot && <small>Elapsed at restore: {formatDurationSec(durationSeconds(fast.actualStartInstant, snapshot.restoredUtc))}</small>}
          </td><td>{formatDurationSec(fast.targetDurationSec)}</td>
        </tr>)}</tbody>
      </table></div>}
    </section>
    <section className="card">
      <h2>Gym attendance</h2>
      <p>Counts are distinct attendance days per week, matching the phone’s Progress summary. Multiple visits on one day count once. Recorded localAttendanceDate is used without timezone conversion.</p>
      <HistoryChart points={attendanceWeeks(visits)} label="Gym attendance by week starting" unit="attendance days / week" bars />
      {!visits.length ? <p>No gym attendance data.</p> : <div className="table-scroll"><table>
        <thead><tr><th>Attendance date</th><th>Gym</th><th>Started</th><th>Duration</th></tr></thead>
        <tbody>{visits.map((visit) => <tr key={visit.id}><td>{visit.localAttendanceDate || "—"}</td><td>{visit.gymLabel ?? "—"}</td>
          <td>{formatInstant(visit.startInstant)}</td><td>{formatDurationSec(durationSeconds(visit.startInstant, visit.endInstant))}</td></tr>)}</tbody>
      </table></div>}
    </section>
    <p>Snapshot restored {formatInstant(snapshot?.restoredUtc)}. Memory-only; no live updates from your phone.
      {snapshot && ` Snapshot date: ${localDate(snapshot.restoredUtc)}.`}</p>
  </>;
}
