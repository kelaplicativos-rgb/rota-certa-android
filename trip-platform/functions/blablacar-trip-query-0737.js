"use strict";

const crypto = require("crypto");
const {
  ACCESS_COLLECTION_0736,
  STATE_COLLECTION_0736,
  JOB_COLLECTION_0736,
  normalizePublicToken0736,
  canonicalUuid0736,
} = require("./standalone-covers-remote-0736");

const TRIP_QUERY_STATE_COLLECTION_0737 = "tripBlablaTripQueryState0737";
const TRIP_QUERY_JOB_COLLECTION_0737 = "tripBlablaTripQueryJobs0737";
const JOB_TTL_MILLIS_0737 = 12 * 60 * 1000;
const RESULT_TTL_MILLIS_0737 = 30 * 60 * 1000;
const MIN_REFRESH_INTERVAL_MILLIS_0737 = 20 * 1000;
const MAX_PAYLOAD_BYTES_0737 = 256 * 1024;
const ACTIVE_STATES_0737 = new Set(["REQUESTED", "PUSH_SENT", "RUNNING"]);
const TERMINAL_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED", "EXPIRED"]);
const RESULT_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED"]);

function clean0737(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0737(value) {
  return crypto.createHash("sha256").update(String(value), "utf8").digest("hex");
}

function normalizeTripHref0737(raw, tripId) {
  const value = clean0737(raw, 2048);
  if (!value || !tripId) return "";
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== "https:" || !/blablacar/i.test(parsed.hostname)) return "";
    const joined = parsed.pathname + "?" + parsed.searchParams.toString();
    if (!joined.includes(tripId)) return "";
    return parsed.toString();
  } catch (_) {
    return "";
  }
}

function queryStateId0737(driverUsername, profileUuid, tripId) {
  return sha256Hex0737([clean0737(driverUsername, 80), profileUuid, tripId].join("|"));
}

function containsForbiddenPrivateField0737(value) {
  if (Array.isArray(value)) return value.some(containsForbiddenPrivateField0737);
  if (!value || typeof value !== "object") return false;
  return Object.entries(value).some(([key, child]) =>
    /phone|booking.?href|cookie|password|authorization|access.?token|refresh.?token|credential|html|evidence.?path|administrative.?href/i.test(key) ||
    containsForbiddenPrivateField0737(child)
  );
}

function sanitizeRemoteTripQueryPayload0737(raw) {
  const root = raw && typeof raw === "object" && !Array.isArray(raw) ? raw : null;
  if (!root) throw new Error("Payload direcionado ausente.");
  const bytes = Buffer.byteLength(JSON.stringify(root), "utf8");
  if (bytes <= 0 || bytes > MAX_PAYLOAD_BYTES_0737) {
    throw new Error("Payload direcionado excede o limite remoto.");
  }
  if (containsForbiddenPrivateField0737(root)) {
    throw new Error("Payload direcionado contém campo privado proibido.");
  }
  if (root.schemaVersion !== "rota-certa-blablacar-trip-query-v1" || root.kind !== "BLABLACAR_TRIP_QUERY") {
    throw new Error("Schema direcionado inválido.");
  }
  const profileUuid = canonicalUuid0736(root.profileUuid);
  const tripId = canonicalUuid0736(root.tripId);
  if (!profileUuid || !tripId) throw new Error("Identidade forte direcionada inválida.");

  const result = clean0737(root.result, 20).toUpperCase();
  if (result !== "COMPLETE" && result !== "PARTIAL") throw new Error("Resultado direcionado inválido.");

  const passengers = Array.isArray(root.passengers) ? root.passengers.map((item) => {
    const p = item && typeof item === "object" ? item : {};
    return {
      name: clean0737(p.name, 180),
      seats: Math.max(1, Math.min(8, Math.floor(Number(p.seats || 1)))),
      boarding: clean0737(p.boarding, 240),
      dropoff: clean0737(p.dropoff, 240),
    };
  }).slice(0, 16) : [];

  const publishedSeats = root.publishedSeats == null
    ? null
    : Math.max(0, Math.min(16, Math.floor(Number(root.publishedSeats || 0))));
  const operationalComplete = root.operationalComplete === true;
  const passengerRosterComplete = root.passengerRosterComplete === true;
  const itineraryAuthoritative = root.itineraryAuthoritative === true;

  if (result === "COMPLETE" && (!operationalComplete || !passengerRosterComplete || !itineraryAuthoritative || publishedSeats == null)) {
    throw new Error("COMPLETE direcionado sem provas operacionais completas.");
  }
  if (result === "PARTIAL" && operationalComplete) {
    throw new Error("PARTIAL direcionado não pode declarar completude operacional.");
  }

  return {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAt: clean0737(root.capturedAt, 80),
    sourceAppVersion: clean0737(root.sourceAppVersion, 40),
    sourceVersionCode: Math.max(0, Math.floor(Number(root.sourceVersionCode || 0))),
    sourceCommitSha: clean0737(root.sourceCommitSha, 80),
    profileUuid,
    tripId,
    result,
    operationalComplete,
    passengerRosterComplete,
    itineraryAuthoritative,
    date: clean0737(root.date, 40),
    departureTime: clean0737(root.departureTime, 20),
    arrivalTime: clean0737(root.arrivalTime, 20),
    origin: clean0737(root.origin, 240),
    destination: clean0737(root.destination, 240),
    price: clean0737(root.price, 80),
    availability: clean0737(root.availability, 80) || "unknown",
    bookedSeats: Math.max(0, Math.min(16, Math.floor(Number(root.bookedSeats || 0)))),
    publishedSeats,
    itineraryStops: Array.isArray(root.itineraryStops)
      ? root.itineraryStops.map((value) => clean0737(value, 240)).filter(Boolean).slice(0, 32)
      : [],
    itineraryStopTimes: Array.isArray(root.itineraryStopTimes)
      ? root.itineraryStopTimes.map((value) => clean0737(value, 20)).filter(Boolean).slice(0, 32)
      : [],
    passengers,
    evidencePresent: root.evidencePresent === true,
    errorCode: clean0737(root.errorCode, 120),
  };
}

function remoteTripQueryTransition0737(currentState, requestedState) {
  const current = clean0737(currentState, 32).toUpperCase();
  const requested = clean0737(requestedState, 32).toUpperCase();
  if (!TERMINAL_STATES_0737.has(current)) return { action: "WRITE", state: requested };
  if (current === requested && current !== "EXPIRED") return { action: "IDEMPOTENT", state: current };
  return { action: "REJECT", state: current };
}

function remoteTripQueryRefreshDecision0737({
  latestState,
  latestExpiresAtMillis,
  lastRequestedAtMillis,
  nowMillis = Date.now(),
}) {
  const now = Number(nowMillis || 0);
  const state = clean0737(latestState, 32).toUpperCase();
  if (ACTIVE_STATES_0737.has(state) && Number(latestExpiresAtMillis || 0) > now) {
    return { action: "REUSE_ACTIVE", retryAfterMillis: 0 };
  }
  const requestedAt = Number(lastRequestedAtMillis || 0);
  const elapsed = requestedAt > 0 ? Math.max(0, now - requestedAt) : MIN_REFRESH_INTERVAL_MILLIS_0737;
  if (requestedAt > 0 && elapsed < MIN_REFRESH_INTERVAL_MILLIS_0737) {
    return { action: "THROTTLE", retryAfterMillis: Math.max(1, MIN_REFRESH_INTERVAL_MILLIS_0737 - elapsed) };
  }
  return { action: "CREATE", retryAfterMillis: 0 };
}

function remoteTripQueryCapablePushToken0737(data, nowMillis = Date.now()) {
  const value = data && typeof data === "object" ? data : {};
  return Number(value.expiresAtMillis || 0) > Number(nowMillis || 0) &&
    clean0737(value.token, 4096).length >= 32 &&
    Number(value.blablaTripQueryRemoteVersion || 0) >= 1;
}

function createBlaBlaRemoteTripQuery0737({
  db,
  requireDriver,
  getMessaging,
  normalizeUsername,
  json,
  fail,
}) {
  async function resolveAccess0737(tokenRaw) {
    const token = normalizePublicToken0736(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(ACCESS_COLLECTION_0736).doc(sha256Hex0737(token)).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    return username ? { token, username } : null;
  }

  async function coverTarget0737(driverUsername, profileUuid, tripId) {
    const stateSnap = await db.collection(STATE_COLLECTION_0736).doc(driverUsername).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const jobId = canonicalUuid0736(state.latestCompletedJobId);
    if (!jobId) return { errorCode: "COVER_SNAPSHOT_REQUIRED" };

    const jobSnap = await db.collection(JOB_COLLECTION_0736).doc(jobId).get();
    if (!jobSnap.exists) return { errorCode: "COVER_SNAPSHOT_REQUIRED" };
    const job = jobSnap.data();
    if (
      clean0737(job.state, 32).toUpperCase() !== "COMPLETE" ||
      Number(job.resultExpiresAtMillis || 0) <= Date.now() ||
      !job.payload ||
      clean0737(job.payload.result, 20).toUpperCase() !== "COMPLETE"
    ) {
      return { errorCode: "COVER_SNAPSHOT_NOT_FRESH_COMPLETE" };
    }

    const matches = [];
    const profiles = Array.isArray(job.payload.profiles) ? job.payload.profiles : [];
    profiles.forEach((profile) => {
      if (
        canonicalUuid0736(profile && profile.profileUuid) !== profileUuid ||
        profile.identityConfirmed !== true ||
        clean0737(profile.status, 20).toUpperCase() !== "COMPLETE"
      ) return;
      const cards = Array.isArray(profile.cards) ? profile.cards : [];
      cards.forEach((card) => {
        if (canonicalUuid0736(card && card.tripId) === tripId) {
          matches.push(card);
        }
      });
    });
    if (matches.length !== 1) {
      return { errorCode: matches.length ? "COVER_TARGET_AMBIGUOUS" : "COVER_TARGET_NOT_FOUND" };
    }
    const tripHref = normalizeTripHref0737(matches[0].administrativeHref, tripId);
    if (!tripHref) return { errorCode: "COVER_TARGET_HREF_INVALID" };
    return { tripHref, coverJobId: jobId, coverCapturedAt: clean0737(job.payload.capturedAt, 80) };
  }

  async function sendPush0737(driverUsername, jobId, profileUuid, tripId, tripHref) {
    const snapshot = await db.collection("tripDriverPushTokens")
      .where("driverUsername", "==", driverUsername)
      .limit(20)
      .get();
    const now = Date.now();
    const active = snapshot.docs.filter((doc) => remoteTripQueryCapablePushToken0737(doc.data(), now));
    const jobRef = db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(jobId);
    if (!active.length) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "NO_TRIP_QUERY_CAPABLE_DEVICE",
        updatedAtMillis: now,
      }, { merge: true });
      return "PENDING_DEVICE";
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0737(doc.data().token, 4096)),
        data: {
          event: "blablacar_trip_query",
          jobId,
          profileUuid,
          tripId,
          tripHref,
          requestedAtMillis: String(now),
        },
        android: { priority: "high", ttl: JOB_TTL_MILLIS_0737 },
      });
      const deletes = [];
      response.responses.forEach((item, index) => {
        if (item.success) return;
        const code = item.error && item.error.code || "";
        if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
          deletes.push(active[index].ref.delete());
        }
      });
      if (deletes.length) await Promise.allSettled(deletes);
      const state = Number(response.successCount || 0) > 0 ? "PUSH_SENT" : "PENDING_DEVICE";
      await jobRef.set({
        state,
        pushSuccessCount: Number(response.successCount || 0),
        pushFailureCount: Number(response.failureCount || 0),
        errorCode: state === "PENDING_DEVICE" ? "DEVICE_PUSH_NOT_DELIVERABLE" : "",
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return state;
    } catch (error) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "DEVICE_PUSH_FAILED",
        errorMessage: clean0737(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return "PENDING_DEVICE";
    }
  }

  async function createJob0737(driverUsername, profileUuid, tripId, target) {
    const now = Date.now();
    const stateId = queryStateId0737(driverUsername, profileUuid, tripId);
    const stateRef = db.collection(TRIP_QUERY_STATE_COLLECTION_0737).doc(stateId);
    let jobId = "";
    let reused = false;
    let throttled = false;
    let retryAfterMillis = 0;

    await db.runTransaction(async (transaction) => {
      const stateSnap = await transaction.get(stateRef);
      const state = stateSnap.exists ? stateSnap.data() : {};
      const latestJobId = canonicalUuid0736(state.latestJobId);
      if (latestJobId) {
        const latestRef = db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(latestJobId);
        const latestSnap = await transaction.get(latestRef);
        if (latestSnap.exists) {
          const latest = latestSnap.data();
          const decision = remoteTripQueryRefreshDecision0737({
            latestState: latest.state,
            latestExpiresAtMillis: latest.expiresAtMillis,
            lastRequestedAtMillis: state.lastRequestedAtMillis,
            nowMillis: now,
          });
          if (decision.action === "REUSE_ACTIVE") {
            jobId = latestJobId;
            reused = true;
            return;
          }
          if (decision.action === "THROTTLE") {
            throttled = true;
            retryAfterMillis = decision.retryAfterMillis;
            return;
          }
        }
      }

      jobId = crypto.randomUUID();
      transaction.set(db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(jobId), {
        jobId,
        driverUsername,
        profileUuid,
        tripId,
        tripHref: target.tripHref,
        coverJobId: target.coverJobId,
        coverCapturedAt: target.coverCapturedAt,
        state: "REQUESTED",
        requestedAtMillis: now,
        updatedAtMillis: now,
        expiresAtMillis: now + JOB_TTL_MILLIS_0737,
        resultExpiresAtMillis: 0,
        payload: null,
        errorCode: "",
        errorMessage: "",
      });
      transaction.set(stateRef, {
        driverUsername,
        profileUuid,
        tripId,
        latestJobId: jobId,
        lastRequestedAtMillis: now,
        updatedAtMillis: now,
      }, { merge: true });
    });

    if (throttled) return { state: "THROTTLED", retryAfterMillis, throttled: true };
    if (!reused) await sendPush0737(driverUsername, jobId, profileUuid, tripId, target.tripHref);
    const snap = await db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(jobId).get();
    const data = snap.exists ? snap.data() : {};
    return {
      jobId,
      profileUuid,
      tripId,
      state: clean0737(data.state, 32) || "PENDING_DEVICE",
      reused,
      requestedAtMillis: Number(data.requestedAtMillis || now),
      updatedAtMillis: Number(data.updatedAtMillis || now),
    };
  }

  async function refreshPublic0737(req, res, tokenRaw, profileRaw, tripRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "trip_query_access_not_found", "Acesso privado expirado ou inválido.");
    const profileUuid = canonicalUuid0736(profileRaw);
    const tripId = canonicalUuid0736(tripRaw);
    if (!profileUuid || !tripId) return fail(res, 400, "trip_query_identity_invalid", "UUID/tripId inválidos.");

    const target = await coverTarget0737(access.username, profileUuid, tripId);
    if (!target.tripHref) {
      return fail(
        res,
        409,
        target.errorCode || "COVER_SNAPSHOT_REQUIRED",
        "A consulta profunda exige primeiro um inventário de capas COMPLETE e fresco.",
        { coverRefreshPath: "/v1/public/standalone-covers/" + normalizePublicToken0736(tokenRaw) + "/refresh" },
      );
    }

    const job = await createJob0737(access.username, profileUuid, tripId, target);
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "trip_query_refresh_throttled", "Aguarde antes de repetir a consulta.", {
        retryAfterMillis: job.retryAfterMillis,
      });
    }
    return json(res, 202, {
      ...job,
      statusMeaning: "Somente COMPLETE comprova passageiros, vagas e itinerário da viagem.",
    });
  }

  async function latestPublic0737(req, res, tokenRaw, profileRaw, tripRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "trip_query_access_not_found", "Acesso privado expirado ou inválido.");
    const profileUuid = canonicalUuid0736(profileRaw);
    const tripId = canonicalUuid0736(tripRaw);
    if (!profileUuid || !tripId) return fail(res, 400, "trip_query_identity_invalid", "UUID/tripId inválidos.");

    const stateId = queryStateId0737(access.username, profileUuid, tripId);
    const stateSnap = await db.collection(TRIP_QUERY_STATE_COLLECTION_0737).doc(stateId).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const jobId = canonicalUuid0736(state.latestJobId);
    const latestCompleteJobId = canonicalUuid0736(state.latestCompleteJobId);

    async function readJob0737(id, allowExpired = false) {
      if (!id) return null;
      const snap = await db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(id).get();
      if (!snap.exists) return null;
      const data = snap.data();
      if (normalizeUsername(data.driverUsername) !== access.username) return null;
      if (canonicalUuid0736(data.profileUuid) !== profileUuid || canonicalUuid0736(data.tripId) !== tripId) return null;
      const now = Date.now();
      let stateValue = clean0737(data.state, 32) || "PENDING_UNKNOWN";
      let result = data.payload || null;
      let errorCode = clean0737(data.errorCode, 120);
      if (
        TERMINAL_STATES_0737.has(stateValue) &&
        stateValue !== "EXPIRED" &&
        Number(data.resultExpiresAtMillis || 0) > 0 &&
        Number(data.resultExpiresAtMillis) <= now
      ) {
        if (!allowExpired) {
          stateValue = "EXPIRED";
          result = null;
          errorCode = "RESULT_EXPIRED";
        }
      } else if (
        ACTIVE_STATES_0737.has(stateValue) &&
        Number(data.expiresAtMillis || 0) > 0 &&
        Number(data.expiresAtMillis) <= now
      ) {
        stateValue = "EXPIRED";
        result = null;
        errorCode = "JOB_EXPIRED";
      }
      return {
        state: stateValue,
        jobId: id,
        requestedAtMillis: Number(data.requestedAtMillis || 0),
        completedAtMillis: Number(data.completedAtMillis || 0),
        result,
        errorCode,
        errorMessage: clean0737(data.errorMessage, 240),
      };
    }

    const current = await readJob0737(jobId);
    const lastComplete = latestCompleteJobId && latestCompleteJobId !== jobId
      ? await readJob0737(latestCompleteJobId)
      : null;
    return json(res, 200, {
      profileUuid,
      tripId,
      state: current ? current.state : "PENDING_UNKNOWN",
      jobId: current ? current.jobId : "",
      requestedAtMillis: current ? current.requestedAtMillis : 0,
      completedAtMillis: current ? current.completedAtMillis : 0,
      result: current ? current.result : null,
      errorCode: current ? current.errorCode : "",
      errorMessage: current ? current.errorMessage : "",
      lastComplete: lastComplete && lastComplete.state === "COMPLETE" ? lastComplete : null,
      statusMeaning: current && current.state === "COMPLETE"
        ? "Consulta profunda comprovada pelo aparelho."
        : "Consulta atual não está comprovada como COMPLETE; trate passageiros/vagas como desconhecidos.",
    });
  }

  async function requireOwnedJob0737(req, res, jobRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    if (!driver.username) {
      fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
      return null;
    }
    const jobId = canonicalUuid0736(jobRaw);
    if (!jobId) {
      fail(res, 400, "trip_query_job_invalid", "Job direcionado inválido.");
      return null;
    }
    const ref = db.collection(TRIP_QUERY_JOB_COLLECTION_0737).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "trip_query_job_not_found", "Job direcionado não encontrado.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function ackJob0737(req, res, jobRaw) {
    const owned = await requireOwnedJob0737(req, res, jobRaw);
    if (!owned) return;
    const current = clean0737(owned.data.state, 32).toUpperCase();
    if (TERMINAL_STATES_0737.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current });
    }
    const now = Date.now();
    if (Number(owned.data.expiresAtMillis || 0) > 0 && Number(owned.data.expiresAtMillis) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "trip_query_job_expired", "A consulta direcionada expirou.");
    }
    await owned.ref.set({
      state: "RUNNING",
      deviceAppVersion: clean0737(req.body && req.body.appVersion, 40),
      deviceSourceCommitSha: clean0737(req.body && req.body.sourceCommitSha, 80),
      startedAtMillis: Number(owned.data.startedAtMillis || 0) || now,
      updatedAtMillis: now,
      errorCode: "",
      errorMessage: "",
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: "RUNNING" });
  }

  async function submitResult0737(req, res, jobRaw) {
    const owned = await requireOwnedJob0737(req, res, jobRaw);
    if (!owned) return;
    const requestedStatus = clean0737(req.body && req.body.status, 20).toUpperCase();
    if (!RESULT_STATES_0737.has(requestedStatus)) {
      return fail(res, 400, "trip_query_result_status_invalid", "Status final inválido.");
    }
    const transition = remoteTripQueryTransition0737(owned.data.state, requestedStatus);
    if (transition.action === "IDEMPOTENT") {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: transition.state, idempotent: true });
    }
    if (transition.action === "REJECT") {
      return fail(res, 409, "trip_query_terminal_conflict", "Job direcionado já encerrado.", {
        currentState: transition.state,
      });
    }

    let payload = null;
    if (requestedStatus === "COMPLETE" || requestedStatus === "PARTIAL") {
      try {
        payload = sanitizeRemoteTripQueryPayload0737(req.body && req.body.payload);
      } catch (error) {
        return fail(res, 400, "trip_query_payload_invalid", clean0737(error && error.message, 240) || "Payload inválido.");
      }
      if (
        payload.result !== requestedStatus ||
        payload.profileUuid !== canonicalUuid0736(owned.data.profileUuid) ||
        payload.tripId !== canonicalUuid0736(owned.data.tripId)
      ) {
        return fail(res, 409, "trip_query_identity_result_mismatch", "Resultado diverge da identidade forte do job.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "trip_query_failed_payload_forbidden", "FAILED não pode carregar resultado como prova.");
    }

    const now = Date.now();
    await owned.ref.set({
      state: requestedStatus,
      payload,
      errorCode: requestedStatus === "FAILED"
        ? clean0737(req.body && req.body.errorCode, 120) || "REMOTE_TRIP_QUERY_FAILED"
        : clean0737(payload && payload.errorCode, 120),
      errorMessage: requestedStatus === "FAILED" ? clean0737(req.body && req.body.errorMessage, 240) : "",
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + RESULT_TTL_MILLIS_0737,
    }, { merge: true });

    const stateRef = db.collection(TRIP_QUERY_STATE_COLLECTION_0737).doc(
      queryStateId0737(owned.driver.username, canonicalUuid0736(owned.data.profileUuid), canonicalUuid0736(owned.data.tripId)),
    );
    const update = {
      latestCompletedJobId: owned.jobId,
      latestCompletedState: requestedStatus,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    };
    if (requestedStatus === "COMPLETE") {
      update.latestCompleteJobId = owned.jobId;
      update.latestCompleteAtMillis = now;
    }
    await stateRef.set(update, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: requestedStatus });
  }

  return {
    refreshPublic0737,
    latestPublic0737,
    ackJob0737,
    submitResult0737,
  };
}

module.exports = {
  TRIP_QUERY_STATE_COLLECTION_0737,
  TRIP_QUERY_JOB_COLLECTION_0737,
  MAX_PAYLOAD_BYTES_0737,
  sanitizeRemoteTripQueryPayload0737,
  remoteTripQueryTransition0737,
  remoteTripQueryRefreshDecision0737,
  remoteTripQueryCapablePushToken0737,
  createBlaBlaRemoteTripQuery0737,
};
