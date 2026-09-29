"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const backend = fs.readFileSync(path.join(root, "functions", "live-tracking-0668.js"), "utf8");
const client = fs.readFileSync(path.join(root, "public", "tracking.js"), "utf8");
const html = fs.readFileSync(path.join(root, "public", "tracking.html"), "utf8");

test("family live endpoint does not query history unless trace is requested", () => {
  assert.match(backend, /traceRequested0692/);
  assert.match(backend, /mode:traceRequested0692 \? "FAMILY_HISTORY" : "FAMILY_LIVE"/);
  assert.match(backend, /familyLiveFirst0692:true/);
  const familyStart = backend.indexOf("async function getPublicFamily0681");
  const queryIndex = backend.indexOf("if (traceRequested0692)", familyStart);
  const pointsIndex = backend.indexOf('sessionRef.collection("points")', queryIndex);
  assert.ok(familyStart >= 0 && queryIndex > familyStart && pointsIndex > queryIndex);
});

test("family browser opens live-first with smart follow and lazy daily history", () => {
  assert.match(client, /FAMILY_LIVE_FIRST_0692/);
  assert.match(client, /FAMILY_DAILY_HISTORY_0694/);
  assert.match(client, /familyHistoryMode0692 = false/);
  assert.doesNotMatch(client, /params\.set\("trace", "1"\)/);
  assert.match(client, /fetchFamilyHistoryDay0694/);
  assert.match(client, /followLive0692 = true/);
  assert.match(client, /map\.setView\(here, Math\.max\(16/);
  assert.match(client, /🕘 Histórico/);
});

test("family page makes the live map primary and exposes history as a separate action", () => {
  assert.ok(html.indexOf('id="mapWrap"') < html.indexOf('id="trackingSummary"'));
  assert.match(html, /id="historyToggle"/);
  assert.match(html, /tracking\.js\?v=0694/);
});
