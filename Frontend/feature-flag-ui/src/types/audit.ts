export interface AuditLog {
  id: number;
  eventId: string | null;
  eventType: string;
  flagKey: string;
  environment: string | null;
  timestamp: string;
  sourceService: string | null;
  actor: string | null;
  beforeState: string | null;
  afterState: string | null;
  occurredAt: string | null;
}
