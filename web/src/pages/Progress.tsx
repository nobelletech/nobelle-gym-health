import { formatDurationSec, formatInstant, formatMeters } from "../sync/format";
import type { OutdoorRoutePoint } from "../sync/model";
import { useSnapshot } from "../sync/store";

/**
 * Dependency-free route thumbnail: route points are projected into a fixed
 * viewBox with latitude on Y (inverted) and longitude on X, aspect-corrected
 * by cos(mean latitude) so the shape isn't stretched. No map tiles — this is
 * a shape preview, not a geographic map.
 */
function RoutePolyline({ points }: { points: OutdoorRoutePoint[] }) {
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

  const totalSets = snapshot
    ? [...snapshot.workouts.values()].reduce((sum, w) => sum + w.sets.length, 0)
    : 0;
  const completedWorkouts = snapshot
    ? [...snapshot.workouts.values()].filter((w) => w.session.state === "COMPLETED").length
    : 0;
  const outdoor = snapshot
    ? [...snapshot.outdoorSessions.values()].sort(
        (a, b) => Date.parse(b.startedUtc) - Date.parse(a.startedUtc),
      )
    : [];

  return (
    <>
      <h1>Progress</h1>
      <div className="card">
        <h2>Training totals</h2>
        {snapshot ? (
          <p>
            {completedWorkouts} completed workouts ({snapshot.workouts.size} sessions synced) ·{" "}
            {totalSets} sets · {snapshot.watchWorkouts.size} watch workouts ·{" "}
            {snapshot.outdoorSessions.size} outdoor sessions ·{" "}
            {snapshot.gymVisits.size} gym visits.
          </p>
        ) : (
          <p>Strength, running and attendance charts appear after syncing sessions.</p>
        )}
      </div>
      {snapshot && (
        <div className="card">
          <h2>Outdoor sessions</h2>
          {outdoor.length === 0 ? (
            <p>No outdoor GPS sessions in the restored snapshot yet.</p>
          ) : (
            <ul>
              {outdoor.slice(0, 10).map((s) => (
                <li key={s.id}>
                  <strong>{s.activity}</strong>
                  <br />
                  {formatInstant(s.startedUtc)} · {formatMeters(s.distanceMeters)} ·{" "}
                  {formatDurationSec(s.durationSec)}
                  <br />
                  <RoutePolyline points={s.route ?? []} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="card">
        <h2>Reports</h2>
        <p>
          Weekly/monthly summaries — descriptive only, not medical interpretation.
          {snapshot ? ` Snapshot restored ${formatInstant(snapshot.restoredUtc)}.` : ""}
        </p>
      </div>
    </>
  );
}
