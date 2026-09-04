import type { UserRole } from "../types/auth";

const PUBLIC_AUTH_PATHS = new Set([
  "/auth/login",
  "/auth/invitations/validate",
  "/auth/invitations/accept",
]);

export type RouteDecision = "pending" | "login" | "allow" | "forbidden";

export function shouldAttachAuthentication(
  requestUrl: string | undefined,
  baseUrl: string | undefined,
  applicationOrigin: string,
): boolean {
  if (!requestUrl) {
    return false;
  }

  try {
    const base = baseUrl
      ? new URL(baseUrl, applicationOrigin)
      : new URL(applicationOrigin);
    const resolved = new URL(requestUrl, base);
    return (
      resolved.origin === applicationOrigin &&
      !PUBLIC_AUTH_PATHS.has(resolved.pathname)
    );
  } catch {
    return false;
  }
}

export function shouldInvalidateAuthentication(
  responseStatus: number | undefined,
  requestWasAuthenticated: boolean,
): boolean {
  return responseStatus === 401 && requestWasAuthenticated;
}

export function resolveProtectedRoute(
  isResolved: boolean,
  isAuthenticated: boolean,
): RouteDecision {
  if (!isResolved) {
    return "pending";
  }
  return isAuthenticated ? "allow" : "login";
}

export function resolveRoleProtectedRoute(
  isResolved: boolean,
  role: UserRole | null,
  allowedRoles: readonly UserRole[],
): RouteDecision {
  if (!isResolved) {
    return "pending";
  }
  if (!role) {
    return "login";
  }
  return allowedRoles.includes(role) ? "allow" : "forbidden";
}
