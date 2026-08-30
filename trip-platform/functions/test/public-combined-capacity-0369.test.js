const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..", "..");
const publicApp = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const backend = fs.readFileSync(path.join(root, "functions", "index.js"), "utf8");

function functionSource(source, startName, endName) {
  const start = source.indexOf(`function ${startName}`);
  const end = source.indexOf(`function ${endName}`, start + 1);
  assert.ok(start >= 0, `missing ${startName}`);
  assert.ok(end > start, `missing ${endName}`);
  return source.slice(start, end);
}

test("public card exposes real Timeline seats plus the Rota Certa pool", () => {
  const source =
    functionSource(publicApp, "normalizedSeatCount", "seatAvailabilityText");
  const factory = new Function(
    "function seatRange(item) { return { minimum: Number(item.capacity || 0), maximum: Number(item.capacity || 0) }; };" +
    source +
    "; return { normalizedSeatCount, seatSourceBreakdown };"
  );
  const { seatSourceBreakdown } = factory();
  assert.deepEqual(
    seatSourceBreakdown({ capacity: 7, blablaAvailableSeats: 3, rotaCertaSeatPool: 4 }, 7),
    { blabla: 3, rotaCerta: 4, total: 7 },
  );
  assert.deepEqual(
    seatSourceBreakdown({ capacity: 4, blablaAvailableSeats: 0, rotaCertaSeatPool: 4 }, 4),
    { blabla: 0, rotaCerta: 4, total: 4 },
  );
  assert.deepEqual(
    seatSourceBreakdown({ capacity: 7, blablaAvailableSeats: 3, rotaCertaSeatPool: 4 }, 6),
    { blabla: 3, rotaCerta: 3, total: 6 },
  );
  assert.match(publicApp, /BlaBlaCar \$\{sourceBreakdown\.blabla\} • Rota Certa \$\{sourceBreakdown\.rotaCerta\}/);
  assert.match(publicApp, /Disponibilidade combinada/);
});

test("existing public BlaBla trip may refresh combined capacity without weakening other trip protection", () => {
  assert.match(backend, /const externalBlaBlaProjection = isExternalBlaBlaTrip\("", previous\);/);
  assert.match(backend, /protectedCapacityChange = capacity !== Number\(previous\.capacity \|\| 0\) && !externalBlaBlaProjection/);
  assert.match(backend, /const externalCapacityChanged = capacityChanged && isExternalBlaBlaTrip\(token, previous\);/);
  assert.match(backend, /assertNoOverbooking\(candidateTrip, loads\);/);
  assert.match(backend, /oldStopIds !== newStopIds/);
});


test("public API preserves raw published capacity and exposes real availability separately", () => {
  assert.match(backend, /blablaAvailableSeats/);
  assert.match(backend, /rotaCertaSeatPool/);
  assert.match(backend, /publishedSeats: data\.publishedSeats/);
  assert.match(backend, /blablaAvailableSeats: data\.blablaAvailableSeats/);
  assert.match(backend, /rotaCertaSeatPool: data\.rotaCertaSeatPool/);
});
