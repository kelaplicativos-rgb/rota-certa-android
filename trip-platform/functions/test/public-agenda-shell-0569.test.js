"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("0569 public Agenda keeps passenger access separate from trip-card actions", () => {
  assert.match(html, /id="agendaTrips"/);
  assert.match(html, /id="whatsappFab0569"/);
  assert.match(html, /id="accessGate0589"/);
  assert.match(html, /id="passengerAreaLink0589"/);
  assert.match(html, /minha-area\.html/i);
  assert.match(html, /position:fixed/);
  assert.match(html, /safe-area-inset-bottom/);
  assert.match(html, /public-agenda-shell-0569\.js\?v=0\.1\.622-html-convergence/);
  assert.doesNotMatch(html, /Administrar|Senha|Reservar vaga|Fazer pedido de reserva/i);
});

test("0584 BlaBlaCar navigation is isolated in Ver carona and never attached to the whole card", () => {
  const cardRenderer = app.slice(
    app.indexOf("function renderTripCard0569"),
    app.indexOf("function renderAgenda0569"),
  );
  assert.match(app, /function validatedBlaBlaPublicUrl0569/);
  assert.match(app, /\["http:", "https:"\]\.includes\(url\.protocol\)/);
  assert.match(app, /requested_seats/);
  assert.match(app, /search_origin/);
  assert.match(app, /sourceParam.*CARPOOLING/);
  assert.match(app, /isOfficialBlaBlaHost0569/);
  assert.match(app, /normalizedPath === "\/trip"/);
  assert.match(app, /normalizedPath\.startsWith\("\/trip\/"\)/);
  assert.match(app, /item\?\.blablaPublicUrl/);
  assert.match(app, /const card = document\.createElement\("article"\)/);
  assert.doesNotMatch(app, /card\.href = publicUrl/);
  assert.match(app, /viewRide\.href = publicUrl/);
  assert.match(app, /viewRide\.textContent = "Ver carona"/);
  assert.doesNotMatch(app, /\/search\?/i);
  assert.doesNotMatch(app, /blablacar:\/\//i);
  assert.doesNotMatch(cardRenderer, /method:\s*["']POST["']/);
  assert.doesNotMatch(cardRenderer, /bindTripCardNavigation0596|window\.location\.assign\(publicUrl\)/);
  assert.doesNotMatch(app, /function bindTripCardNavigation0596/);
});

test("0580 card surface shows only anonymous canonical vacancies per segment", () => {
  const cardRenderer = app.slice(
    app.indexOf("function renderTripCard0569"),
    app.indexOf("function renderAgenda0569"),
  );
  const segmentRenderer = app.slice(
    app.indexOf("function publicSegmentRows0580"),
    app.indexOf("const AGENDA_CARD_REFRESH_KEY_0596"),
  );
  assert.match(app, /agendaDate0569/);
  assert.match(app, /agendaDriver0569/);
  assert.match(app, /agendaJourney0569/);
  assert.match(app, /function publicSegmentRows0580/);
  assert.match(app, /item\?\.segmentAvailability/);
  assert.match(app, /"Vagas por trecho"/);
  assert.match(app, /"LOTADO"/);
  assert.match(app, /segmentSeatDots0580/);
  assert.match(app, /appendSegmentAvailability0580\(card, item, stops\)/);
  assert.doesNotMatch(cardRenderer, /passengerCount0569|agendaOccupancy0569|confirmedPassengerSeats|segmentPassengerLoads/);
  assert.doesNotMatch(cardRenderer, /passengerName|passengerId|phone|contact|bookingId|sourceReference|passageiro/i);
  assert.doesNotMatch(segmentRenderer, /passengerName|passengerId|phone|contact|bookingId|sourceReference/);
  assert.doesNotMatch(html, /agendaOccupancy0569/);
  assert.doesNotMatch(cardRenderer, /priceToNextCents/);
  assert.doesNotMatch(cardRenderer, /plannedAddress|\.address/);
  assert.doesNotMatch(cardRenderer, /toggleAgendaTripDetails|agendaExpanded|expandedItinerary|expandHint/i);
});

test("0569 WhatsApp uses configured public driver field dynamically and never hardcodes a number", () => {
  assert.match(app, /body\?\.driver\?\.whatsapp/);
  assert.match(app, /https:\/\/wa\.me\/\$\{digits\}/);
  assert.match(app, /whatsappDigits0569/);
  assert.doesNotMatch(app, /wa\.me\/[0-9]{10,15}/);
  assert.match(api, /const driverWhatsapp0519 = cleanText\(driver\.driverWhatsapp, 24\)/);
  assert.match(api, /profile\.whatsapp = driverWhatsapp0519/);
});

test("0584 missing BlaBlaCar URL keeps the card visible and disables only Ver carona", () => {
  assert.match(app, /const card = document\.createElement\("article"\)/);
  assert.match(app, /agendaViewRideUnavailable0584/);
  assert.match(app, /unavailable\.setAttribute\("aria-disabled", "true"\)/);
  assert.doesNotMatch(app, /card\.setAttribute\("aria-disabled", "true"\)/);
});

test("0584 each available segment gets its own WhatsApp request and full segments do not", () => {
  const start = app.indexOf("function whatsappDigits0569");
  const end = app.indexOf("\nfunction syncWhatsappFab0569", start);
  assert.ok(start >= 0 && end > start);
  const source = [
    'let publicDriverWhatsapp0569 = "+55 11 99999-0000";',
    'function dateLabel0569(ms){ return ms === 2000 ? "Dom, 20 set" : "Dom, 20 set"; }',
    'function timeLabel0569(ms){ return ms === 2000 ? "20:20" : "10:30"; }',
    app.slice(start, end),
    "return { segmentWhatsappMessage0584, segmentWhatsappHref0584, segmentBoardingMillis0584 };",
  ].join("\n");
  const helpers = Function(source)();
  const available = { from: "Pouso Alegre", to: "Três Corações", availableSeats: 1 };
  const trip = { departureAtMillis: 1000, timezoneId: "America/Sao_Paulo" };
  const stops = [
    { plannedDepartureMillis: 1000 },
    { plannedDepartureMillis: 2000 },
    { plannedDepartureMillis: 3000 },
  ];
  assert.equal(helpers.segmentBoardingMillis0584(trip, stops, 1), 2000);
  const message = helpers.segmentWhatsappMessage0584(trip, available, stops, 1);
  assert.match(message, /Pouso Alegre → Três Corações/);
  assert.match(message, /Dom, 20 set/);
  assert.match(message, /20:20/);
  assert.doesNotMatch(message, /10:30/);
  const href = helpers.segmentWhatsappHref0584(trip, available, stops, 1);
  const url = new URL(href);
  assert.equal(url.hostname, "wa.me");
  assert.equal(url.pathname, "/5511999990000");
  assert.match(url.searchParams.get("text"), /Pouso Alegre → Três Corações/);
  assert.equal(
    helpers.segmentWhatsappHref0584(
      trip,
      { ...available, availableSeats: 0 },
      stops,
      1,
    ),
    "",
  );
  assert.match(app, /reserve\.textContent = "Reserve Já"/);
  assert.match(app, /unavailable\.textContent = "Indisponível"/);
  assert.match(app, /unavailable\.disabled = true/);
  assert.match(app, /segment\.availableSeats > 0/);
  assert.match(html, /\.agendaSegmentReserve0584\{grid-column:3;grid-row:1 \/ span 2/);
  assert.match(html, /\.agendaSegmentReserveUnavailable0584\{background:/);
  assert.doesNotMatch(html, /\.agendaSegmentReserve0584\{grid-column:1 \/ -1/);
  assert.doesNotMatch(app, /wa\.me\/[0-9]{10,15}/);
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
test("0622 public Agenda renders exact year, per-trip profile and near-live refresh", () => {
  assert.match(app, /\$\{parts\.year\}/);
  assert.match(app, /item\?\.blablaProfileName/);
  assert.match(app, /\}, 2000\);/);
  assert.match(api, /blablaProfileName/);
});
