import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");

const readSrc = (relative) =>
  fs.readFileSync(path.join(projectRoot, "src", relative), "utf8");

test("Analytics.tsx removes manual aggregate delete action and provides event semantics notice", () => {
  const content = readSrc("pages/Analytics.tsx");

  // Verify manual delete button and function are absent
  assert.equal(content.includes("deleteAnalytics"), false);
  assert.equal(content.includes("handleDelete"), false);
  assert.equal(content.includes("<button>Delete</button>"), false);

  // Verify explanation regarding event counts, unique users, previews, and replay consistency
  assert.match(content, /Aggregates count total runtime evaluation and mutation events/i);
  assert.match(content, /do not represent unique users or conversion statistics/i);
  assert.match(content, /historical counts may include.*previews/i);
  assert.match(content, /Manual deletion or reset is disabled to ensure Kafka replay consistency/i);
});

test("Members.tsx explains delivery confirmation semantics and token invalidation on resend", () => {
  const content = readSrc("pages/Members.tsx");

  assert.match(content, /Outbound delivery status reflects mail service dispatch confirmation/i);
  assert.match(content, /not guaranteed mailbox receipt/i);
  assert.match(content, /Resending an invitation generates a new security token and invalidates any previous link/i);
});

test("Notifications.tsx distinguishes SENT (provider accepted), FAILED, and PENDING without claiming inbox delivery", () => {
  const content = readSrc("pages/Notifications.tsx");

  assert.match(content, /SENT \(PROVIDER ACCEPTED\)/);
  assert.match(content, /confirms outbound acceptance by the upstream SMTP\/mail provider/i);
  assert.match(content, /does not guarantee receipt in the recipient.*personal inbox/i);
  assert.match(content, /FAILED.*indicates mail dispatch was rejected/i);
  assert.match(content, /PENDING.*indicates the dispatch request is queued/i);
});
