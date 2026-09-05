"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0471 visibility is an explicit per-trip control with backward-compatible online default", () => {
  const visibility = between(api, "function tripPublicOnline0471", "async function getPublicDriverAgenda");
  assert.match(visibility, /publicAgendaOnline0471 === false/);
  assert.match(visibility, /PUBLIC_AGENDA_OFFLINE_0471/);
  assert.match(visibility, /return \!\(data && data\.publicAgendaOnline0471 === false\)/);
});

test("0471 offline is enforced by server discovery, direct trip read and new booking creation", () => {
  const agendaVisibility = between(api, "function publicAgendaTripVisibility0466", "async function getPublicDriverAgenda");
  assert.match(agendaVisibility, /tripPublicOnline0471\(data\)/);
  const direct = between(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.match(direct, /tripPublicOnline0471\(data\)/);
  assert.match(direct, /trip_offline/);
  const booking = between(api, "async function createBooking", "async function updateBooking");
  assert.match(booking, /tripPublicOnline0471\(trip\)/);
  assert.match(booking, /code: "trip_offline"/);
});

test("0471 authenticated Home receives all active canonical cards, including offline cards, without a second trip-list endpoint", () => {
  const capabilities = between(admin, "async function getAdminCardCapabilities0470", "async function getAdminTripContext0470");
  assert.match(capabilities, /activeAdminTrips0417\(trips\)/);
  assert.doesNotMatch(capabilities, /\.filter\(\(entry\) => entry\.effective\.visible === true\)/);
  assert.match(capabilities, /agendaOnline0471:/);
  assert.match(capabilities, /visibilityRevision0471:/);
  assert.match(capabilities, /buildAdminHomeTrip0471/);
  const hydrate = between(app, "async function hydrateAgendaAdminCapabilities0470", "async function loadAgenda");
  assert.match(hydrate, /\/v1\/admin\/card-capabilities/);
  assert.doesNotMatch(hydrate, /\/v1\/admin\/trips/);
  assert.equal((app.match(/function renderAgendaCards/g) || []).length, 1);
});

test("0471 sync completion is observed on authenticated Home by lightweight canonical polling", () => {
  assert.match(app, /scheduleAgendaAdminHomePolling0471/);
  assert.match(app, /setTimeout\(async \(\) => \{/);
  assert.match(app, /1800/);
  assert.match(app, /agendaAdminSignature0471/);
  assert.match(app, /refreshAgendaAdminHome0471/);
  assert.match(app, /agendaTripsCache = applyAgendaAdminCapabilities0470\(agendaTripsCache\)/);
});

test("0471 top-right toggle is accessible, explicit online/offline and suppresses public actions while offline", () => {
  const cards = between(app, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /agendaVisibilityToggle0471/);
  assert.match(cards, /textContent = publicOnline0471 \? "online" : "offline"/);
  assert.match(cards, /aria-pressed/);
  assert.match(cards, /setAgendaTripPublicOnline0471/);
  assert.match(cards, /const actionsEnabled = publicOnline0471/);
  assert.match(cards, /if \(publicOnline0471\) \{/);
  assert.match(html, /position:absolute;top:14px;right:14px/);
  assert.match(html, /agendaTripOffline0471/);
});

test("0471 toggle mutation is tenant-authenticated, idempotent and revision-safe", () => {
  const mutation = between(admin, "async function updateAdminTripPublicVisibility0471", "async function reportDriverAdminSyncHealth0417");
  assert.match(mutation, /requireAdminSession0417/);
  assert.match(mutation, /resolveAdminTrip0470/);
  assert.match(mutation, /claimAdminCommand0417/);
  assert.match(mutation, /TRIP_PUBLIC_VISIBILITY_CHANGED_0471/);
  assert.match(mutation, /expectedVisibilityRevision0471/);
  assert.match(mutation, /trip_visibility_revision_conflict/);
  assert.match(mutation, /db\.runTransaction/);
  assert.match(mutation, /publicAgendaOnline0471: requestedOnline/);
  assert.match(api, /parts\[4\] === "public-visibility"/);
});

test("0471 Android sync cannot reset the independent Agenda visibility control", () => {
  const normalizer = between(api, "function normalizeDriverTrip", "function isExternalBlaBlaTrip");
  assert.doesNotMatch(normalizer, /publicAgendaOnline0471/);
  const updater = between(api, "async function updateDriverTrip", "async function getPublicTrip");
  assert.match(updater, /tx\.update\(ref, committedTripPatch0468\)/);
  assert.doesNotMatch(updater, /publicAgendaOnline0471\s*:/);
});

test("0471 keeps offline distinct from canonical attestation state", () => {
  const classifier = between(api, "function adminPublicTripState0469", "async function createDriverTrip");
  assert.match(classifier, /publicAgendaTripVisibility0466/);
  assert.match(app, /!publicOnline0471 \? "⚫ Offline"/);
  assert.match(app, /attestationState/);
});
