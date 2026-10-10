import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("EditFlagModal enforces flagKey and environment immutability contract", () => {
  const modalFilePath = path.join(__dirname, "../src/components/flags/EditFlagModal.tsx");
  const modalCode = fs.readFileSync(modalFilePath, "utf8");

  // Verify flagKey is read-only / disabled
  assert.match(modalCode, /value=\{flag\.flagKey\}/, "Modal must bind flagKey from flag object");
  assert.match(modalCode, /readOnly/, "Modal must set readOnly on flagKey");
  assert.match(modalCode, /disabled/, "Modal must disable flagKey input");

  // Verify environment is read-only / disabled
  assert.match(modalCode, /value=\{flag\.environment\}/, "Modal must bind environment from flag object");
  assert.doesNotMatch(modalCode, /<select[^>]*value=\{environment\}/, "Modal must not render an editable select for environment");

  // Verify payload sends canonical flag identity
  assert.match(modalCode, /flagKey:\s*flag\.flagKey/, "Payload must send canonical flagKey from flag");
  assert.match(modalCode, /environment:\s*flag\.environment/, "Payload must send canonical environment from flag");
  assert.match(modalCode, /expectedVersion:\s*flag\.version/, "Payload must send expectedVersion for optimistic locking");

  // Verify identity explanation is present
  assert.match(modalCode, /Flag Identity:/i, "Modal must present identity note to user");
  assert.match(modalCode, /cannot be changed after creation/i, "Modal must explain identity immutability");
  assert.match(modalCode, /display name/i, "Modal must advise updating display name instead of deleting/recreating");
});

test("CreateFlagModal maintains editable key and environment during creation", () => {
  const createModalFilePath = path.join(__dirname, "../src/components/flags/CreateFlagModal.tsx");
  const createModalCode = fs.readFileSync(createModalFilePath, "utf8");

  assert.match(createModalCode, /onChange=\{\(e\)\s*=>\s*setFlagKey/, "Create modal must allow setting flagKey");
  assert.match(createModalCode, /onChange=\{\(e\)\s*=>\s*setEnvironment/, "Create modal must allow setting environment");
});
