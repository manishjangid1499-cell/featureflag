import type { FeatureFlag } from "../../types/featureFlag";
import { useAuth } from "../../context/AuthContext";

interface FlagTableProps {
  flags: FeatureFlag[];
  onToggle?: (id: number) => void;
  onEdit?: (flag: FeatureFlag) => void;
  onDelete?: (flag: FeatureFlag) => void;
  onEvaluate?: (flag: FeatureFlag) => void;
  togglingId?: number | null;
}

export function FlagTable({
  flags,
  onToggle,
  onEdit,
  onDelete,
  onEvaluate,
  togglingId = null,
}: FlagTableProps) {
  const { isViewer, canManageFlags, canDeleteFlags } = useAuth();

  const getEnvBadgeStyle = (env?: string | null) => {
    switch (env?.toUpperCase()) {
      case "PROD":
        return { background: "#fee2e2", color: "#991b1b", border: "1px solid #fecaca" };
      case "STAGING":
        return { background: "#fef3c7", color: "#92400e", border: "1px solid #fde68a" };
      case "QA":
        return { background: "#e0e7ff", color: "#3730a3", border: "1px solid #c7d2fe" };
      case "DEV":
      default:
        return { background: "#f3f4f6", color: "#374151", border: "1px solid #e5e7eb" };
    }
  };

  return (
    <div style={{
      background: "white",
      borderRadius: "14px",
      border: "1px solid #e5e7eb",
      boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
      overflow: "hidden"
    }}>
      <div style={{
        padding: "20px 24px",
        borderBottom: "1px solid #f3f4f6",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }}>
        <div>
          <h2 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#111827" }}>
            Feature Flags
          </h2>
          <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
            {isViewer
              ? "Read-only overview of platform feature flags and rollout criteria"
              : "Manage feature flag lifecycle, rollout percentages, and target rules"}
          </p>
        </div>
        <span style={{ fontSize: "12px", color: "#9ca3af", fontWeight: 600 }}>
          {flags.length} Flag{flags.length === 1 ? "" : "s"}
        </span>
      </div>

      {flags.length === 0 ? (
        <div style={{ padding: "60px 20px", textAlign: "center" }}>
          <div style={{
            width: "48px",
            height: "48px",
            borderRadius: "50%",
            background: "#f3f4f6",
            color: "#9ca3af",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "20px",
            margin: "0 auto 14px auto"
          }}>
            ⚑
          </div>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", fontWeight: 700, color: "#111827" }}>
            No feature flags found
          </h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>
            {isViewer
              ? "There are currently no feature flags matching your filter."
              : "Create your first feature flag to begin controlling releases."}
          </p>
        </div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb" }}>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Name & Key
                </th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Environment
                </th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Status
                </th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Rollout %
                </th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Rules
                </th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", letterSpacing: "0.05em", textAlign: "right" }}>
                  Actions
                </th>
              </tr>
            </thead>

            <tbody>
              {flags.map((flag, idx) => {
                const env = flag.environment || "DEV";
                const envStyle = getEnvBadgeStyle(env);
                const isToggling = togglingId === flag.id;
                const isEnabled = Boolean(flag.enabled);
                const rollout = flag.rolloutPercentage ?? 0;

                return (
                  <tr
                    key={flag.id || idx}
                    style={{
                      borderBottom: "1px solid #f3f4f6",
                      transition: "background 0.15s ease",
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = "#fafafa")}
                    onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
                  >
                    <td style={{ padding: "16px 20px" }}>
                      <div style={{ fontWeight: 700, fontSize: "13px", color: "#111827" }}>
                        {flag.name || "Untitled Flag"}
                      </div>
                      <div style={{
                        fontFamily: "monospace",
                        fontSize: "11px",
                        color: "#4f46e5",
                        background: "#eef2ff",
                        padding: "2px 6px",
                        borderRadius: "4px",
                        display: "inline-block",
                        marginTop: "4px"
                      }}>
                        {flag.flagKey || `FLAG_${flag.id}`}
                      </div>
                      {flag.description && (
                        <div style={{ fontSize: "11px", color: "#6b7280", marginTop: "4px", maxWidth: "260px" }}>
                          {flag.description}
                        </div>
                      )}
                    </td>

                    <td style={{ padding: "16px 20px" }}>
                      <span style={{
                        padding: "4px 9px",
                        borderRadius: "12px",
                        fontSize: "11px",
                        fontWeight: 700,
                        ...envStyle
                      }}>
                        {env}
                      </span>
                    </td>

                    <td style={{ padding: "16px 20px" }}>
                      <span style={{
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "6px",
                        padding: "4px 10px",
                        borderRadius: "20px",
                        fontSize: "11px",
                        fontWeight: 700,
                        background: isEnabled ? "#ecfdf5" : "#f3f4f6",
                        color: isEnabled ? "#059669" : "#6b7280",
                        border: isEnabled ? "1px solid #a7f3d0" : "1px solid #e5e7eb"
                      }}>
                        <span style={{
                          width: "6px",
                          height: "6px",
                          borderRadius: "50%",
                          background: isEnabled ? "#10b981" : "#9ca3af"
                        }} />
                        {isEnabled ? "Enabled" : "Disabled"}
                      </span>
                    </td>

                    <td style={{ padding: "16px 20px" }}>
                      <div style={{ width: "120px" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontWeight: 700, color: "#374151", marginBottom: "4px" }}>
                          <span>{rollout}%</span>
                        </div>
                        <div style={{ width: "100%", height: "6px", background: "#e5e7eb", borderRadius: "10px", overflow: "hidden" }}>
                          <div style={{
                            width: `${rollout}%`,
                            height: "100%",
                            background: isEnabled ? "#4f46e5" : "#9ca3af",
                            transition: "width 0.3s ease"
                          }} />
                        </div>
                      </div>
                    </td>

                    <td style={{ padding: "16px 20px" }}>
                      <div style={{ display: "flex", flexDirection: "column", gap: "4px", fontSize: "11px", color: "#6b7280" }}>
                        {flag.targetUsers && flag.targetUsers.length > 0 ? (
                          <span style={{ color: "#4338ca", fontWeight: 600 }}>
                            🎯 {flag.targetUsers.length} user{flag.targetUsers.length === 1 ? "" : "s"} whitelisted
                          </span>
                        ) : (
                          <span style={{ color: "#9ca3af" }}>All Users</span>
                        )}

                        {flag.startDate && (
                          <span style={{ fontSize: "10px" }}>
                            ⏱ From: {new Date(flag.startDate).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                    </td>

                    <td style={{ padding: "16px 20px", textAlign: "right" }}>
                      <div style={{ display: "inline-flex", gap: "8px", alignItems: "center" }}>
                        {flag.flagKey && (
                          <button
                            type="button"
                            onClick={() => onEvaluate?.(flag)}
                            title="Evaluate flag resolution"
                            style={{
                              padding: "6px 11px",
                              border: "1px solid #d1d5db",
                              background: "white",
                              color: "#374151",
                              borderRadius: "6px",
                              fontSize: "11px",
                              fontWeight: 600,
                              cursor: "pointer"
                            }}
                          >
                            Evaluate
                          </button>
                        )}

                        {canManageFlags && flag.id && (
                          <>
                            <button
                              type="button"
                              disabled={isToggling}
                              onClick={() => onToggle?.(flag.id)}
                              style={{
                                padding: "6px 11px",
                                border: isEnabled ? "1px solid #fecaca" : "1px solid #a7f3d0",
                                background: isEnabled ? "#fff1f2" : "#f0fdf4",
                                color: isEnabled ? "#dc2626" : "#16a34a",
                                borderRadius: "6px",
                                fontSize: "11px",
                                fontWeight: 700,
                                cursor: isToggling ? "not-allowed" : "pointer"
                              }}
                            >
                              {isToggling ? "..." : isEnabled ? "Disable" : "Enable"}
                            </button>

                            <button
                              type="button"
                              onClick={() => onEdit?.(flag)}
                              style={{
                                padding: "6px 11px",
                                border: "1px solid #d1d5db",
                                background: "white",
                                color: "#374151",
                                borderRadius: "6px",
                                fontSize: "11px",
                                fontWeight: 600,
                                cursor: "pointer"
                              }}
                            >
                              Edit
                            </button>
                          </>
                        )}

                        {canDeleteFlags && flag.id && (
                          <button
                            type="button"
                            onClick={() => onDelete?.(flag)}
                            style={{
                              padding: "6px 11px",
                              border: "1px solid #fee2e2",
                              background: "#fff5f5",
                              color: "#dc2626",
                              borderRadius: "6px",
                              fontSize: "11px",
                              fontWeight: 600,
                              cursor: "pointer"
                            }}
                          >
                            Delete
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default FlagTable;