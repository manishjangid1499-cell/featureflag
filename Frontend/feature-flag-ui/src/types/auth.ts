export type UserRole = "OWNER" | "ADMIN" | "DEVELOPER" | "VIEWER";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  role: UserRole;
}

export interface AuthUser {
  name?: string | null;
  email: string;
  role: UserRole;
  token: string;
}

export interface ProfileResponse {
  name: string | null;
  email: string;
  role: UserRole;
}

export interface MemberResponse {
  id: number;
  name: string | null;
  email: string;
  role: UserRole;
  enabled: boolean;
}

export type InvitationStatus = "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";

export interface InviteMemberRequest {
  name?: string;
  email: string;
  role: UserRole;
}

export interface InvitationResponse {
  id: number;
  email: string;
  fullName: string | null;
  invitedRole: UserRole;
  invitedByUserId: number | null;
  invitedByEmail: string | null;
  invitedByName: string | null;
  status: InvitationStatus;
  expiresAt: string;
  createdAt: string;
  acceptedAt: string | null;
  emailDeliveryConfirmed?: boolean;
}

export interface ValidateInvitationResponse {
  valid: boolean;
  email: string | null;
  fullName: string | null;
  role: UserRole | null;
  invitedByName: string | null;
  errorMessage: string | null;
}

export interface AcceptInvitationRequest {
  token: string;
  password: string;
  confirmPassword: string;
}
