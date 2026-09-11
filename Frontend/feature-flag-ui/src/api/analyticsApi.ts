import api from "./axios";
import type { AnalyticsEvent } from "../types/analytics";
import { DEFAULT_PAGE_SIZE, type PageResponse } from "../types/page";

export const getAllAnalytics = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<AnalyticsEvent>> => {
  const response = await api.get<PageResponse<AnalyticsEvent>>("/analytics", {
    params: { page, size },
  });
  return response.data;
};

export const deleteAnalytics = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/analytics/${id}`);
  return response.data;
};
