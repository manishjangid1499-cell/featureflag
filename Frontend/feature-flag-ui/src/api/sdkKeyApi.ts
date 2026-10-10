import api from "./axios";
import type {
  CreateSdkKeyRequest,
  SdkKeyCreatedResponse,
  SdkKeyMetadataResponse,
} from "../types/sdkKey";
import { DEFAULT_PAGE_SIZE, type PageResponse } from "../types/page";

export const getSdkKeys = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<SdkKeyMetadataResponse>> => {
  const response = await api.get<PageResponse<SdkKeyMetadataResponse>>("/sdk-keys", {
    params: { page, size },
  });
  return response.data;
};

export const createSdkKey = async (
  request: CreateSdkKeyRequest,
): Promise<SdkKeyCreatedResponse> => {
  const response = await api.post<SdkKeyCreatedResponse>("/sdk-keys", request);
  return response.data;
};

export const revokeSdkKey = async (id: number): Promise<SdkKeyMetadataResponse> => {
  const response = await api.post<SdkKeyMetadataResponse>(`/sdk-keys/${id}/revoke`);
  return response.data;
};
