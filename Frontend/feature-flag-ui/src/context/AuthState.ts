import { createContext } from "react";
import type { AuthUser, LoginRequest, UserRole } from "../types/auth";

export interface AuthContextType {
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

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
