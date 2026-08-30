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

test("public filter does not ask seats and cards defer quantity to the chosen reservation action", () => {
  assert.doesNotMatch(publicHtml, /id="searchSeats"/);
  assert.doesNotMatch(publicHtml, /id="searchReturn"/);
  assert.doesNotMatch(publicApp, /detailsParams\.set\("lugares", String\(searchState\.seats\)\)/);
  assert.match(publicApp, /function openWhatsappSeatPicker/);
  assert.match(publicApp, /seatPickerLimit = source\.capacityReliable === true/);
  assert.match(backend, /canReserve: data\.publicBookingEnabled === true && capacityReliable && !fullyOccupied && availability\.maximum > 0/);
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

test("real-seat copy is exact for zero, singular, plural and changed capacity", () => {
  const copySource = functionSource(publicApp, "normalizedSeatCount", "bestSearchAvailability");
  const copy = new Function(copySource + "; return { seatAvailabilityText, seatLimitText };")();
  assert.equal(copy.seatAvailabilityText(0), "Nenhuma vaga disponível para este trecho.");
  assert.equal(copy.seatAvailabilityText(1), "1 vaga disponível para este trecho.");
  assert.equal(copy.seatAvailabilityText(2), "2 vagas disponíveis para este trecho.");
  assert.equal(copy.seatAvailabilityText(3), "3 vagas disponíveis para este trecho.");
  assert.equal(copy.seatAvailabilityText(4), "4 vagas disponíveis para este trecho.");
  assert.equal(copy.seatLimitText(1, false), "Este carro tem apenas 1 vaga disponível para este trecho.");
  assert.equal(copy.seatLimitText(2, false), "Este carro tem apenas 2 vagas disponíveis para este trecho.");
  assert.equal(copy.seatLimitText(3, false), "Este carro tem apenas 3 vagas disponíveis para este trecho.");
  assert.equal(copy.seatLimitText(0, true), "Não há mais vagas disponíveis para este trecho.");
  assert.equal(copy.seatLimitText(2, true), "Agora este carro tem apenas 2 vagas disponíveis para este trecho.");
});

test("seat picker uses reconciled availability instead of nominal vehicle capacity", () => {
  assert.match(publicHtml, /id="seatPickerAvailability"/);
  assert.match(publicHtml, /id="seatPickerMessage"/);
  assert.doesNotMatch(publicApp, /function maxAgendaCapacity/);
  assert.doesNotMatch(publicApp, /Math\.min\(9, outbound/);
  assert.match(publicApp, /Math\.min\(999, Math\.floor\(Number\(params\.get\("lugares"\)/);
  assert.match(publicApp, /function searchSeatAvailabilityLimit/);
  assert.match(publicApp, /availableForTripSegment\(item, fromIndex, toIndex\)/);

  const tripPicker = functionSource(publicApp, "openTripSeatPicker", "openWhatsappSeatPicker");
  assert.match(tripPicker, /seatPickerLimit = availableFor\(fromIndex, toIndex\)/);
  assert.match(tripPicker, /seatPickerDraft = Math\.max\(1, desiredSeats \|\| 1\)/);
  assert.doesNotMatch(tripPicker, /Math\.min\(seatPickerLimit/);

  const plusMinus = functionSource(publicApp, "changeSeatPicker", "confirmSeatPicker");
  assert.match(plusMinus, /candidate > seatPickerLimit/);
  assert.match(plusMinus, /seatLimitText\(seatPickerLimit, false\)/);
  assert.match(plusMinus, /seatPickerDraft = Math\.max\(1, seatPickerDraft - 1\)/);
});

test("booking form never silently reduces an over-limit request", () => {
  const refresh = functionSource(publicApp, "refreshAvailability", "traceSearchChanged");
  assert.doesNotMatch(refresh, /seatsInput\.value = String\(available\)/);
  assert.match(refresh, /seatAvailabilityText\(available\)/);
  assert.match(refresh, /seatLimitText\(available, false\)/);
  assert.match(refresh, /requested > available/);

  const review = functionSource(publicApp, "reviewBooking", "requestIdentity");
  assert.match(review, /seatLimitText\(available, true\)/);
});

test("all reservation entry points pass through the real-seat picker before server save", () => {
  const quick = functionSource(publicApp, "startQuickReservation", "refreshTripAvailabilitySummary");
  assert.match(quick, /openTripSeatPicker\(auto\)/);
  assert.doesNotMatch(quick, /return reserve\(\)/);

  const confirm = functionSource(publicApp, "confirmSeatPicker", "stopMatchesSearch");
  assert.match(confirm, /seatPickerLimit < 1 \|\| seatPickerDraft > seatPickerLimit/);
  assert.match(confirm, /pendingBooking = \{ \.\.\.seatPickerBookingIntent, seats: seatPickerDraft \}/);
  assert.match(confirm, /return reserve\(\)/);
});

test("server reports authoritative capacity after a transactional race", () => {
  const messageSource = functionSource(backend, "currentSeatCapacityMessage", "capacityAvailabilityRange");
  const currentSeatCapacityMessage = new Function(messageSource + "; return currentSeatCapacityMessage;")();
  assert.equal(currentSeatCapacityMessage(0), "Não há mais vagas disponíveis para este trecho.");
  assert.equal(currentSeatCapacityMessage(1), "Agora este carro tem apenas 1 vaga disponível para este trecho.");
  assert.equal(currentSeatCapacityMessage(2), "Agora este carro tem apenas 2 vagas disponíveis para este trecho.");
  assert.match(backend, /code: "insufficient_seats", availableSeats: available/);
  assert.match(backend, /capacityDetails = Number\.isInteger\(error\.availableSeats\)/);
});

