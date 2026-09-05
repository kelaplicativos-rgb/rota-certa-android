"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const browserAdmin = fs.readFileSync(path.join(root, "trip-platform", "public", "admin-0417.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0470 public and authenticated modes render the same canonical card function", () => {
  const cards = between(app, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /card\.dataset\.canonicalTripId = canonicalTripId0470/);
  assert.match(cards, /adminContext0470\.capabilities\.canManageTrip === true/);
  assert.match(cards, /Administrar esta viagem/);
  assert.match(cards, /RotaCertaAgendaAdmin0470/);
  assert.equal((app.match(/function renderAgendaCards/g) || []).length, 1);
  assert.doesNotMatch(app, /function renderAdminAgendaCards|function renderPrivateTripCards/);
});

test("0470 Home fetches only capability projection, never a second administrative trip list", () => {
  const hydrate = between(app, "async function hydrateAgendaAdminCapabilities0470", "function applyAgendaAdminCapabilities0470");
  assert.match(hydrate, /\/v1\/admin\/card-capabilities/);
  assert.doesNotMatch(hydrate, /\/v1\/admin\/trips/);
  assert.match(app, /agendaAdminCardCapabilities0470 = new Map\(\)/);
  assert.doesNotMatch(app, /adminTripsStore|publicTripsStore/);
});

test("0470 public projection carries the persistent canonical identity to the original card", () => {
  const canonicalPublic = between(api, "function safePublicTripFromCanonical0434", "function safePublicTrip");
  assert.match(canonicalPublic, /canonicalTripId: payload\.canonicalTripId/);
  const fallback = between(api, "function safePublicTrip\(token, data\)", "function canonicalPublicStop0411");
  assert.match(fallback, /canonicalTripId: cleanText\(data\.canonicalTripId \|\| data\.localTripId \|\| token/);
});

test("0470 contextual admin endpoint resolves exact canonical identity inside tenant", () => {
  const resolver = between(admin, "async function resolveAdminTrip0470", "function failResolvedTrip0470");
  assert.match(resolver, /canonicalTripIdentity0470/);
  assert.match(resolver, /where\("driverUsername", "==", session\.driverUsername\)/);
  assert.match(resolver, /where\("canonicalTripId", "==", requested\)/);
  assert.match(resolver, /forbidden: true/);
  assert.doesNotMatch(resolver, /title|departureAtMillis|driverName|city|route/i);
  assert.match(api, /getAdminTripContext0470/);
});

test("0470 card capabilities are server-side, per authenticated Agenda admin, and lightweight", () => {
  const capabilities = between(admin, "async function getAdminCardCapabilities0470", "async function getAdminTripContext0470");
  assert.match(capabilities, /requireAdminSession0417/);
  assert.match(capabilities, /effective\.visible === true/);
  assert.match(capabilities, /canonicalTripId:/);
  assert.match(capabilities, /capabilities: tripAdminCapabilities0470/);
  assert.doesNotMatch(capabilities, /bookings|passengerContact|passengerName/);
  assert.match(admin, /access\.agendaAdmin !== true/);
});

test("0470 contextual trip detail is lazy and does not preload every passenger on Home", () => {
  assert.match(browserAdmin, /\/v1\/admin\/trips\/" \+ encodeURIComponent\(identity\)/);
  assert.match(browserAdmin, /loadAdminTripBookings0468\(tripIdentity\)/);
  const hydrate = between(app, "async function hydrateAgendaAdminCapabilities0470", "function applyAgendaAdminCapabilities0470");
  assert.doesNotMatch(hydrate, /bookings|passengers/);
});

test("0470 BlaBlaCar URL mutation validates server-side and rejects stale concurrent edits", () => {
  const mutation = between(admin, "async function updateAdminTripBlaBlaPublicUrl0465", "async function reportDriverAdminSyncHealth0417");
  assert.match(mutation, /validatedBlaBlaPublicUrl0417/);
  assert.match(mutation, /expectedCanonicalRevision/);
  assert.match(mutation, /expectedManualRevision0465/);
  assert.match(mutation, /db\.runTransaction/);
  assert.match(mutation, /trip_revision_conflict/);
  assert.match(mutation, /httpStatus: 409/);
  assert.match(mutation, /claimAdminCommand0417/);
  assert.match(mutation, /replayed: true/);
});

test("0470 browser sends revisions and operation ID and never claims blue before attestation", () => {
  assert.match(browserAdmin, /expectedCanonicalRevision: Number\(trip\.canonicalRevision/);
  assert.match(browserAdmin, /expectedManualRevision0465:/);
  assert.match(browserAdmin, /newAdminOperationId0427\("public_url"\)/);
  assert.match(browserAdmin, /Aguardando sincronização, publicação, readback e atestação/);
  assert.match(browserAdmin, /attestationState === "VERIFIED"/);
  assert.match(browserAdmin, /Conflito de versão/);
});

test("0470 green and blue remain authoritative server states", () => {
  const classifier = between(api, "function adminPublicTripState0469", "async function createDriverTrip");
  assert.match(classifier, /publicAgendaTripVisibility0466/);
  assert.match(classifier, /state: "PUBLISHED"/);
  assert.match(classifier, /publicProjectionAttestedCurrent0429/);
  assert.match(classifier, /state: "VERIFIED"/);
  assert.match(browserAdmin, /PUBLISHED: "🟢 Publicada"/);
  assert.match(browserAdmin, /VERIFIED: "🔵 Validada"/);
});

test("0470 return path refreshes the same canonical Home card without textual re-search", () => {
  assert.match(app, /RotaCertaAgendaHome0470/);
  assert.match(app, /\[data-canonical-trip-id\]/);
  assert.match(app, /node\.dataset\.canonicalTripId === String\(canonicalTripId/);
  assert.match(browserAdmin, /home\.refreshTrip\(canonicalTripId/);
  assert.doesNotMatch(browserAdmin, /findTrip\([\s\S]*date|driverName[\s\S]*departureTime/);
});

test("0470 deep link and session expiry preserve security boundary", () => {
  assert.match(app, /params\.get\("administrar"\)/);
  assert.match(app, /requestedAdminTripIdentity0470 && !passengerAgendaAdmin0418/);
  assert.match(app, /showPrivateAuthGate\("portal"\)/);
  assert.match(app, /response\.status === 401/);
  assert.match(app, /savePassengerSession\(""/);
  assert.match(admin, /forbidden_trip_tenant/);
});

test("0470 contextual UI is mobile-first and accessible without color-only status", () => {
  assert.match(html, /id="adminTripContext0470"/);
  assert.match(html, /aria-live="polite"/);
  assert.match(html, /aria-label="Seções da administração desta viagem"/);
  assert.match(html, /adminCardAction0470/);
  assert.match(app, /setAttribute\("aria-label", "Administrar esta viagem:/);
  assert.match(app, /🔵 Validada/);
  assert.match(app, /🟢 Publicada/);
  assert.match(app, /🔴 Divergente/);
});

test("0470 keeps global system administration but moves individual editor out of second list card", () => {
  assert.match(html, /A lista abaixo permanece como diagnóstico global/);
  assert.match(html, /A gestão diária de cada viagem começa no próprio card da Home/);
  assert.equal((html.match(/id="adminTripPublicUrlEditor0465"/g) || []).length, 1);
  assert.equal((html.match(/id="adminTripBookings0468"/g) || []).length, 1);
  assert.equal((html.match(/id="adminTripHistory0417"/g) || []).length, 1);
});
