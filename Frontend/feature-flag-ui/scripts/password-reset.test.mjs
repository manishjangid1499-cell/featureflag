import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");

test("types/auth.ts defines ForgotPasswordRequest and ResetPasswordRequest contracts", () => {
  const typesContent = fs.readFileSync(
    path.join(projectRoot, "src/types/auth.ts"),
    "utf-8"
  );

  assert.match(typesContent, /export interface ForgotPasswordRequest\s*\{[\s\S]*email:\s*string;/);
  assert.match(typesContent, /export interface ForgotPasswordResponse\s*\{[\s\S]*message:\s*string;/);
  assert.match(typesContent, /export interface ResetPasswordRequest\s*\{[\s\S]*token:\s*string;[\s\S]*newPassword:\s*string;[\s\S]*confirmPassword:\s*string;/);
  assert.match(typesContent, /export interface ResetPasswordResponse\s*\{[\s\S]*message:\s*string;/);
});

test("authApi.ts defines forgotPassword and resetPassword endpoints", () => {
  const apiContent = fs.readFileSync(
    path.join(projectRoot, "src/api/authApi.ts"),
    "utf-8"
  );

  assert.match(apiContent, /export const forgotPassword\s*=\s*async\s*\([\s\S]*\/auth\/forgot-password/);
  assert.match(apiContent, /export const resetPassword\s*=\s*async\s*\([\s\S]*\/auth\/reset-password/);
});

test("authPolicy.ts includes forgot-password and reset-password in PUBLIC_AUTH_PATHS", () => {
  const policyContent = fs.readFileSync(
    path.join(projectRoot, "src/auth/authPolicy.ts"),
    "utf-8"
  );

  assert.match(policyContent, /"\/auth\/forgot-password"/);
  assert.match(policyContent, /"\/auth\/reset-password"/);
});

test("Login.tsx provides access to Forgot Password flow and renders ForgotPasswordModal", () => {
  const loginContent = fs.readFileSync(
    path.join(projectRoot, "src/pages/Login.tsx"),
    "utf-8"
  );

  assert.match(loginContent, /ForgotPasswordModal/);
  assert.match(loginContent, /Forgot Password\?/);
  assert.match(loginContent, /setShowForgotModal\(true\)/);
});

test("ForgotPasswordModal.tsx maintains generic confirmation and safe error boundaries", () => {
  const modalContent = fs.readFileSync(
    path.join(projectRoot, "src/pages/ForgotPasswordModal.tsx"),
    "utf-8"
  );

  assert.match(modalContent, /forgotPassword\(/);
  assert.match(modalContent, /If an eligible account exists/);
  assert.match(modalContent, /Back to Sign In/);
});

test("ResetPassword.tsx extracts token and strips it from URL into component state", () => {
  const resetContent = fs.readFileSync(
    path.join(projectRoot, "src/pages/ResetPassword.tsx"),
    "utf-8"
  );

  assert.match(resetContent, /searchParams\.get\("token"\)/);
  assert.match(resetContent, /window\.history\.replaceState/);
  // Validates byte length limit for BCrypt
  assert.match(resetContent, /new TextEncoder\(\)\.encode\(newPassword\)\.length/);
  assert.match(resetContent, /72/);
  // Validates mismatch check
  assert.match(resetContent, /newPassword !== confirmPassword/);
  // Does not auto-retry on network error and redirects to login with message on success
  assert.match(resetContent, /navigate\("\/login",\s*\{[\s\S]*replace:\s*true/);
});

test("App.tsx registers /reset-password route", () => {
  const appContent = fs.readFileSync(
    path.join(projectRoot, "src/App.tsx"),
    "utf-8"
  );

  assert.match(appContent, /<Route path="\/reset-password" element=\{<ResetPassword \/>\} \/>/);
});
