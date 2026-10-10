import { useState } from "react";
import { getAllAnalytics } from "../api/analyticsApi";
import { PaginationControls } from "../components/PaginationControls";
import type { AnalyticsEvent } from "../types/analytics";
import { usePagedResource } from "../hooks/usePagedResource";

export function Analytics() {
  const { items: events, data: pageData, page, setPage,
    loading, error, reload: loadAnalytics } = usePagedResource<AnalyticsEvent>(
      getAllAnalytics, "Failed to connect to Analytics Service.",
    );
  const [search, setSearch] = useState("");

  const totalEventCount = events.reduce((sum, e) => sum + (e.count || 0), 0);

  const filteredEvents = events.filter((e) =>
    e.flagKey?.toLowerCase().includes(search.toLowerCase()) ||
    e.eventType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      <div className="page-header" style={{ marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Feature Flag Analytics
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Aggregated lifecycle mutation events and runtime evaluation telemetry consumed from Kafka
          </p>
        </div>
      </div>

      {/* SUMMARY BANNER */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "16px", marginBottom: "24px" }}>
        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>FLAG KEYS (THIS PAGE)</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#111827", marginTop: "4px" }}>
            {error ? "—" : loading ? "..." : new Set(events.map((e) => e.flagKey)).size}
          </div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#4f46e5", fontWeight: 600 }}>EVENT COUNT (THIS PAGE)</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#4f46e5", marginTop: "4px" }}>
            {error ? "—" : loading ? "..." : totalEventCount}
          </div>
        </div>
      </div>

      {/* INFORMATION NOTICE */}
      <div style={{
        background: "#f8fafc",
        border: "1px solid #e2e8f0",
        borderRadius: "10px",
        padding: "14px 18px",
        marginBottom: "20px",
        fontSize: "13px",
        color: "#475569",
        lineHeight: "1.5"
      }}>
        <strong style={{ color: "#1e293b" }}>Evaluation & Lifecycle Telemetry:</strong> Aggregates count total runtime evaluation and mutation events; they do not represent unique users or conversion statistics. Note that historical counts may include dashboard previews executed prior to preview telemetry separation. Manual deletion or reset is disabled to ensure Kafka replay consistency.
      </div>

      {/* SEARCH BAR */}
      <div style={{ background: "white", borderRadius: "12px", border: "1px solid #e5e7eb", padding: "14px 20px", marginBottom: "20px" }}>
        <label htmlFor="analytics-search" style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>
          Search
        </label>
        <input
          id="analytics-search"
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
            Failed to Load Analytics
          </h3>
          <p style={{ margin: "0 0 16px 0", fontSize: "13px", color: "#6b7280" }}>
            {error}
          </p>
          <button
            type="button"
            onClick={() => void loadAnalytics(page)}
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
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading analytics stream...</p>
        </div>
      ) : filteredEvents.length === 0 ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center" }}>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#111827" }}>
            {search.trim() ? "No matching analytics records found" : "No analytics records found"}
          </h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>
            {search.trim() ? "Try clearing or modifying your filter." : "Mutation events will appear as flags are created and modified."}
          </p>
        </div>
      ) : (
        <div className="table-scroll" style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb" }}>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Flag Key</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Event Type</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Count</th>
              </tr>
            </thead>
            <tbody>
              {filteredEvents.map((evt) => (
                <tr key={evt.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                  <td style={{ padding: "14px 20px", fontFamily: "monospace", fontWeight: 700, color: "#111827" }}>
                    {evt.flagKey}
                  </td>
                  <td style={{ padding: "14px 20px" }}>
                    <span style={{
                      padding: "3px 8px",
                      borderRadius: "4px",
                      fontSize: "11px",
                      fontWeight: 700,
                      background: evt.eventType?.includes("DELETE") ? "#fee2e2" : "#e0e7ff",
                      color: evt.eventType?.includes("DELETE") ? "#b91c1c" : "#3730a3"
                    }}>
                      {evt.eventType}
                    </span>
                  </td>
                  <td style={{ padding: "14px 20px", fontSize: "14px", fontWeight: 700, color: "#4f46e5" }}>
                    {evt.count}
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

export default Analytics;
