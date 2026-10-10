import { Component, useEffect, useRef, useState, type ErrorInfo, type ReactNode } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { ChangePasswordModal } from "./ChangePasswordModal";

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught an unhandled error:", error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div
          style={{
            maxWidth: "700px",
            margin: "40px auto",
            padding: "32px",
            background: "white",
            borderRadius: "14px",
            border: "1px solid #fee2e2",
            boxShadow: "0 4px 12px rgba(239, 68, 68, 0.08)",
            textAlign: "center",
          }}
        >
          <div
            style={{
              width: "52px",
              height: "52px",
              borderRadius: "50%",
              background: "#fee2e2",
              color: "#dc2626",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "24px",
              fontWeight: 700,
              margin: "0 auto 16px auto",
            }}
          >
            !
          </div>
          <h2 style={{ margin: "0 0 8px 0", fontSize: "20px", fontWeight: 800, color: "#111827" }}>
            Something went wrong
          </h2>
          <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "#6b7280" }}>
            An unexpected error occurred while rendering this view.
          </p>
          {this.state.error?.message && (
            <div
              style={{
                background: "#fef2f2",
                border: "1px solid #fecaca",
                borderRadius: "8px",
                padding: "12px 16px",
                color: "#991b1b",
                fontSize: "13px",
                fontFamily: "monospace",
                marginBottom: "20px",
                textAlign: "left",
                wordBreak: "break-word",
              }}
            >
              {this.state.error.message}
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "center", gap: "12px" }}>
            <button
              type="button"
              onClick={this.handleRetry}
              style={{
                padding: "9px 18px",
                background: "#4f46e5",
                color: "white",
                border: "none",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Try Again
            </button>
            <button
              type="button"
              onClick={this.handleReload}
              style={{
                padding: "9px 18px",
                background: "white",
                color: "#374151",
                border: "1px solid #d1d5db",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Reload Page
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

function Layout() {
  const { user, logout, canManageMembers } = useAuth();
  const displayName = user?.name?.trim() || user?.email;
  const navigate = useNavigate();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isChangePasswordOpen, setIsChangePasswordOpen] = useState(false);
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  const accountMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleOutsideClick = (event: MouseEvent) => {
      if (
        accountMenuRef.current &&
        !accountMenuRef.current.contains(event.target as Node)
      ) {
        setIsAccountMenuOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsAccountMenuOpen(false);
      }
    };

    if (isAccountMenuOpen) {
      document.addEventListener("mousedown", handleOutsideClick);
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isAccountMenuOpen]);

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
    setIsAccountMenuOpen(false);
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

  const getRoleBadgeBg = () => {
    switch (user?.role) {
      case "OWNER":
        return "#f3e8ff";
      case "ADMIN":
        return "#dbeafe";
      case "DEVELOPER":
        return "#d1fae5";
      case "VIEWER":
        return "#f3f4f6";
      default:
        return "#e0e7ff";
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
            Feature flags
          </NavLink>

          <div className="nav-section-title">OBSERVABILITY</div>

          <NavLink to="/analytics" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>◫</span>
            Analytics
          </NavLink>

          <NavLink to="/audit" className={linkClass} onClick={() => setIsMenuOpen(false)}>
            <span>◷</span>
            Audit logs
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
                Members
              </NavLink>

              <NavLink to="/sdk-keys" className={linkClass} onClick={() => setIsMenuOpen(false)}>
                <span>⚿</span>
                {/* SDK Keys */}
                SDK keys
              </NavLink>
            </>
          )}
        </nav>
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

          <div className="topbar-user" ref={accountMenuRef}>
            <button
              type="button"
              className="account-menu-trigger"
              onClick={() => setIsAccountMenuOpen(!isAccountMenuOpen)}
              aria-expanded={isAccountMenuOpen}
              aria-haspopup="true"
              aria-label="Account menu"
            >
              <div className="avatar" style={{ width: "26px", height: "26px", fontSize: "11px" }}>
                {displayName?.charAt(0).toUpperCase()}
              </div>
              <span
                style={{
                  fontWeight: 500,
                  maxWidth: "150px",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                <strong title={user?.email}>{displayName}</strong>
              </span>
              <span
                style={{
                  background: getRoleBadgeBg(),
                  color: getRoleBadgeColor(),
                  padding: "2px 8px",
                  borderRadius: "12px",
                  fontSize: "11px",
                  fontWeight: 700,
                  marginLeft: "2px",
                }}
              >
                {user?.role}
              </span>
              <span style={{ fontSize: "10px", color: "#64748b", marginLeft: "2px" }} aria-hidden="true">
                {isAccountMenuOpen ? "▲" : "▼"}
              </span>
            </button>

            {isAccountMenuOpen && (
              <div className="account-menu-dropdown" role="menu">
                <div className="account-menu-header">
                  <strong title={user?.email} style={{ fontWeight: 600, fontSize: "13px", color: "#0f172a", marginBottom: "2px", display: "block" }}>
                    {displayName}
                  </strong>
                  <div
                    style={{
                      fontSize: "12px",
                      color: "#64748b",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                    title={user?.email}
                  >
                    {user?.email}
                  </div>
                  <div style={{ marginTop: "6px" }}>
                    <span
                      style={{
                        background: getRoleBadgeBg(),
                        color: getRoleBadgeColor(),
                        padding: "2px 8px",
                        borderRadius: "12px",
                        fontSize: "10px",
                        fontWeight: 700,
                        letterSpacing: "0.04em",
                      }}
                    >
                      {user?.role}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  className="account-menu-item change-password-button"
                  role="menuitem"
                  onClick={() => {
                    setIsAccountMenuOpen(false);
                    setIsChangePasswordOpen(true);
                  }}
                >
                  <span aria-hidden="true">🔒</span>
                  Change Password
                </button>

                <button
                  type="button"
                  className="account-menu-item logout logout-button"
                  role="menuitem"
                  onClick={handleLogout}
                >
                  <span aria-hidden="true">🚪</span>
                  Sign Out
                </button>
              </div>
            )}
          </div>
        </header>

        <section className="page-content">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </section>
      </main>

      <ChangePasswordModal
        isOpen={isChangePasswordOpen}
        onClose={() => setIsChangePasswordOpen(false)}
      />
    </div>
  );
}

export default Layout;
