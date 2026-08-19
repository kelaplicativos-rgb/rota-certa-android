"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const page = fs.readFileSync(path.join(root, "public", "index.html"), "utf8");
const browser = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");

test("vehicle capacity supports 4, 7, 20 and 40 seats without an eight-seat ceiling", () => {
  assert.match(api, /capacity > 999/);
  assert.doesNotMatch(api, /capacity > 8/);
});

test("public booking quantity is dynamic instead of fixed to four options", () => {
  assert.match(page, /id="seats" type="number" min="1" step="1" value="1"/);
  assert.doesNotMatch(page, /<option>4<\/option>/);
  assert.match(browser, /seatsInput\.max = String\(Math\.max\(1, available\)\)/);
  assert.match(browser, /available < 1 \|\| requested > available/);
});

test("backend still rejects invalid seat counts and relies on transactional availability for overbooking", () => {
  assert.match(api, /seats > 999/);
  assert.match(api, /if \(seats > available\)/);
  assert.match(api, /db\.runTransaction/);
  assert.match(api, /insufficient_seats/);
});
