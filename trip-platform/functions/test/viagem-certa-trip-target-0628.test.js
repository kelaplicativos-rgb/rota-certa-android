"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicJs = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0628 trip token is stronger than a public driver alias during passenger login", () => {
  const target = between(api, "async function resolvePassengerTarget0625", "async function openPassengerPasswordSession0625");
  assert.match(target, /requestedResolved = await resolveDriverUsername\(requestedDriver\)/);
  assert.match(target, /const tripResolved = await resolveDriverUsername\(tripDriver\)/);
  assert.match(
    target,
    /requestedResolved\.canonicalUsername !== tripResolved\.canonicalUsername/,
  );
  assert.doesNotMatch(target, /tripDriver !== driverUsername/);
  assert.match(target, /return \{ driverUsername: tripResolved\.canonicalUsername, tripToken \}/);
});

test("0628 trip-specific password session does not send a stale slug beside the trip token", () => {
  const session = between(publicJs, "async function ensurePassengerSession0625", "async function confirmBooking0625");
  assert.match(session, /publicSlug: bookingSelection0623\.tripToken \? undefined : publicSlug0569/);
  assert.match(session, /driverUsername: bookingSelection0623\.tripToken \? undefined : driverUsername0569/);
  assert.match(session, /agendaToken: bookingSelection0623\.tripToken \? undefined : agendaToken0569/);
  assert.match(session, /tripToken: bookingSelection0623\.tripToken/);
});

test("0628 target failure remains explicit and is not a fake successful reservation", () => {
  const open = between(api, "async function openPassengerPasswordSession0625", "async function signupPassengerAccount");
  assert.match(open, /agenda_target_invalid/);
  assert.match(open, /A viagem informada não está disponível\./);
  assert.match(publicJs, /if \(!response\.ok\) throw new Error\(safeMessage0569\(body\?\.message\)/);
});
