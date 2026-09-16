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
  assert.match(html, /public-agenda-shell-0569\.js\?v=0\.1\.569\.1/);
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

test("0569 card surface contains route shell only and exposes no passenger occupancy", () => {
  assert.match(app, /agendaDate0569/);
  assert.match(app, /agendaDriver0569/);
  assert.match(app, /agendaJourney0569/);
  assert.doesNotMatch(app, /passengerCount0569/);
  assert.doesNotMatch(app, /agendaOccupancy0569/);
  assert.doesNotMatch(app, /confirmedPassengerSeats/);
  assert.doesNotMatch(app, /segmentPassengerLoads/);
  assert.doesNotMatch(app, /segmentAvailability/);
  assert.doesNotMatch(app, /passageiro/i);
  assert.doesNotMatch(html, /agendaOccupancy0569/);
  assert.doesNotMatch(html, /passageiro/i);
  assert.doesNotMatch(app, /Vagas por trecho/);
  assert.doesNotMatch(app, /Disponibilidade/);
  assert.doesNotMatch(app, /LOTADO/);
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
