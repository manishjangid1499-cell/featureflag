export interface FeatureFlag {
  id: number
  version: number
  name: string
  flagKey: string
  enabled: boolean
  description: string | null
  environment: string
  rolloutPercentage: number
  startDate: string | null
  endDate: string | null
  targetUsers: string[]
}

export type FlagRequest = Omit<FeatureFlag, "id" | "version">;
export type FlagUpdateRequest = FlagRequest & { expectedVersion: number };

export interface FlagEvaluationResponse {
  flagKey: string
  environment: string
  enabled: boolean
  targetedUser: boolean
  rolloutPercentage: number
  startDate: string | null
  endDate: string | null
  withinSchedule: boolean
}
