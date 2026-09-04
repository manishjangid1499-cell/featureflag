import assert from "node:assert/strict";
import test from "node:test";

import {
  AUTH_SESSION_CHANGED_EVENT,
  AUTH_STORAGE_KEY,
  clearAuthSession,
  isTokenExpired,
  readAuthSession,
  writeAuthSession,
} from "../src/auth/authStorage.ts";
import {
  resolveProtectedRoute,
  resolveRoleProtectedRoute,
  shouldAttachAuthentication,
  shouldInvalidateAuthentication,
} from "../src/auth/authPolicy.ts";

class MemoryStorage {
  #values = new Map();

  getItem(key) {
    return this.#values.has(key) ? this.#values.get(key) : null;
  }

  setItem(key, value) {
    this.#values.set(key, String(value));
  }

  removeItem(key) {
    this.#values.delete(key);
  }

  clear() {
    this.#values.clear();
  }
}

const events = new EventTarget();
globalThis.window = events;
globalThis.localStorage = new MemoryStorage();

function jwt(expSeconds) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none" })}.${encode({ exp: expSeconds })}.signature`;
}

test.beforeEach(() => localStorage.clear());

test("successful login state uses one centralized storage entry", () => {
  const user = {
    email: "owner@example.test",
    role: "OWNER",
    token: jwt(Math.floor(Date.now() / 1000) + 60),
  };
  let changeEvents = 0;
  const listener = () => changeEvents++;
  window.addEventListener(AUTH_SESSION_CHANGED_EVENT, listener);

  writeAuthSession(user);

  assert.deepEqual(readAuthSession(), user);
  assert.equal(localStorage.getItem(AUTH_STORAGE_KEY) !== null, true);
  assert.equal(changeEvents, 1);
  window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, listener);
});

test("failed or malformed authentication data is never retained", () => {
  assert.throws(
    () => writeAuthSession({
      email: "owner@example.test",
      role: "OWNER",
      token: "not-a-jwt",
    }),
    /invalid or expired/,
  );
  assert.equal(localStorage.getItem(AUTH_STORAGE_KEY), null);
});

test("logout and expired tokens clear sensitive authentication state", () => {
  const expired = jwt(Math.floor(Date.now() / 1000) - 1);
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
    email: "owner@example.test",
    role: "OWNER",
    token: expired,
  }));

  assert.equal(isTokenExpired(expired), true);
  assert.equal(readAuthSession(), null);
  assert.equal(localStorage.getItem(AUTH_STORAGE_KEY), null);

  localStorage.setItem(AUTH_STORAGE_KEY, "temporary");
  clearAuthSession();
  assert.equal(localStorage.getItem(AUTH_STORAGE_KEY), null);
});

test("Authorization is limited to same-origin protected requests and never a URL", () => {
  const origin = "https://console.example.test";
  assert.equal(
    shouldAttachAuthentication("/flags?page=0", undefined, origin),
    true,
  );
  assert.equal(
    shouldAttachAuthentication("/auth/login", undefined, origin),
    false,
  );
  assert.equal(
    shouldAttachAuthentication(
      "https://outside.example.test/flags",
      undefined,
      origin,
    ),
    false,
  );
  assert.equal(
    new URL("/flags", origin).searchParams.has("token"),
    false,
  );
});

test("401 invalidates only authenticated requests while 403 and network errors preserve state", () => {
  assert.equal(shouldInvalidateAuthentication(401, true), true);
  assert.equal(shouldInvalidateAuthentication(401, false), false);
  assert.equal(shouldInvalidateAuthentication(403, true), false);
  assert.equal(shouldInvalidateAuthentication(undefined, true), false);
});

test("protected content waits for resolution and role checks mirror backend roles", () => {
  assert.equal(resolveProtectedRoute(false, false), "pending");
  assert.equal(resolveProtectedRoute(true, false), "login");
  assert.equal(resolveProtectedRoute(true, true), "allow");
  assert.equal(
    resolveRoleProtectedRoute(false, null, ["OWNER", "ADMIN"]),
    "pending",
  );
  assert.equal(
    resolveRoleProtectedRoute(true, "VIEWER", ["OWNER", "ADMIN"]),
    "forbidden",
  );
  assert.equal(
    resolveRoleProtectedRoute(true, "ADMIN", ["OWNER", "ADMIN"]),
    "allow",
  );
});
