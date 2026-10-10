export interface SdkKeyMetadataResponse {
  id: number;
  name: string;
  environment: string;
  keyPrefix: string;
  active: boolean;
  createdAt: string;
  revokedAt?: string | null;
  createdBy: string;
}

export interface SdkKeyCreatedResponse {
  id: number;
  name: string;
  environment: string;
  keyPrefix: string;
  active: boolean;
  createdAt: string;
  createdBy: string;
  rawKey: string;
}

export interface CreateSdkKeyRequest {
  name: string;
  environment: string;
}
