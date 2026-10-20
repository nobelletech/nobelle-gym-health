import type { HealthPoint, ObservationSource } from "../sync/history";

const COLORS: Record<ObservationSource, string> = {
  manual: "var(--blue)", imported: "var(--teal)", unknown: "var(--text)",
};
const SOURCES: ObservationSource[] = ["imported", "manual", "unknown"];

/** Sparse time axis: no missing dates are filled in or interpolated. */
export default function HistoryChart({ points, label, unit, bars = false, provenance = false }: {
  points: (Pick<HealthPoint, "date" | "value"> & Partial<HealthPoint>)[];
  label: string;
  unit: string;
  bars?: boolean;
  provenance?: boolean;
}) {
  if (!points.length) return <p>No data.</p>;
  const times = points.map((p) => p.ms ?? Date.parse(p.date));
  const first = Math.min(...times);
  const last = Math.max(...times);
  const low = Math.min(bars ? 0 : Infinity, ...points.map((p) => p.value));
  const high = Math.max(bars ? 0 : -Infinity, ...points.map((p) => p.value));
  const range = high - low || 1;
  const x = (ms: number) => first === last ? 330 : 70 + ((ms - first) / (last - first)) * 520;
  const y = (value: number) => 165 - ((value - low) / range) * 130;
  const width = Math.min(18, 450 / Math.max(1, (last - first) / 86_400_000 + 1));
  const fmt = (value: number) => Number(value.toFixed(2)).toLocaleString();
  return <>
    <p>{unit} · {bars ? "Bars" : "Recorded points (not interpolated)"} · missing dates are blank.</p>
    {provenance && <p className="chart-legend">
      <span style={{ color: COLORS.imported }}>● Imported</span>{" "}
      <span style={{ color: COLORS.manual }}>■ Manually entered</span>{" "}
      <span style={{ color: COLORS.unknown }}>◆ Estimated / unspecified</span>
    </p>}
    <svg className="history-chart" viewBox="0 0 660 215" role="img" aria-label={`${label}, ${unit}; ${points.length} recorded points. Values ${fmt(low)} to ${fmt(high)}. Data table below.`}>
      <line x1="65" x2="610" y1={y(0 < low ? low : 0)} y2={y(0 < low ? low : 0)} stroke="currentColor" opacity="0.3" />
      <text x="60" y="38" textAnchor="end">{fmt(high)}</text>
      <text x="60" y="169" textAnchor="end">{fmt(low)}</text>
      {points.map((point, index) => {
        const cx = x(times[index]);
        const cy = y(point.value);
        const title = `${point.date}: ${fmt(point.value)} ${unit}`;
        if (bars) {
          let cumulative = 0;
          const segments = provenance ? SOURCES.filter((source) => point[source] != null) : [];
          return <g key={index}><title>{title}</title>
            {segments.length ? segments.map((source) => {
              const value = point[source] ?? 0;
              const bottom = y(cumulative);
              cumulative += value;
              return <rect key={source} x={cx - width / 2} y={Math.min(bottom, y(cumulative))} width={width}
                height={Math.max(1, Math.abs(bottom - y(cumulative)))} fill={COLORS[source]} />;
            }) : <rect x={cx - width / 2} y={Math.min(y(0), cy)} width={width}
              height={Math.max(1, Math.abs(y(0) - cy))} fill="var(--teal)" />}
          </g>;
        }
        const source = point.source ?? "unknown";
        return <g key={index} fill={COLORS[source]}><title>{title} ({source})</title>
          {source === "manual" ? <rect x={cx - 3.5} y={cy - 3.5} width="7" height="7" /> :
            source === "unknown" ? <path d={`M${cx},${cy - 4} l4,4 l-4,4 l-4,-4 Z`} /> :
              <circle cx={cx} cy={cy} r="3.5" />}
        </g>;
      })}
      <text x="70" y="200">{points[0].date}</text>
      <text x="590" y="200" textAnchor="end">{points[points.length - 1].date}</text>
    </svg>
    <details className="chart-data"><summary>View chart data</summary>
      <div className="table-scroll"><table><thead><tr><th>Date</th><th>Value ({unit})</th>{provenance && <th>Provenance</th>}</tr></thead>
        <tbody>{points.map((p, i) => <tr key={i}><td>{p.date}</td><td>{fmt(p.value)}</td>{provenance && <td>
          {p.source ?? SOURCES.filter((s) => p[s] != null).map((s) => `${s}: ${fmt(p[s]!)}`).join(" · ")}
        </td>}</tr>)}</tbody></table></div>
    </details>
  </>;
}
