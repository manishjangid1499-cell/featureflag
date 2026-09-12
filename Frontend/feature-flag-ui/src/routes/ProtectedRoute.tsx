import {
  Navigate,
  Outlet,
} from "react-router-dom";

import { useAuth } from "../hooks/useAuth";
import { resolveProtectedRoute } from "../auth/authPolicy";

function ProtectedRoute() {

  const { isAuthenticated, isAuthResolved } =
    useAuth();

  const decision = resolveProtectedRoute(
    isAuthResolved,
    isAuthenticated,
  );

  if (decision === "pending") {
    return null;
  }

  if (decision === "login") {

    return (
      <Navigate
        to="/login"
        replace
      />
    );
  }

  return <Outlet />;
}

export default ProtectedRoute;
