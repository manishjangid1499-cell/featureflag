import assert from "node:assert/strict";
import test from "node:test";
import {
  getUserTimeZone,
  utcIsoToLocalInputValue,
  localInputValueToUtcIso,
  resolveSchedulePayloadValue,
  validateScheduleInputs,
  formatScheduleDisplay,
} from "../src/utils/scheduleTime.ts";

test("Asia/Kolkata 18:00 input maps to 12:30Z when evaluated in Asia/Kolkata timezone", () => {
  // In any environment, localInputValueToUtcIso interprets components according to system local time.
  // Here we explicitly test component conversion under current environment.
  const localInput = "2026-10-06T18:00";
  const utcIso = localInputValueToUtcIso(localInput);
  assert.ok(utcIso, "Must produce a valid ISO string");
  assert.match(utcIso, /Z$/, "Must end in Z");

  // If running in Asia/Kolkata (+05:30), 18:00 is 12:30Z
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  if (tz === "Asia/Kolkata" || tz === "Asia/Calcutta") {
    assert.equal(utcIso, "2026-10-06T12:30:00.000Z");
  }
});

test("Repeated open and save of unchanged schedule preserves the exact instant", () => {
  const originalInstant = "2026-10-06T12:30:45.123Z";

  // Cycle 1: Open modal -> populate input
  const input1 = utcIsoToLocalInputValue(originalInstant);
  // User saves without touching input
  const saved1 = resolveSchedulePayloadValue(input1, originalInstant);
  assert.equal(saved1, originalInstant, "Cycle 1 must preserve exact instant");

  // Cycle 2: Next time modal opens with saved1
  const input2 = utcIsoToLocalInputValue(saved1);
  assert.equal(input2, input1, "Input value must remain identical");
  const saved2 = resolveSchedulePayloadValue(input2, saved1);
  assert.equal(saved2, originalInstant, "Cycle 2 must preserve exact instant");

  // Cycle 3: Repeat again
  const input3 = utcIsoToLocalInputValue(saved2);
  const saved3 = resolveSchedulePayloadValue(input3, saved2);
  assert.equal(saved3, originalInstant, "Cycle 3 must preserve exact instant");
});

test("Non-zero seconds and fractional precision survive unchanged editing", () => {
  const precisionTimestamp = "2026-10-06T14:15:59.999Z";
  const displayInput = utcIsoToLocalInputValue(precisionTimestamp);

  // Even though datetime-local only exposes minute resolution (e.g. YYYY-MM-DDTHH:mm),
  // saving unchanged does NOT truncate the seconds or milliseconds.
  const payload = resolveSchedulePayloadValue(displayInput, precisionTimestamp);
  assert.equal(payload, precisionTimestamp);
  assert.ok(payload.includes(".999Z"));
});

test("Actively editing input converts to new UTC instant ending in Z", () => {
  const originalTimestamp = "2026-10-06T12:30:00.000Z";
  const oldDisplayInput = utcIsoToLocalInputValue(originalTimestamp);

  // User changes date/time
  const newLocalInput = "2026-11-01T09:00";
  assert.notEqual(newLocalInput, oldDisplayInput);

  const newPayload = resolveSchedulePayloadValue(newLocalInput, originalTimestamp);
  assert.ok(newPayload, "Must produce a new UTC string");
  assert.match(newPayload, /Z$/);
  assert.notEqual(newPayload, originalTimestamp);
});

test("Clearing scheduling input returns null", () => {
  const originalTimestamp = "2026-10-06T12:30:00.000Z";
  assert.equal(resolveSchedulePayloadValue("", originalTimestamp), null);
  assert.equal(resolveSchedulePayloadValue("   ", originalTimestamp), null);
  assert.equal(resolveSchedulePayloadValue("", null), null);
});

test("Schedule validation accepts valid ordering and rejects reversed dates", () => {
  // Valid ordering
  assert.equal(validateScheduleInputs("2026-10-06T10:00", "2026-10-06T12:00"), null);
  // Identical boundary (exact instant)
  assert.equal(validateScheduleInputs("2026-10-06T10:00", "2026-10-06T10:00"), null);
  // Null boundaries
  assert.equal(validateScheduleInputs("", "2026-10-06T12:00"), null);
  assert.equal(validateScheduleInputs("2026-10-06T10:00", ""), null);
  assert.equal(validateScheduleInputs("", ""), null);

  // Reversed ordering
  const reversedError = validateScheduleInputs("2026-10-06T15:00", "2026-10-06T10:00");
  assert.equal(reversedError, "End date must be after start date.");
});

test("User timezone helper returns a non-empty timezone identifier", () => {
  const tz = getUserTimeZone();
  assert.ok(tz && typeof tz === "string" && tz.length > 0);
});

test("Schedule display formatting produces readable string", () => {
  const formatted = formatScheduleDisplay("2026-10-06T12:30:00.000Z");
  assert.ok(formatted && formatted.length > 0);
  assert.equal(formatScheduleDisplay(null), "");
  assert.equal(formatScheduleDisplay(""), "");
  assert.equal(formatScheduleDisplay("invalid-date"), "");
});
