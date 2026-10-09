"use strict";

const ALL_CONSULTS_SCHEMA_VERSION_0757 = "all-consults-v1";

function cleanText0757(value, maxLength = 160) {
  return String(value == null ? "" : value).trim().slice(0, maxLength);
}

function noStore0757(res) {
  res.set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
  res.set("Pragma", "no-cache");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("X-Robots-Tag", "noindex, nofollow, noarchive");
  res.set("Referrer-Policy", "no-referrer");
}

function send0757(res, status, body) {
  noStore0757(res);
  res.set("Content-Type", "application/json; charset=utf-8");
  return res.status(status).send(JSON.stringify(body));
}

function capabilities0757() {
  return [
    {
      id: "agenda",
      mode: "LIVE_CANONICAL",
      authority: "live-agenda-feed-v1",
      readOnly: true,
      supports: ["date", "origin", "destination", "profileUuid", "segments", "availability"],
    },
    {
      id: "trips",
      mode: "LIVE_CANONICAL",
      authority: "live-agenda-tool-v1",
      readOnly: true,
      supports: ["date", "origin", "destination", "profileUuid", "snapshotHash"],
    },
    {
      id: "covers",
      mode: "REMOTE_COLLECTOR",
      authority: "standalone-covers-remote-0736",
      readOnly: true,
      supports: ["latest", "refresh"],
    },
    {
      id: "blablacar-trip",
      mode: "REMOTE_COLLECTOR",
      authority: "blablacar-trip-query-remote-0737",
      readOnly: true,
      supports: ["profileUuid", "tripId", "latest", "refresh"],
    },
    {
      id: "technical-zip",
      mode: "REMOTE_COLLECTOR",
      authority: "remote-health-0747:TECHNICAL_ZIP",
      readOnly: true,
      supports: ["refresh", "latest", "download", "consent"],
      note: "ZIP sanitizado capturado somente apos consentimento do motorista.",
    },
    {
      id: "reports",
      mode: "DERIVED_QUERY",
      authority: "canonical-query-sources",
      readOnly: true,
      supports: ["agenda", "trips", "covers"],
      note: "Relatórios devem ser derivados das fontes canônicas; não constituem uma segunda base de dados.",
    },
  ];
}

function createAllConsults0757({ liveAgendaTool0732 }) {
  if (!liveAgendaTool0732 || typeof liveAgendaTool0732.getLiveAgendaTool0732 !== "function") {
    throw new Error("liveAgendaTool0732 is required");
  }

  async function getCapabilities0757(req, res) {
    return send0757(res, 200, {
      schemaVersion: ALL_CONSULTS_SCHEMA_VERSION_0757,
      generatedAtMillis: Date.now(),
      generatedAtIsoUtc: new Date().toISOString(),
      readOnly: true,
      controlEnabled: false,
      cachePolicy: "NO_STORE",
      capabilities: capabilities0757(),
    });
  }

  async function getHealth0757(req, res) {
    return send0757(res, 200, {
      schemaVersion: ALL_CONSULTS_SCHEMA_VERSION_0757,
      status: "READY",
      generatedAtMillis: Date.now(),
      generatedAtIsoUtc: new Date().toISOString(),
      readOnly: true,
      controlEnabled: false,
    });
  }

  async function getTrips0757(req, res, usernameRaw) {
    const username = cleanText0757(usernameRaw, 80);
    if (!username) {
      return send0757(res, 400, {
        schemaVersion: ALL_CONSULTS_SCHEMA_VERSION_0757,
        error: "driver_required",
        message: "Motorista obrigatório.",
      });
    }
    // Deliberately delegate to the existing live canonical tool. No snapshot,
    // cache or duplicate persistence is introduced by ALL Consults.
    return liveAgendaTool0732.getLiveAgendaTool0732(req, res, username);
  }

  return { getCapabilities0757, getHealth0757, getTrips0757 };
}

module.exports = {
  ALL_CONSULTS_SCHEMA_VERSION_0757,
  capabilities0757,
  createAllConsults0757,
};
