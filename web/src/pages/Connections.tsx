export default function Connections() {
  return (
    <>
      <h1>Connections &amp; Data</h1>
      <div className="card">
        <h2>Google account</h2>
        <p>Not connected. Stored in your Google Drive — app data folder only.</p>
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
