"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  sanitizeHealthPayload0747,
  MAX_PAYLOAD_BYTES_0747,
} = require("../remote-health-0747");

function payload0747() {
  return {
    schemaVersion: "rota-certa-remote-health-v1",
    kind: "ROTA_CERTA_REMOTE_HEALTH",
    capturedAtMillis: 1_700_000_000_000,
    sourceAppVersion: "0.1.747",
    sourceVersionCode: 6038,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/remote-health-consent-0.1.747",
    state: "YELLOW",
    validation: "INSUFFICIENT_DATA",
    validationSummary: "Cobertura parcial.",
    sourceEventCount: 12,
    droppedEvents: 0,
    incidents: [{
      id: "INC-ABC",
      severity: "WARNING",
      module: "Agenda",
      fingerprint: "abc",
      firstSeenMillis: 1_699_999_000_000,
      lastSeenMillis: 1_700_000_000_000,
      count: 2,
      errorCode: "EXAMPLE_FAILED",
      symptom: "Falha sanitizada.",
      probableRootCause: "Causa provavel sanitizada.",
      confidencePercent: 80,
      suggestedCorrection: "Corrigir contrato.",
      lifecycle: "ACTIVE",
    }],
    events: [{
      atMillis: 1_700_000_000_000,
      stage: "EXAMPLE_FAILED",
      packageName: "br.com.mapeiaia.rotacerta",
      threadName: "main",
      details: "errorClass=Example",
      parentModule: "AGENDA",
      originModule: "AGENDA",
      executorModule: "AGENDA",
      component: "Example",
      operation: "scan",
      severity: "ERROR",
      result: "FAILED",
      errorCode: "EXAMPLE_FAILED",
      reason: "teste",
      durationMs: 10,
    }],
    buffer: {
      eventsInBuffer: 12,
      bufferCapacity: 6000,
      recordCalls: 20,
      recordMedianNs: 100,
      recordP95Ns: 300,
      recordMaxNs: 1000,
    },
  };
}

test("snapshot remoto de saude aceita somente contrato sanitizado e limitado", () => {
  const value = sanitizeHealthPayload0747(payload0747());
  assert.equal(value.schemaVersion, "rota-certa-remote-health-v1");
  assert.equal(value.kind, "ROTA_CERTA_REMOTE_HEALTH");
  assert.equal(value.incidents.length, 1);
  assert.equal(value.events.length, 1);
  assert.equal(value.buffer.bufferCapacity, 6000);
});

test("snapshot remoto de saude rejeita campos arbitrarios", () => {
  const value = payload0747();
  value.events[0].rawHtml = "<html>segredo</html>";
  assert.throws(() => sanitizeHealthPayload0747(value), /campo nao permitido/i);
});

test("snapshot remoto de saude rejeita excesso antes de persistir", () => {
  const value = payload0747();
  value.events[0].details = "x".repeat(MAX_PAYLOAD_BYTES_0747);
  assert.throws(() => sanitizeHealthPayload0747(value), /acima do limite/i);
});

test("rotas de saude remota sao separadas da Agenda e usam a capability privada existente", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /createRemoteHealth0747/);
  assert.match(source, /parts\[2\] === "remote-health"/);
  assert.match(source, /remoteHealth0747\.refreshPublic0747/);
  assert.match(source, /remoteHealth0747\.latestPublic0747/);
  assert.match(source, /remoteHealth0747\.ackJob0747/);
  assert.match(source, /remoteHealth0747\.submitResult0747/);
  assert.match(source, /remoteHealthVersion/);
});
