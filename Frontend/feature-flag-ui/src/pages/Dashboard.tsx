import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { getAllFlags } from "../api/flagApi";
import { getAllAuditLogs } from "../api/auditApi";
import type { FeatureFlag } from "../types/featureFlag";
import type { AuditLog } from "../types/audit";
import { collectAllPages } from "../types/page";

function formatEventType(type: string): string {
  switch (type) {
    case "FLAG_CREATED":
      return "Flag created";
    case "FLAG_UPDATED":
      return "Flag updated";
    case "FLAG_TOGGLED":
      return "Flag toggled";
    case "FLAG_DELETED":
      return "Flag deleted";
    case "EVALUATION":
      return "Flag evaluated";
    default:
      return type
        ? type.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
        : "Unknown event";
  }
}

import type { AuthUser } from "../types/auth";

interface DashboardSessionProps {
  user: AuthUser | null;
  canManageMembers: boolean;
}

function DashboardSession({ user, canManageMembers }: DashboardSessionProps) {
  const displayName = user?.name?.trim() || user?.email;
  const navigate = useNavigate();

  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [flagsError, setFlagsError] = useState("");
  const [auditError, setAuditError] = useState("");
  const [reloadIndex, setReloadIndex] = useState(0);
  const requestVersion = useRef(0);

  useEffect(() => {
    let cancelled = false;
    const version = ++requestVersion.current;

    Promise.allSettled([
      collectAllPages(getAllFlags),
      getAllAuditLogs(0, 5),
    ]).then(([flagsRes, auditRes]) => {
      if (cancelled || requestVersion.current !== version) return;

      if (flagsRes.status === "fulfilled") {
        setFlags(flagsRes.value);
      } else {
        setFlagsError("Could not load flags from backend.");
      }

      if (auditRes.status === "fulfilled") {
        setAuditLogs(auditRes.value.content);
      } else {
        setAuditError("Could not load recent audit activity from backend.");
      }
    }).catch(() => {
      if (cancelled || requestVersion.current !== version) return;
      setFlagsError("Failed to communicate with API Gateway.");
      setAuditError("Failed to communicate with API Gateway.");
    }).finally(() => {
      if (!cancelled && requestVersion.current === version) {
        setLoading(false);
      }
    });

    return () => {
      cancelled = true;
      requestVersion.current += 1;
    };
  }, [reloadIndex]);

  const handleRetry = useCallback(() => {
    setLoading(true);
    setFlagsError("");
    setAuditError("");
    setReloadIndex((c) => c + 1);
  }, []);

  const error = flagsError || auditError;

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
      <div className="dashboard-hero" style={{
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
          <span className="sr-only">{user?.role} CONSOLE</span>
          <div style={{ fontSize: "12px", textTransform: "uppercase", letterSpacing: "0.08em", color: "#a5b4fc", fontWeight: 700, marginBottom: "4px" }}>
            Dashboard
          </div>
          <h1 style={{ margin: "0 0 6px 0", fontSize: "26px", fontWeight: 800 }}>
            Welcome back, {displayName}
          </h1>
          <p style={{ margin: 0, fontSize: "14px", color: "#c7d2fe", maxWidth: "600px" }}>
            Manage feature flags and review recent activity.
          </p>
        </div>

        <div className="responsive-actions" style={{ display: "flex", gap: "10px" }}>
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
            Manage flags →
          </button>
        </div>
      </div>

      {error && (
        <div className="responsive-row" style={{
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
            onClick={handleRetry}
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
            {flagsError ? "—" : loading ? "..." : totalFlags}
          </div>
          <div style={{ fontSize: "12px", color: flagsError ? "#9ca3af" : "#10b981", marginTop: "4px", fontWeight: 600 }}>
            {flagsError ? "Metrics unavailable" : `${enabledFlags} Enabled (${totalFlags > 0 ? Math.round((enabledFlags / totalFlags) * 100) : 0}%)`}
          </div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>DEV ENVIRONMENT</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#4b5563", marginTop: "4px" }}>
            {flagsError ? "—" : loading ? "..." : devCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>Development flags</div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>QA & STAGING</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#3730a3", marginTop: "4px" }}>
            {flagsError ? "—" : loading ? "..." : qaCount + stagingCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>QA: {qaCount} | Staging: {stagingCount}</div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>PRODUCTION FLAGS</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#111827", marginTop: "4px" }}>
            {flagsError ? "—" : loading ? "..." : prodCount}
          </div>
          <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>Flags configured in PROD</div>
        </div>
      </div>

      {/* SPLIT LAYOUT: RECENT ACTIVITY & QUICK ACTIONS */}
      <div className="dashboard-split" style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "24px", alignItems: "start" }}>
        {/* RECENT AUDIT ACTIVITY */}
        <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", padding: "24px" }}>
          <div className="responsive-row" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "18px" }}>
            <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#111827" }}>
              Recent activity
            </h3>
            <button
              type="button"
              onClick={() => navigate("/audit")}
              style={{ background: "none", border: "none", color: "#4f46e5", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
            >
              View audit logs →
            </button>
          </div>

          {loading ? (
            <p style={{ color: "#6b7280", fontSize: "13px" }}>Loading activity stream...</p>
          ) : auditError ? (
            <div style={{ padding: "16px", background: "#fef2f2", border: "1px solid #fee2e2", borderRadius: "8px", textAlign: "center" }}>
              <p style={{ color: "#991b1b", fontSize: "13px", margin: "0 0 10px 0", fontWeight: 600 }}>{auditError}</p>
              <button
                type="button"
                onClick={handleRetry}
                style={{
                  padding: "5px 12px",
                  background: "#fee2e2",
                  border: "1px solid #f87171",
                  color: "#991b1b",
                  borderRadius: "6px",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                }}
              >
                ↻ Retry
              </button>
            </div>
          ) : auditLogs.length === 0 ? (
            <p style={{ color: "#9ca3af", fontSize: "13px", margin: 0 }}>No recent audit activity recorded.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {auditLogs.map((log) => (
                <div
                  key={log.id}
                  className="responsive-row"
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
                      {formatEventType(log.eventType)}
                    </span>
                    <strong style={{ fontSize: "13px", color: "#111827", fontFamily: "monospace" }}>
                      {log.flagKey}
                    </strong>
                  </div>
                  <span style={{ fontSize: "11px", color: "#9ca3af" }}>
                    {log.timestamp ? new Date(log.timestamp).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "Just now"}
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

export function Dashboard() {
  const { user, canManageMembers } = useAuth();
  const sessionKey = `${user?.email || "anonymous"}-${user?.role || "none"}`;

  return (
    <DashboardSession
      key={sessionKey}
      user={user}
      canManageMembers={canManageMembers}
    />
  );
}

export default Dashboard;
