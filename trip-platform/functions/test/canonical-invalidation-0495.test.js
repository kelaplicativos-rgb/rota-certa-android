"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicApp = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const passengerArea = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const timelineUi = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripTimelineUi.kt"),
  "utf8",
);

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0495 Android Timeline reuses existing booking event bus and keeps polling only as recovery", () => {
  const rootScreen = between(timelineUi, "fun TripTimelineScreen(", "/** Compatibility entry point");
  assert.match(rootScreen, /BookingRealtimeEvents0356\.changes\.collect/);
  assert.match(rootScreen, /MutableSharedFlow<String>/);
  assert.match(rootScreen, /canonicalRefreshMutex0495\.withLock/);
  assert.match(rootScreen, /TIMELINE_INVALIDATED/);
  assert.match(rootScreen, /TIMELINE_REFRESH_STARTED/);
  assert.match(rootScreen, /TIMELINE_REFRESH_APPLIED/);
  assert.match(rootScreen, /POLL_RECOVERY/);
  assert.match(rootScreen, /delay\(10_000L\)/);
  assert.match(rootScreen, /FOREGROUND/);
  assert.match(rootScreen, /NETWORK_AVAILABLE/);
  assert.doesNotMatch(rootScreen, /BlaBlaTimelineAdapter\.merge|BlaBlaCollector/);
});

test("0495 passenger invalidation is authenticated, tenant scoped and reuses tripNotifications", () => {
  const passengerWatch = between(api, "async function waitPassengerCanonicalChange0495", "async function markDriverNotificationRead");
  assert.match(passengerWatch, /requirePassengerSession/);
  assert.match(passengerWatch, /passengerRequestedDriverScope0491/);
  assert.match(passengerWatch, /tripNotifications/);
  assert.match(passengerWatch, /recipientKey/);
  assert.match(passengerWatch, /driverUsername/);
  assert.doesNotMatch(passengerWatch, /passengerName|passwordHash|cancellationToken/);

  const generic = between(api, "function waitForCanonicalInvalidation0495", "async function waitPassengerCanonicalChange0495");
  assert.match(generic, /query\.onSnapshot/);
  assert.match(generic, /timeoutMillis = 25_000/);
  assert.match(generic, /changed: true/);
  assert.match(generic, /cursor/);
});

test("0495 Minha Área refetches immediately on invalidation and retains timer fallback", () => {
  assert.match(passengerArea, /watchPrivateCanonicalChanges0495/);
  assert.match(passengerArea, /\/v1\/passenger\/me\/changes/);
  assert.match(passengerArea, /await refreshPrivateArea0491\(true\)/);
  assert.match(passengerArea, /changeCursor0495/);
  assert.match(passengerArea, /window\.setInterval/);
  assert.match(passengerArea, /10000/);
});

test("0495 public Agenda invalidation returns no private booking payload and triggers sanitized reload", () => {
  const watch = between(api, "async function waitPublicAgendaCanonicalChange0495", "function buildAdminHomeTrip0471");
  assert.match(watch, /selectCanonicalTripDocuments0495/);
  assert.match(watch, /updatedAtMillis/);
  assert.doesNotMatch(watch, /collection\("bookings"\)|passengerName|passengerContact|paymentStatus|operationalStatus/);

  assert.match(publicApp, /watchPublicAgendaChanges0495/);
  assert.match(publicApp, /\/changes\?since=/);
  assert.match(publicApp, /await loadAgenda\(true\)/);
  assert.match(publicApp, /window\.setInterval/);
  assert.match(publicApp, /15000/);
});

test("0495 routes expose only invalidation signals while canonical reads remain separate", () => {
  assert.match(api, /path === "\/v1\/passenger\/me\/changes"/);
  assert.match(api, /parts\[4\] === "changes"/);
  assert.match(api, /parts\[6\] === "changes"/);
  assert.match(api, /getPublicDriverAgenda/);
  assert.match(api, /listPassengerBookings/);
});
