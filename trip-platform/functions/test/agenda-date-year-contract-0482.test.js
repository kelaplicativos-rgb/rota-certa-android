"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function compileCompactAt(year, monthIndex, day) {
  const source = between(app, "function agendaDateLabel0473", "function orderedStops");
  assert.doesNotMatch(source, /;\\n\s*return weekdays/, "literal \\n must never enter executable JavaScript");
  const fixed = source.replace(
    "const now = new Date();",
    `const now = new Date(${year}, ${monthIndex}, ${day}, 12, 0, 0, 0);`,
  );
  assert.notEqual(fixed, source, "fixed-now injection failed");
  return Function(fixed + "\nreturn agendaDateLabel0473;")();
}

function compileLongDate() {
  const source = between(app, "function agendaLongDateLabel0480", "function agendaStopMoment0480");
  return Function(source + "\nreturn agendaLongDateLabel0480;")();
}

test("0482 public bundle is syntactically executable", () => {
  assert.doesNotThrow(() => Function(app));
});

test("0482 compact date contract preserves current-year brevity and explicit other-year labels", () => {
  const compact = compileCompactAt(2026, 8, 5);
  assert.equal(compact(new Date(2026, 8, 5, 12).getTime()), "Hoje");
  assert.equal(compact(new Date(2026, 8, 6, 12).getTime()), "Amanhã");
  assert.equal(compact(new Date(2026, 9, 2, 12).getTime()), "Sex. 02 Out.");
  assert.equal(compact(new Date(2027, 7, 7, 12).getTime()), "Sáb. 07 Ago. 2027");
  assert.equal(compact(new Date(2025, 7, 7, 12).getTime()), "Qui. 07 Ago. 2025");
});

test("0482 expanded date contract always exposes the complete year", () => {
  const longDate = compileLongDate();
  assert.equal(longDate(new Date(2026, 9, 2, 12).getTime()), "Sexta-feira, 2 de outubro de 2026");
  assert.equal(longDate(new Date(2027, 7, 7, 12).getTime()), "Sábado, 7 de agosto de 2027");
});

test("0482 compact and expanded labels remain wired to the same canonical departure", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(cards, /date\.dataset\.compactLabel = agendaDateLabel0473\(item\.departureAtMillis\)/);
  assert.match(cards, /date\.dataset\.expandedLabel = agendaLongDateLabel0480\(item\.departureAtMillis\)/);
  assert.match(cards, /expanded \? dateNode\.dataset\.expandedLabel : dateNode\.dataset\.compactLabel/);
});
