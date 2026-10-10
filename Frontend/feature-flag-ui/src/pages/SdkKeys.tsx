import { useState, useEffect, useRef, type FormEvent } from "react";
import { useAuth } from "../hooks/useAuth";
import { getSdkKeys, createSdkKey, revokeSdkKey } from "../api/sdkKeyApi";
import { getApiErrorMessage } from "../api/errors";
import { PaginationControls } from "../components/PaginationControls";
import { usePagedResource } from "../hooks/usePagedResource";
import type { SdkKeyMetadataResponse } from "../types/sdkKey";

const ENVIRONMENTS = ["DEV", "QA", "STAGING", "PROD"] as const;

export function SdkKeys() {
  const { canManageMembers } = useAuth();
  const {
    items: keys,
    data: pageData,
    setPage,
    loading,
    error: loadError,
    reload,
  } = usePagedResource<SdkKeyMetadataResponse>(
    getSdkKeys,
    "Failed to load SDK keys. Please verify your permissions.",
  );

  const [notification, setNotification] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  // Create Key Modal state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [keyName, setKeyName] = useState("");
  const [environment, setEnvironment] = useState<string>("DEV");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const createInputRef = useRef<HTMLInputElement>(null);

  // Secret Key Reveal Modal state (one-time display only)
  const [revealedKey, setRevealedKey] = useState<string | null>(null);
  const [revealedMeta, setRevealedMeta] = useState<{
    name: string;
    environment: string;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  // Revoke Key Modal state
  const [revokingKey, setRevokingKey] = useState<SdkKeyMetadataResponse | null>(null);
  const [revoking, setRevoking] = useState(false);
  const [revokeError, setRevokeError] = useState("");

  // Accordion for integration guide (collapsed by default)
  const [showGuide, setShowGuide] = useState(false);

  // Focus trap / auto-focus for Create modal
  useEffect(() => {
    if (isCreateOpen) {
      setTimeout(() => createInputRef.current?.focus(), 50);
    }
  }, [isCreateOpen]);

  // Keyboard navigation (Escape to close modals)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (isCreateOpen && !creating) setIsCreateOpen(false);
        if (revokingKey && !revoking) setRevokingKey(null);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isCreateOpen, creating, revokingKey, revoking]);

  // Security: Clean up raw credential material from component state on unmount
  useEffect(() => {
    return () => {
      setRevealedKey(null);
      setRevealedMeta(null);
    };
  }, []);

  const handleCreateSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setCreateError("");
    const trimmedName = keyName.trim();
    if (!trimmedName) {
      setCreateError("Key name is required.");
      return;
    }

    try {
      setCreating(true);
      const response = await createSdkKey({
        name: trimmedName,
        environment,
      });

      // Close creation modal, reload list, and reveal the one-time raw secret
      setIsCreateOpen(false);
      setKeyName("");
      setEnvironment("DEV");
      await reload(0);

      // Never store rawKey in localStorage/sessionStorage/cookies.
      // Retain strictly in component memory for this single reveal dialog.
      setRevealedKey(response.rawKey);
      setRevealedMeta({
        name: response.name,
        environment: response.environment,
      });
      setNotification({
        type: "success",
        message: `SDK key "${response.name}" created successfully for ${response.environment}.`,
      });
    } catch (err: unknown) {
      setCreateError(
        getApiErrorMessage(
          err,
          "Failed to create SDK key. Automatic retry is not performed to prevent duplicate keys.",
        ),
      );
    } finally {
      setCreating(false);
    }
  };

  const handleCloseReveal = () => {
    setRevealedKey(null);
    setRevealedMeta(null);
    setCopied(false);
  };

  const handleCopyRawKey = async () => {
    if (!revealedKey) return;
    try {
      await navigator.clipboard.writeText(revealedKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 3000);
    } catch {
      setNotification({
        type: "error",
        message: "Failed to copy to clipboard. Please copy manually.",
      });
    }
  };

  const handleRevokeConfirm = async () => {
    if (!revokingKey) return;
    setRevokeError("");
    try {
      setRevoking(true);
      await revokeSdkKey(revokingKey.id);
      await reload();
      setNotification({
        type: "success",
        message: `SDK key "${revokingKey.name}" has been revoked successfully.`,
      });
      setRevokingKey(null);
    } catch (err: unknown) {
      setRevokeError(
        getApiErrorMessage(err, "Failed to revoke SDK key. Please try again."),
      );
    } finally {
      setRevoking(false);
    }
  };

  const getEnvBadgeStyle = (env: string) => {
    switch (env?.toUpperCase()) {
      case "PROD":
        return { background: "#fee2e2", color: "#b91c1c", border: "1px solid #fca5a5" };
      case "STAGING":
        return { background: "#f3e8ff", color: "#7e22ce", border: "1px solid #d8b4fe" };
      case "QA":
        return { background: "#fef3c7", color: "#b45309", border: "1px solid #fcd34d" };
      case "DEV":
      default:
        return { background: "#e0f2fe", color: "#0369a1", border: "1px solid #7dd3fc" };
    }
  };

  if (!canManageMembers) {
    return (
      <div style={{ maxWidth: "1200px", margin: "40px auto", padding: "0 20px" }}>
        <div
          style={{
            background: "#fee2e2",
            border: "1px solid #ef4444",
            borderRadius: "8px",
            padding: "24px",
            textAlign: "center",
            color: "#991b1b",
          }}
        >
          <h2 style={{ margin: "0 0 8px 0" }}>Access Denied</h2>
          <p style={{ margin: 0, fontSize: "14px" }}>
            You do not have permission to manage SDK keys. Only organization OWNER and ADMIN roles are authorized.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto", padding: "0 8px" }}>
      {/* HEADER */}
      <div
        className="page-header"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: "24px",
          flexWrap: "wrap",
          gap: "16px",
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            SDK Keys
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Server-side credentials for evaluating feature flags via runtime endpoints and SDKs
          </p>
        </div>

        <div className="responsive-actions" style={{ display: "flex", gap: "10px" }}>
          <button
            type="button"
            onClick={() => setIsCreateOpen(true)}
            style={{
              padding: "9px 18px",
              border: "none",
              background: "#4f46e5",
              color: "white",
              borderRadius: "8px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer",
              boxShadow: "0 2px 6px rgba(79, 70, 229, 0.3)",
            }}
          >
            + Create SDK Key
          </button>
        </div>
      </div>

      {/* NOTIFICATIONS */}
      {notification && (
        <div
          style={{
            marginBottom: "16px",
            padding: "12px 16px",
            borderRadius: "8px",
            fontSize: "13px",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            background: notification.type === "success" ? "#ecfdf5" : "#fef2f2",
            border: `1px solid ${notification.type === "success" ? "#10b981" : "#ef4444"}`,
            color: notification.type === "success" ? "#065f46" : "#991b1b",
          }}
        >
          <span>{notification.message}</span>
          <button
            type="button"
            onClick={() => setNotification(null)}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              fontSize: "16px",
              color: "inherit",
              lineHeight: 1,
            }}
          >
            ×
          </button>
        </div>
      )}

      {loadError && (
        <div
          style={{
            marginBottom: "16px",
            padding: "14px 18px",
            borderRadius: "8px",
            fontSize: "13px",
            background: "#fef2f2",
            border: "1px solid #ef4444",
            color: "#991b1b",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <div>
            <strong>Service Connection Error:</strong> {loadError}
          </div>
          <button
            type="button"
            onClick={() => void reload()}
            style={{
              padding: "6px 14px",
              background: "#be123c",
              color: "white",
              border: "none",
              borderRadius: "6px",
              fontSize: "12px",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Retry
          </button>
        </div>
      )}

      {/* KEYS TABLE */}
      <div
        style={{
          background: "white",
          borderRadius: "10px",
          border: "1px solid #e5e7eb",
          boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
          overflow: "hidden",
          marginBottom: "24px",
        }}
      >
        <div className="table-scroll">
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
                <th style={{ padding: "12px 18px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "26%" }}>
                  NAME
                </th>
                <th style={{ padding: "12px 14px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "12%" }}>
                  ENVIRONMENT
                </th>
                <th style={{ padding: "12px 14px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "14%" }}>
                  KEY PREFIX
                </th>
                <th style={{ padding: "12px 14px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "12%" }}>
                  STATUS
                </th>
                <th style={{ padding: "12px 14px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "16%" }}>
                  CREATED
                </th>
                <th style={{ padding: "12px 14px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", width: "10%" }}>
                  REVOKED
                </th>
                <th style={{ padding: "12px 18px", fontSize: "11px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", textAlign: "right", width: "10%" }}>
                  ACTIONS
                </th>
              </tr>
            </thead>
            <tbody>
              {loadError ? (
                <tr>
                  <td colSpan={7} style={{ padding: "32px", textAlign: "center" }}>
                    <div style={{ color: "#b91c1c", fontWeight: 600, marginBottom: "10px", fontSize: "14px" }}>
                      Failed to load SDK keys: {loadError}
                    </div>
                    <button
                      type="button"
                      onClick={() => void reload()}
                      style={{
                        padding: "6px 14px",
                        background: "#fee2e2",
                        border: "1px solid #f87171",
                        color: "#991b1b",
                        borderRadius: "6px",
                        fontSize: "12px",
                        fontWeight: 600,
                        cursor: "pointer",
                      }}
                    >
                      ↻ Retry
                    </button>
                  </td>
                </tr>
              ) : loading && keys.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#6b7280" }}>
                    Loading SDK keys...
                  </td>
                </tr>
              ) : keys.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#6b7280" }}>
                    No SDK keys created yet. Click "+ Create SDK Key" above to generate one.
                  </td>
                </tr>
              ) : (
                keys.map((key) => {
                  const envBadge = getEnvBadgeStyle(key.environment);
                  return (
                    <tr
                      key={key.id}
                      style={{
                        borderBottom: "1px solid #f1f5f9",
                        background: key.active ? "white" : "#f8fafc",
                        opacity: key.active ? 1 : 0.85,
                      }}
                    >
                      <td style={{ padding: "13px 18px" }}>
                        <div style={{ fontWeight: 600, color: key.active ? "#0f172a" : "#64748b", fontSize: "13px" }}>
                          {key.name}
                        </div>
                      </td>

                      <td style={{ padding: "13px 14px" }}>
                        <span
                          style={{
                            padding: "3px 8px",
                            borderRadius: "12px",
                            fontSize: "11px",
                            fontWeight: 700,
                            ...envBadge,
                            opacity: key.active ? 1 : 0.7,
                          }}
                        >
                          {key.environment}
                        </span>
                      </td>

                      <td style={{ padding: "13px 14px" }}>
                        <code
                          style={{
                            background: key.active ? "#f1f5f9" : "#e2e8f0",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            fontSize: "12px",
                            fontFamily: "monospace",
                            color: key.active ? "#334155" : "#64748b",
                          }}
                        >
                          {key.keyPrefix}...
                        </code>
                      </td>

                      <td style={{ padding: "13px 14px" }}>
                        <span
                          style={{
                            padding: "3px 8px",
                            borderRadius: "12px",
                            fontSize: "11px",
                            fontWeight: 700,
                            background: key.active ? "#dcfce7" : "#f1f5f9",
                            color: key.active ? "#15803d" : "#64748b",
                            border: key.active ? "none" : "1px solid #e2e8f0",
                          }}
                        >
                          {key.active ? "ACTIVE" : "REVOKED"}
                        </span>
                      </td>

                      <td style={{ padding: "13px 14px", fontSize: "12px", color: "#64748b" }}>
                        <div>{key.createdAt ? new Date(key.createdAt).toLocaleString() : "—"}</div>
                        <div style={{ fontSize: "11px", color: "#94a3b8" }}>by {key.createdBy}</div>
                      </td>

                      <td style={{ padding: "13px 14px", fontSize: "12px", color: "#64748b" }}>
                        {key.revokedAt ? (
                          <span style={{ color: "#64748b" }}>
                            {new Date(key.revokedAt).toLocaleString()}
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>

                      <td style={{ padding: "13px 18px", textAlign: "right" }}>
                        {key.active ? (
                          <button
                            type="button"
                            onClick={() => setRevokingKey(key)}
                            style={{
                              padding: "5px 12px",
                              border: "1px solid #fee2e2",
                              background: "#fff",
                              color: "#dc2626",
                              borderRadius: "6px",
                              fontSize: "12px",
                              fontWeight: 600,
                              cursor: "pointer",
                              transition: "all 0.15s ease",
                            }}
                          >
                            Revoke
                          </button>
                        ) : (
                          <span style={{ fontSize: "12px", color: "#94a3b8" }}>
                            Revoked
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <PaginationControls
          page={pageData.page}
          totalPages={pageData.totalPages}
          totalElements={pageData.totalElements}
          disabled={loading}
          onPageChange={setPage}
        />
      </div>

      {/* SERVER-SIDE INTEGRATION GUIDANCE */}
      <div
        style={{
          background: "white",
          borderRadius: "10px",
          border: "1px solid #e5e7eb",
          boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
          padding: "20px",
          marginBottom: "32px",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            cursor: "pointer",
          }}
          onClick={() => setShowGuide(!showGuide)}
        >
          <div>
            <h3 style={{ margin: "0 0 4px 0", fontSize: "16px", fontWeight: 700, color: "#111827" }}>
              Connect your application
            </h3>
            <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>
              Use an SDK key in your backend to evaluate flags.
            </p>
          </div>
          <span style={{ fontSize: "18px", color: "#6b7280" }}>
            {showGuide ? "▲" : "▼"}
          </span>
        </div>

        {showGuide && (
          <div style={{ marginTop: "16px", borderTop: "1px solid #f3f4f6", paddingTop: "16px" }}>
            <div
              style={{
                background: "#eff6ff",
                border: "1px solid #bfdbfe",
                borderRadius: "8px",
                padding: "12px 16px",
                fontSize: "13px",
                color: "#1e40af",
                marginBottom: "16px",
              }}
            >
              <strong>Keep SDK keys on your server. Never include them in browser or mobile application code.</strong> Never embed them in client-side code. All runtime flag evaluations must be performed by your server or backend API.
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "16px" }}>
              <div>
                <h4 style={{ margin: "0 0 8px 0", fontSize: "13px", color: "#374151" }}>
                  HTTP Authentication Header
                </h4>
                <div
                  style={{
                    background: "#1f2937",
                    color: "#f3f4f6",
                    padding: "10px 14px",
                    borderRadius: "6px",
                    fontFamily: "monospace",
                    fontSize: "12px",
                  }}
                >
                  X-Feature-Flag-Key: ff_sdk_&lt;your_secret_key&gt;
                </div>
              </div>

              <div>
                <h4 style={{ margin: "0 0 8px 0", fontSize: "13px", color: "#374151" }}>
                  Runtime Evaluation Path
                </h4>
                <div
                  style={{
                    background: "#1f2937",
                    color: "#f3f4f6",
                    padding: "10px 14px",
                    borderRadius: "6px",
                    fontFamily: "monospace",
                    fontSize: "12px",
                  }}
                >
                  {"GET /runtime/v1/flags/{flagKey}/evaluate?subject={subject}"}
                </div>
              </div>
            </div>

            <div style={{ marginTop: "16px" }}>
              <h4 style={{ margin: "0 0 8px 0", fontSize: "13px", color: "#374151" }}>
                cURL Example
              </h4>
              <pre
                style={{
                  background: "#1f2937",
                  color: "#e5e7eb",
                  padding: "12px 16px",
                  borderRadius: "6px",
                  fontSize: "12px",
                  fontFamily: "monospace",
                  overflowX: "auto",
                  margin: 0,
                }}
              >
{`curl -X GET "https://api.yourdomain.com/runtime/v1/flags/checkout-v2/evaluate?subject=user_42" \\
  -H "X-Feature-Flag-Key: ff_sdk_your_secret_key_here" \\
  -H "Accept: application/json"`}
              </pre>
            </div>

            <div style={{ marginTop: "16px" }}>
              <h4 style={{ margin: "0 0 8px 0", fontSize: "13px", color: "#374151" }}>
                Java SDK Example
              </h4>
              <pre
                style={{
                  background: "#1f2937",
                  color: "#e5e7eb",
                  padding: "12px 16px",
                  borderRadius: "6px",
                  fontSize: "12px",
                  fontFamily: "monospace",
                  overflowX: "auto",
                  margin: 0,
                }}
              >
{`FeatureFlagClient client = FeatureFlagClient.builder()
    .baseUrl("https://api.yourdomain.com")
    .sdkKey(System.getenv("FEATURE_FLAG_SDK_KEY"))
    .requestTimeout(Duration.ofSeconds(2))
    .build();

boolean isCheckoutV2Enabled = client.isEnabled("checkout-v2", "user_42", false);`}
              </pre>
            </div>
          </div>
        )}
      </div>

      {/* CREATE SDK KEY MODAL */}
      {isCreateOpen && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget && !creating) {
              setIsCreateOpen(false);
            }
          }}
        >
          <div
            className="modal-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-sdk-key-title"
            style={{ maxWidth: "520px" }}
          >
            <div className="modal-header">
              <div>
                <h3 id="create-sdk-key-title" style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#111827" }}>
                  Create SDK Key
                </h3>
                <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
                  Generate a server-side credential scoped to a specific environment
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateOpen(false)}
                disabled={creating}
                style={{ background: "none", border: "none", fontSize: "18px", cursor: "pointer", color: "#9ca3af" }}
              >
                ✕
              </button>
            </div>

            {createError && (
              <div
                style={{
                  margin: "16px 24px 0",
                  padding: "10px 14px",
                  borderRadius: "6px",
                  background: "#fef2f2",
                  border: "1px solid #ef4444",
                  color: "#991b1b",
                  fontSize: "13px",
                }}
              >
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateSubmit} style={{ padding: "20px 24px" }}>
              <div style={{ marginBottom: "16px" }}>
                <label
                  style={{
                    display: "block",
                    fontSize: "12px",
                    fontWeight: 600,
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Key Name *
                </label>
                <input
                  ref={createInputRef}
                  type="text"
                  value={keyName}
                  onChange={(e) => setKeyName(e.target.value)}
                  maxLength={120}
                  required
                  disabled={creating}
                  style={{
                    width: "100%",
                    padding: "9px 12px",
                    border: "1px solid #d1d5db",
                    borderRadius: "7px",
                    fontSize: "13px",
                    boxSizing: "border-box",
                  }}
                />
              </div>

              <div style={{ marginBottom: "16px" }}>
                <label
                  style={{
                    display: "block",
                    fontSize: "12px",
                    fontWeight: 600,
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Environment Scope *
                </label>
                <select
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value)}
                  disabled={creating}
                  style={{
                    width: "100%",
                    padding: "9px 12px",
                    border: "1px solid #d1d5db",
                    borderRadius: "7px",
                    fontSize: "13px",
                    background: "white",
                    boxSizing: "border-box",
                  }}
                >
                  {ENVIRONMENTS.map((env) => (
                    <option key={env} value={env}>
                      {env}
                    </option>
                  ))}
                </select>
                <p style={{ margin: "6px 0 0", fontSize: "12px", color: "#6b7280" }}>
                  The SDK key is strictly bound to <strong>{environment}</strong>. It cannot evaluate flags configured for any other environment.
                </p>
              </div>

              <div
                style={{
                  background: "#f9fafb",
                  border: "1px solid #e5e7eb",
                  borderRadius: "6px",
                  padding: "10px 14px",
                  fontSize: "12px",
                  color: "#6b7280",
                  marginBottom: "20px",
                }}
              >
                <strong>Notice:</strong> Automatic retry is not performed on key creation failures to prevent duplicate credential generation. If a failure occurs, check the metadata list or revoke and create a replacement.
              </div>

              <div
                className="responsive-actions"
                style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}
              >
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  disabled={creating}
                  style={{
                    padding: "8px 16px",
                    border: "1px solid #d1d5db",
                    background: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    cursor: "pointer",
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  style={{
                    padding: "8px 20px",
                    border: "none",
                    background: "#4f46e5",
                    color: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: creating ? "not-allowed" : "pointer",
                    boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)",
                  }}
                >
                  {creating ? "Generating Key..." : "Generate Key"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ONE-TIME RAW KEY REVEAL MODAL */}
      {revealedKey && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              handleCloseReveal();
            }
          }}
        >
          <div
            className="modal-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="save-sdk-key-title"
            style={{ maxWidth: "580px" }}
          >
            <div className="modal-header">
              <div>
                <h3 id="save-sdk-key-title" style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#111827" }}>
                  Save Your SDK Key
                </h3>
                <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
                  {revealedMeta?.name} ({revealedMeta?.environment})
                </p>
              </div>
            </div>

            <div style={{ padding: "20px 24px" }}>
              <div
                style={{
                  background: "#fffbeb",
                  border: "1px solid #f59e0b",
                  borderRadius: "8px",
                  padding: "14px 16px",
                  marginBottom: "18px",
                  fontSize: "13px",
                  color: "#92400e",
                  lineHeight: 1.5,
                }}
              >
                <div style={{ fontWeight: 700, marginBottom: "4px" }}>
                  ⚠️ Store this secret key securely; it cannot be shown again!
                </div>
                For security reasons, this raw key is never stored in plaintext on our servers and cannot be recovered after this dialog is closed. If you lose this key, you must revoke it and generate a replacement.
              </div>

              <label
                style={{
                  display: "block",
                  fontSize: "12px",
                  fontWeight: 600,
                  color: "#374151",
                  marginBottom: "6px",
                }}
              >
                Secret SDK Key:
              </label>

              <div
                style={{
                  display: "flex",
                  gap: "8px",
                  marginBottom: "20px",
                }}
              >
                <input
                  type="text"
                  readOnly
                  value={revealedKey}
                  style={{
                    flex: 1,
                    padding: "10px 12px",
                    fontFamily: "monospace",
                    fontSize: "12px",
                    background: "#f9fafb",
                    border: "1px solid #d1d5db",
                    borderRadius: "6px",
                    color: "#111827",
                  }}
                  onFocus={(e) => e.target.select()}
                />
                <button
                  type="button"
                  onClick={handleCopyRawKey}
                  style={{
                    padding: "10px 16px",
                    background: copied ? "#10b981" : "#4f46e5",
                    color: "white",
                    border: "none",
                    borderRadius: "6px",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: "pointer",
                    whiteSpace: "nowrap",
                    transition: "background 0.2s",
                  }}
                >
                  {copied ? "✓ Copied!" : "📋 Copy"}
                </button>
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <button
                  type="button"
                  onClick={handleCloseReveal}
                  style={{
                    padding: "9px 22px",
                    border: "1px solid #d1d5db",
                    background: "#111827",
                    color: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  I Have Stored This Key Safely — Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* REVOCATION CONFIRMATION MODAL */}
      {revokingKey && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget && !revoking) {
              setRevokingKey(null);
            }
          }}
        >
          <div
            className="modal-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="revoke-sdk-key-title"
            style={{ maxWidth: "480px" }}
          >
            <div className="modal-header">
              <div>
                <h3 id="revoke-sdk-key-title" style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#dc2626" }}>
                  Revoke SDK Key
                </h3>
                <p style={{ margin: "4px 0 0", fontSize: "12px", color: "#6b7280" }}>
                  This action is permanent and cannot be undone
                </p>
              </div>
              <button
                type="button"
                onClick={() => setRevokingKey(null)}
                disabled={revoking}
                style={{ background: "none", border: "none", fontSize: "18px", cursor: "pointer", color: "#9ca3af" }}
              >
                ✕
              </button>
            </div>

            {revokeError && (
              <div
                style={{
                  margin: "16px 24px 0",
                  padding: "10px 14px",
                  borderRadius: "6px",
                  background: "#fef2f2",
                  border: "1px solid #ef4444",
                  color: "#991b1b",
                  fontSize: "13px",
                }}
              >
                {revokeError}
              </div>
            )}

            <div style={{ padding: "20px 24px" }}>
              <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "#374151", lineHeight: 1.5 }}>
                Are you sure you want to revoke the SDK key{" "}
                <strong>"{revokingKey.name}"</strong> scoped to{" "}
                <strong>{revokingKey.environment}</strong>?
              </p>

              <div
                style={{
                  background: "#fef2f2",
                  border: "1px solid #fecaca",
                  borderRadius: "6px",
                  padding: "12px 14px",
                  fontSize: "12px",
                  color: "#991b1b",
                  marginBottom: "20px",
                }}
              >
                <strong>Warning:</strong> Any application or service currently using this key will immediately receive HTTP 401 Unauthorized errors on runtime evaluation calls.
              </div>

              <div
                className="responsive-actions"
                style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}
              >
                <button
                  type="button"
                  onClick={() => setRevokingKey(null)}
                  disabled={revoking}
                  style={{
                    padding: "8px 16px",
                    border: "1px solid #d1d5db",
                    background: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    cursor: revoking ? "not-allowed" : "pointer",
                  }}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleRevokeConfirm}
                  disabled={revoking}
                  style={{
                    padding: "8px 20px",
                    border: "none",
                    background: "#dc2626",
                    color: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: revoking ? "not-allowed" : "pointer",
                    boxShadow: "0 4px 12px rgba(220, 38, 38, 0.25)",
                  }}
                >
                  {revoking ? "Revoking..." : "Revoke Key"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default SdkKeys;
