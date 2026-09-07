"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const backend = fs.readFileSync(path.join(root, "trip-platform", "functions", "index.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function compileCompact() {
  const source = between(app, "function agendaTimezone0496", "function orderedStops");
  assert.doesNotMatch(source, /;\\n\s*return weekdays/, "literal \\n must never enter executable JavaScript");
  return Function(source + "\nreturn agendaDateLabel0473;")();
}

function compileLongDate() {
  const helpers = between(app, "function agendaTimezone0496", "function orderedStops");
  const longDate = between(app, "function agendaLongDateLabel0480", "function agendaStopMoment0480");
  return Function(helpers + "\n" + longDate + "\nreturn agendaLongDateLabel0480;")();
}

const SAO_PAULO = "America/Sao_Paulo";
const instant = (iso) => Date.parse(iso);

test("0482 public bundle is syntactically executable", () => {
  assert.doesNotThrow(() => Function(app));
});

test("0496 compact date contract uses the canonical trip timezone for Hoje and Amanhã", () => {
  const compact = compileCompact();
  const now = instant("2026-09-07T23:30:00-03:00");

  assert.equal(compact(instant("2026-09-07T19:00:00-03:00"), SAO_PAULO, now), "Hoje");
  assert.equal(compact(instant("2026-09-08T11:00:00-03:00"), SAO_PAULO, now), "Amanhã");
  assert.equal(compact(instant("2026-09-06T19:00:00-03:00"), SAO_PAULO, now), "Ontem");
});

test("0496 canonical timezone prevents a UTC viewer from shifting the trip to the wrong calendar day", () => {
  const compact = compileCompact();
  const now = instant("2026-09-08T02:30:00Z");

  assert.equal(compact(instant("2026-09-07T22:00:00Z"), SAO_PAULO, now), "Hoje");
  assert.equal(compact(instant("2026-09-08T14:00:00Z"), SAO_PAULO, now), "Amanhã");
});

test("0482 compact date contract preserves current-year brevity and explicit other-year labels", () => {
  const compact = compileCompact();
  const now = instant("2026-09-05T12:00:00-03:00");

  assert.equal(compact(instant("2026-09-05T12:00:00-03:00"), SAO_PAULO, now), "Hoje");
  assert.equal(compact(instant("2026-09-06T12:00:00-03:00"), SAO_PAULO, now), "Amanhã");
  assert.equal(compact(instant("2026-10-02T12:00:00-03:00"), SAO_PAULO, now), "Sex. 02 Out.");
  assert.equal(compact(instant("2027-08-07T12:00:00-03:00"), SAO_PAULO, now), "Sáb. 07 Ago. 2027");
  assert.equal(compact(instant("2025-08-07T12:00:00-03:00"), SAO_PAULO, now), "Qui. 07 Ago. 2025");
});

test("0482 expanded date contract always exposes the complete year in the canonical timezone", () => {
  const longDate = compileLongDate();
  assert.equal(longDate(instant("2026-10-02T12:00:00-03:00"), SAO_PAULO), "Sexta-feira, 2 de outubro de 2026");
  assert.equal(longDate(instant("2027-08-07T12:00:00-03:00"), SAO_PAULO), "Sábado, 7 de agosto de 2027");
});

test("0496 backend preserves canonical timezone through the public projection", () => {
  const canonical = between(backend, "function safePublicTripFromCanonical0434", "function safePublicTrip(");
  const legacy = between(backend, "function safePublicTrip(", "function publicTripProjection0491");
  assert.match(canonical, /timezoneId: payload\.timezoneId/);
  assert.match(legacy, /timezoneId: cleanText\(data\.publicTimezoneId0411, 80\)/);
});

test("0496 compact expanded and stop times are wired to the same canonical timezone", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  const toggle = between(app, "function toggleAgendaTripDetails0480", "function renderAgendaCards");
  assert.match(cards, /date\.dataset\.compactLabel = agendaDateLabel0473\(item\.departureAtMillis, item\.timezoneId\)/);
  assert.match(cards, /date\.dataset\.expandedLabel = agendaLongDateLabel0480\(item\.departureAtMillis, item\.timezoneId\)/);
  assert.match(cards, /agendaTripTime0496\(startMillis0473 \|\| item\.departureAtMillis, item\.timezoneId\)/);
  assert.match(cards, /agendaTripTime0496\(endMillis0473, item\.timezoneId\)/);
  assert.match(cards, /agendaTripTime0496\(moment0480, item\.timezoneId\)/);
  assert.match(toggle, /expanded \? dateNode\.dataset\.expandedLabel : dateNode\.dataset\.compactLabel/);
});
