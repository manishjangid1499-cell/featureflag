import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { getAllAnalytics, deleteAnalytics } from "../api/analyticsApi";
import type { AnalyticsEvent } from "../types/analytics";

export function Analytics() {
  const { canDeleteFlags } = useAuth();
  const [events, setEvents] = useState<AnalyticsEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  const loadAnalytics = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getAllAnalytics();
      setEvents(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error("Failed to load analytics:", err);
      const msg = err?.response?.data?.message || err?.message || "Failed to connect to Analytics Service.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, []);

  const handleDelete = async (id: number) => {
    if (!window.confirm("Are you sure you want to delete this analytics record?")) return;

    try {
      await deleteAnalytics(id);
      setEvents((prev) => prev.filter((e) => e.id !== id));
    } catch (err: any) {
      alert(err?.response?.data?.message || "Failed to delete analytics record.");
    }
  };

  const totalEventCount = events.reduce((sum, e) => sum + (e.count || 0), 0);

  const filteredEvents = events.filter((e) =>
    e.flagKey?.toLowerCase().includes(search.toLowerCase()) ||
    e.eventType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Feature Flag Analytics
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Aggregated lifecycle mutation events and usage telemetry consumed from Kafka
          </p>
        </div>

        <button
          type="button"
          onClick={loadAnalytics}
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

      {/* SUMMARY BANNER */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "16px", marginBottom: "24px" }}>
        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>TRACKED FLAG KEYS</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#111827", marginTop: "4px" }}>
            {new Set(events.map((e) => e.flagKey)).size}
          </div>
        </div>

        <div style={{ background: "white", borderRadius: "12px", padding: "20px", border: "1px solid #e5e7eb" }}>
          <span style={{ fontSize: "12px", color: "#4f46e5", fontWeight: 600 }}>TOTAL EVENT MUTATIONS</span>
          <div style={{ fontSize: "28px", fontWeight: 800, color: "#4f46e5", marginTop: "4px" }}>
            {totalEventCount}
          </div>
        </div>
      </div>

      {/* SEARCH BAR */}
      <div style={{ background: "white", borderRadius: "12px", border: "1px solid #e5e7eb", padding: "14px 20px", marginBottom: "20px" }}>
        <input
          type="text"
          placeholder="Filter by flag key or event type..."
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
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading analytics stream...</p>
        </div>
      ) : filteredEvents.length === 0 ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center" }}>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#111827" }}>No analytics records found</h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>Mutation events will appear as flags are created and modified.</p>
        </div>
      ) : (
        <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb" }}>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Flag Key</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Event Type</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Count</th>
                {canDeleteFlags && <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", textAlign: "right" }}>Action</th>}
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
                  {canDeleteFlags && (
                    <td style={{ padding: "14px 20px", textAlign: "right" }}>
                      <button
                        type="button"
                        onClick={() => handleDelete(evt.id)}
                        style={{
                          padding: "5px 10px",
                          border: "1px solid #fee2e2",
                          background: "#fff5f5",
                          color: "#dc2626",
                          borderRadius: "5px",
                          fontSize: "11px",
                          cursor: "pointer"
                        }}
                      >
                        Delete
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default Analytics;
