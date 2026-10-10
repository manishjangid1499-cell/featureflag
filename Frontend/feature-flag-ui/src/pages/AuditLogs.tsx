import { useState } from "react";
import { getAllAuditLogs } from "../api/auditApi";
import { PaginationControls } from "../components/PaginationControls";
import type { AuditLog } from "../types/audit";
import { usePagedResource } from "../hooks/usePagedResource";

export function AuditLogs() {
  const { items: logs, data: pageData, page, setPage,
    loading, error, reload: loadAuditLogs } = usePagedResource<AuditLog>(
      getAllAuditLogs, "Failed to connect to Audit Service.",
    );
  const [search, setSearch] = useState("");

  const filteredLogs = logs.filter((log) =>
    log.flagKey?.toLowerCase().includes(search.toLowerCase()) ||
    log.eventType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      <div className="page-header" style={{ marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Audit Logs
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Immutable chronological record of all feature flag modifications and administrative actions
          </p>
        </div>
      </div>

      <div style={{ background: "white", borderRadius: "12px", border: "1px solid #e5e7eb", padding: "14px 20px", marginBottom: "20px" }}>
        <label htmlFor="audit-search" style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
          Search
        </label>
        <input
          id="audit-search"
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: "100%", maxWidth: "400px", padding: "8px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
        />
      </div>

      {error ? (
        <div style={{
          background: "white",
          borderRadius: "14px",
          border: "1px solid #fee2e2",
          padding: "48px 20px",
          textAlign: "center",
          marginBottom: "20px"
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
            margin: "0 auto 14px auto"
          }}>
            !
          </div>
          <h3 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: 700, color: "#991b1b" }}>
            Failed to Load Audit Logs
          </h3>
          <p style={{ margin: "0 0 16px 0", fontSize: "13px", color: "#6b7280" }}>
            {error}
          </p>
          <button
            type="button"
            onClick={() => void loadAuditLogs(page)}
            style={{
              padding: "8px 16px",
              background: "#be123c",
              color: "white",
              border: "none",
              borderRadius: "6px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            ↻ Retry
          </button>
        </div>
      ) : loading ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center", color: "#6b7280" }}>
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading audit logs from Kafka pipeline...</p>
        </div>
      ) : filteredLogs.length === 0 ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center" }}>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#111827" }}>
            {search.trim() ? "No matching audit log events found" : "No audit log events recorded"}
          </h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>
            {search.trim() ? "Try clearing or modifying your filter." : "Audit entries will appear as flags are created, toggled, and updated."}
          </p>
        </div>
      ) : (
        <div className="table-scroll" style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", overflow: "hidden" }}>
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
      <PaginationControls
        page={pageData.page}
        totalPages={pageData.totalPages}
        totalElements={pageData.totalElements}
        disabled={loading}
        onPageChange={setPage}
      />
    </div>
  );
}

export default AuditLogs;
