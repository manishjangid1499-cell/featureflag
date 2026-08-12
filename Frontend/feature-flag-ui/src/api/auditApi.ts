import api from "./axios";
import type { AuditLog } from "../types/audit";

export const getAllAuditLogs = async (): Promise<AuditLog[]> => {
  const response = await api.get<AuditLog[]>("/audit");
  return response.data;
};

export const getAuditLogsByFlagKey = async (flagKey: string): Promise<AuditLog[]> => {
  const response = await api.get<AuditLog[]>(`/audit/${encodeURIComponent(flagKey)}`);
  return response.data;
};
