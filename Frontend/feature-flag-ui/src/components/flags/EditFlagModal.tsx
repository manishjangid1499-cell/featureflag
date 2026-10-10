import { useState, type FormEvent } from "react";
import type { FlagUpdateRequest, FeatureFlag } from "../../types/featureFlag";
import { isAxiosError } from "axios";
import { updateFlag } from "../../api/flagApi";
import { getApiErrorMessage } from "../../api/errors";
import { useAuth } from "../../hooks/useAuth";
import {
  getUserTimeZone,
  utcIsoToLocalInputValue,
  resolveSchedulePayloadValue,
  validateScheduleInputs,
} from "../../utils/scheduleTime";

interface EditFlagModalProps {
  flag: FeatureFlag | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updatedFlag: FeatureFlag) => void;
}

interface EditFlagModalContentProps {
  flag: FeatureFlag;
  onClose: () => void;
  onSuccess: (updatedFlag: FeatureFlag) => void;
}

function EditFlagModalContent({ flag, onClose, onSuccess }: EditFlagModalContentProps) {
  const { isDeveloper } = useAuth();
  const [name, setName] = useState(() => flag.name || "");
  const [description, setDescription] = useState(() => flag.description || "");
  const [rolloutPercentage, setRolloutPercentage] = useState<number>(() => flag.rolloutPercentage ?? 0);
  const [enabled, setEnabled] = useState(() => flag.enabled ?? true);
  const [startDate, setStartDate] = useState(() => utcIsoToLocalInputValue(flag.startDate));
  const [endDate, setEndDate] = useState(() => utcIsoToLocalInputValue(flag.endDate));
  const [targetUsersInput, setTargetUsersInput] = useState(() => flag.targetUsers ? flag.targetUsers.join(", ") : "");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const isProdFlag = (flag.environment || "").toUpperCase() === "PROD";
  const isBlockedByRole = isDeveloper && isProdFlag;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (isBlockedByRole) {
      setError("Developers cannot modify production flags. Production changes require Owner or Admin.");
      return;
    }
    setError("");

    if (!name.trim()) {
      setError("Flag name is required.");
      return;
    }

    if (rolloutPercentage < 0 || rolloutPercentage > 100) {
      setError("Rollout percentage must be between 0 and 100.");
      return;
    }

    const scheduleError = validateScheduleInputs(startDate, endDate, flag.startDate, flag.endDate);
    if (scheduleError) {
      setError(scheduleError);
      return;
    }

    const targetUsers = targetUsersInput
      .split(",")
      .map((u) => u.trim())
      .filter(Boolean);

    const payload: FlagUpdateRequest = {
      expectedVersion: flag.version,
      name: name.trim(),
      flagKey: flag.flagKey,
      description: description.trim(),
      environment: flag.environment,
      rolloutPercentage,
      enabled,
      startDate: resolveSchedulePayloadValue(startDate, flag.startDate),
      endDate: resolveSchedulePayloadValue(endDate, flag.endDate),
      targetUsers,
    };

    try {
      setLoading(true);
      const updated = await updateFlag(flag.id, payload);
      onSuccess(updated);
      onClose();
    } catch (error: unknown) {
      setError(isAxiosError(error) && error.response?.status === 409
        ? "This flag changed or its key is already in use. Refresh the page, reopen the flag, and review your changes before saving again."
        : getApiErrorMessage(error, "Failed to update feature flag."));
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
        maxWidth: "600px",
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

        <form className="modal-body" onSubmit={handleSubmit} style={{ padding: "24px" }}>
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

          {isBlockedByRole && (
            <div style={{
              background: "#fffbeb",
              border: "1px solid #fde68a",
              color: "#92400e",
              padding: "10px 14px",
              borderRadius: "8px",
              fontSize: "12px",
              marginBottom: "18px",
              lineHeight: "1.45"
            }}>
              <strong>Production Restricted:</strong> Developers can view but cannot edit flags in PROD. Modifying production flags requires an Owner or Admin account.
            </div>
          )}

          <div style={{
            background: "#f8fafc",
            border: "1px solid #e2e8f0",
            color: "#334155",
            padding: "10px 14px",
            borderRadius: "8px",
            fontSize: "12px",
            marginBottom: "18px",
            lineHeight: "1.45"
          }}>
            <strong>Flag Identity:</strong> Flag key and environment identify the flag evaluated by application SDKs and cannot be changed after creation. If you need a different name, update the display name below rather than deleting and recreating an in-use flag.
          </div>

          <div className="form-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
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
              <span style={{ fontSize: "11px", color: "#6b7280" }}>
                Change display name to relabel this flag.
              </span>
            </div>

            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Flag Key (Immutable)
              </label>
              <input
                type="text"
                value={flag.flagKey}
                readOnly
                disabled
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #e5e7eb", borderRadius: "7px", fontSize: "13px", background: "#f9fafb", color: "#6b7280", fontFamily: "monospace" }}
              />
              <span style={{ fontSize: "11px", color: "#6b7280" }}>
                Application identifier for evaluations.
              </span>
            </div>
          </div>

          <div className="form-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Environment (Immutable)
              </label>
              <input
                type="text"
                value={flag.environment}
                readOnly
                disabled
                style={{ width: "100%", padding: "9px 12px", border: "1px solid #e5e7eb", borderRadius: "7px", fontSize: "13px", background: "#f9fafb", color: "#6b7280" }}
              />
              <span style={{ fontSize: "11px", color: "#6b7280" }}>
                Target runtime environment.
              </span>
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

          <div className="form-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Scheduled Start (Optional) <span style={{ fontSize: "11px", color: "#6b7280", fontWeight: 400 }}>({getUserTimeZone()})</span>
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
                Scheduled Expiration (Optional) <span style={{ fontSize: "11px", color: "#6b7280", fontWeight: 400 }}>({getUserTimeZone()})</span>
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
              style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
            />
          </div>

          <div className="responsive-actions" style={{ display: "flex", justifyContent: "flex-end", gap: "12px", paddingTop: "14px", borderTop: "1px solid #f3f4f6" }}>
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
              disabled={loading || isBlockedByRole}
              style={{
                padding: "9px 20px",
                border: "none",
                background: isBlockedByRole ? "#9ca3af" : "#4f46e5",
                color: "white",
                borderRadius: "7px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: loading || isBlockedByRole ? "not-allowed" : "pointer"
              }}
            >
              {loading ? "Saving..." : isBlockedByRole ? "Owner/Admin Only" : "Save Changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export function EditFlagModal({ flag, isOpen, onClose, onSuccess }: EditFlagModalProps) {
  if (!isOpen || !flag) return null;

  return (
    <EditFlagModalContent
      key={`${flag.id}-${flag.version}`}
      flag={flag}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  );
}

export default EditFlagModal;
