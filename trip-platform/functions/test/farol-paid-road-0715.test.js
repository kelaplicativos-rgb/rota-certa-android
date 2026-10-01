"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  FarolPaidRoadError0715,
  resolveFarolPaidRoad0715,
} = require("../farol-paid-road-0715");

function response(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async text() { return JSON.stringify(payload); },
  };
}

test("terminal Farol fallback normalizes POI then returns real OSRM road km", async () => {
  const calls = [];
  const result = await resolveFarolPaidRoad0715({
    destination: "Shopping Ibirapuera",
    context: "Destino\nShopping Ibirapuera\nMoema\nSão Paulo - SP",
    packageName: "com.ubercab.driver",
    fingerprint: "card-1",
    targets: [
      { latitude: -23.5505, longitude: -46.6333 },
      { latitude: -23.5000, longitude: -46.6200 },
    ],
    apiKey: "test-key",
    fetchImpl: async (url, options = {}) => {
      calls.push(url);
      if (url.includes("api.openai.com")) {
        const body = JSON.parse(options.body);
        assert.equal(body.store, false);
        assert.equal(body.text.format.strict, true);
        return response(200, {
          model: "gpt-5.6-luna",
          output_text: JSON.stringify({
            status: "RESOLVED",
            normalizedAddress: "Shopping Ibirapuera, Moema, São Paulo - SP",
            confidence: 0.97,
            reason: "estabelecimento e bairro identificam o destino",
          }),
        });
      }
      if (url.includes("nominatim.openstreetmap.org")) {
        return response(200, [{ lat: "-23.6100", lon: "-46.6660" }]);
      }
      if (url.includes("router.project-osrm.org")) {
        return response(200, { distances: [[0, 13941, 5569]] });
      }
      throw new Error("unexpected url " + url);
    },
  });
  assert.equal(result.status, "RESOLVED");
  assert.equal(result.normalizedAddress, "Shopping Ibirapuera, Moema, São Paulo - SP");
  assert.equal(result.roadKm, 5.569);
  assert.equal(result.routeProvider, "osrm");
  assert.equal(result.addressProvider, "openai+nominatim");
  assert.equal(calls.length, 3);
});

test("ambiguous establishment never invents a unit or km", async () => {
  let calls = 0;
  const result = await resolveFarolPaidRoad0715({
    destination: "McDonald's",
    context: "Destino\nMcDonald's",
    targets: [{ latitude: -23.55, longitude: -46.63 }],
    apiKey: "test-key",
    fetchImpl: async (url) => {
      calls += 1;
      assert.match(url, /api\.openai\.com/);
      return response(200, {
        output_text: JSON.stringify({
          status: "UNRESOLVED",
          normalizedAddress: "",
          confidence: 0.25,
          reason: "unidade ambígua",
        }),
      });
    },
  });
  assert.equal(result.status, "UNRESOLVED");
  assert.equal(result.roadKm, null);
  assert.equal(calls, 1);
});

test("429 remains one paid attempt and does not fall through to fake distance", async () => {
  let calls = 0;
  await assert.rejects(
    resolveFarolPaidRoad0715({
      destination: "Rua A, 10",
      targets: [{ latitude: -23.55, longitude: -46.63 }],
      apiKey: "test-key",
      fetchImpl: async () => {
        calls += 1;
        return response(429, { error: { message: "rate limited" } });
      },
    }),
    (error) => error instanceof FarolPaidRoadError0715 && error.code === "openai_rate_limited",
  );
  assert.equal(calls, 1);
});
