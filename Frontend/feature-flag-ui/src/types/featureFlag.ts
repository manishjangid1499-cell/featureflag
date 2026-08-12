export interface FeatureFlag {
  id: number
  name: string
  flagKey: string
  enabled: boolean
  description: string
  environment: string
  rolloutPercentage: number
  startDate: string | null
  endDate: string | null
  targetUsers: string[]
}

export interface FlagRequest {
  name: string
  flagKey: string
  enabled: boolean
  description: string
  environment: string
  rolloutPercentage: number
  startDate: string | null
  endDate: string | null
  targetUsers: string[]
}

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