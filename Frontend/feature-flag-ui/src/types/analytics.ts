export interface AnalyticsEvent {
  id: number;
  flagKey: string;
  environment: string;
  eventType: string;
  count: number;
}
