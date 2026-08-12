export interface Notification {
  id: number;
  recipient: string;
  subject: string;
  message: string;
  type: "EMAIL" | "SMS" | "PUSH" | string;
  status: "PENDING" | "SENT" | "FAILED" | string;
  createdAt: string;
  sentAt?: string | null;
}

export interface NotificationRequest {
  recipient: string;
  subject: string;
  message: string;
  type?: string;
}
