"use strict";

(function attachRotaCertaDateContract(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.RotaCertaDateContract = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function buildRotaCertaDateContract() {
  const MODES = Object.freeze({
    SINGLE: "SINGLE",
    MULTIPLE: "MULTIPLE",
    RANGE: "RANGE",
    MONTH: "MONTH",
  });

  const pad2 = (value) => String(value).padStart(2, "0");

  function keyFromParts(year, month, day) {
    const y = Number(year);
    const m = Number(month);
    const d = Number(day);
    if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return "";
    const date = new Date(y, m - 1, d);
    if (
      date.getFullYear() !== y ||
      date.getMonth() !== m - 1 ||
      date.getDate() !== d
    ) return "";
    return `${String(y).padStart(4, "0")}-${pad2(m)}-${pad2(d)}`;
  }

  function keyFromDate(date) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";
    return keyFromParts(date.getFullYear(), date.getMonth() + 1, date.getDate());
  }

  function parseKey(key) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(key || "").trim());
    if (!match) return null;
    const normalized = keyFromParts(Number(match[1]), Number(match[2]), Number(match[3]));
    if (!normalized) return null;
    const [year, month, day] = normalized.split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function normalizeKey(key) {
    const date = parseKey(key);
    return date ? keyFromDate(date) : "";
  }

  function todayKey(now = new Date()) {
    return keyFromDate(now);
  }

  function compareKeys(left, right) {
    const a = normalizeKey(left);
    const b = normalizeKey(right);
    if (!a || !b) return null;
    return a === b ? 0 : (a < b ? -1 : 1);
  }

  function isBefore(left, right) {
    return compareKeys(left, right) === -1;
  }

  function inclusiveKeys(startKey, endKey) {
    const startDate = parseKey(startKey);
    const endDate = parseKey(endKey);
    if (!startDate || !endDate) return [];
    const first = startDate <= endDate ? startDate : endDate;
    const last = startDate <= endDate ? endDate : startDate;
    const result = [];
    for (let date = new Date(first); date <= last; date.setDate(date.getDate() + 1)) {
      result.push(keyFromDate(date));
    }
    return result;
  }

  function normalizeSelection(selection) {
    const mode = Object.values(MODES).includes(selection?.mode) ? selection.mode : MODES.MULTIPLE;
    const dates = Array.from(new Set((selection?.dates || []).map(normalizeKey).filter(Boolean))).sort();
    return { mode, dates };
  }

  function applySelection(selection, key) {
    const current = normalizeSelection(selection);
    const normalizedKey = normalizeKey(key);
    if (!normalizedKey) return current;
    let dates;
    switch (current.mode) {
      case MODES.SINGLE:
        dates = [normalizedKey];
        break;
      case MODES.MULTIPLE:
        dates = current.dates.includes(normalizedKey)
          ? current.dates.filter((item) => item !== normalizedKey)
          : [...current.dates, normalizedKey].sort();
        break;
      case MODES.RANGE:
        dates = current.dates.length === 1
          ? inclusiveKeys(current.dates[0], normalizedKey)
          : [normalizedKey];
        break;
      case MODES.MONTH: {
        const date = parseKey(normalizedKey);
        const first = keyFromParts(date.getFullYear(), date.getMonth() + 1, 1);
        const last = keyFromParts(date.getFullYear(), date.getMonth() + 2, 0);
        dates = inclusiveKeys(first, last);
        break;
      }
      default:
        dates = [normalizedKey];
    }
    return { mode: current.mode, dates };
  }

  function selectMonth(year, month, minKey = "") {
    const first = keyFromParts(year, month, 1);
    const last = keyFromParts(year, Number(month) + 1, 0);
    if (!first || !last) return { mode: MODES.MONTH, dates: [] };
    const min = normalizeKey(minKey);
    const start = min && min > first ? min : first;
    return {
      mode: MODES.MONTH,
      dates: start > last ? [] : inclusiveKeys(start, last),
    };
  }

  function formatFriendly(key, options = {}) {
    const normalized = normalizeKey(key);
    if (!normalized) return options.placeholder || "Data";
    const currentToday = normalizeKey(options.todayKey || todayKey());
    if (options.useTodayLabel !== false && normalized === currentToday) return "Hoje";
    const date = parseKey(normalized);
    return new Intl.DateTimeFormat(options.locale || "pt-BR", {
      weekday: "short",
      day: "2-digit",
      month: "short",
    }).format(date).replace(/\.$/, "");
  }

  return Object.freeze({
    MODES,
    keyFromParts,
    keyFromDate,
    parseKey,
    normalizeKey,
    todayKey,
    compareKeys,
    isBefore,
    inclusiveKeys,
    normalizeSelection,
    applySelection,
    selectMonth,
    formatFriendly,
  });
});
