import { useState, useEffect, type FormEvent } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { resetPassword } from "../api/authApi";
import { getApiErrorMessage, getApiStatus } from "../api/errors";

export function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // Capture token in memory immediately on initialization without effect-driven state updates
  const [token, setToken] = useState(() => searchParams.get("token") || "");

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (searchParams.has("token")) {
      // Strip token immediately from visible browser URL to prevent bookmarking/history leaking,
      // while preserving React Router history state
      window.history.replaceState(window.history.state, document.title, window.location.pathname);
    }
  }, [searchParams]);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");

    if (!token) {
      setError("Password reset token is missing or invalid. Please request a new link.");
      return;
    }

    if (newPassword.length < 8) {
      setError("Password must be at least 8 characters long.");
      return;
    }

    // UTF-8 byte length validation for BCrypt limit (72 bytes)
    const utf8Bytes = new TextEncoder().encode(newPassword).length;
    if (utf8Bytes > 72) {
      setError("Password exceeds maximum allowed length (72 bytes).");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      await resetPassword({
        token,
        newPassword,
        confirmPassword,
      });

      // Clear token from memory upon success
      setToken("");

      // Redirect to login with explicit success message
      navigate("/login", {
        replace: true,
        state: {
          successMessage: "Your password has been successfully reset. Please sign in with your new password.",
        },
      });
    } catch (err: unknown) {
      const status = getApiStatus(err);
      const fallbackMsg = "Failed to reset password. The link may have expired. Please request a new reset link.";
      const apiMsg = getApiErrorMessage(err, fallbackMsg);

      if (status === 400 || status === 409) {
        setError(apiMsg || "Invalid, expired, or already used reset token.");
      } else if (status === 429) {
        setError("Too many requests. Please wait a moment and try again.");
      } else {
        setError(apiMsg);
      }
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "radial-gradient(ellipse at top, #1e1b4b 0%, #0f172a 100%)",
          padding: "24px",
          fontFamily: "Inter, -apple-system, sans-serif",
        }}
      >
        <div
          style={{
            width: "100%",
            maxWidth: "420px",
            background: "white",
            borderRadius: "16px",
            boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
            padding: "36px 32px",
            textAlign: "center",
          }}
        >
          <div
            style={{
              width: "48px",
              height: "48px",
              borderRadius: "12px",
              background: "#fee2e2",
              color: "#dc2626",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "22px",
              margin: "0 auto 16px auto",
            }}
          >
            !
          </div>
          <h2 style={{ margin: "0 0 10px 0", fontSize: "20px", fontWeight: 700, color: "#111827" }}>
            Invalid or Expired Link
          </h2>
          <p style={{ margin: "0 0 24px 0", fontSize: "14px", color: "#6b7280", lineHeight: 1.5 }}>
            This password reset link is invalid, incomplete, or has expired. Please request a new password reset link.
          </p>
          <Link
            to="/login"
            style={{
              display: "inline-block",
              width: "100%",
              boxSizing: "border-box",
              padding: "12px",
              borderRadius: "8px",
              backgroundColor: "#4f46e5",
              color: "#ffffff",
              textDecoration: "none",
              fontSize: "14px",
              fontWeight: 700,
            }}
          >
            Back to Sign In
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "radial-gradient(ellipse at top, #1e1b4b 0%, #0f172a 100%)",
        padding: "24px",
        fontFamily: "Inter, -apple-system, sans-serif",
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "420px",
          background: "white",
          borderRadius: "16px",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            padding: "36px 32px 24px",
            textAlign: "center",
            borderBottom: "1px solid #f3f4f6",
          }}
        >
          <div
            style={{
              width: "48px",
              height: "48px",
              borderRadius: "12px",
              background: "#4f46e5",
              color: "white",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontWeight: 800,
              fontSize: "18px",
              margin: "0 auto 14px auto",
              boxShadow: "0 4px 12px rgba(79, 70, 229, 0.3)",
            }}
          >
            FF
          </div>
          <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 800, color: "#111827" }}>
            Reset password
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Create a secure password of at least 8 characters
          </p>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: "32px" }}>
          {error && (
            <div
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
              {error}
            </div>
          )}

          <div style={{ marginBottom: "18px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
              <label style={{ fontSize: "12px", fontWeight: 600, color: "#374151" }}>
                New password
              </label>
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  background: "none",
                  border: "none",
                  fontSize: "11px",
                  color: "#4f46e5",
                  fontWeight: 600,
                  cursor: "pointer",
                  padding: 0,
                }}
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
            <input
              type={showPassword ? "text" : "password"}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={8}
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

          <div style={{ marginBottom: "24px" }}>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
              Confirm new password
            </label>
            <input
              type={showPassword ? "text" : "password"}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={8}
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

          <button
            type="submit"
            disabled={loading}
            style={{
              width: "100%",
              padding: "12px",
              border: "none",
              background: "#4f46e5",
              color: "white",
              borderRadius: "8px",
              fontSize: "14px",
              fontWeight: 700,
              cursor: loading ? "not-allowed" : "pointer",
              boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)",
            }}
          >
            {loading ? "Resetting password..." : "Reset password"}
          </button>

          <div style={{ marginTop: "18px", textAlign: "center" }}>
            <Link
              to="/login"
              style={{
                fontSize: "13px",
                color: "#6b7280",
                textDecoration: "none",
                fontWeight: 500,
              }}
            >
              Back to Sign In
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ResetPassword;
