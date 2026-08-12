import { useState } from "react";
import type { FeatureFlag } from "../../types/featureFlag";
import { deleteFlag } from "../../api/flagApi";

interface DeleteFlagModalProps {
  flag: FeatureFlag | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (deletedFlagId: number) => void;
}

export function DeleteFlagModal({ flag, isOpen, onClose, onSuccess }: DeleteFlagModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if (!isOpen || !flag) return null;

  const handleDelete = async () => {
    try {
      setLoading(true);
      setError("");
      await deleteFlag(flag.id);
      onSuccess(flag.id);
      onClose();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || "Failed to delete feature flag.";
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
        maxWidth: "460px",
        boxShadow: "0 20px 40px rgba(0,0,0,0.15)",
        border: "1px solid #e5e7eb",
        padding: "28px"
      }}>
        <div style={{
          width: "48px",
          height: "48px",
          borderRadius: "50%",
          background: "#fee2e2",
          color: "#dc2626",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "20px",
          fontWeight: "bold",
          marginBottom: "16px"
        }}>
          ⚠
        </div>

        <h3 style={{ margin: "0 0 8px 0", fontSize: "18px", color: "#111827", fontWeight: 700 }}>
          Delete Feature Flag
        </h3>

        <p style={{ color: "#6b7280", fontSize: "13px", lineHeight: "1.5", margin: "0 0 20px 0" }}>
          Are you sure you want to permanently delete flag{" "}
          <strong style={{ color: "#111827", fontFamily: "monospace" }}>{flag.flagKey}</strong> in environment{" "}
          <span style={{ fontWeight: 600, color: "#4f46e5" }}>{flag.environment}</span>? This action cannot be undone.
        </p>

        {error && (
          <div style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            color: "#b91c1c",
            padding: "9px 12px",
            borderRadius: "7px",
            fontSize: "12px",
            marginBottom: "18px"
          }}>
            {error}
          </div>
        )}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
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
            type="button"
            disabled={loading}
            onClick={handleDelete}
            style={{
              padding: "9px 18px",
              border: "none",
              background: "#dc2626",
              color: "white",
              borderRadius: "7px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: loading ? "not-allowed" : "pointer"
            }}
          >
            {loading ? "Deleting..." : "Delete Flag"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default DeleteFlagModal;
