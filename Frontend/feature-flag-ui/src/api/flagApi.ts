import api from "./axios";
import type { FeatureFlag, FlagRequest, FlagEvaluationResponse } from "../types/featureFlag";

export const getAllFlags = async (): Promise<FeatureFlag[]> => {
  const response = await api.get<FeatureFlag[]>("/flags");
  return response.data;
};

export const getFlagById = async (id: number): Promise<FeatureFlag> => {
  const response = await api.get<FeatureFlag>(`/flags/id/${id}`);
  return response.data;
};

export const getFlagByKey = async (
  key: string,
  environment: string
): Promise<FeatureFlag> => {
  const params = new URLSearchParams({
    environment,
  });

  const response = await api.get<FeatureFlag>(
    `/flags/${encodeURIComponent(key)}?${params.toString()}`
  );

  return response.data;
};

export const createFlag = async (flag: FlagRequest): Promise<FeatureFlag> => {
  const response = await api.post<FeatureFlag>("/flags", flag);
  return response.data;
};

export const updateFlag = async (id: number, flag: FlagRequest): Promise<FeatureFlag> => {
  const response = await api.put<FeatureFlag>(`/flags/${id}`, flag);
  return response.data;
};

export const toggleFlag = async (id: number): Promise<FeatureFlag> => {
  const response = await api.patch<FeatureFlag>(`/flags/${id}/toggle`);
  return response.data;
};

export const deleteFlag = async (id: number): Promise<string> => {
  const response = await api.delete<string>(`/flags/${id}`);
  return response.data;
};

export const evaluateFlag = async (
  flagKey: string,
  userId: string,
  environment: string
): Promise<FlagEvaluationResponse> => {
  const params = new URLSearchParams({
    userId,
    environment,
  });
  const response = await api.get<FlagEvaluationResponse>(
    `/flags/${encodeURIComponent(flagKey)}/evaluate?${params.toString()}`
  );
  return response.data;
};