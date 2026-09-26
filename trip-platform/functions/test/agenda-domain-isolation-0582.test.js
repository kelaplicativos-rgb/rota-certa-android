"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  canonicalAgendaVisibleUntil0582,
  canonicalAgendaLifecycleDecision0582,
} = require("../agenda-domain-0582");

test("0582 canonical cutoff is the public lifecycle authority", () => {
  const now = 2_000_000_000_000;
  const cutoff = now + 8 * 60 * 60 * 1000;
  const data = {
    departureAtMillis: now + 60 * 60 * 1000,
    agendaVisibleUntilMillis0581: cutoff,
    stops: [
      { order: 0, plannedDepartureMillis: now + 60 * 60 * 1000 },
      { order: 1, plannedArrivalMillis: now - 1 },
    ],
  };
  assert.equal(canonicalAgendaVisibleUntil0582(data), cutoff);
  assert.deepEqual(canonicalAgendaLifecycleDecision0582(data, now), {
    visible: true,
    visibleUntilMillis: cutoff,
    reasonCode: "AGENDA_VISIBILITY_FUTURE_TRIP",
  });
});

test("0582 permalink and capacity cannot participate in lifecycle", () => {
  const now = 2_000_000_000_000;
  const base = {
    departureAtMillis: now + 60 * 60 * 1000,
    agendaVisibleUntilMillis0581: now + 6 * 60 * 60 * 1000,
  };
  const a = canonicalAgendaLifecycleDecision0582({
    ...base,
    blablaPublicUrl: "https://www.blablacar.com.br/trip?id=a",
    capacity: 1,
  }, now);
  const b = canonicalAgendaLifecycleDecision0582({
    ...base,
    blablaPublicUrl: "",
    capacity: 99,
    capacityReliable: false,
  }, now);
  assert.deepEqual(a, b);
});

test("0582 legacy fallback remains deterministic for pre-0581 documents", () => {
  const departure = 1_000_000;
  const arrival = departure + 4 * 60 * 60 * 1000;
  const data = {
    departureAtMillis: departure,
    stops: [
      { order: 1, plannedArrivalMillis: arrival },
      { order: 0, plannedDepartureMillis: departure },
    ],
  };
  assert.equal(canonicalAgendaVisibleUntil0582(data), arrival + 60 * 60 * 1000);
});
