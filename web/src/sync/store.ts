import { useSyncExternalStore } from "react";
import type { DashboardSnapshot, RestoreProgress } from "./restore";

/**
 * In-memory snapshot + restore-status store.
 *
 * Personal data deliberately lives in module state only — never
 * localStorage, sessionStorage or cookies — matching the memory-only GIS
 * token model (§24). A page reload drops everything: reconnect Drive and
 * restore again.
 */

export type RestoreStatus = "idle" | "running" | "done" | "error";

export interface SnapshotState {
  snapshot: DashboardSnapshot | null;
  status: RestoreStatus;
  progress: RestoreProgress;
  error?: string;
}

const EMPTY_PROGRESS: RestoreProgress = { filesScanned: 0, filesFetched: 0, applied: 0, skipped: 0 };

let state: SnapshotState = { snapshot: null, status: "idle", progress: EMPTY_PROGRESS };
const listeners = new Set<() => void>();

function emit(next: Partial<SnapshotState>): void {
  state = { ...state, ...next };
  for (const listener of listeners) listener();
}

export function getSnapshot(): DashboardSnapshot | null {
  return state.snapshot;
}

export function getState(): SnapshotState {
  return state;
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useSnapshot(): SnapshotState {
  return useSyncExternalStore(subscribe, getState);
}

export function setSnapshot(snapshot: DashboardSnapshot): void {
  emit({ snapshot });
}

export function beginRestore(): void {
  emit({ status: "running", progress: EMPTY_PROGRESS, error: undefined });
}

export function reportRestoreProgress(progress: RestoreProgress): void {
  emit({ progress });
}

export function finishRestore(snapshot: DashboardSnapshot): void {
  emit({ snapshot, status: "done" });
}

export function failRestore(message: string): void {
  emit({ status: "error", error: message });
}

export function clearSnapshot(): void {
  emit({ snapshot: null, status: "idle", progress: EMPTY_PROGRESS, error: undefined });
}
