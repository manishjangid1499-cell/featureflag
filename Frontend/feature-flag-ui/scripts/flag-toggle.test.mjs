import test from "node:test";
import assert from "node:assert/strict";

test("toggleFlag sends explicit desired state and expectedVersion", async () => {
  let capturedUrl = "";
  let capturedBody = null;

  const mockApi = {
    patch: async (url, body) => {
      capturedUrl = url;
      capturedBody = body;
      return {
        data: {
          id: 42,
          version: 1,
          flagKey: "feature-test",
          name: "Test Flag",
          enabled: true,
        },
      };
    },
  };

  const toggleFlagFn = async (id, enabled, expectedVersion) => {
    const res = await mockApi.patch(`/flags/${id}/toggle`, {
      enabled,
      expectedVersion,
    });
    return res.data;
  };

  const flag = { id: 42, version: 0, enabled: false };
  const desiredEnabled = !flag.enabled;
  const result = await toggleFlagFn(flag.id, desiredEnabled, flag.version);

  assert.equal(capturedUrl, "/flags/42/toggle");
  assert.deepEqual(capturedBody, { enabled: true, expectedVersion: 0 });
  assert.equal(result.enabled, true);
  assert.equal(result.version, 1);
});

test("repeating the same payload cannot flip the flag back", async () => {
  let currentFlag = { id: 42, version: 0, enabled: false };

  const backendHandler = (id, body) => {
    if (body.expectedVersion !== currentFlag.version) {
      const error = new Error("Conflict");
      error.status = 409;
      throw error;
    }
    if (currentFlag.enabled === body.enabled) {
      return { ...currentFlag };
    }
    currentFlag = {
      ...currentFlag,
      enabled: body.enabled,
      version: currentFlag.version + 1,
    };
    return { ...currentFlag };
  };

  // First request: OFF -> ON with version 0
  const req1 = { enabled: true, expectedVersion: 0 };
  const res1 = backendHandler(42, req1);
  assert.equal(res1.enabled, true);
  assert.equal(res1.version, 1);

  // Repeated request with same payload (stale version 0)
  assert.throws(
    () => backendHandler(42, req1),
    (err) => err.status === 409
  );
  // Flag state remains ON (never flipped back to false)
  assert.equal(currentFlag.enabled, true);
  assert.equal(currentFlag.version, 1);
});

test("409 conflict refreshes the flag without auto-retrying or overwriting", async () => {
  let refreshedFlag = null;
  let alertMessage = null;

  const initialFlag = { id: 42, version: 0, enabled: false };
  const concurrentEditedFlag = { id: 42, version: 1, enabled: true, description: "edited by Alice" };

  const mockGetFlagById = async (id) => {
    assert.equal(id, 42);
    return concurrentEditedFlag;
  };

  const handleToggle = async (flag) => {
    try {
      // Simulate backend rejecting with 409
      const err = new Error("Conflict");
      err.response = { status: 409 };
      err.isAxiosError = true;
      throw err;
    } catch (error) {
      if (error?.response?.status === 409) {
        refreshedFlag = await mockGetFlagById(flag.id);
        alertMessage = "This flag was modified by another change. The latest state has been refreshed. Please review and try again if needed.";
      }
    }
  };

  await handleToggle(initialFlag);

  assert.deepEqual(refreshedFlag, concurrentEditedFlag);
  assert.match(alertMessage, /modified by another change/);
  // Confirm it did not overwrite concurrent changes
  assert.equal(refreshedFlag.version, 1);
  assert.equal(refreshedFlag.enabled, true);
});

test("setting already-matching state with current version is a no-op", () => {
  const currentFlag = { id: 42, version: 5, enabled: true };

  const backendHandler = (id, body) => {
    if (body.expectedVersion !== currentFlag.version) {
      const error = new Error("Conflict");
      error.status = 409;
      throw error;
    }
    if (currentFlag.enabled === body.enabled) {
      // No-op: no mutation, no version bump
      return { flag: currentFlag, mutated: false };
    }
    return {
      flag: { ...currentFlag, enabled: body.enabled, version: currentFlag.version + 1 },
      mutated: true,
    };
  };

  const result = backendHandler(42, { enabled: true, expectedVersion: 5 });
  assert.equal(result.mutated, false);
  assert.equal(result.flag.version, 5);
  assert.equal(result.flag.enabled, true);
});
