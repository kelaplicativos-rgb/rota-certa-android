"use strict";

const crypto = require("node:crypto");

const ACCESS_COLLECTION_0737 = "tripBlaBlaHtmlAccess0737";
const STATE_COLLECTION_0737 = "tripBlaBlaHtmlState0737";
const JOB_COLLECTION_0737 = "tripBlaBlaHtmlJobs0737";
const ACCESS_TTL_MILLIS_0737 = 60 * 24 * 60 * 60 * 1000;
const JOB_TTL_MILLIS_0737 = 12 * 60 * 1000;
const RESULT_TTL_MILLIS_0737 = 30 * 60 * 1000;
const MIN_REFRESH_INTERVAL_MILLIS_0737 = 15 * 1000;
const MAX_PAYLOAD_BYTES_0737 = 256 * 1024;
const ACTIVE_STATES_0737 = new Set(["REQUESTED", "PUSH_SENT", "PENDING_DEVICE", "RUNNING"]);
const TERMINAL_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED", "EXPIRED"]);
const RESULT_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED"]);

function clean0737(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0737(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function canonicalUuid0737(value) {
  const raw = clean0737(value, 80).toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(raw)
    ? raw
    : "";
}

function opaqueToken0737() {
  return crypto.randomBytes(32).toString("base64url");
}

function normalizeToken0737(value) {
  const token = clean0737(value, 120);
  return /^[A-Za-z0-9_-]{40,100}$/.test(token) ? token : "";
}

function assertOnlyKeys0737(value, allowed, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(label + " inválido.");
  }
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) throw new Error(label + " contém campo não permitido: " + key);
  }
}

function sanitizePassenger0737(raw) {
  const allowed = new Set(["name", "seats", "boarding", "dropoff"]);
  assertOnlyKeys0737(raw, allowed, "Passageiro");
  const seats = Math.max(1, Math.min(8, Math.floor(Number(raw.seats || 1))));
  return {
    name: clean0737(raw.name, 120),
    seats,
    boarding: clean0737(raw.boarding, 180),
    dropoff: clean0737(raw.dropoff, 180),
  };
}

function sanitizeSnapshot0737(raw, expectedProfileUuid, expectedTripId) {
  const allowed = new Set([
    "profileUuid", "profileName", "tripId", "date", "departureTime", "arrivalTime",
    "origin", "destination", "price", "availability", "passengers", "bookedSeats",
    "publishedSeats", "passengerRosterComplete", "itineraryStops", "itineraryStopTimes",
    "itineraryAuthoritative", "publicTripUrl", "identityConflict",
  ]);
  assertOnlyKeys0737(raw, allowed, "Snapshot");
  const profileUuid = canonicalUuid0737(raw.profileUuid);
  const tripId = canonicalUuid0737(raw.tripId);
  if (profileUuid !== expectedProfileUuid || tripId !== expectedTripId) {
    throw new Error("Identidade forte do snapshot diverge da solicitação.");
  }
  const passengersRaw = Array.isArray(raw.passengers) ? raw.passengers : [];
  if (passengersRaw.length > 12) throw new Error("Quantidade de passageiros remotos fora do limite.");
  const stops = Array.isArray(raw.itineraryStops) ? raw.itineraryStops.slice(0, 32).map((v) => clean0737(v, 180)) : [];
  const times = Array.isArray(raw.itineraryStopTimes) ? raw.itineraryStopTimes.slice(0, 32).map((v) => clean0737(v, 16)) : [];
  const publishedSeats = raw.publishedSeats == null
    ? null
    : Math.max(0, Math.min(20, Math.floor(Number(raw.publishedSeats))));
  return {
    profileUuid,
    profileName: clean0737(raw.profileName, 120),
    tripId,
    date: clean0737(raw.date, 20),
    departureTime: clean0737(raw.departureTime, 16),
    arrivalTime: clean0737(raw.arrivalTime, 16),
    origin: clean0737(raw.origin, 240),
    destination: clean0737(raw.destination, 240),
    price: clean0737(raw.price, 80),
    availability: clean0737(raw.availability, 80) || "unknown",
    passengers: passengersRaw.map(sanitizePassenger0737),
    bookedSeats: Math.max(0, Math.min(20, Math.floor(Number(raw.bookedSeats || 0)))),
    publishedSeats,
    passengerRosterComplete: raw.passengerRosterComplete === true,
    itineraryStops: stops,
    itineraryStopTimes: times,
    itineraryAuthoritative: raw.itineraryAuthoritative === true,
    publicTripUrl: clean0737(raw.publicTripUrl, 500),
    identityConflict: raw.identityConflict === true,
  };
}

function sanitizePrivacy0737(raw) {
  const allowed = new Set([
    "rawHtmlUploaded", "cookiesUploaded", "blablaCredentialsUploaded",
    "passengerPhoneUploaded", "passengerBookingHrefUploaded",
    "writesAgenda", "writesTimeline", "writesCollectorCanonicalState",
  ]);
  assertOnlyKeys0737(raw, allowed, "Privacidade");
  const normalized = {
    rawHtmlUploaded: raw.rawHtmlUploaded === true,
    cookiesUploaded: raw.cookiesUploaded === true,
    blablaCredentialsUploaded: raw.blablaCredentialsUploaded === true,
    passengerPhoneUploaded: raw.passengerPhoneUploaded === true,
    passengerBookingHrefUploaded: raw.passengerBookingHrefUploaded === true,
    writesAgenda: raw.writesAgenda === true,
    writesTimeline: raw.writesTimeline === true,
    writesCollectorCanonicalState: raw.writesCollectorCanonicalState === true,
  };
  if (Object.values(normalized).some(Boolean)) {
    throw new Error("Contrato de privacidade/isolamento violado.");
  }
  return normalized;
}

function sanitizePayload0737(raw) {
  const root = raw && typeof raw === "object" && !Array.isArray(raw) ? raw : null;
  if (!root) throw new Error("Payload HTML remoto ausente.");
  const allowed = new Set([
    "schemaVersion", "kind", "capturedAt", "sourceAppVersion", "sourceVersionCode",
    "sourceCommitSha", "sourceBranch", "profileUuid", "tripId", "result",
    "operationalComplete", "errorCode", "snapshot", "privacy",
  ]);
  assertOnlyKeys0737(root, allowed, "Payload HTML remoto");
  if (root.schemaVersion !== "rota-certa-blablacar-html-trip-v1") throw new Error("Schema HTML remoto inválido.");
  if (root.kind !== "BLABLACAR_HTML_TRIP_QUERY") throw new Error("Tipo HTML remoto inválido.");
  const profileUuid = canonicalUuid0737(root.profileUuid);
  const tripId = canonicalUuid0737(root.tripId);
  if (!profileUuid || !tripId) throw new Error("UUID/tripId HTML remoto inválidos.");
  const result = clean0737(root.result, 20).toUpperCase();
  if (!new Set(["COMPLETE", "PARTIAL"]).has(result)) throw new Error("Resultado HTML remoto inválido.");
  const privacy = sanitizePrivacy0737(root.privacy || {});
  const snapshot = sanitizeSnapshot0737(root.snapshot, profileUuid, tripId);
  const operationalComplete = root.operationalComplete === true;
  if (result === "COMPLETE") {
    if (!operationalComplete) throw new Error("COMPLETE exige operationalComplete.");
    if (!snapshot.passengerRosterComplete) throw new Error("COMPLETE exige roster de passageiros comprovado.");
    if (!snapshot.itineraryAuthoritative) throw new Error("COMPLETE exige itinerário autoritativo.");
    if (snapshot.publishedSeats == null) throw new Error("COMPLETE exige vagas publicadas observadas.");
    if (snapshot.identityConflict) throw new Error("COMPLETE não aceita conflito de identidade.");
  }
  if (result === "PARTIAL" && operationalComplete) {
    throw new Error("PARTIAL não pode declarar operação completa.");
  }
  const value = {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAt: clean0737(root.capturedAt, 80),
    sourceAppVersion: clean0737(root.sourceAppVersion, 40),
    sourceVersionCode: Math.max(0, Math.floor(Number(root.sourceVersionCode || 0))),
    sourceCommitSha: clean0737(root.sourceCommitSha, 80),
    sourceBranch: clean0737(root.sourceBranch, 160),
    profileUuid,
    tripId,
    result,
    operationalComplete,
    errorCode: clean0737(root.errorCode, 120),
    snapshot,
    privacy,
  };
  if (Buffer.byteLength(JSON.stringify(value), "utf8") > MAX_PAYLOAD_BYTES_0737) {
    throw new Error("Payload HTML remoto excede o limite.");
  }
  return value;
}

function targetStateId0737(username, profileUuid, tripId) {
  return sha256Hex0737([username, profileUuid, tripId].join("|"));
}

function refreshDecision0737(state, expiresAtMillis, lastRequestedAtMillis, nowMillis = Date.now()) {
  const current = clean0737(state, 32).toUpperCase();
  const now = Number(nowMillis || 0);
  if (
    ACTIVE_STATES_0737.has(current) &&
    current !== "PENDING_DEVICE" &&
    Number(expiresAtMillis || 0) > now
  ) {
    return { action: "REUSE_ACTIVE", retryAfterMillis: 0 };
  }
  const requestedAt = Number(lastRequestedAtMillis || 0);
  const elapsed = requestedAt > 0 ? Math.max(0, now - requestedAt) : MIN_REFRESH_INTERVAL_MILLIS_0737;
  if (requestedAt > 0 && elapsed < MIN_REFRESH_INTERVAL_MILLIS_0737) {
    return {
      action: "THROTTLE",
      retryAfterMillis: Math.max(1, MIN_REFRESH_INTERVAL_MILLIS_0737 - elapsed),
    };
  }
  return { action: "CREATE", retryAfterMillis: 0 };
}

function remoteCapablePushToken0737(data, nowMillis = Date.now()) {
  const value = data && typeof data === "object" ? data : {};
  return Number(value.expiresAtMillis || 0) > Number(nowMillis || 0) &&
    clean0737(value.token, 4096).length >= 32 &&
    Number(value.blablacarHtmlRemoteVersion || 0) >= 1;
}

function createBlaBlaHtmlRemote0737({
  db,
  requireDriver,
  getMessaging,
  normalizeUsername,
  json,
  fail,
}) {
  async function ensureAccess0737(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return;
    if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
    const now = Date.now();
    const stateRef = db.collection(STATE_COLLECTION_0737).doc("access_" + driver.username);
    const currentSnap = await stateRef.get();
    const current = currentSnap.exists ? currentSnap.data() : {};
    let token = normalizeToken0737(current.accessToken);
    let expiresAtMillis = Number(current.accessExpiresAtMillis || 0);
    if (!token || expiresAtMillis <= now + 7 * 24 * 60 * 60 * 1000) {
      const oldHash = clean0737(current.accessTokenHash, 80);
      token = opaqueToken0737();
      const tokenHash = sha256Hex0737(token);
      expiresAtMillis = now + ACCESS_TTL_MILLIS_0737;
      const batch = db.batch();
      if (oldHash && oldHash !== tokenHash) {
        batch.delete(db.collection(ACCESS_COLLECTION_0737).doc(oldHash));
      }
      batch.set(db.collection(ACCESS_COLLECTION_0737).doc(tokenHash), {
        driverUsername: driver.username,
        enabled: true,
        createdAtMillis: now,
        updatedAtMillis: now,
        expiresAtMillis,
      });
      batch.set(stateRef, {
        driverUsername: driver.username,
        accessToken: token,
        accessTokenHash: tokenHash,
        accessExpiresAtMillis: expiresAtMillis,
        updatedAtMillis: now,
      }, { merge: true });
      await batch.commit();
    }
    const basePath = "/v1/public/blablacar-html/" + token;
    return json(res, 200, {
      enabled: true,
      tripRefreshPath: basePath + "/trip/refresh",
      tripLatestPath: basePath + "/trip/latest",
      expiresAtMillis,
    });
  }

  async function resolveAccess0737(tokenRaw) {
    const token = normalizeToken0737(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(ACCESS_COLLECTION_0737).doc(sha256Hex0737(token)).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    return username ? { token, username } : null;
  }

  async function sendPush0737(driverUsername, jobId, profileUuid, tripId) {
    const tokens = await db.collection("tripDriverPushTokens")
      .where("driverUsername", "==", driverUsername)
      .limit(20)
      .get();
    const now = Date.now();
    const active = tokens.docs.filter((doc) => remoteCapablePushToken0737(doc.data(), now));
    const jobRef = db.collection(JOB_COLLECTION_0737).doc(jobId);
    if (!active.length) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "NO_HTML_REMOTE_CAPABLE_DEVICE",
        updatedAtMillis: now,
      }, { merge: true });
      return;
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0737(doc.data().token, 4096)),
        data: {
          event: "blablacar_html_trip_collect",
          jobId,
          profileUuid,
          tripId,
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
      await jobRef.set({
        state: Number(response.successCount || 0) > 0 ? "PUSH_SENT" : "PENDING_DEVICE",
        pushSuccessCount: Number(response.successCount || 0),
        pushFailureCount: Number(response.failureCount || 0),
        updatedAtMillis: Date.now(),
      }, { merge: true });
    } catch (error) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "HTML_REMOTE_PUSH_FAILED",
        errorMessage: clean0737(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
    }
  }

  async function createJob0737(driverUsername, profileUuidRaw, tripIdRaw, trigger) {
    const profileUuid = canonicalUuid0737(profileUuidRaw);
    const tripId = canonicalUuid0737(tripIdRaw);
    if (!profileUuid || !tripId) {
      return { invalid: true, profileUuid, tripId };
    }
    const now = Date.now();
    const targetId = targetStateId0737(driverUsername, profileUuid, tripId);
    const stateRef = db.collection(STATE_COLLECTION_0737).doc(targetId);
    let jobId = "";
    let reused = false;
    let throttled = false;
    let retryAfterMillis = 0;

    await db.runTransaction(async (transaction) => {
      const stateSnap = await transaction.get(stateRef);
      const state = stateSnap.exists ? stateSnap.data() : {};
      const latestJobId = canonicalUuid0737(state.latestJobId);
      if (latestJobId) {
        const latestJobRef = db.collection(JOB_COLLECTION_0737).doc(latestJobId);
        const latestJobSnap = await transaction.get(latestJobRef);
        if (latestJobSnap.exists) {
          const latest = latestJobSnap.data();
          const decision = refreshDecision0737(
            latest.state,
            latest.expiresAtMillis,
            state.lastRequestedAtMillis,
            now,
          );
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
      transaction.set(db.collection(JOB_COLLECTION_0737).doc(jobId), {
        jobId,
        driverUsername,
        profileUuid,
        tripId,
        state: "REQUESTED",
        trigger: clean0737(trigger, 40),
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

    if (throttled) {
      return { throttled: true, retryAfterMillis, profileUuid, tripId };
    }
    if (!reused) await sendPush0737(driverUsername, jobId, profileUuid, tripId);
    const jobSnap = await db.collection(JOB_COLLECTION_0737).doc(jobId).get();
    const job = jobSnap.exists ? jobSnap.data() : {};
    return {
      jobId,
      profileUuid,
      tripId,
      state: clean0737(job.state, 32) || "PENDING_DEVICE",
      reused,
      requestedAtMillis: Number(job.requestedAtMillis || now),
      updatedAtMillis: Number(job.updatedAtMillis || now),
    };
  }

  async function requestAuthenticated0737(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return;
    if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
    const job = await createJob0737(
      driver.username,
      req.body && req.body.profileUuid,
      req.body && req.body.tripId,
      "driver_authenticated",
    );
    if (job.invalid) return fail(res, 400, "html_remote_identity_invalid", "UUID/tripId inválidos.");
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "html_remote_refresh_throttled", "Aguarde antes de solicitar outra leitura.", {
        retryAfterMillis: job.retryAfterMillis,
      });
    }
    return json(res, 202, job);
  }

  async function refreshPublic0737(req, res, tokenRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "html_remote_access_not_found", "Acesso privado HTML não encontrado ou expirado.");
    const job = await createJob0737(
      access.username,
      req.query && req.query.profileUuid,
      req.query && req.query.tripId,
      "private_capability",
    );
    if (job.invalid) return fail(res, 400, "html_remote_identity_invalid", "UUID/tripId inválidos.");
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "html_remote_refresh_throttled", "Aguarde antes de solicitar outra leitura.", {
        retryAfterMillis: job.retryAfterMillis,
      });
    }
    return json(res, 202, {
      ...job,
      statusMeaning: "Somente COMPLETE comprova roster, itinerário e vagas da viagem solicitada.",
    });
  }

  async function latestForTarget0737(username, profileUuid, tripId) {
    const targetId = targetStateId0737(username, profileUuid, tripId);
    const stateSnap = await db.collection(STATE_COLLECTION_0737).doc(targetId).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const latestJobId = canonicalUuid0737(state.latestJobId);
    let latest = null;
    if (latestJobId) {
      const jobSnap = await db.collection(JOB_COLLECTION_0737).doc(latestJobId).get();
      if (jobSnap.exists) {
        const job = jobSnap.data();
        const now = Date.now();
        let stateValue = clean0737(job.state, 32) || "PENDING_UNKNOWN";
        let payload = job.payload || null;
        let errorCode = clean0737(job.errorCode, 120);
        let errorMessage = clean0737(job.errorMessage, 240);
        if (ACTIVE_STATES_0737.has(stateValue) && Number(job.expiresAtMillis || 0) > 0 && Number(job.expiresAtMillis) <= now) {
          stateValue = "EXPIRED";
          payload = null;
          errorCode = "JOB_EXPIRED";
          await jobSnap.ref.set({ state: "EXPIRED", payload: null, errorCode, updatedAtMillis: now }, { merge: true });
        } else if (
          TERMINAL_STATES_0737.has(stateValue) &&
          stateValue !== "EXPIRED" &&
          Number(job.resultExpiresAtMillis || 0) > 0 &&
          Number(job.resultExpiresAtMillis) <= now
        ) {
          stateValue = "EXPIRED";
          payload = null;
          errorCode = "RESULT_EXPIRED";
          await jobSnap.ref.set({ state: "EXPIRED", payload: null, errorCode, updatedAtMillis: now }, { merge: true });
        }
        latest = {
          state: stateValue,
          jobId: latestJobId,
          requestedAtMillis: Number(job.requestedAtMillis || 0),
          updatedAtMillis: Number(job.updatedAtMillis || 0),
          completedAtMillis: Number(job.completedAtMillis || 0),
          result: payload,
          errorCode,
          errorMessage,
        };
      }
    }
    return {
      latest,
      lastComplete: state.lastCompletePayload || null,
      lastCompleteAtMillis: Number(state.lastCompleteAtMillis || 0),
    };
  }

  async function latestPublic0737(req, res, tokenRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "html_remote_access_not_found", "Acesso privado HTML não encontrado ou expirado.");
    const profileUuid = canonicalUuid0737(req.query && req.query.profileUuid);
    const tripId = canonicalUuid0737(req.query && req.query.tripId);
    if (!profileUuid || !tripId) return fail(res, 400, "html_remote_identity_invalid", "UUID/tripId inválidos.");
    const value = await latestForTarget0737(access.username, profileUuid, tripId);
    const latest = value.latest || {
      state: "PENDING_UNKNOWN",
      jobId: "",
      requestedAtMillis: 0,
      updatedAtMillis: 0,
      completedAtMillis: 0,
      result: null,
      errorCode: "",
      errorMessage: "",
    };
    return json(res, 200, {
      profileUuid,
      tripId,
      ...latest,
      lastComplete: value.lastComplete,
      lastCompleteAtMillis: value.lastCompleteAtMillis,
      decisionSafe: latest.state === "COMPLETE" && latest.result && latest.result.result === "COMPLETE",
      statusMeaning: latest.state === "COMPLETE"
        ? "Viagem comprovada pela captura HTML direcionada no aparelho."
        : "A leitura atual não está comprovada como COMPLETE; trate ocupação e detalhes como desconhecidos.",
    });
  }

  async function requireOwnedJob0737(req, res, jobIdRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    if (!driver.username) {
      fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
      return null;
    }
    const jobId = canonicalUuid0737(jobIdRaw);
    if (!jobId) {
      fail(res, 400, "html_remote_job_invalid", "Job HTML inválido.");
      return null;
    }
    const ref = db.collection(JOB_COLLECTION_0737).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "html_remote_job_not_found", "Job HTML não encontrado.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function ackJob0737(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0737(req, res, jobIdRaw);
    if (!owned) return;
    const current = clean0737(owned.data.state, 32).toUpperCase();
    if (TERMINAL_STATES_0737.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current });
    }
    const now = Date.now();
    if (Number(owned.data.expiresAtMillis || 0) > 0 && Number(owned.data.expiresAtMillis) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "html_remote_job_expired", "Job HTML expirou antes da execução.");
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

  async function submitResult0737(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0737(req, res, jobIdRaw);
    if (!owned) return;
    const current = clean0737(owned.data.state, 32).toUpperCase();
    if (TERMINAL_STATES_0737.has(current)) {
      const requested = clean0737(req.body && req.body.status, 20).toUpperCase();
      if (requested === current && current !== "EXPIRED") {
        return json(res, 200, { accepted: true, jobId: owned.jobId, state: current, idempotent: true });
      }
      return fail(res, 409, "html_remote_terminal_state_conflict", "Job HTML já foi encerrado.", { currentState: current });
    }

    const status = clean0737(req.body && req.body.status, 20).toUpperCase();
    if (!RESULT_STATES_0737.has(status)) return fail(res, 400, "html_remote_result_status_invalid", "Status HTML inválido.");
    let payload = null;
    if (status === "COMPLETE" || status === "PARTIAL") {
      try {
        payload = sanitizePayload0737(req.body && req.body.payload);
      } catch (error) {
        return fail(res, 400, "html_remote_payload_invalid", clean0737(error && error.message, 240));
      }
      if (payload.result !== status) return fail(res, 400, "html_remote_result_mismatch", "Status diverge do payload.");
      if (
        payload.profileUuid !== canonicalUuid0737(owned.data.profileUuid) ||
        payload.tripId !== canonicalUuid0737(owned.data.tripId)
      ) {
        return fail(res, 400, "html_remote_job_identity_mismatch", "Snapshot não corresponde ao alvo do job.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "html_remote_failed_payload_forbidden", "FAILED não transporta snapshot.");
    }

    const now = Date.now();
    await owned.ref.set({
      state: status,
      payload,
      errorCode: status === "FAILED" ? clean0737(req.body && req.body.errorCode, 120) || "HTML_REMOTE_DEVICE_COLLECTION_FAILED" : clean0737(payload && payload.errorCode, 120),
      errorMessage: status === "FAILED" ? clean0737(req.body && req.body.errorMessage, 240) : "",
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + RESULT_TTL_MILLIS_0737,
    }, { merge: true });

    const targetId = targetStateId0737(
      owned.driver.username,
      canonicalUuid0737(owned.data.profileUuid),
      canonicalUuid0737(owned.data.tripId),
    );
    const targetPatch = {
      latestCompletedJobId: owned.jobId,
      latestCompletedState: status,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    };
    if (status === "COMPLETE") {
      targetPatch.lastCompletePayload = payload;
      targetPatch.lastCompleteAtMillis = now;
    }
    await db.collection(STATE_COLLECTION_0737).doc(targetId).set(targetPatch, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: status });
  }

  return {
    ensureAccess0737,
    requestAuthenticated0737,
    refreshPublic0737,
    latestPublic0737,
    ackJob0737,
    submitResult0737,
  };
}

module.exports = {
  ACCESS_COLLECTION_0737,
  STATE_COLLECTION_0737,
  JOB_COLLECTION_0737,
  MAX_PAYLOAD_BYTES_0737,
  canonicalUuid0737,
  sanitizePayload0737,
  refreshDecision0737,
  remoteCapablePushToken0737,
  targetStateId0737,
  createBlaBlaHtmlRemote0737,
};
