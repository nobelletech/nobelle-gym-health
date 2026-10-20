/** Display helpers for restored sync data (locale-formatted, no timezone math). */

export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString();
}

export function formatDurationSec(sec: number | null | undefined): string {
  if (sec == null || !Number.isFinite(sec) || sec < 0) return "—";
  const hours = Math.floor(sec / 3600);
  const minutes = Math.round((sec % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${Math.round(sec)}s`;
}

export function formatMeters(meters: number | null | undefined): string {
  if (meters == null || !Number.isFinite(meters) || meters < 0) return "—";
  if (meters >= 1000) return `${(meters / 1000).toFixed(2)} km`;
  return `${Math.round(meters)} m`;
}
