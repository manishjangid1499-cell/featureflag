import api from "./axios";
import type { Notification, NotificationRequest } from "../types/notification";
import type { PageResponse } from "../types/page";

export const getAllNotifications = async (): Promise<Notification[]> => {
  const response = await api.get<PageResponse<Notification>>(
    "/api/notifications?page=0&size=100"
  );
  return response.data.content;
};

export const getNotificationById = async (id: number): Promise<Notification> => {
  const response = await api.get<Notification>(`/api/notifications/${id}`);
  return response.data;
};

export const getNotificationsByRecipient = async (recipient: string): Promise<Notification[]> => {
  const response = await api.get<PageResponse<Notification>>(
    `/api/notifications/recipient/${encodeURIComponent(recipient)}?page=0&size=100`
  );
  return response.data.content;
};

export const createNotification = async (request: NotificationRequest): Promise<Notification> => {
  const response = await api.post<Notification>("/api/notifications", request);
  return response.data;
};

export const deleteNotification = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/api/notifications/${id}`);
  return response.data;
};
