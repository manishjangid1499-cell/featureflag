import { useState, type FormEvent } from "react";
import type { FlagRequest, FeatureFlag } from "../../types/featureFlag";
import { createFlag } from "../../api/flagApi";

interface CreateFlagModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (newFlag: FeatureFlag) => void;
}

export function CreateFlagModal({ isOpen, onClose, onSuccess }: CreateFlagModalProps) {
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

  if (!isOpen) return null;

  const handleNameChange = (val: string) => {
    setName(val);
    if (!flagKey || flagKey === name.toUpperCase().replace(/[^A-Z0-9]/g, "_")) {
      setFlagKey(val.toUpperCase().replace(/[^A-Z0-9]/g, "_"));
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (!name.trim()) {
      setError("Flag name is required.");
      return;
    }

    if (!flagKey.trim()) {
      setError("Flag key is required.");
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
      const created = await createFlag(payload);
      onSuccess(created);
      onClose();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || "Failed to create feature flag.";
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
              Create Feature Flag
            </h2>
            <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
              Configure flag release criteria and targeting rules
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
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="e.g. Modern Header"
                required
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Flag Key *
              </label>
              <input
                type="text"
                value={flagKey}
                onChange={(e) => setFlagKey(e.target.value)}
                placeholder="e.g. MODERN_HEADER"
                required
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px", fontFamily: "monospace" }}
              />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Environment *
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
                Initial Status
              </label>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "10px" }}>
                <input
                  type="checkbox"
                  id="enabledCheck"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                  style={{ width: "18px", height: "18px", accentColor: "#4f46e5", cursor: "pointer" }}
                />
                <label htmlFor="enabledCheck" style={{ fontSize: "13px", color: "#111827", cursor: "pointer" }}>
                  {enabled ? "Enabled (Active)" : "Disabled (Inactive)"}
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
              placeholder="Explain the feature purpose and rollout goals..."
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
              placeholder="e.g. usr_101, beta_tester_2, admin@corp.io"
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
              {loading ? "Creating..." : "Create Flag"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CreateFlagModal;