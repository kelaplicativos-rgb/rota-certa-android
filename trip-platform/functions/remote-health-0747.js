"use strict";

const crypto = require("crypto");
const {
  ACCESS_COLLECTION_0736,
  normalizePublicToken0736,
  canonicalUuid0736,
} = require("./standalone-covers-remote-0736");

const STATE_COLLECTION_0747 = "tripRemoteHealthState0747";
const JOB_COLLECTION_0747 = "tripRemoteHealthJobs0747";
const JOB_TTL_MILLIS_0747 = 10 * 60 * 1000;
const RESULT_TTL_MILLIS_0747 = 24 * 60 * 60 * 1000;
const MIN_REFRESH_MILLIS_0747 = 30 * 1000;
const MAX_PAYLOAD_BYTES_0747 = 512 * 1024;
const ACTIVE_STATES_0747 = new Set(["REQUESTED", "PUSH_SENT", "PENDING_DEVICE", "RUNNING"]);
const TERMINAL_STATES_0747 = new Set(["COMPLETE", "FAILED", "EXPIRED"]);
const RESULT_STATES_0747 = new Set(["COMPLETE", "FAILED"]);

function clean0747(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256Hex0747(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function int0747(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
  const n = Number(value);
  if (!Number.isSafeInteger(n) || n < min || n > max) throw new Error("Numero invalido.");
  return n;
}

function assertObject0747(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(label + " invalido.");
  return value;
}

function assertKeys0747(value, allowed, label) {
  const extra = Object.keys(value).filter((key) => !allowed.has(key));
  if (extra.length) throw new Error(label + " contem campo nao permitido: " + extra[0]);
}

function sanitizeIncident0747(raw) {
  const v = assertObject0747(raw, "Incidente");
  assertKeys0747(v, new Set([
    "id", "severity", "module", "fingerprint", "firstSeenMillis", "lastSeenMillis",
    "count", "errorCode", "symptom", "probableRootCause", "confidencePercent",
    "suggestedCorrection", "lifecycle",
  ]), "Incidente");
  return {
    id: clean0747(v.id, 80),
    severity: clean0747(v.severity, 24),
    module: clean0747(v.module, 100),
    fingerprint: clean0747(v.fingerprint, 100),
    firstSeenMillis: int0747(v.firstSeenMillis),
    lastSeenMillis: int0747(v.lastSeenMillis),
    count: int0747(v.count, 0, 1_000_000),
    errorCode: clean0747(v.errorCode, 140),
    symptom: clean0747(v.symptom, 1200),
    probableRootCause: clean0747(v.probableRootCause, 1200),
    confidencePercent: int0747(v.confidencePercent, 0, 100),
    suggestedCorrection: clean0747(v.suggestedCorrection, 1200),
    lifecycle: clean0747(v.lifecycle, 32),
  };
}

function sanitizeEvent0747(raw) {
  const v = assertObject0747(raw, "Evento");
  assertKeys0747(v, new Set([
    "atMillis", "stage", "packageName", "threadName", "details",
    "parentModule", "originModule", "executorModule", "component", "operation",
    "severity", "result", "errorCode", "reason", "durationMs",
  ]), "Evento");
  const duration = v.durationMs == null ? null : int0747(v.durationMs, 0, 24 * 60 * 60 * 1000);
  return {
    atMillis: int0747(v.atMillis),
    stage: clean0747(v.stage, 160),
    packageName: clean0747(v.packageName, 160),
    threadName: clean0747(v.threadName, 100),
    details: clean0747(v.details, 700),
    parentModule: clean0747(v.parentModule, 80),
    originModule: clean0747(v.originModule, 80),
    executorModule: clean0747(v.executorModule, 80),
    component: clean0747(v.component, 120),
    operation: clean0747(v.operation, 120),
    severity: clean0747(v.severity, 24),
    result: clean0747(v.result, 60),
    errorCode: clean0747(v.errorCode, 140),
    reason: clean0747(v.reason, 700),
    durationMs: duration,
  };
}

function sanitizeHealthPayload0747(input) {
  const bytes = Buffer.byteLength(JSON.stringify(input == null ? null : input), "utf8");
  if (bytes <= 0 || bytes > MAX_PAYLOAD_BYTES_0747) throw new Error("Snapshot de saude vazio ou acima do limite.");
  const root = assertObject0747(input, "Snapshot de saude");
  assertKeys0747(root, new Set([
    "schemaVersion", "kind", "capturedAtMillis", "sourceAppVersion", "sourceVersionCode",
    "sourceCommitSha", "sourceBranch", "state", "validation", "validationSummary",
    "sourceEventCount", "droppedEvents", "incidents", "events", "buffer",
  ]), "Snapshot de saude");
  if (root.schemaVersion !== "rota-certa-remote-health-v1") throw new Error("Schema de saude nao suportado.");
  if (root.kind !== "ROTA_CERTA_REMOTE_HEALTH") throw new Error("Tipo de saude nao suportado.");

  const incidentsRaw = Array.isArray(root.incidents) ? root.incidents : null;
  const eventsRaw = Array.isArray(root.events) ? root.events : null;
  if (!incidentsRaw || incidentsRaw.length > 40) throw new Error("Lista de incidentes invalida.");
  if (!eventsRaw || eventsRaw.length > 240) throw new Error("Lista de eventos invalida.");

  const buffer = assertObject0747(root.buffer, "Buffer");
  assertKeys0747(buffer, new Set([
    "eventsInBuffer", "bufferCapacity", "recordCalls", "recordMedianNs", "recordP95Ns", "recordMaxNs",
  ]), "Buffer");

  return {
    schemaVersion: root.schemaVersion,
    kind: root.kind,
    capturedAtMillis: int0747(root.capturedAtMillis),
    sourceAppVersion: clean0747(root.sourceAppVersion, 40),
    sourceVersionCode: int0747(root.sourceVersionCode, 0, 10_000_000),
    sourceCommitSha: clean0747(root.sourceCommitSha, 80),
    sourceBranch: clean0747(root.sourceBranch, 160),
    state: clean0747(root.state, 20),
    validation: clean0747(root.validation, 40),
    validationSummary: clean0747(root.validationSummary, 1600),
    sourceEventCount: int0747(root.sourceEventCount, 0, 100_000),
    droppedEvents: int0747(root.droppedEvents, 0, Number.MAX_SAFE_INTEGER),
    incidents: incidentsRaw.map(sanitizeIncident0747),
    events: eventsRaw.map(sanitizeEvent0747),
    buffer: {
      eventsInBuffer: int0747(buffer.eventsInBuffer, 0, 100_000),
      bufferCapacity: int0747(buffer.bufferCapacity, 0, 100_000),
      recordCalls: int0747(buffer.recordCalls, 0, Number.MAX_SAFE_INTEGER),
      recordMedianNs: Number(buffer.recordMedianNs || 0),
      recordP95Ns: Number(buffer.recordP95Ns || 0),
      recordMaxNs: Number(buffer.recordMaxNs || 0),
    },
  };
}

function healthResultTransition0747(currentState, requestedState) {
  const current = clean0747(currentState, 32).toUpperCase();
  const requested = clean0747(requestedState, 32).toUpperCase();
  if (!TERMINAL_STATES_0747.has(current)) return { action: "WRITE", state: requested };
  if (current === requested && current !== "EXPIRED") return { action: "IDEMPOTENT", state: current };
  return { action: "REJECT", state: current };
}

function remoteHealthCapable0747(data, now = Date.now()) {
  const v = data && typeof data === "object" ? data : {};
  return Number(v.expiresAtMillis || 0) > now &&
    clean0747(v.token, 4096).length >= 32 &&
    Number(v.remoteHealthVersion || 0) >= 1;
}

function createRemoteHealth0747({
  db,
  requireDriver,
  getMessaging,
  normalizeUsername,
  json,
  fail,
}) {
  async function resolveAccess0747(tokenRaw) {
    const token = normalizePublicToken0736(tokenRaw);
    if (!token) return null;
    const snap = await db.collection(ACCESS_COLLECTION_0736).doc(sha256Hex0747(token)).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.enabled !== true || Number(data.expiresAtMillis || 0) <= Date.now()) return null;
    const username = normalizeUsername(data.driverUsername);
    return username ? { username } : null;
  }

  async function requireOwnedJob0747(req, res, jobIdRaw) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    const jobId = canonicalUuid0736(jobIdRaw);
    if (!jobId) {
      fail(res, 400, "remote_health_job_invalid", "Identidade da consulta de saude invalida.");
      return null;
    }
    const ref = db.collection(JOB_COLLECTION_0747).doc(jobId);
    const snap = await ref.get();
    if (!snap.exists || normalizeUsername(snap.data().driverUsername) !== driver.username) {
      fail(res, 404, "remote_health_job_not_found", "Consulta de saude nao encontrada.");
      return null;
    }
    return { driver, jobId, ref, data: snap.data() };
  }

  async function sendPush0747(driverUsername, jobId) {
    const snapshot = await db.collection("tripDriverPushTokens")
      .where("driverUsername", "==", driverUsername)
      .limit(20)
      .get();
    const now = Date.now();
    const active = snapshot.docs.filter((doc) => remoteHealthCapable0747(doc.data(), now));
    const jobRef = db.collection(JOB_COLLECTION_0747).doc(jobId);
    if (!active.length) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "NO_REMOTE_HEALTH_CAPABLE_DEVICE",
        updatedAtMillis: now,
      }, { merge: true });
      return "PENDING_DEVICE";
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: active.map((doc) => clean0747(doc.data().token, 4096)),
        data: {
          event: "remote_health_collect",
          jobId,
          requestedAtMillis: String(now),
        },
        android: { priority: "high", ttl: JOB_TTL_MILLIS_0747 },
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
        errorCode: state === "PENDING_DEVICE" ? "REMOTE_HEALTH_PUSH_NOT_DELIVERABLE" : "",
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return state;
    } catch (error) {
      await jobRef.set({
        state: "PENDING_DEVICE",
        errorCode: "REMOTE_HEALTH_PUSH_FAILED",
        errorMessage: clean0747(error && (error.code || error.name || error.message), 160),
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return "PENDING_DEVICE";
    }
  }

  async function createJob0747(driverUsername) {
    const now = Date.now();
    const stateRef = db.collection(STATE_COLLECTION_0747).doc(driverUsername);
    let jobId = "";
    let reused = false;
    let throttled = false;
    let retryAfterMillis = 0;

    await db.runTransaction(async (transaction) => {
      const stateSnap = await transaction.get(stateRef);
      const state = stateSnap.exists ? stateSnap.data() : {};
      const latestJobId = canonicalUuid0736(state.latestJobId);
      const lastRequestedAtMillis = Number(state.lastRequestedAtMillis || 0);
      if (latestJobId) {
        const latestRef = db.collection(JOB_COLLECTION_0747).doc(latestJobId);
        const latestSnap = await transaction.get(latestRef);
        if (latestSnap.exists) {
          const latest = latestSnap.data();
          const latestState = clean0747(latest.state, 32).toUpperCase();
          if (ACTIVE_STATES_0747.has(latestState) &&
              latestState !== "PENDING_DEVICE" &&
              Number(latest.expiresAtMillis || 0) > now) {
            jobId = latestJobId;
            reused = true;
            return;
          }
        }
      }
      const elapsed = lastRequestedAtMillis > 0 ? Math.max(0, now - lastRequestedAtMillis) : MIN_REFRESH_MILLIS_0747;
      if (lastRequestedAtMillis > 0 && elapsed < MIN_REFRESH_MILLIS_0747) {
        throttled = true;
        retryAfterMillis = Math.max(1, MIN_REFRESH_MILLIS_0747 - elapsed);
        return;
      }
      jobId = crypto.randomUUID();
      transaction.set(db.collection(JOB_COLLECTION_0747).doc(jobId), {
        jobId,
        driverUsername,
        state: "REQUESTED",
        requestedAtMillis: now,
        updatedAtMillis: now,
        expiresAtMillis: now + JOB_TTL_MILLIS_0747,
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

    if (throttled) return { throttled: true, retryAfterMillis };
    if (!reused) await sendPush0747(driverUsername, jobId);
    const snap = await db.collection(JOB_COLLECTION_0747).doc(jobId).get();
    const data = snap.exists ? snap.data() : { state: "PENDING_DEVICE" };
    return {
      jobId,
      state: clean0747(data.state, 32) || "PENDING_DEVICE",
      reused,
      requestedAtMillis: Number(data.requestedAtMillis || now),
      updatedAtMillis: Number(data.updatedAtMillis || now),
    };
  }

  async function refreshPublic0747(req, res, tokenRaw) {
    const access = await resolveAccess0747(tokenRaw);
    if (!access) return fail(res, 404, "remote_health_access_not_found", "Acesso privado de saude nao encontrado ou expirado.");
    const job = await createJob0747(access.username);
    if (job.throttled) {
      res.set("Retry-After", String(Math.max(1, Math.ceil(job.retryAfterMillis / 1000))));
      return fail(res, 429, "remote_health_refresh_throttled", "Aguarde antes de solicitar outra consulta de saude.", {
        retryAfterMillis: job.retryAfterMillis,
      });
    }
    return json(res, 202, {
      ...job,
      statusMeaning: "PUSH_SENT significa somente que a solicitacao chegou ao fluxo de consentimento. Logs so podem ser coletados depois de ACEITAR.",
    });
  }

  async function latestPublic0747(req, res, tokenRaw) {
    const access = await resolveAccess0747(tokenRaw);
    if (!access) return fail(res, 404, "remote_health_access_not_found", "Acesso privado de saude nao encontrado ou expirado.");
    const stateSnap = await db.collection(STATE_COLLECTION_0747).doc(access.username).get();
    const state = stateSnap.exists ? stateSnap.data() : {};
    const jobId = canonicalUuid0736(state.latestJobId);
    if (!jobId) {
      return json(res, 200, { state: "PENDING_UNKNOWN", jobId: "", result: null, errorCode: "" });
    }
    const jobSnap = await db.collection(JOB_COLLECTION_0747).doc(jobId).get();
    if (!jobSnap.exists) {
      return json(res, 200, { state: "PENDING_UNKNOWN", jobId, result: null, errorCode: "JOB_NOT_AVAILABLE" });
    }
    const job = jobSnap.data();
    const now = Date.now();
    let stateValue = clean0747(job.state, 32) || "PENDING_UNKNOWN";
    let result = job.payload || null;
    let errorCode = clean0747(job.errorCode, 120);
    let errorMessage = clean0747(job.errorMessage, 240);
    if (ACTIVE_STATES_0747.has(stateValue) && Number(job.expiresAtMillis || 0) > 0 && Number(job.expiresAtMillis) <= now) {
      stateValue = "EXPIRED";
      result = null;
      errorCode = "JOB_EXPIRED";
      await jobSnap.ref.set({ state: "EXPIRED", payload: null, errorCode, updatedAtMillis: now }, { merge: true });
    } else if (TERMINAL_STATES_0747.has(stateValue) && stateValue !== "EXPIRED" &&
      Number(job.resultExpiresAtMillis || 0) > 0 && Number(job.resultExpiresAtMillis) <= now) {
      stateValue = "EXPIRED";
      result = null;
      errorCode = "RESULT_EXPIRED";
      errorMessage = "";
      await jobSnap.ref.set({ state: "EXPIRED", payload: null, errorCode, errorMessage, updatedAtMillis: now }, { merge: true });
    }
    return json(res, 200, {
      state: stateValue,
      jobId,
      requestedAtMillis: Number(job.requestedAtMillis || 0),
      updatedAtMillis: Number(job.updatedAtMillis || 0),
      completedAtMillis: Number(job.completedAtMillis || 0),
      result,
      errorCode,
      errorMessage,
      statusMeaning: stateValue === "COMPLETE"
        ? "Snapshot sanitizado da Central de Saude recebido apos consentimento."
        : stateValue === "FAILED" && errorCode === "REMOTE_ACCESS_DECLINED_BY_USER"
          ? "Solicitacao recusada pelo usuario; nenhum log foi coletado."
          : "Consulta de saude ainda nao comprovada como concluida.",
    });
  }

  async function ackJob0747(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0747(req, res, jobIdRaw);
    if (!owned) return;
    const now = Date.now();
    const current = clean0747(owned.data.state, 32).toUpperCase();
    if (TERMINAL_STATES_0747.has(current)) {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: current });
    }
    if (Number(owned.data.expiresAtMillis || 0) > 0 && Number(owned.data.expiresAtMillis) <= now) {
      await owned.ref.set({ state: "EXPIRED", updatedAtMillis: now }, { merge: true });
      return fail(res, 409, "remote_health_job_expired", "A consulta de saude expirou antes da execucao.");
    }
    await owned.ref.set({
      state: "RUNNING",
      deviceAppVersion: clean0747(req.body && req.body.appVersion, 40),
      deviceSourceCommitSha: clean0747(req.body && req.body.sourceCommitSha, 80),
      startedAtMillis: Number(owned.data.startedAtMillis || 0) || now,
      updatedAtMillis: now,
      errorCode: "",
      errorMessage: "",
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: "RUNNING" });
  }

  async function submitResult0747(req, res, jobIdRaw) {
    const owned = await requireOwnedJob0747(req, res, jobIdRaw);
    if (!owned) return;
    const requested = clean0747(req.body && req.body.status, 20).toUpperCase();
    if (!RESULT_STATES_0747.has(requested)) {
      return fail(res, 400, "remote_health_result_status_invalid", "Status final de saude invalido.");
    }
    const transition = healthResultTransition0747(owned.data.state, requested);
    if (transition.action === "IDEMPOTENT") {
      return json(res, 200, { accepted: true, jobId: owned.jobId, state: transition.state, idempotent: true });
    }
    if (transition.action === "REJECT") {
      return fail(res, 409, "remote_health_terminal_state_conflict", "A consulta de saude ja foi encerrada.", {
        currentState: transition.state,
      });
    }

    let payload = null;
    if (requested === "COMPLETE") {
      try {
        payload = sanitizeHealthPayload0747(req.body && req.body.payload);
      } catch (error) {
        return fail(res, 400, "remote_health_payload_invalid", clean0747(error && error.message, 240) || "Snapshot de saude invalido.");
      }
    } else if (req.body && req.body.payload != null) {
      return fail(res, 400, "remote_health_failed_payload_forbidden", "FAILED nao pode transportar logs.");
    }

    const now = Date.now();
    const errorCode = requested === "FAILED" ? clean0747(req.body && req.body.errorCode, 120) || "REMOTE_HEALTH_FAILED" : "";
    const errorMessage = requested === "FAILED" ? clean0747(req.body && req.body.errorMessage, 240) : "";
    await owned.ref.set({
      state: requested,
      payload,
      errorCode,
      errorMessage,
      completedAtMillis: now,
      updatedAtMillis: now,
      resultExpiresAtMillis: now + RESULT_TTL_MILLIS_0747,
    }, { merge: true });
    await db.collection(STATE_COLLECTION_0747).doc(owned.driver.username).set({
      latestCompletedJobId: owned.jobId,
      latestCompletedState: requested,
      latestCompletedAtMillis: now,
      updatedAtMillis: now,
    }, { merge: true });
    return json(res, 200, { accepted: true, jobId: owned.jobId, state: requested });
  }

  return { refreshPublic0747, latestPublic0747, ackJob0747, submitResult0747 };
}

module.exports = {
  STATE_COLLECTION_0747,
  JOB_COLLECTION_0747,
  MAX_PAYLOAD_BYTES_0747,
  sanitizeHealthPayload0747,
  createRemoteHealth0747,
};
