"use strict";

const crypto = require("crypto");

const ACCESS_COLLECTION_0736 = "tripStandaloneCoverAccess0736";
const STATE_COLLECTION_0736 = "tripStandaloneCoverRemoteState0736";
const JOB_COLLECTION_0736 = "tripStandaloneCoverJobs0736";
const ACCESS_TTL_MILLIS_0736 = 365 * 24 * 60 * 60 * 1000;
const JOB_TTL_MILLIS_0736 = 10 * 60 * 1000;
const RESULT_TTL_MILLIS_0736 = 24 * 60 * 60 * 1000;
const MIN_REFRESH_INTERVAL_MILLIS_0736 = 45 * 1000;
const MAX_PAYLOAD_BYTES_0736 = 700 * 1024;
const ACTIVE_JOB_STATES_0736 = new Set(["REQUESTED", "PUSH_SENT", "PENDING_DEVICE", "RUNNING"]);
const TERMINAL_JOB_STATES_0736 = new Set(["COMPLETE", "PARTIAL", "FAILED", "EXPIRED"]);
const RESULT_STATES_0736 = new Set(["COMPLETE", "PARTIAL", "FAILED"]);

function clean0736(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0736(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function canonicalUuid0736(value) {
  const raw = clean0736(value, 80).toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(raw)
    ? raw
    : "";
}

function assertObject0736(value, message) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(message);
  return value;
}

function assertKeys0736(value, allowed, label) {
  const extra = Object.keys(value).filter((key) => !allowed.has(key));
  if (extra.length) throw new Error(label + " contém campo não permitido: " + extra[0]);
}

function sanitizeStandaloneCoverPayload0736(input) {
  const rawBytes = Buffer.byteLength(JSON.stringify(input == null ? null : input), "utf8");
  if (rawBytes <= 0 || rawBytes > MAX_PAYLOAD_BYTES_0736) {
    throw new Error("Payload avulso vazio ou acima do limite remoto.");
  }
  const root = assertObject0736(input, "Payload avulso inválido.");
  assertKeys0736(root, new Set([
    "schemaVersion", "kind", "capturedAt", "sourceAppVersion", "sourceVersionCode",
    "sourceCommitSha", "sourceBranch", "result", "totalProfiles", "totalCards",
    "isolation", "profiles",
  ]), "Payload avulso");

  if (root.schemaVersion !== "rota-certa-blablacar-ride-covers-v1") throw new Error("Schema avulso não suportado.");
  if (root.kind !== "BLABLACAR_RIDE_COVERS_STANDALONE") throw new Error("Tipo avulso não suportado.");
  const result = clean0736(root.result, 20).toUpperCase();
  if (result !== "COMPLETE" && result !== "PARTIAL") throw new Error("Resultado avulso inválido.");

  const isolation = assertObject0736(root.isolation, "Contrato de isolamento ausente.");
  assertKeys0736(isolation, new Set([
    "downloadOnly", "writesTimeline", "writesAgenda", "writesCanonicalTrips",
    "writesAvailability", "writesCapacity", "writesTodayState", "readsPassengers",
    "opensTripDetails",
  ]), "Contrato de isolamento");
  if (
    isolation.downloadOnly !== true ||
    isolation.writesTimeline !== false ||
    isolation.writesAgenda !== false ||
    isolation.writesCanonicalTrips !== false ||
    isolation.writesAvailability !== false ||
    isolation.writesCapacity !== false ||
    isolation.writesTodayState !== false ||
    isolation.readsPassengers !== false ||
    isolation.opensTripDetails !== false
  ) {
    throw new Error("Contrato de isolamento avulso foi violado.");
  }

  if (!Array.isArray(root.profiles) || root.profiles.length < 1 || root.profiles.length > 32) {
    throw new Error("Quantidade de perfis avulsos inválida.");
  }

  const profileAllowed = new Set([
    "displayName", "profileUuid", "identityConfirmed", "status", "observedCardCount",
    "exportedCardCount", "reachedEnd", "stabilized", "errorCode", "collectionAttempts",
    "recoveredTransiently", "cards",
  ]);
  const cardAllowed = new Set([
    "tripId", "administrativeHref", "dateIso", "dateText", "departureTime",
    "arrivalTime", "origin", "destination", "price",
  ]);

  let totalCards = 0;
  const profiles = root.profiles.map((profileRaw) => {
    const profile = assertObject0736(profileRaw, "Perfil avulso inválido.");
    assertKeys0736(profile, profileAllowed, "Perfil avulso");
    const uuid = canonicalUuid0736(profile.profileUuid);
    if (!uuid) throw new Error("UUID canônico do perfil ausente.");
    const status = clean0736(profile.status, 20).toUpperCase();
    if (status !== "COMPLETE" && status !== "PARTIAL") throw new Error("Status de perfil inválido.");
    const identityConfirmed = profile.identityConfirmed === true;
    if (status === "COMPLETE" && !identityConfirmed) throw new Error("Perfil COMPLETE sem identidade confirmada.");
    const observed = Number(profile.observedCardCount);
    const exported = Number(profile.exportedCardCount);
    if (!Number.isSafeInteger(observed) || observed < 0 || !Number.isSafeInteger(exported) || exported < 0) {
      throw new Error("Contagem de capas inválida.");
    }
    if (!Array.isArray(profile.cards) || profile.cards.length !== exported || profile.cards.length > 500) {
      throw new Error("Inventário de capas inconsistente.");
    }
    if (status === "COMPLETE" && observed !== exported) throw new Error("Perfil COMPLETE com inventário incompleto.");
    if (status === "COMPLETE" && (profile.reachedEnd !== true || profile.stabilized !== true)) {
      throw new Error("Perfil COMPLETE sem prova de fim/estabilidade.");
    }
    const attempts = Number(profile.collectionAttempts);
    if (!Number.isSafeInteger(attempts) || attempts < 1 || attempts > 2) throw new Error("Tentativas avulsas inválidas.");

    const cards = profile.cards.map((cardRaw) => {
      const card = assertObject0736(cardRaw, "Capa avulsa inválida.");
      assertKeys0736(card, cardAllowed, "Capa avulsa");
      const tripId = clean0736(card.tripId, 128);
      const dateIso = clean0736(card.dateIso, 20);
      if (status === "COMPLETE" && (!tripId || !/^\d{4}-\d{2}-\d{2}$/.test(dateIso))) {
        throw new Error("Capa COMPLETE sem tripId/data.");
      }
      return {
        tripId,
        administrativeHref: clean0736(card.administrativeHref, 800),
        dateIso,
        dateText: clean0736(card.dateText, 600),
        departureTime: clean0736(card.departureTime, 40),
        arrivalTime: clean0736(card.arrivalTime, 40),
        origin: clean0736(card.origin, 500),
        destination: clean0736(card.destination, 500),
        price: clean0736(card.price, 120),
      };
    });
    totalCards += cards.length;
    return {
      displayName: clean0736(profile.displayName, 120),
      profileUuid: uuid,
      identityConfirmed,
      status,
      observedCardCount: observed,
      exportedCardCount: exported,
      reachedEnd: profile.reachedEnd === true,
      stabilized: profile.stabilized === true,
      errorCode: clean0736(profile.errorCode, 120),
      collectionAttempts: attempts,
      recoveredTransiently: profile.recoveredTransiently === true,
      cards,
    };
  });

  const totalProfiles = Number(root.totalProfiles);
  const declaredCards = Number(root.totalCards);
  if (totalProfiles !== profiles.length || declaredCards !== totalCards) {
    throw new Error("Totais do payload avulso inconsistentes.");
  }
  const expected = profiles.every((profile) => profile.status === "COMPLETE") ? "COMPLETE" : "PARTIAL";
  if (result !== expected) throw new Error("Resultado global avulso inconsistente.");

  return {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAt: clean0736(root.capturedAt, 80),
    sourceAppVersion: clean0736(root.sourceAppVersion, 40),
    sourceVersionCode: Math.max(0, Number(root.sourceVersionCode || 0)),
    sourceCommitSha: clean0736(root.sourceCommitSha, 80),
    sourceBranch: clean0736(root.sourceBranch, 160),
    result,
    totalProfiles,
    totalCards,
    isolation: {
      downloadOnly: true,
      writesTimeline: false,
      writesAgenda: false,
      writesCanonicalTrips: false,
      writesAvailability: false,
      writesCapacity: false,
      writesTodayState: false,
      readsPassengers: false,
      opensTripDetails: false,
    },
    profiles,
  };
}

function publicAccessToken0736() {
  return crypto.randomBytes(32).toString("base64url");
}

function normalizePublicToken0736(value) {
  const token = clean0736(value, 120);
  return /^[A-Za-z0-9_-]{40,100}$/.test(token) ? token : "";
}

function createStandaloneCoversRemote0736({
  db,
  requireDriver,
  getMessaging,
  normalizeUsername,
  json,
  fail,
}) {
  async function requireOwnedJob0736(req, res, jobIdRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    if (!driver.username) {
      fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
      return null;
    }
    const jobId = canonicalUuid0736(jobIdRaw);
    if (!jobId) {
      fail(res, 400, "standalone_cover_job_invalid", "Identidade da coleta remota inválida.");
      return null;
    }
    const ref = db.collection(JOB_COLLECTION_0736).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "standalone_cover_job_not_found", "Coleta remota não encontrada.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function ensureAccess0736(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return;
    if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
    const now = Date.now();
    const stateRef = db.collection(STATE_COLLECTION_0736).doc(driver.username);
    const existing = await stateRef.get();
    const existingData = existing.exists ? existing.data() : {};
    let token = normalizePublicToken0736(existingData.accessToken);
    let expiresAtMillis = Number(existingData.accessExpiresAtMillis || 0);

    if (!token || expiresAtMillis <= now + 7 * 24 * 60 * 60 * 1000) {
      const previousHash = clean0736(existingData.accessTokenHash, 80);
      token = publicAccessToken0736();
      const tokenHash = sha256Hex0736(token);
      expiresAtMillis = now + ACCESS_TTL_MILLIS_0736;
      const batch = db.batch();
      if (previousHash && previousHash !== tokenHash) {
        batch.delete(db.collection(ACCESS_COLLECTION_0736).doc(previousHash));
      }
      batch.set(db.collection(ACCESS_COLLECTION_0736).doc(tokenHash), {
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

    const basePath = "/v1/public/standalone-covers/" + token;
    return json(res, 200, {
      enabled: true,
      refreshPath: basePath + "/refresh",
      latestPath: basePath + "/latest",
      expiresAtMillis,
    });
  }

  async function resolveAccess0736(tokenRaw) {
    const token = normalizePublicToken0736(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(ACCESS_COLLECTION_0736).doc(sha256Hex0736(token)).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    return username ? { token, username, data } : null;
  }

  async function sendPush0736(driverUsername, jobId) {
    const snapshot = await db.collection("tripDriverPushTokens")
      .where("driverUsername", "==", driverUsername)
      .limit(20)
      .get();
    const now = Date.now();
    const active = snapshot.docs.filter((doc) =>
      Number(doc.data().expiresAtMillis || 0) > now && clean0736(doc.data().token, 4096).length >= 32,
    );
    const jobRef = db.collection(JOB_COLLECTION_0736).doc(jobId);
    if (!active.length) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "NO_ACTIVE_DEVICE_PUSH_TOKEN",
        updatedAtMillis: now,
      }, { merge: true });
      return "PENDING_DEVICE";
    }

    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0736(doc.data().token, 4096)),
        data: {
          event: "standalone_covers_collect",
          jobId,
          requestedAtMillis: String(now),
        },
        android: { priority: "high", ttl: JOB_TTL_MILLIS_0736 },
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
        errorMessage: clean0736(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return "PENDING_DEVICE";
    }
  }

  async function createJob0736(driverUsername, trigger) {
    const now = Date.now();
    const stateRef = db.collection(STATE_COLLECTION_0736).doc(driverUsername);
    let jobId = "";
    let reused = false;
    await db.runTransaction(async (transaction) => {
      const stateSnap = await transaction.get(stateRef);
      const state = stateSnap.exists ? stateSnap.data() : {};
      const latestJobId = canonicalUuid0736(state.latestJobId);
      const lastRequestedAtMillis = Number(state.lastRequestedAtMillis || 0);
      if (latestJobId && now - lastRequestedAtMillis < MIN_REFRESH_INTERVAL_MILLIS_0736) {
        jobId = latestJobId;
        reused = true;
        return;
      }
      jobId = crypto.randomUUID();
      const jobRef = db.collection(JOB_COLLECTION_0736).doc(jobId);
      transaction.set(jobRef, {
        jobId,
        driverUsername,
        state: "REQUESTED",
        trigger: clean0736(trigger, 40),
        requestedAtMillis: now,
        updatedAtMillis: now,
        expiresAtMillis: now + JOB_TTL_MILLIS_0736,
        resultExpiresAtMillis: 0,
        payload: null,
        errorCode: "",
        errorMessage: "",
      });
      transaction.set(stateRef, {
        driverUsername,
        latestJobId: jobId,
        lastRequestedAtMillis: now,
        updatedAtMillis: now,
      }, { merge: true });
    });

    if (!reused) await sendPush0736(driverUsername, jobId);
    const snap = await db.collection(JOB_COLLECTION_0736).doc(jobId).get();
    const data = snap.exists ? snap.data() : { state: "PENDING_DEVICE" };
    return {
      jobId,
      state: clean0736(data.state, 32) || "PENDING_DEVICE",
      reused,
      requestedAtMillis: Number(data.requestedAtMillis || now),
      updatedAtMillis: Number(data.updatedAtMillis || now),
    };
  }

  async function requestAuthenticated0736(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return;
    if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
    const job = await createJob0736(driver.username, "driver_authenticated");
    return json(res, 202, job);
  }

  async function refreshPublic0736(req, res, tokenRaw) {
    const access = await resolveAccess0736(tokenRaw);
    if (!access) return fail(res, 404, "standalone_cover_access_not_found", "Acesso privado à coleta remota não encontrado ou expirado.");
    const job = await createJob0736(access.username, "private_capability");
    return json(res, 202, {
      ...job,
      statusMeaning: "Somente COMPLETE comprova inventário completo; PARTIAL/FAILED/PENDING_DEVICE permanecem desconhecidos.",
    });
  }

  async function latestPublic0736(req, res, tokenRaw) {
    const access = await resolveAccess0736(tokenRaw);
    if (!access) return fail(res, 404, "standalone_cover_access_not_found", "Acesso privado à coleta remota não encontrado ou expirado.");
    const stateSnap = await db.collection(STATE_COLLECTION_0736).doc(access.username).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const jobId = canonicalUuid0736(state.latestJobId);
    if (!jobId) {
      return json(res, 200, {
        state: "PENDING_UNKNOWN",
        jobId: "",
        result: null,
        errorCode: "",
        statusMeaning: "Nenhuma coleta remota foi concluída ainda.",
      });
    }
    const jobSnap = await db.collection(JOB_COLLECTION_0736).doc(jobId).get();
    if (!jobSnap.exists) {
      return json(res, 200, { state: "PENDING_UNKNOWN", jobId, result: null, errorCode: "JOB_NOT_AVAILABLE" });
    }
    const job = jobSnap.data();
    let stateValue = clean0736(job.state, 32) || "PENDING_UNKNOWN";
    if (ACTIVE_JOB_STATES_0736.has(stateValue) && Number(job.expiresAtMillis || 0) > 0 && Number(job.expiresAtMillis) <= Date.now()) {
      stateValue = "EXPIRED";
      await jobSnap.ref.set({ state: "EXPIRED", updatedAtMillis: Date.now() }, { merge: true });
    }
    return json(res, 200, {
      state: stateValue,
      jobId,
      requestedAtMillis: Number(job.requestedAtMillis || 0),
      updatedAtMillis: Number(job.updatedAtMillis || 0),
      completedAtMillis: Number(job.completedAtMillis || 0),
      result: job.payload || null,
      errorCode: clean0736(job.errorCode, 120),
      errorMessage: clean0736(job.errorMessage, 240),
      statusMeaning: stateValue === "COMPLETE"
        ? "Inventário de capas comprovado pelo aparelho."
        : "Inventário não comprovado como completo; trate como desconhecido.",
    });
  }

  async function ackJob0736(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0736(req, res, jobIdRaw);
    if (!owned) return;
    const now = Date.now();
    const current = clean0736(owned.data.state, 32);
    if (TERMINAL_JOB_STATES_0736.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current });
    }
    if (Number(owned.data.expiresAtMillis || 0) > 0 && Number(owned.data.expiresAtMillis) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "standalone_cover_job_expired", "A coleta remota expirou antes da execução.");
    }
    await owned.ref.set({
      state: "RUNNING",
      deviceAppVersion: clean0736(req.body && req.body.appVersion, 40),
      deviceSourceCommitSha: clean0736(req.body && req.body.sourceCommitSha, 80),
      startedAtMillis: Number(owned.data.startedAtMillis || 0) || now,
      updatedAtMillis: now,
      errorCode: "",
      errorMessage: "",
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: "RUNNING" });
  }

  async function submitResult0736(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0736(req, res, jobIdRaw);
    if (!owned) return;
    const requestedStatus = clean0736(req.body && req.body.status, 20).toUpperCase();
    if (!RESULT_STATES_0736.has(requestedStatus)) {
      return fail(res, 400, "standalone_cover_result_status_invalid", "Status final da coleta remota inválido.");
    }

    let payload = null;
    if (requestedStatus === "COMPLETE" || requestedStatus === "PARTIAL") {
      try {
        payload = sanitizeStandaloneCoverPayload0736(req.body && req.body.payload);
      } catch (error) {
        return fail(res, 400, "standalone_cover_payload_invalid", clean0736(error && error.message, 240) || "Payload avulso inválido.");
      }
      if (payload.result !== requestedStatus) {
        return fail(res, 400, "standalone_cover_result_mismatch", "Status remoto diverge do resultado do payload.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "standalone_cover_failed_payload_forbidden", "FAILED não pode transportar inventário como resultado válido.");
    }

    const now = Date.now();
    await owned.ref.set({
      state: requestedStatus,
      payload,
      errorCode: requestedStatus === "FAILED" ? clean0736(req.body && req.body.errorCode, 120) || "REMOTE_DEVICE_COLLECTION_FAILED" : "",
      errorMessage: requestedStatus === "FAILED" ? clean0736(req.body && req.body.errorMessage, 240) : "",
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + RESULT_TTL_MILLIS_0736,
    }, { merge: true });
    await db.collection(STATE_COLLECTION_0736).doc(owned.driver.username).set({
      latestJobId: owned.jobId,
      latestCompletedJobId: owned.jobId,
      latestCompletedState: requestedStatus,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: requestedStatus });
  }

  return {
    ensureAccess0736,
    requestAuthenticated0736,
    refreshPublic0736,
    latestPublic0736,
    ackJob0736,
    submitResult0736,
  };
}

module.exports = {
  ACCESS_COLLECTION_0736,
  STATE_COLLECTION_0736,
  JOB_COLLECTION_0736,
  ACTIVE_JOB_STATES_0736,
  TERMINAL_JOB_STATES_0736,
  MAX_PAYLOAD_BYTES_0736,
  sanitizeStandaloneCoverPayload0736,
  publicAccessToken0736,
  normalizePublicToken0736,
  canonicalUuid0736,
  createStandaloneCoversRemote0736,
};
