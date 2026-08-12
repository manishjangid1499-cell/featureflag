import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../context/AuthContext";
import { getAllNotifications, createNotification, deleteNotification } from "../api/notificationApi";
import type { Notification, NotificationRequest } from "../types/notification";

export function Notifications() {
  const { canManageMembers } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Form
  const [recipient, setRecipient] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);

  const loadNotifications = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getAllNotifications();
      setNotifications(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error("Failed to load notifications:", err);
      const msg = err?.response?.data?.message || err?.message || "Failed to connect to Notification Service.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadNotifications();
  }, []);

  const handleSend = async (e: FormEvent) => {
    e.preventDefault();
    if (!recipient.trim() || !subject.trim() || !message.trim()) return;

    try {
      setSending(true);
      const payload: NotificationRequest = {
        recipient: recipient.trim(),
        subject: subject.trim(),
        message: message.trim(),
        type: "EMAIL",
      };
      const created = await createNotification(payload);
      setNotifications((prev) => [created, ...prev]);
      setIsModalOpen(false);
      setRecipient("");
      setSubject("");
      setMessage("");
    } catch (err: any) {
      alert(err?.response?.data?.message || "Failed to dispatch notification.");
    } finally {
      setSending(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm("Delete this notification record?")) return;
    try {
      await deleteNotification(id);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
    } catch (err: any) {
      alert(err?.response?.data?.message || "Failed to delete notification.");
    }
  };

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: "#111827" }}>
            Notifications & Dispatcher
          </h1>
          <p style={{ margin: "6px 0 0", fontSize: "13px", color: "#6b7280" }}>
            Automated email alerts and dispatch history from the notification pipeline
          </p>
        </div>

        <div style={{ display: "flex", gap: "10px" }}>
          <button
            type="button"
            onClick={loadNotifications}
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

          {canManageMembers && (
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              style={{
                padding: "9px 18px",
                border: "none",
                background: "#4f46e5",
                color: "white",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: 600,
                cursor: "pointer"
              }}
            >
              + Send Notification
            </button>
          )}
        </div>
      </div>

      {error && (
        <div style={{ background: "#fff1f2", border: "1px solid #fecdd3", borderRadius: "10px", padding: "14px 18px", color: "#9f1239", marginBottom: "20px" }}>
          <strong>Service Error:</strong> {error}
        </div>
      )}

      {loading ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center", color: "#6b7280" }}>
          <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>Loading notifications...</p>
        </div>
      ) : notifications.length === 0 ? (
        <div style={{ background: "white", padding: "60px 20px", borderRadius: "14px", border: "1px solid #e5e7eb", textAlign: "center" }}>
          <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#111827" }}>No notifications recorded</h3>
          <p style={{ margin: 0, fontSize: "13px", color: "#6b7280" }}>Notifications will appear as flag mutation emails are dispatched.</p>
        </div>
      ) : (
        <div style={{ background: "white", borderRadius: "14px", border: "1px solid #e5e7eb", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
            <thead>
              <tr style={{ background: "#f9fafb", borderBottom: "1px solid #e5e7eb" }}>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Recipient</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Subject</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Status</th>
                <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase" }}>Dispatched At</th>
                {canManageMembers && <th style={{ padding: "12px 20px", fontSize: "11px", fontWeight: 700, color: "#6b7280", textTransform: "uppercase", textAlign: "right" }}>Action</th>}
              </tr>
            </thead>
            <tbody>
              {notifications.map((n) => (
                <tr key={n.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                  <td style={{ padding: "14px 20px", fontWeight: 600, color: "#111827", fontSize: "13px" }}>
                    {n.recipient}
                  </td>
                  <td style={{ padding: "14px 20px" }}>
                    <div style={{ fontWeight: 600, color: "#111827", fontSize: "13px" }}>{n.subject}</div>
                    <div style={{ fontSize: "12px", color: "#6b7280", marginTop: "2px" }}>{n.message}</div>
                  </td>
                  <td style={{ padding: "14px 20px" }}>
                    <span style={{
                      padding: "3px 9px",
                      borderRadius: "12px",
                      fontSize: "11px",
                      fontWeight: 700,
                      background: n.status === "SENT" ? "#dcfce7" : n.status === "FAILED" ? "#fee2e2" : "#fef3c7",
                      color: n.status === "SENT" ? "#15803d" : n.status === "FAILED" ? "#b91c1c" : "#92400e"
                    }}>
                      {n.status}
                    </span>
                  </td>
                  <td style={{ padding: "14px 20px", fontSize: "12px", color: "#6b7280" }}>
                    {n.createdAt ? new Date(n.createdAt).toLocaleString() : "Just now"}
                  </td>
                  {canManageMembers && (
                    <td style={{ padding: "14px 20px", textAlign: "right" }}>
                      <button
                        type="button"
                        onClick={() => handleDelete(n.id)}
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

      {/* SEND MODAL */}
      {isModalOpen && (
        <div style={{
          position: "fixed",
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: "rgba(17, 24, 39, 0.6)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 50,
          padding: "20px"
        }}>
          <div style={{ background: "white", borderRadius: "14px", width: "100%", maxWidth: "500px", padding: "24px" }}>
            <h3 style={{ margin: "0 0 16px 0", fontSize: "18px", color: "#111827", fontWeight: 700 }}>
              Dispatch Email Notification
            </h3>
            <form onSubmit={handleSend}>
              <div style={{ marginBottom: "14px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>Recipient Email *</label>
                <input
                  type="email"
                  value={recipient}
                  onChange={(e) => setRecipient(e.target.value)}
                  required
                  placeholder="e.g. dev-team@corp.io"
                  style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
                />
              </div>

              <div style={{ marginBottom: "14px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>Subject *</label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  required
                  placeholder="e.g. Production Release Alert"
                  style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px" }}
                />
              </div>

              <div style={{ marginBottom: "18px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#374151", marginBottom: "6px" }}>Message Content *</label>
                <textarea
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  required
                  rows={3}
                  placeholder="Enter notification message..."
                  style={{ width: "100%", padding: "9px 12px", border: "1px solid #d1d5db", borderRadius: "7px", fontSize: "13px", resize: "vertical" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  style={{ padding: "8px 16px", border: "1px solid #d1d5db", background: "white", borderRadius: "7px", fontSize: "13px", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={sending}
                  style={{ padding: "8px 18px", border: "none", background: "#4f46e5", color: "white", borderRadius: "7px", fontSize: "13px", fontWeight: 600, cursor: sending ? "not-allowed" : "pointer" }}
                >
                  {sending ? "Sending..." : "Dispatch Email"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default Notifications;
