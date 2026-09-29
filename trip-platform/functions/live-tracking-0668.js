"use strict";

const crypto = require("crypto");

const FAMILY_PERSISTENT_EXPIRY_MILLIS_0680 = 253402300799000;
const MAX_PASSENGER_MILLIS_0668 = 24 * 60 * 60 * 1000;
const MAX_POINT_BATCH_0668 = 100;
const MAX_PUBLIC_POINTS_0668 = 5000;
const PASSENGER_DESTINATION_RADIUS_METERS_0691 = 140;
const PASSENGER_MIN_ACTIVE_MILLIS_0691 = 60 * 1000;
const PASSENGER_ARRIVAL_REQUIRED_HITS_0691 = 3;
const PASSENGER_ARRIVAL_MIN_DWELL_MILLIS_0691 = 8 * 1000;
const PASSENGER_ARRIVAL_FALLBACK_DWELL_MILLIS_0691 = 15 * 1000;
const PASSENGER_ARRIVAL_MAX_SPEED_MPS_0691 = 8.5;
const PASSENGER_ARRIVAL_MAX_GAP_MILLIS_0691 = 20 * 1000;
const FAMILY_ACCESS_SESSION_MILLIS_0681 = 365 * 24 * 60 * 60 * 1000;
const FAMILY_AUTH_WINDOW_MILLIS_0681 = 10 * 60 * 1000;
const FAMILY_AUTH_MAX_FAILURES_0681 = 5;

function sha256Hex0668(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function cleanText0668(value, max = 160) {
  return String(value || "").trim().replace(/[\u0000-\u001f\u007f]/g, " ").slice(0, max);
}

function finiteNumber0668(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function validCoordinate0668(lat, lon) {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
}

function distanceMeters0668(lat1, lon1, lat2, lon2) {
  const toRad = (value) => value * Math.PI / 180;
  const earth = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function continuousTrackingPoints0670(points) {
  const seen = new Set();
  return (Array.isArray(points) ? points : [])
    .map(normalizePoint0668)
    .filter(Boolean)
    .sort((a, b) => a.recordedAtMillis - b.recordedAtMillis)
    .filter((point) => {
      const key = [point.recordedAtMillis, point.latitude.toFixed(7), point.longitude.toFixed(7)].join("|");
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

function trackingQueryFloor0670(share, session, sinceMillis) {
  const privacyFloor = share && share.scope === "PASSENGER"
    ? Number(share.createdAtMillis || 0)
    : Number((session && session.startedAtMillis) || 0);
  const requestedSince = Number(sinceMillis || 0);
  return Math.max(privacyFloor, Number.isFinite(requestedSince) ? Math.trunc(requestedSince) : 0);
}

function publicTrackingPoints0668(points, share) {
  const floor = share.scope === "PASSENGER" ? Number(share.createdAtMillis || 0) : Number(share.sessionStartedAtMillis || 0);
  return (Array.isArray(points) ? points : [])
    .filter((point) => Number(point.recordedAtMillis || 0) >= floor)
    .sort((a, b) => Number(a.recordedAtMillis || 0) - Number(b.recordedAtMillis || 0))
    .map((point) => ({
      latitude: Number(point.latitude),
      longitude: Number(point.longitude),
      recordedAtMillis: Number(point.recordedAtMillis || 0),
      accuracyMeters: point.accuracyMeters == null ? null : Number(point.accuracyMeters),
      speedMetersPerSecond: point.speedMetersPerSecond == null ? null : Number(point.speedMetersPerSecond),
    }));
}

function passengerDistanceToDestinationMeters0669(current, share) {
  if (!current || !share || share.scope !== "PASSENGER") return null;
  const destinationLatitude = finiteNumber0668(share.destinationLatitude);
  const destinationLongitude = finiteNumber0668(share.destinationLongitude);
  const latitude = finiteNumber0668(current.latitude);
  const longitude = finiteNumber0668(current.longitude);
  if (!validCoordinate0668(latitude, longitude) || !validCoordinate0668(destinationLatitude, destinationLongitude)) return null;
  return Math.round(distanceMeters0668(latitude, longitude, destinationLatitude, destinationLongitude));
}

function publicTrackerTelemetry0670(session, share, current, nowMillis = Date.now()) {
  const heartbeatAt = Number(
    session && (
      session.lastDeviceHeartbeatAtMillis ||
      session.updatedAtMillis ||
      session.latestPointAtMillis ||
      0
    )
  );
  const currentGpsAt = current ? Number(current.recordedAtMillis || 0) : 0;
  const gpsAt = share && share.scope === "PASSENGER"
    ? currentGpsAt
    : Number((session && session.lastGpsAtMillis) || currentGpsAt || 0);
  const heartbeatAgeMillis = heartbeatAt > 0 ? Math.max(0, nowMillis - heartbeatAt) : null;
  const gpsAgeMillis = gpsAt > 0 ? Math.max(0, nowMillis - gpsAt) : null;
  const deviceState = heartbeatAgeMillis == null
    ? "WAITING"
    : heartbeatAgeMillis <= 20_000
      ? "CONNECTED"
      : heartbeatAgeMillis <= 60_000
        ? "DELAYED"
        : "OFFLINE";
  const gpsState = gpsAgeMillis == null
    ? "WAITING"
    : gpsAgeMillis <= 20_000
      ? "FRESH"
      : gpsAgeMillis <= 60_000
        ? "STALE"
        : "OLD";
  return {
    lastDeviceHeartbeatAtMillis: heartbeatAt > 0 ? heartbeatAt : 0,
    lastGpsAtMillis: gpsAt > 0 ? gpsAt : 0,
    heartbeatAgeMillis,
    gpsAgeMillis,
    deviceState,
    gpsState,
  };
}

function passengerArrivalProgress0691(share, latest, nowMillis = Date.now()) {
  const reset = (distanceMeters = null) => ({
    shouldClose:false,
    distanceMeters,
    arrivalHitCount0691:0,
    arrivalCandidateSinceMillis0691:0,
    arrivalLastHitAtMillis0691:0,
  });
  if (!share || share.scope !== "PASSENGER" || !share.active || !latest) return reset();
  const created = Number(share.createdAtMillis || 0);
  const latestRecordedAt = Number(latest.recordedAtMillis || 0);
  if (created <= 0 || latestRecordedAt < created) return reset();
  if (nowMillis - created < PASSENGER_MIN_ACTIVE_MILLIS_0691) return reset();

  const destLat = finiteNumber0668(share.destinationLatitude);
  const destLon = finiteNumber0668(share.destinationLongitude);
  const lat = finiteNumber0668(latest.latitude);
  const lon = finiteNumber0668(latest.longitude);
  if (!validCoordinate0668(destLat, destLon) || !validCoordinate0668(lat, lon)) return reset();

  const accuracy = finiteNumber0668(latest.accuracyMeters);
  if (accuracy != null && (accuracy <= 0 || accuracy > 80)) return reset();

  const distanceMeters = Math.round(distanceMeters0668(lat, lon, destLat, destLon));
  if (distanceMeters > PASSENGER_DESTINATION_RADIUS_METERS_0691) return reset(distanceMeters);

  const previousHitAt = Number(share.arrivalLastHitAtMillis0691 || 0);
  const previousCandidateSince = Number(share.arrivalCandidateSinceMillis0691 || 0);
  const previousHitCount = Math.max(0, Math.trunc(Number(share.arrivalHitCount0691 || 0)));
  const consecutive = previousHitAt > 0 &&
    latestRecordedAt > previousHitAt &&
    latestRecordedAt - previousHitAt <= PASSENGER_ARRIVAL_MAX_GAP_MILLIS_0691;
  const arrivalHitCount0691 = consecutive
    ? Math.min(PASSENGER_ARRIVAL_REQUIRED_HITS_0691, previousHitCount + 1)
    : 1;
  const arrivalCandidateSinceMillis0691 = consecutive && previousCandidateSince > 0
    ? previousCandidateSince
    : latestRecordedAt;
  const arrivalLastHitAtMillis0691 = latestRecordedAt;
  const dwellMillis = Math.max(0, latestRecordedAt - arrivalCandidateSinceMillis0691);
  const speed = finiteNumber0668(latest.speedMetersPerSecond);
  const speedCompatible = speed == null
    ? dwellMillis >= PASSENGER_ARRIVAL_FALLBACK_DWELL_MILLIS_0691
    : speed <= PASSENGER_ARRIVAL_MAX_SPEED_MPS_0691;
  const shouldClose = arrivalHitCount0691 >= PASSENGER_ARRIVAL_REQUIRED_HITS_0691 &&
    dwellMillis >= PASSENGER_ARRIVAL_MIN_DWELL_MILLIS_0691 &&
    speedCompatible;

  return {
    shouldClose,
    distanceMeters,
    arrivalHitCount0691,
    arrivalCandidateSinceMillis0691,
    arrivalLastHitAtMillis0691,
  };
}

function shouldClosePassengerShare0668(share, latest, nowMillis = Date.now()) {
  return passengerArrivalProgress0691(share, latest, nowMillis).shouldClose;
}
function trackingDriverKey0668(req, driver) {
  if (driver && driver.username) return "u:" + cleanText0668(driver.username, 80).toLowerCase();
  const supplied = req.get("X-Rota-Certa-Driver-Token") || "";
  return "legacy:" + sha256Hex0668(supplied).slice(0, 32);
}

function trackingSessionDocId0668(driverKey, sessionId) {
  return sha256Hex0668("session|" + driverKey + "|" + sessionId);
}

function trackingShareDocId0668(token) {
  return sha256Hex0668("tracking:" + token);
}

function normalizeFamilyUsername0681(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

function familyAliasDocId0681(username) {
  return normalizeFamilyUsername0681(username);
}

function familySessionDocId0681(token) {
  return sha256Hex0668("family-session|" + String(token || ""));
}

function familyPinHash0681(username, pin) {
  return sha256Hex0668("family-pin|" + normalizeFamilyUsername0681(username) + "|" + String(pin || ""));
}

function safeEqual0681(left, right) {
  const a = Buffer.from(String(left || ""));
  const b = Buffer.from(String(right || ""));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function trackingShareExpired0680(share, nowMillis = Date.now()) {
  if (!share || share.scope !== "PASSENGER") return false;
  return Number(share.expiresAtMillis || 0) <= nowMillis;
}

function trackingJson0668(res, status, body) {
  res.status(status);
  res.set("Content-Type", "application/json; charset=utf-8");
  res.set("Cache-Control", "no-store");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  return res.send(JSON.stringify(body));
}

function trackingFail0668(res, status, code, message) {
  return trackingJson0668(res, status, { error: code, message });
}

function normalizePoint0668(raw) {
  const latitude = finiteNumber0668(raw && raw.latitude);
  const longitude = finiteNumber0668(raw && raw.longitude);
  const recordedAtMillis = Math.trunc(Number(raw && raw.recordedAtMillis || 0));
  if (!validCoordinate0668(latitude, longitude) || recordedAtMillis <= 0) return null;
  const accuracy = finiteNumber0668(raw && raw.accuracyMeters);
  const speed = finiteNumber0668(raw && raw.speedMetersPerSecond);
  return {
    latitude,
    longitude,
    recordedAtMillis,
    accuracyMeters: accuracy == null ? null : Math.max(0, Math.min(10000, accuracy)),
    speedMetersPerSecond: speed == null ? null : Math.max(0, Math.min(120, speed)),
  };
}

function createLiveTracking0668({ db, requireDriver }) {
  async function requireTrackingDriver0668(req, res) {
    const driver = await requireDriver(req, res);
    if (!driver) return null;
    return { driver, driverKey: trackingDriverKey0668(req, driver) };
  }

  async function sessionForDriver0668(req, res, identity, sessionIdRaw) {
    const sessionId = cleanText0668(sessionIdRaw, 120);
    if (sessionId.length < 16) {
      trackingFail0668(res, 400, "tracking_session_invalid", "Sessão de rastreamento inválida.");
      return null;
    }
    const ref = db.collection("tripTrackingSessions").doc(trackingSessionDocId0668(identity.driverKey, sessionId));
    const snap = await ref.get();
    if (!snap.exists || snap.data().driverKey !== identity.driverKey) {
      trackingFail0668(res, 404, "tracking_session_not_found", "Sessão de rastreamento não encontrada.");
      return null;
    }
    return { ref, snap, data: snap.data(), sessionId };
  }

  async function applyPassengerArrival0691(sessionDocId, latest, nowMillis) {
    if (!latest) return 0;
    const shares = await db.collection("tripTrackingShares")
      .where("sessionDocId", "==", sessionDocId)
      .limit(100)
      .get();
    const activePassengerShares = shares.docs.filter((doc) => {
      const data = doc.data();
      return data.active && data.scope === "PASSENGER";
    });
    if (!activePassengerShares.length) return 0;

    const batch = db.batch();
    let closed = 0;
    for (const doc of activePassengerShares) {
      const progress = passengerArrivalProgress0691(doc.data(), latest, nowMillis);
      const update = {
        arrivalHitCount0691:progress.arrivalHitCount0691,
        arrivalCandidateSinceMillis0691:progress.arrivalCandidateSinceMillis0691,
        arrivalLastHitAtMillis0691:progress.arrivalLastHitAtMillis0691,
        arrivalDistanceMeters0691:progress.distanceMeters,
        updatedAtMillis:nowMillis,
      };
      if (progress.shouldClose) {
        update.active = false;
        update.endedAtMillis = nowMillis;
        update.endReason = "DESTINATION_REACHED";
        update.arrivalConfirmedAtMillis0691 = Number(latest.recordedAtMillis || nowMillis);
        closed += 1;
      }
      batch.set(doc.ref, update, { merge:true });
    }
    await batch.commit();
    return closed;
  }

  async function createSession(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const sessionId = cleanText0668(body.sessionId, 120);
    const startedAtMillis = Math.trunc(Number(body.startedAtMillis || 0));
    const now = Date.now();
    if (sessionId.length < 16 || startedAtMillis <= 0 || startedAtMillis > now + 5 * 60 * 1000) {
      return trackingFail0668(res, 400, "tracking_session_invalid", "Sessão de rastreamento inválida.");
    }
    const docId = trackingSessionDocId0668(identity.driverKey, sessionId);
    const ref = db.collection("tripTrackingSessions").doc(docId);
    const existing = await ref.get();
    if (existing.exists && existing.data().driverKey !== identity.driverKey) {
      return trackingFail0668(res, 403, "tracking_session_owner_mismatch", "Sessão pertence a outro motorista.");
    }
    const expiresAtMillis = FAMILY_PERSISTENT_EXPIRY_MILLIS_0680;
    await ref.set({
      driverKey: identity.driverKey,
      driverUsername: identity.driver.username || "",
      driverDisplayName: cleanText0668(identity.driver.displayName, 120),
      clientSessionIdHash: sha256Hex0668(sessionId),
      startedAtMillis,
      createdAtMillis: existing.exists ? Number(existing.data().createdAtMillis || now) : now,
      updatedAtMillis: now,
      expiresAtMillis,
      active: true,
    }, { merge: true });
    return trackingJson0668(res, 200, { ok: true, acceptedThroughMillis: 0 });
  }

  async function createShare(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const token = cleanText0668(body.token, 180);
    if (!/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      return trackingFail0668(res, 400, "tracking_share_token_invalid", "Token de compartilhamento inválido.");
    }
    const selected = await sessionForDriver0668(req, res, identity, body.sessionId);
    if (!selected) return;
    const scope = cleanText0668(body.scope, 20).toUpperCase();
    if (!["FAMILY", "PASSENGER"].includes(scope)) {
      return trackingFail0668(res, 400, "tracking_share_scope_invalid", "Escopo de rastreamento inválido.");
    }
    const now = Date.now();
    if (!selected.data.active) {
      return trackingFail0668(res, 409, "tracking_session_closed", "Sessão de rastreamento encerrada.");
    }
    const createdAtMillis = Math.max(Number(selected.data.startedAtMillis || now), Math.trunc(Number(body.createdAtMillis || now)));
    const requestedExpiry = Math.trunc(Number(body.expiresAtMillis || 0));
    const scopeMax = now + MAX_PASSENGER_MILLIS_0668;
    const expiresAtMillis = scope === "FAMILY"
      ? FAMILY_PERSISTENT_EXPIRY_MILLIS_0680
      : Math.min(requestedExpiry > now ? requestedExpiry : scopeMax, scopeMax);
    const destinationLatitude = finiteNumber0668(body.destinationLatitude);
    const destinationLongitude = finiteNumber0668(body.destinationLongitude);
    if (scope === "PASSENGER" && !validCoordinate0668(destinationLatitude, destinationLongitude)) {
      return trackingFail0668(res, 400, "tracking_destination_required", "Destino exato do passageiro é obrigatório.");
    }
    const familyUsername0681 = scope === "FAMILY"
      ? normalizeFamilyUsername0681(
          (identity.driver && (identity.driver.publicUsername || identity.driver.username)) ||
          body.driverUsername
        )
      : "";
    const familyPin0681 = scope === "FAMILY" ? cleanText0668(body.familyPin, 12) : "";
    if (scope === "FAMILY" && !familyUsername0681) {
      return trackingFail0668(res, 400, "tracking_family_username_required", "Identidade pública do motorista é obrigatória.");
    }
    if (scope === "FAMILY" && !/^\d{6}$/.test(familyPin0681)) {
      return trackingFail0668(res, 400, "tracking_family_pin_invalid", "Código familiar deve ter 6 dígitos.");
    }

    const shareRef = db.collection("tripTrackingShares").doc(trackingShareDocId0668(token));
    const existing = await shareRef.get();
    if (existing.exists && existing.data().driverKey !== identity.driverKey) {
      return trackingFail0668(res, 409, "tracking_share_collision", "Não foi possível criar o link.");
    }
    await shareRef.set({
      driverKey: identity.driverKey,
      sessionDocId: selected.ref.id,
      scope,
      active: true,
      createdAtMillis,
      expiresAtMillis,
      passengerKeyHash: scope === "PASSENGER" ? sha256Hex0668(cleanText0668(body.passengerKey, 220)) : "",
      passengerName: scope === "PASSENGER" ? cleanText0668(body.passengerName, 120) : "",
      tripIdHash: scope === "PASSENGER" ? sha256Hex0668(cleanText0668(body.tripId, 220)) : "",
      destinationLatitude: scope === "PASSENGER" ? destinationLatitude : null,
      destinationLongitude: scope === "PASSENGER" ? destinationLongitude : null,
      destinationLabel: scope === "PASSENGER" ? cleanText0668(body.destinationLabel, 220) : "",
      familyUsername0681,
      updatedAtMillis: now,
    }, { merge: true });
    if (scope === "FAMILY") {
      await db.collection("tripTrackingFamilyAliases").doc(familyAliasDocId0681(familyUsername0681)).set({
        driverKey: identity.driverKey,
        driverUsername: familyUsername0681,
        shareDocId: shareRef.id,
        active: true,
        pinHash: familyPinHash0681(familyUsername0681, familyPin0681),
        createdAtMillis: existing.exists ? Number(existing.data().createdAtMillis || now) : now,
        updatedAtMillis: now,
      }, { merge: true });
    }
    return trackingJson0668(res, 200, {
      ok: true,
      acceptedThroughMillis: 0,
      familyUsername: scope === "FAMILY" ? familyUsername0681 : "",
    });
  }

  async function postPoints(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const selected = await sessionForDriver0668(req, res, identity, body.sessionId);
    if (!selected) return;
    if (!selected.data.active) {
      return trackingFail0668(res, 409, "tracking_session_closed", "Sessão de rastreamento encerrada.");
    }

    const rawPoints = Array.isArray(body.points) ? body.points : [];
    if (!rawPoints.length || rawPoints.length > MAX_POINT_BATCH_0668) {
      return trackingFail0668(res, 400, "tracking_points_invalid", "Lote de pontos inválido.");
    }
    const points = continuousTrackingPoints0670(rawPoints);
    if (!points.length) return trackingFail0668(res, 400, "tracking_points_invalid", "Nenhum ponto GPS válido.");

    const now = Date.now();
    const sessionStarted = Number(selected.data.startedAtMillis || 0);
    const accepted = points.filter((point) =>
      point.recordedAtMillis >= sessionStarted - 60 * 1000 &&
      point.recordedAtMillis <= now + 5 * 60 * 1000
    );
    if (!accepted.length) return trackingFail0668(res, 400, "tracking_points_out_of_window", "Pontos fora da sessão.");

    const pointBatch = db.batch();
    let stored = 0;
    for (const point of accepted) {
      const pointId = String(point.recordedAtMillis).padStart(16, "0") + "-" +
        sha256Hex0668([point.latitude.toFixed(7), point.longitude.toFixed(7), point.recordedAtMillis].join("|")).slice(0, 12);
      pointBatch.set(selected.ref.collection("points").doc(pointId), point, { merge:true });
      stored += 1;
    }
    await pointBatch.commit();

    const newest = accepted[accepted.length - 1];
    const battery = finiteNumber0668(body.batteryPercent);
    const latestOutcome = await db.runTransaction(async (tx) => {
      const currentSnap = await tx.get(selected.ref);
      if (!currentSnap.exists || !currentSnap.data().active) {
        throw Object.assign(new Error("tracking_session_closed"), { code:"tracking_session_closed" });
      }
      const currentData = currentSnap.data();
      const previousGpsAt = Number(currentData.latestPointAtMillis || 0);
      const applyLatest = newest.recordedAtMillis > previousGpsAt;
      const update = {
        lastDeviceHeartbeatAtMillis:now,
        batteryPercent:battery == null ? (currentData.batteryPercent ?? null) : Math.max(0, Math.min(100, Math.trunc(battery))),
        updatedAtMillis:now,
      };
      if (applyLatest) {
        update.latestPoint = newest;
        update.latestPointAtMillis = newest.recordedAtMillis;
        update.lastGpsAtMillis = newest.recordedAtMillis;
      }
      tx.set(selected.ref, update, { merge:true });
      return {
        applyLatest,
        acceptedThroughMillis:applyLatest ? newest.recordedAtMillis : previousGpsAt,
      };
    });

    const passengerSharesClosed = latestOutcome.applyLatest
      ? await applyPassengerArrival0691(selected.ref.id, newest, now)
      : 0;

    return trackingJson0668(res, 200, {
      ok:true,
      acceptedThroughMillis:latestOutcome.acceptedThroughMillis,
      pathPointsStored:stored,
      passengerSharesClosed,
      latestWins0691:true,
    });
  }

  async function postHeartbeat0670(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const selected = await sessionForDriver0668(req, res, identity, body.sessionId);
    if (!selected) return;
    if (!selected.data.active) {
      return trackingFail0668(res, 409, "tracking_session_closed", "Sessão de rastreamento encerrada.");
    }

    const now = Date.now();
    const sessionStarted = Number(selected.data.startedAtMillis || 0);
    const battery = finiteNumber0668(body.batteryPercent);
    const lastGpsAtMillis = Math.trunc(Number(body.lastGpsAtMillis || 0));
    const heartbeatPoint = normalizePoint0668({
      latitude:finiteNumber0668(body.latitude),
      longitude:finiteNumber0668(body.longitude),
      recordedAtMillis:lastGpsAtMillis,
      accuracyMeters:body.accuracyMeters,
      speedMetersPerSecond:body.speedMetersPerSecond,
    });

    const outcome = await db.runTransaction(async (tx) => {
      const currentSnap = await tx.get(selected.ref);
      if (!currentSnap.exists || !currentSnap.data().active) {
        throw Object.assign(new Error("tracking_session_closed"), { code:"tracking_session_closed" });
      }
      const currentData = currentSnap.data();
      const previousGpsAt = Number(currentData.latestPointAtMillis || 0);
      const pointAllowed = Boolean(
        heartbeatPoint &&
        heartbeatPoint.recordedAtMillis >= sessionStarted - 60 * 1000 &&
        heartbeatPoint.recordedAtMillis <= now + 5 * 60 * 1000 &&
        heartbeatPoint.recordedAtMillis > previousGpsAt
      );
      const update = {
        lastDeviceHeartbeatAtMillis:now,
        clientHeartbeatAtMillis:Math.trunc(Number(body.deviceHeartbeatAtMillis || now)),
        batteryPercent:battery == null ? (currentData.batteryPercent ?? null) : Math.max(0, Math.min(100, Math.trunc(battery))),
        trackingActive:body.trackingActive !== false,
        trackerProtocolVersion:"0691",
        updatedAtMillis:now,
      };
      if (pointAllowed) {
        update.latestPoint = heartbeatPoint;
        update.latestPointAtMillis = heartbeatPoint.recordedAtMillis;
        update.lastGpsAtMillis = heartbeatPoint.recordedAtMillis;
      }
      tx.set(selected.ref, update, { merge:true });
      return {
        pointAllowed,
        acceptedThroughMillis:pointAllowed ? heartbeatPoint.recordedAtMillis : previousGpsAt,
      };
    });

    const passengerSharesClosed = outcome.pointAllowed
      ? await applyPassengerArrival0691(selected.ref.id, heartbeatPoint, now)
      : 0;
    return trackingJson0668(res, 200, {
      ok:true,
      acceptedThroughMillis:outcome.acceptedThroughMillis,
      passengerSharesClosed,
      latestWins0691:true,
    });
  }

  async function closeShare(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const token = cleanText0668(body.token, 180);
    if (!token) return trackingFail0668(res, 400, "tracking_share_token_required", "Token obrigatório.");
    const ref = db.collection("tripTrackingShares").doc(trackingShareDocId0668(token));
    const snap = await ref.get();
    if (!snap.exists || snap.data().driverKey !== identity.driverKey) {
      return trackingFail0668(res, 404, "tracking_share_not_found", "Link não encontrado.");
    }
    const now = Date.now();
    await ref.set({ active: false, endedAtMillis: now, endReason: "DRIVER_REVOKED", updatedAtMillis: now }, { merge: true });
    const share = snap.data();
    if (share.scope === "FAMILY") {
      const username = normalizeFamilyUsername0681(share.familyUsername0681 || (identity.driver && (identity.driver.publicUsername || identity.driver.username)));
      if (username) {
        const aliasRef = db.collection("tripTrackingFamilyAliases").doc(familyAliasDocId0681(username));
        const aliasSnap = await aliasRef.get();
        if (aliasSnap.exists && aliasSnap.data().shareDocId === ref.id) {
          await aliasRef.set({ active: false, updatedAtMillis: now, endedAtMillis: now }, { merge: true });
        }
      }
    }
    return trackingJson0668(res, 200, { ok: true, acceptedThroughMillis: 0 });
  }

  async function closeSession(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const selected = await sessionForDriver0668(req, res, identity, body.sessionId);
    if (!selected) return;
    const now = Date.now();
    await selected.ref.set({ active: false, endedAtMillis: now, updatedAtMillis: now }, { merge: true });
    const shares = await db.collection("tripTrackingShares").where("sessionDocId", "==", selected.ref.id).limit(100).get();
    const activeShares = shares.docs.filter((doc) => doc.data().active);
    if (activeShares.length) {
      const batch = db.batch();
      activeShares.forEach((doc) => batch.set(doc.ref, {
        active: false,
        endedAtMillis: now,
        endReason: "SESSION_ENDED",
        updatedAtMillis: now,
      }, { merge: true }));
      await batch.commit();
    }
    return trackingJson0668(res, 200, { ok: true, acceptedThroughMillis: 0 });
  }

  async function getFamilyStatus0681(req, res) {
    const identity = await requireTrackingDriver0668(req, res);
    if (!identity) return;
    const username = normalizeFamilyUsername0681(identity.driver && (identity.driver.publicUsername || identity.driver.username));
    if (!username) return trackingJson0668(res, 200, { ok:true, active:false, state:"INACTIVE", username:"" });
    const aliasRef = db.collection("tripTrackingFamilyAliases").doc(familyAliasDocId0681(username));
    const aliasSnap = await aliasRef.get();
    if (!aliasSnap.exists || !aliasSnap.data().active) {
      return trackingJson0668(res, 200, { ok:true, active:false, state:"INACTIVE", username, publicPath:"/" + username + "/gps" });
    }
    const alias = aliasSnap.data();
    const shareSnap = await db.collection("tripTrackingShares").doc(cleanText0668(alias.shareDocId, 128)).get();
    if (!shareSnap.exists || !shareSnap.data().active) {
      return trackingJson0668(res, 200, { ok:true, active:false, state:"INACTIVE", username, publicPath:"/" + username + "/gps" });
    }
    const share = shareSnap.data();
    const sessionSnap = await db.collection("tripTrackingSessions").doc(cleanText0668(share.sessionDocId, 128)).get();
    if (!sessionSnap.exists || !sessionSnap.data().active) {
      return trackingJson0668(res, 200, { ok:true, active:false, state:"INACTIVE", username, publicPath:"/" + username + "/gps" });
    }
    const session = sessionSnap.data();
    const latest = normalizePoint0668(session.latestPoint);
    const telemetry = publicTrackerTelemetry0670(session, share, latest, Date.now());
    const state = telemetry.deviceState === "CONNECTED" ? "ACTIVE" : "RECONNECTING";
    return trackingJson0668(res, 200, {
      ok:true,
      active:true,
      state,
      username,
      publicPath:"/" + username + "/gps",
      deviceState:telemetry.deviceState,
      gpsState:telemetry.gpsState,
      lastDeviceHeartbeatAtMillis:telemetry.lastDeviceHeartbeatAtMillis,
      lastGpsAtMillis:telemetry.lastGpsAtMillis,
    });
  }

  async function openFamilySession0681(req, res) {
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const username = normalizeFamilyUsername0681(body.username);
    const pin = cleanText0668(body.pin, 12);
    if (!username || !/^\d{6}$/.test(pin)) {
      return trackingFail0668(res, 400, "tracking_family_credentials_invalid", "Código familiar inválido.");
    }
    const aliasRef = db.collection("tripTrackingFamilyAliases").doc(familyAliasDocId0681(username));
    const aliasSnap = await aliasRef.get();
    if (!aliasSnap.exists || !aliasSnap.data().active) {
      return trackingFail0668(res, 410, "tracking_family_inactive", "O motorista não está compartilhando a localização.");
    }
    const alias = aliasSnap.data();
    const ip = cleanText0668(req.ip || req.get("X-Forwarded-For") || "unknown", 120);
    const attemptRef = db.collection("tripTrackingFamilyAuthAttempts").doc(sha256Hex0668(username + "|" + ip));
    const attemptSnap = await attemptRef.get();
    const now = Date.now();
    const attempt = attemptSnap.exists ? attemptSnap.data() : {};
    const windowStart = Number(attempt.windowStartedAtMillis || 0);
    const failures = now - windowStart <= FAMILY_AUTH_WINDOW_MILLIS_0681 ? Number(attempt.failures || 0) : 0;
    if (failures >= FAMILY_AUTH_MAX_FAILURES_0681) {
      return trackingFail0668(res, 429, "tracking_family_auth_limited", "Muitas tentativas. Aguarde alguns minutos.");
    }
    const expectedHash = cleanText0668(alias.pinHash, 128);
    if (!safeEqual0681(expectedHash, familyPinHash0681(username, pin))) {
      await attemptRef.set({
        username,
        failures: failures + 1,
        windowStartedAtMillis: failures === 0 ? now : windowStart,
        updatedAtMillis: now,
      }, { merge:true });
      return trackingFail0668(res, 401, "tracking_family_pin_invalid", "Código familiar incorreto.");
    }
    await attemptRef.set({ failures:0, windowStartedAtMillis:now, updatedAtMillis:now }, { merge:true });
    const sessionToken = crypto.randomBytes(32).toString("base64url");
    const expiresAtMillis = now + FAMILY_ACCESS_SESSION_MILLIS_0681;
    await db.collection("tripTrackingFamilyAccessSessions").doc(familySessionDocId0681(sessionToken)).set({
      username,
      driverKey:alias.driverKey,
      shareDocId:alias.shareDocId,
      createdAtMillis:now,
      updatedAtMillis:now,
      expiresAtMillis,
    });
    return trackingJson0668(res, 200, { ok:true, sessionToken, username, expiresAtMillis });
  }

  async function getPublicFamily0681(req, res, usernameRaw) {
    const username = normalizeFamilyUsername0681(usernameRaw);
    const sessionToken = cleanText0668(req.get("X-Rota-Certa-Family-Session") || "", 180);
    if (!username || !/^[A-Za-z0-9_-]{22,180}$/.test(sessionToken)) {
      return trackingFail0668(res, 401, "tracking_family_session_required", "Autorize este navegador para acompanhar.");
    }
    const accessRef = db.collection("tripTrackingFamilyAccessSessions").doc(familySessionDocId0681(sessionToken));
    const accessSnap = await accessRef.get();
    const now = Date.now();
    if (!accessSnap.exists || Number(accessSnap.data().expiresAtMillis || 0) <= now || accessSnap.data().username !== username) {
      return trackingFail0668(res, 401, "tracking_family_session_invalid", "Autorização familiar expirada.");
    }
    const aliasSnap = await db.collection("tripTrackingFamilyAliases").doc(familyAliasDocId0681(username)).get();
    if (!aliasSnap.exists || !aliasSnap.data().active) {
      return trackingFail0668(res, 410, "tracking_family_inactive", "O motorista não está compartilhando a localização.");
    }
    const alias = aliasSnap.data();
    if (alias.driverKey !== accessSnap.data().driverKey) {
      return trackingFail0668(res, 401, "tracking_family_session_invalid", "Autorização familiar inválida.");
    }
    const shareSnap = await db.collection("tripTrackingShares").doc(cleanText0668(alias.shareDocId, 128)).get();
    if (!shareSnap.exists || !shareSnap.data().active) {
      return trackingFail0668(res, 410, "tracking_family_inactive", "O motorista não está compartilhando a localização.");
    }
    const share = shareSnap.data();
    const sessionRef = db.collection("tripTrackingSessions").doc(cleanText0668(share.sessionDocId, 120));
    const sessionSnap = await sessionRef.get();
    if (!sessionSnap.exists || !sessionSnap.data().active) {
      return trackingFail0668(res, 410, "tracking_session_ended", "O motorista não está compartilhando a localização.");
    }
    const session = sessionSnap.data();
    const traceRequested0692 = String(req.query && req.query.trace || "") === "1";
    const requestedSince = traceRequested0692 ? finiteNumber0668(req.query && req.query.since) : null;
    let points = [];
    let hasMorePoints = false;
    let nextSinceMillis = 0;

    if (traceRequested0692) {
      const queryFloor = trackingQueryFloor0670(share, session, requestedSince);
      const pointsSnap = await sessionRef.collection("points")
        .where("recordedAtMillis", ">=", queryFloor)
        .orderBy("recordedAtMillis", "asc")
        .limit(MAX_PUBLIC_POINTS_0668)
        .get();
      points = publicTrackingPoints0668(pointsSnap.docs.map((doc) => doc.data()), {
        scope:"FAMILY",
        createdAtMillis:share.createdAtMillis,
        sessionStartedAtMillis:session.startedAtMillis,
      }).filter((point) => requestedSince == null || point.recordedAtMillis > requestedSince);
      const lastRoutePoint = points.length ? points[points.length - 1] : null;
      hasMorePoints = pointsSnap.size >= MAX_PUBLIC_POINTS_0668;
      nextSinceMillis = lastRoutePoint
        ? Number(lastRoutePoint.recordedAtMillis || 0)
        : Math.max(0, Number(requestedSince || 0));
    }

    const latest = normalizePoint0668(session.latestPoint);
    const tracker0670 = publicTrackerTelemetry0670(session, share, latest, now);
    await accessRef.set({ updatedAtMillis:now, expiresAtMillis:now + FAMILY_ACCESS_SESSION_MILLIS_0681 }, { merge:true });
    return trackingJson0668(res, 200, {
      ok:true,
      scope:"FAMILY",
      mode:traceRequested0692 ? "FAMILY_HISTORY" : "FAMILY_LIVE",
      traceAvailable:true,
      familyLiveFirst0692:true,
      driverDisplayName:cleanText0668(session.driverDisplayName, 120),
      startedAtMillis:Number(session.startedAtMillis || 0),
      lastUpdatedAtMillis:tracker0670.lastDeviceHeartbeatAtMillis,
      lastDeviceHeartbeatAtMillis:tracker0670.lastDeviceHeartbeatAtMillis,
      lastGpsAtMillis:tracker0670.lastGpsAtMillis,
      deviceState:tracker0670.deviceState,
      gpsState:tracker0670.gpsState,
      serverNowMillis:now,
      batteryPercent:session.batteryPercent == null ? null : Number(session.batteryPercent),
      points,
      hasMorePoints,
      nextSinceMillis,
      current:latest,
      destination:null,
      distanceToDestinationMeters:null,
      permanentPath:"/" + username + "/gps",
    });
  }

  async function getPublic(req, res, tokenRaw) {
    const token = cleanText0668(tokenRaw, 180);
    if (!/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      return trackingFail0668(res, 404, "tracking_share_not_found", "Link de acompanhamento inválido.");
    }
    const shareSnap = await db.collection("tripTrackingShares").doc(trackingShareDocId0668(token)).get();
    if (!shareSnap.exists) return trackingFail0668(res, 404, "tracking_share_not_found", "Link de acompanhamento não encontrado.");

    const share = shareSnap.data();
    const now = Date.now();
    const shareExpired = trackingShareExpired0680(share, now);
    if (!share.active || shareExpired) {
      if (share.scope === "PASSENGER" && share.endReason === "DESTINATION_REACHED") {
        return trackingFail0668(res, 410, "passenger_arrived", "Passageiro chegou ao destino. Este acompanhamento foi encerrado.");
      }
      return trackingFail0668(res, 410, "tracking_share_ended", "Este acompanhamento foi encerrado.");
    }

    const sessionRef = db.collection("tripTrackingSessions").doc(cleanText0668(share.sessionDocId, 120));
    const sessionSnap = await sessionRef.get();
    if (!sessionSnap.exists) return trackingFail0668(res, 410, "tracking_session_ended", "Sessão de acompanhamento indisponível.");
    const session = sessionSnap.data();
    if (!session.active) {
      return trackingFail0668(res, 410, "tracking_session_ended", "Este acompanhamento foi encerrado.");
    }

    const privacyFloor = trackingQueryFloor0670(share, session, 0);
    const passengerLiveOnly0691 = share.scope === "PASSENGER";
    let points = [];
    let hasMorePoints = false;
    let nextSinceMillis = 0;

    if (!passengerLiveOnly0691) {
      const requestedSince = finiteNumber0668(req.query && req.query.since);
      const queryFloor = trackingQueryFloor0670(share, session, requestedSince);
      const pointsSnap = await sessionRef.collection("points")
        .where("recordedAtMillis", ">=", queryFloor)
        .orderBy("recordedAtMillis", "asc")
        .limit(MAX_PUBLIC_POINTS_0668)
        .get();
      points = publicTrackingPoints0668(pointsSnap.docs.map((doc) => doc.data()), {
        scope:share.scope,
        createdAtMillis:share.createdAtMillis,
        sessionStartedAtMillis:session.startedAtMillis,
      }).filter((point) => requestedSince == null || point.recordedAtMillis > requestedSince);
      const lastRoutePoint = points.length ? points[points.length - 1] : null;
      hasMorePoints = pointsSnap.size >= MAX_PUBLIC_POINTS_0668;
      nextSinceMillis = lastRoutePoint
        ? Number(lastRoutePoint.recordedAtMillis || 0)
        : Math.max(0, Number(requestedSince || 0));
    }

    const latest = normalizePoint0668(session.latestPoint);
    const current = latest && latest.recordedAtMillis >= privacyFloor ? latest : null;
    const destinationLatitude = passengerLiveOnly0691 ? finiteNumber0668(share.destinationLatitude) : null;
    const destinationLongitude = passengerLiveOnly0691 ? finiteNumber0668(share.destinationLongitude) : null;
    const distanceToDestinationMeters = passengerDistanceToDestinationMeters0669(current, share);
    const tracker0670 = publicTrackerTelemetry0670(session, share, current, now);

    return trackingJson0668(res, 200, {
      ok:true,
      scope:share.scope,
      mode:passengerLiveOnly0691 ? "LIVE_ONLY" : "LIVE_WITH_TRACE",
      traceAvailable:!passengerLiveOnly0691,
      driverDisplayName:cleanText0668(session.driverDisplayName, 120),
      startedAtMillis:passengerLiveOnly0691 ? Number(share.createdAtMillis || privacyFloor) : Number(session.startedAtMillis || privacyFloor),
      expiresAtMillis:Number(share.expiresAtMillis || 0),
      lastUpdatedAtMillis:tracker0670.lastDeviceHeartbeatAtMillis,
      lastDeviceHeartbeatAtMillis:tracker0670.lastDeviceHeartbeatAtMillis,
      lastGpsAtMillis:tracker0670.lastGpsAtMillis,
      deviceState:tracker0670.deviceState,
      gpsState:tracker0670.gpsState,
      serverNowMillis:now,
      batteryPercent:session.batteryPercent == null ? null : Number(session.batteryPercent),
      points,
      hasMorePoints,
      nextSinceMillis,
      current,
      destination:passengerLiveOnly0691 && validCoordinate0668(destinationLatitude, destinationLongitude) ? {
        latitude:destinationLatitude,
        longitude:destinationLongitude,
        label:cleanText0668(share.destinationLabel, 220),
      } : null,
      distanceToDestinationMeters,
      arrivalConfirmation:{
        requiredHits:PASSENGER_ARRIVAL_REQUIRED_HITS_0691,
        radiusMeters:PASSENGER_DESTINATION_RADIUS_METERS_0691,
      },
    });
  }

  return {
    createSession,
    createShare,
    postPoints,
    postHeartbeat0670,
    closeShare,
    closeSession,
    getFamilyStatus0681,
    openFamilySession0681,
    getPublicFamily0681,
    getPublic,
  };
}

module.exports = {
  createLiveTracking0668,
  distanceMeters0668,
  continuousTrackingPoints0670,
  trackingQueryFloor0670,
  publicTrackingPoints0668,
  publicTrackerTelemetry0670,
  passengerDistanceToDestinationMeters0669,
  shouldClosePassengerShare0668,
  passengerArrivalProgress0691,
  trackingShareDocId0668,
  trackingShareExpired0680,
  normalizeFamilyUsername0681,
  familyPinHash0681,
};