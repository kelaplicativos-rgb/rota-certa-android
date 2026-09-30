"use strict";

const CONTRACT_VERSION_0702 = 2;
const LEASE_MILLIS_0702 = 90_000;
const COLLECTION_0702 = "rideAppLearningProfiles";

function learningKey0702(sha256Hex, packageName, apkSha256) {
  const pkg = String(packageName || "").trim().toLowerCase();
  const sha = String(apkSha256 || "").trim().toLowerCase();
  return sha256Hex("v" + CONTRACT_VERSION_0702 + "|" + pkg + "|" + sha);
}

function cacheDecision0702(data, nowMillis) {
  const value = data && typeof data === "object" ? data : {};
  if (value.status === "LEARNED" && value.profile && typeof value.profile === "object") {
    return { action: "RETURN_LEARNED", profile: value.profile };
  }
  if (
    value.status === "PROCESSING" &&
    Number(value.leaseUntilMillis || 0) > Number(nowMillis || 0)
  ) {
    return {
      action: "RETURN_PROCESSING",
      retryAfterMillis: Math.max(
        500,
        Math.min(5_000, Number(value.leaseUntilMillis || 0) - Number(nowMillis || 0)),
      ),
    };
  }
  return { action: "ACQUIRE" };
}

function publicProfileResponse0702(profile, cached) {
  const value = profile && typeof profile === "object" ? profile : {};
  return {
    ...value,
    cached: Boolean(cached),
    retryAfterMillis: 0,
    contractVersion: CONTRACT_VERSION_0702,
  };
}

function publicProcessingResponse0702(packageName, retryAfterMillis) {
  return {
    status: "PROCESSING",
    packageName: String(packageName || "").trim().toLowerCase(),
    profileVersion: 1,
    confidence: 0,
    cached: false,
    retryAfterMillis: Math.max(500, Math.min(5_000, Number(retryAfterMillis || 1_500))),
    contractVersion: CONTRACT_VERSION_0702,
    pickupLabels: [],
    destinationLabels: [],
    fareLabels: [],
    distanceLabels: [],
    rideAnchors: [],
    actionLabels: [],
    resourceHints: [],
    ignoreLabels: [],
    reason: "Aprendizado em andamento para este mesmo APK.",
    provider: "openai",
    model: "",
    contract: "RIDE_APP_OPENAI_LEARNING_0700",
  };
}

module.exports = {
  CONTRACT_VERSION_0702,
  LEASE_MILLIS_0702,
  COLLECTION_0702,
  learningKey0702,
  cacheDecision0702,
  publicProfileResponse0702,
  publicProcessingResponse0702,
};
