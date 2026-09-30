"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CONTRACT_VERSION_0702,
  LEASE_MILLIS_0702,
  learningKey0702,
  cacheDecision0702,
  publicProfileResponse0702,
  publicProcessingResponse0702,
} = require("../ride-app-learning-idempotency-0702");

test("learned profile is returned from cache without reacquiring OpenAI lease", () => {
  const profile = { status: "LEARNED", packageName: "regional.driver", confidence: 0.91, destinationLabels: ["Destino"] };
  const decision = cacheDecision0702({ status: "LEARNED", profile }, 1_000);
  assert.equal(decision.action, "RETURN_LEARNED");
  assert.equal(decision.profile, profile);
});

test("active processing lease blocks duplicate paid analysis", () => {
  const now = 10_000;
  const decision = cacheDecision0702({
    status: "PROCESSING",
    leaseUntilMillis: now + LEASE_MILLIS_0702,
  }, now);
  assert.equal(decision.action, "RETURN_PROCESSING");
  assert.ok(decision.retryAfterMillis >= 500);
  assert.ok(decision.retryAfterMillis <= 5_000);
});

test("expired processing lease can be safely reacquired", () => {
  const decision = cacheDecision0702({
    status: "PROCESSING",
    leaseUntilMillis: 9_000,
  }, 10_000);
  assert.equal(decision.action, "ACQUIRE");
});

test("learning key includes package sha and contract version", () => {
  const fakeHash = (value) => "hash:" + value;
  const key = learningKey0702(fakeHash, "Regional.Driver", "ABCDEF");
  assert.equal(key, "hash:v" + CONTRACT_VERSION_0702 + "|regional.driver|abcdef");
});

test("public responses expose cache and processing state declaratively", () => {
  const learned = publicProfileResponse0702({
    status: "LEARNED",
    packageName: "regional.driver",
    confidence: 0.9,
  }, true);
  assert.equal(learned.cached, true);
  assert.equal(learned.contractVersion, CONTRACT_VERSION_0702);

  const processing = publicProcessingResponse0702("Regional.Driver", 1_500);
  assert.equal(processing.status, "PROCESSING");
  assert.equal(processing.packageName, "regional.driver");
  assert.equal(processing.cached, false);
  assert.equal(processing.retryAfterMillis, 1_500);
});
