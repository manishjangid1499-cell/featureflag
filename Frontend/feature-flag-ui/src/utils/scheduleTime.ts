const pad = (n: number) => n.toString().padStart(2, "0");

/**
 * Returns the current user/browser timezone identifier (e.g. "Asia/Kolkata", "America/New_York", "UTC").
 */
export function getUserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

/**
 * Converts an absolute UTC ISO string (e.g. "2026-10-06T12:30:45.123Z")
 * to the browser-local representation formatted for <input type="datetime-local"> ("YYYY-MM-DDTHH:mm").
 */
export function utcIsoToLocalInputValue(utcIso: string | null | undefined): string {
  if (!utcIso) return "";
  const d = new Date(utcIso);
  if (isNaN(d.getTime())) return "";
  const year = d.getFullYear();
  const month = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

/**
 * Parses a browser-local datetime-local string (e.g. "2026-10-06T18:00")
 * into an absolute UTC ISO string ending in "Z" (e.g. "2026-10-06T12:30:00.000Z").
 */
export function localInputValueToUtcIso(localValue: string | null | undefined): string | null {
  if (!localValue || !localValue.trim()) return null;
  const parts = localValue.trim().split("T");
  if (parts.length !== 2) return null;
  const [datePart, timePart] = parts;
  const [year, month, day] = datePart.split("-").map(Number);
  const timePieces = timePart.split(":").map(Number);
  if (isNaN(year) || isNaN(month) || isNaN(day) || timePieces.some(isNaN)) {
    return null;
  }
  const hours = timePieces[0] ?? 0;
  const minutes = timePieces[1] ?? 0;
  const seconds = timePieces[2] ?? 0;
  const d = new Date(year, month - 1, day, hours, minutes, seconds, 0);
  if (isNaN(d.getTime())) return null;
  return d.toISOString();
}

/**
 * Resolves the payload value for an edited schedule instant.
 * If the user did not modify the datetime-local input, the original UTC instant
 * is preserved unchanged (retaining non-zero seconds and millisecond precision).
 * If the input was cleared, returns null.
 * If the input was actively edited, returns the newly computed UTC ISO string.
 */
export function resolveSchedulePayloadValue(
  currentInputValue: string,
  originalUtcIso: string | null | undefined
): string | null {
  if (!currentInputValue || !currentInputValue.trim()) {
    return null;
  }
  if (originalUtcIso) {
    const initialInputValue = utcIsoToLocalInputValue(originalUtcIso);
    if (currentInputValue === initialInputValue) {
      return originalUtcIso;
    }
  }
  return localInputValueToUtcIso(currentInputValue);
}

/**
 * Validates start and end scheduling inputs.
 * Returns an error message if invalid, or null if valid.
 */
export function validateScheduleInputs(
  startDateInput: string,
  endDateInput: string,
  originalStartIso?: string | null,
  originalEndIso?: string | null
): string | null {
  const startIso = resolveSchedulePayloadValue(startDateInput, originalStartIso);
  const endIso = resolveSchedulePayloadValue(endDateInput, originalEndIso);

  if (startDateInput && !startIso) {
    return "Invalid start date format.";
  }
  if (endDateInput && !endIso) {
    return "Invalid end date format.";
  }
  if (startIso && endIso) {
    const startTime = new Date(startIso).getTime();
    const endTime = new Date(endIso).getTime();
    if (endTime < startTime) {
      return "End date must be after start date.";
    }
  }
  return null;
}

/**
 * Formats an instant for user display with timezone information.
 */
export function formatScheduleDisplay(
  utcIso: string | null | undefined,
  includeSeconds = false
): string {
  if (!utcIso) return "";
  const d = new Date(utcIso);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: includeSeconds ? "2-digit" : undefined,
  });
}
