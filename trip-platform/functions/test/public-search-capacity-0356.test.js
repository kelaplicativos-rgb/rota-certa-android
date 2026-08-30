const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const backend = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicApp = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const publicHtml = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

function functionSource(source, name, nextName) {
  const start = source.indexOf("function " + name + "(");
  const end = source.indexOf("\nfunction " + nextName + "(", start);
  assert.ok(start >= 0 && end > start, "missing function " + name);
  return source.slice(start, end);
}

test("accent normalization and segment bottleneck use the real public search functions", () => {
  const normalizeSource = functionSource(publicApp, "normalizeSearchText", "dateKeyFromMillis");
  const normalizeSearchText = new Function(normalizeSource + "; return normalizeSearchText;")();
  assert.equal(normalizeSearchText("Três Corações"), "tres coracoes");
  assert.equal(normalizeSearchText("TRES CORACOES"), "tres coracoes");
  assert.equal(normalizeSearchText("São Tomé"), "sao tome");

  const availableSource = functionSource(publicApp, "availableForTripSegment", "tripSearchEligible");
  const availableForTripSegment = new Function(availableSource + "; return availableForTripSegment;")();
  const trip = { capacity: 4, segmentLoads: [3, 1] };
  assert.equal(availableForTripSegment(trip, 0, 2), 1);
  assert.equal(availableForTripSegment(trip, 1, 2), 3);
});

test("generic cards and backend both use the whole-trip minimum, never the maximum", () => {
  assert.match(publicApp, /seatRange\(item\)\.minimum >= seats/);
  assert.match(publicApp, /detailsParams\.set\("lugares", String\(searchState\.seats\)\)/);
  assert.match(backend, /capacityReliable && !fullyOccupied && availability\.minimum > 0/);
  assert.doesNotMatch(backend, /canReserve:[^\n]*availability\.maximum > 0/);
});

test("autocomplete retains exact stop ids and intermediate stops require authoritative itinerary", () => {
  assert.match(publicHtml, /id="searchFromSuggestions"/);
  assert.match(publicHtml, /id="searchToSuggestions"/);
  assert.match(publicApp, /function buildSearchSuggestions/);
  assert.match(publicApp, /stopId: String\(stop\?\.id \|\| ""\)/);
  assert.match(publicApp, /stops\[candidate\.stopIndex\]\?\.id === candidate\.stopId/);
  assert.match(publicApp, /item\?\.itineraryAuthoritative === true/);
  assert.match(publicApp, /return fromIndex === 0 && toIndex === stops\.length - 1/);
  assert.match(publicApp, /normalizeSearchText\(suggestion\.name\)\.startsWith\(needle\)/);
});

test("free text must resolve canonically before search and direct POST is fail-closed", () => {
  assert.match(publicApp, /function resolveCanonicalSelection/);
  assert.match(publicApp, /Esse local não aparece nas viagens disponíveis para os filtros selecionados\./);
  assert.match(publicApp, /Há mais de um ponto correspondente\. Selecione uma opção da lista\./);
  const matcher = functionSource(publicApp, "matchTripSegment", "searchDirection");
  assert.doesNotMatch(matcher, /includes\(/);
  assert.match(matcher, /selectedStopIndex/);
  assert.match(backend, /code: "capacity_unconfirmed"/);
  assert.match(backend, /code: "itinerary_unconfirmed"/);
  assert.match(backend, /code: "insufficient_seats"/);
  assert.match(backend, /PUBLIC_BOOKING_BLOCKED_NO_CAPACITY/);
});
