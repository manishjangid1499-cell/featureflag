import { useState, type FormEvent } from "react";
import { forgotPassword } from "../api/authApi";
import { getApiStatus } from "../api/errors";

interface ForgotPasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialEmail?: string;
}

export function ForgotPasswordModal({ isOpen, onClose, initialEmail = "" }: ForgotPasswordModalProps) {
  const [email, setEmail] = useState(initialEmail);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState("");

  if (!isOpen) {
    return null;
  }

  const handleResetState = () => {
    setEmail(initialEmail);
    setError("");
    setLoading(false);
    setSubmitted(false);
    setFeedbackMessage("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await forgotPassword({ email: email.trim() });
      setFeedbackMessage(
        response.message || "If an eligible account exists, you will receive password reset instructions."
      );
      setSubmitted(true);
    } catch (err: unknown) {
      const status = getApiStatus(err);
      if (status === 429) {
        setError("Too many requests. Please wait a moment and try again.");
      } else {
        // Safe generic messaging even on network/server error to not reveal state or block user
        setFeedbackMessage(
          "If an eligible account exists, you will receive password reset instructions."
        );
        setSubmitted(true);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(15, 23, 42, 0.65)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 50,
        padding: "16px",
      }}
      onClick={handleResetState}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "420px",
          backgroundColor: "#ffffff",
          borderRadius: "16px",
          padding: "28px",
          boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.2)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 style={{ margin: "0 0 8px 0", fontSize: "18px", fontWeight: 700, color: "#111827" }}>
          Forgot password?
        </h2>
        <p style={{ margin: "0 0 20px 0", fontSize: "13px", color: "#6b7280", lineHeight: 1.5 }}>
          Enter the email address associated with your account.
        </p>

        {submitted ? (
          <div>
            <div
              style={{
                backgroundColor: "#ecfdf5",
                border: "1px solid #a7f3d0",
                color: "#065f46",
                padding: "14px",
                borderRadius: "8px",
                fontSize: "13px",
                lineHeight: 1.5,
                marginBottom: "24px",
              }}
            >
              {feedbackMessage}
            </div>
            <button
              type="button"
              onClick={handleResetState}
              style={{
                width: "100%",
                padding: "10px 16px",
                border: "none",
                borderRadius: "8px",
                backgroundColor: "#4f46e5",
                color: "#ffffff",
                fontSize: "14px",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Back to Sign In
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            {error && (
              <div
                style={{
                  backgroundColor: "#fef2f2",
                  border: "1px solid #fecaca",
                  color: "#991b1b",
                  padding: "11px 14px",
                  borderRadius: "8px",
                  fontSize: "13px",
                  marginBottom: "16px",
                }}
              >
                {error}
              </div>
            )}

            <div style={{ marginBottom: "20px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                Email address
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                style={{
                  width: "100%",
                  padding: "10px 14px",
                  border: "1px solid #d1d5db",
                  borderRadius: "8px",
                  fontSize: "14px",
                  boxSizing: "border-box",
                }}
              />
            </div>

            <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}>
              <button
                type="button"
                onClick={handleResetState}
                disabled={loading}
                style={{
                  padding: "10px 16px",
                  border: "1px solid #d1d5db",
                  borderRadius: "8px",
                  backgroundColor: "#ffffff",
                  color: "#374151",
                  fontSize: "13px",
                  fontWeight: 600,
                  cursor: loading ? "not-allowed" : "pointer",
                }}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                style={{
                  padding: "10px 18px",
                  border: "none",
                  borderRadius: "8px",
                  backgroundColor: "#4f46e5",
                  color: "#ffffff",
                  fontSize: "13px",
                  fontWeight: 600,
                  cursor: loading ? "not-allowed" : "pointer",
                }}
              >
                {loading ? "Sending…" : "Send reset link"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

export default ForgotPasswordModal;
