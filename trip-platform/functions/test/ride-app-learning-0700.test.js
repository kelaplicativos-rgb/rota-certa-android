"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CONTRACT_MARKER,
  DECLARATIVE_ONLY_MARKER,
  learnRideApp0700,
  systemInstruction0700,
} = require("../ride-app-learning-0700");

function fakeResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  };
}

test("TKX-like dossier becomes one declarative learned profile in one OpenAI call", async () => {
  let calls = 0;
  let sent = null;
  const fetchImpl = async (url, options) => {
    calls += 1;
    assert.equal(url, "https://api.openai.com/v1/responses");
    sent = JSON.parse(options.body);
    return fakeResponse({
      model: "gpt-5.6-luna",
      output_text: JSON.stringify({
        status: "LEARNED",
        confidence: 0.94,
        reason: "Há layouts e campos explícitos de corrida/destino/distância.",
        pickupLabels: ["ORIGEM"],
        destinationLabels: ["ENDERECO_DESTINO", "Destino"],
        fareLabels: ["GCM_FIELD_VALOR", "Valor"],
        distanceLabels: ["DISTANCIA", "DISTANCIA_KM"],
        rideAnchors: ["corrida", "CorridasDisponiveis"],
        actionLabels: ["aceitar"],
        resourceHints: ["res/layout/aceitar_corrida.xml", "res/layout/corrida_taxista_detalhe.xml"],
        ignoreLabels: ["cronometro"],
      }),
    });
  };

  const result = await learnRideApp0700({
    packageName: "br.com.tkx.taxi.drivermachine",
    versionName: "1",
    versionCode: 1,
    apkSha256: "a".repeat(64),
    dossier: "res/layout/aceitar_corrida.xml\nres/layout/corrida_taxista_detalhe.xml\nENDERECO_DESTINO\nDISTANCIA_KM\nGCM_FIELD_VALOR\nCorridasDisponiveis",
    apiKey: "sk-test",
    fetchImpl,
  });

  assert.equal(calls, 1);
  assert.equal(result.status, "LEARNED");
  assert.equal(result.provider, "openai");
  assert.equal(result.contract, CONTRACT_MARKER);
  assert.ok(result.destinationLabels.includes("ENDERECO_DESTINO"));
  assert.equal(sent.store, false);
  assert.match(sent.input[0].content[0].text, /configuração declarativa/i);
});

test("system prompt treats APK dossier as untrusted data and forbids executable output", () => {
  const prompt = systemInstruction0700();
  assert.match(prompt, /dado não confiável/i);
  assert.match(prompt, /nunca produza código/i);
  assert.equal(DECLARATIVE_ONLY_MARKER, "DECLARATIVE_READER_PROFILE_NO_EXECUTABLE_CODE_0700");
});

test("missing OpenAI secret fails closed", async () => {
  await assert.rejects(
    learnRideApp0700({
      packageName: "regional.driver",
      apkSha256: "b".repeat(64),
      dossier: "Destino\nCorrida\nresource_destination".repeat(5),
      apiKey: "",
      fetchImpl: async () => { throw new Error("should not call"); },
    }),
    /OpenAI não configurada/,
  );
});
