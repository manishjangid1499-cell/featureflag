import api from "./axios";
import type { Notification, NotificationRequest } from "../types/notification";
import { DEFAULT_PAGE_SIZE, type PageResponse } from "../types/page";

export const getAllNotifications = async (
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<Notification>> => {
  const response = await api.get<PageResponse<Notification>>(
    "/api/notifications",
    { params: { page, size } },
  );
  return response.data;
};

export const getNotificationById = async (id: number): Promise<Notification> => {
  const response = await api.get<Notification>(`/api/notifications/${id}`);
  return response.data;
};

export const getNotificationsByRecipient = async (
  recipient: string,
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PageResponse<Notification>> => {
  const response = await api.get<PageResponse<Notification>>(
    `/api/notifications/recipient/${encodeURIComponent(recipient)}`,
    { params: { page, size } },
  );
  return response.data;
};

export const createNotification = async (request: NotificationRequest): Promise<Notification> => {
  const response = await api.post<Notification>("/api/notifications", request);
  return response.data;
};

export const deleteNotification = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/api/notifications/${id}`);
  return response.data;
};
