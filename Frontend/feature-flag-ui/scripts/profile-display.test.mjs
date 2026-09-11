import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import ts from "typescript";
import * as storage from "../src/auth/authStorage.ts";

// Exercise the real TSX components using the existing TypeScript/React packages.
function loadComponent(relativePath, mocks) {
  const filename = fileURLToPath(new URL(relativePath, import.meta.url));
  const require = createRequire(filename);
  const { outputText } = ts.transpileModule(readFileSync(filename, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: filename,
  });
  const exports = {};
  runInNewContext(outputText, {
    exports, window,
    require: (name) => Object.hasOwn(mocks, name) ? mocks[name] : require(name),
  }, { filename });
  return exports;
}

const values = new Map();
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: (key) => values.delete(key),
};
globalThis.window = Object.assign(new EventTarget(), {
  setTimeout: () => 1,
  clearTimeout: () => {},
});
test.beforeEach(() => values.clear());

let user;
const auth = { useAuth: () => ({ user, isViewer: user.role === "VIEWER" }) };
const { Dashboard } = loadComponent("../src/pages/Dashboard.tsx", {
  "../hooks/useAuth": auth,
  "../api/flagApi": {},
  "../api/auditApi": {},
  "../types/page": {},
});
const { default: Layout } = loadComponent("../src/components/Layout.tsx", {
  "../hooks/useAuth": auth,
});
const render = (component) => renderToStaticMarkup(
  React.createElement(MemoryRouter, null, React.createElement(component)),
);
const greeting = () => render(Dashboard).match(/<h1[^>]*>(.*?)<\/h1>/)[1];

for (const role of ["OWNER", "ADMIN", "DEVELOPER", "VIEWER"]) {
  test(`${role} dashboard greets the stored name and retains its role label`, () => {
    user = { name: "  Abhay Thakur  ", email: "person@example.test", role };
    assert.equal(greeting(), "Welcome back, Abhay Thakur");
    assert.ok(!greeting().includes(user.email));
    assert.ok(render(Dashboard).includes(`${role} CONSOLE`));
  });
}

for (const name of ["", "   ", null, undefined]) {
  test(`dashboard falls back to email for name=${JSON.stringify(name)}`, () => {
    user = { name, email: "user@example.test", role: "VIEWER" };
    assert.equal(greeting(), "Welcome back, user@example.test");
  });
}

test("sidebar uses the same name while keeping email available as account identity", () => {
  user = { name: "  Abhay Thakur  ", email: "person@example.test", role: "ADMIN" };
  assert.ok(render(Layout).includes('<strong title="person@example.test">Abhay Thakur</strong>'));
  user.name = null;
  assert.ok(render(Layout).includes('<strong title="person@example.test">person@example.test</strong>'));
});

function session(overrides = {}) {
  const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString("base64url");
  return { email: "person@example.test", role: "ADMIN", token: `header.${payload}.signature`, ...overrides };
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

// A small hook harness lets session effects and their cleanup run without adding a DOM library.
function mountAuth(t) {
  const states = [], effects = [], requests = [];
  let stateIndex, effectIndex, pending, dirty = true, value;
  const hooks = {
    ...React,
    useState(initial) {
      const index = stateIndex++;
      if (!(index in states)) states[index] = initial;
      return [states[index], (next) => {
        if (!Object.is(states[index], next)) { states[index] = next; dirty = true; }
      }];
    },
    useEffect(setup, dependencies) {
      const index = effectIndex++;
      const previous = effects[index];
      if (!previous || dependencies.some((dependency, i) => !Object.is(dependency, previous.dependencies[i]))) {
        pending.push(() => {
          previous?.cleanup?.();
          effects[index] = { dependencies, cleanup: setup() };
        });
      }
    },
  };
  const { AuthProvider } = loadComponent("../src/context/AuthContext.tsx", {
    react: hooks,
    "./AuthState": { AuthContext: React.createContext(null) },
    "../auth/authStorage": storage,
    "../api/authApi": {
      login: async () => session(),
      getProfile: () => { const request = deferred(); requests.push(request); return request.promise; },
    },
  });
  function flush() {
    let renders = 0;
    while (dirty) {
      assert.ok(++renders < 20, "session effects must not loop");
      dirty = false; stateIndex = 0; effectIndex = 0; pending = [];
      value = AuthProvider({ children: null }).props.value;
      for (const run of pending) run();
    }
    return value;
  }
  t.after(() => { for (const effect of effects) effect?.cleanup?.(); });
  flush();
  return { flush, requests };
}

test("login fetches and persists the profile name without changing token or role", async (t) => {
  const provider = mountAuth(t);
  await provider.flush().login({ email: "person@example.test", password: "test-only" });
  const signedIn = provider.flush().user;
  assert.equal(provider.requests.length, 1);
  provider.requests[0].resolve({ name: "Abhay Thakur", email: signedIn.email, role: signedIn.role });
  await Promise.resolve();
  assert.deepEqual(provider.flush().user, { ...signedIn, name: "Abhay Thakur" });
  assert.deepEqual(storage.readAuthSession(), provider.flush().user);
  assert.equal(provider.requests.length, 1);
});

test("browser refresh retains the saved name and fetches the current profile", async (t) => {
  storage.writeAuthSession(session({ name: "Previous Name" }));
  const provider = mountAuth(t);
  assert.equal(provider.flush().user.name, "Previous Name");
  provider.requests[0].resolve({ name: "Updated Name" });
  await Promise.resolve();
  assert.equal(provider.flush().user.name, "Updated Name");
  assert.equal(storage.readAuthSession().name, "Updated Name");
});

test("an old session without a name is enriched on refresh", async (t) => {
  storage.writeAuthSession(session());
  const provider = mountAuth(t);
  assert.equal(provider.flush().user.name, undefined);
  provider.requests[0].resolve({ name: "Abhay Thakur" });
  await Promise.resolve();
  user = provider.flush().user;
  assert.equal(greeting(), "Welcome back, Abhay Thakur");
  assert.equal(storage.readAuthSession().name, "Abhay Thakur");
});

test("a profile outage keeps the saved session and display name", async (t) => {
  const saved = session({ name: "Abhay Thakur" });
  storage.writeAuthSession(saved);
  const provider = mountAuth(t);
  provider.requests[0].reject(new Error("profile unavailable"));
  await Promise.resolve();
  await Promise.resolve();
  assert.deepEqual(provider.flush().user, saved);
  assert.deepEqual(storage.readAuthSession(), saved);
});

test("a late profile response cannot restore a logged-out session", async (t) => {
  storage.writeAuthSession(session());
  const provider = mountAuth(t);
  provider.flush().logout();
  provider.flush();
  provider.requests[0].resolve({ name: "Old User" });
  await Promise.resolve();
  assert.equal(provider.flush().user, null);
  assert.equal(storage.readAuthSession(), null);
});

test("cross-tab account changes cannot receive a previous account's profile", async (t) => {
  storage.writeAuthSession(session());
  const provider = mountAuth(t);
  const other = session({ email: "other@example.test", name: "Other User", role: "VIEWER" });
  other.token += "-other";
  localStorage.setItem(storage.AUTH_STORAGE_KEY, JSON.stringify(other));
  window.dispatchEvent(Object.assign(new Event("storage"), { key: storage.AUTH_STORAGE_KEY }));
  assert.deepEqual(provider.flush().user, other);
  provider.requests[0].resolve({ name: "Old User" });
  await Promise.resolve();
  assert.deepEqual(provider.flush().user, other);
  assert.deepEqual(storage.readAuthSession(), other);
});

test("legacy null names survive storage, while invalid name types are rejected", () => {
  const legacy = session({ name: null });
  storage.writeAuthSession(legacy);
  assert.deepEqual(storage.readAuthSession(), legacy);
  assert.throws(() => storage.writeAuthSession(session({ name: 42 })), /invalid/);
});
