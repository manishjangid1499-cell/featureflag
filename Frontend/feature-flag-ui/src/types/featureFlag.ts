export interface FeatureFlag {
  id: number
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

export type FlagRequest = Omit<FeatureFlag, "id">;

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
