"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("0569 public Agenda exposes only shell cards and one fixed WhatsApp action", () => {
  assert.match(html, /id="agendaTrips"/);
  assert.match(html, /id="whatsappFab0569"/);
  assert.match(html, /position:fixed/);
  assert.match(html, /safe-area-inset-bottom/);
  assert.match(html, /public-agenda-shell-0569\.js\?v=0\.1\.580\.1/);
  assert.doesNotMatch(html, /Minha Área/i);
  assert.doesNotMatch(html, /minha-area\.html/i);
  assert.doesNotMatch(html, /Administrar|Login|Senha|Reservar vaga|Fazer pedido de reserva/i);
});

test("0569 whole valid card goes only to canonical official BlaBlaCar trip URL", () => {
  assert.match(app, /function validatedBlaBlaPublicUrl0569/);
  assert.match(app, /\["http:", "https:"\]\.includes\(url\.protocol\)/);
  assert.match(app, /requested_seats/);
  assert.match(app, /search_origin/);
  assert.match(app, /sourceParam.*CARPOOLING/);
  assert.match(app, /isOfficialBlaBlaHost0569/);
  assert.match(app, /normalizedPath === "\/trip"/);
  assert.match(app, /normalizedPath\.startsWith\("\/trip\/"\)/);
  assert.match(app, /item\?\.blablaPublicUrl/);
  assert.match(app, /card\.href = publicUrl/);
  assert.match(app, /document\.createElement\(publicUrl \? "a" : "article"\)/);
  assert.doesNotMatch(app, /\/search\?/i);
  assert.doesNotMatch(app, /blablacar:\/\//i);
  assert.doesNotMatch(app, /method:\s*["']POST["']/);
});

test("0580 card surface shows only anonymous canonical vacancies per segment", () => {
  assert.match(app, /agendaDate0569/);
  assert.match(app, /agendaDriver0569/);
  assert.match(app, /agendaJourney0569/);
  assert.match(app, /function publicSegmentRows0580/);
  assert.match(app, /item\?\.segmentAvailability/);
  assert.match(app, /"Vagas por trecho"/);
  assert.match(app, /"LOTADO"/);
  assert.match(app, /segmentSeatDots0580/);
  assert.match(app, /appendSegmentAvailability0580\(card, item, stops\)/);
  assert.doesNotMatch(app, /passengerCount0569/);
  assert.doesNotMatch(app, /agendaOccupancy0569/);
  assert.doesNotMatch(app, /confirmedPassengerSeats/);
  assert.doesNotMatch(app, /segmentPassengerLoads/);
  assert.doesNotMatch(app, /passengerSeats/);
  assert.doesNotMatch(app, /passageiro/i);
  assert.doesNotMatch(html, /agendaOccupancy0569/);
  assert.doesNotMatch(html, /passageiro/i);
  assert.doesNotMatch(app, /priceToNextCents/);
  assert.doesNotMatch(app, /plannedAddress|\.address/);
  assert.doesNotMatch(app, /toggleAgendaTripDetails|agendaExpanded|expandedItinerary|expandHint/i);
});

test("0569 WhatsApp uses configured public driver field dynamically and never hardcodes a number", () => {
  assert.match(app, /body\?\.driver\?\.whatsapp/);
  assert.match(app, /https:\/\/wa\.me\/\$\{digits\}/);
  assert.match(app, /whatsappDigits0569/);
  assert.doesNotMatch(app, /wa\.me\/[0-9]{10,15}/);
  assert.match(api, /const driverWhatsapp0519 = cleanText\(driver\.driverWhatsapp, 24\)/);
  assert.match(api, /profile\.whatsapp = driverWhatsapp0519/);
});

test("0569 missing BlaBlaCar URL leaves the shell visible but inert", () => {
  assert.match(app, /document\.createElement\(publicUrl \? "a" : "article"\)/);
  assert.match(app, /card\.setAttribute\("aria-disabled", "true"\)/);
  assert.doesNotMatch(app, /Reserva temporariamente indisponível/);
});

test("0580 segment labels and dots follow real capacity without a fixed four-seat assumption", () => {
  const labelSource = app.slice(
    app.indexOf("function segmentAvailabilityLabel0580"),
    app.indexOf("function appendSegmentAvailability0580"),
  );
  const helpers = Function(labelSource + "\nreturn { segmentAvailabilityLabel0580, segmentSeatDots0580 };")();
  assert.equal(helpers.segmentAvailabilityLabel0580(0), "LOTADO");
  assert.equal(helpers.segmentAvailabilityLabel0580(1), "1 vaga");
  assert.equal(helpers.segmentAvailabilityLabel0580(3), "3 vagas");
  assert.equal(helpers.segmentSeatDots0580(4, 1), "●●●○");
  assert.equal(helpers.segmentSeatDots0580(6, 4), "●●○○○○");
  assert.equal(helpers.segmentSeatDots0580(2, 0), "●●");
});

test("0580 public segment renderer consumes server projection instead of passenger identities", () => {
  const block = app.slice(
    app.indexOf("function publicSegmentRows0580"),
    app.indexOf("function appendJourney0569"),
  );
  assert.match(block, /segmentAvailability/);
  assert.match(block, /availableSeats/);
  assert.match(block, /item\?\.capacity/);
  assert.doesNotMatch(block, /passengerName|passengerId|phone|contact|bookingId|sourceReference|segmentPassengerLoads/);
  assert.match(html, /agendaSegments0580/);
  assert.match(html, /agendaSegmentDots0580/);
});
