export interface Notification {
  id: number;
  recipient: string;
  creatorEmail: string | null;
  subject: string;
  message: string;
  type: string;
  status: "PENDING" | "RETRY" | "PROCESSING" | "SENT" | "FAILED" | "DEAD";
  createdAt: string;
  sentAt: string | null;
}

export interface NotificationRequest {
  recipient: string;
  subject: string;
  message: string;
  type: "EMAIL";
}
