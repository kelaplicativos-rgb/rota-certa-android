"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const android = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "AgendaAutomaticSyncUi0397.kt"), "utf8");
const gradle = fs.readFileSync(path.join(root, "app", "build.gradle.kts"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0589 Viagem Certa opens with WhatsApp identification before any agenda card", () => {
  assert.match(html, /<title>Viagem Certa — Suas viagens<\/title>/);
  assert.match(html, /id="accessGate0589"/);
  assert.match(html, /id="passengerWhatsapp0589"/);
  assert.match(html, /inputmode="tel"/);
  assert.match(html, /id="passengerAccessContinue0589"/);
  assert.match(html, /id="agenda" class="hidden"/);
  assert.match(html, /id="passengerNav0589" class="passengerNav0589 hidden"/);
  assert.match(html, />Minhas viagens</);
});

test("0589 browser exchanges an authorized WhatsApp for a scoped view token and never persists the phone", () => {
  assert.match(shell, /\/v1\/public\/passenger-access/);
  assert.match(shell, /passengerContact/);
  assert.match(shell, /viewToken/);
  assert.match(shell, /X-Rota-Certa-Agenda-View-Token/);
  assert.match(shell, /sessionStorage\.setItem\(agendaViewStorageKey0589, agendaViewToken0589\)/);
  assert.match(shell, /sessionStorage\.removeItem\(agendaViewStorageKey0589\)/);
  assert.doesNotMatch(shell, /localStorage/);
  assert.doesNotMatch(shell, /sessionStorage[^\n]*(passengerContact|passengerWhatsapp|phone)/i);
  assert.doesNotMatch(shell, /__agenda_fallback/);
});

test("0589 backend fails closed for anonymous agenda, change watch and individual trip reads", () => {
  const agenda = between(api, "async function getPublicDriverAgenda", "async function waitPublicAgendaCanonicalChange0495");
  assert.match(agenda, /requirePassengerAgendaView\(req, res, username\)/);
  assert.match(agenda, /identifiedAccessRequired0589: true/);
  assert.match(agenda, /PASSENGER_WHATSAPP_VIEW/);

  const changes = between(api, "async function waitPublicAgendaCanonicalChange0495", "function buildAdminHomeTrip0471");
  assert.match(changes, /requirePassengerAgendaView\(req, res, username\)/);

  const publicTrip = between(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.match(publicTrip, /requirePassengerAgendaView\(req, res, driverUsername\)/);
  assert.match(publicTrip, /PASSENGER_WHATSAPP_VIEW/);
});

test("0589 WhatsApp gate only grants view sessions to passengers already authorized for that driver", () => {
  const access = between(api, "async function openPassengerAgendaView", "async function invalidatePassengerSessions");
  assert.match(access, /enforceBookingRateLimit/);
  assert.match(access, /normalizeBrazilWhatsapp/);
  assert.match(access, /passengerAccessFor\(username, passengerContact\)/);
  assert.match(access, /passengerAccessIsAuthorized\(access\)/);
  assert.match(access, /createPassengerAgendaViewSession/);
  assert.doesNotMatch(access, /passwordHash|passwordSalt/);
});

test("0589 private passenger data remains behind the stronger passenger session", () => {
  assert.match(privateHtml, /VIAGEM CERTA/);
  assert.match(privateHtml, /<h1>Minhas viagens<\/h1>/);
  assert.match(privateHtml, /type="password"/);
  assert.match(privateJs, /headers\.Authorization = "Bearer " \+ sessionToken0491/);
  assert.match(privateJs, /\/v1\/passenger\/me\/bookings/);
  assert.doesNotMatch(privateJs, /localStorage/);
});

test("0589 Android and package metadata expose the new passenger product name without changing the package", () => {
  assert.match(android, /ABRIR VIAGEM CERTA/);
  assert.match(android, /Abrindo o Viagem Certa\./);
  assert.match(gradle, /versionCode = 5880/);
  assert.match(gradle, /versionName = "0\.1\.589"/);
});
