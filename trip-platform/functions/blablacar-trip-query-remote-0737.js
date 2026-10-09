"use strict";

const crypto = require("crypto");
const {
  ACCESS_COLLECTION_0736,
  STATE_COLLECTION_0736,
  JOB_COLLECTION_0736,
  normalizePublicToken0736,
  canonicalUuid0736,
} = require("./standalone-covers-remote-0736");

const QUERY_STATE_COLLECTION_0737 = "tripBlaBlaQueryState0737";
const QUERY_JOB_COLLECTION_0737 = "tripBlaBlaQueryJobs0737";
const QUERY_PENDING_COLLECTION_0760 = "tripBlaBlaQueryPending0760";
const QUERY_JOB_TTL_MILLIS_0737 = 10 * 60 * 1000;
const QUERY_RESULT_TTL_MILLIS_0737 = 24 * 60 * 60 * 1000;
const QUERY_MIN_REFRESH_MILLIS_0737 = 30 * 1000;
const QUERY_MAX_PAYLOAD_BYTES_0737 = 192 * 1024;
const QUERY_ACTIVE_STATES_0737 = new Set(["REQUESTED", "PUSH_SENT", "PENDING_DEVICE", "RUNNING"]);
const QUERY_TERMINAL_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED", "EXPIRED"]);
const QUERY_RESULT_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED"]);

function clean0737(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0737(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function normalizeTripId0737(value) {
  const raw = clean0737(value, 160);
  return /^[A-Za-z0-9_-]{8,160}$/.test(raw) ? raw : "";
}

function normalizeAdministrativeHref0737(value, expectedTripId) {
  const raw = clean0737(value, 800);
  if (!raw || !expectedTripId) return "";
  let parsed;
  try {
    parsed = new URL(raw);
  } catch (_) {
    return "";
  }
  const host = String(parsed.hostname || "").toLowerCase();
  if (parsed.protocol !== "https:" || !(host === "blablacar.com.br" || host.endsWith(".blablacar.com.br"))) return "";
  return raw.includes(expectedTripId) ? raw : "";
}

function assertObject0737(value, message) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(message);
  return value;
}

function assertKeys0737(value, allowed, label) {
  const extra = Object.keys(value).filter((key) => !allowed.has(key));
  if (extra.length) throw new Error(label + " contém campo não permitido: " + extra[0]);
}

function sanitizeTripQueryPayload0737(input, expectedProfileUuid = "", expectedTripId = "") {
  const rawBytes = Buffer.byteLength(JSON.stringify(input == null ? null : input), "utf8");
  if (rawBytes <= 0 || rawBytes > QUERY_MAX_PAYLOAD_BYTES_0737) {
    throw new Error("Snapshot HTML vazio ou acima do limite remoto.");
  }
  const root = assertObject0737(input, "Snapshot HTML inválido.");
  assertKeys0737(root, new Set([
    "schemaVersion", "kind", "capturedAt", "sourceAppVersion", "sourceVersionCode",
    "sourceCommitSha", "sourceBranch", "profileUuid", "tripId", "dateIso",
    "departureTime", "arrivalTime", "origin", "destination", "price", "availability",
    "bookedSeats", "publishedSeats", "passengerRosterComplete", "itineraryAuthoritative",
    "operationalComplete", "passengerCount", "passengerSeatCount", "individualFaresComplete", "passengers",
    "itineraryStops", "itineraryStopTimes",
  ]), "Snapshot HTML");

  if (root.schemaVersion !== "rota-certa-blablacar-trip-query-v1") throw new Error("Schema HTML remoto não suportado.");
  if (root.kind !== "BLABLACAR_TRIP_QUERY") throw new Error("Tipo HTML remoto não suportado.");

  const profileUuid = canonicalUuid0736(root.profileUuid);
  const tripId = normalizeTripId0737(root.tripId);
  if (!profileUuid || !tripId) throw new Error("Identidade forte do snapshot ausente.");
  if (expectedProfileUuid && profileUuid !== canonicalUuid0736(expectedProfileUuid)) throw new Error("UUID do snapshot diverge do job.");
  if (expectedTripId && tripId !== normalizeTripId0737(expectedTripId)) throw new Error("tripId do snapshot diverge do job.");

  const passengersRaw = Array.isArray(root.passengers) ? root.passengers : null;
  if (!passengersRaw || passengersRaw.length > 16) throw new Error("Lista de passageiros inválida.");
  const passengers = passengersRaw.map((raw) => {
    const p = assertObject0737(raw, "Passageiro remoto inválido.");
    assertKeys0737(p, new Set([
      "name", "seats", "boarding", "dropoff",
      "passengerTotalMinorUnits", "driverReceivesMinorUnits", "fareCurrencyCode",
    ]), "Passageiro remoto");
    const seats = Number(p.seats);
    if (!Number.isSafeInteger(seats) || seats < 1 || seats > 8) throw new Error("Quantidade de lugares do passageiro inválida.");
    function monetaryEvidence(raw, field) {
      if (raw == null) return null;
      const n = Number(raw);
      if (!Number.isSafeInteger(n) || n < 0 || n > 10_000_000) {
        throw new Error(field + " inválido (centavos inteiros).");
      }
      return n;
    }
    const passengerTotalMinorUnits = monetaryEvidence(p.passengerTotalMinorUnits, "Valor individual");
    const driverReceivesMinorUnits = monetaryEvidence(p.driverReceivesMinorUnits, "Repasse ao motorista");
    const fareCurrencyCode = clean0737(p.fareCurrencyCode, 3).toUpperCase();
    if ((passengerTotalMinorUnits != null || driverReceivesMinorUnits != null) && fareCurrencyCode !== "BRL") {
      throw new Error("Valor individual sem moeda BRL confirmada.");
    }
    if (fareCurrencyCode && fareCurrencyCode !== "BRL") throw new Error("Moeda não suportada.");
    return {
      name: clean0737(p.name, 160),
      seats,
      boarding: clean0737(p.boarding, 240),
      dropoff: clean0737(p.dropoff, 240),
      passengerTotalMinorUnits,
      driverReceivesMinorUnits,
      fareCurrencyCode,
    };
  });

  const passengerCount = Number(root.passengerCount);
  const passengerSeatCount = Number(root.passengerSeatCount);
  const bookedSeats = Number(root.bookedSeats);
  if (!Number.isSafeInteger(passengerCount) || passengerCount !== passengers.length) throw new Error("Contagem de passageiros inconsistente.");
  if (!Number.isSafeInteger(passengerSeatCount) || passengerSeatCount < 0) throw new Error("Contagem de lugares dos passageiros inválida.");
  if (passengerSeatCount !== passengers.reduce((sum, p) => sum + p.seats, 0)) throw new Error("Lugares dos passageiros inconsistentes.");
  if (!Number.isSafeInteger(bookedSeats) || bookedSeats < 0 || bookedSeats < passengerSeatCount) throw new Error("Ocupação observada inválida.");
  const individualFaresComplete = passengers.every((p) =>
    p.passengerTotalMinorUnits != null && p.fareCurrencyCode === "BRL");
  if (root.individualFaresComplete != null &&
      root.individualFaresComplete !== individualFaresComplete) {
    throw new Error("Completude de valores individuais inconsistente.");
  }

  let publishedSeats = null;
  if (root.publishedSeats != null) {
    publishedSeats = Number(root.publishedSeats);
    if (!Number.isSafeInteger(publishedSeats) || publishedSeats < 0 || publishedSeats > 20) throw new Error("Vagas publicadas inválidas.");
  }

  const itineraryStopsRaw = Array.isArray(root.itineraryStops) ? root.itineraryStops : [];
  const itineraryTimesRaw = Array.isArray(root.itineraryStopTimes) ? root.itineraryStopTimes : [];
  if (itineraryStopsRaw.length > 64 || itineraryTimesRaw.length > 64) throw new Error("Itinerário remoto excede limite.");
  const itineraryStops = itineraryStopsRaw.map((value) => clean0737(value, 240));
  const itineraryStopTimes = itineraryTimesRaw.map((value) => clean0737(value, 40));

  return {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAt: clean0737(root.capturedAt, 80),
    sourceAppVersion: clean0737(root.sourceAppVersion, 40),
    sourceVersionCode: Math.max(0, Math.floor(Number(root.sourceVersionCode || 0))),
    sourceCommitSha: clean0737(root.sourceCommitSha, 80),
    sourceBranch: clean0737(root.sourceBranch, 160),
    profileUuid,
    tripId,
    dateIso: clean0737(root.dateIso, 20),
    departureTime: clean0737(root.departureTime, 40),
    arrivalTime: clean0737(root.arrivalTime, 40),
    origin: clean0737(root.origin, 500),
    destination: clean0737(root.destination, 500),
    price: clean0737(root.price, 120),
    availability: clean0737(root.availability, 80) || "unknown",
    bookedSeats,
    publishedSeats,
    passengerRosterComplete: root.passengerRosterComplete === true,
    itineraryAuthoritative: root.itineraryAuthoritative === true,
    operationalComplete: root.operationalComplete === true,
    passengerCount,
    passengerSeatCount,
    individualFaresComplete,
    passengers,
    itineraryStops,
    itineraryStopTimes,
  };
}

function tripQueryStateKey0737(driverUsername, profileUuid, tripId) {
  return sha256Hex0737(clean0737(driverUsername, 80) + "|" + canonicalUuid0736(profileUuid) + "|" + normalizeTripId0737(tripId));
}

function queryResultTransition0737(currentState, requestedState) {
  const current = clean0737(currentState, 32).toUpperCase();
  const requested = clean0737(requestedState, 32).toUpperCase();
  if (!QUERY_TERMINAL_STATES_0737.has(current)) return { action: "WRITE", state: requested };
  if (current === requested && current !== "EXPIRED") return { action: "IDEMPOTENT", state: current };
  return { action: "REJECT", state: current };
}

function remoteQueryCapablePushToken0737(data, nowMillis = Date.now()) {
  const value = data && typeof data === "object" ? data : {};
  return Number(value.expiresAtMillis || 0) > Number(nowMillis || 0) &&
    clean0737(value.token, 4096).length >= 32 &&
    Number(value.blablacarTripQueryRemoteVersion || 0) >= 1;
}

function pendingConsentTripQuery0760(job, nowMillis = Date.now()) {
  if (!job || typeof job !== "object") return false;
  const state = clean0737(job.state, 32).toUpperCase();
  return (state === "REQUESTED" || state === "PUSH_SENT" || state === "PENDING_DEVICE") &&
    Number(job.expiresAtMillis || 0) > Number(nowMillis || 0);
}


/**
 * A PARTIAL fleet inventory can still contain a fully verified individual
 * profile. Never waive any per-profile or per-card checks: only verified
 * identity, end-of-list, stabilized collection, exact tripId and date may
 * authorize an HTML detail query. A PARTIAL profile remains ineligible.
 */
function verifiedCoverTarget0767(job, profileUuidRaw, tripIdRaw, nowMillis = Date.now()) {
  const profileUuid = canonicalUuid0736(profileUuidRaw);
  const tripId = normalizeTripId0737(tripIdRaw);
  if (!profileUuid || !tripId) return { ok: false, code: "TRIP_IDENTITY_INVALID" };
  const state = clean0737(job && job.state, 20).toUpperCase();
  const result = clean0737(job && job.payload && job.payload.result, 20).toUpperCase();
  if (!["COMPLETE", "PARTIAL"].includes(state) || result !== state) {
    return { ok: false, code: "COVER_INDEX_NOT_COMPLETE" };
  }
  if (Number(job.resultExpiresAtMillis || 0) <= Number(nowMillis)) {
    return { ok: false, code: "COVER_INDEX_STALE" };
  }
  const profiles = Array.isArray(job.payload.profiles) ? job.payload.profiles : [];
  const matching = profiles.filter(profile =>
    canonicalUuid0736(profile && profile.profileUuid) === profileUuid &&
    profile.identityConfirmed === true &&
    clean0737(profile.status, 20).toUpperCase() === "COMPLETE" &&
    profile.reachedEnd === true &&
    profile.stabilized === true
  );
  if (matching.length !== 1) {
    return { ok: false, code: "PROFILE_NOT_COMPLETE_IN_COVER_INDEX" };
  }
  const cards = Array.isArray(matching[0].cards) ? matching[0].cards : [];
  const matches = cards.filter(card => normalizeTripId0737(card && card.tripId) === tripId);
  if (matches.length !== 1) {
    return { ok: false, code: matches.length > 1 ? "COVER_INDEX_DUPLICATE_TRIP" : "TRIP_NOT_IN_COMPLETE_COVER_INDEX" };
  }
  const card = matches[0];
  const dateIso = clean0737(card.dateIso, 20);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateIso)) {
    return { ok: false, code: "COVER_INDEX_DATE_INVALID" };
  }
  const administrativeHref = normalizeAdministrativeHref0737(card.administrativeHref, tripId);
  if (!administrativeHref) return { ok: false, code: "COVER_INDEX_ADMIN_HREF_INVALID" };
  return {
    ok: true,
    profileUuid,
    tripId,
    administrativeHref,
    dateIso,
    departureTime: clean0737(card.departureTime, 40),
    arrivalTime: clean0737(card.arrivalTime, 40),
    origin: clean0737(card.origin, 500),
    destination: clean0737(card.destination, 500),
    price: clean0737(card.price, 120),
  };
}

function createBlaBlaTripQueryRemote0737({ db, requireDriver, getMessaging, normalizeUsername, json, fail }) {
  async function resolveAccess0737(tokenRaw) {
    const token = normalizePublicToken0736(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(ACCESS_COLLECTION_0736).doc(sha256Hex0737(token)).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    if (!username) return null;
    const stateSnap = await db.collection(STATE_COLLECTION_0736).doc(username).get();
    if (!stateSnap.exists) return null;
    const state = stateSnap.data();
    if (state.remoteAutoAccessEnabled0764 === false ||
        clean0737(state.accessTokenHash, 80) !== sha256Hex0737(token)) return null;
    return { token, username };
  }

  async function latestCompleteCoverCard0737(driverUsername, profileUuidRaw, tripIdRaw) {
    const profileUuid = canonicalUuid0736(profileUuidRaw);
    const tripId = normalizeTripId0737(tripIdRaw);
    if (!profileUuid || !tripId) return { ok: false, code: "TRIP_IDENTITY_INVALID" };

    const stateSnap = await db.collection(STATE_COLLECTION_0736).doc(driverUsername).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const lastState = clean0737(state.latestCompletedState, 20).toUpperCase();
    let jobId = ["COMPLETE", "PARTIAL"].includes(lastState)
      ? clean0737(state.latestCompletedJobId, 80)
      : "";
    if (!jobId) jobId = clean0737(state.latestCompleteJobId, 80);
    if (!jobId) return { ok: false, code: "COVER_INDEX_COMPLETE_REQUIRED" };

    const jobSnap = await db.collection(JOB_COLLECTION_0736).doc(jobId).get();
    if (!jobSnap.exists) return { ok: false, code: "COVER_INDEX_JOB_MISSING" };
    const job = jobSnap.data();
    if (normalizeUsername(job.driverUsername) !== driverUsername) {
      return { ok: false, code: "COVER_INDEX_SCOPE_MISMATCH" };
    }
    const target = verifiedCoverTarget0767(job, profileUuid, tripId);
    if (!target.ok) return target;
    return {
      ...target,
      coverJobId: jobId,
      coverCapturedAt: clean0737(job.payload.capturedAt, 80),
    };
  }

  async function sendPush0737(driverUsername, jobId) {
    const snapshot = await db.collection("tripDriverPushTokens").where("driverUsername", "==", driverUsername).limit(20).get();
    const now = Date.now();
    const active = snapshot.docs.filter((doc) => remoteQueryCapablePushToken0737(doc.data(), now));
    const jobRef = db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId);
    if (!active.length) {
      await jobRef.set({ state: "PENDING_DEVICE", errorCode: "NO_TRIP_QUERY_CAPABLE_DEVICE", updatedAtMillis: now }, { merge: true });
      return "PENDING_DEVICE";
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0737(doc.data().token, 4096)),
        data: { event: "blablacar_trip_query_collect", jobId, requestedAtMillis: String(now) },
        android: { priority: "high", ttl: QUERY_JOB_TTL_MILLIS_0737 },
      });
      const state = Number(response.successCount || 0) > 0 ? "PUSH_SENT" : "PENDING_DEVICE";
      await jobRef.set({
        state,
        pushSuccessCount: Number(response.successCount || 0),
        pushFailureCount: Number(response.failureCount || 0),
        errorCode: state === "PENDING_DEVICE" ? "TRIP_QUERY_PUSH_NOT_DELIVERABLE" : "",
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return state;
    } catch (error) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "TRIP_QUERY_PUSH_FAILED",
        errorMessage: clean0737(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return "PENDING_DEVICE";
    }
  }

  async function createJob0737(driverUsername, target) {
    const now = Date.now();
    const stateKey = tripQueryStateKey0737(driverUsername, target.profileUuid, target.tripId);
    const stateRef = db.collection(QUERY_STATE_COLLECTION_0737).doc(stateKey);
    let jobId = "";
    let reused = false;
    let throttled = false;
    let retryAfterMillis = 0;
    let busy = false;

    await db.runTransaction(async (transaction) => {
      const stateSnap = await transaction.get(stateRef);
      const state = stateSnap.exists ? stateSnap.data() : {};
      const latestJobId = clean0737(state.latestJobId, 80);
      const lastRequestedAtMillis = Number(state.lastRequestedAtMillis || 0);
      if (latestJobId) {
        const latestRef = db.collection(QUERY_JOB_COLLECTION_0737).doc(latestJobId);
        const latestSnap = await transaction.get(latestRef);
        if (latestSnap.exists) {
          const latest = latestSnap.data();
          const latestState = clean0737(latest.state, 20).toUpperCase();
          if (QUERY_ACTIVE_STATES_0737.has(latestState) && latestState !== "PENDING_DEVICE" && Number(latest.expiresAtMillis || 0) > now) {
            jobId = latestJobId;
            reused = true;
            return;
          }
        }
      }
      const elapsed = lastRequestedAtMillis > 0 ? Math.max(0, now - lastRequestedAtMillis) : QUERY_MIN_REFRESH_MILLIS_0737;
      if (lastRequestedAtMillis > 0 && elapsed < QUERY_MIN_REFRESH_MILLIS_0737) {
        throttled = true;
        retryAfterMillis = Math.max(1, QUERY_MIN_REFRESH_MILLIS_0737 - elapsed);
        return;
      }

      // One visible pending consultation per driver. Do not overwrite a previous
      // unanswered consent request when a second target is requested.
      const pendingRef = db.collection(QUERY_PENDING_COLLECTION_0760).doc(driverUsername);
      const pendingSnap = await transaction.get(pendingRef);
      const currentId = clean0737(pendingSnap.exists && pendingSnap.data().latestJobId, 80);
      if (currentId) {
        const previousSnap = await transaction.get(db.collection(QUERY_JOB_COLLECTION_0737).doc(currentId));
        if (previousSnap.exists && normalizeUsername(previousSnap.data().driverUsername) === driverUsername &&
            pendingConsentTripQuery0760(previousSnap.data(), now)) {
          busy = true;
          return;
        }
      }

      jobId = crypto.randomUUID();
      transaction.set(pendingRef, {
        driverUsername,
        latestJobId: jobId,
        updatedAtMillis: now,
      }, { merge: true });
      transaction.set(db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId), {
        jobId,
        driverUsername,
        state: "REQUESTED",
        profileUuid: target.profileUuid,
        tripId: target.tripId,
        administrativeHref: target.administrativeHref,
        dateIso: target.dateIso,
        departureTime: target.departureTime,
        arrivalTime: target.arrivalTime,
        origin: target.origin,
        destination: target.destination,
        price: target.price,
        coverJobId: target.coverJobId,
        coverCapturedAt: target.coverCapturedAt,
        requestedAtMillis: now,
        updatedAtMillis: now,
        expiresAtMillis: now + QUERY_JOB_TTL_MILLIS_0737,
        resultExpiresAtMillis: 0,
        payload: null,
        errorCode: "",
        errorMessage: "",
      });
      transaction.set(stateRef, {
        driverUsername,
        profileUuid: target.profileUuid,
        tripId: target.tripId,
        latestJobId: jobId,
        lastRequestedAtMillis: now,
        updatedAtMillis: now,
      }, { merge: true });
    });

    if (throttled) return { state: "THROTTLED", throttled: true, retryAfterMillis };
    if (busy) return { state: "PENDING_CONSENT", busy: true };
    if (!reused) await sendPush0737(driverUsername, jobId);
    const snap = await db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId).get();
    const data = snap.exists ? snap.data() : {};
    return {
      jobId,
      state: clean0737(data.state, 20) || "PENDING_DEVICE",
      reused,
      throttled: false,
      requestedAtMillis: Number(data.requestedAtMillis || now),
      updatedAtMillis: Number(data.updatedAtMillis || now),
    };
  }

  // A driver-authenticated poll is the fallback when FCM registration is absent.
  // Never expose trip details or driver identity to unauthenticated callers.
  async function pollDriverPending0760(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return;
    if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
    res.set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
    const pendingSnap = await db.collection(QUERY_PENDING_COLLECTION_0760).doc(driver.username).get();
    const jobId = clean0737(pendingSnap.exists && pendingSnap.data().latestJobId, 80);
    if (!/^[0-9a-f-]{36}$/i.test(jobId)) return json(res, 200, { pending: false, jobId: "", state: "NONE" });

    const jobSnap = await db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId).get();
    if (!jobSnap.exists) return json(res, 200, { pending: false, jobId: "", state: "MISSING" });
    const job = jobSnap.data();
    if (normalizeUsername(job.driverUsername) !== driver.username) {
      return fail(res, 403, "blablacar_query_scope_mismatch", "Consulta pertence a outro motorista.");
    }
    if (!pendingConsentTripQuery0760(job)) {
      return json(res, 200, { pending: false, jobId: "", state: clean0737(job.state, 32) || "EXPIRED" });
    }
    return json(res, 200, {
      pending: true,
      jobId,
      state: clean0737(job.state, 32),
      requestedAtMillis: Number(job.requestedAtMillis || 0),
      expiresAtMillis: Number(job.expiresAtMillis || 0),
    });
  }

  async function refreshPublic0737(req, res, tokenRaw, profileUuidRaw, tripIdRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "blablacar_query_access_not_found", "Acesso privado à consulta BlaBlaCar não encontrado ou expirado.");
    const target = await latestCompleteCoverCard0737(access.username, profileUuidRaw, tripIdRaw);
    if (!target.ok) {
      return fail(res, 409, "blablacar_query_cover_index_required", "A consulta detalhada exige um inventário de capas COMPLETE e fresco.", { reason: target.code });
    }
    const job = await createJob0737(access.username, target);
    if (job.busy) {
      return fail(res, 409, "blablacar_query_consent_pending", "Conclua a solicitação remota pendente antes de consultar outra viagem.");
    }
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "blablacar_query_refresh_throttled", "Aguarde antes de solicitar novamente esta viagem.", { retryAfterMillis: job.retryAfterMillis });
    }
    return json(res, 202, {
      ...job,
      profileUuid: target.profileUuid,
      tripId: target.tripId,
      coverCapturedAt: target.coverCapturedAt,
      statusMeaning: "Somente COMPLETE comprova roster, itinerário e ocupação operacional desta viagem.",
    });
  }

  async function latestPublic0737(req, res, tokenRaw, profileUuidRaw, tripIdRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "blablacar_query_access_not_found", "Acesso privado à consulta BlaBlaCar não encontrado ou expirado.");
    const profileUuid = canonicalUuid0736(profileUuidRaw);
    const tripId = normalizeTripId0737(tripIdRaw);
    if (!profileUuid || !tripId) return fail(res, 400, "blablacar_query_identity_invalid", "profileUuid/tripId inválidos.");

    const stateKey = tripQueryStateKey0737(access.username, profileUuid, tripId);
    const stateSnap = await db.collection(QUERY_STATE_COLLECTION_0737).doc(stateKey).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const jobId = clean0737(state.latestJobId, 80);
    if (!jobId) {
      return json(res, 200, {
        state: "PENDING_UNKNOWN",
        profileUuid,
        tripId,
        result: null,
        decisionSafe: false,
        statusMeaning: "Nenhuma consulta HTML remota foi concluída para esta viagem.",
      });
    }

    const jobSnap = await db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId).get();
    if (!jobSnap.exists) {
      return json(res, 200, {
        state: "PENDING_UNKNOWN",
        profileUuid,
        tripId,
        result: null,
        decisionSafe: false,
        errorCode: "TRIP_QUERY_JOB_NOT_AVAILABLE",
      });
    }

    const job = jobSnap.data();
    const now = Date.now();
    let stateValue = clean0737(job.state, 20) || "PENDING_UNKNOWN";
    let result = job.payload || null;
    let errorCode = clean0737(job.errorCode, 120);
    let errorMessage = clean0737(job.errorMessage, 240);
    if (QUERY_ACTIVE_STATES_0737.has(stateValue) && Number(job.expiresAtMillis || 0) > 0 && Number(job.expiresAtMillis) <= now) {
      stateValue = "EXPIRED";
      result = null;
      errorCode = "TRIP_QUERY_JOB_EXPIRED";
      await jobSnap.ref.set({ state: stateValue, payload: null, errorCode, updatedAtMillis: now }, { merge: true });
    } else if (
      QUERY_TERMINAL_STATES_0737.has(stateValue) &&
      stateValue !== "EXPIRED" &&
      Number(job.resultExpiresAtMillis || 0) > 0 &&
      Number(job.resultExpiresAtMillis) <= now
    ) {
      stateValue = "EXPIRED";
      result = null;
      errorCode = "TRIP_QUERY_RESULT_EXPIRED";
      await jobSnap.ref.set({ state: stateValue, payload: null, errorCode, errorMessage: "", updatedAtMillis: now }, { merge: true });
    }

    return json(res, 200, {
      state: stateValue,
      jobId,
      profileUuid,
      tripId,
      requestedAtMillis: Number(job.requestedAtMillis || 0),
      completedAtMillis: Number(job.completedAtMillis || 0),
      updatedAtMillis: Number(job.updatedAtMillis || 0),
      coverCapturedAt: clean0737(job.coverCapturedAt, 80),
      result,
      errorCode,
      errorMessage,
      decisionSafe: stateValue === "COMPLETE" && result &&
        result.operationalComplete === true && result.individualFaresComplete === true,
      statusMeaning: stateValue === "COMPLETE"
        ? "Detalhe HTML desta viagem comprovado pelo aparelho."
        : "Detalhe não comprovado como COMPLETE; trate como desconhecido para decisões críticas.",
    });
  }

  async function requireOwnedJob0737(req, res, jobIdRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    if (!driver.username) {
      fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
      return null;
    }
    const jobId = clean0737(jobIdRaw, 80);
    if (!/^[0-9a-f-]{36}$/i.test(jobId)) {
      fail(res, 400, "blablacar_query_job_invalid", "Identidade do job inválida.");
      return null;
    }
    const ref = db.collection(QUERY_JOB_COLLECTION_0737).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "blablacar_query_job_not_found", "Consulta remota não encontrada.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function ackJob0737(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0737(req, res, jobIdRaw);
    if (!owned) return;
    const now = Date.now();
    const current = clean0737(owned.data.state, 20).toUpperCase();
    const targetTripId = normalizeTripId0737(owned.data.tripId);
    const target = {
      profileUuid: canonicalUuid0736(owned.data.profileUuid),
      tripId: targetTripId,
      administrativeHref: normalizeAdministrativeHref0737(owned.data.administrativeHref, targetTripId),
      dateIso: clean0737(owned.data.dateIso, 20),
      departureTime: clean0737(owned.data.departureTime, 40),
      arrivalTime: clean0737(owned.data.arrivalTime, 40),
      origin: clean0737(owned.data.origin, 500),
      destination: clean0737(owned.data.destination, 500),
      price: clean0737(owned.data.price, 120),
    };
    if (!target.profileUuid || !target.tripId || !target.administrativeHref) {
      return fail(res, 409, "blablacar_query_target_corrupt", "Alvo da consulta remota está inconsistente.");
    }
    if (QUERY_TERMINAL_STATES_0737.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current, ...target });
    }
    if (Number(owned.data.expiresAtMillis || 0) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "blablacar_query_job_expired", "Consulta remota expirou antes da execução.");
    }
    await owned.ref.set({
      state: "RUNNING",
      startedAtMillis: Number(owned.data.startedAtMillis || 0) || now,
      updatedAtMillis: now,
      errorCode: "",
      errorMessage: "",
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: "RUNNING", ...target });
  }

  async function submitResult0737(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0737(req, res, jobIdRaw);
    if (!owned) return;
    const requestedStatus = clean0737(req.body && req.body.status, 20).toUpperCase();
    if (!QUERY_RESULT_STATES_0737.has(requestedStatus)) {
      return fail(res, 400, "blablacar_query_result_status_invalid", "Status final da consulta remota inválido.");
    }

    const transition = queryResultTransition0737(owned.data.state, requestedStatus);
    if (transition.action === "IDEMPOTENT") {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: transition.state, idempotent: true });
    }
    if (transition.action === "REJECT") {
      return fail(res, 409, "blablacar_query_terminal_state_conflict", "A consulta remota já terminou e não pode ser sobrescrita.");
    }

    let payload = null;
    if (requestedStatus === "COMPLETE" || requestedStatus === "PARTIAL") {
      try {
        payload = sanitizeTripQueryPayload0737(req.body && req.body.payload, owned.data.profileUuid, owned.data.tripId);
      } catch (error) {
        return fail(res, 400, "blablacar_query_payload_invalid", clean0737(error && error.message, 240) || "Snapshot HTML inválido.");
      }
      if (
        requestedStatus === "COMPLETE" &&
        (
          payload.operationalComplete !== true ||
          payload.passengerRosterComplete !== true ||
          payload.itineraryAuthoritative !== true ||
          payload.individualFaresComplete !== true ||
          payload.publishedSeats == null
        )
      ) {
        return fail(res, 400, "blablacar_query_complete_not_proven", "COMPLETE exige dados operacionais e valores individuais comprovados.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "blablacar_query_failed_payload_forbidden", "FAILED não pode transportar snapshot como prova válida.");
    }

    const now = Date.now();
    await owned.ref.set({
      state: requestedStatus,
      payload,
      errorCode: requestedStatus === "COMPLETE" ? "" : clean0737(req.body && req.body.errorCode, 120),
      errorMessage: requestedStatus === "FAILED" ? clean0737(req.body && req.body.errorMessage, 240) : "",
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + QUERY_RESULT_TTL_MILLIS_0737,
    }, { merge: true });

    const stateKey = tripQueryStateKey0737(owned.driver.username, owned.data.profileUuid, owned.data.tripId);
    const statePatch = {
      latestCompletedJobId: owned.jobId,
      latestCompletedState: requestedStatus,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    };
    if (requestedStatus === "COMPLETE") {
      statePatch.latestCompleteJobId = owned.jobId;
      statePatch.latestCompleteAtMillis = now;
    }
    await db.collection(QUERY_STATE_COLLECTION_0737).doc(stateKey).set(statePatch, { merge: true });

    return json(res, 200, { accepted: true, jobId: owned.jobId, state: requestedStatus });
  }

  return { refreshPublic0737, latestPublic0737, ackJob0737, submitResult0737, pollDriverPending0760 };
}

module.exports = {
  QUERY_STATE_COLLECTION_0737,
  QUERY_JOB_COLLECTION_0737,
  QUERY_PENDING_COLLECTION_0760,
  QUERY_MAX_PAYLOAD_BYTES_0737,
  normalizeTripId0737,
  normalizeAdministrativeHref0737,
  sanitizeTripQueryPayload0737,
  tripQueryStateKey0737,
  queryResultTransition0737,
  remoteQueryCapablePushToken0737,
  pendingConsentTripQuery0760,
  verifiedCoverTarget0767,
  createBlaBlaTripQueryRemote0737,
};
