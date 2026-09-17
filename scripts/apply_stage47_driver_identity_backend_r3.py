#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
PATCHES = Path(sys.argv[2]).resolve()
BACKEND = PATCHES / "trip-platform/functions/index.js"


def once(old: str, new: str, label: str) -> None:
    text = BACKEND.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    BACKEND.write_text(text.replace(old, new, 1), encoding="utf-8")

once(
'''function requireDriver(req, res) {
  const supplied = req.get("X-Rota-Certa-Driver-Token") || "";
  const expected = driverTokenSecret.value() || "";
  if (!safeEqual(supplied, expected)) {
    fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
    return false;
  }
  return true;
}
''',
'''function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function normalizeUsername(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\\u0300-\\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

async function requireDriver(req, res) {
  const supplied = req.get("X-Rota-Certa-Driver-Token") || "";
  const username = normalizeUsername(req.get("X-Rota-Certa-Driver-Username") || "");
  if (username) {
    const driverSnap = await db.collection("tripDrivers").doc(username).get();
    if (!driverSnap.exists || !safeEqual(sha256Hex(supplied), driverSnap.data().driverTokenHash || "")) {
      fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
      return null;
    }
    const data = driverSnap.data();
    return { username, displayName: cleanText(data.displayName, 120), legacy: false };
  }
  const expected = driverTokenSecret.value() || "";
  if (!safeEqual(supplied, expected)) {
    fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
    return null;
  }
  return { username: "", displayName: "", legacy: true };
}
''',
"per-driver authentication",
)

once(
'''    publicUrl: data.publicUrl || null,
    updatedAtMillis: data.updatedAtMillis || null,
''',
'''    publicUrl: data.publicUrl || null,
    driverUsername: data.driverUsername || "",
    driverDisplayName: data.driverDisplayName || "",
    updatedAtMillis: data.updatedAtMillis || null,
''',
"public driver identity",
)

once(
'''function publicUrlFor(req, token) {
  const supplied = cleanText(req.get("X-Rota-Certa-Public-Base-Url"), 500).replace(/\\/$/, "");
  if (supplied.startsWith("https://")) return `${supplied}/?trip=${encodeURIComponent(token)}`;
  const proto = cleanText(req.get("x-forwarded-proto"), 12) || "https";
  const host = cleanText(req.get("x-forwarded-host") || req.get("host"), 300);
  return host ? `${proto}://${host}/?trip=${encodeURIComponent(token)}` : `/?trip=${encodeURIComponent(token)}`;
}
''',
'''function publicBaseFor(req) {
  const supplied = cleanText(req.get("X-Rota-Certa-Public-Base-Url"), 500).replace(/\\/$/, "");
  if (supplied.startsWith("https://")) return supplied;
  const proto = cleanText(req.get("x-forwarded-proto"), 12) || "https";
  const host = cleanText(req.get("x-forwarded-host") || req.get("host"), 300);
  return host ? `${proto}://${host}` : "";
}

function publicUrlFor(req, token, username = "") {
  const base = publicBaseFor(req);
  const query = username
    ? `?motorista=${encodeURIComponent(username)}&trip=${encodeURIComponent(token)}`
    : `?trip=${encodeURIComponent(token)}`;
  return base ? `${base}/${query}` : `/${query}`;
}

function publicAgendaUrlFor(req, username, agendaToken) {
  const base = publicBaseFor(req);
  const query = `?motorista=${encodeURIComponent(username)}&agenda=${encodeURIComponent(agendaToken)}`;
  return base ? `${base}/${query}` : `/${query}`;
}

function publicCalendarUrlFor(req, username, agendaToken) {
  const base = publicBaseFor(req);
  const path = `/calendar/${encodeURIComponent(username)}/${encodeURIComponent(agendaToken)}.ics`;
  return base ? `${base}${path}` : path;
}
''',
"public driver URLs",
)

once(
'''async function createDriverTrip(req, res) {
  if (!requireDriver(req, res)) return;
''',
'''async function registerDriver(req, res) {
  await enforceBookingRateLimit(req);
  const displayName = cleanText(req.body && req.body.displayName, 120);
  const username = normalizeUsername(req.body && req.body.username);
  if (!displayName) return fail(res, 400, "driver_name_required", "Informe o nome público do motorista.");
  if (username.length < 3 || username.length > 32) return fail(res, 400, "invalid_username", "Nome de usuário inválido.");
  const driverToken = crypto.randomBytes(32).toString("base64url");
  const publicAgendaToken = crypto.randomBytes(24).toString("base64url");
  const ref = db.collection("tripDrivers").doc(username);
  const now = Date.now();
  try {
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) throw Object.assign(new Error("Esse nome de usuário já está em uso."), { httpStatus: 409, code: "username_taken" });
      tx.create(ref, {
        username,
        displayName,
        driverTokenHash: sha256Hex(driverToken),
        agendaTokenHash: sha256Hex(publicAgendaToken),
        createdAtMillis: now,
        updatedAtMillis: now,
      });
    });
    return json(res, 201, {
      displayName,
      username,
      driverToken,
      publicAgendaToken,
      publicAgendaUrl: publicAgendaUrlFor(req, username, publicAgendaToken),
      calendarUrl: publicCalendarUrlFor(req, username, publicAgendaToken),
    });
  } catch (error) {
    return fail(res, error.httpStatus || 500, error.code || "driver_registration_failed", error.message || "Falha ao gerar o link do motorista.");
  }
}

async function getPublicDriverAgenda(res, req, usernameRaw, agendaToken) {
  const username = normalizeUsername(usernameRaw);
  if (!username || !agendaToken) return fail(res, 404, "agenda_not_found", "Agenda não encontrada.");
  const driverSnap = await db.collection("tripDrivers").doc(username).get();
  if (!driverSnap.exists || !safeEqual(sha256Hex(agendaToken), driverSnap.data().agendaTokenHash || "")) {
    return fail(res, 404, "agenda_not_found", "Agenda não encontrada.");
  }
  const driver = driverSnap.data();
  const snapshot = await db.collection("trips").where("driverUsername", "==", username).limit(200).get();
  const cutoff = Date.now() - 6 * 60 * 60 * 1000;
  const trips = snapshot.docs
    .map((doc) => safePublicTrip(doc.id, doc.data()))
    .filter((trip) => PUBLIC_STATUSES.has(trip.status) && Number(trip.departureAtMillis) >= cutoff)
    .sort((a, b) => Number(a.departureAtMillis) - Number(b.departureAtMillis))
    .slice(0, 100);
  return json(res, 200, {
    driver: { displayName: cleanText(driver.displayName, 120), username },
    trips,
  });
}

async function createDriverTrip(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
''',
"driver registration and create auth",
)

once(
'''  const publicUrl = publicUrlFor(req, token);
''',
'''  const publicUrl = publicUrlFor(req, token, driver.username);
''',
"driver trip public URL",
)

once(
'''        publicToken: token,
        publicUrl,
        segmentLoads: new Array(normalized.stops.length - 1).fill(0),
''',
'''        publicToken: token,
        publicUrl,
        driverUsername: driver.username,
        driverDisplayName: driver.displayName,
        segmentLoads: new Array(normalized.stops.length - 1).fill(0),
''',
"driver ownership on create",
)

once(
'''async function updateDriverTrip(req, res, token) {
  if (!requireDriver(req, res)) return;
''',
'''async function updateDriverTrip(req, res, token) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
''',
"update driver auth",
)

once(
'''      const previous = snap.data();
      const normalized = normalizeDriverTrip(req.body || {}, previous);
      const publicUrl = previous.publicUrl || publicUrlFor(req, token);
      tx.update(ref, { ...normalized, publicUrl, updatedAtMillis: Date.now() });
''',
'''      const previous = snap.data();
      if (previous.driverUsername && previous.driverUsername !== driver.username) {
        throw Object.assign(new Error("Viagem pertence a outro motorista."), { httpStatus: 403, code: "trip_owner_mismatch" });
      }
      const normalized = normalizeDriverTrip(req.body || {}, previous);
      const ownerUsername = previous.driverUsername || driver.username;
      const ownerDisplayName = previous.driverDisplayName || driver.displayName;
      const publicUrl = previous.publicUrl || publicUrlFor(req, token, ownerUsername);
      tx.update(ref, { ...normalized, publicUrl, driverUsername: ownerUsername, driverDisplayName: ownerDisplayName, updatedAtMillis: Date.now() });
''',
"driver ownership on update",
)

once(
'''async function listDriverBookings(req, res, token) {
  if (!requireDriver(req, res)) return;
  const tripSnap = await db.collection("trips").doc(token).get();
  if (!tripSnap.exists) return fail(res, 404, "trip_not_found", "Viagem não encontrada.");
''',
'''async function listDriverBookings(req, res, token) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  const tripSnap = await db.collection("trips").doc(token).get();
  if (!tripSnap.exists) return fail(res, 404, "trip_not_found", "Viagem não encontrada.");
  const tripData = tripSnap.data();
  if (tripData.driverUsername && tripData.driverUsername !== driver.username) return fail(res, 403, "trip_owner_mismatch", "Viagem pertence a outro motorista.");
''',
"driver ownership on bookings",
)

once(
'''  try {
    if (req.method === "POST" && path === "/v1/driver/trips") return await createDriverTrip(req, res);
''',
'''  try {
    if (req.method === "POST" && path === "/v1/drivers/register") return await registerDriver(req, res);
    if (req.method === "POST" && path === "/v1/driver/trips") return await createDriverTrip(req, res);
''',
"driver registration route",
)

once(
'''    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && req.method === "GET") {
      return await getPublicTrip(res, parts[3]);
    }
''',
'''    if (parts.length === 6 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "drivers" && parts[5] === "agenda" && req.method === "GET") {
      return await getPublicDriverAgenda(res, req, parts[3], parts[4]);
    }
    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && req.method === "GET") {
      return await getPublicTrip(res, parts[3]);
    }
''',
"public driver agenda route",
)

print("stage47_driver_identity_backend_r3=PASS per_driver_auth=true unique_username=true scoped_agenda=true legacy_driver_secret_preserved=true")
