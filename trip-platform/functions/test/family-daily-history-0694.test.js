"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const {
  familyHistoryDayKey0694,
  familyHistoryDayBounds0694,
} = require("../live-tracking-0668");

const root = path.resolve(__dirname, "..", "..");
const backend = fs.readFileSync(path.join(root, "functions", "live-tracking-0668.js"), "utf8");
const index = fs.readFileSync(path.join(root, "functions", "index.js"), "utf8");
const client = fs.readFileSync(path.join(root, "public", "tracking.js"), "utf8");
const html = fs.readFileSync(path.join(root, "public", "tracking.html"), "utf8");

test("daily history uses Sao Paulo calendar boundaries", () => {
  assert.equal(familyHistoryDayKey0694(Date.UTC(2026, 8, 29, 2, 59, 59)), "2026-09-28");
  assert.equal(familyHistoryDayKey0694(Date.UTC(2026, 8, 29, 3, 0, 0)), "2026-09-29");
  const bounds = familyHistoryDayBounds0694("2026-09-29");
  assert.ok(bounds);
  assert.equal(bounds.startMillis, Date.UTC(2026, 8, 29, 3, 0, 0));
  assert.equal(bounds.endMillis, Date.UTC(2026, 8, 30, 3, 0, 0));
  assert.equal(familyHistoryDayBounds0694("2026-02-31"), null);
});

test("family history is independently indexed by driver and day", () => {
  assert.match(backend, /FAMILY_DAILY_HISTORY_0694/);
  assert.match(backend, /tripTrackingFamilyHistoryDrivers/);
  assert.match(backend, /familyHistoryDayKey0694/);
  assert.match(backend, /persistFamilyHistoryPoints0694/);
  assert.match(backend, /listPublicFamilyHistoryDays0694/);
  assert.match(backend, /getPublicFamilyHistoryDay0694/);
  assert.match(backend, /America\/Sao_Paulo/);
});

test("public router exposes history only under family route", () => {
  assert.match(index, /listPublicFamilyHistoryDays0694/);
  assert.match(index, /getPublicFamilyHistoryDay0694/);
  assert.match(index, /parts\[3\] === "family"/);
  assert.doesNotMatch(index, /getPublicFamilyHistoryDay0694\(req, res, parts\[3\]/);
});

test("family browser uses calendar history and no longer asks live endpoint for trace", () => {
  assert.match(client, /FAMILY_DAILY_HISTORY_0694/);
  assert.match(client, /fetchFamilyHistoryDays0694/);
  assert.match(client, /fetchFamilyHistoryDay0694/);
  assert.match(client, /splitHistorySegments0694/);
  assert.match(client, /Nenhum histórico disponível nesta data/);
  assert.doesNotMatch(client, /params\.set\("trace", "1"\)/);
  assert.match(html, /id="historyCalendar"/);
  assert.match(html, /id="historyDateButton"/);
  assert.match(html, /🕘 Histórico/);
  assert.match(html, /tracking\.js\?v=0694/);
});
