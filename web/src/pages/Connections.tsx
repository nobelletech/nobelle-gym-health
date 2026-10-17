import { useState } from "react";
import { disconnectDrive, requestDriveAccess } from "../google/auth";
import { AppDataDriveClient } from "../google/drive";

export default function Connections() {
  const [status, setStatus] = useState("Not connected");
  const [connected, setConnected] = useState(false);
  const [busy, setBusy] = useState(false);

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
    setStatus("Disconnected. Existing Drive data was not deleted.");
    setBusy(false);
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
          </button>
        </p>
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
