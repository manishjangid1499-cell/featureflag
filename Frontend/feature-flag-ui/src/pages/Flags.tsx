import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import FlagStats from "../components/flags/FlagStats";
import FlagTable from "../components/flags/FlagTable";
import CreateFlagModal from "../components/flags/CreateFlagModal";
import EditFlagModal from "../components/flags/EditFlagModal";
import DeleteFlagModal from "../components/flags/DeleteFlagModal";
import FlagEvaluationModal from "../components/flags/FlagEvaluationModal";
import { getAllFlags, toggleFlag } from "../api/flagApi";
import type { FeatureFlag } from "../types/featureFlag";

export function Flags() {
  const { canManageFlags } = useAuth();

  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [togglingId, setTogglingId] = useState<number | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedEnv, setSelectedEnv] = useState("ALL");
  const [selectedStatus, setSelectedStatus] = useState("ALL");

  // Modals
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingFlag, setEditingFlag] = useState<FeatureFlag | null>(null);
  const [deletingFlag, setDeletingFlag] = useState<FeatureFlag | null>(null);
  const [evaluatingFlag, setEvaluatingFlag] = useState<FeatureFlag | null>(null);

  const loadFlags = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getAllFlags();
      setFlags(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error("Failed to load flags:", err);
      const msg = err?.response?.data?.message || err?.message || "Unable to connect to FeatureFlag service.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFlags();
  }, []);

  const handleToggle = async (id: number) => {
    try {
      setTogglingId(id);
      const updated = await toggleFlag(id);
      setFlags((current) =>
        current.map((flag) => (flag.id === id ? updated : flag))
      );
    } catch (err: any) {
      console.error("Failed to toggle flag:", err);
      alert(err?.response?.data?.message || "Failed to toggle feature flag.");
    } finally {
      setTogglingId(null);
    }
  };

  const handleCreateSuccess = (newFlag: FeatureFlag) => {
    setFlags((prev) => [newFlag, ...prev]);
  };

  const handleEditSuccess = (updatedFlag: FeatureFlag) => {
    setFlags((prev) =>
      prev.map((f) => (f.id === updatedFlag.id ? updatedFlag : f))
    );
  };

  const handleDeleteSuccess = (deletedId: number) => {
    setFlags((prev) => prev.filter((f) => f.id !== deletedId));
  };

  // Metrics
  const total = flags.length;
  const enabled = flags.filter((f) => Boolean(f?.enabled)).length;
  const disabled = total - enabled;
  const prodFlags = flags.filter((f) => f?.environment?.toUpperCase() === "PROD");
  const avgRollout =
    total > 0
      ? Math.round(flags.reduce((acc, f) => acc + (f?.rolloutPercentage || 0), 0) / total)
      : 0;

  // Filtered List with safe null checks
  const filteredFlags = flags.filter((flag) => {
    if (!flag) return false;
    const name = (flag.name || "").toLowerCase();
    const key = (flag.flagKey || "").toLowerCase();
    const env = (flag.environment || "").toUpperCase();
    const search = searchQuery.toLowerCase();

    const matchesSearch = name.includes(search) || key.includes(search);

    const matchesEnv =
      selectedEnv === "ALL" ||
      env === selectedEnv ||
      (selectedEnv === "DEV" && (!env || env.startsWith("DEV")));

    const matchesStatus =
      selectedStatus === "ALL" ||
      (selectedStatus === "ENABLED" && Boolean(flag.enabled)) ||
      (selectedStatus === "DISABLED" && !Boolean(flag.enabled));

    return matchesSearch && matchesEnv && matchesStatus;
  });

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      {/* PAGE HEADER */}
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        marginBottom: "24px",
        flexWrap: "wrap",
        gap: "16px"
      }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Feature Flags
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Control runtime release toggles, canary deployments, and targeted rollouts
          </p>
        </div>

        <div style={{ display: "flex", gap: "10px" }}>
          <button
            type="button"
            onClick={loadFlags}
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

          {canManageFlags && (
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
                boxShadow: "0 2px 4px rgba(79, 70, 229, 0.25)"
              }}
            >
              + Create Flag
            </button>
          )}
        </div>
      </div>

      {/* STATS OVERVIEW */}
      <FlagStats
        total={total}
        enabled={enabled}
        disabled={disabled}
        prodCount={prodFlags.length}
        avgRollout={avgRollout}
      />

      {/* FILTER & SEARCH BAR */}
      <div style={{
        background: "white",
        borderRadius: "12px",
        border: "1px solid #e5e7eb",
        padding: "16px 20px",
        marginBottom: "20px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "14px"
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1, minWidth: "260px" }}>
          <input
            type="text"
            placeholder="Search by flag name or key..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: "100%",
              maxWidth: "360px",
              padding: "9px 14px",
              border: "1px solid #d1d5db",
              borderRadius: "7px",
              fontSize: "13px"
            }}
          />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>Env:</span>
            <select
              value={selectedEnv}
              onChange={(e) => setSelectedEnv(e.target.value)}
              style={{
                padding: "8px 12px",
                border: "1px solid #d1d5db",
                borderRadius: "7px",
                fontSize: "12px",
                background: "white"
              }}
            >
              <option value="ALL">All Environments</option>
              <option value="DEV">DEV</option>
              <option value="QA">QA</option>
              <option value="STAGING">STAGING</option>
              <option value="PROD">PROD</option>
            </select>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: 600 }}>Status:</span>
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              style={{
                padding: "8px 12px",
                border: "1px solid #d1d5db",
                borderRadius: "7px",
                fontSize: "12px",
                background: "white"
              }}
            >
              <option value="ALL">All Statuses</option>
              <option value="ENABLED">Enabled Only</option>
              <option value="DISABLED">Disabled Only</option>
            </select>
          </div>
        </div>
      </div>

      {/* ERROR STATE */}
      {error && (
        <div style={{
          background: "#fff1f2",
          border: "1px solid #fecdd3",
          borderRadius: "10px",
          padding: "16px 20px",
          color: "#9f1239",
          marginBottom: "20px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center"
        }}>
          <div>
            <strong>Service Connection Error:</strong> {error}
          </div>
          <button
            type="button"
            onClick={loadFlags}
            style={{
              padding: "6px 14px",
              background: "#be123c",
              color: "white",
              border: "none",
              borderRadius: "6px",
              fontSize: "12px",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            Retry
          </button>
        </div>
      )}

      {/* LOADING STATE */}
      {loading ? (
        <div style={{
          background: "white",
          padding: "60px 20px",
          borderRadius: "14px",
          border: "1px solid #e5e7eb",
          textAlign: "center",
          color: "#6b7280"
        }}>
          <div style={{
            width: "36px",
            height: "36px",
            border: "3px solid #e5e7eb",
            borderTopColor: "#4f46e5",
            borderRadius: "50%",
            animation: "spin 1s linear infinite",
            margin: "0 auto 14px auto"
          }} />
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading feature flags from Redis/MySQL...</p>
        </div>
      ) : (
        <FlagTable
          flags={filteredFlags}
          onToggle={handleToggle}
          onEdit={(flag) => setEditingFlag(flag)}
          onDelete={(flag) => setDeletingFlag(flag)}
          onEvaluate={(flag) => setEvaluatingFlag(flag)}
          togglingId={togglingId}
        />
      )}

      {/* MODALS */}
      <CreateFlagModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSuccess={handleCreateSuccess}
      />

      <EditFlagModal
        flag={editingFlag}
        isOpen={editingFlag !== null}
        onClose={() => setEditingFlag(null)}
        onSuccess={handleEditSuccess}
      />

      <DeleteFlagModal
        flag={deletingFlag}
        isOpen={deletingFlag !== null}
        onClose={() => setDeletingFlag(null)}
        onSuccess={handleDeleteSuccess}
      />

      <FlagEvaluationModal
        flag={evaluatingFlag}
        isOpen={evaluatingFlag !== null}
        onClose={() => setEvaluatingFlag(null)}
      />
    </div>
  );
}

export default Flags;