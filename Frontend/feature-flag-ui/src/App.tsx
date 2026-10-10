import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import AcceptInvitation from "./pages/AcceptInvitation";
import ResetPassword from "./pages/ResetPassword";
import Dashboard from "./pages/Dashboard";
import Flags from "./pages/Flags";
import Analytics from "./pages/Analytics";
import AuditLogs from "./pages/AuditLogs";
import Notifications from "./pages/Notifications";
import Members from "./pages/Members";
import SdkKeys from "./pages/SdkKeys";

import ProtectedRoute from "./routes/ProtectedRoute";
import RoleProtectedRoute from "./routes/RoleProtectedRoute";
import Layout from "./components/Layout";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/accept-invitation" element={<AcceptInvitation />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/flags" element={<Flags />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/audit" element={<AuditLogs />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route
              path="/members"
              element={
                <RoleProtectedRoute allowedRoles={["OWNER", "ADMIN"]}>
                  <Members />
                </RoleProtectedRoute>
              }
            />
            <Route
              path="/sdk-keys"
              element={
                <RoleProtectedRoute allowedRoles={["OWNER", "ADMIN"]}>
                  <SdkKeys />
                </RoleProtectedRoute>
              }
            />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;