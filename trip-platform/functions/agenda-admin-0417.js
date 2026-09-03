"use strict";

const crypto = require("crypto");

const ADMIN_AUDIT_RETENTION_MILLIS_0417 = 30 * 24 * 60 * 60 * 1000;

function sha256Hex0417(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function clean0417(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function json0417(res, status, body) {
  res.status(status);
  res.set("Content-Type", "application/json; charset=utf-8");
  res.set("Cache-Control", "no-store");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  res.send(JSON.stringify(body));
}

function fail0417(res, status, code, message) {
  return json0417(res, status, { error: code, message });
}

function safeVisibility0417(raw) {
  const source = raw && typeof raw === "object" ? raw : {};
  const fields = [
    "name", "whatsapp", "photo", "about", "rating", "reviews",
    "badge", "vehicle", "amenities", "preferences", "paymentInstructions",
  ];
  const out = {};
  fields.forEach((field) => { out[field] = source[field] !== false; });
  return out;
}

function normalizeProfileScope0417(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value
    .map((item) => clean0417(item, 160).toLowerCase())
    .filter((item) => /^[0-9a-f-]{16,160}$/.test(item)))]
    .slice(0, 64);
}

function normalizeSyncPolicy0417(raw) {
  const source = raw && typeof raw === "object" ? raw : {};
  const interval = Number(source.intervalMinutes);
  return {
    automatic: source.automatic !== false,
    intervalMinutes: Number.isInteger(interval) ? Math.max(15, Math.min(1440, interval)) : 15,
  };
}

function sameSyncPolicy0417(left, right) {
  const a = normalizeSyncPolicy0417(left);
  const b = normalizeSyncPolicy0417(right);
  return a.automatic === b.automatic && a.intervalMinutes === b.intervalMinutes;
}

function authenticationRequired0417(driver) {
  return !(driver && driver.agendaAuthenticationRequired0428 === false);
}

function adminOperationId0417(req) {
  const supplied = clean0417(req && req.get && req.get("X-Rota-Certa-Operation-Id"), 100);
  return /^[A-Za-z0-9_-]{8,100}$/.test(supplied) ? supplied : crypto.randomUUID();
}

function redact0417(value) {
  if (Array.isArray(value)) return value.map(redact0417);
  if (!value || typeof value !== "object") return value;
  const out = {};
  for (const [key, child] of Object.entries(value)) {
    if (/password|token|cookie|secret|authorization|api.?key|credential/i.test(key)) {
      out[key] = "[REDACTED]";
    } else {
      out[key] = redact0417(child);
    }
  }
  return out;
}

function activeAdminTrips0417(trips, nowMillis = Date.now()) {
  return (Array.isArray(trips) ? trips : []).filter((trip) =>
    Number(trip.departureAtMillis || 0) > nowMillis &&
    ["PUBLISHED", "FULL", "STARTING", "ACTIVE"].includes(clean0417(trip.status, 24))
  );
}

function validatedBlaBlaPublicUrl0417(raw, expectedTripId) {
  const value = clean0417(raw, 1200);
  const expected = clean0417(expectedTripId, 160);
  if (!value || !expected) return "";
  try {
    const url = new URL(value);
    if (url.protocol !== "https:") return "";
    if (!/(^|\.)blablacar\.[a-z.]+$/i.test(url.hostname)) return "";
    const path = url.pathname.replace(/\/+$/, "");
    if (path !== "/trip" && !path.startsWith("/trip/")) return "";
    const pathId = path.startsWith("/trip/") ? decodeURIComponent(path.slice("/trip/".length)).split("/")[0] : "";
    const actual = clean0417(url.searchParams.get("id") || pathId, 160);
    return actual === expected ? value : "";
  } catch (_) {
    return "";
  }
}

function createAgendaAdmin0417({
  db,
  resolveDriverUsername,
  requireDriver,
  requirePassengerSession,
  passengerAccessForIdentity,
  passengerAccessIsAuthorized,
  sendDriverBookingPush,
  touchPassengerSessionActivity0427,
}) {
  async function appendAdminAudit0417({
    driverUsername,
    eventType,
    actorId = "",
    correlationId = "",
    requestId = "",
    tripId = "",
    changes = [],
    result = "SUCCESS",
    source = "AGENDA_ADMIN_PANEL",
  }) {
    const now = Date.now();
    const safeChanges = Array.isArray(changes)
      ? changes.slice(0, 32).map((item) => ({
        field: clean0417(item && item.field, 80),
        before: clean0417(item && item.before, 600),
        after: clean0417(item && item.after, 600),
      }))
      : [];
    const id = "admin_" + sha256Hex0417([
      driverUsername, eventType, correlationId, tripId, now, crypto.randomBytes(6).toString("hex"),
    ].join("|")).slice(0, 56);
    await db.collection("tripChangeEvents").doc(id).set({
      eventId: id,
      eventType: clean0417(eventType, 80),
      tripId: clean0417(tripId, 120),
      publicToken: clean0417(tripId, 120),
      bookingId: "",
      passengerId: "",
      driverUsername: clean0417(driverUsername, 40),
      actor: "ADMIN",
      actorId: clean0417(actorId, 80),
      source: clean0417(source, 80),
      correlationId: clean0417(correlationId, 100),
      requestId: clean0417(requestId, 100),
      result: clean0417(result, 24),
      createdAtMillis: now,
      expiresAtMillis: now + ADMIN_AUDIT_RETENTION_MILLIS_0417,
      changes: safeChanges,
      affectedPassengerIds: [],
    });
  }

  async function touchAdminSession0417(session) {
    if (!session || typeof touchPassengerSessionActivity0427 !== "function") return 0;
    const touched = await touchPassengerSessionActivity0427(session.sessionRefId);
    if (touched) session.lastActivityAtMillis = touched;
    return touched;
  }

  async function claimAdminCommand0417({ session, eventType, operationId }) {
    const requestId = clean0417(operationId, 100);
    const id = "adminop_" + sha256Hex0417([
      session.driverUsername,
      session.sessionRefId,
      eventType,
      requestId,
    ].join("|")).slice(0, 56);
    const ref = db.collection("tripChangeEvents").doc(id);
    const claim = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (snap.exists) {
        const data = snap.data();
        return {
          created: false,
          correlationId: clean0417(data.correlationId, 100),
          result: clean0417(data.result, 24),
        };
      }
      const now = Date.now();
      const correlationId = crypto.randomUUID();
      tx.set(ref, {
        eventId: id,
        eventType: clean0417(eventType, 80),
        tripId: "",
        publicToken: "",
        bookingId: "",
        passengerId: clean0417(session.passengerId, 120),
        driverUsername: clean0417(session.driverUsername, 40),
        actor: "ADMIN",
        actorId: clean0417(session.actorId, 80),
        source: "AGENDA_ADMIN_PANEL",
        correlationId,
        requestId,
        result: "DISPATCHING",
        createdAtMillis: now,
        expiresAtMillis: now + ADMIN_AUDIT_RETENTION_MILLIS_0417,
        changes: [],
        affectedPassengerIds: [],
      });
      return { created: true, correlationId, result: "DISPATCHING" };
    });
    return { ...claim, ref, requestId };
  }

  async function requireAdminSession0417(req, res) {
    const requestedDriver = clean0417(
      req.get("X-Rota-Certa-Admin-Driver") ||
      (req.query && req.query.driverUsername) ||
      (req.body && req.body.driverUsername),
      80,
    );
    const resolved = await resolveDriverUsername(requestedDriver);
    if (!resolved || !resolved.canonicalUsername) {
      fail0417(res, 400, "admin_driver_required", "Agenda não identificada.");
      return null;
    }
    const driver = resolved.driverSnap && resolved.driverSnap.exists ? resolved.driverSnap.data() : {};
    if (!authenticationRequired0417(driver)) {
      return {
        driverUsername: resolved.canonicalUsername,
        actorId: "agenda-open-0428",
        passengerId: "",
        passengerContact: "",
        contactHash: "",
        sessionContextHash: "",
        sessionRefId: "",
        createdAtMillis: 0,
        lastActivityAtMillis: 0,
        expiresAtMillis: 0,
        openAccess: true,
      };
    }
    const passengerSession = await requirePassengerSession(req, res);
    if (!passengerSession) return null;
    const access = await passengerAccessForIdentity(
      resolved.canonicalUsername,
      passengerSession.passengerId,
      passengerSession.passengerContact,
    );
    if (!access || !passengerAccessIsAuthorized(access)) {
      fail0417(res, 403, "admin_access_unavailable", "Seu acesso a esta Agenda não está disponível.");
      return null;
    }
    if (access.agendaAdmin !== true) {
      fail0417(res, 403, "agenda_admin_role_required", "Este usuário não é administrador desta Agenda.");
      return null;
    }
    return {
      driverUsername: resolved.canonicalUsername,
      actorId: clean0417(passengerSession.passengerId, 120) ||
        sha256Hex0417(passengerSession.passengerContact).slice(0, 24),
      passengerId: clean0417(passengerSession.passengerId || access.passengerId, 120),
      passengerContact: clean0417(passengerSession.passengerContact, 40),
      contactHash: clean0417(passengerSession.contactHash, 80),
      sessionContextHash: clean0417(passengerSession.sessionContextHash, 80),
      sessionRefId: clean0417(passengerSession.sessionRefId, 100),
      createdAtMillis: Number(passengerSession.createdAtMillis || 0),
      lastActivityAtMillis: Number(passengerSession.lastActivityAtMillis || passengerSession.createdAtMillis || 0),
      expiresAtMillis: Number(passengerSession.expiresAtMillis || 0),
    };
  }

  async function getAdminMe0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const snap = await db.collection("tripDrivers").doc(session.driverUsername).get();
    const driver = snap.exists ? snap.data() : {};
    return json0417(res, 200, {
      driverUsername: session.driverUsername,
      displayName: clean0417(driver.displayName, 120),
      actorId: session.actorId,
      sessionStartedAtMillis: session.createdAtMillis,
      expiresAtMillis: session.expiresAtMillis,
    });
  }

  async function readDriverAndTrips0417(driverUsername) {
    const [driverSnap, tripSnap] = await Promise.all([
      db.collection("tripDrivers").doc(driverUsername).get(),
      db.collection("trips").where("driverUsername", "==", driverUsername).limit(300).get(),
    ]);
    return {
      driver: driverSnap.exists ? driverSnap.data() : {},
      trips: tripSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() })),
    };
  }

  function safeTripAdminSummary0417(trip) {
    const state = clean0417(trip.publicAttestationState0417, 24) || "UNPROVEN";
    const blablaTripId = clean0417(trip.blablaTripId, 160);
    const blablaPublicUrl = validatedBlaBlaPublicUrl0417(trip.blablaPublicUrl, blablaTripId);
    return {
      remoteTripId: trip.id,
      canonicalTripId: clean0417(trip.canonicalTripId || trip.localTripId, 180),
      blablaProfileUuid: clean0417(trip.blablaProfileUuid, 160),
      blablaTripId,
      title: clean0417(trip.title, 220),
      departureAtMillis: Math.max(0, Number(trip.departureAtMillis || 0)),
      status: clean0417(trip.status, 24),
      publicationRevision: Math.max(0, Number(trip.publicationRevision || 0)),
      canonicalStateHash: clean0417(trip.canonicalStateHash, 160),
      attestationState: state,
      attestedAtMillis: Math.max(0, Number(trip.publicAttestedAtMillis0417 || 0)),
      attestationReason: clean0417(trip.publicAttestationReason0417, 160),
      mismatchFields: Array.isArray(trip.publicAttestationMismatchFields0417)
        ? trip.publicAttestationMismatchFields0417.map((v) => clean0417(v, 80)).slice(0, 24)
        : [],
      publicUrl: clean0417(trip.publicUrl, 1200),
      blablaPublicUrl,
      capacityReliable: trip.capacityReliable === true,
      availableSeatsMinimum: Math.max(0, Number(trip.availableSeatsMinimum || 0)),
      availableSeatsMaximum: Math.max(0, Number(trip.availableSeatsMaximum || 0)),
      operationalAvailableSeats: Math.max(0, Number(trip.operationalAvailableSeats || 0)),
      updatedAtMillis: Math.max(0, Number(trip.updatedAtMillis || 0)),
    };
  }

  async function getAdminOverview0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const { driver, trips } = await readDriverAndTrips0417(session.driverUsername);
    const active = activeAdminTrips0417(trips);
    const counts = { verified: 0, pending: 0, divergent: 0, unproven: 0, linksValid: 0, linksPending: 0 };
    active.forEach((trip) => {
      const state = clean0417(trip.publicAttestationState0417, 24);
      if (state === "VERIFIED") counts.verified++;
      else if (state === "PENDING") counts.pending++;
      else if (state === "DIVERGENT" || state === "ERROR") counts.divergent++;
      else counts.unproven++;
      const blablaTripId = clean0417(trip.blablaTripId, 160);
      if (!blablaTripId) return;
      if (validatedBlaBlaPublicUrl0417(trip.blablaPublicUrl, blablaTripId)) counts.linksValid++;
      else counts.linksPending++;
    });
    const health = driver.adminSyncHealth0417 && typeof driver.adminSyncHealth0417 === "object"
      ? driver.adminSyncHealth0417
      : {};
    return json0417(res, 200, {
      counts,
      activeTrips: active.length,
      lastSync: {
        startedAtMillis: Math.max(0, Number(health.startedAtMillis || 0)),
        finishedAtMillis: Math.max(0, Number(health.finishedAtMillis || 0)),
        result: clean0417(health.result, 40) || "UNKNOWN",
        trigger: clean0417(health.trigger, 80),
        correlationId: clean0417(health.correlationId, 100),
        failures: Math.max(0, Number(health.failures || 0)),
        changed: Math.max(0, Number(health.changed || 0)),
        skipped: Math.max(0, Number(health.skipped || 0)),
        pending: Math.max(0, Number(health.pending || 0)),
        divergent: Math.max(0, Number(health.divergent || 0)),
      },
      publicVisibility: safeVisibility0417(driver.publicVisibility0417),
      publicProfileUuids: normalizeProfileScope0417(driver.publicTripProfileUuids0417),
      syncPolicy: normalizeSyncPolicy0417(driver.adminSyncPolicy0417),
    });
  }

  async function listAdminTrips0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const { trips } = await readDriverAndTrips0417(session.driverUsername);
    const active = activeAdminTrips0417(trips);
    return json0417(res, 200, {
      scope: "ACTIVE_PUBLIC_TRIPS",
      total: active.length,
      trips: active
        .map(safeTripAdminSummary0417)
        .sort((a, b) => a.departureAtMillis - b.departureAtMillis),
    });
  }

  async function getAdminSettings0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const { driver, trips } = await readDriverAndTrips0417(session.driverUsername);
    const knownProfiles = [...new Set(trips
      .map((trip) => clean0417(trip.blablaProfileUuid, 160).toLowerCase())
      .filter(Boolean))]
      .map((uuid) => ({ uuid, label: "Perfil " + uuid.slice(-8) }));
    return json0417(res, 200, {
      publicVisibility: safeVisibility0417(driver.publicVisibility0417),
      publicProfileUuids: normalizeProfileScope0417(driver.publicTripProfileUuids0417),
      knownProfiles,
      syncPolicy: normalizeSyncPolicy0417(driver.adminSyncPolicy0417),
      authenticationRequired: authenticationRequired0417(driver),
    });
  }

  async function updateAdminPublicSettings0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const ref = db.collection("tripDrivers").doc(session.driverUsername);
    const snap = await ref.get();
    if (!snap.exists) return fail0417(res, 404, "driver_not_found", "Agenda não encontrada.");
    const before = snap.data();
    const visibility = safeVisibility0417(req.body && req.body.publicVisibility);
    const profiles = normalizeProfileScope0417(req.body && req.body.publicProfileUuids);
    const authenticationRequired = typeof (req.body && req.body.authenticationRequired) === "boolean"
      ? req.body.authenticationRequired
      : authenticationRequired0417(before);
    await ref.set({
      publicVisibility0417: visibility,
      publicTripProfileUuids0417: profiles,
      agendaAuthenticationRequired0428: authenticationRequired,
      updatedAtMillis: Date.now(),
    }, { merge: true });
    await touchAdminSession0417(session);
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      actorId: session.actorId,
      eventType: "PUBLIC_VISIBILITY_CHANGED",
      changes: [
        { field: "publicVisibility", before: JSON.stringify(safeVisibility0417(before.publicVisibility0417)), after: JSON.stringify(visibility) },
        { field: "publicProfileUuids", before: JSON.stringify(normalizeProfileScope0417(before.publicTripProfileUuids0417)), after: JSON.stringify(profiles) },
      ],
    });
    return json0417(res, 200, {
      publicVisibility: visibility,
      publicProfileUuids: profiles,
      authenticationRequired,
    });
  }

  async function updateAdminSyncSettings0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const ref = db.collection("tripDrivers").doc(session.driverUsername);
    const requested = normalizeSyncPolicy0417(req.body);
    const requestId = adminOperationId0417(req);
    const outcome = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return { missing: true, changed: false, before: requested };
      const before = normalizeSyncPolicy0417(snap.data().adminSyncPolicy0417);
      if (sameSyncPolicy0417(before, requested)) return { missing: false, changed: false, before };
      tx.set(ref, {
        adminSyncPolicy0417: requested,
        updatedAtMillis: Date.now(),
      }, { merge: true });
      return { missing: false, changed: true, before };
    });
    if (outcome.missing) return fail0417(res, 404, "driver_not_found", "Agenda não encontrada.");
    await touchAdminSession0417(session);
    if (!outcome.changed) {
      return json0417(res, 200, {
        syncPolicy: outcome.before,
        changed: false,
        correlationId: "",
        requestId,
      });
    }
    const correlationId = crypto.randomUUID();
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      actorId: session.actorId,
      eventType: "SYNC_POLICY_CHANGED",
      correlationId,
      requestId,
      changes: [
        { field: "automatic", before: String(outcome.before.automatic), after: String(requested.automatic) },
        { field: "intervalMinutes", before: String(outcome.before.intervalMinutes), after: String(requested.intervalMinutes) },
      ],
    });
    await sendDriverBookingPush({
      driverUsername: session.driverUsername,
      event: "admin_sync_policy_changed",
      tripToken: "",
      correlationId,
    });
    return json0417(res, 200, {
      syncPolicy: requested,
      changed: true,
      correlationId,
      requestId,
    });
  }

  async function requestAdminSync0417(req, res, full) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const event = full ? "admin_full_reconcile" : "admin_update_now";
    const eventType = full ? "ADMIN_FULL_RECONCILE_REQUESTED" : "ADMIN_UPDATE_NOW_REQUESTED";
    const operationId = adminOperationId0417(req);
    const claim = await claimAdminCommand0417({
      session,
      eventType,
      operationId,
    });
    await touchAdminSession0417(session);
    if (!claim.created) {
      return json0417(res, 202, {
        accepted: true,
        replayed: true,
        correlationId: claim.correlationId,
        operation: event,
        requestId: claim.requestId,
      });
    }
    try {
      await sendDriverBookingPush({
        driverUsername: session.driverUsername,
        event,
        tripToken: "",
        correlationId: claim.correlationId,
      });
      await claim.ref.set({
        result: "REQUESTED",
        dispatchedAtMillis: Date.now(),
      }, { merge: true });
    } catch (error) {
      await claim.ref.set({
        result: "ERROR",
        dispatchFailedAtMillis: Date.now(),
      }, { merge: true }).catch(() => {});
      throw error;
    }
    return json0417(res, 202, {
      accepted: true,
      replayed: false,
      correlationId: claim.correlationId,
      operation: event,
      requestId: claim.requestId,
    });
  }

  async function getDriverAdminSyncPolicy0417(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver || !driver.username) return;
    const snap = await db.collection("tripDrivers").doc(driver.username).get();
    return json0417(res, 200, {
      syncPolicy: normalizeSyncPolicy0417(snap.exists && snap.data().adminSyncPolicy0417),
    });
  }

  async function reportDriverAdminSyncHealth0417(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver || !driver.username) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const failures = Math.max(0, Number(body.failures || 0));
    const pending = Math.max(0, Number(body.pending || 0));
    const divergent = Math.max(0, Number(body.divergent || 0));
    const readbackFailures = Math.max(0, Number(body.readbackFailures || 0));
    const requestedResult = clean0417(body.result, 40);
    const safeResult = requestedResult === "SUCCESS" && (failures || pending || divergent || readbackFailures)
      ? "INCOMPLETE"
      : requestedResult;
    const health = {
      startedAtMillis: Math.max(0, Number(body.startedAtMillis || 0)),
      finishedAtMillis: Math.max(0, Number(body.finishedAtMillis || Date.now())),
      result: safeResult,
      trigger: clean0417(body.trigger, 80),
      correlationId: clean0417(body.correlationId, 100),
      failures,
      changed: Math.max(0, Number(body.changed || 0)),
      skipped: Math.max(0, Number(body.skipped || 0)),
      pending,
      divergent,
      readbackFailures,
      appVersion: clean0417(body.appVersion, 40),
      updatedAtMillis: Date.now(),
    };
    await db.collection("tripDrivers").doc(driver.username).set({ adminSyncHealth0417: health }, { merge: true });
    return json0417(res, 200, { recorded: true });
  }

  async function recordDriverPublicAttestation0417(req, res, tripId) {
    const driver = await requireDriver(req, res);
    if (!driver || !driver.username) return;
    const ref = db.collection("trips").doc(clean0417(tripId, 120));
    const snap = await ref.get();
    if (!snap.exists) return fail0417(res, 404, "trip_not_found", "Viagem não encontrada.");
    const data = snap.data();
    if (clean0417(data.driverUsername, 40) !== driver.username) {
      return fail0417(res, 403, "trip_owner_mismatch", "Viagem pertence a outro motorista.");
    }
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const currentRevision = Math.max(0, Number(data.publicationRevision || 0));
    const requestedRevision = Math.max(0, Number(body.publicationRevision || 0));
    const currentCanonicalRevision = Math.max(0, Number(data.canonicalRevision || 0));
    const requestedCanonicalRevision = Math.max(0, Number(body.canonicalRevision || 0));
    const requestedState = clean0417(body.state, 24).toUpperCase();
    const readbackHash = clean0417(body.readbackHash, 160);
    const expectedHash = clean0417(body.expectedHash, 160);
    const canonicalHash = clean0417(data.canonicalStateHash, 160);
    const requestedCanonicalHash = clean0417(body.canonicalStateHash, 160);
    const verified = requestedState === "VERIFIED" &&
      requestedRevision > 0 &&
      requestedRevision === currentRevision &&
      requestedCanonicalRevision > 0 &&
      requestedCanonicalRevision === currentCanonicalRevision &&
      readbackHash &&
      expectedHash &&
      readbackHash === expectedHash &&
      (!requestedCanonicalHash || requestedCanonicalHash === canonicalHash);
    const state = verified
      ? "VERIFIED"
      : (requestedState === "DIVERGENT" || requestedState === "ERROR" ? "DIVERGENT" : "PENDING");
    const now = Date.now();
    await ref.set({
      publicAttestationState0417: state,
      publicAttestedPublicationRevision0417: verified ? currentRevision : 0,
      publicAttestedCanonicalRevision0417: verified ? requestedCanonicalRevision : 0,
      publicAttestedHash0417: verified ? readbackHash : "",
      publicAttestedAtMillis0417: verified ? now : 0,
      publicAttestationReason0417: clean0417(body.reason, 160),
      publicAttestationMismatchFields0417: Array.isArray(body.mismatchFields)
        ? body.mismatchFields.map((item) => clean0417(item, 80)).filter(Boolean).slice(0, 24)
        : [],
      publicAttestationCorrelationId0417: clean0417(body.correlationId, 100),
    }, { merge: true });
    return json0417(res, 200, { state, verified, publicationRevision: currentRevision, canonicalRevision: currentCanonicalRevision });
  }

  async function listAdminLogs0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const [debugSnap, auditSnap] = await Promise.all([
      db.collection("tripPublicDebugEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
      db.collection("tripChangeEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
    ]);
    const after = Math.max(0, Number(req.query && req.query.afterMillis || 0));
    const before = Math.max(after, Number(req.query && req.query.beforeMillis || Number.MAX_SAFE_INTEGER));
    const tripId = clean0417(req.query && req.query.tripId, 120);
    const errorsOnly = String(req.query && req.query.errorsOnly || "") === "true";
    const tripHash = tripId ? sha256Hex0417("trip:" + tripId).slice(0, 24) : "";
    const debug = debugSnap.docs.map((doc) => ({ id: doc.id, ...doc.data(), category: "DEBUG" }))
      .filter((event) => Number(event.createdAtMillis || 0) >= after && Number(event.createdAtMillis || 0) <= before)
      .filter((event) => !tripHash || clean0417(event.tripRefHash, 24) === tripHash)
      .filter((event) => !errorsOnly || /fail|error|diverg|invalid|stale/i.test(String(event.event || "") + " " + String(event.reason || "")));
    const audit = auditSnap.docs.map((doc) => ({ id: doc.id, ...doc.data(), category: "AUDIT" }))
      .filter((event) => Number(event.createdAtMillis || 0) >= after && Number(event.createdAtMillis || 0) <= before)
      .filter((event) => !tripId || clean0417(event.tripId, 120) === tripId)
      .filter((event) => !errorsOnly || /fail|error|reject|denied|diverg/i.test(String(event.eventType || "") + " " + String(event.result || "")));
    const events = [...debug, ...audit]
      .sort((a, b) => Number(b.createdAtMillis || 0) - Number(a.createdAtMillis || 0))
      .slice(0, 500)
      .map(redact0417);
    return json0417(res, 200, { events });
  }

  async function getAdminTripHistory0417(req, res, tripId) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const tripSnap = await db.collection("trips").doc(clean0417(tripId, 120)).get();
    if (!tripSnap.exists || clean0417(tripSnap.data().driverUsername, 40) !== session.driverUsername) {
      return fail0417(res, 404, "trip_not_found", "Viagem não encontrada.");
    }
    return listAdminLogs0417WithSession0417(req, res, session, clean0417(tripId, 120));
  }

  async function listAdminLogs0417WithSession0417(req, res, session, forcedTripId = "") {
    const [debugSnap, auditSnap] = await Promise.all([
      db.collection("tripPublicDebugEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
      db.collection("tripChangeEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
    ]);
    const tripId = forcedTripId || clean0417(req.query && req.query.tripId, 120);
    const tripHash = tripId ? sha256Hex0417("trip:" + tripId).slice(0, 24) : "";
    const events = [
      ...debugSnap.docs.map((doc) => ({ id: doc.id, ...doc.data(), category: "DEBUG" }))
        .filter((event) => !tripHash || clean0417(event.tripRefHash, 24) === tripHash),
      ...auditSnap.docs.map((doc) => ({ id: doc.id, ...doc.data(), category: "AUDIT" }))
        .filter((event) => !tripId || clean0417(event.tripId, 120) === tripId),
    ].sort((a, b) => Number(a.createdAtMillis || 0) - Number(b.createdAtMillis || 0)).map(redact0417);
    return json0417(res, 200, { events });
  }

  async function exportAdminLogs0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const [debugSnap, auditSnap] = await Promise.all([
      db.collection("tripPublicDebugEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
      db.collection("tripChangeEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
    ]);
    const payload = redact0417({
      schemaVersion: "rota-certa-admin-log-export-v1",
      driverUsername: session.driverUsername,
      exportedAtMillis: Date.now(),
      debug: debugSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() })),
      audit: auditSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() })),
    });
    res.status(200);
    res.set("Content-Type", "application/json; charset=utf-8");
    res.set("Cache-Control", "no-store");
    res.set("Content-Disposition", 'attachment; filename="rota-certa-logs.json"');
    res.send(JSON.stringify(payload, null, 2));
  }

  async function listAdminSessions0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const query = session.passengerId
      ? db.collection("passengerSessions").where("passengerId", "==", session.passengerId).limit(100)
      : db.collection("passengerSessions").where("contactHash", "==", session.contactHash).limit(100);
    const snap = await query.get();
    const now = Date.now();
    const expired = snap.docs.filter((doc) => Number(doc.data().expiresAtMillis || 0) <= now);
    if (expired.length) {
      const batch = db.batch();
      expired.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
    const active = snap.docs.filter((doc) => Number(doc.data().expiresAtMillis || 0) > now);
    return json0417(res, 200, {
      sessions: active.map((doc) => {
        const data = doc.data();
        const contextHash = clean0417(data.sessionContextHash, 80);
        return {
          id: sha256Hex0417(doc.id).slice(0, 16),
          actorId: session.actorId,
          createdAtMillis: Number(data.createdAtMillis || 0),
          lastActivityAtMillis: Number(data.lastActivityAtMillis || data.createdAtMillis || 0),
          expiresAtMillis: Number(data.expiresAtMillis || 0),
          current: doc.id === session.sessionRefId,
          sameContext: Boolean(session.sessionContextHash && contextHash === session.sessionContextHash),
          legacyContext: !contextHash,
        };
      }).sort((a, b) => b.lastActivityAtMillis - a.lastActivityAtMillis),
    });
  }

  return {
    getAdminMe0417,
    getAdminOverview0417,
    listAdminTrips0417,
    getAdminSettings0417,
    updateAdminPublicSettings0417,
    updateAdminSyncSettings0417,
    requestAdminUpdateNow0417: (req, res) => requestAdminSync0417(req, res, false),
    requestAdminFullReconcile0417: (req, res) => requestAdminSync0417(req, res, true),
    getDriverAdminSyncPolicy0417,
    reportDriverAdminSyncHealth0417,
    recordDriverPublicAttestation0417,
    listAdminLogs0417,
    getAdminTripHistory0417,
    exportAdminLogs0417,
    listAdminSessions0417,
  };
}

module.exports = {
  createAgendaAdmin0417,
  normalizeProfileScope0417,
  normalizeSyncPolicy0417,
  sameSyncPolicy0417,
  authenticationRequired0417,
  safeVisibility0417,
  redact0417,
  activeAdminTrips0417,
  validatedBlaBlaPublicUrl0417,
};
