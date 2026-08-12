import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getAllFlags } from "../api/flagApi";
import { getAllAuditLogs } from "../api/auditApi";
import type { FeatureFlag } from "../types/featureFlag";
import type { AuditLog } from "../types/audit";

export function Dashboard() {
  const { user, isViewer, canManageMembers } = useAuth();
  const navigate = useNavigate();

  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      setError("");
      const [flagsRes, auditRes] = await Promise.allSettled([
        getAllFlags(),
        getAllAuditLogs(),
      ]);

      if (flagsRes.status === "fulfilled" && Array.isArray(flagsRes.value)) {
        setFlags(flagsRes.value);
      } else if (flagsRes.status === "rejected") {
        console.error("Flags load error:", flagsRes.reason);
        setError("Could not load flags from backend.");
      }

      if (auditRes.status === "fulfilled" && Array.isArray(auditRes.value)) {
        setAuditLogs(auditRes.value.slice(0, 5));
      } else if (auditRes.status === "rejected") {
        console.error("Audit load error:", auditRes.reason);
      }
    } catch (e) {
      console.error("Dashboard error:", e);
      setError("Failed to communicate with API Gateway.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const totalFlags = flags.length;
  const enabledFlags = flags.filter((f) => f.enabled).length;

  const devCount = flags.filter(
    (f) => !f.environment || f.environment.toUpperCase().startsWith("DEV")
  ).length;
  const qaCount = flags.filter(
    (f) => f.environment?.toUpperCase() === "QA"
  ).length;
  const stagingCount = flags.filter(
    (f) => f.environment?.toUpperCase() === "STAGING"
  ).length;
  const prodCount = flags.filter(
    (f) => f.environment?.toUpperCase() === "PROD"
  ).length;

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      {/* GREETING HERO */}
      <div style={{
        background: "linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%)",
        borderRadius: "16px",
        padding: "32px",
        color: "white",
        marginBottom: "28px",
        boxShadow: "0 10px 25px rgba(49, 46, 129, 0.2)",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "20px"
      }}>
        <div>
          <span style={{
            background: "rgba(255, 255, 255, 0.15)",
            padding: "4px 12px",
            borderRadius: "20px",
            fontSize: "11px",
            fontWeight: 700,
            letterSpacing: "0.08em",
            textTransform: "uppercase"
          }}>
            {user?.role} CONSOLE
          </span>
          <h1 style={{ margin: "10px 0 6px 0", fontSize: "26px", fontWeight: 800 }}>
            Welcome back, {user?.email}
          </h1>
          <p style={{ margin: 0, fontSize: "14px", color: "#c7d2fe", maxWidth: "600px" }}>
            {isViewer
              ? "You have read-only access to inspect feature flag rollouts, audit events, and metrics."
              : "Control platform feature releases, target environments, and inspect runtime evaluation metrics."}
          </p>
        </div>

        <div style={{ display: "flex", gap: "10px" }}>
          <button
            type="button"
            onClick={fetchDashboardData}
            style={{
              padding: "10px 16px",
              background: "rgba(255, 255, 255, 0.1)",
              color: "white",
              border: "1px solid rgba(255, 255, 255, 0.2)",
              borderRadius: "8px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            ↻ Refresh
          </button>
          <button
            type="button"
            onClick={() => navigate("/flags")}
            style={{
              padding: "10px 20px",
              background: "white",
              color: "#312e81",
              border: "none",
              borderRadius: "8px",
              fontSize: "13px",
              fontWeight: 700,
              cursor: "pointer"
            }}
          >
            Manage Flags →
          </button>
        </div>
      </div>

      {error && (
        <div style={{
          background: "#fff1f2",
          border: "1px solid #fecdd3",
          borderRadius: "10px",
          padding: "14px 18px",
          color: "#9f1239",
          marginBottom: "20px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center"
        }}>
          <div>
            <strong>Service Warning:</strong> {error}
          </div>
          <button
            type="button"
            onClick={fetchDashboardData}
            style={{
              padding: "6px 12px",
              background: "#be123c",
              color: "white",
              border: "none",
              borderRadius: "6px",
              fontSize: "12px",
              cursor: "pointer"
            }}
          >
            Retry
          </button>
        </div>
      )}

      {/* METRICS GRID */}
      <div style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        gap: "18px",
        marginBottom: "28px"
      }}>
        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>TOTAL FLAGS</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#111827", marginTop: "4px" }}>
            {loading ? "..." : totalFlags}
          </div>
          <div style={{ fontSize: "12px", color: "#10b981", marginTop: "4px", fontWeight: 600 }}>
            {enabledFlags} Active ({totalFlags > 0 ? Math.round((enabledFlags / totalFlags) * 100) : 0}%)
          </div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>DEV ENVIRONMENT</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#4b5563", marginTop: "4px" }}>
            {loading ? "..." : devCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>Development flags</div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>QA & STAGING</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#3730a3", marginTop: "4px" }}>
            {loading ? "..." : qaCount + stagingCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>QA: {qaCount} | Staging: {stagingCount}</div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#dc2626", fontWeight: 600 }}>PRODUCTION FLAGS</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#dc2626", marginTop: "4px" }}>
            {loading ? "..." : prodCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>Live in production</div>
        </div>
      </div>

      {/* SPLIT LAYOUT: RECENT ACTIVITY & QUICK ACTIONS */}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "24px", alignItems: "start" }}>
        {/* RECENT AUDIT ACTIVITY */}
        <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", padding: "24px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "18px" }}>
            <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#111827" }}>
              Recent Flag Activity
            </h3>
            <button
              type="button"
              onClick={() => navigate("/audit")}
              style={{ background: "none", border: "none", color: "#4f46e5", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
            >
              View All Logs →
            </button>
          </div>

          {loading ? (
            <p style={{ color: "#6b7280", fontSize: "13px" }}>Loading activity stream...</p>
          ) : auditLogs.length === 0 ? (
            <p style={{ color: "#9ca3af", fontSize: "13px", margin: 0 }}>No recent audit activity recorded.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {auditLogs.map((log) => (
                <div
                  key={log.id}
                  style={{
                    padding: "12px 14px",
                    background: "#f9fafb",
                    borderRadius: "8px",
                    border: "1px solid #f3f4f6",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center"
                  }}
                >
                  <div>
                    <span style={{
                      fontWeight: 700,
                      fontSize: "11px",
                      padding: "2px 8px",
                      borderRadius: "4px",
                      background: log.eventType?.includes("DELETE") ? "#fee2e2" : "#e0e7ff",
                      color: log.eventType?.includes("DELETE") ? "#b91c1c" : "#3730a3",
                      marginRight: "8px"
                    }}>
                      {log.eventType}
                    </span>
                    <strong style={{ fontSize: "13px", color: "#111827", fontFamily: "monospace" }}>
                      {log.flagKey}
                    </strong>
                  </div>
                  <span style={{ fontSize: "11px", color: "#9ca3af" }}>
                    {log.timestamp ? new Date(log.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "Just now"}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* QUICK SHORTCUTS */}
        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", padding: "22px" }}>
            <h4 style={{ margin: "0 0 12px 0", fontSize: "14px", fontWeight: 700, color: "#111827" }}>
              Quick Navigation
            </h4>
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <button
                type="button"
                onClick={() => navigate("/flags")}
                style={{
                  textAlign: "left",
                  padding: "10px 14px",
                  border: "1px solid #e5e7eb",
                  background: "#f9fafb",
                  borderRadius: "8px",
                  fontSize: "13px",
                  fontWeight: 600,
                  color: "#374151",
                  cursor: "pointer"
                }}
              >
                ⚑ Feature Flags Table
              </button>

              <button
                type="button"
                onClick={() => navigate("/analytics")}
                style={{
                  textAlign: "left",
                  padding: "10px 14px",
                  border: "1px solid #e5e7eb",
                  background: "#f9fafb",
                  borderRadius: "8px",
                  fontSize: "13px",
                  fontWeight: 600,
                  color: "#374151",
                  cursor: "pointer"
                }}
              >
                ◫ Usage Analytics
              </button>

              <button
                type="button"
                onClick={() => navigate("/notifications")}
                style={{
                  textAlign: "left",
                  padding: "10px 14px",
                  border: "1px solid #e5e7eb",
                  background: "#f9fafb",
                  borderRadius: "8px",
                  fontSize: "13px",
                  fontWeight: 600,
                  color: "#374151",
                  cursor: "pointer"
                }}
              >
                ◉ Notification Logs
              </button>

              {canManageMembers && (
                <button
                  type="button"
                  onClick={() => navigate("/members")}
                  style={{
                    textAlign: "left",
                    padding: "10px 14px",
                    border: "1px solid #e5e7eb",
                    background: "#f9fafb",
                    borderRadius: "8px",
                    fontSize: "13px",
                    fontWeight: 600,
                    color: "#374151",
                    cursor: "pointer"
                  }}
                >
                  ♙ Member Administration
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Dashboard;