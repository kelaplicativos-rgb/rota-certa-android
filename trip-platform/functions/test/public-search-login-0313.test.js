"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("public agenda now starts behind WhatsApp-only consultation access", () => {
  assert.match(html, /id="accessGate"/);
  assert.match(html, /id="accessContact"/);
  assert.doesNotMatch(html, /id="accessPassword"/);
  assert.match(html, /id="privateAuthPassword"/);
  assert.match(web, /requestPublicAgendaAccess/);
  assert.match(api, /async function requirePassengerAgendaView/);
  assert.match(api, /\/v1\/public\/passenger-access/);
});

test("unknown passenger still cannot create a private identity from the public gate", () => {
  assert.doesNotMatch(html, /id="accessSignup"/);
  assert.doesNotMatch(html, /Criar acesso e entrar/);
  assert.match(api, /async function signupPassengerAccount/);
  assert.match(api, /passenger_invite_required/);
  assert.match(api, /passenger_access_not_available/);
});

test("search filter uses simple browser fields without GPS and keeps dates seats and public cards", () => {
  for (const marker of ["searchFromInput", "searchToInput", "searchDeparture", "searchReturn", "searchSeats", "searchSubmit"]) {
    assert.match(html, new RegExp(`id="${marker}"`));
  }
  assert.doesNotMatch(html, /Usar localização atual|Usar o local digitado/);
  assert.doesNotMatch(web, /navigator\.geolocation|getCurrentPosition\(/);
  assert.match(web, /renderCalendarMonths/);
  assert.match(web, /openCalendarPicker\("departure"\)/);
  assert.match(web, /openCalendarPicker\("returnDate"\)/);
  assert.match(web, /renderAgendaCards\(result\.matches/);
});

test("route search is waypoint-order and segment-capacity aware", () => {
  assert.match(web, /index > fromIndex && stopMatchesSearch/);
  assert.match(web, /availableForTripSegment/);
  assert.match(web, /entry\.available >= seats/);
  assert.match(web, /O local informado não faz parte do percurso disponível nesta data/);
  assert.match(web, /Não há \$\{seats\} lugar\(es\) disponível\(is\) nesse trecho/);
});

test("search result carries exact segment into booking selectors", () => {
  assert.match(web, /next\.set\("embarque"/);
  assert.match(web, /next\.set\("destino"/);
  assert.match(web, /next\.set\("lugares"/);
  assert.match(web, /requestedBoardingStopId/);
  assert.match(web, /requestedDropoffStopId/);
  assert.match(web, /requestedSeats/);
});
