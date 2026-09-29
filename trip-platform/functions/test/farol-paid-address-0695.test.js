"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  FarolPaidAddressError0695,
  resolveFarolAddress0695,
} = require("../farol-paid-address-0695");

function response(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async text() { return JSON.stringify(payload); },
  };
}

test("paid Farol fallback performs exactly one remote attempt and returns address only", async () => {
  let calls = 0;
  const result = await resolveFarolAddress0695({
    text: "Pedido de viagem\nDestino: Rua Vergueiro, 1000, São Paulo - SP",
    packageName: "sinet.startup.indriver",
    fingerprint: "abc",
    apiKey: "test-key",
    fetchImpl: async (_url, options) => {
      calls += 1;
      const body = JSON.parse(options.body);
      assert.equal(body.store, false);
      assert.equal(body.text.format.strict, true);
      return response(200, {
        model: "gpt-5.6-luna",
        output_text: JSON.stringify({
          status: "RESOLVED",
          address: "Rua Vergueiro, 1000, São Paulo - SP",
          confidence: 0.96,
          reason: "destino explícito",
        }),
      });
    },
  });
  assert.equal(calls, 1);
  assert.equal(result.status, "RESOLVED");
  assert.equal(result.address, "Rua Vergueiro, 1000, São Paulo - SP");
  assert.equal(result.provider, "openai");
  assert.equal(Object.hasOwn(result, "color"), false);
  assert.equal(Object.hasOwn(result, "distanceKm"), false);
});

test("ambiguous result remains unresolved", async () => {
  const result = await resolveFarolAddress0695({
    text: "Rua A, 10\nRua B, 20",
    apiKey: "test-key",
    fetchImpl: async () => response(200, {
      output_text: JSON.stringify({
        status: "UNRESOLVED",
        address: "",
        confidence: 0.2,
        reason: "dois destinos possíveis",
      }),
    }),
  });
  assert.equal(result.status, "UNRESOLVED");
  assert.equal(result.address, "");
});

test("429 does not retry inside paid fallback", async () => {
  let calls = 0;
  await assert.rejects(
    resolveFarolAddress0695({
      text: "Destino: Rua A, 10",
      apiKey: "test-key",
      fetchImpl: async () => {
        calls += 1;
        return response(429, { error: { message: "rate limited" } });
      },
    }),
    (error) => error instanceof FarolPaidAddressError0695 && error.code === "openai_rate_limited",
  );
  assert.equal(calls, 1);
});
