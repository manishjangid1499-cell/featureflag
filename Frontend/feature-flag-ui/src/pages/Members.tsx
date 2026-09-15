import { useState, type FormEvent } from "react";
import { useAuth } from "../hooks/useAuth";
import {
  getAllMembers,
  updateMemberRole,
  updateMemberStatus,
  deleteMember,
  inviteMember,
  getAllInvitations,
  resendInvitation,
  revokeInvitation,
} from "../api/memberApi";
import { getApiErrorMessage } from "../api/errors";
import { invitationDeliveryFeedback } from "../api/invitationDelivery";
import { PaginationControls } from "../components/PaginationControls";
import type {
  MemberResponse,
  InvitationResponse,
  InviteMemberRequest,
  UserRole,
} from "../types/auth";
import { usePagedResource } from "../hooks/usePagedResource";

export function Members() {
  const { user, isOwner } = useAuth();
  const { items: members, setItems: setMembers, data: memberPageData,
    setPage: setMemberPage, loading: membersLoading,
    error: membersError, reload: reloadMembers } = usePagedResource<MemberResponse>(
      getAllMembers, "Failed to load members.",
    );
  const { items: invitations, setItems: setInvitations, data: invitationPageData,
    setPage: setInvitationPage, loading: invitationsLoading,
    error: invitationsError, reload: reloadInvitations } = usePagedResource<InvitationResponse>(
      getAllInvitations, "Failed to load invitations.",
    );
  const loading = membersLoading || invitationsLoading;
  const error = membersError || invitationsError;
  const [successMessage, setSuccessMessage] = useState("");
  const [deliveryWarning, setDeliveryWarning] = useState("");
  const [resendingId, setResendingId] = useState<number | null>(null);

  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<UserRole>("DEVELOPER");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  const loadData = async () => {
    await Promise.all([reloadMembers(), reloadInvitations()]);
  };

  const handleInvite = async (e: FormEvent) => {
    e.preventDefault();
    setFormError("");
    setSuccessMessage("");
    setDeliveryWarning("");

    if (!email.trim()) {
      setFormError("Email address is required.");
      return;
    }

    try {
      setSubmitting(true);
      const payload: InviteMemberRequest = {
        name: name.trim() || undefined,
        email: email.trim(),
        role,
      };
      const created = await inviteMember(payload);
      await reloadInvitations(0);
      setIsInviteOpen(false);
      setName("");
      setEmail("");
      setRole("DEVELOPER");
      const feedback = invitationDeliveryFeedback(created, "created");
      setDeliveryWarning(feedback.warning ? feedback.message : "");
      setSuccessMessage(feedback.warning ? "" : feedback.message);
      setTimeout(() => setSuccessMessage(""), 6000);
    } catch (error: unknown) {
      setFormError(getApiErrorMessage(
        error,
        "Failed to send invitation. Please verify permissions.",
      ));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async (invitationId: number) => {
    setSuccessMessage("");
    setDeliveryWarning("");
    setResendingId(invitationId);
    try {
      const renewed = await resendInvitation(invitationId);
      await reloadInvitations();
      const feedback = invitationDeliveryFeedback(renewed, "renewed");
      setDeliveryWarning(feedback.warning ? feedback.message : "");
      setSuccessMessage(feedback.warning ? "" : feedback.message);
      setTimeout(() => setSuccessMessage(""), 5000);
    } catch (error: unknown) {
      alert(getApiErrorMessage(error, "Failed to resend invitation."));
    } finally {
      setResendingId(null);
    }
  };

  const handleRevoke = async (invitationId: number, memberEmail: string) => {
    if (!window.confirm(`Revoke pending invitation for ${memberEmail}?`)) return;
    try {
      await revokeInvitation(invitationId);
      setInvitations((prev) =>
        prev.map((i) => (i.id === invitationId ? { ...i, status: "REVOKED" } : i))
      );
      setSuccessMessage(`Invitation for ${memberEmail} has been revoked.`);
      setTimeout(() => setSuccessMessage(""), 5000);
    } catch (error: unknown) {
      alert(getApiErrorMessage(error, "Failed to revoke invitation."));
    }
  };

  const handleRoleChange = async (memberId: number, newRole: UserRole) => {
    try {
      const updated = await updateMemberRole(memberId, newRole);
      setMembers((prev) => prev.map((m) => (m.id === memberId ? updated : m)));
      setSuccessMessage(`Role updated for ${updated.email}.`);
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (error: unknown) {
      alert(getApiErrorMessage(error, "Failed to update member role."));
    }
  };

  const handleStatusChange = async (
    memberId: number,
    memberEmail: string,
    enabled: boolean,
  ) => {
    const action = enabled ? "enable" : "disable";
    if (!window.confirm(`${action[0].toUpperCase()}${action.slice(1)} ${memberEmail}?`)) {
      return;
    }

    try {
      const updated = await updateMemberStatus(memberId, enabled);
      setMembers((previous) =>
        previous.map((member) => (member.id === memberId ? updated : member)),
      );
      setSuccessMessage(
        `${updated.email} is now ${updated.enabled ? "enabled" : "disabled"}.`,
      );
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (error: unknown) {
      alert(getApiErrorMessage(error, `Failed to ${action} member.`));
    }
  };

  const handleDelete = async (memberId: number, memberEmail: string) => {
    if (memberEmail === user?.email) {
      alert("You cannot remove your own account.");
      return;
    }
    if (!window.confirm(`Are you sure you want to remove member ${memberEmail}?`))
      return;

    try {
      await deleteMember(memberId);
      await reloadMembers();
      setSuccessMessage(`Member ${memberEmail} removed from platform.`);
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (error: unknown) {
      alert(getApiErrorMessage(error, "Failed to remove member."));
    }
  };

  return (
    <div style={{ maxWidth: "1600px", margin: "0 auto" }}>
      {/* HEADER */}
      <div
        className="page-header"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: "24px",
        }}
      >
        <div>
          <h1
            style={{
              margin: 0,
              fontSize: "24px",
              fontWeight: 800,
              color: "#111827",
            }}
          >
            Member Management
          </h1>
          <p
            style={{
              margin: "6px 0 0",
              fontSize: "13px",
              color: "#6b7280",
            }}
          >
            Invite teammates, manage role authorizations, and track onboarding invitations
          </p>
        </div>

        <div className="responsive-actions" style={{ display: "flex", gap: "10px" }}>
          <button
            type="button"
            onClick={() => void loadData()}
            style={{
              padding: "9px 15px",
              border: "1px solid #d1d5db",
              background: "white",
              color: "#374151",
              borderRadius: "8px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            ↻ Refresh
          </button>

          <button
            type="button"
            onClick={() => {
              setIsInviteOpen(true);
              setFormError("");
            }}
            style={{
              padding: "9px 18px",
              border: "none",
              background: "#4f46e5",
              color: "white",
              borderRadius: "8px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer",
              boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)",
            }}
          >
            + Invite Member
          </button>
        </div>
      </div>

      {deliveryWarning && (
        <div role="alert" style={{ background: "#fffbeb", border: "1px solid #fcd34d",
          borderRadius: "10px", padding: "12px 18px", color: "#92400e", marginBottom: "20px" }}>
          {deliveryWarning}
        </div>
      )}

      {successMessage && (
        <div
          style={{
            background: "#f0fdf4",
            border: "1px solid #bbf7d0",
            borderRadius: "10px",
            padding: "12px 18px",
            color: "#166534",
            fontSize: "13px",
            fontWeight: 600,
            marginBottom: "20px",
          }}
        >
          ✓ {successMessage}
        </div>
      )}

      {error && (
        <div
          style={{
            background: "#fff1f2",
            border: "1px solid #fecdd3",
            borderRadius: "10px",
            padding: "14px 18px",
            color: "#9f1239",
            marginBottom: "20px",
          }}
        >
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* ACTIVE MEMBERS SECTION */}
      <div style={{ marginBottom: "36px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "14px" }}>
          <h2 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#1e293b" }}>
            Platform Members ({memberPageData.totalElements})
          </h2>
        </div>

        {loading ? (
          <div
            style={{
              background: "white",
              padding: "50px 20px",
              borderRadius: "14px",
              border: "1px solid #e5e7eb",
              textAlign: "center",
              color: "#6b7280",
            }}
          >
            <p style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>
              Loading platform members...
            </p>
          </div>
        ) : (
          <div
            className="table-scroll member-table-scroll"
            style={{
              background: "white",
              borderRadius: "14px",
              border: "1px solid #e5e7eb",
              overflow: "hidden",
            }}
          >
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                textAlign: "left",
              }}
            >
              <thead>
                <tr
                  style={{
                    background: "#f9fafb",
                    borderBottom: "1px solid #e5e7eb",
                  }}
                >
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Member
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Role
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Access Tier
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                      textAlign: "right",
                    }}
                  >
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {members.map((m) => {
                  const isSelf = m.email === user?.email;
                  const isTargetOwner = m.role === "OWNER";
                  const canModifyTarget =
                    !isSelf &&
                    !isTargetOwner &&
                    (isOwner || m.role === "DEVELOPER" || m.role === "VIEWER");

                  return (
                    <tr key={m.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                      <td style={{ padding: "16px 20px" }}>
                        <div
                          style={{
                            fontWeight: 700,
                            fontSize: "13px",
                            color: "#111827",
                          }}
                        >
                          {m.name || m.email.split("@")[0]}{" "}
                          {isSelf && (
                            <span style={{ color: "#4f46e5", fontSize: "11px" }}>
                              (You)
                            </span>
                          )}
                        </div>
                        <div
                          style={{
                            fontSize: "12px",
                            color: "#6b7280",
                            marginTop: "2px",
                          }}
                        >
                          {m.email}
                        </div>
                        <div
                          style={{
                            display: "inline-block",
                            marginTop: "6px",
                            padding: "2px 8px",
                            borderRadius: "12px",
                            fontSize: "10px",
                            fontWeight: 700,
                            background: m.enabled ? "#dcfce7" : "#fee2e2",
                            color: m.enabled ? "#15803d" : "#b91c1c",
                          }}
                        >
                          {m.enabled ? "ENABLED" : "DISABLED"}
                        </div>
                      </td>

                      <td style={{ padding: "16px 20px" }}>
                        <span
                          style={{
                            padding: "4px 10px",
                            borderRadius: "14px",
                            fontSize: "11px",
                            fontWeight: 700,
                            background:
                              m.role === "OWNER"
                                ? "#ede9fe"
                                : m.role === "ADMIN"
                                ? "#dbeafe"
                                : m.role === "DEVELOPER"
                                ? "#d1fae5"
                                : "#f3f4f6",
                            color:
                              m.role === "OWNER"
                                ? "#6d28d9"
                                : m.role === "ADMIN"
                                ? "#1d4ed8"
                                : m.role === "DEVELOPER"
                                ? "#047857"
                                : "#4b5563",
                          }}
                        >
                          {m.role}
                        </span>
                      </td>

                      <td
                        style={{
                          padding: "16px 20px",
                          fontSize: "12px",
                          color: "#6b7280",
                        }}
                      >
                        {m.role === "OWNER"
                          ? "Full Organization Control"
                          : m.role === "ADMIN"
                          ? "Platform Management"
                          : m.role === "DEVELOPER"
                          ? "Feature Flag Releases"
                          : "Read-Only Viewer"}
                      </td>

                      <td style={{ padding: "16px 20px", textAlign: "right" }}>
                        {canModifyTarget && (
                          <div
                            style={{
                              display: "inline-flex",
                              gap: "8px",
                              alignItems: "center",
                            }}
                          >
                            <select
                              value={m.role}
                              onChange={(e) =>
                                handleRoleChange(m.id, e.target.value as UserRole)
                              }
                              style={{
                                padding: "5px 10px",
                                border: "1px solid #d1d5db",
                                borderRadius: "6px",
                                fontSize: "12px",
                                background: "white",
                              }}
                            >
                              {isOwner && <option value="ADMIN">ADMIN</option>}
                              <option value="DEVELOPER">DEVELOPER</option>
                              <option value="VIEWER">VIEWER</option>
                            </select>

                            <button
                              type="button"
                              onClick={() =>
                                handleStatusChange(m.id, m.email, !m.enabled)
                              }
                              style={{
                                padding: "5px 10px",
                                border: "1px solid #d1d5db",
                                background: "white",
                                color: "#374151",
                                borderRadius: "6px",
                                fontSize: "12px",
                                cursor: "pointer",
                              }}
                            >
                              {m.enabled ? "Disable" : "Enable"}
                            </button>

                            <button
                              type="button"
                              onClick={() => handleDelete(m.id, m.email)}
                              style={{
                                padding: "5px 10px",
                                border: "1px solid #fee2e2",
                                background: "#fff5f5",
                                color: "#dc2626",
                                borderRadius: "6px",
                                fontSize: "12px",
                                cursor: "pointer",
                              }}
                            >
                              Remove
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <PaginationControls
          page={memberPageData.page}
          totalPages={memberPageData.totalPages}
          totalElements={memberPageData.totalElements}
          disabled={loading}
          onPageChange={setMemberPage}
        />
      </div>

      {/* INVITATIONS SECTION */}
      <div>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "14px" }}>
          <h2 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#1e293b" }}>
            Member Invitations ({invitationPageData.totalElements})
          </h2>
        </div>

        {invitations.length === 0 ? (
          <div
            style={{
              background: "white",
              padding: "36px 20px",
              borderRadius: "14px",
              border: "1px solid #e5e7eb",
              textAlign: "center",
              color: "#6b7280",
            }}
          >
            <p style={{ margin: 0, fontSize: "13px" }}>
              No pending or past invitations found. Click "+ Invite Member" to send an onboarding invitation.
            </p>
          </div>
        ) : (
          <div
            className="table-scroll member-table-scroll"
            style={{
              background: "white",
              borderRadius: "14px",
              border: "1px solid #e5e7eb",
              overflow: "hidden",
            }}
          >
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                textAlign: "left",
              }}
            >
              <thead>
                <tr
                  style={{
                    background: "#f9fafb",
                    borderBottom: "1px solid #e5e7eb",
                  }}
                >
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Invited Recipient
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Assigned Role
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Invited By
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                    }}
                  >
                    Status
                  </th>
                  <th
                    style={{
                      padding: "12px 20px",
                      fontSize: "11px",
                      fontWeight: 700,
                      color: "#6b7280",
                      textTransform: "uppercase",
                      textAlign: "right",
                    }}
                  >
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {invitations.map((inv) => {
                  const isPending = inv.status === "PENDING";

                  return (
                    <tr key={inv.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                      <td style={{ padding: "14px 20px" }}>
                        <div
                          style={{
                            fontWeight: 700,
                            fontSize: "13px",
                            color: "#111827",
                          }}
                        >
                          {inv.fullName || inv.email.split("@")[0]}
                        </div>
                        <div
                          style={{
                            fontSize: "12px",
                            color: "#6b7280",
                            marginTop: "2px",
                          }}
                        >
                          {inv.email}
                        </div>
                      </td>

                      <td style={{ padding: "14px 20px" }}>
                        <span
                          style={{
                            padding: "3px 9px",
                            borderRadius: "12px",
                            fontSize: "11px",
                            fontWeight: 700,
                            background:
                              inv.invitedRole === "ADMIN"
                                ? "#dbeafe"
                                : inv.invitedRole === "DEVELOPER"
                                ? "#d1fae5"
                                : "#f3f4f6",
                            color:
                              inv.invitedRole === "ADMIN"
                                ? "#1d4ed8"
                                : inv.invitedRole === "DEVELOPER"
                                ? "#047857"
                                : "#4b5563",
                          }}
                        >
                          {inv.invitedRole}
                        </span>
                      </td>

                      <td
                        style={{
                          padding: "14px 20px",
                          fontSize: "12px",
                          color: "#475569",
                        }}
                      >
                        {inv.invitedByName || inv.invitedByEmail || "System"}
                      </td>

                      <td style={{ padding: "14px 20px" }}>
                        <span
                          style={{
                            padding: "3px 9px",
                            borderRadius: "12px",
                            fontSize: "11px",
                            fontWeight: 700,
                            background:
                              inv.status === "ACCEPTED"
                                ? "#dcfce7"
                                : inv.status === "PENDING"
                                ? "#fef3c7"
                                : inv.status === "EXPIRED"
                                ? "#f1f5f9"
                                : "#fee2e2",
                            color:
                              inv.status === "ACCEPTED"
                                ? "#15803d"
                                : inv.status === "PENDING"
                                ? "#b45309"
                                : inv.status === "EXPIRED"
                                ? "#64748b"
                                : "#b91c1c",
                          }}
                        >
                          {inv.status}
                        </span>
                      </td>

                      <td style={{ padding: "14px 20px", textAlign: "right" }}>
                        {isPending && (
                          <div
                            style={{
                              display: "inline-flex",
                              gap: "8px",
                              alignItems: "center",
                            }}
                          >
                            <button
                              type="button"
                              onClick={() => handleResend(inv.id)}
                              disabled={resendingId !== null}
                              style={{
                                padding: "4px 9px",
                                border: "1px solid #d1d5db",
                                background: "white",
                                color: "#374151",
                                borderRadius: "6px",
                                fontSize: "11px",
                                fontWeight: 600,
                                cursor: "pointer",
                              }}
                            >
                              {resendingId === inv.id ? "Sending..." : "Resend"}
                            </button>

                            <button
                              type="button"
                              onClick={() => handleRevoke(inv.id, inv.email)}
                              style={{
                                padding: "4px 9px",
                                border: "1px solid #fee2e2",
                                background: "#fff5f5",
                                color: "#dc2626",
                                borderRadius: "6px",
                                fontSize: "11px",
                                fontWeight: 600,
                                cursor: "pointer",
                              }}
                            >
                              Revoke
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <PaginationControls
          page={invitationPageData.page}
          totalPages={invitationPageData.totalPages}
          totalElements={invitationPageData.totalElements}
          disabled={loading}
          onPageChange={setInvitationPage}
        />
      </div>

      {/* INVITE MEMBER MODAL */}
      {isInviteOpen && (
        <div
          className="modal-overlay"
          style={{
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
            padding: "20px",
          }}
        >
          <div
            className="modal-panel modal-panel-padded"
            style={{
              background: "white",
              borderRadius: "16px",
              width: "100%",
              maxWidth: "480px",
              padding: "28px",
              boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)",
            }}
          >
            <h3
              style={{
                margin: "0 0 8px 0",
                fontSize: "18px",
                color: "#111827",
                fontWeight: 800,
              }}
            >
              Invite Organization Member
            </h3>

            <p
              style={{
                margin: "0 0 18px 0",
                fontSize: "12px",
                color: "#6b7280",
                lineHeight: "1.4",
              }}
            >
              An invitation email will be sent to this email address. The member will create their own password.
            </p>

            {formError && (
              <div
                style={{
                  background: "#fef2f2",
                  border: "1px solid #fecaca",
                  color: "#b91c1c",
                  padding: "10px 14px",
                  borderRadius: "8px",
                  fontSize: "12px",
                  marginBottom: "16px",
                }}
              >
                {formError}
              </div>
            )}

            <form onSubmit={handleInvite}>
              <div style={{ marginBottom: "14px" }}>
                <label
                  style={{
                    display: "block",
                    fontSize: "12px",
                    fontWeight: 600,
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Full Name
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Sarah Jenkins"
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

              <div style={{ marginBottom: "14px" }}>
                <label
                  style={{
                    display: "block",
                    fontSize: "12px",
                    fontWeight: 600,
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Email Address *
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  placeholder="e.g. sarah@corp.io"
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

              <div style={{ marginBottom: "24px" }}>
                <label
                  style={{
                    display: "block",
                    fontSize: "12px",
                    fontWeight: 600,
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Role *
                </label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value as UserRole)}
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
                  {isOwner && <option value="ADMIN">ADMIN</option>}
                  <option value="DEVELOPER">DEVELOPER</option>
                  <option value="VIEWER">VIEWER</option>
                </select>
              </div>

              <div
                className="responsive-actions"
                style={{
                  display: "flex",
                  justifyContent: "flex-end",
                  gap: "10px",
                }}
              >
                <button
                  type="button"
                  onClick={() => setIsInviteOpen(false)}
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
                  disabled={submitting}
                  style={{
                    padding: "8px 20px",
                    border: "none",
                    background: "#4f46e5",
                    color: "white",
                    borderRadius: "7px",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: submitting ? "not-allowed" : "pointer",
                    boxShadow: "0 4px 12px rgba(79, 70, 229, 0.25)",
                  }}
                >
                  {submitting ? "Sending..." : "Send Invitation"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default Members;
