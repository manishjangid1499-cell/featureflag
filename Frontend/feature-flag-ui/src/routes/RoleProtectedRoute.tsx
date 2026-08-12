import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { UserRole } from "../types/auth";
import type { ReactNode } from "react";

interface RoleProtectedRouteProps {
  allowedRoles: UserRole[];
  children: ReactNode;
}

export function RoleProtectedRoute({ allowedRoles, children }: RoleProtectedRouteProps) {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    return (
      <div style={{
        padding: "60px 40px",
        textAlign: "center",
        maxWidth: "600px",
        margin: "60px auto",
        background: "white",
        borderRadius: "14px",
        boxShadow: "0 10px 30px rgba(0,0,0,0.06)",
        border: "1px solid #e5e7eb"
      }}>
        <div style={{
          width: "56px",
          height: "56px",
          borderRadius: "50%",
          background: "#fee2e2",
          color: "#dc2626",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "24px",
          fontWeight: "bold",
          margin: "0 auto 20px auto"
        }}>
          !
        </div>
        <h2 style={{ fontSize: "22px", color: "#111827", margin: "0 0 10px 0" }}>
          Access Denied (403)
        </h2>
        <p style={{ color: "#6b7280", fontSize: "14px", lineHeight: "1.6", marginBottom: "28px" }}>
          Your current role (<strong style={{ color: "#4f46e5" }}>{user.role}</strong>) does not have permission to view or manage this section.
        </p>
        <a
          href="/dashboard"
          style={{
            display: "inline-block",
            padding: "10px 22px",
            background: "#4f46e5",
            color: "white",
            textDecoration: "none",
            borderRadius: "8px",
            fontSize: "14px",
            fontWeight: 600
          }}
        >
          Return to Dashboard
        </a>
      </div>
    );
  }

  return <>{children}</>;
}

export default RoleProtectedRoute;
