import { formatDurationSec, formatInstant, formatMeters } from "../sync/format";
import { useSnapshot } from "../sync/store";

type ActivityItem = {
  key: string;
  ts: number;
  label: string;
  detail: string;
};

export default function Today() {
  const { snapshot } = useSnapshot();

  if (!snapshot) {
    return (
      <>
        <h1>Today</h1>
        <div className="card">
          <h2>Sign in with Google</h2>
          <p>
            Your history syncs only to your own Google Drive (app data folder).
            Nobelle runs no personal-data backend. Sign-in and Drive access are
            separate grants you can revoke anytime.
          </p>
          <p>Connect Google Drive on the Connections page, then restore.</p>
        </div>
        <div className="card">
          <h2>Recent activity</h2>
          <p>Synced sessions appear here once your phone uploads to Drive.</p>
        </div>
      </>
    );
  }

  const items: ActivityItem[] = [];
  for (const workout of snapshot.workouts.values()) {
    if (workout.session.state !== "COMPLETED") continue;
    const startMs = workout.session.startedUtc ? Date.parse(workout.session.startedUtc) : NaN;
    const endMs = workout.session.endedUtc ? Date.parse(workout.session.endedUtc) : NaN;
    const durationSec =
      Number.isFinite(startMs) && Number.isFinite(endMs) ? Math.max(0, (endMs - startMs) / 1000) : undefined;
    items.push({
      key: `workout:${workout.session.id}`,
      ts: Number.isFinite(startMs) ? startMs : 0,
      label: `Workout — ${workout.session.category}`,
      detail: `${formatInstant(workout.session.startedUtc)} · ${workout.sets.length} sets · ${formatDurationSec(durationSec)}`,
    });
  }
  for (const watch of snapshot.watchWorkouts.values()) {
    const bpm = [
      watch.avgBpm != null ? `avg ${Math.round(watch.avgBpm)} bpm` : null,
      watch.maxBpm != null ? `max ${Math.round(watch.maxBpm)} bpm` : null,
    ]
      .filter(Boolean)
      .join(" · ");
    items.push({
      key: `watch:${watch.id}`,
      ts: watch.startedUtc,
      label: `Watch workout — ${watch.activity ?? "workout"}`,
      detail:
        `${new Date(watch.startedUtc).toLocaleString()} · ${formatDurationSec(watch.durationSec)}` +
        (bpm ? ` · ${bpm}` : ""),
    });
  }
  for (const outdoor of snapshot.outdoorSessions.values()) {
    const ts = Date.parse(outdoor.startedUtc);
    items.push({
      key: `outdoor:${outdoor.id}`,
      ts: Number.isFinite(ts) ? ts : 0,
      label: `Outdoor — ${outdoor.activity}`,
      detail:
        `${formatInstant(outdoor.startedUtc)} · ${formatMeters(outdoor.distanceMeters)} · ` +
        `${formatDurationSec(outdoor.durationSec)} · ${outdoor.pointCount ?? 0} GPS points`,
    });
  }
  for (const visit of snapshot.gymVisits.values()) {
    const ts = Date.parse(visit.startInstant);
    items.push({
      key: `visit:${visit.id}`,
      ts: Number.isFinite(ts) ? ts : 0,
      label: `Gym visit${visit.gymLabel ? ` — ${visit.gymLabel}` : ""}`,
      detail: visit.localAttendanceDate || formatInstant(visit.startInstant),
    });
  }
  items.sort((a, b) => b.ts - a.ts);
  const recent = items.slice(0, 10);

  return (
    <>
      <h1>Today</h1>
      <div className="card">
        <h2>Synced data</h2>
        <p>
          {snapshot.workouts.size} workouts · {snapshot.watchWorkouts.size} watch workouts ·{" "}
          {snapshot.outdoorSessions.size} outdoor sessions ·{" "}
          {snapshot.gymVisits.size} gym visits · {snapshot.fastingSessions.size} fasts ·{" "}
          {snapshot.healthObservations.size} health observations — restored{" "}
          {formatInstant(snapshot.restoredUtc)}. Snapshot lives in memory only.
        </p>
      </div>
      <div className="card">
        <h2>Recent activity</h2>
        {recent.length === 0 ? (
          <p>No sessions in the restored snapshot yet.</p>
        ) : (
          <ul>
            {recent.map((item) => (
              <li key={item.key}>
                <strong>{item.label}</strong>
                <br />
                {item.detail}
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
