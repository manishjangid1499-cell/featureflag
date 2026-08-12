import {
  createContext,
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

interface AuthContextType {
  user: AuthUser | null;
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
  const [user, setUser] = useState<AuthUser | null>(() => {
    const storedUser = localStorage.getItem("authUser");
    if (storedUser) {
      try {
        return JSON.parse(storedUser);
      } catch {
        localStorage.removeItem("authUser");
      }
    }
    return null;
  });

  const login = async (request: LoginRequest) => {
    const response: LoginResponse = await loginApi(request);

    const authUser: AuthUser = {
      email: response.email,
      role: response.role,
      token: response.token,
    };

    localStorage.setItem("authUser", JSON.stringify(authUser));
    setUser(authUser);
  };

  const logout = () => {
    localStorage.removeItem("authUser");
    setUser(null);
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