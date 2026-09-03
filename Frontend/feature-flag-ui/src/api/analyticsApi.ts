import api from "./axios";
import type { AnalyticsEvent } from "../types/analytics";
import type { PageResponse } from "../types/page";

export const getAllAnalytics = async (): Promise<AnalyticsEvent[]> => {
  const response = await api.get<PageResponse<AnalyticsEvent>>(
    "/analytics?page=0&size=100"
  );
  return response.data.content;
};

export const getAnalyticsByFlagKey = async (flagKey: string): Promise<AnalyticsEvent[]> => {
  const response = await api.get<PageResponse<AnalyticsEvent>>(
    `/analytics/${encodeURIComponent(flagKey)}?page=0&size=100`
  );
  return response.data.content;
};

export const deleteAnalytics = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/analytics/${id}`);
  return response.data;
};
