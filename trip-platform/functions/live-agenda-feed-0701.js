"use strict";

const FEED_SCHEMA_VERSION_0701 = "live-agenda-feed-v1";
const DEFAULT_TIMEZONE_0701 = "America/Sao_Paulo";
const MAX_TRIPS_0701 = 100;

function cleanText0701(value, maxLength = 240) {
  return String(value == null ? "" : value).trim().slice(0, maxLength);
}

function normalizePlace0701(value) {
  return cleanText0701(value, 180)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function placeMatches0701(stopName, query) {
  const stop = normalizePlace0701(stopName);
  const wanted = normalizePlace0701(query);
  if (!stop || !wanted) return false;
  return stop === wanted ||
    (wanted.length >= 3 && stop.includes(wanted)) ||
    (stop.length >= 3 && wanted.includes(stop));
}

function safeInteger0701(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : fallback;
}

function dateTimeParts0701(millis, timeZone = DEFAULT_TIMEZONE_0701) {
  const instant = Number(millis || 0);
  if (!Number.isFinite(instant) || instant <= 0) {
    return { date: "", time: "", isoUtc: "" };
  }
  const date = new Date(instant);
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: cleanText0701(timeZone, 80) || DEFAULT_TIMEZONE_0701,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const byType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return {
    date: `${byType.year || ""}-${byType.month || ""}-${byType.day || ""}`,
    time: `${byType.hour || ""}:${byType.minute || ""}`,
    isoUtc: date.toISOString(),
  };
}

function normalizeDate0701(value) {
  const raw = cleanText0701(value, 20);
  if (!raw) return "";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    throw Object.assign(new Error("Data inválida. Use AAAA-MM-DD."), {
      httpStatus: 400,
      code: "invalid_feed_date",
    });
  }
  return raw;
}

function findStopIndex0701(stops, query, startIndex = 0) {
  if (!query) return -1;
  for (let index = Math.max(0, startIndex); index < stops.length; index += 1) {
    if (placeMatches0701(stops[index] && stops[index].name, query)) return index;
  }
  return -1;
}

function segmentAvailableSeats0701(trip, index) {
  if (!trip || trip.capacityReliable !== true) return null;
  const capacity = safeInteger0701(trip.capacity);
  const declared = Array.isArray(trip.segmentAvailability) ? trip.segmentAvailability[index] : null;
  if (declared && Number.isFinite(Number(declared.availableSeats))) {
    return Math.max(0, Math.min(capacity, safeInteger0701(declared.availableSeats)));
  }
  const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads : [];
  if (!Number.isFinite(Number(loads[index]))) return null;
  return Math.max(0, capacity - safeInteger0701(loads[index]));
}

function requestedSegment0701(trip, originRaw, destinationRaw) {
  const origin = cleanText0701(originRaw, 180);
  const destination = cleanText0701(destinationRaw, 180);
  if (!origin || !destination) return null;
  const stops = Array.isArray(trip && trip.stops) ? trip.stops : [];
  const fromIndex = findStopIndex0701(stops, origin, 0);
  const toIndex = fromIndex >= 0 ? findStopIndex0701(stops, destination, fromIndex + 1) : -1;
  if (fromIndex < 0 || toIndex <= fromIndex) {
    return {
      found: false,
      origin,
      destination,
      reliable: false,
      availableSeats: null,
      occupiedSeats: null,
    };
  }

  const capacity = safeInteger0701(trip.capacity);
  const reliable = trip.capacityReliable === true;
  let minimumAvailable = reliable ? capacity : null;
  for (let index = fromIndex; index < toIndex; index += 1) {
    const available = segmentAvailableSeats0701(trip, index);
    if (available == null) {
      minimumAvailable = null;
      break;
    }
    minimumAvailable = Math.min(minimumAvailable, available);
  }
  return {
    found: true,
    origin: cleanText0701(stops[fromIndex] && stops[fromIndex].name, 160) || origin,
    destination: cleanText0701(stops[toIndex] && stops[toIndex].name, 160) || destination,
    fromIndex,
    toIndex,
    reliable: reliable && minimumAvailable != null,
    availableSeats: reliable && minimumAvailable != null ? minimumAvailable : null,
    occupiedSeats: reliable && minimumAvailable != null ? Math.max(0, capacity - minimumAvailable) : null,
  };
}

function projectTrip0701(tripRaw, query = {}) {
  const trip = tripRaw && typeof tripRaw === "object" ? tripRaw : {};
  const timeZone = cleanText0701(trip.timezoneId, 80) || DEFAULT_TIMEZONE_0701;
  const departure = dateTimeParts0701(trip.departureAtMillis, timeZone);
  const updated = dateTimeParts0701(trip.updatedAtMillis, timeZone);
  const capacity = safeInteger0701(trip.capacity);
  const stops = (Array.isArray(trip.stops) ? trip.stops : [])
    .map((stop, index) => ({
      order: Number.isFinite(Number(stop && stop.order)) ? Number(stop.order) : index,
      name: cleanText0701(stop && stop.name, 160),
      plannedArrivalMillis: Number.isFinite(Number(stop && stop.plannedArrivalMillis))
        ? Number(stop.plannedArrivalMillis)
        : null,
      plannedDepartureMillis: Number.isFinite(Number(stop && stop.plannedDepartureMillis))
        ? Number(stop.plannedDepartureMillis)
        : null,
    }))
    .filter((stop) => stop.name);

  const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads : [];
  const passengerLoads = Array.isArray(trip.segmentPassengerLoads) ? trip.segmentPassengerLoads : [];
  const blockedLoads = Array.isArray(trip.segmentBlockedLoads) ? trip.segmentBlockedLoads : [];
  const segmentCount = Math.max(0, stops.length - 1);
  const segments = [];
  for (let index = 0; index < segmentCount; index += 1) {
    const availableSeats = segmentAvailableSeats0701(trip, index);
    const occupiedSeats = availableSeats == null
      ? null
      : Math.max(0, capacity - availableSeats);
    segments.push({
      index,
      from: stops[index].name,
      to: stops[index + 1].name,
      availableSeats,
      occupiedSeats,
      passengerSeats: Number.isFinite(Number(passengerLoads[index]))
        ? safeInteger0701(passengerLoads[index])
        : null,
      blockedSeats: Number.isFinite(Number(blockedLoads[index]))
        ? safeInteger0701(blockedLoads[index])
        : null,
      loadSeats: Number.isFinite(Number(loads[index]))
        ? safeInteger0701(loads[index])
        : occupiedSeats,
    });
  }

  const reliableSegmentSeats = segments
    .map((segment) => segment.availableSeats)
    .filter((value) => value != null);
  const fullRouteAvailableSeats =
    trip.capacityReliable === true &&
    segmentCount > 0 &&
    reliableSegmentSeats.length === segmentCount
      ? Math.min(...reliableSegmentSeats)
      : null;

  const requested = requestedSegment0701(
    { ...trip, stops },
    query.origin,
    query.destination,
  );

  return {
    id: cleanText0701(trip.canonicalTripId || trip.tripId || trip.publicToken, 180),
    title: cleanText0701(trip.title, 220),
    localDate: departure.date,
    localTime: departure.time,
    departureAtMillis: safeInteger0701(trip.departureAtMillis),
    departureAtIsoUtc: departure.isoUtc,
    timezoneId: timeZone,
    status: cleanText0701(trip.status, 32).toUpperCase(),
    capacity,
    capacityReliable: trip.capacityReliable === true,
    itineraryAuthoritative: trip.itineraryAuthoritative === true,
    availableSeats: fullRouteAvailableSeats,
    updatedAtMillis: safeInteger0701(trip.updatedAtMillis),
    updatedAtIsoUtc: updated.isoUtc,
    stops,
    segments,
    requestedSegment: requested,
  };
}

function tripMatchesOneSidedPlace0701(trip, origin, destination) {
  const stops = Array.isArray(trip && trip.stops) ? trip.stops : [];
  if (origin && !stops.some((stop) => placeMatches0701(stop && stop.name, origin))) return false;
  if (destination && !stops.some((stop) => placeMatches0701(stop && stop.name, destination))) return false;
  return true;
}

function xmlEscape0701(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function xmlAttr0701(name, value) {
  if (value == null || value === "") return "";
  return ` ${name}="${xmlEscape0701(value)}"`;
}

function serializeXml0701(feed) {
  const lines = ['<?xml version="1.0" encoding="UTF-8"?>'];
  lines.push(
    `<agenda${xmlAttr0701("schemaVersion", feed.schemaVersion)}` +
    `${xmlAttr0701("motorista", feed.driverUsername)}` +
    `${xmlAttr0701("geradoEm", feed.generatedAtIsoUtc)}` +
    `${xmlAttr0701("timezone", feed.timezoneId)}` +
    `${xmlAttr0701("statusFonte", feed.sourceStatus)}>`,
  );
  lines.push(
    `  <consulta${xmlAttr0701("data", feed.query.date)}` +
    `${xmlAttr0701("origem", feed.query.origin)}` +
    `${xmlAttr0701("destino", feed.query.destination)} />`,
  );
  lines.push(
    `  <viagens count="${feed.trips.length}"${xmlAttr0701("ultimaAlteracaoMillis", feed.latestChangeAtMillis)}>`,
  );
  for (const trip of feed.trips) {
    lines.push(
      `    <viagem${xmlAttr0701("id", trip.id)}` +
      `${xmlAttr0701("data", trip.localDate)}` +
      `${xmlAttr0701("hora", trip.localTime)}` +
      `${xmlAttr0701("status", trip.status)}` +
      `${xmlAttr0701("capacidade", trip.capacity)}` +
      `${xmlAttr0701("capacidadeConfiavel", trip.capacityReliable)}` +
      `${xmlAttr0701("itinerarioAutoritativo", trip.itineraryAuthoritative)}` +
      `${xmlAttr0701("vagasRotaCompleta", trip.availableSeats)}` +
      `${xmlAttr0701("atualizadoEmMillis", trip.updatedAtMillis)}>`,
    );
    if (trip.title) lines.push(`      <titulo>${xmlEscape0701(trip.title)}</titulo>`);
    lines.push("      <paradas>");
    for (const stop of trip.stops) {
      lines.push(
        `        <parada${xmlAttr0701("ordem", stop.order)}${xmlAttr0701("nome", stop.name)}` +
        `${xmlAttr0701("chegadaMillis", stop.plannedArrivalMillis)}` +
        `${xmlAttr0701("saidaMillis", stop.plannedDepartureMillis)} />`,
      );
    }
    lines.push("      </paradas>");
    lines.push("      <trechos>");
    for (const segment of trip.segments) {
      lines.push(
        `        <trecho${xmlAttr0701("ordem", segment.index)}` +
        `${xmlAttr0701("origem", segment.from)}` +
        `${xmlAttr0701("destino", segment.to)}` +
        `${xmlAttr0701("ocupados", segment.occupiedSeats)}` +
        `${xmlAttr0701("passageiros", segment.passengerSeats)}` +
        `${xmlAttr0701("bloqueados", segment.blockedSeats)}` +
        `${xmlAttr0701("vagas", segment.availableSeats)} />`,
      );
    }
    lines.push("      </trechos>");
    if (trip.requestedSegment) {
      lines.push(
        `      <trechoConsultado${xmlAttr0701("encontrado", trip.requestedSegment.found)}` +
        `${xmlAttr0701("origem", trip.requestedSegment.origin)}` +
        `${xmlAttr0701("destino", trip.requestedSegment.destination)}` +
        `${xmlAttr0701("confiavel", trip.requestedSegment.reliable)}` +
        `${xmlAttr0701("ocupados", trip.requestedSegment.occupiedSeats)}` +
        `${xmlAttr0701("vagas", trip.requestedSegment.availableSeats)} />`,
      );
    }
    lines.push("    </viagem>");
  }
  lines.push("  </viagens>");
  lines.push("</agenda>");
  return lines.join("\n");
}

function sendFeed0701(res, status, body, format) {
  res.status(status);
  res.set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
  res.set("Pragma", "no-cache");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("X-Robots-Tag", "noindex, nofollow, noarchive");
  res.set("Referrer-Policy", "no-referrer");
  if (format === "xml") {
    res.set("Content-Type", "application/xml; charset=utf-8");
    return res.send(
      status >= 400
        ? `<?xml version="1.0" encoding="UTF-8"?><erro codigo="${xmlEscape0701(body.error || "feed_error")}">${xmlEscape0701(body.message || "Falha no feed.")}</erro>`
        : serializeXml0701(body),
    );
  }
  res.set("Content-Type", "application/json; charset=utf-8");
  return res.send(JSON.stringify(body));
}

function createLiveAgendaFeed0701({
  db,
  resolveDriverUsername,
  selectCanonicalTripDocuments0495,
  publicAgendaTripVisibility0466,
  safePublicTripWithCanonicalBookings0497,
}) {
  async function getLiveAgendaFeed0701(req, res, usernameRaw, formatRaw = "json") {
    const format = String(formatRaw || "").toLowerCase() === "xml" ? "xml" : "json";
    const usernameRequested = cleanText0701(usernameRaw, 80);
    try {
      const date = normalizeDate0701(req && req.query && (req.query.data || req.query.date));
      const origin = cleanText0701(req && req.query && (req.query.origem || req.query.origin), 180);
      const destination = cleanText0701(req && req.query && (req.query.destino || req.query.destination), 180);
      const resolvedDriver = await resolveDriverUsername(usernameRequested);
      const canonicalUsername = resolvedDriver ? cleanText0701(resolvedDriver.canonicalUsername, 80) : "";
      const publicUsername = resolvedDriver
        ? cleanText0701(resolvedDriver.publicUsername || resolvedDriver.requestedUsername || usernameRequested, 80)
        : "";
      if (!canonicalUsername || !resolvedDriver.driverSnap || !resolvedDriver.driverSnap.exists) {
        return sendFeed0701(res, 404, {
          error: "agenda_not_found",
          message: "Agenda não encontrada.",
        }, format);
      }

      const now = Date.now();
      const driver = resolvedDriver.driverSnap.data() || {};
      const snapshot = await db.collection("trips")
        .where("driverUsername", "==", canonicalUsername)
        .limit(200)
        .get();
      const canonicalDocs = selectCanonicalTripDocuments0495(snapshot.docs);
      const sourceDocs = canonicalDocs
        .filter((doc) => publicAgendaTripVisibility0466(driver, doc.id, doc.data(), now).visible)
        .sort((left, right) => Number(left.data().departureAtMillis || 0) - Number(right.data().departureAtMillis || 0))
        .slice(0, MAX_TRIPS_0701);

      const publicTrips = await Promise.all(
        sourceDocs.map((doc) => safePublicTripWithCanonicalBookings0497(doc)),
      );
      const query = { date, origin, destination };
      let trips = publicTrips.map((trip) => projectTrip0701(trip, query));

      if (date) trips = trips.filter((trip) => trip.localDate === date);
      if (origin && destination) {
        trips = trips.filter((trip) => trip.requestedSegment && trip.requestedSegment.found);
      } else if (origin || destination) {
        trips = trips.filter((trip) => tripMatchesOneSidedPlace0701(trip, origin, destination));
      }

      const latestChangeAtMillis = trips.reduce(
        (latest, trip) => Math.max(latest, safeInteger0701(trip.updatedAtMillis)),
        0,
      );
      const feed = {
        schemaVersion: FEED_SCHEMA_VERSION_0701,
        sourceStatus: "LIVE",
        generatedAtMillis: now,
        generatedAtIsoUtc: new Date(now).toISOString(),
        timezoneId: DEFAULT_TIMEZONE_0701,
        driverUsername: publicUsername || usernameRequested || canonicalUsername,
        query,
        count: trips.length,
        latestChangeAtMillis,
        trips,
      };
      return sendFeed0701(res, 200, feed, format);
    } catch (error) {
      return sendFeed0701(res, error && error.httpStatus || 500, {
        error: error && error.code || "live_feed_failed",
        message: error && error.message || "Falha ao consultar a agenda.",
      }, format);
    }
  }

  return { getLiveAgendaFeed0701 };
}

module.exports = {
  FEED_SCHEMA_VERSION_0701,
  normalizePlace0701,
  placeMatches0701,
  requestedSegment0701,
  projectTrip0701,
  serializeXml0701,
  createLiveAgendaFeed0701,
};
