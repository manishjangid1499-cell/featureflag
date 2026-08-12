import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const role = user?.role ?? "USER";

  const canManageFlags =
    role === "OWNER" ||
    role === "ADMIN" ||
    role === "DEVELOPER";

  const navStyle = ({ isActive }: { isActive: boolean }) => ({
    display: "flex",
    alignItems: "center",
    gap: "12px",
    padding: "12px 16px",
    borderRadius: "8px",
    textDecoration: "none",
    color: isActive ? "#ffffff" : "#aeb8c7",
    background: isActive ? "#2563eb" : "transparent",
    fontWeight: isActive ? 600 : 400,
    marginBottom: "4px",
  });

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        background: "#f5f7fb",
      }}
    >
      {/* SIDEBAR */}
      <aside
        style={{
          width: "250px",
          background: "#111827",
          color: "white",
          padding: "24px 16px",
          boxSizing: "border-box",
          flexShrink: 0,
        }}
      >
        <div
          style={{
            fontSize: "20px",
            fontWeight: 700,
            padding: "0 12px 30px",
          }}
        >
          Feature Flags
        </div>

        <div
          style={{
            fontSize: "11px",
            color: "#6b7280",
            padding: "0 12px 8px",
            textTransform: "uppercase",
            letterSpacing: "0.08em",
          }}
        >
          Main
        </div>

        <nav>
          <NavLink to="/dashboard" style={navStyle}>
            <span>▦</span>
            Dashboard
          </NavLink>

          <NavLink to="/flags" style={navStyle}>
            <span>⚑</span>
            Feature Flags
          </NavLink>

          {canManageFlags && (
            <NavLink to="/flags/new" style={navStyle}>
              <span>＋</span>
              Create Flag
            </NavLink>
          )}
        </nav>

        <div
          style={{
            fontSize: "11px",
            color: "#6b7280",
            padding: "24px 12px 8px",
            textTransform: "uppercase",
            letterSpacing: "0.08em",
          }}
        >
          Platform
        </div>

        <nav>
          <NavLink to="/analytics" style={navStyle}>
            <span>◔</span>
            Analytics
          </NavLink>

          <NavLink to="/audit" style={navStyle}>
            <span>◷</span>
            Audit Logs
          </NavLink>

          <NavLink to="/notifications" style={navStyle}>
            <span>♢</span>
            Notifications
          </NavLink>
        </nav>

        <div
          style={{
            position: "absolute",
            bottom: "20px",
            width: "218px",
            borderTop: "1px solid #1f2937",
            paddingTop: "16px",
          }}
        >
          <div
            style={{
              fontSize: "13px",
              color: "#d1d5db",
              marginBottom: "4px",
              wordBreak: "break-word",
            }}
          >
            {user?.email}
          </div>

          <div
            style={{
              fontSize: "11px",
              color: "#60a5fa",
              marginBottom: "12px",
            }}
          >
            {role}
          </div>

          <button
            onClick={handleLogout}
            style={{
              width: "100%",
              padding: "9px",
              borderRadius: "6px",
              border: "1px solid #374151",
              background: "#1f2937",
              color: "#d1d5db",
              cursor: "pointer",
            }}
          >
            Logout
          </button>
        </div>
      </aside>

      {/* MAIN */}
      <main
        style={{
          flex: 1,
          minWidth: 0,
        }}
      >
        {/* TOP BAR */}
        <header
          style={{
            height: "70px",
            background: "#ffffff",
            borderBottom: "1px solid #e5e7eb",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "0 32px",
            boxSizing: "border-box",
          }}
        >
          <div>
            <div
              style={{
                fontSize: "14px",
                color: "#6b7280",
              }}
            >
              Feature Flag Management
            </div>

            <div
              style={{
                fontSize: "12px",
                color: "#9ca3af",
                marginTop: "2px",
              }}
            >
              Manage releases, rollouts and environments
            </div>
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "12px",
            }}
          >
            <div
              style={{
                width: "36px",
                height: "36px",
                borderRadius: "50%",
                background: "#2563eb",
                color: "white",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: 700,
              }}
            >
              {user?.email?.charAt(0).toUpperCase()}
            </div>

            <div>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: 600,
                }}
              >
                {user?.email}
              </div>

              <div
                style={{
                  fontSize: "11px",
                  color: "#6b7280",
                }}
              >
                {role}
              </div>
            </div>
          </div>
        </header>

        <div style={{ padding: "32px" }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default AppLayout;