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

export interface RegisterRequest {
  name?: string;
  email: string;
  password: string;
  role?: UserRole;
}

export interface AuthUser {
  email: string;
  role: UserRole;
  token: string;
}

export interface MemberRequest {
  name?: string;
  email: string;
  password?: string;
  role: UserRole;
}

export interface MemberResponse {
  id: number;
  name?: string;
  email: string;
  role: UserRole;
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
  fullName?: string;
  invitedRole: UserRole;
  invitedByUserId?: number;
  invitedByEmail?: string;
  invitedByName?: string;
  status: InvitationStatus;
  expiresAt: string;
  createdAt: string;
  acceptedAt?: string;
}

export interface ValidateInvitationResponse {
  valid: boolean;
  email?: string;
  fullName?: string;
  role?: UserRole;
  invitedByName?: string;
  errorMessage?: string;
}

export interface AcceptInvitationRequest {
  token: string;
  password: string;
  confirmPassword: string;
}