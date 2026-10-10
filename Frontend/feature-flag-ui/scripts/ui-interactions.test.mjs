import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test("Layout.tsx renders top-right account menu dropdown and removes sidebar action buttons", () => {
  const layoutPath = path.join(__dirname, "../src/components/Layout.tsx");
  const code = fs.readFileSync(layoutPath, "utf8");

  // Verify account menu dropdown state and toggle
  assert.match(code, /isAccountMenuOpen/, "Layout must track isAccountMenuOpen state");
  assert.match(code, /account-menu-trigger/, "Layout must have account-menu-trigger button");
  assert.match(code, /account-menu-dropdown/, "Layout must render account-menu-dropdown");
  assert.match(code, /accountMenuRef/, "Layout must reference account menu container for outside clicks");

  // Verify Change Password and Logout inside dropdown
  assert.match(code, /className="account-menu-item change-password-button"/, "Account menu must have Change Password item");
  assert.match(code, /className="account-menu-item logout logout-button"/, "Account menu must have Logout button retaining .logout-button class");

  // Verify sidebar does not contain the former oversized action buttons
  const sidebarSection = code.match(/<aside[\s\S]*?<\/aside>/)?.[0] || "";
  assert.doesNotMatch(sidebarSection, /Change Password/, "Sidebar must not contain Change Password button");
  assert.doesNotMatch(sidebarSection, /logout-button/, "Sidebar must not contain logout button");
});

test("SdkKeys.tsx renders Create SDK Key as a dialog modal with autofocus and collapsed guidance", () => {
  const pagePath = path.join(__dirname, "../src/pages/SdkKeys.tsx");
  const code = fs.readFileSync(pagePath, "utf8");

  // Create modal has role="dialog" and aria-modal="true"
  assert.match(code, /role="dialog"/, "SdkKeys modals must declare role dialog");
  assert.match(code, /aria-modal="true"/, "SdkKeys modals must declare aria-modal");
  assert.match(code, /createInputRef/, "Create SDK Key modal must bind input ref for autofocus");

  // Integration guidance collapsed by default
  assert.match(code, /const \[showGuide, setShowGuide\] = useState\(false\)/, "Integration guidance must be collapsed by default");

  // Table does not render database ID
  assert.doesNotMatch(code, /ID:\s*\{key\.id\}/, "SDK Keys table must not display internal database IDs");

  // Java SDK example uses environment variable
  assert.match(code, /System\.getenv\("FEATURE_FLAG_SDK_KEY"\)/, "Java SDK snippet must read key from environment variable");
});
