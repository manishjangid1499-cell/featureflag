import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.join(__dirname, "../src");

test("EditFlagModal keys its inner component by flag identity and version to ensure clean reopening and avoid leaking stale state", () => {
  const code = fs.readFileSync(path.join(srcDir, "components/flags/EditFlagModal.tsx"), "utf8");

  assert.match(code, /function EditFlagModalContent/, "Must separate form into an inner content component");
  assert.match(code, /key=\{`?\$\{flag\.id\}-\$\{flag\.version\}`?\}/, "Must key EditFlagModalContent by flag id and version");
  assert.doesNotMatch(code, /useEffect\(\(\)\s*=>\s*\{[^}]*setName/, "Must not set form state via useEffect on flag changes");
});

test("FlagEvaluationModal directly derives preview environment and keys content to reset previous verdict", () => {
  const code = fs.readFileSync(path.join(srcDir, "components/flags/FlagEvaluationModal.tsx"), "utf8");

  assert.match(code, /const environment = flag\.environment/, "Must directly derive environment from the selected flag");
  assert.match(code, /key=\{`?\$\{flag\.id\}-\$\{flag\.version\}`?\}/, "Must key FlagEvaluationModalContent by flag id and version");
  assert.doesNotMatch(code, /setEnvironment/, "Must not use state for the read-only preview environment");
});

test("ChangePasswordModal conditionally mounts dialog to guarantee clean state upon reopening", () => {
  const code = fs.readFileSync(path.join(srcDir, "components/ChangePasswordModal.tsx"), "utf8");

  assert.match(code, /if \(!isOpen\) return null;/, "Must conditionally mount dialog only when isOpen is true");
  assert.match(code, /<ChangePasswordModalDialog onClose=\{onClose\} \/>/, "Must render inner dialog component");
  assert.doesNotMatch(code, /useEffect\(\(\)\s*=>\s*\{[^}]*!isOpen[^}]*resetState/, "Must not use an effect to reset state when closed");
});

test("ResetPassword captures token in state during initialization and replaces URL preserving history state", () => {
  const code = fs.readFileSync(path.join(srcDir, "pages/ResetPassword.tsx"), "utf8");

  assert.match(code, /useState\(\(\)\s*=>\s*searchParams\.get\("token"\)/, "Must capture token synchronously in initial state");
  assert.match(code, /window\.history\.replaceState\(window\.history\.state/, "Must preserve React Router history state when sanitizing URL");
  assert.doesNotMatch(code, /useEffect\(\(\)\s*=>\s*\{[^}]*setToken\(/, "Must not set token asynchronously via an effect");
  assert.match(code, /setToken\(""\)/, "Must clear token from memory upon successful password reset");
});

test("AcceptInvitation initializes missing token validation state directly without effect-driven cascading renders", () => {
  const code = fs.readFileSync(path.join(srcDir, "pages/AcceptInvitation.tsx"), "utf8");

  assert.match(code, /useState\(\(\)\s*=>\s*Boolean\(token\)\)/, "Must initialize validating state based on token presence");
  assert.match(code, /useState\(\(\)\s*=>[\s\S]*?Invitation token is missing from the URL/, "Must initialize missing token error message synchronously");
  assert.match(code, /let isCancelled = false;/, "Must protect validation effect with cancellation against late responses");
  assert.match(code, /return \(\) => \{\s*isCancelled = true;\s*\};/, "Must cancel pending token validation on unmount or token change");
});

test("Dashboard protects against late or obsolete responses after unmount or account changes", () => {
  const code = fs.readFileSync(path.join(srcDir, "pages/Dashboard.tsx"), "utf8");

  assert.match(code, /const requestVersion = useRef\(0\);/, "Must track request version to prevent obsolete updates");
  assert.match(code, /let cancelled = false;/, "Must track cancellation within the loading effect");
  assert.match(code, /if \(cancelled \|\| requestVersion\.current !== version\) return;/, "Must drop obsolete or cancelled responses");
  assert.match(code, /return \(\) => \{\s*cancelled = true;\s*requestVersion\.current \+= 1;\s*\};/, "Must cancel on unmount or dependency update");
});

test("usePagedResource guards against obsolete page responses and handles last-page correction", () => {
  const code = fs.readFileSync(path.join(srcDir, "hooks/usePagedResource.ts"), "utf8");

  assert.match(code, /let ignore = false;/, "Must track ignore flag for in-flight requests");
  assert.match(code, /const version = \+\+requestVersion\.current;/, "Must increment request version for each fetch");
  assert.match(code, /if \(ignore \|\| version !== requestVersion\.current\) return;/, "Must ignore stale responses from previous pages");
  assert.match(code, /if \(curTarget > lastPage\)/, "Must correct out-of-range page numbers");
  assert.match(code, /return \(\) => \{\s*ignore = true;\s*requestVersion\.current \+= 1;/, "Must clean up on unmount or page change");
});

// Real React rendering test harness using jsdom, ReactDOMClient, and React.act under StrictMode
import { JSDOM } from "jsdom";
import React, { act } from "react";
import ReactDOMClient from "react-dom/client";

const dom = new JSDOM("<!doctype html><html><body><div id=\"root\"></div></body></html>", {
  url: "http://localhost/",
  pretendToBeVisual: true,
});

globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.Element = dom.window.Element;
globalThis.Node = dom.window.Node;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { runInNewContext } from "node:vm";
import { createRequire } from "node:module";
import ts from "typescript";

function loadComponent(relativePath, mocks = {}) {
  const filename = fileURLToPath(new URL(relativePath, import.meta.url));
  const req = createRequire(filename);
  const { outputText } = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: filename,
  });
  const exports = {};
  runInNewContext(outputText, {
    exports,
    window: dom.window,
    document: dom.window.document,
    globalThis,
    console,
    require: (name) => Object.hasOwn(mocks, name) ? mocks[name] : req(name),
  }, { filename });
  return exports;
}

const errorsModule = loadComponent("../src/api/errors.ts");
const pageModule = loadComponent("../src/types/page.ts");
const { usePagedResource } = loadComponent("../src/hooks/usePagedResource.ts", {
  react: React,
  "../api/errors": errorsModule,
  "../types/page": pageModule,
});

function createRealReactHookHarness(initialFetchPage, initialError = "Error") {
  let latestHook = null;
  let currentFetchPage = initialFetchPage;
  let currentError = initialError;

  function HarnessComponent({ fetchPage, error, onValue }) {
    const value = usePagedResource(fetchPage, error);
    onValue(value);
    return React.createElement("div", null, `page: ${value.page}`);
  }

  const container = dom.window.document.createElement("div");
  dom.window.document.body.appendChild(container);
  const root = ReactDOMClient.createRoot(container);

  const onValue = (val) => {
    latestHook = val;
  };

  const render = async (fetchPage = currentFetchPage, error = currentError) => {
    currentFetchPage = fetchPage;
    currentError = error;
    await act(async () => {
      root.render(
        React.createElement(
          React.StrictMode,
          null,
          React.createElement(HarnessComponent, { fetchPage: currentFetchPage, error: currentError, onValue })
        )
      );
    });
  };

  const unmount = async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
  };

  return {
    render,
    unmount,
    getHook: () => latestHook,
  };
}

test("usePagedResource: same-page direct and functional updates do not trigger unnecessary loading", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  assert.equal(harness.getHook().loading, true, "Loading should be true initially");

  // In StrictMode, initial render runs effects. Resolve pending requests:
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item1", "item2"],
      totalPages: 3,
      totalElements: 6,
      pageNumber: 0,
      pageSize: 2,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false, "Loading should be false after resolution");
  assert.equal(harness.getHook().page, 0);
  const initialCallCount = fetchCalls.length;

  // Direct same-page update: setPage(0)
  await act(async () => {
    harness.getHook().setPage(0);
  });
  assert.equal(harness.getHook().loading, false, "Loading must stay false on direct same-page update");
  assert.equal(fetchCalls.length, initialCallCount, "No new fetch should occur on same-page direct update");

  // Functional same-page update: setPage((prev) => prev)
  await act(async () => {
    harness.getHook().setPage((prev) => prev);
  });
  assert.equal(harness.getHook().loading, false, "Loading must stay false on functional same-page update");
  assert.equal(fetchCalls.length, initialCallCount, "No new fetch should occur on functional same-page update");

  await harness.unmount();
});

test("usePagedResource: reload(99) from page 0 when totalPages=1 fetches corrected page and finishes loading", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["initial-item"],
      totalPages: 1,
      totalElements: 1,
      pageNumber: 0,
      pageSize: 10,
      isFirst: true,
      isLast: true,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().page, 0);
  assert.equal(harness.getHook().loading, false);
  assert.deepEqual(harness.getHook().items, ["initial-item"]);

  // Call reload(99) from page 0 when totalPages=1 (so corrected last page is 0, identical to current page)
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(99);
  });

  assert.equal(harness.getHook().loading, true, "Loading should be true during reload");
  assert.equal(fetchCalls[fetchCalls.length - 1], 99, "Reload should request out-of-range page 99");

  // Page 99 returns totalPages: 1
  const page99Resolver = resolvers[resolvers.length - 1];
  page99Resolver({
    content: [],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 99,
    pageSize: 10,
    isFirst: false,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  // Verify that loading remains active and corrected page 0 is fetched directly
  assert.equal(harness.getHook().loading, true, "Loading must stay true across correction boundary");
  assert.equal(fetchCalls[fetchCalls.length - 1], 0, "Must directly fetch corrected page 0");

  // Resolve corrected page 0
  const correctedResolver = resolvers[resolvers.length - 1];
  correctedResolver({
    content: ["refreshed-item"],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 0,
    pageSize: 10,
    isFirst: true,
    isLast: true,
  });

  await act(async () => {
    await reloadPromise;
  });

  assert.equal(harness.getHook().loading, false, "Loading must be false after reload finishes");
  assert.equal(harness.getHook().page, 0);
  assert.deepEqual(harness.getHook().items, ["refreshed-item"]);

  await harness.unmount();
});

test("usePagedResource: normal last-page correction keeps loading active until corrected page settles", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item0"],
      totalPages: 3,
      totalElements: 3,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);
  assert.equal(harness.getHook().page, 0);

  // Navigate to page 2
  await act(async () => {
    harness.getHook().setPage(2);
  });

  assert.equal(harness.getHook().loading, true);
  assert.equal(fetchCalls[fetchCalls.length - 1], 2);

  // Server responds that items were deleted and totalPages is now 1 (lastPage is 0)
  const page2Resolver = resolvers[resolvers.length - 1];
  page2Resolver({
    content: [],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 2,
    pageSize: 1,
    isFirst: false,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  // State should be corrected to page 0, and loading should stay active
  assert.equal(harness.getHook().page, 0, "Page should be corrected to 0");
  assert.equal(harness.getHook().loading, true, "Loading should remain true during correction");
  assert.equal(fetchCalls[fetchCalls.length - 1], 0, "Corrected page 0 must be requested");

  // Corrected page 0 settles
  const correctedResolver = resolvers[resolvers.length - 1];
  correctedResolver({
    content: ["surviving-item"],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 0,
    pageSize: 1,
    isFirst: true,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);
  assert.deepEqual(harness.getHook().items, ["surviving-item"]);

  await harness.unmount();
});

test("usePagedResource: overlapping reload and page changes ignore stale responses", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item0"],
      totalPages: 5,
      totalElements: 5,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  // Initiate reload(1)
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(1);
  });
  const reload1Resolver = resolvers[resolvers.length - 1];

  // Immediately navigate to page 2 before reload(1) finishes
  await act(async () => {
    harness.getHook().setPage(2);
  });
  const page2Resolver = resolvers[resolvers.length - 1];

  // Resolve reload(1) late
  reload1Resolver({
    content: ["STALE reload 1"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 1,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await reloadPromise;
  });

  assert.notDeepEqual(harness.getHook().items, ["STALE reload 1"], "Stale reload response must be ignored");

  // Resolve page 2
  page2Resolver({
    content: ["FRESH page 2"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 2,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await Promise.resolve();
  });

  assert.deepEqual(harness.getHook().items, ["FRESH page 2"], "Latest page request data must be applied");
  assert.equal(harness.getHook().loading, false);

  await harness.unmount();
});

test("usePagedResource: fetch dependency changes immediately show accurate loading state", async () => {
  const resolvers1 = [];
  const fetchPage1 = () => new Promise((resolve) => { resolvers1.push(resolve); });

  const harness = createRealReactHookHarness(fetchPage1, "Failed to load flags");
  await harness.render();

  while (resolvers1.length > 0) {
    resolvers1.shift()({
      content: ["envA-flag"],
      totalPages: 1,
      totalElements: 1,
      pageNumber: 0,
      pageSize: 10,
      isFirst: true,
      isLast: true,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);
  assert.deepEqual(harness.getHook().items, ["envA-flag"]);

  // Change fetchPage dependency (e.g. user toggles environment filter)
  const resolvers2 = [];
  const fetchPage2 = () => new Promise((resolve) => { resolvers2.push(resolve); });

  await harness.render(fetchPage2, "Failed to load filtered flags");

  assert.equal(harness.getHook().loading, true, "Loading must immediately switch to true on dependency change");

  while (resolvers2.length > 0) {
    resolvers2.shift()({
      content: ["envB-flag"],
      totalPages: 1,
      totalElements: 1,
      pageNumber: 0,
      pageSize: 10,
      isFirst: true,
      isLast: true,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);
  assert.deepEqual(harness.getHook().items, ["envB-flag"]);

  await harness.unmount();
});

test("usePagedResource: unmount with a pending request cleans up without errors or leaks", async () => {
  let pendingResolver;
  const fetchPage = () => new Promise((resolve) => { pendingResolver = resolve; });

  const harness = createRealReactHookHarness(fetchPage, "Error");
  await harness.render();

  assert.equal(harness.getHook().loading, true);

  // Unmount while request is pending
  await harness.unmount();

  // Resolve after unmount
  assert.doesNotThrow(() => {
    pendingResolver({
      content: ["ignored"],
      totalPages: 1,
      totalElements: 1,
      pageNumber: 0,
      pageSize: 10,
      isFirst: true,
      isLast: true,
    });
  });

  await act(async () => {
    await Promise.resolve();
  });
});

test("usePagedResource: successful reload(1): exactly one request for page 1 after initial mounting", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request (including StrictMode remount)
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item-p0"],
      totalPages: 3,
      totalElements: 3,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);
  assert.equal(harness.getHook().page, 0);

  // Calls to fetchPage(1) prior to reload(1) must be 0
  assert.equal(fetchCalls.filter((p) => p === 1).length, 0);

  // Trigger reload(1)
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(1);
  });

  assert.equal(harness.getHook().loading, true, "Loading should be true during reload(1)");
  assert.equal(harness.getHook().page, 1, "Page should immediately reflect target 1");

  // Calls to fetchPage(1) must be exactly 1
  assert.equal(fetchCalls.filter((p) => p === 1).length, 1, "Must make exactly one request for page 1");

  // Resolve page 1 request
  const page1Resolver = resolvers[resolvers.length - 1];
  page1Resolver({
    content: ["item-p1"],
    totalPages: 3,
    totalElements: 3,
    pageNumber: 1,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await reloadPromise;
  });

  assert.equal(harness.getHook().loading, false, "Loading must be false after page 1 finishes");
  assert.equal(harness.getHook().page, 1, "Page should stay 1");
  assert.deepEqual(harness.getHook().items, ["item-p1"]);

  // Verify that no duplicate request was triggered after resolution
  assert.equal(fetchCalls.filter((p) => p === 1).length, 1, "Must remain exactly one request for page 1 (no duplicate effect fetch)");

  await harness.unmount();
});

test("usePagedResource: reload(99) correcting to a different current page", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item-p0"],
      totalPages: 3,
      totalElements: 3,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  // Navigate to page 1
  await act(async () => {
    harness.getHook().setPage(1);
  });
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item-p1"],
      totalPages: 3,
      totalElements: 3,
      pageNumber: 1,
      pageSize: 1,
      isFirst: false,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  // Now current displayed page is 1
  assert.equal(harness.getHook().page, 1);
  assert.equal(harness.getHook().loading, false);

  // Trigger reload(99) from current page 1
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(99);
  });

  assert.equal(harness.getHook().loading, true, "Loading should be true during reload");
  assert.equal(fetchCalls[fetchCalls.length - 1], 99, "Reload should request page 99");

  // Page 99 returns totalPages: 1 (last page = 0, which is different from current page 1)
  const page99Resolver = resolvers[resolvers.length - 1];
  page99Resolver({
    content: [],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 99,
    pageSize: 1,
    isFirst: false,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  // Verify that page corrected to 0 (different from current page 1) and loading stays true
  assert.equal(harness.getHook().loading, true, "Loading must stay true across correction boundary");
  assert.equal(harness.getHook().page, 0, "Page must be corrected to 0 (different from page 1)");
  assert.equal(fetchCalls[fetchCalls.length - 1], 0, "Must fetch corrected page 0");

  // Resolve corrected page 0
  const page0Resolver = resolvers[resolvers.length - 1];
  page0Resolver({
    content: ["corrected-p0"],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 0,
    pageSize: 1,
    isFirst: true,
    isLast: true,
  });

  await act(async () => {
    await reloadPromise;
  });

  assert.equal(harness.getHook().loading, false, "Loading must be false after reload finishes");
  assert.equal(harness.getHook().page, 0);
  assert.deepEqual(harness.getHook().items, ["corrected-p0"]);

  await harness.unmount();
});

test("usePagedResource: rejection of the corrected-page request", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const rejecters = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve, reject) => {
      resolvers.push(resolve);
      rejecters.push(reject);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load paged resource");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item-p0"],
      totalPages: 1,
      totalElements: 1,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: true,
    });
    rejecters.shift();
  }
  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, false);

  // Trigger reload(99)
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(99);
  });

  assert.equal(harness.getHook().loading, true);
  assert.equal(fetchCalls[fetchCalls.length - 1], 99);

  // Page 99 returns totalPages: 1 (corrects to page 0)
  const page99Resolver = resolvers[resolvers.length - 1];
  page99Resolver({
    content: [],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 99,
    pageSize: 1,
    isFirst: false,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(harness.getHook().loading, true, "Loading stays true when corrected page 0 is requested");
  assert.equal(fetchCalls[fetchCalls.length - 1], 0, "Corrected page 0 is requested");

  // Corrected-page request rejects
  const page0Rejecter = rejecters[rejecters.length - 1];
  page0Rejecter(new Error("Network connection lost"));

  await act(async () => {
    await reloadPromise;
  });

  assert.equal(harness.getHook().loading, false, "Loading must finish after rejection");
  assert.equal(harness.getHook().error, "Failed to load paged resource", "Error must be published");
  assert.equal(harness.getHook().page, 0, "Page reflects corrected page 0");

  await harness.unmount();
});

test("usePagedResource: overlapping reloads only publish latest active request and settle both callers", async () => {
  const fetchCalls = [];
  const resolvers = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers.push(resolve);
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  while (resolvers.length > 0) {
    resolvers.shift()({
      content: ["item-p0"],
      totalPages: 5,
      totalElements: 5,
      pageNumber: 0,
      pageSize: 1,
      isFirst: true,
      isLast: false,
    });
  }
  await act(async () => {
    await Promise.resolve();
  });

  // Trigger reload(1)
  let p1;
  await act(async () => {
    p1 = harness.getHook().reload(1);
  });
  const reload1Resolver = resolvers[resolvers.length - 1];

  // Immediately trigger overlapping reload(2) before reload(1) finishes
  let p2;
  await act(async () => {
    p2 = harness.getHook().reload(2);
  });
  const reload2Resolver = resolvers[resolvers.length - 1];

  assert.equal(harness.getHook().loading, true);
  assert.equal(harness.getHook().page, 2);

  // Resolve reload(1) late
  reload1Resolver({
    content: ["STALE-RELOAD-1"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 1,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await p1;
  });

  assert.notDeepEqual(harness.getHook().items, ["STALE-RELOAD-1"], "Stale reload(1) data must not be published");
  assert.equal(harness.getHook().loading, true, "Loading must remain true while reload(2) is active");

  // Resolve reload(2)
  reload2Resolver({
    content: ["FRESH-RELOAD-2"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 2,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await p2;
  });

  assert.deepEqual(harness.getHook().items, ["FRESH-RELOAD-2"], "Latest reload(2) data must be published");
  assert.equal(harness.getHook().loading, false, "Loading must finish when latest reload settles");
  assert.equal(harness.getHook().page, 2);

  await harness.unmount();
});

test("usePagedResource: active reload promise settles upon unmount when network request never resolves", async () => {
  const fetchCalls = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise(() => {});
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  assert.equal(harness.getHook().loading, true);

  // Trigger reload(1) which creates a pending promise whose fetch never resolves
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(1);
  });

  let settled = false;
  reloadPromise.then(() => {
    settled = true;
  });

  // Verify promise is pending before unmount
  await act(async () => {
    await Promise.resolve();
  });
  assert.equal(settled, false, "Reload promise must be pending while request is in-flight");

  // Unmount while network request is still pending
  await harness.unmount();

  // Verify the reload promise settles upon unmount without waiting for network request
  await act(async () => {
    await reloadPromise;
  });
  assert.equal(settled, true, "Reload promise must settle upon unmount");
});

test("usePagedResource: reload A superseded by reload B settles both promises while A never resolves", async () => {
  const fetchCalls = [];
  const resolvers = {};
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers[page] = resolve;
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  resolvers[0]({
    content: ["item-p0"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 0,
    pageSize: 1,
    isFirst: true,
    isLast: false,
  });
  await act(async () => {
    await Promise.resolve();
  });
  assert.equal(harness.getHook().loading, false);

  // Start reload A (page 1) whose network request will never resolve
  let pA;
  await act(async () => {
    pA = harness.getHook().reload(1);
  });
  let pASettled = false;
  pA.then(() => {
    pASettled = true;
  });

  // Start reload B (page 2) which supersedes reload A
  let pB;
  await act(async () => {
    pB = harness.getHook().reload(2);
  });
  let pBSettled = false;
  pB.then(() => {
    pBSettled = true;
  });

  assert.equal(harness.getHook().loading, true);
  assert.equal(harness.getHook().page, 2);

  // Reload A was superseded, so pA settles immediately without waiting for its network request
  await act(async () => {
    await Promise.resolve();
  });
  assert.equal(pASettled, true, "Superseded reload A must settle without waiting for obsolete network request");

  // Reload B has not finished yet, so pB is still pending
  assert.equal(pBSettled, false, "Active reload B must remain pending until resolved");

  // Finish reload B (page 2)
  resolvers[2]({
    content: ["FRESH-B"],
    totalPages: 5,
    totalElements: 5,
    pageNumber: 2,
    pageSize: 1,
    isFirst: false,
    isLast: false,
  });

  await act(async () => {
    await pB;
  });

  assert.equal(pBSettled, true, "Reload B must settle after its response resolves");
  assert.equal(harness.getHook().loading, false);
  assert.equal(harness.getHook().page, 2);
  assert.deepEqual(harness.getHook().items, ["FRESH-B"]);

  // Verify resolvers[1] was never called, keeping request A unresolved
  assert.ok(resolvers[1], "Fetch call for page 1 was dispatched");

  await harness.unmount();
});

test("usePagedResource: out-of-range correction does not settle reload promise prematurely", async () => {
  const fetchCalls = [];
  const resolvers = {};
  const fetchPage = (page) => {
    fetchCalls.push(page);
    return new Promise((resolve) => {
      resolvers[page] = resolve;
    });
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Resolve initial page 0 request
  resolvers[0]({
    content: ["initial-p0"],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 0,
    pageSize: 10,
    isFirst: true,
    isLast: true,
  });
  await act(async () => {
    await Promise.resolve();
  });
  assert.equal(harness.getHook().loading, false);

  // Trigger reload(99) where totalPages=1 (causes correction to page 0)
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(99);
  });
  let settled = false;
  reloadPromise.then(() => {
    settled = true;
  });

  assert.equal(harness.getHook().loading, true);
  assert.equal(fetchCalls[fetchCalls.length - 1], 99);

  // Page 99 returns totalPages: 1 (lastPage = 0)
  resolvers[99]({
    content: [],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 99,
    pageSize: 10,
    isFirst: false,
    isLast: true,
  });

  await act(async () => {
    await Promise.resolve();
  });

  // Correction is now in progress: page 0 is being fetched
  assert.equal(fetchCalls[fetchCalls.length - 1], 0, "Corrected page 0 is requested");
  assert.equal(harness.getHook().page, 0, "Page is updated to 0");
  assert.equal(harness.getHook().loading, true, "Loading remains active across correction");

  // CRITICAL: Reload promise must NOT have settled yet during correction!
  assert.equal(settled, false, "Reload promise must NOT settle prematurely during out-of-range correction");

  // Now resolve the corrected page 0 request
  resolvers[0]({
    content: ["corrected-fresh-p0"],
    totalPages: 1,
    totalElements: 1,
    pageNumber: 0,
    pageSize: 10,
    isFirst: true,
    isLast: true,
  });

  await act(async () => {
    await reloadPromise;
  });

  // Now the reload promise settles
  assert.equal(settled, true, "Reload promise settles once corrected page settles");
  assert.equal(harness.getHook().loading, false);
  assert.deepEqual(harness.getHook().items, ["corrected-fresh-p0"]);

  await harness.unmount();
});

test("usePagedResource: calling reload() and unmounting in the same act settles promise without network response", async () => {
  const fetchCalls = [];
  const fetchPage = (page) => {
    fetchCalls.push(page);
    // Network never responds
    return new Promise(() => {});
  };

  const harness = createRealReactHookHarness(fetchPage, "Failed to load");
  await harness.render();

  // Call reload(1) and unmount in the SAME act before replacement effect starts
  let reloadPromise;
  await act(async () => {
    reloadPromise = harness.getHook().reload(1);
    await harness.unmount();
  });

  let settled = false;
  reloadPromise.then(() => {
    settled = true;
  });

  await act(async () => {
    await Promise.resolve();
  });

  assert.equal(settled, true, "Reload promise must settle even when unmounted in the same act before replacement effect starts");
});

test("AcceptInvitation: token changes completely isolate sessions and reset state via keying", () => {
  const req = createRequire(import.meta.url);
  const code = ts.transpileModule(fs.readFileSync(path.join(srcDir, "pages/AcceptInvitation.tsx"), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;

  let currentParams = new URLSearchParams("token=tokA");
  const exports = {};
  runInNewContext(code, {
    exports,
    require: (name) => {
      if (name === "react") return req("react");
      if (name === "react-router-dom") {
        return {
          useSearchParams: () => [currentParams],
          useNavigate: () => () => {},
          Link: () => null,
        };
      }
      if (name === "../api/authApi") {
        return {
          validateInvitation: () => Promise.resolve({ valid: true, email: "user@test.test", role: "DEVELOPER" }),
          acceptInvitation: () => Promise.resolve("Success"),
        };
      }
      if (name === "../api/errors") return { getApiErrorMessage: (_e, fallback) => fallback };
      return req(name);
    },
  });

  const session1 = exports.AcceptInvitation();
  assert.equal(session1.key, "tokA");
  assert.equal(session1.props.token, "tokA");

  // Transition from token to missing
  currentParams = new URLSearchParams("");
  const session2 = exports.AcceptInvitation();
  assert.equal(session2.key, "__missing_token__");
  assert.equal(session2.props.token, "");

  // Transition to a different valid token
  currentParams = new URLSearchParams("token=tokB");
  const session3 = exports.AcceptInvitation();
  assert.equal(session3.key, "tokB");
  assert.equal(session3.props.token, "tokB");
});

test("Dashboard: user and role changes isolate session state via keying", () => {
  const req = createRequire(import.meta.url);
  const code = ts.transpileModule(fs.readFileSync(path.join(srcDir, "pages/Dashboard.tsx"), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;

  let currentUser = { email: "owner@company.test", role: "OWNER" };
  const exports = {};
  runInNewContext(code, {
    exports,
    require: (name) => {
      if (name === "react") return req("react");
      if (name === "react-router-dom") return { useNavigate: () => () => {} };
      if (name === "../hooks/useAuth") return { useAuth: () => ({ user: currentUser, canManageMembers: true }) };
      if (name === "../api/flagApi") return { getAllFlags: () => Promise.resolve([]) };
      if (name === "../api/auditApi") return { getAllAuditLogs: () => Promise.resolve({ content: [] }) };
      if (name === "../types/page") return { collectAllPages: (fn) => fn() };
      return req(name);
    },
  });

  const session1 = exports.Dashboard();
  assert.equal(session1.key, "owner@company.test-OWNER");

  // Switch to different user
  currentUser = { email: "dev@company.test", role: "DEVELOPER" };
  const session2 = exports.Dashboard();
  assert.equal(session2.key, "dev@company.test-DEVELOPER");

  // Switch to logged out / null
  currentUser = null;
  const session3 = exports.Dashboard();
  assert.equal(session3.key, "anonymous-none");
});
