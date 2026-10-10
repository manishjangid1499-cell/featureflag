import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("App.tsx guards /sdk-keys route strictly to OWNER and ADMIN", () => {
  const appFilePath = path.join(__dirname, "../src/App.tsx");
  const code = fs.readFileSync(appFilePath, "utf8");

  assert.match(code, /path="\/sdk-keys"/, "App.tsx must define /sdk-keys route");
  assert.match(
    code,
    /<RoleProtectedRoute allowedRoles=\{\["OWNER", "ADMIN"\]\}>\s*<SdkKeys \/>\s*<\/RoleProtectedRoute>/,
    "/sdk-keys route must be strictly guarded for OWNER and ADMIN only",
  );
  assert.doesNotMatch(
    code,
    /allowedRoles=\{\[[^\]]*DEVELOPER[^\]]*\]\}>\s*<SdkKeys/,
    "DEVELOPER must not have access to /sdk-keys route",
  );
  assert.doesNotMatch(
    code,
    /allowedRoles=\{\[[^\]]*VIEWER[^\]]*\]\}>\s*<SdkKeys/,
    "VIEWER must not have access to /sdk-keys route",
  );
});

test("Layout.tsx only renders SDK Keys navigation link when canManageMembers is true", () => {
  const layoutFilePath = path.join(__dirname, "../src/components/Layout.tsx");
  const code = fs.readFileSync(layoutFilePath, "utf8");

  assert.match(code, /\{canManageMembers && \(/, "Must conditionally render under canManageMembers");
  assert.match(code, /to="\/sdk-keys"/, "Must render NavLink to /sdk-keys");
  assert.match(code, /SDK Keys/, "Must label nav item as SDK Keys");
});

test("SdkKeys.tsx enforces access control and displays metadata fields", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  assert.match(code, /if \(!canManageMembers\) \{/, "Must verify canManageMembers before rendering dashboard");
  assert.match(code, /Access Denied/, "Must provide explicit Access Denied view when not permitted");
  assert.match(code, /NAME/, "Must show Name column in table");
  assert.match(code, /ENVIRONMENT/, "Must show Environment column in table");
  assert.match(code, /KEY PREFIX/, "Must show Key Prefix column in table");
  assert.match(code, /STATUS/, "Must show Status column in table");
  assert.match(code, /CREATED/, "Must show Created time in table");
  assert.match(code, /REVOKED/, "Must show Revoked time in table");
  assert.match(code, /ACTIONS/, "Must show Actions column in table");
});

test("SdkKeys.tsx handles one-time secret reveal securely without persisting raw keys", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  assert.match(
    code,
    /Store this secret key securely; it cannot be shown again/i,
    "Must display explicit warning that raw key cannot be shown again",
  );
  assert.match(
    code,
    /setRevealedKey\(null\)/,
    "Must clear raw key from component state when closed",
  );
  assert.match(
    code,
    /useEffect\(\(\) => \{\s*return \(\) => \{\s*setRevealedKey\(null\);/s,
    "Must clear raw key on component unmount to prevent retention when navigating away or logging out",
  );

  // Security checks: ensure raw key is never written to storage or exported
  assert.doesNotMatch(
    code,
    /localStorage\.setItem\([^)]*rawKey/i,
    "Must not write raw key to localStorage",
  );
  assert.doesNotMatch(
    code,
    /sessionStorage\.setItem\([^)]*rawKey/i,
    "Must not write raw key to sessionStorage",
  );
  assert.doesNotMatch(
    code,
    /document\.cookie/i,
    "Must not write raw key to cookies",
  );
});

test("SdkKeys.tsx revocation modal confirms key name and environment while disabling controls", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  assert.match(
    code,
    /Are you sure you want to revoke the SDK key/i,
    "Revocation modal must ask for user confirmation",
  );
  assert.match(
    code,
    /\{revokingKey\.name\}/,
    "Revocation modal must display the key name",
  );
  assert.match(
    code,
    /\{revokingKey\.environment\}/,
    "Revocation modal must display the key environment",
  );
  assert.match(
    code,
    /disabled=\{revoking\}/,
    "Revocation modal must disable mutation controls while pending",
  );
  assert.match(
    code,
    /await reload\(\)/,
    "Revocation must refresh the displayed list upon success",
  );
});

test("SdkKeys.tsx creation does not automatically retry and explains recovery", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  assert.match(
    code,
    /Automatic retry is not performed/,
    "Must explicitly explain why automatic retry is not performed on key creation",
  );
});

test("SdkKeys.tsx provides server-side integration guidance referencing exact header and runtime path", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  assert.match(
    code,
    /X-Feature-Flag-Key/,
    "Must document exact header constant X-Feature-Flag-Key",
  );
  assert.match(
    code,
    /\/runtime\/v1\/flags\/\{flagKey\}\/evaluate\?subject=\{subject\}/,
    "Must document exact runtime evaluation path",
  );
  assert.match(
    code,
    /Never\s*<\/[^>]+>\s*embed them in client-side code|Never.*embed them in client-side code/i,
    "Must warn against embedding SDK keys in client-side code or browser JavaScript",
  );
});
