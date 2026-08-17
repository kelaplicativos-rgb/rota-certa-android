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

exports.tripCalendarFeed = onRequest(
  { secrets: [publicCalendarToken], region: "southamerica-east1" },
  async (req, res) => {
    const path = (req.path || req.url || "").split("?")[0];
    const match = path.match(/\/calendar\/([A-Za-z0-9_-]{16,120})\.ics$/);
    const supplied = match ? match[1] : "";
    if (!safeEqual(supplied, publicCalendarToken.value())) {
      return res.status(404).set("Cache-Control", "no-store").send("Not found");
    }
    try {
      const snapshot = await db.collection("trips")
        .where("departureAtMillis", ">=", Date.now() - 6 * 60 * 60 * 1000)
        .orderBy("departureAtMillis", "asc")
        .limit(100)
        .get();
      const events = snapshot.docs
        .filter((doc) => allowedStatuses.has(doc.data().status))
        .map((doc) => tripEvent(doc.id, doc.data()));
      const body = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Rota Certa//Agenda Pública de Viagens//PT-BR",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:Rota Certa — Viagens",
        ...events,
        "END:VCALENDAR",
        "",
      ].join("\r\n");
      res.status(200)
        .set("Content-Type", "text/calendar; charset=utf-8")
        .set("Cache-Control", "public, max-age=60")
        .set("X-Content-Type-Options", "nosniff")
        .set("Referrer-Policy", "no-referrer")
        .send(body);
    } catch (error) {
      console.error("tripCalendarFeed", error);
      res.status(500).set("Cache-Control", "no-store").send("Calendar temporarily unavailable");
    }
  },
);
