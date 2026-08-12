import api from "./axios";
import type {
  InviteMemberRequest,
  InvitationResponse,
  MemberRequest,
  MemberResponse,
  UserRole
} from "../types/auth";

export const getAllMembers = async (): Promise<MemberResponse[]> => {
  const response = await api.get<MemberResponse[]>("/members");
  return response.data;
};

export const getMemberById = async (id: number): Promise<MemberResponse> => {
  const response = await api.get<MemberResponse>(`/members/${id}`);
  return response.data;
};

export const inviteMember = async (request: InviteMemberRequest): Promise<InvitationResponse> => {
  const response = await api.post<InvitationResponse>("/members/invite", request);
  return response.data;
};

export const getAllInvitations = async (): Promise<InvitationResponse[]> => {
  const response = await api.get<InvitationResponse[]>("/members/invitations");
  return response.data;
};

export const resendInvitation = async (id: number): Promise<InvitationResponse> => {
  const response = await api.post<InvitationResponse>(`/members/invitations/${id}/resend`);
  return response.data;
};

export const revokeInvitation = async (id: number): Promise<string> => {
  const response = await api.post<string>(`/members/invitations/${id}/revoke`);
  return response.data;
};

export const createMember = async (request: MemberRequest): Promise<MemberResponse> => {
  const response = await api.post<MemberResponse>("/members", request);
  return response.data;
};

export const updateMemberRole = async (id: number, role: UserRole): Promise<MemberResponse> => {
  const response = await api.patch<MemberResponse>(`/members/${id}/role?role=${encodeURIComponent(role)}`);
  return response.data;
};

export const deleteMember = async (id: number): Promise<void> => {
  await api.delete(`/members/${id}`);
};
