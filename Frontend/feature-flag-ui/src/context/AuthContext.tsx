import {
  createContext,
  useEffect,
  useContext,
  useState,
  type ReactNode,
} from "react";

import type {
  AuthUser,
  LoginRequest,
  LoginResponse,
  UserRole,
} from "../types/auth";

import { login as loginApi } from "../api/authApi";
import {
  AUTH_SESSION_CHANGED_EVENT,
  AUTH_STORAGE_KEY,
  clearAuthSession,
  getTokenExpiryMs,
  readAuthSession,
  writeAuthSession,
} from "../auth/authStorage";

interface AuthContextType {
  user: AuthUser | null;
  isAuthResolved: boolean;
  isAuthenticated: boolean;
  role: UserRole | null;
  isOwner: boolean;
  isAdmin: boolean;
  isDeveloper: boolean;
  isViewer: boolean;
  canManageFlags: boolean;
  canDeleteFlags: boolean;
  canManageMembers: boolean;
  login: (request: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isAuthResolved, setAuthResolved] = useState(false);

  useEffect(() => {
    const refreshSession = () => {
      setUser(readAuthSession());
      setAuthResolved(true);
    };
    const handleStorage = (event: StorageEvent) => {
      if (event.key === AUTH_STORAGE_KEY || event.key === null) {
        refreshSession();
      }
    };

    refreshSession();
    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, refreshSession);
    window.addEventListener("storage", handleStorage);

    return () => {
      window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, refreshSession);
      window.removeEventListener("storage", handleStorage);
    };
  }, []);

  useEffect(() => {
    if (!user) {
      return;
    }

    const expiryMs = getTokenExpiryMs(user.token);
    if (expiryMs === null) {
      clearAuthSession();
      return;
    }

    const delayMs = Math.max(0, expiryMs - Date.now());
    const timer = window.setTimeout(
      () => clearAuthSession(),
      Math.min(delayMs, 2_147_483_647),
    );
    return () => window.clearTimeout(timer);
  }, [user]);

  const login = async (request: LoginRequest) => {
    const response: LoginResponse = await loginApi(request);

    const authUser: AuthUser = {
      email: response.email,
      role: response.role,
      token: response.token,
    };

    writeAuthSession(authUser);
    setUser(authUser);
    setAuthResolved(true);
  };

  const logout = () => {
    clearAuthSession();
    setUser(null);
    setAuthResolved(true);
  };

  const role = user?.role || null;
  const isOwner = role === "OWNER";
  const isAdmin = role === "ADMIN";
  const isDeveloper = role === "DEVELOPER";
  const isViewer = role === "VIEWER";

  // Permission helpers
  const canManageFlags = isOwner || isAdmin || isDeveloper;
  const canDeleteFlags = isOwner || isAdmin;
  const canManageMembers = isOwner || isAdmin;

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthResolved,
        isAuthenticated: user !== null,
        role,
        isOwner,
        isAdmin,
        isDeveloper,
        isViewer,
        canManageFlags,
        canDeleteFlags,
        canManageMembers,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
