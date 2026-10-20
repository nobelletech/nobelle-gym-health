import { NavLink, Route, Routes } from "react-router-dom";
import Today from "./pages/Today";
import Health from "./pages/Health";
import Progress from "./pages/Progress";
import Workouts from "./pages/Workouts";
import Plans from "./pages/Plans";
import Connections from "./pages/Connections";

/**
 * Web dashboard (§2): read synced history, edit plans, review reports.
 * The browser cannot read the phone's Health Connect store — data arrives
 * only through the user's authorized Google Drive sync (§24).
 */
export default function App() {
  return (
    <>
      <header>
        <span className="brand">Nobelle Gym &amp; Health</span>
        <nav>
          <NavLink to="/">Today</NavLink>
          <NavLink to="/plans">Plans</NavLink>
          <NavLink to="/health">My Health</NavLink>
          <NavLink to="/workouts">Workouts</NavLink>
          <NavLink to="/progress">Progress</NavLink>
          <NavLink to="/connections">Connections</NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Today />} />
          <Route path="/plans" element={<Plans />} />
          <Route path="/health" element={<Health />} />
          <Route path="/workouts" element={<Workouts />} />
          <Route path="/progress" element={<Progress />} />
          <Route path="/connections" element={<Connections />} />
        </Routes>
      </main>
    </>
  );
}
