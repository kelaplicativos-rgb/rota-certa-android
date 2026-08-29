"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const DateContract = require("../../public/date-selection.js");

test("global date contract exposes the same four modes as Android", () => {
  assert.deepEqual(DateContract.MODES, {
    SINGLE: "SINGLE",
    MULTIPLE: "MULTIPLE",
    RANGE: "RANGE",
    MONTH: "MONTH",
  });
});

test("multiple dates can be selected and toggled off", () => {
  let selection = { mode: DateContract.MODES.MULTIPLE, dates: [] };
  selection = DateContract.applySelection(selection, "2026-09-05");
  selection = DateContract.applySelection(selection, "2026-09-07");
  selection = DateContract.applySelection(selection, "2026-09-12");
  assert.deepEqual(selection.dates, ["2026-09-05", "2026-09-07", "2026-09-12"]);

  selection = DateContract.applySelection(selection, "2026-09-07");
  assert.deepEqual(selection.dates, ["2026-09-05", "2026-09-12"]);
});

test("range expands every date inclusively", () => {
  let selection = { mode: DateContract.MODES.RANGE, dates: [] };
  selection = DateContract.applySelection(selection, "2026-09-05");
  selection = DateContract.applySelection(selection, "2026-09-12");

  assert.equal(selection.dates.length, 8);
  assert.equal(selection.dates[0], "2026-09-05");
  assert.equal(selection.dates.at(-1), "2026-09-12");
});

test("month selection can start at today instead of past days", () => {
  const selection = DateContract.selectMonth(2026, 8, "2026-08-29");
  assert.deepEqual(selection.dates, ["2026-08-29", "2026-08-30", "2026-08-31"]);
});

test("single selection and friendly today formatting use ISO internally", () => {
  const selection = DateContract.applySelection(
    { mode: DateContract.MODES.SINGLE, dates: [] },
    "2026-08-29",
  );
  assert.deepEqual(selection.dates, ["2026-08-29"]);
  assert.equal(
    DateContract.formatFriendly("2026-08-29", { todayKey: "2026-08-29", locale: "pt-BR" }),
    "Hoje",
  );
  assert.equal(DateContract.normalizeKey("2026-02-31"), "");
});
