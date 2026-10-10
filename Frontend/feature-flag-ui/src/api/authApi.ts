import api from "./axios";
import type {
  AcceptInvitationRequest,
  ChangePasswordRequest,
  ChangePasswordResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  LoginRequest,
  LoginResponse,
  ProfileResponse,
  ResetPasswordRequest,
  ResetPasswordResponse,
  ValidateInvitationResponse
} from "../types/auth";

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  const response = await api.post<LoginResponse>("/auth/login", request);
  return response.data;
};

export const getProfile = async (): Promise<ProfileResponse> => {
  const response = await api.get<ProfileResponse>("/auth/profile");
  return response.data;
};

export const validateInvitation = async (token: string): Promise<ValidateInvitationResponse> => {
  const response = await api.get<ValidateInvitationResponse>(`/auth/invitations/validate?token=${encodeURIComponent(token)}`);
  return response.data;
};

export const acceptInvitation = async (request: AcceptInvitationRequest): Promise<string> => {
  const response = await api.post<string>("/auth/invitations/accept", request);
  return response.data;
};

export const changePassword = async (request: ChangePasswordRequest): Promise<ChangePasswordResponse> => {
  const response = await api.post<ChangePasswordResponse>("/auth/change-password", request);
  return response.data;
};

export const forgotPassword = async (request: ForgotPasswordRequest): Promise<ForgotPasswordResponse> => {
  const response = await api.post<ForgotPasswordResponse>("/auth/forgot-password", request);
  return response.data;
};

export const resetPassword = async (request: ResetPasswordRequest): Promise<ResetPasswordResponse> => {
  const response = await api.post<ResetPasswordResponse>("/auth/reset-password", request);
  return response.data;
};
