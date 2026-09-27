"use strict";

const crypto = require("crypto");

const MAX_SESSION_MILLIS_0668 = 36 * 60 * 60 * 1000;
const MAX_PASSENGER_MILLIS_0668 = 24 * 60 * 60 * 1000;
const MAX_POINT_BATCH_0668 = 100;
const MAX_PUBLIC_POINTS_0668 = 5000;
const PASSENGER_DESTINATION_RADIUS_METERS_0668 = 220;
const PASSENGER_MIN_ACTIVE_MILLIS_0668 = 10 * 60 * 1000;
const PATH_SAMPLE_DISTANCE_METERS_0668 = 200;
const PATH_SAMPLE_INTERVAL_MILLIS_0668 = 60 * 1000;

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

function shouldClosePassengerShare0668(share, latest, nowMillis = Date.now()) {
  if (!share || share.scope !== "PASSENGER" || !share.active || !latest) return false;
  const created = Number(share.createdAtMillis || 0);
  const latestRecordedAt = Number(latest.recordedAtMillis || 0);
  if (latestRecordedAt < created) return false;
  if (nowMillis - created < PASSENGER_MIN_ACTIVE_MILLIS_0668) return false;
  const destLat = finiteNumber0668(share.destinationLatitude);
  const destLon = finiteNumber0668(share.destinationLongitude);
  const lat = finiteNumber0668(latest.latitude);
  const lon = finiteNumber0668(latest.longitude);
  if (!validCoordinate0668(destLat, destLon) || !validCoordinate0668(lat, lon)) return false;
  return distanceMeters0668(lat, lon, destLat, destLon) <= PASSENGER_DESTINATION_RADIUS_METERS_0668;
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
    const expiresAtMillis = Math.max(now + 60 * 60 * 1000, Math.min(startedAtMillis + MAX_SESSION_MILLIS_0668, now + MAX_SESSION_MILLIS_0668));
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
    if (!selected.data.active || Number(selected.data.expiresAtMillis || 0) <= now) {
      return trackingFail0668(res, 409, "tracking_session_closed", "Sessão de rastreamento encerrada.");
    }
    const createdAtMillis = Math.max(Number(selected.data.startedAtMillis || now), Math.trunc(Number(body.createdAtMillis || now)));
    const requestedExpiry = Math.trunc(Number(body.expiresAtMillis || 0));
    const sessionExpiry = Number(selected.data.expiresAtMillis || (now + MAX_SESSION_MILLIS_0668));
    const scopeMax = scope === "PASSENGER" ? now + MAX_PASSENGER_MILLIS_0668 : now + MAX_SESSION_MILLIS_0668;
    const expiresAtMillis = Math.min(
      requestedExpiry > now ? requestedExpiry : scopeMax,
      sessionExpiry,
      scopeMax,
    );
    const destinationLatitude = finiteNumber0668(body.destinationLatitude);
    const destinationLongitude = finiteNumber0668(body.destinationLongitude);
    if (scope === "PASSENGER" && !validCoordinate0668(destinationLatitude, destinationLongitude)) {
      return trackingFail0668(res, 400, "tracking_destination_required", "Destino exato do passageiro é obrigatório.");
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
      updatedAtMillis: now,
    }, { merge: true });
    return trackingJson0668(res, 200, { ok: true, acceptedThroughMillis: 0 });
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
    const points = rawPoints.map(normalizePoint0668).filter(Boolean).sort((a, b) => a.recordedAtMillis - b.recordedAtMillis);
    if (!points.length) return trackingFail0668(res, 400, "tracking_points_invalid", "Nenhum ponto GPS válido.");
    const now = Date.now();
    const sessionStarted = Number(selected.data.startedAtMillis || 0);
    const accepted = points.filter((point) =>
      point.recordedAtMillis >= sessionStarted - 60 * 1000 &&
      point.recordedAtMillis <= now + 5 * 60 * 1000
    );
    if (!accepted.length) return trackingFail0668(res, 400, "tracking_points_out_of_window", "Pontos fora da sessão.");

    let prior = selected.data.latestPoint && normalizePoint0668(selected.data.latestPoint);
    const batch = db.batch();
    let stored = 0;
    for (const point of accepted) {
      const shouldStorePath = !prior ||
        point.recordedAtMillis - prior.recordedAtMillis >= PATH_SAMPLE_INTERVAL_MILLIS_0668 ||
        distanceMeters0668(prior.latitude, prior.longitude, point.latitude, point.longitude) >= PATH_SAMPLE_DISTANCE_METERS_0668;
      if (shouldStorePath) {
        const pointId = String(point.recordedAtMillis).padStart(16, "0") + "-" +
          sha256Hex0668([point.latitude.toFixed(6), point.longitude.toFixed(6), point.recordedAtMillis].join("|")).slice(0, 12);
        batch.set(selected.ref.collection("points").doc(pointId), point, { merge: true });
        stored += 1;
        prior = point;
      }
    }
    const latest = accepted[accepted.length - 1];
    const battery = finiteNumber0668(body.batteryPercent);
    batch.set(selected.ref, {
      latestPoint: latest,
      latestPointAtMillis: latest.recordedAtMillis,
      batteryPercent: battery == null ? null : Math.max(0, Math.min(100, Math.trunc(battery))),
      updatedAtMillis: now,
    }, { merge: true });
    await batch.commit();

    const shares = await db.collection("tripTrackingShares")
      .where("sessionDocId", "==", selected.ref.id)
      .limit(100)
      .get();
    const closing = shares.docs.filter((doc) => doc.data().active && shouldClosePassengerShare0668(doc.data(), latest, now));
    if (closing.length) {
      const closeBatch = db.batch();
      closing.forEach((doc) => closeBatch.set(doc.ref, {
        active: false,
        endedAtMillis: now,
        endReason: "DESTINATION_REACHED",
        updatedAtMillis: now,
      }, { merge: true }));
      await closeBatch.commit();
    }

    return trackingJson0668(res, 200, {
      ok: true,
      acceptedThroughMillis: latest.recordedAtMillis,
      pathPointsStored: stored,
      passengerSharesClosed: closing.length,
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

  async function getPublic(req, res, tokenRaw) {
    const token = cleanText0668(tokenRaw, 180);
    if (!/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      return trackingFail0668(res, 404, "tracking_share_not_found", "Link de acompanhamento inválido.");
    }
    const shareSnap = await db.collection("tripTrackingShares").doc(trackingShareDocId0668(token)).get();
    if (!shareSnap.exists) return trackingFail0668(res, 404, "tracking_share_not_found", "Link de acompanhamento não encontrado.");
    const share = shareSnap.data();
    const now = Date.now();
    if (!share.active || Number(share.expiresAtMillis || 0) <= now) {
      return trackingFail0668(res, 410, "tracking_share_ended", "Este acompanhamento foi encerrado.");
    }
    const sessionRef = db.collection("tripTrackingSessions").doc(cleanText0668(share.sessionDocId, 120));
    const sessionSnap = await sessionRef.get();
    if (!sessionSnap.exists) return trackingFail0668(res, 410, "tracking_session_ended", "Sessão de acompanhamento indisponível.");
    const session = sessionSnap.data();
    if (!session.active || Number(session.expiresAtMillis || 0) <= now) {
      return trackingFail0668(res, 410, "tracking_session_ended", "Este acompanhamento foi encerrado.");
    }

    const floor = share.scope === "PASSENGER" ? Number(share.createdAtMillis || now) : Number(session.startedAtMillis || now);
    const pointsSnap = await sessionRef.collection("points")
      .where("recordedAtMillis", ">=", floor)
      .orderBy("recordedAtMillis", "asc")
      .limit(MAX_PUBLIC_POINTS_0668)
      .get();
    let points = pointsSnap.docs.map((doc) => doc.data());
    const latest = normalizePoint0668(session.latestPoint);
    if (latest && latest.recordedAtMillis >= floor) {
      const last = points.length ? normalizePoint0668(points[points.length - 1]) : null;
      if (!last || latest.recordedAtMillis > last.recordedAtMillis) points.push(latest);
    }
    points = publicTrackingPoints0668(points, {
      scope: share.scope,
      createdAtMillis: share.createdAtMillis,
      sessionStartedAtMillis: session.startedAtMillis,
    });

    const current = points.length ? normalizePoint0668(points[points.length - 1]) : null;
    const destinationLatitude = share.scope === "PASSENGER" ? finiteNumber0668(share.destinationLatitude) : null;
    const destinationLongitude = share.scope === "PASSENGER" ? finiteNumber0668(share.destinationLongitude) : null;
    const distanceToDestinationMeters = current && validCoordinate0668(destinationLatitude, destinationLongitude)
      ? Math.round(distanceMeters0668(current.latitude, current.longitude, destinationLatitude, destinationLongitude))
      : null;

    return trackingJson0668(res, 200, {
      ok: true,
      scope: share.scope,
      driverDisplayName: cleanText0668(session.driverDisplayName, 120),
      startedAtMillis: share.scope === "PASSENGER" ? Number(share.createdAtMillis || floor) : Number(session.startedAtMillis || floor),
      expiresAtMillis: Number(share.expiresAtMillis || 0),
      lastUpdatedAtMillis: current ? Number(current.recordedAtMillis || 0) : 0,
      batteryPercent: session.batteryPercent == null ? null : Number(session.batteryPercent),
      points,
      current,
      destination: share.scope === "PASSENGER" && validCoordinate0668(destinationLatitude, destinationLongitude) ? {
        latitude: destinationLatitude,
        longitude: destinationLongitude,
        label: cleanText0668(share.destinationLabel, 220),
      } : null,
      distanceToDestinationMeters,
    });
  }

  return {
    createSession,
    createShare,
    postPoints,
    closeShare,
    closeSession,
    getPublic,
  };
}

module.exports = {
  createLiveTracking0668,
  distanceMeters0668,
  publicTrackingPoints0668,
  shouldClosePassengerShare0668,
  trackingShareDocId0668,
};