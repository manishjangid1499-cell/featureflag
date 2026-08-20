import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      await login({
        email: email.trim(),
        password,
      });
      navigate("/dashboard");
    } catch (err: any) {
      console.error("Login failed:", err);
      const status = err?.response?.status;
      const backendMessage = err?.response?.data?.message?.toLowerCase() || "";

      if (
        status === 400 ||
        status === 401 ||
        backendMessage.includes("password") ||
        backendMessage.includes("user not found") ||
        backendMessage.includes("credentials") ||
        backendMessage.includes("invalid") ||
        backendMessage.includes("bad request")
      ) {
        setError("Invalid email or password.");
      } else if (err?.message?.includes("Network Error") || !err?.response) {
        setError("Unable to connect to the service. Please try again.");
      } else {
        setError("Unable to sign in. Please try again.");
      }
    } finally {
      setLoading(false);
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
        maxWidth: "420px",
        background: "white",
        borderRadius: "16px",
        boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
        overflow: "hidden"
      }}>
        {/* HEADER BRAND */}
        <div style={{
          padding: "36px 32px 24px",
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
            margin: "0 auto 14px auto",
            boxShadow: "0 4px 12px rgba(79, 70, 229, 0.3)"
          }}>
            FF
          </div>
          <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 800, color: "#111827" }}>
            FeatureFlag Platform
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Sign in to access your organization's console
          </p>
        </div>

        {/* LOGIN FORM */}
        <form onSubmit={handleSubmit} style={{ padding: "32px" }}>
          {error && (
            <div style={{
              background: "#fef2f2",
              border: "1px solid #fecaca",
              color: "#991b1b",
              padding: "11px 14px",
              borderRadius: "8px",
              fontSize: "13px",
              marginBottom: "20px",
              lineHeight: "1.4"
            }}>
              {error}
            </div>
          )}

          <div style={{ marginBottom: "18px" }}>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
              Work Email
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              placeholder="name@company.com"
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
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
              <label style={{ fontSize: "12px", fontWeight: 600, color: "#374151" }}>
                Password
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
              placeholder="••••••••"
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
              boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)"
            }}
          >
            {loading ? "Signing In..." : "Sign In to Platform"}
          </button>
        </form>
      </div>
    </div>
  );
}

export default Login;