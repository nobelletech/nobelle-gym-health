import { useState } from "react";
import { currentAccessToken, disconnectDrive, requestDriveAccess } from "../google/auth";
import { runDriveDiagnostic } from "../google/diagnostic";
import { AppDataDriveClient } from "../google/drive";
import { formatInstant } from "../sync/format";
import { resetRestoreState, restoreFromDrive } from "../sync/restore";
import {
  beginRestore,
  clearSnapshot,
  failRestore,
  finishRestore,
  reportRestoreProgress,
  useSnapshot,
} from "../sync/store";

export default function Connections() {
  const [status, setStatus] = useState("Not connected");
  const [connected, setConnected] = useState(false);
  const [busy, setBusy] = useState(false);
  const [diagnostic, setDiagnostic] = useState<string>();
  const sync = useSnapshot();
  const restoring = sync.status === "running";

  async function connect() {
    setBusy(true);
    try {
      const token = await requestDriveAccess();
      const page = await new AppDataDriveClient(token).list(undefined, 1);
      setStatus(`Connected to your Google Drive app data folder (${page.files.length ? "Nobelle data found" : "empty"})`);
      setConnected(true);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Could not connect");
    } finally {
      setBusy(false);
    }
  }

  async function disconnect() {
    setBusy(true);
    await disconnectDrive();
    setConnected(false);
    setDiagnostic(undefined);
    clearSnapshot();
    resetRestoreState();
    setStatus("Disconnected. Existing Drive data was not deleted.");
    setBusy(false);
  }

  async function testDrive() {
    const token = currentAccessToken();
    if (!token) {
      setConnected(false);
      setDiagnostic("Authorization is no longer in memory. Connect Google Drive again.");
      return;
    }
    setBusy(true);
    setDiagnostic("Running upload, read-back, checksum and deletion…");
    try {
      const result = await runDriveDiagnostic(new AppDataDriveClient(token));
      setDiagnostic(`Passed: created ${result.bytes} non-personal bytes, read them back, matched SHA-256 ${result.sha256}, and deleted the temporary file.`);
    } catch (error) {
      setDiagnostic(error instanceof Error ? `Failed: ${error.message}` : "Drive diagnostic failed");
    } finally {
      setBusy(false);
    }
  }

  async function restore() {
    const token = currentAccessToken();
    if (!token) {
      setConnected(false);
      setStatus("Authorization is no longer in memory. Connect Google Drive again.");
      return;
    }
    beginRestore();
    try {
      const snapshot = await restoreFromDrive(new AppDataDriveClient(token), reportRestoreProgress);
      finishRestore(snapshot);
    } catch (error) {
      failRestore(error instanceof Error ? error.message : "Restore failed");
    }
  }

  const snapshot = sync.snapshot;
  const progressText = `Scanned ${sync.progress.filesScanned} files, fetched ${sync.progress.filesFetched}, applied ${sync.progress.applied} events, skipped ${sync.progress.skipped}`;

  return (
    <>
      <h1>Connections &amp; Data</h1>
      <div className="card">
        <h2>Google Drive</h2>
        <p>{status}</p>
        <p>
          Nobelle requests only the non-sensitive <code>drive.appdata</code> scope.
          Personal data stays in your hidden Google Drive application-data folder.
        </p>
        <p>
          <button disabled={busy} onClick={connected ? disconnect : connect}>
            {busy ? "Please wait…" : connected ? "Disconnect" : "Connect Google Drive"}
          </button>{" "}
          {connected && (
            <>
              <button className="secondary" disabled={busy} onClick={testDrive}>
                Test upload and deletion
              </button>{" "}
              <button className="secondary" disabled={busy || restoring} onClick={restore}>
                {restoring ? "Restoring…" : "Restore from Drive"}
              </button>
            </>
          )}
        </p>
        {diagnostic && <p role="status">{diagnostic}</p>}
      </div>
      <div className="card">
        <h2>Sync status</h2>
        {restoring && <p role="status">{progressText}</p>}
        {sync.status === "error" && <p role="alert">Restore failed: {sync.error}</p>}
        {snapshot ? (
          <p>
            Last restore {formatInstant(snapshot.restoredUtc)} — {progressText}. Restored{" "}
            {snapshot.workouts.size} workouts, {snapshot.watchWorkouts.size} watch workouts,{" "}
            {snapshot.gymVisits.size} gym visits, {snapshot.fastingSessions.size} fasting sessions,{" "}
            {snapshot.fastingSchedules.size} fasting schedules,{" "}
            {snapshot.healthObservations.size} health observations.
            {snapshot.ownerNamespace ? ` Owner: ${snapshot.ownerNamespace}.` : ""}
          </p>
        ) : (
          !restoring && sync.status !== "error" && <p>Last successful sync: —. Pending records: —.</p>
        )}
        <p>
          Snapshot is in memory only — reconnect + restore after reload.
        </p>
      </div>
      <div className="card">
        <h2>Sources</h2>
        <p>
          Samsung Health, Google Health/Fit, OneTouch, OMRON and 1byone are
          configured on the phone. This dashboard shows their status once
          synced.
        </p>
      </div>
    </>
  );
}
