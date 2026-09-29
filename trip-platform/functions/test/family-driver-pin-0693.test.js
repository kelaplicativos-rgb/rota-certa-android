"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const backend = fs.readFileSync(path.join(root, "functions", "live-tracking-0668.js"), "utf8");
const android = fs.readFileSync(path.join(root, "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "LiveTrackingShare0668.kt"), "utf8");
const ui = fs.readFileSync(path.join(root, "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "WorkTrackingActivity.kt"), "utf8");

test("backend confirms exact family PIN and revokes stale access on change", () => {
  assert.match(backend, /familyPinConfirmed0693/);
  assert.match(backend, /familyPinRevision0693/);
  assert.match(backend, /tripTrackingFamilyAccessSessions/);
  assert.match(backend, /tripTrackingFamilyAuthAttempts/);
  assert.match(backend, /pinChanged0693/);
  assert.match(backend, /familyPinConfirmed: familyPinConfirmed0693/);
});

test("android persists driver-defined PIN and requires server confirmation", () => {
  assert.match(android, /fixedFamilyPin0693/);
  assert.match(android, /saveFixedFamilyPin0693/);
  assert.match(android, /setFamilyPin0693/);
  assert.match(android, /familyPinConfirmed/);
  assert.match(android, /Defina um código familiar fixo de 6 dígitos/);
});

test("work tracking screen exposes fixed PIN editor", () => {
  assert.match(ui, /Código familiar fixo/);
  assert.match(ui, /Salvar código familiar/);
  assert.match(ui, /Código familiar confirmado pelo servidor/);
  assert.match(ui, /Você define este código\. Ele não será regenerado automaticamente/);
});
