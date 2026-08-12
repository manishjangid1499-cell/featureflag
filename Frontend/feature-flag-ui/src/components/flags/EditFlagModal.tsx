import { useState, useEffect, type FormEvent } from "react";
import type { FlagRequest, FeatureFlag } from "../../types/featureFlag";
import { updateFlag } from "../../api/flagApi";

interface EditFlagModalProps {
  flag: FeatureFlag | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updatedFlag: FeatureFlag) => void;
}

export function EditFlagModal({ flag, isOpen, onClose, onSuccess }: EditFlagModalProps) {
  const [name, setName] = useState("");
  const [flagKey, setFlagKey] = useState("");
  const [description, setDescription] = useState("");
  const [environment, setEnvironment] = useState("DEV");
  const [rolloutPercentage, setRolloutPercentage] = useState<number>(0);
  const [enabled, setEnabled] = useState(true);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [targetUsersInput, setTargetUsersInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (flag) {
      setName(flag.name || "");
      setFlagKey(flag.flagKey || "");
      setDescription(flag.description || "");
      setEnvironment(flag.environment || "DEV");
      setRolloutPercentage(flag.rolloutPercentage ?? 0);
      setEnabled(flag.enabled ?? true);
      setStartDate(flag.startDate ? flag.startDate.slice(0, 16) : "");
      setEndDate(flag.endDate ? flag.endDate.slice(0, 16) : "");
      setTargetUsersInput(flag.targetUsers ? flag.targetUsers.join(", ") : "");
      setError("");
    }
  }, [flag]);

  if (!isOpen || !flag) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (!name.trim()) {
      setError("Flag name is required.");
      return;
    }

    if (rolloutPercentage < 0 || rolloutPercentage > 100) {
      setError("Rollout percentage must be between 0 and 100.");
      return;
    }

    if (startDate && endDate && new Date(startDate) > new Date(endDate)) {
      setError("End date must be after start date.");
      return;
    }

    const targetUsers = targetUsersInput
      .split(",")
      .map((u) => u.trim())
      .filter(Boolean);

    const payload: FlagRequest = {
      name: name.trim(),
      flagKey: flagKey.trim(),
      description: description.trim(),
      environment,
      rolloutPercentage,
      enabled,
      startDate: startDate ? new Date(startDate).toISOString().slice(0, 19) : null,
      endDate: endDate ? new Date(endDate).toISOString().slice(0, 19) : null,
      targetUsers,
    };

    try {
      setLoading(true);
      const updated = await updateFlag(flag.id, payload);
      onSuccess(updated);
      onClose();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || "Failed to update feature flag.";
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
        maxWidth: "600px",
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
              Edit Feature Flag: {flag.flagKey}
            </h2>
            <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
              Update flag configuration and targeted rollout rules
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

        <form onSubmit={handleSubmit} style={{ padding: "24px" }}>
          {error && (
            <div style={{
              background: "#fef2f2",
              border: "1px solid #fecaca",
              color: "#b91c1c",
              padding: "10px 14px",
              borderRadius: "8px",
              fontSize: "13px",
              marginBottom: "18px"
            }}>
              {error}
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Flag Name *
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Flag Key (Immutable)
              </label>
              <input
                type="text"
                value={flagKey}
                disabled
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #e5e7eb", borderRadius: "7px", fontSize: "13px", background: "#f9fafb", color: "#6b7280", fontFamily: "monospace" }}
              />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
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
                Status
              </label>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "10px" }}>
                <input
                  type="checkbox"
                  id="editEnabledCheck"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                  style={{ width: "18px", height: "18px", accentColor: "#4f46e5", cursor: "pointer" }}
                />
                <label htmlFor="editEnabledCheck" style={{ fontSize: "13px", color: "#111827", cursor: "pointer" }}>
                  {enabled ? "Enabled" : "Disabled"}
                </label>
              </div>
            </div>
          </div>

          <div style={{ marginBottom: "16px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px" }}>
              <label style={{ fontSize: "12px", fontWeight: 600, color: "#374151" }}>
                Rollout Percentage: <strong>{rolloutPercentage}%</strong>
              </label>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={rolloutPercentage}
              onChange={(e) => setRolloutPercentage(Number(e.target.value))}
              style={{ width: "100%", accentColor: "#4f46e5" }}
            />
          </div>

          <div style={{ marginBottom: "16px" }}>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
              Description
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px", resize: "vertical" }}
            />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Scheduled Start (Optional)
              </label>
              <input
                type="datetime-local"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                style={{ width: "100%", padding: "8px 10px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "12px" }}
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Scheduled Expiration (Optional)
              </label>
              <input
                type="datetime-local"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                style={{ width: "100%", padding: "8px 10px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "12px" }}
              />
            </div>
          </div>

          <div style={{ marginBottom: "22px" }}>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
              Whitelisted Target User IDs (Comma-separated)
            </label>
            <input
              type="text"
              value={targetUsersInput}
              onChange={(e) => setTargetUsersInput(e.target.value)}
              placeholder="e.g. usr_101, beta_tester_2"
              style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px", paddingTop: "14px", borderTop: "1px solid #f3f4f6" }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: "9px 16px",
                border: "1px solid #d1d5db",
                background: "white",
                color: "#374151",
                borderRadius: "7px",
                fontSize: "13px",
                cursor: "pointer"
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                padding: "9px 20px",
                border: "none",
                background: "#4f46e5",
                color: "white",
                borderRadius: "7px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: loading ? "not-allowed" : "pointer"
              }}
            >
              {loading ? "Saving..." : "Save Changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EditFlagModal;
