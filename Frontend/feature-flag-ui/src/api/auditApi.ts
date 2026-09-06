import api from "./axios";
import type { AuditLog } from "../types/audit";
import { DEFAULT_PAGE_SIZE, type PageResponse } from "../types/page";

export const getAllAuditLogs = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<AuditLog>> => {
  const response = await api.get<PageResponse<AuditLog>>("/audit", {
    params: { page, size },
  });
  return response.data;
};

export const getAuditLogsByFlagKey = async (
  flagKey: string,
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<AuditLog>> => {
  const response = await api.get<PageResponse<AuditLog>>(
    `/audit/${encodeURIComponent(flagKey)}`,
    { params: { page, size } },
  );
  return response.data;
};
