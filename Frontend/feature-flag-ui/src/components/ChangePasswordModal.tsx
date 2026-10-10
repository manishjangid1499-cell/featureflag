import { useState, useEffect, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { changePassword } from "../api/authApi";
import { useAuth } from "../hooks/useAuth";
import { getApiErrorMessage, getApiStatus } from "../api/errors";

interface ChangePasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface ChangePasswordModalDialogProps {
  onClose: () => void;
}

function ChangePasswordModalDialog({ onClose }: ChangePasswordModalDialogProps) {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const resetState = () => {
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setShowPasswords(false);
    setErrorMessage("");
    setSubmitting(false);
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !submitting) {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [submitting, onClose]);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMessage("");

    if (!currentPassword) {
      setErrorMessage("Current password is required.");
      return;
    }

    if (!newPassword) {
      setErrorMessage("New password is required.");
      return;
    }

    if (newPassword.length < 8) {
      setErrorMessage("Use at least 8 characters.");
      return;
    }

    const utf8ByteLength = new TextEncoder().encode(newPassword).length;
    if (utf8ByteLength > 72) {
      setErrorMessage("This password is too long. Use a shorter password.");
      return;
    }

    if (newPassword === currentPassword) {
      setErrorMessage("New password cannot be the same as your current password.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setErrorMessage("Passwords do not match.");
      return;
    }

    try {
      setSubmitting(true);
      await changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      });

      // Clear password states immediately
      resetState();
      logout();
      onClose();
      navigate("/login", {
        replace: true,
        state: {
          successMessage: "Password changed successfully. Please sign in with your new password.",
        },
      });
    } catch (err: unknown) {
      const status = getApiStatus(err);
      if (status === undefined) {
        setErrorMessage(
          "Network connection error. If your password was changed, please sign in with your new password."
        );
      } else {
        setErrorMessage(
          getApiErrorMessage(err, "Failed to change password. Please check your current password and try again.")
        );
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="modal-backdrop"
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(15, 23, 42, 0.65)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "16px",
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget && !submitting) {
          onClose();
        }
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="change-password-modal-title"
        style={{
          width: "100%",
          maxWidth: "460px",
          background: "#ffffff",
          borderRadius: "16px",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
          overflow: "hidden",
        }}
      >
        {/* HEADER */}
        <div
          style={{
            padding: "20px 24px",
            borderBottom: "1px solid #e2e8f0",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div>
            <h2
              id="change-password-modal-title"
              style={{
                margin: 0,
                fontSize: "18px",
                fontWeight: 700,
                color: "#0f172a",
              }}
            >
              Change password
            </h2>
            <p
              style={{
                margin: "4px 0 0",
                fontSize: "13px",
                color: "#64748b",
              }}
            >
              After changing your password, sign in again on all devices.
            </p>
          </div>
          <button
            type="button"
            aria-label="Close change password modal"
            onClick={onClose}
            disabled={submitting}
            style={{
              background: "none",
              border: "none",
              fontSize: "20px",
              color: "#94a3b8",
              cursor: submitting ? "not-allowed" : "pointer",
              padding: "4px 8px",
              borderRadius: "6px",
              lineHeight: 1,
            }}
          >
            ✕
          </button>
        </div>

        {/* FORM */}
        <form onSubmit={handleSubmit} style={{ padding: "24px" }}>
          {errorMessage && (
            <div
              role="alert"
              style={{
                background: "#fef2f2",
                border: "1px solid #fecaca",
                color: "#991b1b",
                padding: "11px 14px",
                borderRadius: "8px",
                fontSize: "13px",
                marginBottom: "20px",
                lineHeight: "1.4",
              }}
            >
              {errorMessage}
            </div>
          )}

          {/* CURRENT PASSWORD */}
          <div style={{ marginBottom: "18px" }}>
            <label
              htmlFor="current-password"
              style={{
                display: "block",
                fontSize: "12px",
                fontWeight: 600,
                color: "#334155",
                marginBottom: "6px",
              }}
            >
              Current password
            </label>
            <input
              id="current-password"
              name="currentPassword"
              type={showPasswords ? "text" : "password"}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              disabled={submitting}
              required
              autoComplete="current-password"
              style={{
                width: "100%",
                padding: "10px 14px",
                fontSize: "14px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                boxSizing: "border-box",
                outline: "none",
              }}
            />
          </div>

          {/* NEW PASSWORD */}
          <div style={{ marginBottom: "18px" }}>
            <label
              htmlFor="new-password"
              style={{
                display: "block",
                fontSize: "12px",
                fontWeight: 600,
                color: "#334155",
                marginBottom: "6px",
              }}
            >
              New password
            </label>
            <input
              id="new-password"
              name="newPassword"
              type={showPasswords ? "text" : "password"}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              disabled={submitting}
              required
              autoComplete="new-password"
              style={{
                width: "100%",
                padding: "10px 14px",
                fontSize: "14px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                boxSizing: "border-box",
                outline: "none",
              }}
            />
          </div>

          {/* CONFIRM PASSWORD */}
          <div style={{ marginBottom: "20px" }}>
            <label
              htmlFor="confirm-password"
              style={{
                display: "block",
                fontSize: "12px",
                fontWeight: 600,
                color: "#334155",
                marginBottom: "6px",
              }}
            >
              Confirm new password
            </label>
            <input
              id="confirm-password"
              name="confirmPassword"
              type={showPasswords ? "text" : "password"}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={submitting}
              required
              autoComplete="new-password"
              style={{
                width: "100%",
                padding: "10px 14px",
                fontSize: "14px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                boxSizing: "border-box",
                outline: "none",
              }}
            />
          </div>

          {/* TOGGLE SHOW PASSWORD */}
          <div style={{ marginBottom: "24px" }}>
            <label
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                fontSize: "13px",
                color: "#475569",
                cursor: "pointer",
              }}
            >
              <input
                type="checkbox"
                checked={showPasswords}
                onChange={(e) => setShowPasswords(e.target.checked)}
                disabled={submitting}
              />
              Show passwords
            </label>
          </div>

          {/* ACTIONS */}
          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              gap: "12px",
            }}
          >
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              style={{
                padding: "10px 16px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                background: "#ffffff",
                color: "#475569",
                fontSize: "14px",
                fontWeight: 600,
                cursor: submitting ? "not-allowed" : "pointer",
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              style={{
                padding: "10px 20px",
                borderRadius: "8px",
                border: "none",
                background: submitting ? "#93c5fd" : "#2563eb",
                color: "#ffffff",
                fontSize: "14px",
                fontWeight: 600,
                cursor: submitting ? "not-allowed" : "pointer",
                boxShadow: "0 2px 4px rgba(37, 99, 235, 0.2)",
              }}
            >
              {submitting ? "Changing password..." : "Change password"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export function ChangePasswordModal({ isOpen, onClose }: ChangePasswordModalProps) {
  if (!isOpen) return null;

  return <ChangePasswordModalDialog onClose={onClose} />;
}
