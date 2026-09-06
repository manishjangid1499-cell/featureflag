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
import { getApiErrorMessage, getApiProblem, getApiStatus } from "../src/api/errors.ts";
import { collectAllPages } from "../src/types/page.ts";

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

test("ProblemDetail errors are rendered through one safe contract parser", () => {
  const error = {
    isAxiosError: true,
    response: {
      status: 400,
      data: {
        title: "Validation Failed",
        detail: "Validation failed for one or more fields",
        correlationId: "corr-test",
        errors: { flagKey: "must not be blank" },
      },
    },
  };

  assert.equal(
    getApiErrorMessage(error, "fallback"),
    "flagKey: must not be blank (Reference: corr-test)",
  );
  assert.equal(getApiErrorMessage(new Error("private internal details"), "fallback"), "fallback");
});

test("dashboard aggregation walks every backend page instead of truncating at 100", async () => {
  const requestedPages = [];
  const result = await collectAllPages(async (page, size) => {
    requestedPages.push([page, size]);
    return {
      content: [`record-${page}`],
      page,
      size,
      totalElements: 3,
      totalPages: 3,
    };
  });

  assert.deepEqual(result, ["record-0", "record-1", "record-2"]);
  assert.deepEqual(requestedPages, [[0, 100], [1, 100], [2, 100]]);
});

test("malformed error bodies never render raw server objects or exception text", () => {
  for (const data of [null, "SQL PRIVATE_VALUE", ["PRIVATE_VALUE"],
    { title: { sql: "PRIVATE_VALUE" }, detail: 42, errors: ["PRIVATE_VALUE"] },
    { errors: { field: { secret: "PRIVATE_VALUE" } }, correlationId: "invalid correlation" }]) {
    const error = { isAxiosError: true, response: { status: 500, data } };
    assert.equal(getApiErrorMessage(error, "Request failed"), "Request failed");
    assert.equal(getApiStatus(error), 500);
  }
  assert.equal(getApiStatus(new Error("PRIVATE_VALUE")), undefined);
});

test("ProblemDetail fields and correlation references are bounded", () => {
  const errors = Object.fromEntries(Array.from({ length: 100 }, (_, i) => [`field${i}`, "x".repeat(300)]));
  const error = { isAxiosError: true, response: { status: 400,
    data: { errors, correlationId: "c".repeat(65) } } };
  const problem = getApiProblem(error);
  assert.equal(Object.keys(problem.errors).length, 32);
  assert.equal(problem.errors.field0.length, 160);
  assert.equal(problem.correlationId, undefined);
  assert.equal(getApiErrorMessage(error, "fallback").length, 1024);
});

test("dashboard aggregation surfaces a failed page instead of returning partial totals", async () => {
  await assert.rejects(collectAllPages(async (page, size) => {
    if (page === 1) throw new Error("request failed");
    return { content: ["first"], page, size, totalElements: 2, totalPages: 2 };
  }), /request failed/);
});
