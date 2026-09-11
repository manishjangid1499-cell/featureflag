import api from "./axios";
import type {
  InviteMemberRequest,
  InvitationResponse,
  MemberResponse,
  UserRole
} from "../types/auth";
import { DEFAULT_PAGE_SIZE, type PageResponse } from "../types/page";

export const getAllMembers = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<MemberResponse>> => {
  const response = await api.get<PageResponse<MemberResponse>>("/members", {
    params: { page, size },
  });
  return response.data;
};

export const inviteMember = async (request: InviteMemberRequest): Promise<InvitationResponse> => {
  const response = await api.post<InvitationResponse>("/members/invite", request);
  return response.data;
};

export const getAllInvitations = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<InvitationResponse>> => {
  const response = await api.get<PageResponse<InvitationResponse>>(
    "/members/invitations",
    { params: { page, size } },
  );
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

export const updateMemberRole = async (id: number, role: UserRole): Promise<MemberResponse> => {
  const response = await api.patch<MemberResponse>(`/members/${id}/role?role=${encodeURIComponent(role)}`);
  return response.data;
};

export const updateMemberStatus = async (
  id: number,
  enabled: boolean,
): Promise<MemberResponse> => {
  const response = await api.patch<MemberResponse>(`/members/${id}/status`, {
    enabled,
  });
  return response.data;
};

export const deleteMember = async (id: number): Promise<void> => {
  await api.delete(`/members/${id}`);
};
