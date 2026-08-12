import api from "./axios";
import type { AnalyticsEvent } from "../types/analytics";

export const getAllAnalytics = async (): Promise<AnalyticsEvent[]> => {
  const response = await api.get<AnalyticsEvent[]>("/analytics");
  return response.data;
};

export const getAnalyticsByFlagKey = async (flagKey: string): Promise<AnalyticsEvent[]> => {
  const response = await api.get<AnalyticsEvent[]>(`/analytics/${encodeURIComponent(flagKey)}`);
  return response.data;
};

export const deleteAnalytics = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/analytics/${id}`);
  return response.data;
};
