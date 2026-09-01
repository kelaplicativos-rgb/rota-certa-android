"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const android = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"), "utf8");
const timeline = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripTimelineUi.kt"), "utf8");
const outbox = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripPublicationOutbox0387.kt"), "utf8");

function bodyOf(source, name, nextName) {
  const start = source.indexOf(name);
  assert.notEqual(start, -1, `${name} missing`);
  const end = nextName ? source.indexOf(nextName, start + name.length) : source.length;
  return source.slice(start, end < 0 ? source.length : end);
}

test("capacity snapshot is atomic idempotent and incomplete input cannot downgrade a reliable snapshot", () => {
  const fn = bodyOf(api, "async function reconcileDriverCapacitySnapshot", "async function upsertDriverCapacityBooking");
  assert.match(fn, /if \(!sourceComplete \|\| !snapshotRevision\)/);
  assert.match(fn, /capacity_snapshot_incomplete/);
  assert.match(fn, /db\.runTransaction/);
  assert.match(fn, /previous\.capacityReliable === true && cleanText\(previous\.capacitySnapshotRevision/);
  assert.match(fn, /changed: false/);
  assert.match(fn, /capacitySnapshotRevision: snapshotRevision/);
  assert.match(fn, /occupancyRevision/);
  assert.match(fn, /capacityReliable: true/);
  assert.match(fn, /snapshotOverbooked \? "FULL"/);
  assert.match(fn, /tx\.set\(tripRef\.collection\("bookings"\)/);
  assert.match(fn, /tx\.update\(tripRef/);
});

test("public booking remains fail closed and transactional against the same canonical segment capacity", () => {
  const fn = bodyOf(api, "async function createBooking", "async function updatePublicBooking");
  assert.match(fn, /db\.runTransaction/);
  assert.match(fn, /if \(!capacityIsReliable\(token, trip\)\)/);
  assert.match(fn, /capacity_unconfirmed/);
  assert.match(fn, /const available = availableForBooking/);
  assert.match(fn, /if \(seats > available\)/);
  assert.match(fn, /insufficient_seats/);
  assert.match(fn, /assertNoOverbooking/);
  assert.match(fn, /assertNoOperationalOverbooking/);
});

test("LOTADO and unknown public cards keep details visible but expose no reservation CTAs", () => {
  assert.match(web, /return "🪑 LOTADO"/);
  assert.match(web, /return "Disponibilidade sendo atualizada"/);
  assert.match(web, /if \(actionsEnabled\) \{\s*const choices = document\.createElement\("div"\)/);
  assert.match(web, /show\("tripSticky", canUseExternalActions\)/);
  assert.match(web, /if \(!source \|\| source\.capacityReliable !== true\) return/);
  assert.match(web, /if \(availableForTripSegment\(source, fromIndex, resolvedTo\) < 1\) return/);
  assert.match(web, /if \(!trip \|\| trip\.capacityReliable !== true \|\| isFullTrip\(trip\)/);
});

test("single exact-card collector change flows through durable per-trip outbox before incremental publication", () => {
  assert.match(android, /suspend fun syncExternalTripIncremental/);
  assert.match(android, /reconcileCapacitySnapshot/);
  assert.match(android, /PUBLIC_AGENDA_INCREMENTAL_END/);
  assert.match(android, /fullSyncRequested=false/);
  assert.match(timeline, /onResult = \{ nextResponse ->/);
  assert.match(timeline, /recordExternalManualMutation/);
  assert.match(timeline, /tripMutationCoordinator\.drainPending\(\)/);
  assert.match(timeline, /incrementalPublishMutex\.withLock/);
  assert.match(outbox, /TripPublicationOperation0387\.UPSERT_EXTERNAL/);
  assert.match(outbox, /PublicAgendaAutoSync0300\.syncExternalTripIncremental/);
  const onResult = timeline.indexOf("onResult = { nextResponse ->");
  const onChanged = timeline.indexOf("onChanged = onChanged", onResult);
  assert.ok(onResult >= 0 && onChanged > onResult);
  const resultBlock = timeline.slice(onResult, onChanged);
  assert.doesNotMatch(resultBlock, /PublicAgendaAutoSync0300\.syncExternalTripIncremental/);
});

test("semantic revision ignores presentation PII but contains canonical occupancy fields", () => {
  const fn = bodyOf(android, "internal fun externalCapacitySnapshotRevision", "internal fun parsePriceCents");
  assert.match(fn, /profile_uuid/);
  assert.match(fn, /trip_id/);
  assert.match(fn, /published_seats/);
  assert.match(fn, /passenger_roster_complete/);
  assert.match(fn, /passenger\.booking_href/);
  assert.match(fn, /passenger\.seats/);
  assert.doesNotMatch(fn, /passenger\.name/);
  assert.doesNotMatch(fn, /passenger\.phone/);
});
