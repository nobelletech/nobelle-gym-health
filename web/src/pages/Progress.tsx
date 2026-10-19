import { formatInstant } from "../sync/format";
import { useSnapshot } from "../sync/store";

export default function Progress() {
  const { snapshot } = useSnapshot();

  const totalSets = snapshot
    ? [...snapshot.workouts.values()].reduce((sum, w) => sum + w.sets.length, 0)
    : 0;
  const completedWorkouts = snapshot
    ? [...snapshot.workouts.values()].filter((w) => w.session.state === "COMPLETED").length
    : 0;

  return (
    <>
      <h1>Progress</h1>
      <div className="card">
        <h2>Training totals</h2>
        {snapshot ? (
          <p>
            {completedWorkouts} completed workouts ({snapshot.workouts.size} sessions synced) ·{" "}
            {totalSets} sets · {snapshot.watchWorkouts.size} watch workouts ·{" "}
            {snapshot.gymVisits.size} gym visits.
          </p>
        ) : (
          <p>Strength, running and attendance charts appear after syncing sessions.</p>
        )}
      </div>
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
