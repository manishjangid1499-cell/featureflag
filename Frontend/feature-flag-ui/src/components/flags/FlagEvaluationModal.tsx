import { useState, type FormEvent } from "react";
import type { FeatureFlag, FlagEvaluationResponse } from "../../types/featureFlag";
import { evaluateFlag } from "../../api/flagApi";

interface FlagEvaluationModalProps {
  flag: FeatureFlag | null;
  isOpen: boolean;
  onClose: () => void;
}

export function FlagEvaluationModal({ flag, isOpen, onClose }: FlagEvaluationModalProps) {
  const [userId, setUserId] = useState("user_test_101");
  const [environment, setEnvironment] = useState(flag?.environment || "DEV");
  const [evaluationResult, setEvaluationResult] = useState<FlagEvaluationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if (!isOpen || !flag) return null;

  const handleEvaluate = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setEvaluationResult(null);

    if (!userId.trim()) {
      setError("User ID is required for evaluation.");
      return;
    }

    try {
      setLoading(true);
      const res = await evaluateFlag(flag.flagKey, userId.trim(), environment);
      setEvaluationResult(res);
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || "Evaluation failed. Ensure flag exists in this environment.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
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
      <div style={{
        background: "white",
        borderRadius: "14px",
        width: "100%",
        maxWidth: "560px",
        maxHeight: "90vh",
        overflowY: "auto",
        boxShadow: "0 20px 40px rgba(0,0,0,0.15)",
        border: "1px solid #e5e7eb"
      }}>
        <div style={{
          padding: "20px 24px",
          borderBottom: "1px solid #f3f4f6",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center"
        }}>
          <div>
            <h2 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#111827" }}>
              Evaluate Feature Flag: {flag.flagKey}
            </h2>
            <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
              Test runtime flag resolution for specific user context
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

        <div style={{ padding: "24px" }}>
          <form onSubmit={handleEvaluate}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px", marginBottom: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                  Environment
                </label>
                <select
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value)}
                  style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px", background: "white" }}
                >
                  <option value="DEV">DEV</option>
                  <option value="QA">QA</option>
                  <option value="STAGING">STAGING</option>
                  <option value="PROD">PROD</option>
                </select>
              </div>

              <div>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                  User ID *
                </label>
                <input
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="e.g. user_123"
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
              {loading ? "Evaluating..." : "Run Evaluation Query"}
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
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
                <span style={{ fontSize: "13px", fontWeight: 600, color: "#4b5563" }}>
                  Evaluation Verdict:
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

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", fontSize: "12px" }}>
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
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default FlagEvaluationModal;
