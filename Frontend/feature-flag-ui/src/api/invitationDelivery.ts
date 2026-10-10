import type { InvitationResponse } from "../types/auth";

export function invitationDeliveryFeedback(
  invitation: Pick<InvitationResponse, "email" | "emailDeliveryConfirmed">,
  action: "created" | "renewed",
) {
  if (invitation.emailDeliveryConfirmed === true) {
    const actionText = action === "renewed" ? "Invitation renewed" : "Invitation created";
    return {
      warning: false,
      message: `${actionText}. Outbound email dispatch accepted by mail service for ${invitation.email}. Note: Acceptance by mail provider confirms dispatch attempt, not final recipient inbox delivery.`,
    };
  }
  return {
    warning: true,
    message: `Invitation ${action}, but email delivery could not be confirmed. Check mail configuration and use Resend (resending issues a new link and invalidates any previous invitation).`,
  };
}
