"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, `missing block start: ${start}`);
  assert.ok(to > from, `missing block end: ${end}`);
  return source.slice(from, to);
}

test("consultation gate asks only WhatsApp and private gate owns password", () => {
  assert.match(html, /id="accessContact"/);
  assert.doesNotMatch(html, /id="accessPassword"/);
  assert.match(html, /id="privateAuthPassword"/);
  assert.match(html, /id="privateAuthPasswordConfirm"/);
  assert.match(web, /requestPublicAgendaAccess/);
  assert.match(web, /showPrivateAuthGate/);
});

test("authorized phone gets a separate view session and blocked or suspended access stays unavailable", () => {
  assert.match(api, /passengerAgendaViewSessions/);
  assert.match(api, /X-Rota-Certa-Agenda-View-Token/);
  assert.match(api, /PASSENGER_AUTHORIZED_ACCESS_STATUSES/);
  assert.match(api, /PASSENGER_RESTRICTED_ACCESS_STATUSES/);
  assert.match(api, /Seu acesso a esta agenda não está disponível\./);
  const agenda = block(api, "async function getPublicDriverAgenda", "async function createDriverTrip");
  const trip = block(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.match(agenda, /requirePassengerAgendaView/);
  assert.match(trip, /requirePassengerAgendaView/);
});

test("phone normalization collapses masks and Brazilian country prefix before identity lookup", () => {
  const normalize = block(api, "function normalizeBrazilWhatsapp", "function publicBookingIdempotencyKey");
  assert.match(normalize, /replace\(\/\\D\/g, ""\)/);
  assert.match(normalize, /digits\.startsWith\("55"\)/);
  assert.match(normalize, /return `\+55\$\{digits\}`/);
});

test("first private action creates password on the existing canonical passenger instead of a new passenger", () => {
  const activate = block(api, "async function activatePassengerAccount", "async function loginPassengerAccount");
  assert.match(activate, /view\.passengerId/);
  assert.match(activate, /driverPassengerAccessRef/);
  assert.match(activate, /passengerAccounts/);
  assert.doesNotMatch(activate, /PassengerProfile|publicPassengers|bookingPassengers/);
  assert.match(activate, /passengerPasswordDigest/);
  assert.match(activate, /createPassengerSession\(passengerContact, passengerId\)/);
});

test("reservation, alteration and cancellation require authenticated passenger session", () => {
  for (const [start, end] of [
    ["async function createBooking", "async function cancelPublicBooking"],
    ["async function cancelPublicBooking", "async function updatePublicBooking"],
    ["async function updatePublicBooking", "async function mutateProtectedBooking"],
  ]) {
    assert.match(block(api, start, end), /requirePassengerSession/);
  }
  const create = block(api, "async function createBooking", "async function cancelPublicBooking");
  assert.match(create, /passengerId,/);
  assert.match(create, /passenger_identity_unavailable/);
});

test("canonical passenger directory sync preserves restricted status and never creates a password", () => {
  const sync = block(api, "async function syncDriverPassengerDirectory", "async function setDriverPassengerBlocked");
  assert.match(sync, /passengerId/);
  assert.match(sync, /PASSENGER_RESTRICTED_ACCESS_STATUSES/);
  assert.match(sync, /"AUTHORIZED"/);
  assert.doesNotMatch(sync, /passwordHash|temporaryPassengerPassword/);
});

test("private session token is not persisted in localStorage and reservation intent resumes after auth", () => {
  const save = block(web, "function savePassengerSession", "function openPassengerPortal");
  assert.match(save, /sessionStorage\.setItem\("rotacerta-passenger-session"/);
  assert.doesNotMatch(save, /localStorage\.setItem\("rotacerta-passenger-session"/);
  assert.match(web, /pendingPrivateAction/);
  assert.match(web, /resume === "reserve"/);
  assert.match(web, /showPrivateAuthGate\("booking"\)/);
});
