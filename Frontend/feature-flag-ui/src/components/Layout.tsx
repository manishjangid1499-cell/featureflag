import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

function Layout() {
  const { user, logout, canManageMembers } = useAuth();
  const displayName = user?.name?.trim() || user?.email;
  const navigate = useNavigate();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!isMenuOpen) return;

    const mobileQuery = window.matchMedia("(max-width: 700px)");
    const menuButton = menuButtonRef.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    sidebarRef.current?.querySelector<HTMLButtonElement>("button")?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsMenuOpen(false);
      if (event.key !== "Tab") return;

      const controls = sidebarRef.current?.querySelectorAll<HTMLElement>(
        'a[href], button:not(:disabled)',
      );
      const first = controls?.[0];
      const last = controls?.[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    };
    const handleResize = () => {
      if (!mobileQuery.matches) setIsMenuOpen(false);
    };

    document.addEventListener("keydown", handleKeyDown);
    mobileQuery.addEventListener("change", handleResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleKeyDown);
      mobileQuery.removeEventListener("change", handleResize);
      if (mobileQuery.matches) menuButton?.focus();
    };
  }, [isMenuOpen]);

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
      {isMenuOpen && (
        <div className="sidebar-backdrop" onClick={() => setIsMenuOpen(false)} aria-hidden="true" />
      )}
      <aside
        id="app-sidebar"
        ref={sidebarRef}
        className={`sidebar${isMenuOpen ? " sidebar-open" : ""}`}
        role={isMenuOpen ? "dialog" : undefined}
        aria-modal={isMenuOpen || undefined}
        aria-label="Main navigation"
      >
        <button
          type="button"
          className="mobile-menu-button sidebar-close"
          aria-label="Close navigation menu"
          onClick={() => setIsMenuOpen(false)}
        >
          <span aria-hidden="true">✕</span>
        </button>
        <div className="brand">
          <div className="brand-icon">FF</div>
          <div>
            <div className="brand-title">FeatureFlag</div>
            <div className="brand-subtitle">Management Console</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section-title">PLATFORM</div>

          <NavLink to="/dashboard" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>▦</span>
            Dashboard
          </NavLink>

          <NavLink to="/flags" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>⚑</span>
            Feature Flags
          </NavLink>

          <div className="nav-section-title">OBSERVABILITY</div>

          <NavLink to="/analytics" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>◫</span>
            Analytics
          </NavLink>

          <NavLink to="/audit" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>◷</span>
            Audit Logs
          </NavLink>

          <NavLink to="/notifications" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>◉</span>
            Notifications
          </NavLink>

          {canManageMembers && (
            <>
              <div className="nav-section-title">ADMINISTRATION</div>

              <NavLink to="/members" className={linkClass} onClick={() => setIsMenuOpen(false)}>
                <span>♙</span>
                Member Management
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-bottom">
          <div className="user-card">
            <div className="avatar">
              {displayName?.charAt(0).toUpperCase()}
            </div>

            <div className="user-info">
              <strong title={user?.email}>{displayName}</strong>
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

      <main className="main-content" inert={isMenuOpen}>
        <header className="topbar">
          <div className="topbar-heading" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <button
              ref={menuButtonRef}
              type="button"
              className="mobile-menu-button"
              aria-label="Open navigation menu"
              aria-controls="app-sidebar"
              aria-expanded={isMenuOpen}
              onClick={() => setIsMenuOpen(true)}
            >
              <span aria-hidden="true">☰</span>
            </button>
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
