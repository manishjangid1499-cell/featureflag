import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");

test("types/auth.ts defines TransferOwnershipRequest interface", () => {
  const authTypesContent = fs.readFileSync(
    path.join(projectRoot, "src", "types", "auth.ts"),
    "utf8"
  );
  assert.match(
    authTypesContent,
    /export interface TransferOwnershipRequest\s*\{[\s\S]*newOwnerId:\s*number;[\s\S]*currentPassword:\s*string;[\s\S]*\}/
  );
});

test("memberApi.ts defines transferOwnership calling POST /members/transfer-ownership", () => {
  const memberApiContent = fs.readFileSync(
    path.join(projectRoot, "src", "api", "memberApi.ts"),
    "utf8"
  );
  assert.match(
    memberApiContent,
    /export const transferOwnership = async\s*\(/
  );
  assert.match(
    memberApiContent,
    /\/members\/transfer-ownership/
  );
});

test("Members.tsx renders Transfer Ownership button strictly for isOwner", () => {
  const membersContent = fs.readFileSync(
    path.join(projectRoot, "src", "pages", "Members.tsx"),
    "utf8"
  );
  assert.match(
    membersContent,
    /\{isOwner\s*&&[\s\S]*Transfer Ownership[\s\S]*\}/
  );
});

test("Members.tsx modal restricts target selection to eligible active ADMINs", () => {
  const membersContent = fs.readFileSync(
    path.join(projectRoot, "src", "pages", "Members.tsx"),
    "utf8"
  );
  assert.match(
    membersContent,
    /const eligibleAdmins = members\.filter\(\s*\(m\)\s*=>\s*m\.role === "ADMIN" && m\.enabled/
  );
  assert.match(
    membersContent,
    /transferTargetId/
  );
  assert.match(
    membersContent,
    /transferPassword/
  );
  assert.match(
    membersContent,
    /transferConfirmed/
  );
});

test("Members.tsx clears authentication and redirects without auto-retrying on failure", () => {
  const membersContent = fs.readFileSync(
    path.join(projectRoot, "src", "pages", "Members.tsx"),
    "utf8"
  );
  assert.match(
    membersContent,
    /localStorage\.clear\(\)/
  );
  assert.match(
    membersContent,
    /sessionStorage\.clear\(\)/
  );
  assert.match(
    membersContent,
    /\/login\?transferred=true/
  );
  assert.match(
    membersContent,
    /setTransferError\(\s*getApiErrorMessage\(/
  );
});
