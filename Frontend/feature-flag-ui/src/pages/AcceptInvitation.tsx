import { useState, useEffect, type FormEvent } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { validateInvitation, acceptInvitation } from "../api/authApi";
import type { ValidateInvitationResponse } from "../types/auth";

export function AcceptInvitation() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token") || "";

  const [validating, setValidating] = useState(true);
  const [invitationData, setInvitationData] = useState<ValidateInvitationResponse | null>(null);
  const [validationError, setValidationError] = useState("");

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setValidating(false);
      setValidationError("Invitation token is missing from the URL.");
      return;
    }

    const checkToken = async () => {
      try {
        setValidating(true);
        const res = await validateInvitation(token);
        if (res.valid) {
          setInvitationData(res);
        } else {
          setValidationError(res.errorMessage || "This invitation link is invalid or expired.");
        }
      } catch (err: any) {
        console.error("Token validation error:", err);
        setValidationError(err?.response?.data?.message || "Failed to validate invitation.");
      } finally {
        setValidating(false);
      }
    };

    checkToken();
  }, [token]);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitError("");

    if (password.length < 6) {
      setSubmitError("Password must be at least 6 characters long.");
      return;
    }

    if (password !== confirmPassword) {
      setSubmitError("Passwords do not match.");
      return;
    }

    try {
      setSubmitting(true);
      const msg = await acceptInvitation({
        token,
        password,
        confirmPassword,
      });
      setSuccessMessage(msg || "Account created successfully! You can now sign in.");
    } catch (err: any) {
      console.error("Accept invitation failed:", err);
      setSubmitError(err?.response?.data?.message || "Failed to create account. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{
      minHeight: "100vh",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      background: "radial-gradient(ellipse at top, #1e1b4b 0%, #0f172a 100%)",
      padding: "24px",
      fontFamily: "Inter, -apple-system, sans-serif"
    }}>
      <div style={{
        width: "100%",
        maxWidth: "460px",
        background: "white",
        borderRadius: "16px",
        boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
        overflow: "hidden"
      }}>
        {/* HEADER BRAND */}
        <div style={{
          padding: "32px 32px 20px",
          textAlign: "center",
          borderBottom: "1px solid #f3f4f6"
        }}>
          <div style={{
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
            margin: "0 auto 12px auto",
            boxShadow: "0 4px 12px rgba(79, 70, 229, 0.3)"
          }}>
            FF
          </div>
          <h1 style={{ margin: 0, fontSize: "20px", fontWeight: 800, color: "#111827" }}>
            FeatureFlag Platform
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Accept Invitation & Setup Password
          </p>
        </div>

        <div style={{ padding: "32px" }}>
          {validating ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#6b7280" }}>
              <p style={{ fontSize: "14px", fontWeight: 600 }}>Validating invitation security token...</p>
            </div>
          ) : validationError ? (
            <div>
              <div style={{
                background: "#fef2f2",
                border: "1px solid #fecaca",
                color: "#991b1b",
                padding: "16px",
                borderRadius: "10px",
                fontSize: "13px",
                lineHeight: "1.5",
                marginBottom: "24px"
              }}>
                <strong style={{ display: "block", marginBottom: "4px" }}>Invitation Unavailable</strong>
                {validationError}
              </div>

              <div style={{ textAlign: "center" }}>
                <Link
                  to="/login"
                  style={{
                    display: "inline-block",
                    padding: "10px 20px",
                    background: "#4f46e5",
                    color: "white",
                    borderRadius: "8px",
                    fontSize: "13px",
                    fontWeight: 600,
                    textDecoration: "none"
                  }}
                >
                  Return to Sign In
                </Link>
              </div>
            </div>
          ) : successMessage ? (
            <div style={{ textAlign: "center" }}>
              <div style={{
                width: "56px",
                height: "56px",
                borderRadius: "50%",
                background: "#dcfce7",
                color: "#16a34a",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "24px",
                margin: "0 auto 16px auto",
                fontWeight: "bold"
              }}>
                ✓
              </div>
              <h2 style={{ fontSize: "18px", fontWeight: 800, color: "#111827", margin: "0 0 8px 0" }}>
                Account Ready!
              </h2>
              <p style={{ fontSize: "13px", color: "#4b5563", marginBottom: "24px", lineHeight: "1.5" }}>
                {successMessage}
              </p>

              <button
                type="button"
                onClick={() => navigate("/login")}
                style={{
                  width: "100%",
                  padding: "12px",
                  border: "none",
                  background: "#4f46e5",
                  color: "white",
                  borderRadius: "8px",
                  fontSize: "14px",
                  fontWeight: 700,
                  cursor: "pointer",
                  boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)"
                }}
              >
                Go to Sign In
              </button>
            </div>
          ) : (
            <div>
              {/* INVITATION DETAILS BANNER */}
              <div style={{
                background: "#f8fafc",
                border: "1px solid #e2e8f0",
                borderRadius: "10px",
                padding: "14px 16px",
                marginBottom: "24px"
              }}>
                <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>
                  {invitationData?.invitedByName
                    ? `${invitationData.invitedByName} has invited you to join as:`
                    : "You've been invited to join as:"}
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 700, fontSize: "14px", color: "#0f172a" }}>
                    {invitationData?.email}
                  </span>
                  <span style={{
                    padding: "3px 10px",
                    borderRadius: "12px",
                    fontSize: "11px",
                    fontWeight: 700,
                    background: invitationData?.role === "ADMIN" ? "#dbeafe" : "#d1fae5",
                    color: invitationData?.role === "ADMIN" ? "#1d4ed8" : "#047857"
                  }}>
                    {invitationData?.role}
                  </span>
                </div>
              </div>

              {submitError && (
                <div style={{
                  background: "#fef2f2",
                  border: "1px solid #fecaca",
                  color: "#991b1b",
                  padding: "11px 14px",
                  borderRadius: "8px",
                  fontSize: "13px",
                  marginBottom: "18px",
                  lineHeight: "1.4"
                }}>
                  {submitError}
                </div>
              )}

              <form onSubmit={handleSubmit}>
                <div style={{ marginBottom: "16px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
                    <label style={{ fontSize: "12px", fontWeight: 600, color: "#374151" }}>
                      Create Password *
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
                        padding: 0
                      }}
                    >
                      {showPassword ? "Hide" : "Show"}
                    </button>
                  </div>
                  <input
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    placeholder="Minimum 6 characters"
                    style={{
                      width: "100%",
                      padding: "10px 14px",
                      border: "1px solid #d1d5db",
                      borderRadius: "8px",
                      fontSize: "14px",
                      boxSizing: "border-box"
                    }}
                  />
                </div>

                <div style={{ marginBottom: "24px" }}>
                  <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
                    Confirm Password *
                  </label>
                  <input
                    type={showPassword ? "text" : "password"}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    placeholder="Re-enter your password"
                    style={{
                      width: "100%",
                      padding: "10px 14px",
                      border: "1px solid #d1d5db",
                      borderRadius: "8px",
                      fontSize: "14px",
                      boxSizing: "border-box"
                    }}
                  />
                </div>

                <button
                  type="submit"
                  disabled={submitting}
                  style={{
                    width: "100%",
                    padding: "12px",
                    border: "none",
                    background: "#4f46e5",
                    color: "white",
                    borderRadius: "8px",
                    fontSize: "14px",
                    fontWeight: 700,
                    cursor: submitting ? "not-allowed" : "pointer",
                    boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)"
                  }}
                >
                  {submitting ? "Creating Account..." : "Create Account"}
                </button>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AcceptInvitation;
