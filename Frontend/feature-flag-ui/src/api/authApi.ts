import api from "./axios";
import type {
  AcceptInvitationRequest,
  LoginRequest,
  LoginResponse,
  ValidateInvitationResponse
} from "../types/auth";

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  const response = await api.post<LoginResponse>("/auth/login", request);
  return response.data;
};

export const getProfile = async (): Promise<string> => {
  const response = await api.get<string>("/auth/profile");
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
