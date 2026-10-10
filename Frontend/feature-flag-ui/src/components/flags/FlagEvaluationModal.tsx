import { useState, type FormEvent } from "react";
import type { FeatureFlag, FlagEvaluationResponse } from "../../types/featureFlag";
import { evaluateFlag } from "../../api/flagApi";
import { getApiErrorMessage } from "../../api/errors";
import { getUserTimeZone, formatScheduleDisplay } from "../../utils/scheduleTime";

interface FlagEvaluationModalProps {
  flag: FeatureFlag | null;
  isOpen: boolean;
  onClose: () => void;
}

interface FlagEvaluationModalContentProps {
  flag: FeatureFlag;
  onClose: () => void;
}

function FlagEvaluationModalContent({ flag, onClose }: FlagEvaluationModalContentProps) {
  const [userId, setUserId] = useState("user_test_101");
  const environment = flag.environment || "DEV";
  const [evaluationResult, setEvaluationResult] = useState<FlagEvaluationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleEvaluate = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setEvaluationResult(null);

    if (!userId.trim()) {
      setError("User ID is required for preview.");
      return;
    }

    try {
      setLoading(true);
      const res = await evaluateFlag(flag.flagKey, userId.trim(), environment);
      setEvaluationResult(res);
    } catch (error: unknown) {
      setError(getApiErrorMessage(
        error,
        "Preview failed. Ensure flag exists in this environment.",
      ));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" style={{
      position: "fixed",
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: "rgba(17, 24, 39, 0.6)",
      backdropFilter: "blur(4px)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      zIndex: 50,
      padding: "20px"
    }}>
      <div className="modal-panel" style={{
        background: "white",
        borderRadius: "14px",
        width: "100%",
        maxWidth: "560px",
        maxHeight: "90vh",
        overflowY: "auto",
        boxShadow: "0 20px 40px rgba(0,0,0,0.15)",
        border: "1px solid #e5e7eb"
      }}>
        <div className="modal-header" style={{
          padding: "20px 24px",
          borderBottom: "1px solid #f3f4f6",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center"
        }}>
          <div>
            <h2 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#111827" }}>
              Preview Feature Flag: {flag.flagKey}
            </h2>
            <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
              Test flag resolution rules without increasing application evaluation counts
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{ background: "none", border: "none", fontSize: "20px", cursor: "pointer", color: "#9ca3af" }}
          >
            ✕
          </button>
        </div>

        <div className="modal-body" style={{ padding: "24px" }}>
          <div style={{
            marginBottom: "16px",
            padding: "10px 14px",
            background: "#eff6ff",
            border: "1px solid #bfdbfe",
            borderRadius: "8px",
            fontSize: "12px",
            color: "#1e40af",
            lineHeight: "1.4"
          }}>
            <strong>Dashboard Preview:</strong> This tool previews schedule, targeting, and rollout rules using current flag configuration. Previews do not emit telemetry events or increase application evaluation counts.
          </div>

          <form onSubmit={handleEvaluate}>
            <div className="form-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px", marginBottom: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                  Environment (Read-only)
                </label>
                <input
                  type="text"
                  value={environment}
                  readOnly
                  disabled
                  aria-readonly="true"
                  title="Preview environment is fixed to the selected flag"
                  style={{
                    width: "100%",
                    padding: "9px 12px",
                    border: "1px solid #d1d5db",
                    borderRadius: "7px",
                    fontSize: "13px",
                    background: "#f9fafb",
                    color: "#374151",
                    cursor: "not-allowed"
                  }}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                  User ID *
                </label>
                <input
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  required
                  style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              style={{
                width: "100%",
                padding: "10px",
                border: "none",
                background: "#4f46e5",
                color: "white",
                borderRadius: "7px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: loading ? "not-allowed" : "pointer"
              }}
            >
              {loading ? "Evaluating Preview..." : "Run Preview"}
            </button>
          </form>

          {error && (
            <div style={{
              marginTop: "18px",
              background: "#fef2f2",
              border: "1px solid #fecaca",
              color: "#b91c1c",
              padding: "10px 14px",
              borderRadius: "8px",
              fontSize: "13px"
            }}>
              {error}
            </div>
          )}

          {evaluationResult && (
            <div style={{
              marginTop: "20px",
              background: "#f9fafb",
              border: "1px solid #e5e7eb",
              borderRadius: "10px",
              padding: "18px"
            }}>
              <div className="responsive-row" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
                <span style={{ fontSize: "13px", fontWeight: 600, color: "#4b5563" }}>
                  Preview Verdict:
                </span>
                <span style={{
                  padding: "6px 14px",
                  borderRadius: "20px",
                  fontSize: "12px",
                  fontWeight: 700,
                  background: evaluationResult.enabled ? "#dcfce7" : "#fee2e2",
                  color: evaluationResult.enabled ? "#15803d" : "#b91c1c"
                }}>
                  {evaluationResult.enabled ? "✓ FEATURE ENABLED (TRUE)" : "✕ FEATURE DISABLED (FALSE)"}
                </span>
              </div>

              <div className="form-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", fontSize: "12px" }}>
                <div style={{ padding: "8px 12px", background: "white", borderRadius: "6px", border: "1px solid #f3f4f6" }}>
                  <span style={{ color: "#6b7280" }}>Within Schedule:</span>{" "}
                  <strong>{evaluationResult.withinSchedule ? "Yes" : "No"}</strong>
                </div>

                <div style={{ padding: "8px 12px", background: "white", borderRadius: "6px", border: "1px solid #f3f4f6" }}>
                  <span style={{ color: "#6b7280" }}>Target User Whitelist:</span>{" "}
                  <strong>{evaluationResult.targetedUser ? "Matched" : "No Match"}</strong>
                </div>

                <div style={{ padding: "8px 12px", background: "white", borderRadius: "6px", border: "1px solid #f3f4f6" }}>
                  <span style={{ color: "#6b7280" }}>Configured Rollout:</span>{" "}
                  <strong>{evaluationResult.rolloutPercentage}%</strong>
                </div>

                <div style={{ padding: "8px 12px", background: "white", borderRadius: "6px", border: "1px solid #f3f4f6" }}>
                  <span style={{ color: "#6b7280" }}>Environment:</span>{" "}
                  <strong>{evaluationResult.environment}</strong>
                </div>

                {(flag.startDate || flag.endDate) && (
                  <div style={{ padding: "8px 12px", background: "white", borderRadius: "6px", border: "1px solid #f3f4f6", gridColumn: "1 / -1" }}>
                    <span style={{ color: "#6b7280" }}>Schedule Window:</span>{" "}
                    <strong>
                      {flag.startDate ? formatScheduleDisplay(flag.startDate) : "Any"} → {flag.endDate ? formatScheduleDisplay(flag.endDate) : "Any"}{" "}
                      <span style={{ fontSize: "11px", color: "#6b7280", fontWeight: 400 }}>({getUserTimeZone()})</span>
                    </strong>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function FlagEvaluationModal({ flag, isOpen, onClose }: FlagEvaluationModalProps) {
  if (!isOpen || !flag) return null;

  return (
    <FlagEvaluationModalContent
      key={`${flag.id}-${flag.version}`}
      flag={flag}
      onClose={onClose}
    />
  );
}

export default FlagEvaluationModal;
