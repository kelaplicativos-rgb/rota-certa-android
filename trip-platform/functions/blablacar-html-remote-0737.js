"use strict";

const crypto = require("crypto");
const {
  JOB_COLLECTION_0736,
  STATE_COLLECTION_0736,
  normalizePublicToken0736,
  canonicalUuid0736,
} = require("./standalone-covers-remote-0736");

const HTML_ACCESS_COLLECTION_0737 = "tripBlaBlaHtmlAccess0737";
const HTML_STATE_COLLECTION_0737 = "tripBlaBlaHtmlQueryState0737";
const HTML_JOB_COLLECTION_0737 = "tripBlaBlaHtmlQueryJobs0737";
const HTML_ACCESS_TTL_MILLIS_0737 = 180 * 24 * 60 * 60 * 1000;
const HTML_JOB_TTL_MILLIS_0737 = 15 * 60 * 1000;
const HTML_RESULT_TTL_MILLIS_0737 = 24 * 60 * 60 * 1000;
const HTML_MIN_REFRESH_INTERVAL_MILLIS_0737 = 20 * 1000;
const HTML_MAX_PAYLOAD_BYTES_0737 = 256 * 1024;
const HTML_ACTIVE_STATES_0737 = new Set(["REQUESTED", "PUSH_SENT", "PENDING_DEVICE", "RUNNING"]);
const HTML_TERMINAL_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED", "EXPIRED"]);
const HTML_RESULT_STATES_0737 = new Set(["COMPLETE", "PARTIAL", "FAILED"]);

function clean0737(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0737(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function accessToken0737() {
  return crypto.randomBytes(32).toString("base64url");
}

function normalizeAccessToken0737(value) {
  return normalizePublicToken0736(value);
}

function assertObject0737(value, message) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(message);
  return value;
}

function assertKeys0737(value, allowed, label) {
  const extra = Object.keys(value).filter((key) => !allowed.has(key));
  if (extra.length) throw new Error(label + " contém campo não permitido: " + extra[0]);
}

function targetStateId0737(username, profileUuid, tripId) {
  return sha256Hex0737([clean0737(username, 80), profileUuid, tripId].join("|"));
}

function normalizeTarget0737(profileUuidRaw, tripIdRaw) {
  const profileUuid = canonicalUuid0736(profileUuidRaw);
  const tripId = canonicalUuid0736(tripIdRaw);
  return profileUuid && tripId ? { profileUuid, tripId } : null;
}

function sanitizeHtmlPayload0737(input) {
  const rawBytes = Buffer.byteLength(JSON.stringify(input == null ? null : input), "utf8");
  if (rawBytes <= 0 || rawBytes > HTML_MAX_PAYLOAD_BYTES_0737) {
    throw new Error("Payload HTML vazio ou acima do limite remoto.");
  }
  const root = assertObject0737(input, "Payload HTML inválido.");
  assertKeys0737(root, new Set([
    "schemaVersion", "kind", "capturedAt", "sourceAppVersion", "sourceVersionCode",
    "sourceCommitSha", "sourceBranch", "result", "profileUuid", "profileName", "tripId",
    "date", "departureTime", "arrivalTime", "origin", "destination", "price", "availability",
    "uuidValidation", "bookedSeats", "publishedSeats", "passengerRosterComplete",
    "itineraryAuthoritative", "itineraryStops", "itineraryStopTimes", "identityConflict",
    "coreComplete", "privateDetailsComplete", "passengers", "errorCode", "isolation",
  ]), "Payload HTML");

  if (root.schemaVersion !== "rota-certa-blablacar-trip-html-v1") throw new Error("Schema HTML não suportado.");
  if (root.kind !== "BLABLACAR_TRIP_HTML_QUERY") throw new Error("Tipo HTML não suportado.");
  const result = clean0737(root.result, 20).toUpperCase();
  if (result !== "COMPLETE" && result !== "PARTIAL") throw new Error("Resultado HTML inválido.");

  const profileUuid = canonicalUuid0736(root.profileUuid);
  const tripId = canonicalUuid0736(root.tripId);
  if (!profileUuid || !tripId) throw new Error("Identidade forte HTML inválida.");

  const isolation = assertObject0737(root.isolation, "Contrato de isolamento HTML ausente.");
  assertKeys0737(isolation, new Set([
    "writesTimeline", "writesAgenda", "writesCanonicalTrips", "writesAvailability",
    "writesCapacity", "writesTodayState", "readsPassengers", "opensTripDetails",
    "rawHtmlUploaded", "credentialsUploaded",
  ]), "Contrato de isolamento HTML");
  if (
    isolation.writesTimeline !== false ||
    isolation.writesAgenda !== false ||
    isolation.writesCanonicalTrips !== false ||
    isolation.writesAvailability !== false ||
    isolation.writesCapacity !== false ||
    isolation.writesTodayState !== false ||
    isolation.readsPassengers !== true ||
    isolation.opensTripDetails !== true ||
    isolation.rawHtmlUploaded !== false ||
    isolation.credentialsUploaded !== false
  ) {
    throw new Error("Contrato de isolamento HTML foi violado.");
  }

  if (!Array.isArray(root.itineraryStops) || root.itineraryStops.length > 80) {
    throw new Error("Itinerário HTML inválido.");
  }
  if (!Array.isArray(root.itineraryStopTimes) || root.itineraryStopTimes.length > 80) {
    throw new Error("Horários de itinerário HTML inválidos.");
  }
  if (!Array.isArray(root.passengers) || root.passengers.length > 64) {
    throw new Error("Lista de passageiros HTML inválida.");
  }
  const passengers = root.passengers.map((raw) => {
    const passenger = assertObject0737(raw, "Passageiro HTML inválido.");
    assertKeys0737(passenger, new Set(["name", "seats", "boarding", "dropoff"]), "Passageiro HTML");
    const seats = Math.floor(Number(passenger.seats || 0));
    if (seats < 1 || seats > 20) throw new Error("Quantidade de lugares do passageiro inválida.");
    return {
      name: clean0737(passenger.name, 120),
      seats,
      boarding: clean0737(passenger.boarding, 180),
      dropoff: clean0737(passenger.dropoff, 180),
    };
  });

  const publishedSeats = root.publishedSeats == null ? null : Math.floor(Number(root.publishedSeats));
  if (publishedSeats != null && (publishedSeats < 0 || publishedSeats > 20)) {
    throw new Error("Vagas publicadas HTML inválidas.");
  }
  const bookedSeats = Math.max(0, Math.min(100, Math.floor(Number(root.bookedSeats || 0))));
  const passengerRosterComplete = root.passengerRosterComplete === true;
  const itineraryAuthoritative = root.itineraryAuthoritative === true;
  const identityConflict = root.identityConflict === true;
  const coreComplete = root.coreComplete === true;
  const privateDetailsComplete = root.privateDetailsComplete === true;

  if (result === "COMPLETE" && (!coreComplete || !passengerRosterComplete || identityConflict || publishedSeats == null)) {
    throw new Error("COMPLETE HTML sem prova operacional mínima.");
  }

  return {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAt: clean0737(root.capturedAt, 80),
    sourceAppVersion: clean0737(root.sourceAppVersion, 40),
    sourceVersionCode: Math.max(0, Number(root.sourceVersionCode || 0)),
    sourceCommitSha: clean0737(root.sourceCommitSha, 80),
    sourceBranch: clean0737(root.sourceBranch, 160),
    result,
    profileUuid,
    profileName: clean0737(root.profileName, 120),
    tripId,
    date: clean0737(root.date, 20),
    departureTime: clean0737(root.departureTime, 20),
    arrivalTime: clean0737(root.arrivalTime, 20),
    origin: clean0737(root.origin, 180),
    destination: clean0737(root.destination, 180),
    price: clean0737(root.price, 80),
    availability: clean0737(root.availability, 80),
    uuidValidation: clean0737(root.uuidValidation, 80),
    bookedSeats,
    publishedSeats,
    passengerRosterComplete,
    itineraryAuthoritative,
    itineraryStops: root.itineraryStops.map((value) => clean0737(value, 180)),
    itineraryStopTimes: root.itineraryStopTimes.map((value) => clean0737(value, 20)),
    identityConflict,
    coreComplete,
    privateDetailsComplete,
    passengers,
    errorCode: clean0737(root.errorCode, 160),
    isolation: {
      writesTimeline: false,
      writesAgenda: false,
      writesCanonicalTrips: false,
      writesAvailability: false,
      writesCapacity: false,
      writesTodayState: false,
      readsPassengers: true,
      opensTripDetails: true,
      rawHtmlUploaded: false,
      credentialsUploaded: false,
    },
  };
}

function remoteHtmlCapablePushToken0737(data, nowMillis = Date.now()) {
  const value = data && typeof data === "object" ? data : {};
  return Number(value.expiresAtMillis || 0) > Number(nowMillis || 0) &&
    clean0737(value.token, 4096).length >= 32 &&
    Number(value.blablacarHtmlRemoteVersion || 0) >= 1;
}

async function resolveTripHrefFromCompleteCovers0737(db, driverUsername, profileUuid, tripId) {
  const stateSnap = await db.collection(STATE_COLLECTION_0736).doc(driverUsername).get();
  if (!stateSnap.exists) return { errorCode: "COMPLETE_COVERS_REQUIRED" };
  const state = stateSnap.data() || {};
  const candidates = [
    canonicalUuid0736(state.latestCompleteJobId),
    canonicalUuid0736(state.latestCompletedJobId),
  ].filter(Boolean);
  for (const jobId of [...new Set(candidates)]) {
    const snap = await db.collection(JOB_COLLECTION_0736).doc(jobId).get();
    if (!snap.exists) continue;
    const job = snap.data() || {};
    if (clean0737(job.state, 32).toUpperCase() !== "COMPLETE" || !job.payload) continue;
    const profiles = Array.isArray(job.payload.profiles) ? job.payload.profiles : [];
    const matchingProfiles = profiles.filter((profile) =>
      canonicalUuid0736(profile && profile.profileUuid) === profileUuid &&
      profile && profile.status === "COMPLETE" &&
      profile.identityConfirmed === true
    );
    if (matchingProfiles.length !== 1) continue;
    const cards = Array.isArray(matchingProfiles[0].cards) ? matchingProfiles[0].cards : [];
    const matches = cards.filter((card) => canonicalUuid0736(card && card.tripId) === tripId);
    if (matches.length !== 1) continue;
    const href = clean0737(matches[0].administrativeHref, 1200);
    if (!href) continue;
    return { href, coversJobId: jobId };
  }
  return { errorCode: "TRIP_NOT_PROVEN_IN_COMPLETE_COVERS" };
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
    const stateRef = db.collection(HTML_STATE_COLLECTION_0737).doc("access_" + sha256Hex0737(driver.username));
    const existing = await stateRef.get();
    const data = existing.exists ? existing.data() : {};
    let token = normalizeAccessToken0737(data.accessToken);
    let expiresAtMillis = Number(data.accessExpiresAtMillis || 0);
    if (!token || expiresAtMillis <= now + 7 * 24 * 60 * 60 * 1000) {
      const previousHash = clean0737(data.accessTokenHash, 80);
      token = accessToken0737();
      const tokenHash = sha256Hex0737(token);
      expiresAtMillis = now + HTML_ACCESS_TTL_MILLIS_0737;
      const batch = db.batch();
      if (previousHash && previousHash !== tokenHash) {
        batch.delete(db.collection(HTML_ACCESS_COLLECTION_0737).doc(previousHash));
      }
      batch.set(db.collection(HTML_ACCESS_COLLECTION_0737).doc(tokenHash), {
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
    const basePath = "/v1/public/blablacar-html/" + token + "/trip";
    return json(res, 200, {
      enabled: true,
      refreshPath: basePath + "/refresh",
      latestPath: basePath + "/latest",
      expiresAtMillis,
    });
  }

  async function resolveAccess0737(tokenRaw) {
    const token = normalizeAccessToken0737(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(HTML_ACCESS_COLLECTION_0737).doc(sha256Hex0737(token)).get();
    if (!snap.exists) return null;
    const data = snap.data() || {};
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    return username ? { username } : null;
  }

  async function requireOwnedJob0737(req, res, jobIdRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    const jobId = canonicalUuid0736(jobIdRaw);
    if (!jobId) {
      fail(res, 400, "blablacar_html_job_invalid", "Identidade da consulta HTML inválida.");
      return null;
    }
    const ref = db.collection(HTML_JOB_COLLECTION_0737).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "blablacar_html_job_not_found", "Consulta HTML não encontrada.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function sendPush0737(driverUsername, job) {
    const snapshot = await db.collection("tripDriverPushTokens")
      .where("driverUsername", "==", driverUsername)
      .limit(20)
      .get();
    const active = snapshot.docs.filter((doc) => remoteHtmlCapablePushToken0737(doc.data()));
    const jobRef = db.collection(HTML_JOB_COLLECTION_0737).doc(job.jobId);
    if (!active.length) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "NO_HTML_REMOTE_CAPABLE_DEVICE",
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return;
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0737(doc.data().token, 4096)),
        data: {
          event: "blablacar_html_trip_collect",
          jobId: job.jobId,
          profileUuid: job.profileUuid,
          tripId: job.tripId,
          tripHref: job.tripHref,
        },
        android: { priority: "high", ttl: HTML_JOB_TTL_MILLIS_0737 },
      });
      const state = Number(response.successCount || 0) > 0 ? "PUSH_SENT" : "PENDING_DEVICE";
      await jobRef.set({
        state,
        pushSuccessCount: Number(response.successCount || 0),
        pushFailureCount: Number(response.failureCount || 0),
        errorCode: state === "PENDING_DEVICE" ? "HTML_DEVICE_PUSH_NOT_DELIVERABLE" : "",
        updatedAtMillis: Date.now(),
      }, { merge: true });
    } catch (error) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "HTML_DEVICE_PUSH_FAILED",
        errorMessage: clean0737(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
    }
  }

  async function createTripJob0737(driverUsername, profileUuid, tripId) {
    const targetKey = targetStateId0737(driverUsername, profileUuid, tripId);
    const stateRef = db.collection(HTML_STATE_COLLECTION_0737).doc(targetKey);
    const now = Date.now();
    const existingStateSnap = await stateRef.get();
    const existingState = existingStateSnap.exists ? existingStateSnap.data() : {};
    const latestJobId = canonicalUuid0736(existingState.latestJobId);
    if (latestJobId) {
      const latestSnap = await db.collection(HTML_JOB_COLLECTION_0737).doc(latestJobId).get();
      if (latestSnap.exists) {
        const latest = latestSnap.data() || {};
        const state = clean0737(latest.state, 32).toUpperCase();
        if (HTML_ACTIVE_STATES_0737.has(state) && Number(latest.expiresAtMillis || 0) > now && state !== "PENDING_DEVICE") {
          return {
            jobId: latestJobId,
            state,
            reused: true,
            requestedAtMillis: Number(latest.requestedAtMillis || 0),
          };
        }
      }
    }
    const lastRequestedAtMillis = Number(existingState.lastRequestedAtMillis || 0);
    if (lastRequestedAtMillis > 0 && now - lastRequestedAtMillis < HTML_MIN_REFRESH_INTERVAL_MILLIS_0737) {
      return {
        throttled: true,
        retryAfterMillis: HTML_MIN_REFRESH_INTERVAL_MILLIS_0737 - (now - lastRequestedAtMillis),
      };
    }

    const cover = await resolveTripHrefFromCompleteCovers0737(db, driverUsername, profileUuid, tripId);
    if (!cover.href) return { blocked: true, errorCode: cover.errorCode || "COMPLETE_COVERS_REQUIRED" };

    const jobId = crypto.randomUUID();
    const job = {
      jobId,
      driverUsername,
      profileUuid,
      tripId,
      tripHref: cover.href,
      coversJobId: cover.coversJobId || "",
      state: "REQUESTED",
      requestedAtMillis: now,
      updatedAtMillis: now,
      expiresAtMillis: now + HTML_JOB_TTL_MILLIS_0737,
      resultExpiresAtMillis: 0,
      payload: null,
      errorCode: "",
      errorMessage: "",
    };
    const batch = db.batch();
    batch.set(db.collection(HTML_JOB_COLLECTION_0737).doc(jobId), job);
    batch.set(stateRef, {
      driverUsername,
      profileUuid,
      tripId,
      latestJobId: jobId,
      lastRequestedAtMillis: now,
      updatedAtMillis: now,
    }, { merge: true });
    await batch.commit();
    await sendPush0737(driverUsername, job);
    const after = await db.collection(HTML_JOB_COLLECTION_0737).doc(jobId).get();
    const saved = after.exists ? after.data() : job;
    return {
      jobId,
      state: clean0737(saved.state, 32) || "PENDING_DEVICE",
      reused: false,
      requestedAtMillis: now,
    };
  }

  async function refreshPublic0737(req, res, tokenRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "blablacar_html_access_not_found", "Acesso privado HTML não encontrado ou expirado.");
    const target = normalizeTarget0737(req.query && req.query.profileUuid, req.query && req.query.tripId);
    if (!target) return fail(res, 400, "blablacar_html_target_invalid", "Informe profileUuid e tripId canônicos.");
    const job = await createTripJob0737(access.username, target.profileUuid, target.tripId);
    if (job.blocked) {
      return fail(
        res,
        409,
        "blablacar_html_complete_covers_required",
        "A consulta HTML exige que a viagem esteja comprovada no último inventário COMPLETE de capas.",
        { errorCode: job.errorCode },
      );
    }
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "blablacar_html_refresh_throttled", "Aguarde antes de solicitar nova consulta HTML.", {
        retryAfterMillis: job.retryAfterMillis,
      });
    }
    return json(res, 202, {
      ...job,
      profileUuid: target.profileUuid,
      tripId: target.tripId,
      statusMeaning: "COMPLETE exige identidade forte, roster de passageiros completo, itinerário autoritativo e vagas publicadas comprovadas.",
    });
  }

  async function latestPublic0737(req, res, tokenRaw) {
    const access = await resolveAccess0737(tokenRaw);
    if (!access) return fail(res, 404, "blablacar_html_access_not_found", "Acesso privado HTML não encontrado ou expirado.");
    const target = normalizeTarget0737(req.query && req.query.profileUuid, req.query && req.query.tripId);
    if (!target) return fail(res, 400, "blablacar_html_target_invalid", "Informe profileUuid e tripId canônicos.");
    const stateRef = db.collection(HTML_STATE_COLLECTION_0737).doc(
      targetStateId0737(access.username, target.profileUuid, target.tripId),
    );
    const stateSnap = await stateRef.get();
    const stateData = stateSnap.exists ? stateSnap.data() : {};
    const latestJobId = canonicalUuid0736(stateData.latestJobId);
    const lastCompleteJobId = canonicalUuid0736(stateData.lastCompleteJobId);

    async function readJob(jobId) {
      if (!jobId) return null;
      const snap = await db.collection(HTML_JOB_COLLECTION_0737).doc(jobId).get();
      return snap.exists ? { id: jobId, ref: snap.ref, data: snap.data() || {} } : null;
    }

    const latest = await readJob(latestJobId);
    const cachedComplete = lastCompleteJobId && lastCompleteJobId !== latestJobId
      ? await readJob(lastCompleteJobId)
      : null;
    if (!latest) {
      return json(res, 200, {
        state: "PENDING_UNKNOWN",
        jobId: "",
        profileUuid: target.profileUuid,
        tripId: target.tripId,
        result: null,
        lastCompleteResult: cachedComplete ? cachedComplete.data.payload || null : null,
        statusMeaning: "Nenhuma consulta HTML foi concluída para esta viagem.",
      });
    }

    const now = Date.now();
    let state = clean0737(latest.data.state, 32) || "PENDING_UNKNOWN";
    let result = latest.data.payload || null;
    let errorCode = clean0737(latest.data.errorCode, 160);
    let errorMessage = clean0737(latest.data.errorMessage, 240);
    const expiresAt = HTML_ACTIVE_STATES_0737.has(state)
      ? Number(latest.data.expiresAtMillis || 0)
      : Number(latest.data.resultExpiresAtMillis || 0);
    if (expiresAt > 0 && expiresAt <= now && state !== "EXPIRED") {
      state = "EXPIRED";
      result = null;
      errorCode = "HTML_RESULT_EXPIRED";
      errorMessage = "";
      await latest.ref.set({ state, payload: null, errorCode, errorMessage, updatedAtMillis: now }, { merge: true });
    }
    return json(res, 200, {
      state,
      jobId: latest.id,
      profileUuid: target.profileUuid,
      tripId: target.tripId,
      requestedAtMillis: Number(latest.data.requestedAtMillis || 0),
      updatedAtMillis: Number(latest.data.updatedAtMillis || 0),
      completedAtMillis: Number(latest.data.completedAtMillis || 0),
      result,
      errorCode,
      errorMessage,
      lastCompleteJobId: cachedComplete ? cachedComplete.id : (state === "COMPLETE" ? latest.id : lastCompleteJobId || ""),
      lastCompleteResult: state === "COMPLETE"
        ? result
        : cachedComplete && clean0737(cachedComplete.data.state, 32) === "COMPLETE"
          ? cachedComplete.data.payload || null
          : null,
      statusMeaning: state === "COMPLETE"
        ? "Consulta HTML operacional comprovada para esta viagem."
        : "A consulta mais recente não está COMPLETE; não trate ausência de passageiros ou dados como comprovada.",
    });
  }

  async function ackJob0737(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0737(req, res, jobIdRaw);
    if (!owned) return;
    const current = clean0737(owned.data.state, 32).toUpperCase();
    if (HTML_TERMINAL_STATES_0737.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current });
    }
    const now = Date.now();
    if (Number(owned.data.expiresAtMillis || 0) > 0 && Number(owned.data.expiresAtMillis) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "blablacar_html_job_expired", "A consulta HTML expirou antes da execução.");
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
    const requestedStatus = clean0737(req.body && req.body.status, 20).toUpperCase();
    if (!HTML_RESULT_STATES_0737.has(requestedStatus)) {
      return fail(res, 400, "blablacar_html_result_status_invalid", "Status final HTML inválido.");
    }
    if (HTML_TERMINAL_STATES_0737.has(current)) {
      if (current === requestedStatus && current !== "EXPIRED") {
        return json(res, 200, { accepted: true, jobId: owned.jobId, state: current, idempotent: true });
      }
      return fail(res, 409, "blablacar_html_terminal_state_conflict", "A consulta HTML já foi encerrada.", {
        currentState: current,
      });
    }

    let payload = null;
    if (requestedStatus === "COMPLETE" || requestedStatus === "PARTIAL") {
      try {
        payload = sanitizeHtmlPayload0737(req.body && req.body.payload);
      } catch (error) {
        return fail(res, 400, "blablacar_html_payload_invalid", clean0737(error && error.message, 240) || "Payload HTML inválido.");
      }
      if (payload.result !== requestedStatus) {
        return fail(res, 400, "blablacar_html_result_mismatch", "Status remoto diverge do payload HTML.");
      }
      if (payload.profileUuid !== owned.data.profileUuid || payload.tripId !== owned.data.tripId) {
        return fail(res, 409, "blablacar_html_identity_mismatch", "Resultado HTML diverge da identidade solicitada.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "blablacar_html_failed_payload_forbidden", "FAILED não pode transportar resultado HTML.");
    }

    const now = Date.now();
    await owned.ref.set({
      state: requestedStatus,
      payload,
      errorCode: requestedStatus === "FAILED" ? clean0737(req.body && req.body.errorCode, 160) || "HTML_DEVICE_COLLECTION_FAILED" : "",
      errorMessage: requestedStatus === "FAILED" ? clean0737(req.body && req.body.errorMessage, 240) : "",
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + HTML_RESULT_TTL_MILLIS_0737,
    }, { merge: true });

    const stateRef = db.collection(HTML_STATE_COLLECTION_0737).doc(
      targetStateId0737(owned.driver.username, owned.data.profileUuid, owned.data.tripId),
    );
    const update = {
      latestCompletedJobId: owned.jobId,
      latestCompletedState: requestedStatus,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    };
    if (requestedStatus === "COMPLETE") {
      update.lastCompleteJobId = owned.jobId;
      update.lastCompleteAtMillis = now;
    }
    await stateRef.set(update, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: requestedStatus });
  }

  return {
    ensureAccess0737,
    refreshPublic0737,
    latestPublic0737,
    ackJob0737,
    submitResult0737,
  };
}

module.exports = {
  HTML_ACCESS_COLLECTION_0737,
  HTML_STATE_COLLECTION_0737,
  HTML_JOB_COLLECTION_0737,
  HTML_MAX_PAYLOAD_BYTES_0737,
  sanitizeHtmlPayload0737,
  normalizeTarget0737,
  remoteHtmlCapablePushToken0737,
  resolveTripHrefFromCompleteCovers0737,
  createBlaBlaHtmlRemote0737,
};
