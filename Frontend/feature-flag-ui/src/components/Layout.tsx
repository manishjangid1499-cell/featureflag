import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function Layout() {
  const { user, logout, canManageMembers } = useAuth();
  const navigate = useNavigate();

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? "active" : ""}`;

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const getRoleBadgeColor = () => {
    switch (user?.role) {
      case "OWNER":
        return "#7c3aed";
      case "ADMIN":
        return "#2563eb";
      case "DEVELOPER":
        return "#059669";
      case "VIEWER":
        return "#4b5563";
      default:
        return "#4f46e5";
    }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">FF</div>
          <div>
            <div className="brand-title">FeatureFlag</div>
            <div className="brand-subtitle">Enterprise Console</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section-title">PLATFORM</div>

          <NavLink to="/dashboard" className={linkClass}>
            <span>▦</span>
            Dashboard
          </NavLink>

          <NavLink to="/flags" className={linkClass}>
            <span>⚑</span>
            Feature Flags
          </NavLink>

          <div className="nav-section-title">OBSERVABILITY</div>

          <NavLink to="/analytics" className={linkClass}>
            <span>◫</span>
            Analytics
          </NavLink>

          <NavLink to="/audit" className={linkClass}>
            <span>◷</span>
            Audit Logs
          </NavLink>

          <NavLink to="/notifications" className={linkClass}>
            <span>◉</span>
            Notifications
          </NavLink>

          {canManageMembers && (
            <>
              <div className="nav-section-title">ADMINISTRATION</div>

              <NavLink to="/members" className={linkClass}>
                <span>♙</span>
                Member Management
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-bottom">
          <div className="user-card">
            <div className="avatar">
              {user?.email?.charAt(0).toUpperCase()}
            </div>

            <div className="user-info">
              <strong>{user?.email}</strong>
              <span style={{
                color: getRoleBadgeColor(),
                fontWeight: 700,
                fontSize: "11px",
                letterSpacing: "0.05em"
              }}>
                {user?.role}
              </span>
            </div>
          </div>

          <button
            type="button"
            className="logout-button"
            onClick={handleLogout}
          >
            Sign Out
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span className="topbar-label">FeatureFlag Management Platform</span>
          </div>

          <div className="topbar-user" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span style={{
              background: "#e0e7ff",
              color: "#3730a3",
              padding: "4px 10px",
              borderRadius: "16px",
              fontSize: "11px",
              fontWeight: 700
            }}>
              {user?.role}
            </span>
          </div>
        </header>

        <section className="page-content">
          <Outlet />
        </section>
      </main>
    </div>
  );
}

export default Layout;