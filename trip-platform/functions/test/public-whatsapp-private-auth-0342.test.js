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


test("passengerId remains canonical when the driver changes the Agenda access WhatsApp", () => {
  const update = block(api, "async function updateDriverPassengerWhatsapp", "async function resetDriverPassengerPassword");
  assert.match(update, /passengerId/);
  assert.match(update, /passenger_whatsapp_conflict/);
  assert.match(update, /passengerAccounts/);
  assert.match(update, /passengerSessions/);
  assert.match(update, /passengerAgendaViewSessions/);
  assert.match(update, /passengerCreditLedgerRef/);
  assert.match(update, /collectionGroup\("bookings"\)/);
  assert.match(update, /passengerBookingIndexRef\(newPassengerContact/);
  assert.match(update, /status: "MOVED"/);
  assert.match(api, /async function passengerAccessForPassengerId/);
  assert.match(api, /function passengerSessionOwnsBooking/);
  assert.match(api, /path === "\/v1\/driver\/passengers\/whatsapp"/);
});

test("directory sync rejects duplicate access WhatsApp across different passengerId values", () => {
  const sync = block(api, "async function syncDriverPassengerDirectory", "async function setDriverPassengerBlocked");
  assert.match(sync, /passenger_whatsapp_conflict/);
  assert.match(sync, /previousPassengerId !== normalized\[index\]\.passengerId/);
  assert.match(sync, /inRequestContacts/);
});

test("captured contact and Agenda access WhatsApp are kept as separate local fields", () => {
  const identity = fs.readFileSync(
    path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerIdentityStore.kt"),
    "utf8",
  );
  assert.match(identity, /val whatsapp: String = ""/);
  assert.match(identity, /val agendaAccessWhatsapp: String = ""/);
});


test("unknown, pending and restricted WhatsApp receive explicit Agenda access messages", () => {
  const openAccess = block(api, "async function openPassengerAgendaView", "async function invalidatePassengerSessions");
  assert.match(openAccess, /passenger_access_denied/);
  assert.match(openAccess, /Acesso negado\. Este WhatsApp não está na lista de passageiros autorizados desta Agenda/);
  assert.match(openAccess, /passenger_access_pending/);
  assert.match(openAccess, /Seu convite precisa ser aprovado pelo motorista/);
  assert.match(openAccess, /acesso suspenso ou bloqueado nesta Agenda/);
  const requestAccess = block(web, "async function requestPublicAgendaAccess", "async function validatePassengerSession");
  assert.match(requestAccess, /accessMessage"\)\.className = "error"/);
  assert.match(requestAccess, /Acesso negado\. Este WhatsApp não está autorizado para esta Agenda/);
});
