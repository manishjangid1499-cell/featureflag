import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("Layout.tsx renders Change Password button accessible to all authenticated roles", () => {
  const layoutPath = path.join(__dirname, "../src/components/Layout.tsx");
  const code = fs.readFileSync(layoutPath, "utf8");

  assert.match(code, /Change Password/, "Layout must provide Change Password action");
  assert.match(code, /setIsChangePasswordOpen\(true\)/, "Change Password button must open ChangePasswordModal");
  assert.match(code, /<ChangePasswordModal/, "Layout must render ChangePasswordModal");
  // Ensure Change Password is NOT restricted by canManageMembers
  assert.doesNotMatch(
    code,
    /canManageMembers\s*&&\s*\(?[^)]*Change Password/s,
    "Change Password must NOT be restricted to canManageMembers (accessible to all roles)"
  );
});

test("types/auth.ts defines ChangePasswordRequest and ChangePasswordResponse", () => {
  const typesPath = path.join(__dirname, "../src/types/auth.ts");
  const code = fs.readFileSync(typesPath, "utf8");

  assert.match(code, /export interface ChangePasswordRequest/, "Must export ChangePasswordRequest interface");
  assert.match(code, /currentPassword:\s*string;/, "Must require currentPassword");
  assert.match(code, /newPassword:\s*string;/, "Must require newPassword");
  assert.match(code, /confirmPassword\?:\s*string;/, "Must support confirmPassword");
  assert.match(code, /export interface ChangePasswordResponse/, "Must export ChangePasswordResponse interface");
});

test("api/authApi.ts defines changePassword calling POST /auth/change-password", () => {
  const apiPath = path.join(__dirname, "../src/api/authApi.ts");
  const code = fs.readFileSync(apiPath, "utf8");

  assert.match(code, /export const changePassword\b/, "Must export changePassword function");
  assert.match(code, /api\.post<[^>]*>\(["']\/auth\/change-password["']/, "Must post to /auth/change-password");
});

test("ChangePasswordModal.tsx enforces client-side password validation contract", () => {
  const modalPath = path.join(__dirname, "../src/components/ChangePasswordModal.tsx");
  const code = fs.readFileSync(modalPath, "utf8");

  // Minimum length check
  assert.match(code, /newPassword\.length\s*<\s*8/, "Must validate newPassword minimum 8 characters");

  // UTF-8 byte boundary check (not character length)
  assert.match(code, /new\s+TextEncoder\(\)\.encode\(newPassword\)\.length/, "Must enforce 72 UTF-8 byte limit using TextEncoder");
  assert.match(code, />\s*72/, "Must check against 72 byte limit");

  // Password reuse rejection
  assert.match(code, /newPassword\s*===\s*currentPassword/, "Must reject reuse of current password");

  // Confirm password match
  assert.match(code, /newPassword\s*!==\s*confirmPassword/, "Must require newPassword to match confirmPassword");

  // Disabled while submitting
  assert.match(code, /disabled=\{submitting\}/, "Must disable submission and inputs while pending");

  // Clear inputs and session on success, then navigate to /login
  assert.match(code, /resetState\(\)/, "Must reset password inputs upon completion");
  assert.match(code, /logout\(\)/, "Must clear auth session upon completion");
  assert.match(code, /navigate\(["']\/login["']/, "Must redirect to /login upon completion");

  // Network error message explains re-login without auto-retry
  assert.match(
    code,
    /Network connection error\. If your password was changed, please sign in with your new password\./,
    "Must provide clear network uncertainty message without blind retries"
  );

  // Security: No raw passwords in localStorage, sessionStorage, or logs
  assert.doesNotMatch(code, /localStorage\.set.*Password/i, "Must never persist passwords to localStorage");
  assert.doesNotMatch(code, /sessionStorage\.set.*Password/i, "Must never persist passwords to sessionStorage");
  assert.doesNotMatch(code, /console\.log.*Password/i, "Must never log passwords");
});

test("Login.tsx displays successMessage when redirected after password change", () => {
  const loginPath = path.join(__dirname, "../src/pages/Login.tsx");
  const code = fs.readFileSync(loginPath, "utf8");

  assert.match(code, /useLocation/, "Login page must use location state");
  assert.match(code, /successMessage/, "Login page must extract and render successMessage banner");
});

test("authPolicy.ts preserves session on HTTP 400 (incorrect current password)", () => {
  const policyPath = path.join(__dirname, "../src/auth/authPolicy.ts");
  const code = fs.readFileSync(policyPath, "utf8");

  assert.match(
    code,
    /responseStatus\s*===\s*401\s*&&\s*requestWasAuthenticated/,
    "Session invalidation must strictly trigger only on HTTP 401, preserving session on HTTP 400"
  );
});
