import axios from "axios";
import type { ProblemDetail } from "../types/problem";

export const getApiStatus = (error: unknown): number | undefined =>
  axios.isAxiosError(error) ? error.response?.status : undefined;

const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const text = (value: unknown, limit: number): string | undefined =>
  typeof value === "string" && value.trim() ? value.trim().slice(0, limit) : undefined;

export const getApiProblem = (error: unknown): ProblemDetail | undefined => {
  if (!axios.isAxiosError(error) || !record(error.response?.data)) return undefined;
  const data = error.response.data;
  const errors: Record<string, string> = {};
  if (record(data.errors)) {
    for (const [field, value] of Object.entries(data.errors).slice(0, 32)) {
      const message = text(value, 160);
      if (message && /^[A-Za-z][A-Za-z0-9_.[\]-]{0,63}$/.test(field)) {
        Object.defineProperty(errors, field, { value: message, enumerable: true });
      }
    }
  }
  const correlationId = typeof data.correlationId === "string"
    && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(data.correlationId)
    ? data.correlationId : undefined;
  return {
    status: error.response?.status,
    title: text(data.title, 128),
    detail: text(data.detail, 512),
    errors,
    correlationId,
  };
};

export const getApiErrorMessage = (
  error: unknown,
  fallback: string,
): string => {
  const problem = getApiProblem(error);
  const fields = Object.entries(problem?.errors ?? {});
  const message = fields.length > 0
    ? fields
      .map(([field, message]) => `${field}: ${message}`)
      .join("; ").slice(0, 1024)
    : problem?.detail || problem?.title || fallback;
  return problem?.correlationId
    ? `${message} (Reference: ${problem.correlationId})` : message;
};
