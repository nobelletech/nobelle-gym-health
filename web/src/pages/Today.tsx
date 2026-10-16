export default function Today() {
  return (
    <>
      <h1>Today</h1>
      <div className="card">
        <h2>Sign in with Google</h2>
        <p>
          Your history syncs only to your own Google Drive (app data folder).
          Nobelle runs no personal-data backend. Sign-in and Drive access are
          separate grants you can revoke anytime.
        </p>
        <p>
          <button disabled>Connect Google account</button>
        </p>
      </div>
      <div className="card">
        <h2>Recent activity</h2>
        <p>Synced sessions appear here once your phone uploads to Drive.</p>
      </div>
    </>
  );
}
