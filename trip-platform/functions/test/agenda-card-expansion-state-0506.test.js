"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const backend = fs.readFileSync(path.join(root, "trip-platform", "functions", "index.js"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const gradle = fs.readFileSync(path.join(root, "app", "build.gradle.kts"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function compileProjection() {
  const source = between(backend, "function publicAgendaUiTripKey0506", "function canonicalPublicStop0411");
  return Function(
    "crypto",
    'function cleanText(value, max) { return String(value || "").trim().slice(0, max || 9999); }\n' +
    'function sha256Hex(value) { return crypto.createHash("sha256").update(String(value || "")).digest("hex"); }\n' +
    source +
    "\nreturn { publicAgendaUiTripKey0506, publicTripProjection0491 };"
  )(crypto);
}

test("0506 public UI key is canonical, opaque and stable across mutable revisions", () => {
  const { publicAgendaUiTripKey0506 } = compileProjection();
  const base = {
    canonicalTripId: "canonical-trip-123",
    tripId: "public-token-older",
    departureAtMillis: 1000,
    updatedAtMillis: 2000,
    publicProjectionRevision0434: 12,
    publicProjectionHash0434: "hash-a",
  };
  const next = {
    ...base,
    departureAtMillis: 3000,
    updatedAtMillis: 4000,
    publicProjectionRevision0434: 13,
    publicProjectionHash0434: "hash-b",
    availableSeatsMinimum: 0,
    availableSeatsMaximum: 3,
  };
  assert.equal(publicAgendaUiTripKey0506(base), publicAgendaUiTripKey0506(next));
  assert.notEqual(
    publicAgendaUiTripKey0506(base),
    publicAgendaUiTripKey0506({ ...base, canonicalTripId: "canonical-trip-456" }),
  );
  assert.match(publicAgendaUiTripKey0506(base), /^agenda-trip-[a-f0-9]{40}$/);
  assert.equal(publicAgendaUiTripKey0506(base).includes(base.canonicalTripId), false);
});

test("0506 public projection exposes only opaque UI identity, not canonical or token identities", () => {
  const { publicTripProjection0491 } = compileProjection();
  const projected = publicTripProjection0491({
    canonicalTripId: "private-canonical-id",
    tripId: "private-trip-token",
    publicToken: "private-public-token",
    blablaTripId: "private-blabla-id",
    title: "A → B",
    departureAtMillis: 123,
    status: "PUBLISHED",
    capacity: 4,
    stops: [{ order: 0, name: "A" }, { order: 1, name: "B" }],
  });
  assert.match(projected.publicTripKey0506, /^agenda-trip-[a-f0-9]{40}$/);
  const serialized = JSON.stringify(projected);
  for (const forbidden of [
    "private-canonical-id",
    "private-trip-token",
    "private-public-token",
    "private-blabla-id",
    "canonicalTripId",
    "publicToken",
    "blablaTripId",
  ]) assert.equal(serialized.includes(forbidden), false, forbidden);
});

test("0506 expanded state lives outside disposable card DOM and survives snapshot re-render", () => {
  const state = between(app, "function agendaExpansionStorageKey0506", "function show(id, visible = true)");
  const toggle = between(app, "function applyAgendaTripExpansionState0506", "function renderAgendaCards");
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  const render = between(app, "function renderAgenda(trips)", "let agendaLoadInFlight0491");

  assert.match(state, /const expandedAgendaTripKeys0506 = restoreExpandedAgendaTripKeys0506\(\)/);
  assert.match(state, /sessionStorage\.setItem/);
  assert.match(state, /navigation\.type !== "reload"/);
  assert.match(toggle, /expandedAgendaTripKeys0506\.add\(tripKey0506\)/);
  assert.match(toggle, /expandedAgendaTripKeys0506\.delete\(tripKey0506\)/);
  assert.match(toggle, /"USER_TOGGLE"/);
  assert.match(cards, /expandedAgendaTripKeys0506\.has\(tripKey0506\)/);
  assert.match(cards, /applyAgendaTripExpansionState0506[\s\S]*restoreExpanded0506/);
  assert.match(cards, /"DATA_UPDATE_STATE_RESTORED"/);
  assert.match(render, /agendaTrips"\)\.innerHTML = ""/);
  assert.match(render, /"TRIP_REMOVED"/);
  assert.doesNotMatch(render, /expandedAgendaTripKeys0506\.clear\(\)/);
});

test("0506 UI identity is never derived from mutable content, order, revision, date or hash", () => {
  const keyFn = between(app, "function publicAgendaTripKey0506", "function agendaTripRevision0506");
  assert.match(keyFn, /item\?\.publicTripKey0506/);
  assert.doesNotMatch(keyFn, /updatedAt|revision|hash|index|departure|title|origin|destination|from|to/);

  const backendKey = between(backend, "function publicAgendaUiTripKey0506", "function publicTripProjection0491");
  assert.match(backendKey, /input\.canonicalTripId \|\| input\.tripId \|\| input\.publicToken/);
  assert.doesNotMatch(backendKey, /updatedAt|Revision|Hash|departureAt|title|stops/);
});

test("0506 telemetry proves user collapse, restored-open data updates and true removal separately", () => {
  assert.match(app, /"AGENDA_CARD_EXPANDED"/);
  assert.match(app, /"AGENDA_CARD_COLLAPSED"/);
  assert.match(app, /canonicalTripRef/);
  assert.match(app, /previousRevision/);
  assert.match(app, /newRevision/);
  assert.match(app, /"USER_TOGGLE"/);
  assert.match(app, /"DATA_UPDATE_STATE_RESTORED"/);
  assert.match(app, /"TRIP_REMOVED"/);
});

test("0506 build and cache identity are exact", () => {
  assert.match(gradle, /versionCode = 5815/);
  assert.match(gradle, /versionName = "0\.1\.523"/);
  assert.match(html, /app\.js\?v=0\.1\.519-global-extra-seats-whatsapp0519/);
});