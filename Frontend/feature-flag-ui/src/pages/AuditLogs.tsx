import { useEffect, useState } from "react";
import { getAllAuditLogs } from "../api/auditApi";
import type { AuditLog } from "../types/audit";

export function AuditLogs() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  const loadAuditLogs = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getAllAuditLogs();
      setLogs(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error("Failed to load audit logs:", err);
      const msg = err?.response?.data?.message || err?.message || "Failed to connect to Audit Service.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAuditLogs();
  }, []);

  const filteredLogs = logs.filter((log) =>
    log.flagKey?.toLowerCase().includes(search.toLowerCase()) ||
    log.eventType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Audit Logs
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Immutable chronological record of all feature flag modifications and administrative actions
          </p>
        </div>

        <button
          type="button"
          onClick={loadAuditLogs}
          style={{
            padding: "9px 15px",
            border: "1px solid #d1d5db",
            background: "white",
            color: "#374151",
            borderRadius: "8px",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer"
          }}
        >
          ↻ Refresh
        </button>
      </div>

      <div style={{ background: "white", borderRadius: "12px", border: "1px solid #e5e7eb", padding: "14px 20px", marginBottom: "20px" }}>
        <input
          type="text"
          placeholder="Filter audit logs by flag key or event type..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: "100%", maxWidth: "400px", padding: "8px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
        />
      </div>

      {error && (
        <div style={{ background: "#fff1f2", border: "1px solid #fecdd3", borderRadius: "10px", padding: "14px 18px", color: "#9f1239", marginBottom: "20px" }}>
          <strong>Service Error:</strong> {error}
        </div>
      )}

      {loading ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center", color: "#6b7280" }}>
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading audit logs from Kafka pipeline...</p>
        </div>
      ) : filteredLogs.length === 0 ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center" }}>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#111827" }}>No audit log events recorded</h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>Audit entries will appear as flags are created, toggled, and updated.</p>
        </div>
      ) : (
        <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb" }}>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>ID</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Event Action</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Flag Key</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.map((log) => (
                <tr key={log.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                  <td style={{ padding: "14px 20px", fontSize: "12px", color: "#6b7280" }}>
                    #{log.id}
                  </td>
                  <td style={{ padding: "14px 20px" }}>
                    <span style={{
                      padding: "3px 8px",
                      borderRadius: "4px",
                      fontSize: "11px",
                      fontWeight: 700,
                      background: log.eventType?.includes("DELETE") ? "#fee2e2" : log.eventType?.includes("CREATE") ? "#dcfce7" : "#e0e7ff",
                      color: log.eventType?.includes("DELETE") ? "#b91c1c" : log.eventType?.includes("CREATE") ? "#15803d" : "#3730a3"
                    }}>
                      {log.eventType}
                    </span>
                  </td>
                  <td style={{ padding: "14px 20px", fontFamily: "monospace", fontWeight: 700, color: "#111827" }}>
                    {log.flagKey}
                  </td>
                  <td style={{ padding: "14px 20px", fontSize: "12px", color: "#4b5563" }}>
                    {log.timestamp ? new Date(log.timestamp).toLocaleString() : "N/A"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default AuditLogs;
