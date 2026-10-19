import { formatDurationSec, formatInstant } from "../sync/format";
import { useSnapshot } from "../sync/store";

const GROUPS = [
  "Activity",
  "Heart & vitals",
  "Sleep & recovery",
  "Body",
  "Nutrition",
  "Labs & records",
  "Fasting",
];

export default function Health() {
  const { snapshot } = useSnapshot();

  if (!snapshot) {
    return (
      <>
        <h1>My Health</h1>
        <div className="grid">
          {GROUPS.map((g) => (
            <div className="card" key={g}>
              <h2>{g}</h2>
              <p>No synced records yet.</p>
            </div>
          ))}
        </div>
      </>
    );
  }

  // Count per marker + most recent measuredUtc.
  const markers = new Map<string, { count: number; latestIso: string; latestMs: number }>();
  for (const obs of snapshot.healthObservations.values()) {
    const ms = Date.parse(obs.measuredUtc);
    const existing = markers.get(obs.marker);
    if (!existing) {
      markers.set(obs.marker, { count: 1, latestIso: obs.measuredUtc, latestMs: Number.isNaN(ms) ? 0 : ms });
    } else {
      existing.count++;
      if (!Number.isNaN(ms) && ms >= existing.latestMs) {
        existing.latestMs = ms;
        existing.latestIso = obs.measuredUtc;
      }
    }
  }
  const markerRows = [...markers.entries()].sort((a, b) => b[1].latestMs - a[1].latestMs);

  const fasts = [...snapshot.fastingSessions.values()].sort(
    (a, b) => Date.parse(b.actualStartInstant) - Date.parse(a.actualStartInstant),
  );
  const activeFast = fasts.find((f) => f.status === "ACTIVE" && !f.deletedUtc);
  const latestFast = fasts[0];
  const schedule = [...snapshot.fastingSchedules.values()].find((s) => s.enabled);

  return (
    <>
      <h1>My Health</h1>
      <div className="grid">
        <div className="card">
          <h2>Health observations</h2>
          <p>
            {snapshot.healthObservations.size} synced records across {markers.size} markers.
          </p>
          {markerRows.length > 0 && (
            <ul>
              {markerRows.slice(0, 8).map(([marker, info]) => (
                <li key={marker}>
                  {marker}: {info.count} — latest {formatInstant(info.latestIso)}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="card">
          <h2>Fasting</h2>
          {activeFast ? (
            <p>
              Fast in progress since {formatInstant(activeFast.actualStartInstant)}
              {activeFast.targetDurationSec
                ? ` — target ${formatDurationSec(activeFast.targetDurationSec)}`
                : ""}
              .
            </p>
          ) : latestFast ? (
            <p>
              Last fast {latestFast.status.toLowerCase()}: {formatInstant(latestFast.actualStartInstant)}
              {latestFast.actualEndInstant ? ` → ${formatInstant(latestFast.actualEndInstant)}` : ""}.
            </p>
          ) : (
            <p>No fasting sessions synced yet.</p>
          )}
          {schedule ? (
            <p>
              Enabled schedule: {schedule.fastingHours ?? "?"}h fasting / {schedule.eatingHours ?? "?"}h
              eating{schedule.startLocalTime ? `, starting ${schedule.startLocalTime}` : ""}.
            </p>
          ) : (
            <p>No enabled fasting schedule.</p>
          )}
        </div>
        {GROUPS.filter((g) => g !== "Fasting").map((g) => (
          <div className="card" key={g}>
            <h2>{g}</h2>
            <p>Detailed views arrive in a later update.</p>
          </div>
        ))}
      </div>
    </>
  );
}
