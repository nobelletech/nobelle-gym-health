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
