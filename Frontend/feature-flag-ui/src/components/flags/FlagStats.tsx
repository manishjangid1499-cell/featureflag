interface Props {
  total: number;
  enabled: number;
  disabled: number;
  prodCount?: number;
  avgRollout?: number;
}

export function FlagStats({ total, enabled, disabled, prodCount = 0, avgRollout = 0 }: Props) {
  return (
    <div style={{
      display: "grid",
      gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
      gap: "16px",
      marginBottom: "24px"
    }}>
      <div style={{
        background: "white",
        borderRadius: "12px",
        padding: "20px",
        border: "1px solid #e5e7eb",
        boxShadow: "0 1px 3px rgba(0,0,0,0.04)"
      }}>
        <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Total Flags
        </span>
        <div style={{ fontSize: "28px", fontWeight: 800, color: "#111827", marginTop: "6px" }}>
          {total}
        </div>
        <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>
          Configured across all environments
        </div>
      </div>

      <div style={{
        background: "white",
        borderRadius: "12px",
        padding: "20px",
        border: "1px solid #e5e7eb",
        boxShadow: "0 1px 3px rgba(0,0,0,0.04)"
      }}>
        <span style={{ fontSize: "12px", color: "#059669", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Enabled Flags
        </span>
        <div style={{ fontSize: "28px", fontWeight: 800, color: "#059669", marginTop: "6px" }}>
          {enabled}
        </div>
        <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>
          Active and serving traffic
        </div>
      </div>

      <div style={{
        background: "white",
        borderRadius: "12px",
        padding: "20px",
        border: "1px solid #e5e7eb",
        boxShadow: "0 1px 3px rgba(0,0,0,0.04)"
      }}>
        <span style={{ fontSize: "12px", color: "#dc2626", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Disabled Flags
        </span>
        <div style={{ fontSize: "28px", fontWeight: 800, color: "#dc2626", marginTop: "6px" }}>
          {disabled}
        </div>
        <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>
          Turned off globally
        </div>
      </div>

      <div style={{
        background: "white",
        borderRadius: "12px",
        padding: "20px",
        border: "1px solid #e5e7eb",
        boxShadow: "0 1px 3px rgba(0,0,0,0.04)"
      }}>
        <span style={{ fontSize: "12px", color: "#4f46e5", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          PROD Environment
        </span>
        <div style={{ fontSize: "28px", fontWeight: 800, color: "#4f46e5", marginTop: "6px" }}>
          {prodCount}
        </div>
        <div style={{ fontSize: "11px", color: "#9ca3af", marginTop: "4px" }}>
          Avg Rollout: {avgRollout}%
        </div>
      </div>
    </div>
  );
}

export default FlagStats;