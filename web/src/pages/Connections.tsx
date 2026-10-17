import { useState } from "react";
import { currentAccessToken, disconnectDrive, requestDriveAccess } from "../google/auth";
import { runDriveDiagnostic } from "../google/diagnostic";
import { AppDataDriveClient } from "../google/drive";

export default function Connections() {
  const [status, setStatus] = useState("Not connected");
  const [connected, setConnected] = useState(false);
  const [busy, setBusy] = useState(false);
  const [diagnostic, setDiagnostic] = useState<string>();

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
            <button className="secondary" disabled={busy} onClick={testDrive}>
              Test upload and deletion
            </button>
          )}
        </p>
        {diagnostic && <p role="status">{diagnostic}</p>}
      </div>
      <div className="card">
        <h2>Sync status</h2>
        <p>Last successful sync: —. Pending records: —.</p>
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
