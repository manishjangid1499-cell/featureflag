import type { InvitationResponse } from "../types/auth";

export function invitationDeliveryFeedback(
  invitation: Pick<InvitationResponse, "email" | "emailDeliveryConfirmed">,
  action: "created" | "renewed",
) {
  if (invitation.emailDeliveryConfirmed === true) {
    return { warning: false, message: `Invitation sent successfully to ${invitation.email}.` };
  }
  return {
    warning: true,
    message: `Invitation ${action}, but email delivery could not be confirmed. Check mail configuration and use Resend.`,
  };
}
