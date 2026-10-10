import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("FlagEvaluationModal derives preview environment directly and resets state on flag change via keying", () => {
  const modalFilePath = path.join(__dirname, "../src/components/flags/FlagEvaluationModal.tsx");
  const code = fs.readFileSync(modalFilePath, "utf8");

  assert.match(code, /const environment = flag\.environment/, "Must derive preview environment directly from the selected flag");
  assert.match(code, /key=\{`?\$\{flag\.id\}/, "Must key modal content to flag identity to reset state on flag change");
  assert.doesNotMatch(code, /setEnvironment/, "Environment must be directly derived rather than updated via setState in an effect");
});

test("FlagEvaluationModal displays selected flag environment as read-only", () => {
  const modalFilePath = path.join(__dirname, "../src/components/flags/FlagEvaluationModal.tsx");
  const code = fs.readFileSync(modalFilePath, "utf8");

  assert.match(code, /Environment \(Read-only\)/, "Must label environment field as read-only");
  assert.match(code, /readOnly/, "Must mark environment input as readOnly");
  assert.match(code, /disabled/, "Must disable editing of environment");
  assert.doesNotMatch(code, /<select[^>]*value=\{environment\}/, "Environment must not be an editable select dropdown");
});

test("FlagEvaluationModal clearly labels operation as Preview and explains it does not increase evaluation counts", () => {
  const modalFilePath = path.join(__dirname, "../src/components/flags/FlagEvaluationModal.tsx");
  const code = fs.readFileSync(modalFilePath, "utf8");

  assert.match(code, /Preview Feature Flag:/, "Modal header must clearly label operation as Preview");
  assert.match(code, /Dashboard Preview:/, "Explains dashboard preview mode in banner");
  assert.match(code, /do not emit telemetry events or increase application evaluation counts/, "Must explicitly explain that previews do not increase evaluation counts");
  assert.match(code, /Run Preview/, "Submit button must be labeled as Preview");
  assert.match(code, /Preview Verdict:/, "Verdict section must be labeled as Preview Verdict");
});

test("FlagTable labels dashboard action button as Preview", () => {
  const tableFilePath = path.join(__dirname, "../src/components/flags/FlagTable.tsx");
  const code = fs.readFileSync(tableFilePath, "utf8");

  assert.match(code, />\s*Preview\s*<\/button>/, "Action button in FlagTable must be labeled Preview");
  assert.match(code, /title="Preview flag resolution without increasing application evaluation counts"/, "Button title must explain it does not increase evaluation counts");
});
