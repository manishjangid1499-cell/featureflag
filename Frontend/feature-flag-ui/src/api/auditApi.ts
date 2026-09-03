import api from "./axios";
import type { AuditLog } from "../types/audit";
import type { PageResponse } from "../types/page";

export const getAllAuditLogs = async (): Promise<AuditLog[]> => {
  const response = await api.get<PageResponse<AuditLog>>("/audit?page=0&size=100");
  return response.data.content;
};

export const getAuditLogsByFlagKey = async (flagKey: string): Promise<AuditLog[]> => {
  const response = await api.get<PageResponse<AuditLog>>(
    `/audit/${encodeURIComponent(flagKey)}?page=0&size=100`
  );
  return response.data.content;
};
