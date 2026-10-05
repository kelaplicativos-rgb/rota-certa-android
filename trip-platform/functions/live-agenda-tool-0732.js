"use strict";

const crypto = require("crypto");

const TOOL_SCHEMA_VERSION_0732 = "live-agenda-tool-v1";

function cleanText0732(value, maxLength = 240) {
  return String(value == null ? "" : value).trim().slice(0, maxLength);
}

function safeInteger0732(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : fallback;
}

function stableSnapshotMaterial0732(feed) {
  return {
    driverUsername: cleanText0732(feed && feed.driverUsername, 80),
    query: feed && feed.query && typeof feed.query === "object" ? feed.query : {},
    latestChangeAtMillis: safeInteger0732(feed && feed.latestChangeAtMillis),
    trips: Array.isArray(feed && feed.trips) ? feed.trips : [],
  };
}

function snapshotHash0732(feed) {
  return crypto
    .createHash("sha256")
    .update(JSON.stringify(stableSnapshotMaterial0732(feed)))
    .digest("hex");
}

function compactTrip0732(tripRaw) {
  const trip = tripRaw && typeof tripRaw === "object" ? tripRaw : {};
  const stops = Array.isArray(trip.stops)
    ? trip.stops.map((stop) => ({
        order: Number.isFinite(Number(stop && stop.order)) ? Number(stop.order) : 0,
        name: cleanText0732(stop && stop.name, 160),
      })).filter((stop) => stop.name)
    : [];
  const segments = Array.isArray(trip.segments)
    ? trip.segments.map((segment) => ({
        index: Number.isFinite(Number(segment && segment.index)) ? Number(segment.index) : 0,
        from: cleanText0732(segment && segment.from, 160),
        to: cleanText0732(segment && segment.to, 160),
        availableSeats: segment && segment.availableSeats == null ? null : safeInteger0732(segment.availableSeats),
        occupiedSeats: segment && segment.occupiedSeats == null ? null : safeInteger0732(segment.occupiedSeats),
        passengerSeats: segment && segment.passengerSeats == null ? null : safeInteger0732(segment.passengerSeats),
        blockedSeats: segment && segment.blockedSeats == null ? null : safeInteger0732(segment.blockedSeats),
      }))
    : [];
  const requested = trip.requestedSegment && typeof trip.requestedSegment === "object"
    ? {
        found: trip.requestedSegment.found === true,
        origin: cleanText0732(trip.requestedSegment.origin, 160),
        destination: cleanText0732(trip.requestedSegment.destination, 160),
        reliable: trip.requestedSegment.reliable === true,
        availableSeats: trip.requestedSegment.availableSeats == null
          ? null
          : safeInteger0732(trip.requestedSegment.availableSeats),
        occupiedSeats: trip.requestedSegment.occupiedSeats == null
          ? null
          : safeInteger0732(trip.requestedSegment.occupiedSeats),
      }
    : null;
  return {
    id: cleanText0732(trip.id, 180),
    localDate: cleanText0732(trip.localDate, 20),
    localTime: cleanText0732(trip.localTime, 10),
    status: cleanText0732(trip.status, 32),
    capacity: safeInteger0732(trip.capacity),
    capacityReliable: trip.capacityReliable === true,
    itineraryAuthoritative: trip.itineraryAuthoritative === true,
    availableSeats: trip.availableSeats == null ? null : safeInteger0732(trip.availableSeats),
    updatedAtMillis: safeInteger0732(trip.updatedAtMillis),
    stops,
    segments,
    requestedSegment: requested,
  };
}

function classifyAgendaRead0732(feed) {
  const trips = Array.isArray(feed && feed.trips) ? feed.trips : [];
  const query = feed && feed.query && typeof feed.query === "object" ? feed.query : {};
  const requestedSegmentRequired = Boolean(cleanText0732(query.origin, 180) && cleanText0732(query.destination, 180));
  let reliableTrips = 0;
  let unreliableTrips = 0;
  const reasons = [];

  for (const trip of trips) {
    const segments = Array.isArray(trip && trip.segments) ? trip.segments : [];
    const segmentVacanciesKnown = segments.every((segment) => segment && segment.availableSeats != null);
    const requestedReliable = !requestedSegmentRequired ||
      Boolean(trip && trip.requestedSegment && trip.requestedSegment.found === true && trip.requestedSegment.reliable === true);
    const reliable = Boolean(
      trip &&
      trip.capacityReliable === true &&
      trip.itineraryAuthoritative === true &&
      segmentVacanciesKnown &&
      requestedReliable,
    );
    if (reliable) reliableTrips += 1;
    else unreliableTrips += 1;
  }

  let validationStatus = "COMPLETE";
  if (unreliableTrips > 0) {
    validationStatus = "PARTIAL";
    reasons.push("UNRELIABLE_TRIP_FIELDS");
  }

  if (feed && cleanText0732(feed.sourceStatus, 24).toUpperCase() !== "LIVE") {
    validationStatus = "PARTIAL";
    reasons.push("SOURCE_NOT_LIVE");
  }

  return {
    validationStatus,
    reliableTrips,
    unreliableTrips,
    reasons,
  };
}

async function readFeed0732(liveAgendaFeed0701, req, username) {
  let statusCode = 200;
  let payload = "";
  const headers = {};
  const sink = {
    status(code) {
      statusCode = Number(code || 200);
      return this;
    },
    set(key, value) {
      headers[String(key)] = String(value);
      return this;
    },
    send(value) {
      payload = String(value == null ? "" : value);
      return this;
    },
  };

  await liveAgendaFeed0701.getLiveAgendaFeed0701(req, sink, username, "json");

  let body = null;
  try {
    body = payload ? JSON.parse(payload) : null;
  } catch (_) {
    body = null;
  }

  if (statusCode >= 400 || !body || typeof body !== "object") {
    const error = new Error(
      cleanText0732(body && body.message, 300) ||
      "Falha ao consultar a agenda ao vivo.",
    );
    error.httpStatus = statusCode >= 400 ? statusCode : 502;
    error.code = cleanText0732(body && body.error, 80) || "live_agenda_read_failed";
    throw error;
  }
  return body;
}

function sendTool0732(req, res, body) {
  const etag = `"${body.snapshotHash}"`;
  res.set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
  res.set("Pragma", "no-cache");
  res.set("ETag", etag);
  res.set("X-Rota-Certa-Validation-Status", body.validationStatus);
  res.set("X-Rota-Certa-Collector-Status", body.collectorValidationStatus);
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  res.set("Content-Type", "application/json; charset=utf-8");

  const ifNoneMatch = cleanText0732(req && req.headers && (req.headers["if-none-match"] || req.headers["If-None-Match"]), 160);
  const ifSnapshotHash = cleanText0732(
    req && req.query && (req.query.ifSnapshotHash || req.query.snapshotHash),
    80,
  ).toLowerCase();
  const snapshotMatch = /^[a-f0-9]{64}$/.test(ifSnapshotHash) &&
    ifSnapshotHash === String(body.snapshotHash || "").toLowerCase();
  if ((ifNoneMatch && ifNoneMatch === etag) || snapshotMatch) {
    return res.status(304).send("");
  }
  return res.status(200).send(JSON.stringify(body));
}

function sendToolFailure0732(res, error) {
  const status = Number(error && error.httpStatus || 502);
  res.status(status >= 400 && status <= 599 ? status : 502);
  res.set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
  res.set("Pragma", "no-cache");
  res.set("X-Rota-Certa-Validation-Status", "FAILED");
  res.set("X-Rota-Certa-Collector-Status", "FAILED");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  res.set("Content-Type", "application/json; charset=utf-8");
  return res.send(JSON.stringify({
    schemaVersion: TOOL_SCHEMA_VERSION_0732,
    validationStatus: "FAILED",
    collectorValidationStatus: "FAILED",
    decisionSafeForPublish: false,
    error: cleanText0732(error && error.code, 80) || "live_agenda_tool_failed",
    message: cleanText0732(error && error.message, 300) || "Falha ao consultar a agenda ao vivo.",
  }));
}

function createLiveAgendaTool0732({ liveAgendaFeed0701 }) {
  if (!liveAgendaFeed0701 || typeof liveAgendaFeed0701.getLiveAgendaFeed0701 !== "function") {
    throw new Error("liveAgendaFeed0701 is required");
  }

  async function getLiveAgendaTool0732(req, res, usernameRaw) {
    const username = cleanText0732(usernameRaw, 80);
    try {
      const feed = await readFeed0732(liveAgendaFeed0701, req, username);
      const classification = classifyAgendaRead0732(feed);
      const compactTrips = (Array.isArray(feed.trips) ? feed.trips : []).map(compactTrip0732);
      const snapshotHash = snapshotHash0732(feed);
      const body = {
        schemaVersion: TOOL_SCHEMA_VERSION_0732,
        sourceFeedSchemaVersion: cleanText0732(feed.schemaVersion, 80),
        sourceStatus: cleanText0732(feed.sourceStatus, 24).toUpperCase() || "UNKNOWN",
        validationStatus: classification.validationStatus,
        collectorValidationStatus: "PENDING_UNKNOWN",
        collectorValidationReason: "Este endpoint comprova a leitura canônica da Agenda; validação pública BlaBlaCar por DATA+ORIGEM+DESTINO+SENTIDO continua sendo uma consulta independente.",
        decisionSafeForPublish: false,
        generatedAtMillis: safeInteger0732(feed.generatedAtMillis),
        generatedAtIsoUtc: cleanText0732(feed.generatedAtIsoUtc, 80),
        latestChangeAtMillis: safeInteger0732(feed.latestChangeAtMillis),
        driverUsername: cleanText0732(feed.driverUsername || username, 80),
        query: feed.query && typeof feed.query === "object" ? feed.query : {},
        count: compactTrips.length,
        snapshotHash,
        completeness: {
          reliableTrips: classification.reliableTrips,
          unreliableTrips: classification.unreliableTrips,
          reasons: classification.reasons,
        },
        trips: compactTrips,
      };
      return sendTool0732(req, res, body);
    } catch (error) {
      return sendToolFailure0732(res, error);
    }
  }

  return { getLiveAgendaTool0732 };
}

module.exports = {
  TOOL_SCHEMA_VERSION_0732,
  compactTrip0732,
  classifyAgendaRead0732,
  snapshotHash0732,
  createLiveAgendaTool0732,
};
