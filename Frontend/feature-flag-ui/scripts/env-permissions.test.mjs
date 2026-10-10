import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("CreateFlagModal restricts environment choices for DEVELOPER and provides explanation", () => {
  const createModalFilePath = path.join(__dirname, "../src/components/flags/CreateFlagModal.tsx");
  const code = fs.readFileSync(createModalFilePath, "utf8");

  assert.match(code, /useAuth/, "CreateFlagModal must import and use useAuth hook");
  assert.match(code, /isDeveloper/, "CreateFlagModal must check isDeveloper");
  assert.match(code, /<option value="QA">QA<\/option>/, "QA option must be present");
  assert.match(code, /!isDeveloper\s*&&\s*<option value="PROD">PROD<\/option>/, "PROD option must be omitted for developers");
  assert.match(code, /Developer role can create flags in DEV, QA, and STAGING/, "Explanatory copy must be shown to developers");
});

test("EditFlagModal disables changes on PROD for DEVELOPER with explanatory warning", () => {
  const editModalFilePath = path.join(__dirname, "../src/components/flags/EditFlagModal.tsx");
  const code = fs.readFileSync(editModalFilePath, "utf8");

  assert.match(code, /useAuth/, "EditFlagModal must import and use useAuth hook");
  assert.match(code, /isDeveloper\s*&&\s*isProdFlag/, "EditFlagModal must compute developer restriction on PROD");
  assert.match(code, /disabled=\{loading\s*\|\|\s*isBlockedByRole\}/, "Save button must be disabled when blocked");
  assert.match(code, /Production Restricted:/, "Warning banner must be shown for PROD flag when user is developer");
  assert.match(code, /Developers cannot modify production flags/, "Validation in submit handler must reject PROD modifications by developer");
});

test("FlagTable disables/hides mutation actions on PROD flags for DEVELOPER", () => {
  const tableFilePath = path.join(__dirname, "../src/components/flags/FlagTable.tsx");
  const code = fs.readFileSync(tableFilePath, "utf8");

  assert.match(code, /isDeveloper\s*&&\s*env\.toUpperCase\(\)\s*===\s*"PROD"/, "FlagTable must detect PROD flags for DEVELOPER");
  assert.match(code, /Prod: Owner\/Admin only/, "FlagTable must render notice when developer views PROD flag");
});
