import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import AcceptInvitation from "./pages/AcceptInvitation";
import Dashboard from "./pages/Dashboard";
import Flags from "./pages/Flags";
import Analytics from "./pages/Analytics";
import AuditLogs from "./pages/AuditLogs";
import Notifications from "./pages/Notifications";
import Members from "./pages/Members";

import ProtectedRoute from "./routes/ProtectedRoute";
import RoleProtectedRoute from "./routes/RoleProtectedRoute";
import Layout from "./components/Layout";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* PUBLIC ROUTES */}
        <Route path="/login" element={<Login />} />
        <Route path="/accept-invitation" element={<AcceptInvitation />} />

        {/* AUTHENTICATED ROUTES */}
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/flags" element={<Flags />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/audit" element={<AuditLogs />} />
            <Route path="/notifications" element={<Notifications />} />

            {/* RESTRICTED ROUTES (OWNER / ADMIN ONLY) */}
            <Route
              path="/members"
              element={
                <RoleProtectedRoute allowedRoles={["OWNER", "ADMIN"]}>
                  <Members />
                </RoleProtectedRoute>
              }
            />
          </Route>
        </Route>

        {/* FALLBACK REDIRECT */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;