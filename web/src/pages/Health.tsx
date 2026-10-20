import { useState } from "react";
import { Link } from "react-router-dom";
import { healthHistory } from "../sync/history";
import { formatInstant } from "../sync/format";
import { useSnapshot } from "../sync/store";
import HistoryChart from "./HistoryChart";

export default function Health() {
  const { snapshot } = useSnapshot();
  const [days, setDays] = useState(90);
  const observations = [...(snapshot?.healthObservations.values() ?? [])].filter((o) => !o.deletedUtc);
  const series = snapshot ? healthHistory(observations, days, snapshot.restoredUtc) : [];
  const markers = [...new Set(observations.map((o) => o.marker))].sort();
  return <>
    <h1>My Health</h1>
    <div className="card">
      <h2>Health history</h2>
      <p>Descriptive history only, not medical interpretation. Dates use your browser’s local timezone.
        Step records are added per day; other markers show individual measurements. Different units are kept separate.</p>
      <label className="window-select">Window: <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
        <option value={7}>7 days</option><option value={30}>30 days</option><option value={90}>90 days</option>
      </select></label>
      <p>{snapshot ? `Window ends at snapshot restore: ${formatInstant(snapshot.restoredUtc)}.` : "No data. Connect and restore from Drive on Connections."}</p>
      <p><Link to="/progress">Fasting and gym attendance history →</Link></p>
    </div>
    {!markers.length && <div className="card"><p>No health data.</p></div>}
    {markers.map((marker) => {
      const groups = series.filter((s) => s.marker === marker);
      return <section className="card" key={marker}>
        <h2>{marker.replaceAll("_", " ")}</h2>
        {!groups.length && <p>No data in this window.</p>}
        {groups.map((group) => <div key={group.unit}>
          <HistoryChart points={group.points} label={marker} unit={group.unit} bars={marker === "steps"} provenance />
        </div>)}
      </section>;
    })}
  </>;
}
