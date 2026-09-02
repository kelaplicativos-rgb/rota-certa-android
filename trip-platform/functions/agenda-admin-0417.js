"use strict";

const crypto = require("crypto");

const ADMIN_SESSION_MILLIS_0417 = 12 * 60 * 60 * 1000;
const ADMIN_AUDIT_RETENTION_MILLIS_0417 = 30 * 24 * 60 * 60 * 1000;
const ADMIN_RATE_WINDOW_MILLIS_0417 = 60 * 1000;
const ADMIN_RATE_LIMIT_0417 = 6;

function sha256Hex0417(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function clean0417(value, max = 240) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function normalizedContactDigits0417(value) {
  return String(value || "").replace(/\D/g, "").slice(0, 18);
}

function safeEqual0417(a, b) {
  if (!a || !b) return false;
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function passwordDigest0417(password, salt) {
  return crypto.scryptSync(String(password || ""), String(salt || ""), 64).toString("hex");
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

function createAgendaAdmin0417({
  db,
  resolveDriverUsername,
  requireDriver,
  sendDriverBookingPush,
}) {
  async function enforceAdminRateLimit0417(req, identity = "") {
    const ip = clean0417((req.get("x-forwarded-for") || "").split(",")[0] || req.ip || "unknown", 96);
    const minute = Math.floor(Date.now() / ADMIN_RATE_WINDOW_MILLIS_0417);
    const ref = db.collection("tripAdminRateLimits").doc(
      sha256Hex0417(`${ip}|${clean0417(identity, 80)}|${minute}`),
    );
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const count = snap.exists ? Number(snap.data().count || 0) : 0;
      if (count >= ADMIN_RATE_LIMIT_0417) {
        const error = new Error("Muitas tentativas. Aguarde um minuto.");
        error.httpStatus = 429;
        error.code = "admin_rate_limited";
        throw error;
      }
      tx.set(ref, {
        count: count + 1,
        expiresAtMillis: Date.now() + 5 * ADMIN_RATE_WINDOW_MILLIS_0417,
      }, { merge: true });
    });
  }

  async function appendAdminAudit0417({
    driverUsername,
    eventType,
    actorId = "",
    correlationId = "",
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
      result: clean0417(result, 24),
      createdAtMillis: now,
      expiresAtMillis: now + ADMIN_AUDIT_RETENTION_MILLIS_0417,
      changes: safeChanges,
      affectedPassengerIds: [],
    });
  }

  async function createAdminSession0417(req, res) {
    const driverRaw = clean0417(req.body && req.body.driverUsername, 80);
    try {
      await enforceAdminRateLimit0417(req, driverRaw);
    } catch (error) {
      return fail0417(res, error.httpStatus || 429, error.code || "admin_rate_limited", error.message);
    }
    const resolved = await resolveDriverUsername(driverRaw);
    if (!resolved || !resolved.driverSnap || !resolved.driverSnap.exists) {
      return fail0417(res, 401, "admin_invalid_credentials", "WhatsApp ou senha inválidos.");
    }
    const driver = resolved.driverSnap.data();
    const storedContact = clean0417(driver.driverWhatsapp, 40);
    const suppliedContact = clean0417(req.body && req.body.contact, 40);
    const password = String(req.body && req.body.password || "");
    if (
      !storedContact ||
      !normalizedContactDigits0417(suppliedContact) ||
      normalizedContactDigits0417(storedContact) !== normalizedContactDigits0417(suppliedContact) ||
      password.length < 8 || password.length > 72
    ) {
      await appendAdminAudit0417({
        driverUsername: resolved.canonicalUsername,
        eventType: "ADMIN_LOGIN_REJECTED",
        actorId: sha256Hex0417(normalizedContactDigits0417(suppliedContact)).slice(0, 24),
        result: "DENIED",
      }).catch(() => {});
      return fail0417(res, 401, "admin_invalid_credentials", "WhatsApp ou senha inválidos.");
    }
    const accountSnap = await db.collection("passengerAccounts").doc(sha256Hex0417(storedContact)).get();
    if (!accountSnap.exists) {
      return fail0417(res, 401, "admin_password_not_configured", "Defina primeiro a senha da Administração no aplicativo Rota Certa.");
    }
    const account = accountSnap.data();
    const salt = clean0417(account.passwordSalt, 80);
    const expected = clean0417(account.passwordHash, 256);
    const supplied = salt ? passwordDigest0417(password, salt) : "";
    if (!safeEqual0417(supplied, expected)) {
      await appendAdminAudit0417({
        driverUsername: resolved.canonicalUsername,
        eventType: "ADMIN_LOGIN_REJECTED",
        actorId: sha256Hex0417(storedContact).slice(0, 24),
        result: "DENIED",
      }).catch(() => {});
      return fail0417(res, 401, "admin_invalid_credentials", "WhatsApp ou senha inválidos.");
    }
    const token = crypto.randomBytes(32).toString("base64url");
    const tokenHash = sha256Hex0417(token);
    const now = Date.now();
    const expiresAtMillis = now + ADMIN_SESSION_MILLIS_0417;
    const actorId = sha256Hex0417(storedContact).slice(0, 24);
    await db.collection("tripAdminSessions").doc(tokenHash).set({
      driverUsername: resolved.canonicalUsername,
      actorId,
      contactHash: sha256Hex0417(storedContact),
      createdAtMillis: now,
      lastActivityAtMillis: now,
      expiresAtMillis,
      revokedAtMillis: 0,
    });
    await appendAdminAudit0417({
      driverUsername: resolved.canonicalUsername,
      eventType: "ADMIN_LOGIN",
      actorId,
      result: "SUCCESS",
    }).catch(() => {});
    return json0417(res, 200, {
      sessionToken: token,
      expiresAtMillis,
      driverUsername: resolved.publicUsername || resolved.canonicalUsername,
    });
  }

  async function requireAdminSession0417(req, res) {
    const token = clean0417(req.get("X-Rota-Certa-Admin-Session"), 400);
    if (!/^[A-Za-z0-9_-]{32,200}$/.test(token)) {
      fail0417(res, 401, "admin_auth_required", "Entre como administrador.");
      return null;
    }
    const ref = db.collection("tripAdminSessions").doc(sha256Hex0417(token));
    const snap = await ref.get();
    if (!snap.exists) {
      fail0417(res, 401, "admin_session_invalid", "Sessão administrativa inválida.");
      return null;
    }
    const data = snap.data();
    const now = Date.now();
    if (Number(data.revokedAtMillis || 0) > 0 || Number(data.expiresAtMillis || 0) <= now) {
      await ref.delete().catch(() => {});
      if (Number(data.expiresAtMillis || 0) <= now) {
        await appendAdminAudit0417({
          driverUsername: clean0417(data.driverUsername, 40),
          eventType: "ADMIN_SESSION_EXPIRED",
          actorId: clean0417(data.actorId, 80),
          result: "EXPIRED",
        }).catch(() => {});
      }
      fail0417(res, 401, "admin_session_expired", "Sessão administrativa expirada.");
      return null;
    }
    if (now - Number(data.lastActivityAtMillis || 0) > 60 * 1000) {
      await ref.set({ lastActivityAtMillis: now }, { merge: true }).catch(() => {});
    }
    return {
      ref,
      driverUsername: clean0417(data.driverUsername, 40),
      actorId: clean0417(data.actorId, 80),
      createdAtMillis: Number(data.createdAtMillis || 0),
      expiresAtMillis: Number(data.expiresAtMillis || 0),
    };
  }

  async function logoutAdmin0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    await session.ref.delete();
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      eventType: "ADMIN_LOGOUT",
      actorId: session.actorId,
    }).catch(() => {});
    return json0417(res, 200, { loggedOut: true });
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

  async function setDriverAdminPassword0417(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver || !driver.username) return;
    const password = String(req.body && req.body.password || "");
    if (password.length < 8 || password.length > 72) {
      return fail0417(res, 400, "invalid_admin_password", "A senha precisa ter entre 8 e 72 caracteres.");
    }
    const driverSnap = await db.collection("tripDrivers").doc(driver.username).get();
    const storedContact = driverSnap.exists ? clean0417(driverSnap.data().driverWhatsapp, 40) : "";
    if (!storedContact) {
      return fail0417(res, 409, "admin_contact_required", "Configure primeiro o WhatsApp do proprietário da Agenda.");
    }
    const accountRef = db.collection("passengerAccounts").doc(sha256Hex0417(storedContact));
    const accountSnap = await accountRef.get();
    const now = Date.now();
    const salt = crypto.randomBytes(16).toString("hex");
    await accountRef.set({
      passengerContact: storedContact,
      passwordSalt: salt,
      passwordHash: passwordDigest0417(password, salt),
      adminCredentialOwner0417: true,
      mustChangePassword: false,
      createdAtMillis: Number(accountSnap.exists && accountSnap.data().createdAtMillis || now),
      updatedAtMillis: now,
    }, { merge: true });
    const sessions = await db.collection("tripAdminSessions").where("driverUsername", "==", driver.username).limit(100).get();
    await Promise.allSettled(sessions.docs.map((doc) => doc.ref.delete()));
    await appendAdminAudit0417({
      driverUsername: driver.username,
      eventType: "ADMIN_PASSWORD_CHANGED",
      actorId: "driver-app",
      source: "ROTA_CERTA_ANDROID",
    }).catch(() => {});
    return json0417(res, 200, { configured: true });
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
    return {
      remoteTripId: trip.id,
      canonicalTripId: clean0417(trip.canonicalTripId || trip.localTripId, 180),
      blablaProfileUuid: clean0417(trip.blablaProfileUuid, 160),
      blablaTripId: clean0417(trip.blablaTripId, 160),
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
      blablaPublicUrl: clean0417(trip.blablaPublicUrl, 1200),
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
    const now = Date.now();
    const active = trips.filter((trip) =>
      Number(trip.departureAtMillis || 0) > now &&
      ["PUBLISHED", "FULL", "STARTING", "ACTIVE"].includes(clean0417(trip.status, 24)),
    );
    const counts = { verified: 0, pending: 0, divergent: 0, unproven: 0, linksValid: 0, linksPending: 0 };
    active.forEach((trip) => {
      const state = clean0417(trip.publicAttestationState0417, 24);
      if (state === "VERIFIED") counts.verified++;
      else if (state === "PENDING") counts.pending++;
      else if (state === "DIVERGENT" || state === "ERROR") counts.divergent++;
      else counts.unproven++;
      if (!clean0417(trip.blablaTripId, 160)) return;
      if (/^https:\/\//i.test(clean0417(trip.blablaPublicUrl, 1200))) counts.linksValid++;
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
    return json0417(res, 200, {
      trips: trips
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
    await ref.set({
      publicVisibility0417: visibility,
      publicTripProfileUuids0417: profiles,
      updatedAtMillis: Date.now(),
    }, { merge: true });
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      actorId: session.actorId,
      eventType: "PUBLIC_VISIBILITY_CHANGED",
      changes: [
        { field: "publicVisibility", before: JSON.stringify(safeVisibility0417(before.publicVisibility0417)), after: JSON.stringify(visibility) },
        { field: "publicProfileUuids", before: JSON.stringify(normalizeProfileScope0417(before.publicTripProfileUuids0417)), after: JSON.stringify(profiles) },
      ],
    });
    return json0417(res, 200, { publicVisibility: visibility, publicProfileUuids: profiles });
  }

  async function updateAdminSyncSettings0417(req, res) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const ref = db.collection("tripDrivers").doc(session.driverUsername);
    const snap = await ref.get();
    if (!snap.exists) return fail0417(res, 404, "driver_not_found", "Agenda não encontrada.");
    const before = normalizeSyncPolicy0417(snap.data().adminSyncPolicy0417);
    const policy = normalizeSyncPolicy0417(req.body);
    const correlationId = crypto.randomUUID();
    await ref.set({ adminSyncPolicy0417: policy, updatedAtMillis: Date.now() }, { merge: true });
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      actorId: session.actorId,
      eventType: "SYNC_POLICY_CHANGED",
      correlationId,
      changes: [
        { field: "automatic", before: String(before.automatic), after: String(policy.automatic) },
        { field: "intervalMinutes", before: String(before.intervalMinutes), after: String(policy.intervalMinutes) },
      ],
    });
    await sendDriverBookingPush({
      driverUsername: session.driverUsername,
      event: "admin_sync_policy_changed",
      tripToken: "",
      correlationId,
    });
    return json0417(res, 200, { syncPolicy: policy, correlationId });
  }

  async function requestAdminSync0417(req, res, full) {
    const session = await requireAdminSession0417(req, res);
    if (!session) return;
    const correlationId = crypto.randomUUID();
    const event = full ? "admin_full_reconcile" : "admin_update_now";
    await appendAdminAudit0417({
      driverUsername: session.driverUsername,
      actorId: session.actorId,
      eventType: full ? "ADMIN_FULL_RECONCILE_REQUESTED" : "ADMIN_UPDATE_NOW_REQUESTED",
      correlationId,
    });
    await sendDriverBookingPush({
      driverUsername: session.driverUsername,
      event,
      tripToken: "",
      correlationId,
    });
    return json0417(res, 202, { accepted: true, correlationId, operation: event });
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
    const health = {
      startedAtMillis: Math.max(0, Number(body.startedAtMillis || 0)),
      finishedAtMillis: Math.max(0, Number(body.finishedAtMillis || Date.now())),
      result: clean0417(body.result, 40),
      trigger: clean0417(body.trigger, 80),
      correlationId: clean0417(body.correlationId, 100),
      failures: Math.max(0, Number(body.failures || 0)),
      changed: Math.max(0, Number(body.changed || 0)),
      skipped: Math.max(0, Number(body.skipped || 0)),
      pending: Math.max(0, Number(body.pending || 0)),
      divergent: Math.max(0, Number(body.divergent || 0)),
      readbackFailures: Math.max(0, Number(body.readbackFailures || 0)),
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
    const requestedState = clean0417(body.state, 24).toUpperCase();
    const readbackHash = clean0417(body.readbackHash, 160);
    const expectedHash = clean0417(body.expectedHash, 160);
    const canonicalHash = clean0417(data.canonicalStateHash, 160);
    const requestedCanonicalHash = clean0417(body.canonicalStateHash, 160);
    const verified = requestedState === "VERIFIED" &&
      requestedRevision > 0 &&
      requestedRevision === currentRevision &&
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
      publicAttestedCanonicalRevision0417: verified ? Math.max(0, Number(body.canonicalRevision || 0)) : 0,
      publicAttestedHash0417: verified ? readbackHash : "",
      publicAttestedAtMillis0417: verified ? now : 0,
      publicAttestationReason0417: clean0417(body.reason, 160),
      publicAttestationMismatchFields0417: Array.isArray(body.mismatchFields)
        ? body.mismatchFields.map((item) => clean0417(item, 80)).filter(Boolean).slice(0, 24)
        : [],
      publicAttestationCorrelationId0417: clean0417(body.correlationId, 100),
    }, { merge: true });
    return json0417(res, 200, { state, verified, publicationRevision: currentRevision });
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
    req.query = { ...(req.query || {}), tripId: clean0417(tripId, 120) };
    return listAdminLogs0417WithSession0417(req, res, session);
  }

  async function listAdminLogs0417WithSession0417(req, res, session) {
    const [debugSnap, auditSnap] = await Promise.all([
      db.collection("tripPublicDebugEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
      db.collection("tripChangeEvents").where("driverUsername", "==", session.driverUsername).limit(500).get(),
    ]);
    const tripId = clean0417(req.query && req.query.tripId, 120);
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
    const snap = await db.collection("tripAdminSessions").where("driverUsername", "==", session.driverUsername).limit(100).get();
    return json0417(res, 200, {
      sessions: snap.docs.map((doc) => {
        const data = doc.data();
        return {
          id: sha256Hex0417(doc.id).slice(0, 16),
          actorId: clean0417(data.actorId, 80),
          createdAtMillis: Number(data.createdAtMillis || 0),
          lastActivityAtMillis: Number(data.lastActivityAtMillis || 0),
          expiresAtMillis: Number(data.expiresAtMillis || 0),
          current: doc.id === session.ref.id,
        };
      }).sort((a, b) => b.lastActivityAtMillis - a.lastActivityAtMillis),
    });
  }

  return {
    createAdminSession0417,
    logoutAdmin0417,
    getAdminMe0417,
    setDriverAdminPassword0417,
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
  safeVisibility0417,
  redact0417,
};
