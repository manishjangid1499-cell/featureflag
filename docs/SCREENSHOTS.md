# Application Screenshots

[Project overview](../README.md)

Screenshots illustrate the management workflows; the running deployment may contain newer UI changes.

### Feature Flag Management

<p align="center">
  <img src="images/feature-flags.png" alt="Feature flag management" width="100%" />
</p>

The control plane supports environment-specific flag lifecycle management, filtering, rollout percentages, targeting rules, and preview actions (without recording runtime evaluation telemetry).

### Create and Configure a Flag

<p align="center">
  <img src="images/create-feature-flag.png" alt="Create feature flag dialog" width="90%" />
</p>

Flag creation supports environment selection, initial state, rollout percentage, optional schedules, descriptions, and targeted subject IDs.

### Member and Role Administration

<p align="center">
  <img src="images/member-management.png" alt="Member management" width="100%" />
</p>

The platform uses `OWNER`, `ADMIN`, `DEVELOPER`, and `VIEWER` roles with backend-enforced authorization boundaries.

### Invitation Workflow

<p align="center">
  <img src="images/invite-member.png" alt="Invite organization member" width="90%" />
</p>

Invitations allow administrators to onboard members without creating passwords on their behalf. Invitees choose their password after accepting the invitation.

### Analytics

<p align="center">
  <img src="images/analytics.png" alt="Feature flag analytics" width="100%" />
</p>

Analytics aggregates lifecycle mutations and runtime evaluation telemetry consumed from Kafka.

### Audit Logs

<p align="center">
  <img src="images/audit-logs.png" alt="Feature flag audit logs" width="100%" />
</p>

Audit records provide a chronological history of feature-flag lifecycle activity.

### Notifications

<p align="center">
  <img src="images/notifications.png" alt="Notification history" width="100%" />
</p>

Notification history exposes provider acceptance and retry state; SENT does not prove inbox delivery for invitation and lifecycle email processing.
