const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const backend = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicApp = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const publicHtml = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");
const tripDetail = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "assets", "blablacar", "scripts", "trip_detail.js"), "utf8");

function functionSource(source, name, nextName) {
  const syncStart = source.indexOf("function " + name + "(");
  const asyncStart = source.indexOf("async function " + name + "(");
  const start = syncStart >= 0 ? syncStart : asyncStart;
  const syncEnd = source.indexOf("\nfunction " + nextName + "(", start);
  const asyncEnd = source.indexOf("\nasync function " + nextName + "(", start);
  const ends = [syncEnd, asyncEnd].filter((value) => value > start);
  const end = ends.length ? Math.min(...ends) : -1;
  assert.ok(start >= 0 && end > start, "missing function " + name);
  return source.slice(start, end);
}

test("filter surface is only from, to and one date", () => {
  assert.match(publicHtml, /De onde você vai\?/);
  assert.match(publicHtml, /Para onde você vai\?/);
  assert.match(publicHtml, /<span class="searchLabel">Quando\?<\/span>/);
  assert.match(publicHtml, />Buscar viagens<\/button>/);
  assert.doesNotMatch(publicHtml, /id="searchReturn"/);
  assert.doesNotMatch(publicHtml, /id="searchSeats"/);
});

test("route search uses ordered real stops and can return sold-out cards", () => {
  const eligible = functionSource(publicApp, "tripSearchEligible", "publicSegmentReservable");
  assert.match(eligible, /\["PUBLISHED", "FULL"\]/);
  assert.doesNotMatch(eligible, /capacityReliable === true/);

  const matcher = functionSource(publicApp, "matchTripSegment", "searchDirection");
  assert.match(matcher, /selectedStopIndex\(item, fromSelection\)/);
  assert.match(matcher, /selectedStopIndex\(item, toSelection, fromIndex\)/);
  assert.match(matcher, /segmentEvidenceTrusted\(item, fromIndex, toIndex\)/);
  assert.match(matcher, /return \{ item, fromIndex, toIndex, available/);
  assert.doesNotMatch(matcher, /available >= seats \?/);

  const suggestions = functionSource(publicApp, "buildSearchSuggestions", "applySearchSelection");
  assert.match(suggestions, /agendaTripsCache\.filter/);
  assert.match(suggestions, /orderedStops\(item\)/);
  assert.doesNotMatch(suggestions, /publicSegmentReservable\(item, fromIndex, toIndex, seats, dateKey\)/);
});

test("WhatsApp asks seats, revalidates with GET, and never creates a booking", () => {
  assert.match(publicHtml, /Quantos lugares você precisa\?/);
  const execute = functionSource(publicApp, "openWhatsappFromSeatPicker", "safeBlaBlaPublicUrl");
  assert.match(execute, /fetch\(\`\/v1\/public\/trips\//);
  assert.match(execute, /availableForTripSegment\(trip, fromIndex, toIndex\)/);
  assert.match(execute, /https:\/\/wa\.me\//);
  assert.match(execute, /availability_revalidated_no_booking/);
  assert.doesNotMatch(execute, /method:\s*"POST"/);
  assert.doesNotMatch(execute, /reserve\(/);
  assert.doesNotMatch(execute, /pendingBooking/);
});

test("three choices are explicit and Rota Certa is disabled", () => {
  assert.match(publicHtml, /Reservar pelo WhatsApp/);
  assert.match(publicHtml, /Reservar na BlaBlaCar/);
  assert.match(publicHtml, /id="bookRotaCerta"[^>]*disabled/);
  assert.match(publicHtml, /Em breve/);
  assert.match(publicApp, /bookingChoice bookingWhatsapp/);
  assert.match(publicApp, /bookingChoice bookingBlabla/);
  assert.match(publicApp, /rota\.disabled = true/);
});

test("BlaBla public link is fail-closed and never uses the admin rides offer URL", () => {
  assert.match(tripDetail, /publicTripHref/);
  assert.match(tripDetail, /id !== currentTripId/);
  assert.match(tripDetail, /path !== '\/trip'/);
  assert.match(backend, /function normalizeBlaBlaPublicUrl/);
  assert.match(backend, /blablaPublicUrl: normalizeBlaBlaPublicUrl/);
  assert.match(backend, /actualTripId !== expected/);
  const safe = functionSource(publicApp, "safeBlaBlaPublicUrl", "stopMatchesSearch");
  assert.match(safe, /hostname\.toLowerCase\(\) !== "www\.blablacar\.com\.br"/);
  assert.match(safe, /path !== "\/trip"/);
});
