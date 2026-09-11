import type { AuthUser, UserRole } from "../types/auth";

export const AUTH_STORAGE_KEY = "authUser";
export const AUTH_SESSION_CHANGED_EVENT = "auth-session-changed";

const USER_ROLES = new Set<UserRole>([
  "OWNER",
  "ADMIN",
  "DEVELOPER",
  "VIEWER",
]);

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const segments = token.split(".");
  if (segments.length !== 3) {
    return null;
  }

  try {
    const base64 = segments[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const decoded = decodeURIComponent(
      Array.from(atob(padded), (character) =>
        `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`,
      ).join(""),
    );
    const payload: unknown = JSON.parse(decoded);
    return payload !== null && typeof payload === "object"
      ? (payload as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

export function getTokenExpiryMs(token: string): number | null {
  const payload = decodeJwtPayload(token);
  return payload && typeof payload.exp === "number" && payload.exp > 0
    ? payload.exp * 1000
    : null;
}

export function isTokenExpired(
  token: string,
  nowMs: number = Date.now(),
): boolean {
  const expiryMs = getTokenExpiryMs(token);
  return expiryMs === null || expiryMs <= nowMs;
}

function isAuthUser(value: unknown): value is AuthUser {
  if (value === null || typeof value !== "object") {
    return false;
  }

  const candidate = value as Partial<AuthUser>;
  return (
    (candidate.name == null || typeof candidate.name === "string") &&
    typeof candidate.email === "string" &&
    candidate.email.trim().length > 0 &&
    typeof candidate.token === "string" &&
    candidate.token.length > 0 &&
    typeof candidate.role === "string" &&
    USER_ROLES.has(candidate.role as UserRole)
  );
}

function notifySessionChanged(): void {
  window.dispatchEvent(new Event(AUTH_SESSION_CHANGED_EVENT));
}

export function clearAuthSession(notify = true): void {
  localStorage.removeItem(AUTH_STORAGE_KEY);
  if (notify) {
    notifySessionChanged();
  }
}

export function readAuthSession(
  nowMs: number = Date.now(),
): AuthUser | null {
  const stored = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(stored);
    if (!isAuthUser(parsed) || isTokenExpired(parsed.token, nowMs)) {
      clearAuthSession();
      return null;
    }
    return parsed;
  } catch {
    clearAuthSession();
    return null;
  }
}

export function writeAuthSession(user: AuthUser): void {
  if (!isAuthUser(user) || isTokenExpired(user.token)) {
    throw new Error("Cannot store an invalid or expired authentication session");
  }
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
  notifySessionChanged();
}
