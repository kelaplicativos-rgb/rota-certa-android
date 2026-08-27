"use strict";

const crypto = require("crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

initializeApp();
const db = getFirestore();
const publicCalendarToken = defineSecret("ROTA_CERTA_PUBLIC_CALENDAR_TOKEN");
const allowedStatuses = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);

function safeEqual(a, b) {
  if (!a || !b) return false;
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function normalizeUsername(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

async function agendaHashForDriver(username, driverSnap) {
  const linkRef = db.collection("tripPublicAgendaLinks").doc(username);
  const linkSnap = await linkRef.get();
  if (linkSnap.exists) return String(linkSnap.data().tokenHash || "");
  const legacyHash = String((driverSnap && driverSnap.exists && driverSnap.data().agendaTokenHash) || "");
  if (!legacyHash) return "";
  const now = Date.now();
  await linkRef.create({
    driverUsername: username,
    tokenHash: legacyHash,
    generation: 1,
    migratedFromLegacy: true,
    createdAtMillis: now,
    updatedAtMillis: now,
  }).catch(() => {});
  const after = await linkRef.get();
  return after.exists ? String(after.data().tokenHash || "") : legacyHash;
}

function escapeIcs(value) {
  return String(value || "")
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

function utc(ms) {
  return new Date(ms).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
}

function publicTrip(id, trip) {
  return {
    tripId: id,
    publicToken: trip.publicToken || id,
    title: trip.title || "Rota Certa — Viagem",
    departureAtMillis: trip.departureAtMillis,
    capacity: trip.capacity,
    status: trip.status,
    stops: Array.isArray(trip.stops) ? trip.stops : [],
    segmentLoads: Array.isArray(trip.segmentLoads) ? trip.segmentLoads : [],
    notes: trip.notes || "",
    publicUrl: trip.publicUrl || null,
    driverUsername: trip.driverUsername || "",
    driverDisplayName: trip.driverDisplayName || "",
    updatedAtMillis: trip.updatedAtMillis || null,
  };
}

function tripEvent(id, trip) {
  const stops = Array.isArray(trip.stops) ? [...trip.stops].sort((a, b) => a.order - b.order) : [];
  const first = stops[0] || {};
  const last = stops[stops.length - 1] || {};
  const begin = Number(first.plannedDepartureMillis || first.plannedArrivalMillis || trip.departureAtMillis);
  const end = Math.max(begin + 60000, Number(last.plannedArrivalMillis || (begin + 3600000)));
  const route = stops.map((stop) => stop.name).filter(Boolean).join(" → ");
  return [
    "BEGIN:VEVENT",
    `UID:${escapeIcs(id)}@rotacerta`,
    `DTSTAMP:${utc(Date.now())}`,
    `DTSTART:${utc(begin)}`,
    `DTEND:${utc(end)}`,
    `SUMMARY:${escapeIcs(trip.title || route || "Rota Certa — Viagem")}`,
    `LOCATION:${escapeIcs(first.address || first.name || "")}`,
    `DESCRIPTION:${escapeIcs(`Rota Certa | ${route}${trip.notes ? ` | ${trip.notes}` : ""}`)}`,
    trip.publicUrl ? `URL:${escapeIcs(trip.publicUrl)}` : null,
    "END:VEVENT",
  ].filter(Boolean).join("\r\n");
}

async function driverAgenda(usernameRaw, agendaToken) {
  const username = normalizeUsername(usernameRaw);
  if (!username || !agendaToken) return null;
  const driverRef = db.collection("tripDrivers").doc(username);
  const driverSnap = await driverRef.get();
  const agendaHash = driverSnap.exists ? await agendaHashForDriver(username, driverSnap) : "";
  if (!driverSnap.exists || !safeEqual(sha256Hex(agendaToken), agendaHash)) return null;
  const snapshot = await db.collection("trips").where("driverUsername", "==", username).limit(200).get();
  const cutoff = Date.now() - 6 * 60 * 60 * 1000;
  const documents = snapshot.docs
    .filter((doc) => {
      const trip = doc.data();
      return allowedStatuses.has(trip.status) && trip.publicBookingEnabled === true && Number(trip.departureAtMillis || 0) >= cutoff;
    })
    .sort((a, b) => Number(a.data().departureAtMillis || 0) - Number(b.data().departureAtMillis || 0))
    .slice(0, 100);
  return { username, driver: driverSnap.data(), documents };
}

async function legacyAgenda(supplied) {
  if (!safeEqual(supplied, publicCalendarToken.value())) return null;
  const snapshot = await db.collection("trips")
    .where("departureAtMillis", ">=", Date.now() - 6 * 60 * 60 * 1000)
    .orderBy("departureAtMillis", "asc")
    .limit(100)
    .get();
  return {
    username: "",
    driver: { displayName: "Rota Certa" },
    documents: snapshot.docs.filter((doc) => allowedStatuses.has(doc.data().status)),
  };
}

exports.tripCalendarFeed = onRequest(
  { secrets: [publicCalendarToken], region: "southamerica-east1" },
  async (req, res) => {
    const path = (req.path || req.url || "").split("?")[0];
    const scoped = path.match(/\/calendar\/([a-z0-9-]{3,32})\/([A-Za-z0-9_-]{16,120})\.(ics|json)$/);
    const legacy = path.match(/\/calendar\/([A-Za-z0-9_-]{16,120})\.(ics|json)$/);
    try {
      const agenda = scoped
        ? await driverAgenda(scoped[1], scoped[2])
        : legacy
          ? await legacyAgenda(legacy[1])
          : null;
      if (!agenda) return res.status(404).set("Cache-Control", "no-store").send("Not found");
      const format = scoped ? scoped[3] : legacy[2];
      if (format === "json") {
        const trips = agenda.documents.map((doc) => publicTrip(doc.id, doc.data()));
        return res.status(200)
          .set("Content-Type", "application/json; charset=utf-8")
          .set("Cache-Control", "public, max-age=30")
          .set("X-Content-Type-Options", "nosniff")
          .set("Referrer-Policy", "no-referrer")
          .send(JSON.stringify({ driver: { username: agenda.username, displayName: agenda.driver.displayName || agenda.username }, trips }));
      }
      const events = agenda.documents.map((doc) => tripEvent(doc.id, doc.data()));
      const calendarName = agenda.driver.displayName ? `Rota Certa — ${agenda.driver.displayName}` : "Rota Certa — Viagens";
      const body = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Rota Certa//Agenda Pública de Viagens//PT-BR",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        `X-WR-CALNAME:${escapeIcs(calendarName)}`,
        ...events,
        "END:VCALENDAR",
        "",
      ].join("\r\n");
      return res.status(200)
        .set("Content-Type", "text/calendar; charset=utf-8")
        .set("Cache-Control", "public, max-age=60")
        .set("X-Content-Type-Options", "nosniff")
        .set("Referrer-Policy", "no-referrer")
        .send(body);
    } catch (error) {
      console.error("tripCalendarFeed", error);
      return res.status(500).set("Cache-Control", "no-store").send("Calendar temporarily unavailable");
    }
  },
);
