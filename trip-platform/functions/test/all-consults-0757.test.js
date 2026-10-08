"use strict";

const assert = require("assert");
const { capabilities0757, createAllConsults0757 } = require("../all-consults-0757");

function response0757() {
  return {
    headers: {},
    statusCode: 0,
    body: "",
    set(name, value) { this.headers[name] = value; return this; },
    status(code) { this.statusCode = code; return this; },
    send(body) { this.body = body; return this; },
  };
}

async function run() {
  const caps = capabilities0757();
  assert.ok(caps.some((item) => item.id === "agenda" && item.mode === "LIVE_CANONICAL"));
  assert.ok(caps.some((item) => item.id === "covers" && item.mode === "REMOTE_COLLECTOR"));
  assert.ok(caps.every((item) => item.readOnly === true));

  let delegated = null;
  const api = createAllConsults0757({
    liveAgendaTool0732: {
      async getLiveAgendaTool0732(req, res, username) {
        delegated = { req, res, username };
        return "delegated-live";
      },
    },
  });

  const capsRes = response0757();
  await api.getCapabilities0757({}, capsRes);
  const body = JSON.parse(capsRes.body);
  assert.strictEqual(capsRes.statusCode, 200);
  assert.strictEqual(body.readOnly, true);
  assert.strictEqual(body.controlEnabled, false);
  assert.strictEqual(capsRes.headers["Cache-Control"], "no-store, no-cache, max-age=0, must-revalidate");

  const req = { query: { date: "2026-10-13", origin: "Três Corações", destination: "Santo André" } };
  const res = response0757();
  const result = await api.getTrips0757(req, res, "ezequiel");
  assert.strictEqual(result, "delegated-live");
  assert.strictEqual(delegated.req, req);
  assert.strictEqual(delegated.res, res);
  assert.strictEqual(delegated.username, "ezequiel");

  console.log("ALL Consults 0.1.757 contracts: OK");
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
