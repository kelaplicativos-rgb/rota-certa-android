"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, `missing block start: ${start}`);
  assert.ok(to > from, `missing block end: ${end}`);
  return source.slice(from, to);
}

test("A/B existing canonical passenger is automatically authorized unless blocked", () => {
  const sync = block(api, "async function syncDriverPassengerDirectory", "async function invalidatePassengerIdentitySessions");
  assert.match(sync, /passengerId/);
  assert.match(sync, /blocked: raw && raw\.blocked === true/);
  assert.match(sync, /const status = item\.blocked \? "BLOCKED" : "AUTHORIZED"/);
  assert.doesNotMatch(sync, /PASSENGER_RESTRICTED_ACCESS_STATUSES/);
});

test("C unknown phone remains denied by Agenda de Viagens gate", () => {
  const open = block(api, "async function openPassengerAgendaView", "async function invalidatePassengerSessions");
  assert.match(open, /passenger_access_not_available/);
  assert.match(open, /não está associado a um passageiro cadastrado nesta Agenda de Viagens/);
});

test("D canonical block is written by passengerId and invalidates access sessions", () => {
  const setBlock = block(api, "async function setDriverPassengerBlocked", "async function commitPassengerWhatsappWrites");
  assert.match(setBlock, /passenger_id_required/);
  assert.match(setBlock, /const status = blocking \? "BLOCKED" : "AUTHORIZED"/);
  assert.match(setBlock, /passengerAccessForIdentity\(driver\.username, passengerId, passengerContact\)/);
  assert.match(setBlock, /invalidatePassengerIdentitySessions\(passengerId/);
});

test("E blocking cancels active Rota Certa reservations and recalculates seats", () => {
  const cancel = block(api, "async function cancelActiveBookingsForBlockedPassenger", "async function setDriverPassengerBlocked");
  assert.match(cancel, /\["REQUESTED", "HELD", "CONFIRMED"\]/);
  assert.match(cancel, /"ROTA_CERTA"/);
  assert.match(cancel, /status: "CANCELLED"/);
  assert.match(cancel, /reconciledSegmentLoads/);
  assert.match(cancel, /statusForReconciledLoads/);
  assert.match(cancel, /refundBookingCreditsIfNeeded/);
});

test("F changed phone or visual data cannot replace permanent identity", () => {
  const identity = block(api, "async function passengerAccessForPassengerId", "function passengerSessionOwnsBooking");
  assert.match(identity, /where\("passengerId", "==", canonicalPassengerId\)/);
  const sync = block(api, "async function syncDriverPassengerDirectory", "async function invalidatePassengerIdentitySessions");
  assert.match(sync, /where\("passengerId", "==", item\.passengerId\)/);
  assert.match(sync, /status: "MOVED"/);
  assert.match(sync, /movedToPassengerContact: item\.passengerContact/);
  const update = block(api, "async function updateDriverPassengerWhatsapp", "async function resetDriverPassengerPassword");
  assert.match(update, /passengerId/);
  assert.match(update, /status: "MOVED"/);
  assert.match(update, /collectionGroup\("bookings"\)\.where\("passengerId", "==", passengerId\)/);
});

test("G blocked state survives sessions because every Agenda view rechecks durable access", () => {
  const view = block(api, "async function requirePassengerAgendaView", "async function openPassengerAgendaView");
  assert.match(view, /passengerAccessForIdentity/);
  assert.match(view, /passengerAccessIsAuthorized/);
  const setBlock = block(api, "async function setDriverPassengerBlocked", "async function commitPassengerWhatsappWrites");
  assert.match(setBlock, /where\("passengerId", "==", passengerId\)/);
  assert.match(setBlock, /identityWrites/);
  assert.match(setBlock, /commitPassengerWhatsappWrites\(identityWrites\)/);
  assert.match(setBlock, /status,/);
});

test("passenger-facing web terminology is Agenda de Viagens", () => {
  assert.match(web, /Agenda de Viagens/);
  assert.doesNotMatch(web, /Agenda Pública/);
});
