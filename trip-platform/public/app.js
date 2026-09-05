"use strict";

const DateContract = window.RotaCertaDateContract;
if (!DateContract) throw new Error("Rota Certa date contract unavailable");
const PUBLIC_AGENDA_CARD_STATUSES_0469 = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);

const $ = (id) => document.getElementById(id);

function normalizePublicSlug(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

// Keep only paths that Firebase/Functions really own.
// Driver-chosen words such as "agenda" are valid when registered server-side.
const RESERVED_PUBLIC_SLUGS = new Set([
  "v1", "calendar",
]);

function publicSlugFromPath() {
  const parts = location.pathname.split("/").filter(Boolean);
  if (parts.length !== 1) return "";
  let raw = parts[0];
  try { raw = decodeURIComponent(raw); } catch (_) { return ""; }
  const normalized = normalizePublicSlug(raw);
  if (normalized.length < 3 || RESERVED_PUBLIC_SLUGS.has(normalized)) return "";
  return normalized;
}

const params = new URLSearchParams(location.search);
const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const publicSlug = publicSlugFromPath();
const queryDriverUsername = normalizePublicSlug(params.get("motorista") || "");
const driverUsername = queryDriverUsername || publicSlug;
const shortAgendaRoute = Boolean(publicSlug && !tripToken && !agendaToken);
const portalMode = params.get("portal") === "1";
const requestedBoardingStopId = (params.get("embarque") || "").replace(/[^A-Za-z0-9_-]/g, "");
const requestedDropoffStopId = (params.get("destino") || "").replace(/[^A-Za-z0-9_-]/g, "");
const requestedSeats = Math.max(1, Math.min(999, Math.floor(Number(params.get("lugares") || 1) || 1)));
const referralCode = (params.get("ref") || "").replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
const directReserveRequested = params.get("reservar") === "1";
const testerBootstrapToken = (params.get("tester") || "").replace(/[^A-Za-z0-9_-]/g, "").slice(0, 240);
const requestedAdminTripIdentity0470 = String(params.get("administrar") || "").trim().slice(0, 180);

let driverDisplayName = "";
let driverProfile = {};
let trip = null;
let confirmedBooking = null;
let pendingBooking = null;
let editingExistingBooking = false;
let passengerSessionToken = (() => {
  try {
    const token = sessionStorage.getItem("rotacerta-passenger-session") || "";
    localStorage.removeItem("rotacerta-passenger-session");
    return token;
  } catch (_) { return ""; }
})();
let passengerAgendaViewToken = (() => {
  try { return sessionStorage.getItem("rotacerta-agenda-view-session") || ""; } catch (_) { return ""; }
})();
let testerSessionToken = (() => {
  try { return sessionStorage.getItem("rotacerta-tester-session") || ""; } catch (_) { return ""; }
})();
let testerSessionContext = (() => {
  try { return JSON.parse(sessionStorage.getItem("rotacerta-tester-context") || "null"); } catch (_) { return null; }
})();
let passengerSessionContact = (() => {
  try { return localStorage.getItem("rotacerta-passenger-contact") || ""; } catch (_) { return ""; }
})();
const passengerSessionContextId0427 = (() => {
  const key = "rotacerta-passenger-session-context-0427";
  try {
    let value = (localStorage.getItem(key) || "").trim();
    if (!/^[A-Za-z0-9_-]{16,120}$/.test(value)) {
      value = (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function")
        ? globalThis.crypto.randomUUID()
        : "ctx_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2, 18);
      localStorage.setItem(key, value);
    }
    return value;
  } catch (_) {
    return "";
  }
})();
let agendaTripsCache = [];
const agendaAdminCardCapabilities0470 = new Map();
let agendaReturnScrollY0470 = 0;
let pendingAuthDestination = (portalMode || requestedAdminTripIdentity0470) ? "portal" : (tripToken ? "trip" : "agenda");
let calendarPickerTarget = "departure";
let seatPickerDraft = 1;
let seatPickerMode = "search";
let seatPickerBookingIntent = null;
let seatPickerChannel = "internal";
let seatPickerReturnView = "trip";
let seatPickerLimit = 0;
let passengerCreditBalanceCents = 0;
let passengerMustChangePassword = false;
let passengerViewAccountActivated = false;
let passengerAgendaAdmin0418 = false;
let agendaAuthenticationRequired0428 = true;
let pendingPrivateAction = "";
let bookingRequestInFlight = false;
let directReserveConsumed = false;
let quickUndoTimer = null;
let passengerUnreadNotificationCount = 0;
let passengerUnreadBookingIds = new Set();

function localTodayKey() {
  return DateContract.todayKey();
}

const searchState = {
  from: "",
  to: "",
  departure: localTodayKey(),
  returnDate: "",
  seats: 1,
  selectedFrom: null,
  selectedTo: null,
};
const searchSuggestionLists = { from: [], to: [] };
const searchSuggestionIndex = { from: -1, to: -1 };

const publicDebugSessionId = (() => {
  try {
    const key = "rotacerta-public-debug-session";
    const current = sessionStorage.getItem(key);
    if (current && /^[A-Za-z0-9_-]{16,80}$/.test(current)) return current;
    const next = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}_${Math.random().toString(36).slice(2)}`)
      .replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
    sessionStorage.setItem(key, next);
    return next;
  } catch (_) {
    return `s_${Date.now()}_${Math.random().toString(36).slice(2)}`
      .replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
  }
})();

function tracePublicAction(event, details = {}) {
  // TESTER has its own isolated server-side audit trail. Never feed production browser telemetry.
  if (testerBootstrapToken || isTesterMode()) return;
  const payload = {
    event,
    sessionId: publicDebugSessionId,
    screen: tripToken ? "trip" : ((agendaToken || publicSlug) ? "agenda" : "unknown"),
    tripToken: tripToken || "",
    agendaToken: tripToken ? "" : (agendaToken || ""),
    publicSlug: publicSlug || "",
    driverUsername: driverUsername || "",
    statusCode: Number(details.statusCode || 0),
    reason: String(details.reason || "").slice(0, 80),
    seats: Number(details.seats || 0),
    fromIndex: Number.isInteger(details.fromIndex) ? details.fromIndex : -1,
    toIndex: Number.isInteger(details.toIndex) ? details.toIndex : -1,
    replayed: details.replayed === true,
  };
  try {
    fetch("/v1/public/debug/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      keepalive: true,
    }).catch(() => {});
  } catch (_) {}
}

const mainSections = ["accessGate", "privateAuth", "agenda", "calendarPicker", "seatPicker", "searchResults", "trip", "passengerPortal", "booking", "review", "confirmed", "cancelBooking"];

function show(id, visible = true) {
  const node = $(id);
  if (node) node.classList.toggle("hidden", !visible);
}

function showOnly(id) {
  mainSections.forEach((key) => show(key, key === id));
  show("loading", false);
  show("error", false);
}

function setError(message) {
  $("error").textContent = message;
  show("error", true);
  show("loading", false);
}

function formatDate(ms) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "full", timeStyle: "short" }).format(new Date(ms));
}

function formatDateOnly(ms) {
  return new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "long" }).format(new Date(ms));
}

function formatTime(ms) {
  if (!ms) return "";
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(new Date(Number(ms)));
}

function formatMoney(cents) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .format(Math.max(0, Number(cents || 0)) / 100);
}

function durationFor(item) {
  const stops = orderedStops(item);
  if (!stops.length) return "";
  const start = Number(stops[0]?.plannedDepartureMillis || stops[0]?.plannedArrivalMillis || item?.departureAtMillis || 0);
  const end = Number(stops[stops.length - 1]?.plannedArrivalMillis || stops[stops.length - 1]?.plannedDepartureMillis || 0);
  if (!start || !end || end <= start) return "";
  const minutes = Math.round((end - start) / 60000);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? `${hours}h${String(rest).padStart(2, "0")}` : `${minutes} min`;
}

function normalizeWhatsapp(value) {
  let digits = String(value || "").replace(/\D/g, "");
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) digits = digits.slice(2);
  return digits.length === 10 || digits.length === 11 ? `+55${digits}` : "";
}

function whatsappDigits(value) {
  const normalized = normalizeWhatsapp(value);
  return normalized ? normalized.replace(/\D/g, "") : "";
}

function maskWhatsapp(value) {
  let digits = String(value || "").replace(/\D/g, "");
  if (digits.startsWith("55") && digits.length > 11) digits = digits.slice(2);
  digits = digits.slice(0, 11);
  if (digits.length <= 2) return digits;
  if (digits.length <= 6) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}

function orderedStops(source = trip) {
  return [...(source?.stops || [])].sort((a, b) => Number(a.order) - Number(b.order));
}

function seatRange(item) {
  const serverMinimum = Number(item.availableSeatsMinimum);
  const serverMaximum = Number(item.availableSeatsMaximum);
  if (Number.isFinite(serverMinimum) && Number.isFinite(serverMaximum)) {
    return { minimum: Math.max(0, serverMinimum), maximum: Math.max(0, serverMaximum) };
  }
  const loads = Array.isArray(item.segmentLoads) ? item.segmentLoads.map(Number) : [];
  if (!loads.length) return { minimum: Number(item.capacity || 0), maximum: Number(item.capacity || 0) };
  const available = loads.map((load) => Math.max(0, Number(item.capacity || 0) - load));
  return { minimum: Math.min(...available), maximum: Math.max(...available) };
}

function isFullTrip(item) {
  const range = seatRange(item);
  return item?.isFull === true || item?.status === "FULL" ||
    (range.minimum === 0 && range.maximum === 0);
}

function exactAvailabilityLabel(available, suffix = "") {
  const count = Math.max(0, Number(available || 0));
  if (count === 0) return "🪑 LOTADO";
  return count === 1
    ? `🪑 1 vaga disponível${suffix}`
    : `🪑 ${count} vagas disponíveis${suffix}`;
}

function publicAvailabilityLabel(item, available = null, filtered = false) {
  if (item?.capacityReliable !== true) return "Disponibilidade sendo atualizada";
  if (filtered && Number.isFinite(Number(available))) return exactAvailabilityLabel(available, " neste trecho");
  const range = seatRange(item);
  if (range.minimum === 0 && range.maximum === 0) return "🪑 LOTADO";
  if (range.minimum === range.maximum) return exactAvailabilityLabel(range.maximum);
  return `🪑 ${range.minimum}–${range.maximum} vagas disponíveis por trecho`;
}

function fullFareFor(item) {
  const stops = orderedStops(item);
  return stops.slice(0, -1).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
}

function availableFor(fromIndex, toIndex) {
  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;
  let available = Number(trip.capacity || 0);
  for (let i = fromIndex; i < toIndex; i += 1) {
    available = Math.min(available, Number(trip.capacity || 0) - Number((trip.segmentLoads || [])[i] || 0));
  }
  const operational = Number(trip.operationalAvailableSeats);
  if (Number.isFinite(operational)) available = Math.min(available, Math.max(0, operational));
  return Math.max(0, available);
}

function fareForTripSegment(source, fromIndex, toIndex) {
  if (!source || fromIndex < 0 || toIndex <= fromIndex) return 0;
  const stops = orderedStops(source);
  return stops.slice(fromIndex, toIndex)
    .reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
}

function fareFor(fromIndex, toIndex) {
  return fareForTripSegment(trip, fromIndex, toIndex);
}

function stopMoment(stop, index) {
  if (!stop) return "";
  const millis = index === 0
    ? (stop.plannedDepartureMillis || stop.plannedArrivalMillis || trip?.departureAtMillis)
    : (stop.plannedArrivalMillis || stop.plannedDepartureMillis);
  return formatTime(millis);
}

function routeLabel(item = trip) {
  const stops = orderedStops(item);
  const from = stops[0]?.name || "Origem";
  const to = stops[stops.length - 1]?.name || "Destino";
  return { from, to };
}

function setWhatsappLink(element, message) {
  if (!element) return false;
  if (isTesterMode()) {
    element.classList.add("hidden");
    element.removeAttribute("href");
    return false;
  }
  const digits = whatsappDigits(driverProfile.whatsapp || "");
  if (!digits) {
    element.classList.add("hidden");
    element.removeAttribute("href");
    return false;
  }
  element.href = `https://wa.me/${digits}?text=${encodeURIComponent(message || "")}`;
  element.target = "_blank";
  element.classList.remove("hidden");
  return true;
}

function defaultDriverMessage() {
  if (!trip) return `Olá, ${driverDisplayName || "motorista"}. Estou falando pela Agenda de Viagens do Rota Certa.`;
  const { from, to } = routeLabel(trip);
  return `Olá, ${driverDisplayName || "motorista"}. Estou falando pela Agenda de Viagens do Rota Certa sobre a viagem ${from} → ${to}, ${formatDate(trip.departureAtMillis)}.`;
}


function isTesterMode() {
  return Boolean(testerSessionToken);
}

function hasPrivatePortalSession() {
  return Boolean(testerSessionToken || passengerSessionToken);
}

function testerHeaders(extra = {}) {
  const headers = { ...extra };
  if (testerSessionToken) headers["X-Rota-Certa-Tester-Session"] = testerSessionToken;
  return headers;
}

function saveTesterSession(token, context = null) {
  testerSessionToken = String(token || "");
  testerSessionContext = context || null;
  try {
    if (testerSessionToken) sessionStorage.setItem("rotacerta-tester-session", testerSessionToken);
    else sessionStorage.removeItem("rotacerta-tester-session");
    if (testerSessionContext) sessionStorage.setItem("rotacerta-tester-context", JSON.stringify(testerSessionContext));
    else sessionStorage.removeItem("rotacerta-tester-context");
  } catch (_) {}
  updateTesterChrome();
  updateAuthenticatedChrome();
}

function updateTesterChrome() {
  show("testModeBanner", isTesterMode());
  if (isTesterMode() && $("testModeDetail")) {
    const id = String(testerSessionContext?.testSessionId || "");
    $("testModeDetail").textContent = `Tudo que você reservar, alterar ou cancelar aqui fica somente nesta simulação${id ? ` • sessão ${id.slice(0, 8)}` : ""}.`;
  }
}

function bookingStoragePrefix() {
  if (!isTesterMode()) return "rotacerta-";
  const sessionKey = String(testerSessionContext?.testSessionId || "session").replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 40);
  return `rotacerta-tester-${sessionKey}-`;
}

async function resetTesterSimulation() {
  if (!isTesterMode()) return;
  const button = $("resetTestSimulation");
  if (button) button.disabled = true;
  try {
    const response = await fetch("/v1/tester/reset", {
      method: "POST",
      headers: testerHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
      body: "{}",
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível reiniciar a simulação.");
    confirmedBooking = null;
    pendingBooking = null;
    editingExistingBooking = false;
    try {
      const prefix = bookingStoragePrefix();
      Object.keys(localStorage).filter((key) => key.startsWith(prefix + "booking-") || key.startsWith(prefix + "booking-intent-")).forEach((key) => localStorage.removeItem(key));
    } catch (_) {}
    await loadPassengerNotifications({ silent: true });
    await loadPassengerCredits();
    if (tripToken) await loadTrip();
    else await loadAgenda();
  } catch (error) {
    setError(error.message || "Falha ao reiniciar a simulação.");
  } finally {
    if (button) button.disabled = false;
  }
}

function authenticatedHeaders(extra = {}) {
  const headers = { ...extra };
  if (testerSessionToken) headers["X-Rota-Certa-Tester-Session"] = testerSessionToken;
  else if (passengerSessionToken) headers.Authorization = `Bearer ${passengerSessionToken}`;
  return headers;
}

function agendaViewHeaders(extra = {}) {
  const headers = { ...extra };
  if (testerSessionToken) headers["X-Rota-Certa-Tester-Session"] = testerSessionToken;
  else if (passengerAgendaViewToken) headers["X-Rota-Certa-Agenda-View-Token"] = passengerAgendaViewToken;
  return headers;
}

function savePassengerContact(contact) {
  passengerSessionContact = normalizeWhatsapp(contact || "");
  try {
    if (passengerSessionContact) localStorage.setItem("rotacerta-passenger-contact", passengerSessionContact);
    else localStorage.removeItem("rotacerta-passenger-contact");
  } catch (_) {}
}

function saveAgendaViewSession(token) {
  passengerAgendaViewToken = String(token || "");
  try {
    if (passengerAgendaViewToken) sessionStorage.setItem("rotacerta-agenda-view-session", passengerAgendaViewToken);
    else sessionStorage.removeItem("rotacerta-agenda-view-session");
  } catch (_) {}
  updateAuthenticatedChrome();
}

function updateAuthenticatedChrome() {
  show("openPassengerPortal", Boolean(isTesterMode() || !agendaAuthenticationRequired0428 || passengerAgendaViewToken || passengerSessionToken));
  show("passengerNotificationsBell", Boolean(isTesterMode() || passengerSessionToken));
  const areaTitle = document.querySelector("#openPassengerPortal .passengerAreaTitle");
  const areaSub = document.querySelector("#openPassengerPortal .passengerAreaSub");
  if (areaTitle) areaTitle.textContent = "Minha Área";
  if (areaSub) areaSub.textContent = passengerAgendaAdmin0418 ? "Administrador" : "Reservas e conta";
  show("portalLogout", Boolean(!isTesterMode() && passengerSessionToken));
  show("portalAgendaAdminCard0418", Boolean(
    !isTesterMode() && passengerSessionToken && passengerAgendaAdmin0418
  ));
  show("portalPasswordWrap0428", Boolean(agendaAuthenticationRequired0428));
  show("portalPasswordCard0428", Boolean(agendaAuthenticationRequired0428 && !isTesterMode()));
  if (!hasPrivatePortalSession()) {
    passengerUnreadNotificationCount = 0;
    show("passengerNotificationBadge", false);
  }
}

function showAccessGate(destination = pendingAuthDestination, message = "") {
  pendingAuthDestination = destination || "agenda";
  pendingPrivateAction = "";
  if (isTesterMode()) return continueAfterAuthentication();
  showOnly("accessGate");
  updateAuthenticatedChrome();
  const hasPublicTarget = Boolean(driverUsername && (agendaToken || publicSlug || tripToken));
  show("accessLoginBox", hasPublicTarget);
  show("referralRequestBox", Boolean(referralCode && driverUsername));
  $("accessMessage").textContent = message;
  if (passengerSessionContact && !$("accessContact").value) $("accessContact").value = maskWhatsapp(passengerSessionContact);
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function requestPublicAgendaAccess(contactInput = "") {
  if (isTesterMode()) {
    await continueAfterViewAccess();
    return true;
  }
  const passengerContact = normalizeWhatsapp(contactInput || $("accessContact").value || passengerSessionContact);
  if (!passengerContact) {
    $("accessMessage").textContent = "Informe seu WhatsApp com DDD.";
    return false;
  }
  if (!driverUsername || (!agendaToken && !publicSlug && !tripToken)) {
    $("accessMessage").textContent = "Este link não identifica uma agenda válida.";
    return false;
  }
  $("accessLogin").disabled = true;
  $("accessMessage").className = "muted";
  $("accessMessage").textContent = "Verificando acesso…";
  tracePublicAction("PUBLIC_ACCESS_CONTACT_SUBMITTED", { reason: "contact_present" });
  try {
    const response = await fetch("/v1/public/passenger-access", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ passengerContact, driverUsername, agendaToken, publicSlug, tripToken }),
    });
    const body = await response.json();
    if (!response.ok) {
      tracePublicAction("PUBLIC_ACCESS_DENIED", { statusCode: response.status, reason: `http_${response.status}` });
      saveAgendaViewSession("");
      $("accessMessage").className = "error";
      $("accessMessage").textContent = body.message || "Acesso negado. Este WhatsApp não pertence a um passageiro autorizado nesta Agenda de Viagens.";
      return false;
    }
    savePassengerContact(body.passengerContact || passengerContact);
    saveAgendaViewSession(body.viewToken);
    passengerViewAccountActivated = body.accountActivated === true;
    tracePublicAction("PUBLIC_ACCESS_GRANTED", { statusCode: response.status, reason: "authorized" });
    $("accessMessage").className = "muted";
    $("accessMessage").textContent = "";
    await continueAfterViewAccess();
    return true;
  } catch (error) {
    tracePublicAction("PUBLIC_ACCESS_DENIED", { reason: "network_or_client_error" });
    $("accessMessage").className = "error";
    $("accessMessage").textContent = error.message || "Não foi possível verificar o acesso.";
    return false;
  } finally {
    $("accessLogin").disabled = false;
  }
}

async function validatePassengerSession() {
  if (!passengerSessionToken) return false;
  try {
    const response = await fetch(
      "/v1/passenger/me?driverUsername=" + encodeURIComponent(driverUsername || ""),
      { headers: authenticatedHeaders({ Accept: "application/json" }) },
    );
    const body = await response.json();
    if (!response.ok) {
      savePassengerSession("");
      return false;
    }
    savePassengerContact(body.passengerContact || passengerSessionContact);
    passengerMustChangePassword = body.mustChangePassword === true;
    passengerViewAccountActivated = true;
    passengerAgendaAdmin0418 = body.agendaAdmin === true;
    updateAuthenticatedChrome();
    return true;
  } catch (_) {
    return false;
  }
}

async function continueAfterViewAccess() {
  updateAuthenticatedChrome();
  if (portalMode || pendingAuthDestination === "portal") {
    if (passengerSessionToken) return openPassengerPortal();
    return showPrivateAuthGate("portal");
  }
  if (tripToken) return loadTrip();
  if (agendaToken || publicSlug) return loadAgenda();
  return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
}

async function continueAfterAuthentication() {
  updateAuthenticatedChrome();
  const resume = pendingPrivateAction;
  pendingPrivateAction = "";
  if (resume === "reserve") {
    if (!pendingBooking) pendingBooking = restorePendingBookingIntent();
    return reserve();
  }
  if (resume === "undo") return undoQuickBooking();
  if (resume === "update") return updateExistingReservation();
  if (resume === "cancel") return cancelReservation();
  if (resume === "edit") return beginExistingReservationEdit();
  if (resume === "showCancel") {
    showOnly("cancelBooking");
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }
  if (pendingAuthDestination === "portal" && passengerAgendaAdmin0418 && (agendaToken || publicSlug)) {
    await loadAgenda({ restoreScrollY: agendaReturnScrollY0470 });
    if (requestedAdminTripIdentity0470) setTimeout(() => openRequestedAdminTrip0470(), 0);
    return;
  }
  if (pendingAuthDestination === "portal") return openPassengerPortal();
  if (pendingAuthDestination === "booking") return openBookingFlow();
  if (pendingAuthDestination === "review") {
    showOnly("review");
    return;
  }
  if (pendingAuthDestination === "trip" && tripToken) return loadTrip();
  if (agendaToken || publicSlug) return loadAgenda();
  return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
}

async function loginAccessGate() {
  await requestPublicAgendaAccess();
}

function showPrivateAuthGate(destination = "portal", resumeAction = "") {
  if (destination === "portal" && (agendaToken || publicSlug)) {
    agendaReturnScrollY0470 = Math.max(0, Math.floor(window.scrollY || 0));
  }
  if (isTesterMode()) {
    pendingAuthDestination = destination;
    pendingPrivateAction = resumeAction;
    return continueAfterAuthentication();
  }
  pendingAuthDestination = destination;
  pendingPrivateAction = resumeAction;

  if (!agendaAuthenticationRequired0428) {
    $("privateAuthContact").readOnly = false;
    $("privateAuthContact").value = maskWhatsapp(passengerSessionContact || "");
    $("privateAuthPassword").value = "";
    $("privateAuthPasswordConfirm").value = "";
    $("privateAuthMessage").textContent = "";
    show("privateAuthPasswordWrap0428", false);
    show("privateAuthConfirmWrap", false);
    $("privateAuthTitle").textContent = "Identifique suas viagens";
    $("privateAuthIntro").textContent = "A autenticação desta Agenda está desligada. Informe seu WhatsApp apenas para localizar suas viagens e reservas; nenhuma senha será solicitada.";
    $("privateAuthSubmit").textContent = "Continuar sem senha";
    tracePublicAction("PUBLIC_PRIVATE_AUTH_SHOWN", { reason: "authentication_disabled" });
    showOnly("privateAuth");
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }

  if (!passengerAgendaViewToken || !passengerSessionContact) {
    return showAccessGate(destination, "Informe seu WhatsApp para continuar.");
  }
  $("privateAuthContact").readOnly = true;
  $("privateAuthContact").value = maskWhatsapp(passengerSessionContact);
  $("privateAuthPassword").value = "";
  $("privateAuthPasswordConfirm").value = "";
  $("privateAuthMessage").textContent = "";
  show("privateAuthPasswordWrap0428", true);
  show("privateAuthConfirmWrap", !passengerViewAccountActivated);
  $("privateAuthTitle").textContent = passengerViewAccountActivated ? "Entre para continuar" : "Crie sua senha para continuar";
  $("privateAuthIntro").textContent = passengerViewAccountActivated
    ? "Sua senha protege reservas e informações privadas."
    : "Esta será a senha da sua área particular. Seu cadastro atual será mantido.";
  $("privateAuthSubmit").textContent = passengerViewAccountActivated ? "Entrar" : "Criar senha e continuar";
  tracePublicAction("PUBLIC_PRIVATE_AUTH_SHOWN", {
    reason: passengerViewAccountActivated ? "login" : "activate",
  });
  showOnly("privateAuth");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function submitPrivateAuthentication() {
  if (isTesterMode()) return continueAfterAuthentication();
  if (!agendaAuthenticationRequired0428) {
    const passengerContact = normalizeWhatsapp($("privateAuthContact").value || passengerSessionContact);
    if (!passengerContact) {
      $("privateAuthMessage").textContent = "Informe seu WhatsApp com DDD para localizar suas viagens.";
      return;
    }
    $("privateAuthSubmit").disabled = true;
    $("privateAuthMessage").textContent = "Localizando suas viagens…";
    try {
      const response = await fetch("/v1/passenger/session", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ passengerContact, driverUsername, sessionContextId: passengerSessionContextId0427 }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || "Não foi possível localizar suas viagens.");
      savePassengerSession(body.sessionToken);
      savePassengerContact(body.passengerContact || passengerContact);
      passengerMustChangePassword = false;
      passengerViewAccountActivated = true;
      passengerAgendaAdmin0418 = body.agendaAdmin === true;
      agendaAuthenticationRequired0428 = body.authenticationRequired !== false;
      updateAuthenticatedChrome();
      tracePublicAction("PUBLIC_PRIVATE_AUTH_SUCCESS", { statusCode: response.status, reason: "authentication_disabled" });
      await continueAfterAuthentication();
    } catch (error) {
      $("privateAuthMessage").textContent = error.message || "Falha ao localizar suas viagens.";
    } finally {
      $("privateAuthSubmit").disabled = false;
    }
    return;
  }
  const passengerContact = passengerSessionContact;
  const password = $("privateAuthPassword").value;
  const confirmation = $("privateAuthPasswordConfirm").value;
  if (!passengerContact || password.length < 8 || password.length > 72) {
    tracePublicAction("PUBLIC_PRIVATE_AUTH_FAILED", { reason: "invalid_input_shape" });
    $("privateAuthMessage").textContent = "Use uma senha de 8 a 72 caracteres.";
    return;
  }
  if (!passengerViewAccountActivated && password !== confirmation) {
    tracePublicAction("PUBLIC_PRIVATE_AUTH_FAILED", { reason: "confirmation_mismatch" });
    $("privateAuthMessage").textContent = "As senhas não conferem.";
    return;
  }
  $("privateAuthSubmit").disabled = true;
  $("privateAuthMessage").textContent = passengerViewAccountActivated ? "Entrando…" : "Criando sua senha…";
  try {
    const activating = !passengerViewAccountActivated;
    const response = await fetch(activating ? "/v1/passenger/activate" : "/v1/passenger/session", {
      method: "POST",
      headers: activating
        ? agendaViewHeaders({ "Content-Type": "application/json", Accept: "application/json" })
        : { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ passengerContact, password, driverUsername, sessionContextId: passengerSessionContextId0427 }),
    });
    const body = await response.json();
    if (!response.ok) {
      tracePublicAction("PUBLIC_PRIVATE_AUTH_FAILED", {
        statusCode: response.status,
        reason: `http_${response.status}`,
      });
      if (body.error === "passenger_account_already_activated") {
        passengerViewAccountActivated = true;
        showPrivateAuthGate(pendingAuthDestination, pendingPrivateAction);
        $("privateAuthMessage").textContent = "Sua senha já existe. Entre com ela para continuar.";
        return;
      }
      throw new Error(body.message || "Não foi possível autenticar.");
    }
    savePassengerSession(body.sessionToken);
    savePassengerContact(body.passengerContact || passengerContact);
    passengerMustChangePassword = body.mustChangePassword === true;
    passengerViewAccountActivated = true;
    passengerAgendaAdmin0418 = body.agendaAdmin === true;
    updateAuthenticatedChrome();
    tracePublicAction("PUBLIC_PRIVATE_AUTH_SUCCESS", {
      statusCode: response.status,
      reason: activating ? "activated" : "authenticated",
    });
    await continueAfterAuthentication();
  } catch (error) {
    $("privateAuthMessage").textContent = error.message || "Falha ao autenticar.";
  } finally {
    $("privateAuthSubmit").disabled = false;
  }
}

function closePrivateAuth() {
  pendingPrivateAction = "";
  if (trip) return renderTrip();
  if ((agendaToken || publicSlug) && agendaTripsCache.length) return renderAgenda(agendaTripsCache);
  if (agendaToken || publicSlug) return loadAgenda();
  return showAccessGate("agenda");
}

async function requestReferralInvite() {
  if (!referralCode || !driverUsername) return;
  const displayName = $("referralRequestName").value.trim();
  const passengerContact = normalizeWhatsapp($("referralRequestContact").value);
  if (!displayName || !passengerContact) {
    $("referralRequestMessage").textContent = "Informe seu nome e WhatsApp com DDD.";
    return;
  }
  $("referralRequestSubmit").disabled = true;
  $("referralRequestMessage").textContent = "Enviando sua solicitação…";
  try {
    const response = await fetch("/v1/public/referrals/request", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ driverUsername, referralCode, displayName, passengerContact }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível solicitar o convite.");
    $("referralRequestMessage").textContent = "Solicitação enviada. Aguarde o motorista liberar seu acesso e enviar sua senha temporária.";
    $("referralRequestSubmit").disabled = true;
  } catch (error) {
    $("referralRequestMessage").textContent = error.message || "Falha ao solicitar o convite.";
    $("referralRequestSubmit").disabled = false;
  }
}

async function hydrateAgendaAdminCapabilities0470() {
  agendaAdminCardCapabilities0470.clear();
  if (isTesterMode() || !passengerSessionToken || !passengerAgendaAdmin0418 || !driverUsername) return false;
  try {
    const response = await fetch("/v1/admin/card-capabilities", {
      headers: authenticatedHeaders({
        Accept: "application/json",
        "X-Rota-Certa-Admin-Driver": driverUsername,
      }),
    });
    const body = await response.json();
    if (!response.ok) {
      if (response.status === 401) {
        savePassengerSession("");
        passengerAgendaAdmin0418 = false;
        updateAuthenticatedChrome();
      } else if (response.status === 403) {
        passengerAgendaAdmin0418 = false;
        updateAuthenticatedChrome();
      }
      return false;
    }
    (Array.isArray(body.cards) ? body.cards : []).forEach((card) => {
      const canonicalTripId = String(card && card.canonicalTripId || "").trim();
      if (canonicalTripId) agendaAdminCardCapabilities0470.set(canonicalTripId, card);
    });
    return true;
  } catch (_) {
    return false;
  }
}

function applyAgendaAdminCapabilities0470(trips) {
  return (Array.isArray(trips) ? trips : []).map((item) => {
    const canonicalTripId = String(item && item.canonicalTripId || "").trim();
    const adminContext0470 = canonicalTripId ? agendaAdminCardCapabilities0470.get(canonicalTripId) || null : null;
    return { ...item, adminContext0470 };
  });
}

async function loadAgenda(options = {}) {
  if (driverUsername.length < 3 || (!publicSlug && agendaToken.length < 16)) return setError("Link de agenda inválido.");
  let statusCode = 0;
  try {
    const endpoint = publicSlug
      ? `/v1/public/agenda/${encodeURIComponent(publicSlug)}`
      : `/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`;
    const response = await fetch(
      endpoint,
      { headers: agendaViewHeaders({ Accept: "application/json" }) },
    );
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok && (response.status === 401 || response.status === 403)) {
      if (isTesterMode()) {
        saveTesterSession("");
        return setError(body.message || "Sessão de teste encerrada. Gere um novo link de teste no aplicativo.");
      }
      saveAgendaViewSession("");
      if (passengerSessionContact) {
        const opened = await requestPublicAgendaAccess(passengerSessionContact);
        if (opened) return;
      }
      return showAccessGate("agenda", body.message || "Informe seu WhatsApp novamente.");
    }
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    agendaAuthenticationRequired0428 = body.authenticationRequired !== false;
    driverProfile = body.driver || {};
    driverDisplayName = driverProfile.displayName || driverUsername;
    updateAuthenticatedChrome();
    tracePublicAction("PUBLIC_AGENDA_LOADED", { statusCode });
    agendaTripsCache = Array.isArray(body.trips) ? body.trips : [];
    await hydrateAgendaAdminCapabilities0470();
    agendaTripsCache = applyAgendaAdminCapabilities0470(agendaTripsCache);
    if (publicSlug) $("subscribeCalendar").textContent = "Compartilhar link da Agenda";
    if (portalMode && !passengerAgendaAdmin0418) return openPassengerPortal();
    renderAgenda(agendaTripsCache);
    if (Number.isFinite(Number(options.restoreScrollY))) {
      window.scrollTo({ top: Math.max(0, Number(options.restoreScrollY)), behavior: "auto" });
    }
  } catch (error) {
    tracePublicAction("PUBLIC_AGENDA_LOAD_FAILED", { statusCode, reason: "client_load_error" });
    setError(error.message || "Não foi possível carregar a agenda.");
  }
}


function normalizeSearchText(value) {
  return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function dateKeyFromMillis(ms) {
  return DateContract.keyFromDate(new Date(Number(ms || 0)));
}

function formatSearchDate(key) {
  return DateContract.formatFriendly(key, {
    todayKey: localTodayKey(),
    placeholder: "Data",
    locale: "pt-BR",
  });
}

function updateSearchUi() {
  const fromInput = $("searchFromInput");
  const toInput = $("searchToInput");
  if (fromInput && fromInput.value !== searchState.from) fromInput.value = searchState.from;
  if (toInput && toInput.value !== searchState.to) toInput.value = searchState.to;
  $("searchDepartureValue").textContent = formatSearchDate(searchState.departure);
}

function openCalendarPicker() {
  calendarPickerTarget = "departure";
  $("calendarTitle").textContent = "Quando?";
  renderCalendarMonths();
  showOnly("calendarPicker");
  window.scrollTo({ top: 0, behavior: "auto" });
}

function renderCalendarMonths() {
  const container = $("calendarMonths");
  container.innerHTML = "";
  const minimumKey = localTodayKey();
  const minimum = DateContract.parseKey(minimumKey);
  const monthStart = new Date(minimum.getFullYear(), minimum.getMonth(), 1);
  const selected = searchState.departure;
  const week = ["D", "S", "T", "Q", "Q", "S", "S"];
  for (let offset = 0; offset < 12; offset += 1) {
    const first = new Date(monthStart.getFullYear(), monthStart.getMonth() + offset, 1);
    const lastDay = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const card = document.createElement("div");
    card.className = "calendarMonth";
    const title = document.createElement("h2");
    title.className = "calendarMonthTitle";
    title.textContent = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" }).format(first);
    const weekRow = document.createElement("div");
    weekRow.className = "calendarWeek";
    week.forEach((label) => { const span = document.createElement("span"); span.textContent = label; weekRow.appendChild(span); });
    const grid = document.createElement("div");
    grid.className = "calendarGrid";
    for (let blank = 0; blank < first.getDay(); blank += 1) {
      const cell = document.createElement("span");
      cell.className = "calendarBlank";
      grid.appendChild(cell);
    }
    for (let day = 1; day <= lastDay; day += 1) {
      const date = new Date(first.getFullYear(), first.getMonth(), day);
      const key = DateContract.keyFromDate(date);
      const button = document.createElement("button");
      button.type = "button";
      button.className = "calendarDay" + (key === selected ? " calendarDaySelected" : "");
      button.textContent = String(day);
      button.disabled = DateContract.isBefore(key, minimumKey);
      button.addEventListener("click", () => selectCalendarDate(key));
      grid.appendChild(button);
    }
    card.append(title, weekRow, grid);
    container.appendChild(card);
  }
}

function selectCalendarDate(key) {
  searchState.departure = key;
  searchState.returnDate = "";
  invalidateSearchSelections();
  updateSearchUi();
  renderAgenda(agendaTripsCache);
}

function clearReturnDate() {
  searchState.returnDate = "";
  updateSearchUi();
  renderAgenda(agendaTripsCache);
}

function normalizedSeatCount(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function segmentLoadRange(item, field, fromIndex = 0, toIndex = null) {
  const stops = orderedStops(item);
  const end = Number.isInteger(toIndex) ? toIndex : Math.max(0, stops.length - 1);
  const fallbackLoads = Array.isArray(item && item.segmentLoads) ? item.segmentLoads.map(normalizedSeatCount) : [];
  let loads = Array.isArray(item && item[field]) ? item[field].map(normalizedSeatCount) : [];
  if (!loads.length && field === "segmentPassengerLoads") loads = fallbackLoads;
  if (!loads.length && field === "segmentBlockedLoads") loads = fallbackLoads.map(() => 0);
  const selected = loads.slice(Math.max(0, fromIndex), Math.max(0, end));
  if (!selected.length) return { minimum: 0, maximum: 0 };
  return { minimum: Math.min(...selected), maximum: Math.max(...selected) };
}

function rangeText(range, singular, plural) {
  if (range.minimum === range.maximum) {
    return `${range.maximum} ${range.maximum === 1 ? singular : plural}`;
  }
  return `${range.minimum}–${range.maximum} ${plural} por trecho`;
}

function seatAvailabilityText(available) {
  const seats = normalizedSeatCount(available);
  if (seats === 0) return "Nenhuma vaga disponível para este trecho.";
  if (seats === 1) return "1 vaga disponível para este trecho.";
  return `${seats} vagas disponíveis para este trecho.`;
}

function seatLimitText(available, changed = false) {
  const seats = normalizedSeatCount(available);
  if (seats === 0) return changed
    ? "Não há mais vagas disponíveis para este trecho."
    : "Não há vagas disponíveis para este trecho.";
  const prefix = changed ? "Agora este carro tem" : "Este carro tem";
  return seats === 1
    ? `${prefix} apenas 1 vaga disponível para este trecho.`
    : `${prefix} apenas ${seats} vagas disponíveis para este trecho.`;
}

function bestSearchAvailability(dateKey, fromSelection = null, toSelection = null) {
  let best = 0;
  agendaTripsCache.filter((item) => tripSearchEligible(item, dateKey)).forEach((item) => {
    const stops = orderedStops(item);
    const fromIndexes = [];
    if (fromSelection) {
      const exactFrom = selectedStopIndex(item, fromSelection);
      if (exactFrom >= 0) fromIndexes.push(exactFrom);
    } else {
      for (let index = 0; index < stops.length - 1; index += 1) {
        if (stopEvidenceTrusted(item, index, stops)) fromIndexes.push(index);
      }
    }
    fromIndexes.forEach((fromIndex) => {
      if (toSelection) {
        const toIndex = selectedStopIndex(item, toSelection, fromIndex);
        if (toIndex > fromIndex && segmentEvidenceTrusted(item, fromIndex, toIndex)) {
          best = Math.max(best, availableForTripSegment(item, fromIndex, toIndex));
        }
        return;
      }
      for (let toIndex = fromIndex + 1; toIndex < stops.length; toIndex += 1) {
        if (!stopEvidenceTrusted(item, toIndex, stops)) continue;
        if (!segmentEvidenceTrusted(item, fromIndex, toIndex)) continue;
        best = Math.max(best, availableForTripSegment(item, fromIndex, toIndex));
      }
    });
  });
  return best;
}

function searchSeatAvailabilityLimit() {
  const outbound = bestSearchAvailability(
    searchState.departure,
    searchState.selectedFrom,
    searchState.selectedTo,
  );
  if (!searchState.returnDate) return outbound;
  const returning = bestSearchAvailability(
    searchState.returnDate,
    searchState.selectedTo,
    searchState.selectedFrom,
  );
  return Math.min(outbound, returning);
}

function updateSeatPickerUi(capacityChanged = false) {
  $("seatPickerValue").textContent = String(seatPickerDraft);
  if (seatPickerMode === "booking") {
    $("seatPickerAvailability").textContent = seatAvailabilityText(seatPickerLimit);
    $("seatPickerMessage").textContent = seatPickerDraft > seatPickerLimit
      ? seatLimitText(seatPickerLimit, capacityChanged)
      : "";
  } else {
    const seats = normalizedSeatCount(seatPickerLimit);
    $("seatPickerAvailability").textContent = seats === 0
      ? "Nenhuma viagem disponível para os filtros atuais."
      : (seats === 1
        ? "Há no máximo 1 vaga real disponível nos filtros atuais."
        : `Há no máximo ${seats} vagas reais disponíveis nos filtros atuais.`);
    $("seatPickerMessage").textContent = seatPickerDraft > seatPickerLimit
      ? (seats === 0
        ? "Não há vagas disponíveis para os filtros atuais."
        : `A busca atual possui no máximo ${seats} ${seats === 1 ? "vaga disponível" : "vagas disponíveis"}.`)
      : "";
  }
  $("seatMinus").disabled = seatPickerDraft <= 1;
  $("seatConfirm").disabled = seatPickerLimit < 1 || seatPickerDraft > seatPickerLimit;
}

function openSeatPicker() {
  seatPickerMode = "search";
  seatPickerBookingIntent = null;
  seatPickerLimit = searchSeatAvailabilityLimit();
  seatPickerDraft = Math.max(1, normalizedSeatCount(searchState.seats));
  updateSeatPickerUi(seatPickerDraft > seatPickerLimit);
  showOnly("seatPicker");
}

function openTripSeatPicker(auto = false, intentOverride = null, capacityChanged = false) {
  if (!trip || bookingRequestInFlight) return;
  seatPickerChannel = "internal";
  seatPickerReturnView = "trip";
  if (auto) {
    if (directReserveConsumed) return;
    directReserveConsumed = true;
  }
  if (trip.capacityReliable !== true) {
    showQuickBookingNotice("Reserva indisponível", "A capacidade desta viagem ainda não foi confirmada.", true);
    return;
  }
  const stops = orderedStops();
  if (stops.length < 2) return;
  const requestedIntent = intentOverride || {};
  const desiredBoarding = requestedIntent.boardingStopId || requestedBoardingStopId;
  const desiredDropoff = requestedIntent.dropoffStopId || requestedDropoffStopId;
  let fromIndex = desiredBoarding ? stops.findIndex((stop) => stop.id === desiredBoarding) : 0;
  let toIndex = desiredDropoff ? stops.findIndex((stop) => stop.id === desiredDropoff) : stops.length - 1;
  if (fromIndex < 0) fromIndex = 0;
  if (toIndex <= fromIndex) toIndex = stops.length - 1;
  if (!segmentEvidenceTrusted(trip, fromIndex, toIndex)) {
    showQuickBookingNotice("Reserva indisponível", "Esse trecho ainda não foi confirmado pela fonte da viagem.", true);
    return;
  }

  seatPickerMode = "booking";
  seatPickerBookingIntent = {
    boardingStopId: stops[fromIndex].id,
    dropoffStopId: stops[toIndex].id,
    creditToUseCents: Math.max(0, Number(requestedIntent.creditToUseCents || 0)),
    quick: requestedIntent.quick !== false,
  };
  seatPickerLimit = availableFor(fromIndex, toIndex);
  const desiredSeats = normalizedSeatCount(requestedIntent.seats == null ? requestedSeats : requestedIntent.seats);
  seatPickerDraft = Math.max(1, desiredSeats || 1);
  updateSeatPickerUi(capacityChanged || seatPickerDraft > seatPickerLimit);
  tracePublicAction("PUBLIC_SEAT_PICKER_OPENED", { seats: seatPickerDraft, fromIndex, toIndex });
  showOnly("seatPicker");
}

function openWhatsappSeatPicker(source, fromIndex = 0, toIndex = null, returnView = "searchResults") {
  if (!source || source.capacityReliable !== true) return;
  const stops = orderedStops(source);
  const resolvedTo = Number.isInteger(toIndex) ? toIndex : stops.length - 1;
  if (fromIndex < 0 || resolvedTo <= fromIndex || resolvedTo >= stops.length) return;
  if (!segmentEvidenceTrusted(source, fromIndex, resolvedTo)) {
    showQuickBookingNotice("Trecho indisponível", "Esse trecho ainda não foi confirmado pela fonte da viagem.", true);
    return;
  }
  if (availableForTripSegment(source, fromIndex, resolvedTo) < 1) return;
  trip = source;
  seatPickerMode = "booking";
  seatPickerChannel = "whatsapp";
  seatPickerReturnView = returnView;
  seatPickerBookingIntent = {
    boardingStopId: stops[fromIndex].id,
    dropoffStopId: stops[resolvedTo].id,
    creditToUseCents: 0,
    quick: false,
  };
  seatPickerLimit = source.capacityReliable === true
    ? availableForTripSegment(source, fromIndex, resolvedTo)
    : 0;
  seatPickerDraft = 1;
  updateSeatPickerUi(seatPickerLimit < 1);
  if (source.capacityReliable !== true) {
    $("seatPickerMessage").textContent = "As vagas desta viagem ainda não foram confirmadas.";
  }
  tracePublicAction("PUBLIC_SEAT_PICKER_OPENED", { seats: 1, fromIndex, toIndex: resolvedTo, reason: "whatsapp" });
  showOnly("seatPicker");
}

function changeSeatPicker(delta) {
  if (seatPickerMode === "search") seatPickerLimit = searchSeatAvailabilityLimit();
  if (delta < 0) {
    seatPickerDraft = Math.max(1, seatPickerDraft - 1);
    updateSeatPickerUi(seatPickerDraft > seatPickerLimit);
    return;
  }
  const candidate = seatPickerDraft + 1;
  if (candidate > seatPickerLimit) {
    updateSeatPickerUi(false);
    $("seatPickerMessage").textContent = seatPickerMode === "booking"
      ? seatLimitText(seatPickerLimit, false)
      : (seatPickerLimit === 0
        ? "Não há vagas disponíveis para os filtros atuais."
        : `A busca atual possui no máximo ${seatPickerLimit} ${seatPickerLimit === 1 ? "vaga disponível" : "vagas disponíveis"}.`);
    return;
  }
  seatPickerDraft = candidate;
  updateSeatPickerUi(false);
}

async function confirmSeatPicker() {
  if (seatPickerMode === "search") {
    seatPickerLimit = searchSeatAvailabilityLimit();
    if (seatPickerLimit < 1 || seatPickerDraft > seatPickerLimit) {
      updateSeatPickerUi(true);
      return;
    }
    searchState.seats = seatPickerDraft;
    invalidateSearchSelections();
    updateSearchUi();
    renderAgenda(agendaTripsCache);
    return;
  }

  if (!seatPickerBookingIntent || seatPickerLimit < 1 || seatPickerDraft > seatPickerLimit) {
    updateSeatPickerUi(true);
    return;
  }
  if (seatPickerChannel === "whatsapp") {
    return openWhatsappFromSeatPicker();
  }
  pendingBooking = { ...seatPickerBookingIntent, seats: seatPickerDraft };
  persistPendingBookingIntent(pendingBooking);
  const stops = orderedStops();
  tracePublicAction("PUBLIC_RESERVATION_STARTED", {
    seats: seatPickerDraft,
    fromIndex: stops.findIndex((stop) => stop.id === pendingBooking.boardingStopId),
    toIndex: stops.findIndex((stop) => stop.id === pendingBooking.dropoffStopId),
  });
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("trip", "reserve");
  if (editingExistingBooking && confirmedBooking?.bookingId && confirmedBooking?.cancellationToken) {
    return updateExistingReservation();
  }
  return reserve();
}

async function openWhatsappFromSeatPicker() {
  const intent = seatPickerBookingIntent;
  const token = publicTripKey(trip);
  if (!intent || !token) return;
  $("seatConfirm").disabled = true;
  $("seatPickerMessage").textContent = "Confirmando as vagas…";
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(token)}`, {
      headers: agendaViewHeaders({ Accept: "application/json" }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível confirmar as vagas agora.");
    trip = body;
    if (body.driver) {
      driverProfile = body.driver;
      driverDisplayName = driverProfile.displayName || driverDisplayName;
    }
    const stops = orderedStops(trip);
    const fromIndex = stops.findIndex((stop) => stop.id === intent.boardingStopId);
    const toIndex = stops.findIndex((stop) => stop.id === intent.dropoffStopId);
    if (trip.capacityReliable !== true || !segmentEvidenceTrusted(trip, fromIndex, toIndex)) {
      seatPickerLimit = 0;
      updateSeatPickerUi(true);
      $("seatPickerMessage").textContent = "As vagas deste trecho ainda não foram confirmadas.";
      return;
    }
    const available = availableForTripSegment(trip, fromIndex, toIndex);
    seatPickerLimit = available;
    if (available < 1 || seatPickerDraft > available) {
      updateSeatPickerUi(true);
      $("seatPickerMessage").textContent = seatLimitText(available, true);
      return;
    }
    const digits = whatsappDigits(driverProfile.whatsapp || "");
    if (!digits) {
      $("seatPickerMessage").textContent = "O motorista ainda não cadastrou um WhatsApp válido.";
      return;
    }
    const from = stops[fromIndex];
    const to = stops[toIndex];
    const time = formatTime(from?.plannedDepartureMillis || from?.plannedArrivalMillis || trip.departureAtMillis);
    const fare = fareForTripSegment(trip, fromIndex, toIndex);
    const quantity = seatPickerDraft === 1 ? "1 lugar" : `${seatPickerDraft} lugares`;
    const message = [
      "Olá! Quero viajar com você.",
      `${from?.name || "Embarque"} → ${to?.name || "Destino"}`,
      `${formatDateOnly(trip.departureAtMillis)} às ${time} • ${quantity}`,
      fare > 0 ? `Valor: ${formatMoney(fare * seatPickerDraft)}` : "",
      `Viagem: ${trip.title || ((from?.name || "") + " → " + (to?.name || ""))}`,
      "Ainda está disponível?",
    ].filter(Boolean).join("\n");
    tracePublicAction("PUBLIC_WHATSAPP_RESERVATION_OPENED", {
      seats: seatPickerDraft,
      fromIndex,
      toIndex,
      reason: "availability_revalidated_no_booking",
    });
    location.href = `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
  } catch (error) {
    $("seatPickerMessage").textContent = error.message || "Não foi possível confirmar as vagas agora.";
  } finally {
    $("seatConfirm").disabled = seatPickerLimit < 1 || seatPickerDraft > seatPickerLimit;
  }
}

function isOfficialBlaBlaHost(hostname) {
  const labels = String(hostname || "").trim().toLowerCase().replace(/^\.+|\.+$/g, "").split(".").filter(Boolean);
  const root = labels[0] === "www" ? labels.slice(1) : labels;
  if (root[0] !== "blablacar") return false;
  const suffix = root.slice(1);
  if (suffix.length === 1) return suffix[0] === "com" || /^[a-z]{2}$/.test(suffix[0]);
  if (suffix.length === 2) return ["com", "co"].includes(suffix[0]) && /^[a-z]{2}$/.test(suffix[1]);
  return false;
}

function safeBlaBlaPublicUrl(item) {
  const raw = String(item?.blablaPublicUrl || "").trim();
  const administrativeTripId = String(item?.blablaTripId || "").trim();
  if (!raw || !administrativeTripId) return "";
  try {
    const url = new URL(raw);
    const path = url.pathname.replace(/\/+$/, "").toLowerCase();
    if (url.protocol !== "https:" || !isOfficialBlaBlaHost(url.hostname)) return "";
    if (url.username || url.password || (url.port && url.port !== "443")) return "";
    if (path !== "/trip" && !path.startsWith("/trip/")) return "";
    const publicTripToken = String(url.searchParams.get("id") || url.pathname.match(/\/trip\/([^/?#]+)/i)?.[1] || "").trim();
    if (!publicTripToken) return "";
    // The server only exposes this field from the committed canonical projection.
    // BlaBlaCar's public /trip token may differ from the administrative trip ID.
    url.searchParams.delete("search_uuid");
    url.hash = "";
    return url.href;
  } catch (_) {
    return "";
  }
}

function stopMatchesSearch(stop, query) {
  const needle = normalizeSearchText(query);
  if (!needle) return false;
  const name = normalizeSearchText(stop && stop.name);
  const address = normalizeSearchText(stop && stop.address);
  return name === needle || name.includes(needle) || address.includes(needle);
}

function publicTripKey(item) {
  return String(item?.publicToken || item?.tripId || "");
}

function canonicalStopKey(stop) {
  const name = normalizeSearchText(stop && stop.name);
  const address = normalizeSearchText(stop && stop.address);
  return name + "|" + (address || name);
}

function stopEvidenceTrusted(item, index, stops = orderedStops(item)) {
  if (index < 0 || index >= stops.length) return false;
  return index === 0 || index === stops.length - 1 || item?.itineraryAuthoritative === true;
}

function segmentEvidenceTrusted(item, fromIndex, toIndex) {
  const stops = orderedStops(item);
  if (fromIndex < 0 || toIndex <= fromIndex || toIndex >= stops.length) return false;
  if (item?.itineraryAuthoritative === true) return true;
  return fromIndex === 0 && toIndex === stops.length - 1;
}

function availableForTripSegment(item, fromIndex, toIndex) {
  if (fromIndex < 0 || toIndex <= fromIndex) return 0;
  let available = Number(item.capacity || 0);
  for (let index = fromIndex; index < toIndex; index += 1) {
    available = Math.min(available, Number(item.capacity || 0) - Number((item.segmentLoads || [])[index] || 0));
  }
  const operational = Number(item.operationalAvailableSeats);
  if (Number.isFinite(operational)) available = Math.min(available, Math.max(0, operational));
  return Math.max(0, available);
}

function tripSearchEligible(item, dateKey) {
  return ["PUBLISHED", "FULL"].includes(item?.status) &&
    item?.publicBookingEnabled === true &&
    dateKeyFromMillis(item.departureAtMillis) === dateKey &&
    orderedStops(item).length >= 2;
}

function publicSegmentReservable(item, fromIndex, toIndex, seats, dateKey) {
  return tripSearchEligible(item, dateKey) &&
    segmentEvidenceTrusted(item, fromIndex, toIndex) &&
    availableForTripSegment(item, fromIndex, toIndex) >= seats;
}

function wholeTripReservable(item, seats) {
  const stops = orderedStops(item);
  return item?.publicBookingEnabled === true &&
    item?.capacityReliable === true &&
    !isFullTrip(item) &&
    stops.length >= 2 &&
    seatRange(item).minimum >= seats;
}

function addStopSuggestion(groups, item, stop, stopIndex) {
  const key = canonicalStopKey(stop);
  if (!key || key === "|") return;
  let suggestion = groups.get(key);
  if (!suggestion) {
    suggestion = {
      key,
      name: String(stop?.name || "").trim(),
      address: String(stop?.address || "").trim(),
      candidates: [],
    };
    groups.set(key, suggestion);
  }
  const candidate = { tripKey: publicTripKey(item), stopId: String(stop?.id || ""), stopIndex };
  if (!suggestion.candidates.some((entry) =>
    entry.tripKey === candidate.tripKey && entry.stopId === candidate.stopId && entry.stopIndex === candidate.stopIndex
  )) suggestion.candidates.push(candidate);
}

function buildSearchSuggestions(kind, query, dateKey = searchState.departure, seats = searchState.seats) {
  const groups = new Map();
  const needle = normalizeSearchText(query);
  const fromSelection = searchState.selectedFrom;
  agendaTripsCache.filter((item) => tripSearchEligible(item, dateKey)).forEach((item) => {
    const stops = orderedStops(item);
    if (kind === "from") {
      for (let fromIndex = 0; fromIndex < stops.length - 1; fromIndex += 1) {
        if (!stopEvidenceTrusted(item, fromIndex, stops)) continue;
        const hasDestination = stops.some((_, toIndex) =>
          toIndex > fromIndex && stopEvidenceTrusted(item, toIndex, stops) &&
            segmentEvidenceTrusted(item, fromIndex, toIndex)
        );
        if (hasDestination) addStopSuggestion(groups, item, stops[fromIndex], fromIndex);
      }
      return;
    }
    if (!fromSelection) return;
    const fromIndexes = stops.map((stop, index) => ({ stop, index }))
      .filter(({ stop, index }) => stopEvidenceTrusted(item, index, stops) && canonicalStopKey(stop) === fromSelection.key)
      .map(({ index }) => index);
    fromIndexes.forEach((fromIndex) => {
      for (let toIndex = fromIndex + 1; toIndex < stops.length; toIndex += 1) {
        if (!stopEvidenceTrusted(item, toIndex, stops)) continue;
        if (segmentEvidenceTrusted(item, fromIndex, toIndex)) {
          addStopSuggestion(groups, item, stops[toIndex], toIndex);
        }
      }
    });
  });
  tracePublicAction("PUBLIC_STOP_CATALOG_BUILT", { seats, reason: kind });
  const result = [...groups.values()].filter((suggestion) => {
    if (!needle) return true;
    return normalizeSearchText(suggestion.name).startsWith(needle) ||
      normalizeSearchText(suggestion.address).startsWith(needle);
  }).sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
  tracePublicAction("PUBLIC_SEARCH_SUGGESTIONS_BUILT", { seats, reason: kind });
  return result;
}

function applySearchSelection(kind, suggestion) {
  if (!suggestion) return;
  if (kind === "from") {
    searchState.selectedFrom = suggestion;
    searchState.from = suggestion.name;
    searchState.selectedTo = null;
  } else {
    searchState.selectedTo = suggestion;
    searchState.to = suggestion.name;
  }
  const input = $(kind === "from" ? "searchFromInput" : "searchToInput");
  if (input) input.value = suggestion.name;
  closeSearchSuggestions();
  tracePublicAction("PUBLIC_SEARCH_STOP_RESOLVED", { seats: searchState.seats, reason: kind });
  if (kind === "from" && searchState.to) renderSearchSuggestions("to");
}

function closeSearchSuggestions() {
  ["from", "to"].forEach((kind) => {
    const element = $(kind === "from" ? "searchFromSuggestions" : "searchToSuggestions");
    if (element) {
      element.innerHTML = "";
      element.classList.add("hidden");
    }
    searchSuggestionLists[kind] = [];
    searchSuggestionIndex[kind] = -1;
  });
}

function invalidateSearchSelections() {
  searchState.selectedFrom = null;
  searchState.selectedTo = null;
  closeSearchSuggestions();
}

function paintSuggestionActive(kind) {
  const container = $(kind === "from" ? "searchFromSuggestions" : "searchToSuggestions");
  if (!container) return;
  [...container.querySelectorAll(".searchSuggestion")].forEach((button, index) => {
    button.classList.toggle("searchSuggestionActive", index === searchSuggestionIndex[kind]);
  });
}

function renderSearchSuggestions(kind) {
  const input = $(kind === "from" ? "searchFromInput" : "searchToInput");
  const container = $(kind === "from" ? "searchFromSuggestions" : "searchToSuggestions");
  if (!input || !container || (kind === "to" && !searchState.selectedFrom)) {
    if (container) container.classList.add("hidden");
    return;
  }
  const suggestions = buildSearchSuggestions(kind, input.value);
  searchSuggestionLists[kind] = suggestions.slice(0, 12);
  searchSuggestionIndex[kind] = suggestions.length ? 0 : -1;
  container.innerHTML = "";
  searchSuggestionLists[kind].forEach((suggestion) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "searchSuggestion";
    const name = document.createElement("span");
    name.className = "searchSuggestionName";
    name.textContent = suggestion.name;
    button.appendChild(name);
    const secondary = suggestion.address && normalizeSearchText(suggestion.address) !== normalizeSearchText(suggestion.name)
      ? suggestion.address
      : "";
    if (secondary) {
      const address = document.createElement("span");
      address.className = "searchSuggestionAddress";
      address.textContent = secondary;
      button.appendChild(address);
    }
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => applySearchSelection(kind, suggestion));
    container.appendChild(button);
  });
  container.classList.toggle("hidden", searchSuggestionLists[kind].length === 0);
  paintSuggestionActive(kind);
}

function handleSearchInput(kind, event) {
  const value = String(event.target.value || "");
  searchState[kind] = value;
  const selectedKey = kind === "from" ? "selectedFrom" : "selectedTo";
  const selected = searchState[selectedKey];
  if (selected && normalizeSearchText(value) !== normalizeSearchText(selected.name)) searchState[selectedKey] = null;
  if (kind === "from") searchState.selectedTo = null;
  $("searchMessage").textContent = "";
  renderSearchSuggestions(kind);
}

function handleSearchKeydown(kind, event) {
  const suggestions = searchSuggestionLists[kind];
  if (event.key === "ArrowDown" && suggestions.length) {
    event.preventDefault();
    searchSuggestionIndex[kind] = Math.min(suggestions.length - 1, searchSuggestionIndex[kind] + 1);
    paintSuggestionActive(kind);
    return;
  }
  if (event.key === "ArrowUp" && suggestions.length) {
    event.preventDefault();
    searchSuggestionIndex[kind] = Math.max(0, searchSuggestionIndex[kind] - 1);
    paintSuggestionActive(kind);
    return;
  }
  if (event.key === "Escape") {
    closeSearchSuggestions();
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    if (suggestions.length) {
      applySearchSelection(kind, suggestions[Math.max(0, searchSuggestionIndex[kind])]);
      return;
    }
    submitTripSearch();
  }
}

function resolveCanonicalSelection(kind) {
  const input = $(kind === "from" ? "searchFromInput" : "searchToInput");
  const value = String(input?.value || searchState[kind] || "").trim();
  searchState[kind] = value;
  const selectedKey = kind === "from" ? "selectedFrom" : "selectedTo";
  const current = searchState[selectedKey];
  if (current && normalizeSearchText(current.name) === normalizeSearchText(value)) return { selection: current, reason: "" };
  if (kind === "to" && !searchState.selectedFrom) return { selection: null, reason: "Selecione primeiro o ponto de embarque." };
  const options = buildSearchSuggestions(kind, value);
  const normalized = normalizeSearchText(value);
  const exact = options.filter((option) =>
    normalizeSearchText(option.name) === normalized || normalizeSearchText(option.address) === normalized
  );
  const chosen = exact.length === 1 ? exact[0] : (exact.length === 0 && options.length === 1 ? options[0] : null);
  if (chosen) {
    applySearchSelection(kind, chosen);
    return { selection: chosen, reason: "" };
  }
  if (options.length > 1 || exact.length > 1) {
    tracePublicAction("PUBLIC_SEARCH_STOP_AMBIGUOUS", { seats: searchState.seats, reason: kind });
    renderSearchSuggestions(kind);
    return { selection: null, reason: "Há mais de um ponto correspondente. Selecione uma opção da lista." };
  }
  tracePublicAction("PUBLIC_SEARCH_STOP_NOT_FOUND", { seats: searchState.seats, reason: kind });
  return { selection: null, reason: "Esse local não aparece nas viagens disponíveis para os filtros selecionados." };
}

function selectedStopIndex(item, selection, afterIndex = -1) {
  if (!selection) return -1;
  const stops = orderedStops(item);
  const key = publicTripKey(item);
  const exact = (selection.candidates || []).find((candidate) =>
    candidate.tripKey === key &&
    candidate.stopIndex > afterIndex &&
    stops[candidate.stopIndex]?.id === candidate.stopId
  );
  if (exact) return exact.stopIndex;
  return stops.findIndex((stop, index) =>
    index > afterIndex && stopEvidenceTrusted(item, index, stops) && canonicalStopKey(stop) === selection.key
  );
}

function matchTripSegment(item, fromSelection, toSelection, dateKey, seats) {
  if (!tripSearchEligible(item, dateKey)) return null;
  const fromIndex = selectedStopIndex(item, fromSelection);
  if (fromIndex < 0) return null;
  const toIndex = selectedStopIndex(item, toSelection, fromIndex);
  if (toIndex < 0 || !segmentEvidenceTrusted(item, fromIndex, toIndex)) return null;
  const available = item.capacityReliable === true ? availableForTripSegment(item, fromIndex, toIndex) : 0;
  return { item, fromIndex, toIndex, available, capacityReliable: item.capacityReliable === true };
}

function searchDirection(fromSelection, toSelection, dateKey, seats) {
  const eligible = agendaTripsCache.filter((item) => tripSearchEligible(item, dateKey));
  const matches = eligible.map((item) => matchTripSegment(item, fromSelection, toSelection, dateKey, seats)).filter(Boolean);
  if (!matches.length) {
    tracePublicAction("PUBLIC_SEARCH_DIRECTION_REJECTED", { seats, reason: "route_or_date" });
    return { matches: [], reason: "Não há viagem publicada com essas paradas, nessa ordem, para essa data." };
  }
  matches.forEach((entry) => tracePublicAction("PUBLIC_SEARCH_MATCH_CONFIRMED", {
    seats,
    fromIndex: entry.fromIndex,
    toIndex: entry.toIndex,
  }));
  return { matches, reason: "" };
}


function renderSearchSummary() {
  const summary = $("searchSummary");
  summary.innerHTML = "";
  const route = document.createElement("div");
  route.className = "searchSummaryRoute";
  route.textContent = `${searchState.from} → ${searchState.to}`;
  const meta = document.createElement("div");
  meta.className = "searchSummaryMeta";
  meta.textContent = formatSearchDate(searchState.departure);
  summary.append(route, meta);
}

function renderDirectionResult(containerId, titleText, result) {
  const container = $(containerId);
  container.innerHTML = "";
  const title = document.createElement("h2");
  title.className = "resultSectionTitle";
  title.textContent = titleText;
  container.appendChild(title);
  if (!result.matches.length) {
    const empty = document.createElement("div");
    empty.className = "resultEmpty";
    empty.textContent = `Nenhuma viagem encontrada para esse trecho. ${result.reason}`;
    container.appendChild(empty);
    return;
  }
  renderAgendaCards(result.matches, container, true);
}

function submitTripSearch() {
  searchState.from = String($("searchFromInput")?.value || searchState.from || "").trim();
  searchState.to = String($("searchToInput")?.value || searchState.to || "").trim();
  $("searchMessage").textContent = "";
  if (!searchState.from || !searchState.to) {
    $("searchMessage").textContent = "Informe De e Para para procurar.";
    return;
  }
  const fromResolution = resolveCanonicalSelection("from");
  if (!fromResolution.selection) {
    $("searchMessage").textContent = fromResolution.reason;
    return;
  }
  const toResolution = resolveCanonicalSelection("to");
  if (!toResolution.selection) {
    $("searchMessage").textContent = toResolution.reason;
    return;
  }
  if (fromResolution.selection.key === toResolution.selection.key) {
    $("searchMessage").textContent = "Origem e destino precisam ser diferentes.";
    return;
  }
  const outbound = searchDirection(fromResolution.selection, toResolution.selection, searchState.departure, 1);
  renderSearchSummary();
  renderDirectionResult("outboundResult", "Viagens disponíveis", outbound);
  showOnly("searchResults");
  window.scrollTo({ top: 0, behavior: "smooth" });
}


function swapSearchRoute() {
  const previous = searchState.from;
  const previousSelection = searchState.selectedFrom;
  searchState.from = searchState.to;
  searchState.to = previous;
  searchState.selectedFrom = searchState.selectedTo;
  searchState.selectedTo = previousSelection;
  updateSearchUi();
}

function renderAgenda(trips) {
  showOnly("agenda");
  $("driverName").textContent = driverDisplayName ? "Viagens com " + driverDisplayName : "Próximas viagens";
  updateSearchUi();
  const container = $("agendaTrips");
  container.innerHTML = "";
  const visibleTrips = trips.filter((item) =>
    PUBLIC_AGENDA_CARD_STATUSES_0469.has(item?.status) &&
    item?.publicBookingEnabled === true &&
    orderedStops(item).length >= 2
  );
  if (!visibleTrips.length) {
    const empty = document.createElement("div");
    empty.className = "card muted";
    empty.textContent = "Nenhuma próxima viagem publicada.";
    container.appendChild(empty);
    return;
  }
  renderAgendaCards(visibleTrips.map((item) => ({ item })), container, false);
}


function renderAgendaCards(entries, container, filtered = false) {
  entries.forEach((entry) => {
    const item = entry.item || entry;
    const full = isFullTrip(item);
    const range = seatRange(item);
    const stops = orderedStops(item);
    const fromIndex = filtered && Number.isInteger(entry.fromIndex) ? entry.fromIndex : 0;
    const toIndex = filtered && Number.isInteger(entry.toIndex) ? entry.toIndex : stops.length - 1;
    const segmentAvailable = item.capacityReliable === true
      ? (filtered && Number.isFinite(Number(entry.available))
        ? Math.max(0, Number(entry.available))
        : availableForTripSegment(item, fromIndex, toIndex))
      : 0;
    const soldOut = item.capacityReliable === true && segmentAvailable === 0;
    const actionsEnabled = item.capacityReliable === true && segmentAvailable > 0;
    const from = stops[fromIndex]?.name || "Origem";
    const to = stops[toIndex]?.name || "Destino";
    const fare = filtered ? fareForTripSegment(item, fromIndex, toIndex) : fullFareFor(item);
    const card = document.createElement("article");
    card.className = (full || soldOut) ? "agendaTrip agendaTripFull" : "agendaTrip";
    const canonicalTripId0470 = String(item.canonicalTripId || "").trim();
    if (canonicalTripId0470) card.dataset.canonicalTripId = canonicalTripId0470;

    const owner = item.driverUsername || driverUsername;
    const detailsParams = new URLSearchParams({ motorista: owner, trip: item.publicToken || item.tripId });
    if (filtered && Number.isInteger(entry.fromIndex) && Number.isInteger(entry.toIndex)) {
      detailsParams.set("embarque", stops[fromIndex]?.id || "");
      detailsParams.set("destino", stops[toIndex]?.id || "");
    }

    const date = document.createElement("div");
    date.className = "agendaDate";
    date.textContent = formatDateOnly(item.departureAtMillis);
    const routeFrom = document.createElement("div");
    routeFrom.className = "agendaRouteLine";
    routeFrom.textContent = from;
    const arrow = document.createElement("div");
    arrow.className = "agendaArrow";
    arrow.textContent = "↓";
    const routeTo = document.createElement("div");
    routeTo.className = "agendaRouteLine";
    routeTo.textContent = to;
    const meta = document.createElement("div");
    meta.className = "agendaMetaRow";
    const time = document.createElement("span");
    time.className = "bigPill";
    time.textContent = `🕘 ${formatTime(item.departureAtMillis)}`;
    const seats = document.createElement("span");
    seats.className = "bigPill";
    seats.textContent = publicAvailabilityLabel(item, segmentAvailable, filtered);
    meta.append(time, seats);
    if (item.capacityReliable === true) {
      const passengers = document.createElement("span");
      passengers.className = "bigPill";
      passengers.textContent = `👥 Passageiros confirmados: ${normalizedSeatCount(item.confirmedPassengerSeats)}`;
      meta.appendChild(passengers);

      const blockedTotal = normalizedSeatCount(item.blockedSeats);
      if (blockedTotal > 0) {
        const blocked = document.createElement("span");
        blocked.className = "bigPill";
        blocked.textContent = `🚫 Vagas bloqueadas: ${blockedTotal}`;
        meta.appendChild(blocked);
      }

      const blablaAvailable = item.blablaAvailableSeats == null ? null : normalizedSeatCount(item.blablaAvailableSeats);
      const rotaAvailable = item.rotaCertaAvailableSeats == null ? null : normalizedSeatCount(item.rotaCertaAvailableSeats);
      const totalAvailable = item.totalAvailableSeats == null ? null : normalizedSeatCount(item.totalAvailableSeats);
      if (blablaAvailable != null && rotaAvailable != null && totalAvailable != null) {
        const availability = document.createElement("span");
        availability.className = "bigPill";
        availability.textContent = `BlaBlaCar ${blablaAvailable} vaga(s) • Rota Certa ${rotaAvailable} vaga(s) • Total disponível ${totalAvailable}`;
        meta.appendChild(availability);
      }
    }
    const duration = durationFor(item);
    if (duration) {
      const durationPill = document.createElement("span");
      durationPill.className = "bigPill";
      durationPill.textContent = `⏱ ${duration}`;
      meta.appendChild(durationPill);
    }
    const bottom = document.createElement("div");
    bottom.className = "agendaBottom";
    const driver = document.createElement("div");
    driver.className = "driverMini";
    driver.textContent = driverDisplayName || "Motorista Rota Certa";
    const price = document.createElement("div");
    price.className = "priceMini";
    price.textContent = fare > 0 ? formatMoney(fare) : "";
    bottom.append(driver, price);

    card.append(date, routeFrom, arrow, routeTo, meta, bottom);

    const adminContext0470 = item.adminContext0470;
    if (adminContext0470 && adminContext0470.capabilities && adminContext0470.capabilities.canManageTrip === true) {
      const state = String(adminContext0470.attestationState || "UNPROVEN").toUpperCase();
      const stateLabel = {
        VERIFIED: "🔵 Validada",
        PUBLISHED: "🟢 Publicada",
        PENDING: "🟠 Sincronizando",
        DIVERGENT: "🔴 Divergente",
        ERROR: "🔴 Erro",
      }[state] || "⚪ Não comprovada";
      const adminState = document.createElement("div");
      adminState.className = "agendaAdminState0470";
      adminState.setAttribute("role", "status");
      adminState.setAttribute("aria-label", "Estado administrativo da viagem: " + stateLabel.replace(/^[^ ]+ /, ""));
      adminState.textContent = stateLabel;
      card.appendChild(adminState);
    }

    const details = document.createElement("a");
    details.className = "agendaDetailsLink";
    details.href = `/?${detailsParams.toString()}`;
    details.textContent = "Ver detalhes";
    details.addEventListener("click", () => tracePublicAction("PUBLIC_TRIP_SELECTED"));
    card.appendChild(details);

    if (soldOut || full) {
      const fullWord = document.createElement("div");
      fullWord.className = "fullWord";
      fullWord.textContent = "LOTADO";
      bottom.appendChild(fullWord);
    }

    if (actionsEnabled) {
      const choices = document.createElement("div");
      choices.className = "reservationChoices";

      if (isTesterMode()) {
        const tester = document.createElement("button");
        tester.type = "button";
        tester.className = "bookingChoice bookingTester";
        tester.textContent = "🧪 Abrir para simular";
        tester.addEventListener("click", () => {
          const target = item.publicUrl || `/?motorista=${encodeURIComponent(item.driverUsername || driverUsername)}&trip=${encodeURIComponent(item.publicToken || item.tripId)}`;
          location.href = target;
        });
        choices.appendChild(tester);
        card.appendChild(choices);
        container.appendChild(card);
        return;
      }

      const whatsapp = document.createElement("button");
      whatsapp.type = "button";
      whatsapp.className = "bookingChoice bookingWhatsapp";
      whatsapp.textContent = "Reservar pelo WhatsApp";
      whatsapp.addEventListener("click", () => openWhatsappSeatPicker(item, fromIndex, toIndex, filtered ? "searchResults" : "agenda"));

      const blablaUrl = safeBlaBlaPublicUrl(item);
      const blabla = document.createElement("a");
      blabla.className = "bookingChoice bookingBlabla";
      blabla.textContent = "Reservar na BlaBlaCar";
      if (blablaUrl) {
        blabla.href = blablaUrl;
        blabla.target = "_blank";
        blabla.rel = "noopener noreferrer";
        blabla.addEventListener("click", () => tracePublicAction("PUBLIC_BLABLACAR_RESERVATION_OPENED", { fromIndex, toIndex }));
        choices.append(whatsapp, blabla);
      } else {
        choices.appendChild(whatsapp);
      }
      card.appendChild(choices);
    }

    if (adminContext0470 && adminContext0470.capabilities && adminContext0470.capabilities.canManageTrip === true && canonicalTripId0470) {
      const administer = document.createElement("button");
      administer.type = "button";
      administer.className = "adminCardAction0470";
      administer.textContent = "⚙ Administrar esta viagem";
      administer.setAttribute("aria-label", "Administrar esta viagem: " + from + " para " + to + ", " + formatDateOnly(item.departureAtMillis) + " às " + formatTime(item.departureAtMillis));
      administer.addEventListener("click", () => {
        const api = globalThis.RotaCertaAgendaAdmin0470;
        if (!api || typeof api.openTrip !== "function") {
          return setError("A administração desta viagem ainda não está disponível.");
        }
        api.openTrip(canonicalTripId0470, {
          source: "home",
          returnScrollY: Math.max(0, Math.floor(window.scrollY || 0)),
          returnFocusElement: administer,
        });
      });
      card.appendChild(administer);
    }
    container.appendChild(card);
  });
}

async function loadTrip() {
  if (tripToken.length < 16) return setError("Link de viagem inválido.");
  let statusCode = 0;
  try {
    const response = await fetch(
      `/v1/public/trips/${encodeURIComponent(tripToken)}`,
      { headers: agendaViewHeaders({ Accept: "application/json" }) },
    );
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok && (response.status === 401 || response.status === 403)) {
      if (isTesterMode()) {
        saveTesterSession("");
        return setError(body.message || "Sessão de teste encerrada. Gere um novo link de teste no aplicativo.");
      }
      saveAgendaViewSession("");
      if (passengerSessionContact) {
        const opened = await requestPublicAgendaAccess(passengerSessionContact);
        if (opened) return;
      }
      return showAccessGate("trip", body.message || "Informe seu WhatsApp novamente.");
    }
    if (!response.ok) throw new Error(body.message || "Viagem indisponível.");
    agendaAuthenticationRequired0428 = body.authenticationRequired !== false;
    trip = body;
    driverProfile = body.driver || {};
    updateAuthenticatedChrome();
    driverDisplayName = driverProfile.displayName || trip.driverDisplayName || driverUsername;
    tracePublicAction("PUBLIC_TRIP_LOADED", { statusCode });
    renderTrip();
  } catch (error) {
    tracePublicAction("PUBLIC_TRIP_LOAD_FAILED", { statusCode, reason: "client_load_error" });
    setError(error.message || "Não foi possível carregar a viagem.");
  }
}

function renderTrip() {
  showOnly("trip");
  const full = isFullTrip(trip);
  const range = seatRange(trip);
  const { from, to } = routeLabel(trip);
  const fare = fullFareFor(trip);

  const reliable = trip.capacityReliable === true;
  $("status").textContent = reliable ? (full ? "LOTADO" : "Disponível") : "Atualizando";
  $("status").classList.toggle("statusFull", reliable && full);
  $("tripDate").textContent = formatDateOnly(trip.departureAtMillis);
  $("tripRouteTitle").innerHTML = "";
  const fromNode = document.createElement("div");
  fromNode.textContent = from;
  const arrow = document.createElement("span");
  arrow.className = "tripRouteArrow";
  arrow.textContent = "↓";
  const toNode = document.createElement("div");
  toNode.textContent = to;
  $("tripRouteTitle").append(fromNode, arrow, toNode);

  $("tripAvailability").textContent = publicAvailabilityLabel(trip);

  $("tripPrice").textContent = fare > 0 ? `A partir de ${formatMoney(fare)} no trajeto completo` : "";

  renderTimeline();
  renderDriverProfile();
  renderTripFacts();

  if (trip.notes) {
    $("notes").textContent = trip.notes;
    show("notes", true);
  } else {
    show("notes", false);
  }

  const actionStops = orderedStops(trip);
  let actionFromIndex = requestedBoardingStopId ? actionStops.findIndex((stop) => stop.id === requestedBoardingStopId) : 0;
  let actionToIndex = requestedDropoffStopId ? actionStops.findIndex((stop) => stop.id === requestedDropoffStopId) : actionStops.length - 1;
  if (actionFromIndex < 0) actionFromIndex = 0;
  if (actionToIndex <= actionFromIndex) actionToIndex = actionStops.length - 1;
  const actionAvailable = trip.capacityReliable === true && segmentEvidenceTrusted(trip, actionFromIndex, actionToIndex)
    ? availableForTripSegment(trip, actionFromIndex, actionToIndex)
    : 0;
  const canUseExternalActions = trip.capacityReliable === true && actionAvailable > 0;

  const testerCanReserve = isTesterMode() && trip.canReserve !== false && canUseExternalActions;
  if (isTesterMode()) show("tripSticky", testerCanReserve);
  else show("tripSticky", canUseExternalActions);
  $("startBooking").disabled = !(isTesterMode() ? testerCanReserve : canUseExternalActions);
  $("startBooking").textContent = isTesterMode() ? "🧪 Simular reserva" : "Reservar pelo WhatsApp";
  show("bookBlaBla", !isTesterMode());

  const blablaUrl = safeBlaBlaPublicUrl(trip);
  const blabla = $("bookBlaBla");
  if (blablaUrl && canUseExternalActions) {
    blabla.href = blablaUrl;
    blabla.target = "_blank";
    blabla.rel = "noopener noreferrer external";
    blabla.removeAttribute("aria-disabled");
  } else {
    blabla.removeAttribute("href");
    blabla.removeAttribute("target");
    blabla.removeAttribute("rel");
    blabla.setAttribute("aria-disabled", "true");
  }
  blabla.textContent = blablaUrl && canUseExternalActions
    ? "Reservar na BlaBlaCar"
    : (blablaUrl ? "BlaBlaCar — indisponível" : "BlaBlaCar — link indisponível");

  prepareBookingSelectors();
  restoreCancellation();
  const restored = restoreExistingBooking();
  if (!restored) {
    setWhatsappLink($("driverWhatsappTrip"), defaultDriverMessage());
  }
}

function renderTimeline() {
  const container = $("routeTimeline");
  container.innerHTML = "";
  orderedStops().forEach((stop, index) => {
    const row = document.createElement("div");
    row.className = "timelineStop";

    const time = document.createElement("div");
    time.className = "time";
    time.textContent = stopMoment(stop, index) || "—";

    const dotCol = document.createElement("div");
    dotCol.className = "dotCol";
    const dot = document.createElement("span");
    dot.className = "dot";
    dotCol.appendChild(dot);

    const detail = document.createElement("div");
    const name = document.createElement("div");
    name.className = "stopName";
    name.textContent = stop.name || "Parada";
    detail.appendChild(name);

    if (stop.address && stop.address !== stop.name) {
      const address = document.createElement("div");
      address.className = "stopAddress";
      address.textContent = stop.address;
      detail.appendChild(address);
    }
    const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads : [];
    if (index < orderedStops().length - 1 && Number.isFinite(Number(loads[index]))) {
      const next = orderedStops()[index + 1];
      const available = Math.max(0, Number(trip.capacity || 0) - Number(loads[index] || 0));
      const segment = document.createElement("div");
      segment.className = "stopAddress";
      segment.textContent = `${available} vaga(s) até ${next?.name || "a próxima parada"}`;
      detail.appendChild(segment);
    }

    row.append(time, dotCol, detail);
    container.appendChild(row);
  });
}

function renderDriverReviews() {
  const container = $("driverReviews");
  if (!container) return;
  container.innerHTML = "";
  const reviews = Array.isArray(driverProfile.reviews) ? driverProfile.reviews : [];
  const total = Math.max(0, Number(driverProfile.reviewCount || 0));

  if (!reviews.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = total > 0
      ? `${total} avaliação(ões) informada(s). Os detalhes ainda não foram sincronizados.`
      : "Nenhuma avaliação detalhada sincronizada.";
    container.appendChild(empty);
    return;
  }

  const summary = document.createElement("p");
  summary.className = "muted";
  summary.textContent = total > reviews.length
    ? `Mostrando ${reviews.length} de ${total} avaliação(ões) sincronizada(s).`
    : `${reviews.length} avaliação(ões) sincronizada(s).`;
  container.appendChild(summary);

  reviews.forEach((review) => {
    const item = document.createElement("div");
    item.className = "driverReviewItem";
    const head = document.createElement("div");
    head.className = "driverReviewHead";
    const who = document.createElement("span");
    who.textContent = String(review.author || "Passageiro");
    const meta = document.createElement("span");
    const parts = [];
    if (review.rating) parts.push(`★ ${review.rating}`);
    if (review.dateLabel) parts.push(String(review.dateLabel));
    meta.textContent = parts.join(" • ");
    head.append(who, meta);
    item.appendChild(head);
    if (review.text) {
      const textNode = document.createElement("div");
      textNode.className = "driverReviewText";
      textNode.textContent = String(review.text);
      item.appendChild(textNode);
    }
    container.appendChild(item);
  });
}

function toggleDriverReviews() {
  const line = $("driverRatingLine");
  const panel = $("driverReviews");
  if (!line || !panel || line.disabled) return;
  const open = panel.classList.contains("hidden");
  show("driverReviews", open);
  line.setAttribute("aria-expanded", open ? "true" : "false");
}

function renderDriverProfile() {
  $("driverCardName").textContent = driverDisplayName || "Motorista Rota Certa";
  const photo = $("driverPhoto");
  if (driverProfile.photoUrl && String(driverProfile.photoUrl).startsWith("https://")) {
    photo.src = driverProfile.photoUrl;
    photo.alt = `Foto de ${driverDisplayName || "motorista"}`;
    show("driverPhoto", true);
  } else {
    photo.removeAttribute("src");
    show("driverPhoto", false);
  }

  const ratingParts = [];
  const detailedReviews = Array.isArray(driverProfile.reviews) ? driverProfile.reviews : [];
  if (driverProfile.rating) ratingParts.push(`★ ${driverProfile.rating}`);
  if (Number(driverProfile.reviewCount || 0) > 0) ratingParts.push(`${driverProfile.reviewCount} avaliações`);
  if (ratingParts.length) {
    const ratingLine = $("driverRatingLine");
    ratingLine.textContent = ratingParts.join(" • ");
    ratingLine.disabled = Number(driverProfile.reviewCount || 0) <= 0 && detailedReviews.length === 0;
    ratingLine.setAttribute("aria-expanded", "false");
    show("driverRatingLine", true);
    renderDriverReviews();
    show("driverReviews", false);
  } else {
    show("driverRatingLine", false);
    show("driverReviews", false);
  }

  if (driverProfile.badge) {
    $("driverBadgeLine").textContent = driverProfile.badge;
    show("driverBadgeLine", true);
  } else {
    show("driverBadgeLine", false);
  }

  if (driverProfile.about) {
    $("driverAbout").textContent = driverProfile.about;
    show("driverAbout", true);
  } else {
    show("driverAbout", false);
  }

  const tags = $("driverTags");
  tags.innerHTML = "";
  const pushTag = (text) => {
    if (!text) return;
    const tag = document.createElement("span");
    tag.className = "tag";
    tag.textContent = text;
    tags.appendChild(tag);
  };

  const vehicle = driverProfile.vehicle || {};
  if (vehicle.makeModel) pushTag(`🚗 ${vehicle.makeModel}`);
  if (vehicle.color) pushTag(`🎨 ${vehicle.color}`);
  (driverProfile.amenities || []).forEach((item) => pushTag(`✓ ${item}`));
  (driverProfile.preferences || []).forEach((item) => pushTag(item));

  setWhatsappLink($("driverWhatsappTrip"), defaultDriverMessage());
  if (isTesterMode()) show("driverWhatsappTrip", false);
}

function renderTripFacts() {
  const facts = $("tripFacts");
  facts.innerHTML = "";
  const addFact = (label, value) => {
    if (!value) return;
    const div = document.createElement("div");
    div.className = "fact";
    const strong = document.createElement("strong");
    strong.textContent = `${label}: `;
    div.append(strong, document.createTextNode(String(value)));
    facts.appendChild(div);
  };

  addFact("Saída", formatDate(trip.departureAtMillis));
  if (trip.blablaAvailableSeats != null) addFact("Vagas BlaBlaCar", `${normalizedSeatCount(trip.blablaAvailableSeats)} disponíveis`);
  if (trip.rotaCertaAvailableSeats != null) addFact("Vagas Rota Certa", `${normalizedSeatCount(trip.rotaCertaAvailableSeats)} disponíveis`);
  if (trip.totalAvailableSeats != null) addFact("Total disponível", `${normalizedSeatCount(trip.totalAvailableSeats)} vaga(s)`);
  if (trip.capacityReliable === true) {
    const available = seatRange(trip);
    addFact("Passageiros confirmados", normalizedSeatCount(trip.confirmedPassengerSeats));
    if (normalizedSeatCount(trip.blockedSeats) > 0) addFact("Vagas bloqueadas", normalizedSeatCount(trip.blockedSeats));
    addFact("Vagas disponíveis", rangeText(available, "vaga", "vagas"));
  } else {
    addFact("Ocupação", "aguardando sincronização");
  }
  const duration = durationFor(trip);
  if (duration) addFact("Duração prevista", duration);

  const fare = fullFareFor(trip);
  if (fare > 0) addFact("Valor no trajeto completo", formatMoney(fare));

  const stops = orderedStops();
  if (stops.length > 2) addFact("Paradas", `${stops.length} pontos no trajeto`);

  const vehicle = driverProfile.vehicle || {};
  if (vehicle.makeModel) addFact("Veículo", [vehicle.makeModel, vehicle.color].filter(Boolean).join(" • "));
  if (driverProfile.paymentInstructions) addFact("Pagamento", driverProfile.paymentInstructions);
}

function prepareBookingSelectors() {
  const stops = orderedStops();
  const boarding = $("boarding");
  boarding.innerHTML = "";
  stops.slice(0, -1).forEach((stop) => {
    const option = document.createElement("option");
    option.value = stop.id;
    option.textContent = stop.name;
    boarding.appendChild(option);
  });
  if (requestedBoardingStopId && stops.some((stop) => stop.id === requestedBoardingStopId)) boarding.value = requestedBoardingStopId;
  refreshSelectors();
  if (requestedDropoffStopId && [...$("dropoff").options].some((option) => option.value === requestedDropoffStopId)) $("dropoff").value = requestedDropoffStopId;
  $("seats").value = String(requestedSeats);
  refreshAvailability();
}

function pendingBookingIntentKey() {
  return `rotacerta-pending-booking-${tripToken}`;
}

function persistPendingBookingIntent(payload) {
  try { sessionStorage.setItem(pendingBookingIntentKey(), JSON.stringify(payload)); } catch (_) {}
}

function restorePendingBookingIntent() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(pendingBookingIntentKey()) || "null");
    return saved && saved.boardingStopId && saved.dropoffStopId ? saved : null;
  } catch (_) {
    return null;
  }
}

function clearPendingBookingIntent() {
  try { sessionStorage.removeItem(pendingBookingIntentKey()); } catch (_) {}
}

function showQuickBookingNotice(title, message, isError = false) {
  $("quickBookingTitle").textContent = title;
  $("quickBookingText").textContent = message;
  $("quickBookingText").className = isError ? "error" : "muted";
  show("quickBookingNotice", true);
  if (isError) {
    show("quickUndo", false);
    show("quickObservation", false);
  }
}

function hideQuickBookingNotice() {
  show("quickBookingNotice", false);
  if (quickUndoTimer) clearTimeout(quickUndoTimer);
  quickUndoTimer = null;
}

function defaultBookingIntent() {
  if (!trip || trip.capacityReliable !== true) return null;
  const stops = orderedStops();
  if (stops.length < 2) return null;
  let fromIndex = requestedBoardingStopId ? stops.findIndex((stop) => stop.id === requestedBoardingStopId) : 0;
  let toIndex = requestedDropoffStopId ? stops.findIndex((stop) => stop.id === requestedDropoffStopId) : stops.length - 1;
  if (fromIndex < 0) fromIndex = 0;
  if (toIndex <= fromIndex) toIndex = stops.length - 1;
  const seats = Math.max(1, requestedSeats || 1);
  if (!segmentEvidenceTrusted(trip, fromIndex, toIndex)) return null;
  const available = availableFor(fromIndex, toIndex);
  if (available < seats) return null;
  return {
    boardingStopId: stops[fromIndex].id,
    dropoffStopId: stops[toIndex].id,
    seats,
    creditToUseCents: 0,
    quick: true,
  };
}

function startQuickReservation(auto = false) {
  if (!trip || trip.capacityReliable !== true || isFullTrip(trip) || trip.canReserve === false || bookingRequestInFlight) return;
  return openTripSeatPicker(auto);
}

function refreshTripAvailabilitySummary() {
  if (!trip) return;
  const full = isFullTrip(trip);
  const range = seatRange(trip);
  const reliable = trip.capacityReliable === true;
  $("status").textContent = reliable ? (full ? "LOTADO" : "Disponível") : "Atualizando";
  $("status").classList.toggle("statusFull", reliable && full);
  $("tripAvailability").textContent = publicAvailabilityLabel(trip);
  if (confirmedBooking) {
    show("tripSticky", false);
  } else {
    const canReserveNow = reliable && !full && trip.canReserve !== false;
    show("tripSticky", canReserveNow);
    $("startBooking").disabled = !canReserveNow;
    $("adjustBooking").disabled = !canReserveNow;
  }
}

function openBookingFlow() {
  if (!trip || trip.capacityReliable !== true || isFullTrip(trip) || trip.canReserve === false) return;
  editingExistingBooking = false;
  $("confirmReserve").textContent = "Fazer pedido de reserva";
  if (!isTesterMode() && passengerSessionContact) {
    $("contact").value = maskWhatsapp(passengerSessionContact);
    $("contact").readOnly = true;
  } else if (isTesterMode()) {
    $("contact").value = "";
    $("contact").readOnly = false;
  }
  $("creditToUse").value = "0";
  loadPassengerCredits();
  showOnly("booking");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function refreshSelectors() {
  const stops = orderedStops();
  const boarding = $("boarding");
  const dropoff = $("dropoff");
  const fromIndex = stops.findIndex((stop) => stop.id === boarding.value);
  dropoff.innerHTML = "";

  stops.forEach((stop, index) => {
    if (index <= fromIndex || availableFor(fromIndex, index) < 1) return;
    const option = document.createElement("option");
    option.value = stop.id;
    option.textContent = stop.name;
    dropoff.appendChild(option);
  });
  refreshAvailability();
}

function refreshAvailability() {
  if (!trip) return;
  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  const available = availableFor(fromIndex, toIndex);
  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const seatsInput = $("seats");
  seatsInput.max = String(Math.max(1, available));

  let requested = Number(seatsInput.value || 1);
  if (!Number.isInteger(requested) || requested < 1) {
    requested = 1;
    seatsInput.value = "1";
  }

  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa` : "";
  $("availability").textContent = seatAvailabilityText(available) + fareText;
  $("bookingMessage").textContent = requested > available
    ? seatLimitText(available, false)
    : "";
  $("reserve").disabled = available < 1 || requested > available || !$("dropoff").value;
}

function traceSearchChanged() {
  if (!trip) return;
  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  tracePublicAction("PUBLIC_SEARCH_CHANGED", {
    seats: Number($("seats").value || 0),
    fromIndex,
    toIndex,
  });
}

function reviewBooking() {
  if (!trip) return;
  const seats = Number($("seats").value || 0);
  if (!$("boarding").value || !$("dropoff").value || seats < 1) {
    return void ($("bookingMessage").textContent = "Escolha um trecho com vagas.");
  }

  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  const available = availableFor(fromIndex, toIndex);
  if (available < seats) {
    return void ($("bookingMessage").textContent = seatLimitText(available, true));
  }

  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const totalFareCents = farePerSeatCents * seats;
  const requestedCreditCents = Math.max(0, Math.round(Number($("creditToUse").value || 0) * 100));
  const creditToUseCents = editingExistingBooking ? 0 : Math.min(requestedCreditCents, passengerCreditBalanceCents, totalFareCents);

  pendingBooking = {
    boardingStopId: $("boarding").value,
    dropoffStopId: $("dropoff").value,
    seats,
    creditToUseCents,
    quick: !editingExistingBooking,
  };
  persistPendingBookingIntent(pendingBooking);
  tracePublicAction("PUBLIC_RESERVATION_STARTED", { seats, fromIndex, toIndex });
  $("bookingMessage").textContent = "";

  if (editingExistingBooking && confirmedBooking?.bookingId && confirmedBooking?.cancellationToken) {
    return updateExistingReservation();
  }
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("booking", "reserve");
  return reserve();
}

function requestIdentity(payload) {
  const fingerprint = JSON.stringify({
    boardingStopId: payload.boardingStopId,
    dropoffStopId: payload.dropoffStopId,
    seats: payload.seats,
    creditToUseCents: payload.creditToUseCents || 0,
  });
  const key = isTesterMode()
    ? `${bookingStoragePrefix()}booking-intent-${tripToken}`
    : `rotacerta-booking-intent-${tripToken}`;
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(key) || "null"); } catch (_) {}
  if (saved?.fingerprint === fingerprint && saved?.idempotencyKey) return saved.idempotencyKey;

  const idempotencyKey = (crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`)
    .replace(/[^A-Za-z0-9_-]/g, "_");
  try { localStorage.setItem(key, JSON.stringify({ fingerprint, idempotencyKey })); } catch (_) {}
  return idempotencyKey;
}

async function reserve() {
  if (!trip) return;
  if (!pendingBooking) pendingBooking = restorePendingBookingIntent();
  if (!pendingBooking || bookingRequestInFlight) return;
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("trip", "reserve");
  if (editingExistingBooking && confirmedBooking?.bookingId && confirmedBooking?.cancellationToken) {
    return updateExistingReservation();
  }

  bookingRequestInFlight = true;
  const intent = { ...pendingBooking };
  const bookingPayload = {
    boardingStopId: intent.boardingStopId,
    dropoffStopId: intent.dropoffStopId,
    seats: intent.seats,
    creditToUseCents: intent.creditToUseCents || 0,
  };
  const idempotencyKey = requestIdentity(bookingPayload);
  const debugStops = orderedStops();
  const fromIndex = debugStops.findIndex((s) => s.id === bookingPayload.boardingStopId);
  const toIndex = debugStops.findIndex((s) => s.id === bookingPayload.dropoffStopId);
  let statusCode = 0;

  $("startBooking").disabled = true;
  $("reserve").disabled = true;
  showQuickBookingNotice("Solicitando reserva…", "Validando a vaga e registrando sua solicitação.");
  tracePublicAction("PUBLIC_RESERVATION_REQUEST_SENT", {
    seats: bookingPayload.seats,
    fromIndex,
    toIndex,
  });

  try {
    const bookingEndpoint = isTesterMode()
      ? `/v1/tester/trips/${encodeURIComponent(tripToken)}/bookings`
      : `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings`;
    const response = await fetch(bookingEndpoint, {
      method: "POST",
      headers: authenticatedHeaders({
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": idempotencyKey,
      }),
      body: JSON.stringify({ ...bookingPayload, idempotencyKey }),
    });
    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        return setError(body.message || "Sessão de teste encerrada.");
      }
      savePassengerSession("");
      passengerViewAccountActivated = true;
      pendingBooking = intent;
      persistPendingBookingIntent(intent);
      showPrivateAuthGate("trip", "reserve");
      return;
    }
    if (!response.ok) {
      const failure = new Error(body.message || "Não foi possível reservar.");
      failure.code = body.error || "";
      failure.availableSeats = Number.isInteger(Number(body.availableSeats)) ? Number(body.availableSeats) : null;
      throw failure;
    }

    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      passengerName: body.passengerName || (isTesterMode() ? "🧪 Passageiro de teste" : ""),
      passengerContact: isTesterMode() ? "" : passengerSessionContact,
      boardingStopId: bookingPayload.boardingStopId,
      dropoffStopId: bookingPayload.dropoffStopId,
      seats: bookingPayload.seats,
      farePerSeatCents: Number(body.farePerSeatCents || 0),
      totalFareCents: Number(body.totalFareCents || 0),
      creditAppliedCents: Number(body.creditAppliedCents || 0),
      amountDueCents: Number(body.amountDueCents || 0),
      status: body.status || "REQUESTED",
      operationalStatus: body.operationalStatus || "PENDING",
    };

    try {
      const bookingKey = isTesterMode()
        ? `${bookingStoragePrefix()}booking-${body.bookingId}`
        : `rotacerta-booking-${body.bookingId}`;
      localStorage.setItem(
        bookingKey,
        JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }),
      );
      localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking));
    } catch (_) {}

    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    $("cancelCode").textContent = body.cancellationToken;

    trip.segmentLoads = recomputeLoadsAfterBooking(trip.segmentLoads || [], confirmedBooking);

    tracePublicAction("PUBLIC_RESERVATION_CREATED", {
      statusCode,
      seats: confirmedBooking.seats,
      fromIndex,
      toIndex,
      replayed: body.replayed === true,
    });
    tracePublicAction("PUBLIC_SEATS_UPDATED", {
      statusCode,
      seats: confirmedBooking.seats,
      fromIndex,
      toIndex,
    });

    const total = Number(body.totalFareCents || 0);
    const credits = Number(body.creditAppliedCents || 0);
    const due = Number(body.amountDueCents || 0);
    const fareText = total > 0
      ? (credits > 0
        ? ` Valor: ${formatMoney(total)} • créditos: −${formatMoney(credits)} • a pagar: ${formatMoney(due)}.`
        : ` Valor: ${formatMoney(total)}.`)
      : "";
    $("confirmationText").textContent = body.replayed
      ? `Esta reserva já estava registrada. Nenhuma duplicata foi criada.${fareText}`
      : `Reserva solicitada para ${confirmedBooking.seats} lugar(es).${fareText}`;

    passengerCreditBalanceCents = Math.max(0, passengerCreditBalanceCents - credits);
    if (isTesterMode()) {
      await loadPassengerNotifications({ silent: true });
      await loadPassengerCredits();
    }
    clearPendingBookingIntent();
    pendingBooking = null;
    editingExistingBooking = false;

    showOnly("trip");
    refreshTripAvailabilitySummary();
    showQuickBookingNotice(
      isTesterMode() ? "🧪 RESERVA SIMULADA" : "✓ RESERVA SOLICITADA",
      body.replayed
        ? "Essa solicitação já estava registrada. Nenhuma reserva duplicada foi criada."
        : (isTesterMode()
          ? "A reserva e a disponibilidade foram alteradas somente nesta simulação. Nenhum motorista foi avisado."
          : "Sua solicitação foi registrada, a vaga ficou protegida e o motorista foi avisado."),
    );

    const observation = `Olá, ${driverDisplayName || "motorista"}. Tenho uma observação sobre minha reserva ${confirmedBooking.bookingId}: `;
    const hasObservation = isTesterMode() ? false : setWhatsappLink($("quickObservation"), observation);
    show("quickObservation", hasObservation);
    show("quickUndo", !body.replayed);
    if (quickUndoTimer) clearTimeout(quickUndoTimer);
    quickUndoTimer = setTimeout(() => show("quickUndo", false), 8000);
  } catch (error) {
    tracePublicAction("PUBLIC_RESERVATION_FAILED", {
      statusCode,
      reason: statusCode ? `http_${statusCode}` : "network_or_client_error",
      seats: intent.seats || 0,
      fromIndex,
      toIndex,
    });
    pendingBooking = intent;
    persistPendingBookingIntent(intent);
    if (statusCode === 409) {
      await loadTrip();
      if (error.code === "insufficient_seats") {
        openTripSeatPicker(false, intent, true);
      }
    } else {
      showOnly("trip");
    }
    const currentMessage = error.code === "insufficient_seats" && Number.isInteger(error.availableSeats)
      ? seatLimitText(error.availableSeats, true)
      : (error.message || "Falha ao registrar a reserva.");
    showQuickBookingNotice("Reserva não concluída", currentMessage, true);
  } finally {
    bookingRequestInFlight = false;
    if (!confirmedBooking) {
      $("startBooking").disabled = isFullTrip(trip) || trip?.canReserve === false;
      $("reserve").disabled = false;
    }
  }
}

async function undoQuickBooking() {
  const booking = confirmedBooking;
  if (!booking?.bookingId || !booking?.cancellationToken || !tripToken) return;
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("trip", "undo");
  $("quickUndo").disabled = true;
  showQuickBookingNotice("Desfazendo…", "Cancelando a solicitação e devolvendo a vaga.");
  tracePublicAction("PUBLIC_RESERVATION_CANCEL_STARTED");
  let statusCode = 0;
  try {
    const response = await fetch(
      isTesterMode()
        ? `/v1/tester/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(booking.bookingId)}/cancel`
        : `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(booking.bookingId)}/cancel`,
      {
        method: "POST",
        headers: authenticatedHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
        body: JSON.stringify({ cancellationToken: booking.cancellationToken }),
      },
    );
    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        return setError(body.message || "Sessão de teste encerrada. Gere um novo link de teste no aplicativo.");
      }
      savePassengerSession("");
      passengerViewAccountActivated = true;
      return showPrivateAuthGate("trip", "undo");
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível desfazer.");

    try {
      localStorage.removeItem(cancellationStorageKey());
      if (isTesterMode()) {
        localStorage.removeItem(`${bookingStoragePrefix()}booking-${booking.bookingId}`);
        localStorage.removeItem(`${bookingStoragePrefix()}booking-intent-${tripToken}`);
      } else {
        localStorage.removeItem(`rotacerta-booking-${booking.bookingId}`);
        localStorage.removeItem(`rotacerta-booking-intent-${tripToken}`);
      }
    } catch (_) {}
    clearPendingBookingIntent();
    confirmedBooking = null;
    directReserveConsumed = true;
    tracePublicAction("PUBLIC_RESERVATION_CANCELLED", { statusCode });
    tracePublicAction("PUBLIC_SEATS_UPDATED", { statusCode });
    hideQuickBookingNotice();
    await loadTrip();
  } catch (error) {
    showQuickBookingNotice("Não foi possível desfazer", error.message || "Use Minha área para cancelar a reserva.", true);
  } finally {
    $("quickUndo").disabled = false;
  }
}

function cancellationStorageKey() {
  return `${bookingStoragePrefix()}booking-trip-${tripToken}`;
}

function restoreCancellation() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return;
  $("cancelBookingId").value = saved.bookingId;
  $("cancelToken").value = saved.cancellationToken;
}

function restoreExistingBooking() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return false;

  confirmedBooking = saved;
  $("confirmationText").textContent = saved.status === "REQUESTED"
    ? "Sua solicitação está aguardando aprovação do motorista."
    : "Sua reserva está registrada neste aparelho.";
  $("cancelCode").textContent = saved.cancellationToken;
  $("cancelBookingId").value = saved.bookingId;
  $("cancelToken").value = saved.cancellationToken;

  setWhatsappLink(
    $("driverWhatsappConfirmed"),
    `Olá, ${driverDisplayName || "motorista"}. Estou falando sobre minha reserva ${saved.bookingId} no Rota Certa.`,
  );

  showOnly("confirmed");
  return true;
}

function beginExistingReservationEdit() {
  if (!trip || !confirmedBooking?.bookingId || !confirmedBooking?.cancellationToken) return;
  editingExistingBooking = true;
  $("boarding").value = confirmedBooking.boardingStopId || $("boarding").value;
  refreshSelectors();
  if (confirmedBooking.dropoffStopId) $("dropoff").value = confirmedBooking.dropoffStopId;
  $("seats").value = String(Math.max(1, Number(confirmedBooking.seats || 1)));
  refreshAvailability();
  $("bookingMessage").textContent = "Altere o que precisar e continue.";
  $("confirmReserve").textContent = "Confirmar alteração";
  showOnly("booking");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function updateExistingReservation() {
  if (!trip || !pendingBooking || !confirmedBooking?.bookingId || !confirmedBooking?.cancellationToken) return;
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("review", "update");

  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Atualizando sua reserva…";
  let statusCode = 0;

  try {
    const response = await fetch(
      isTesterMode()
        ? `/v1/tester/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(confirmedBooking.bookingId)}`
        : `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(confirmedBooking.bookingId)}`,
      {
        method: "PUT",
        headers: authenticatedHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
        body: JSON.stringify({
          ...pendingBooking,
          cancellationToken: confirmedBooking.cancellationToken,
        }),
      },
    );

    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        setError(body.message || "Sessão de teste encerrada. Gere um novo link de teste no aplicativo.");
        return;
      }
      savePassengerSession("");
      passengerViewAccountActivated = true;
      showPrivateAuthGate("review", "update");
      return;
    }
    if (!response.ok) {
      const failure = new Error(body.message || "Não foi possível alterar a reserva.");
      failure.code = body.error || "";
      failure.availableSeats = Number.isInteger(Number(body.availableSeats)) ? Number(body.availableSeats) : null;
      throw failure;
    }

    confirmedBooking = {
      ...confirmedBooking,
      passengerName: pendingBooking.passengerName || confirmedBooking.passengerName,
      passengerContact: pendingBooking.passengerContact || confirmedBooking.passengerContact,
      boardingStopId: pendingBooking.boardingStopId,
      dropoffStopId: pendingBooking.dropoffStopId,
      seats: pendingBooking.seats,
      farePerSeatCents: Number(body.farePerSeatCents || 0),
      totalFareCents: Number(body.totalFareCents || 0),
    };
    try { localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking)); } catch (_) {}

    tracePublicAction("PUBLIC_RESERVATION_CHANGED", { statusCode, seats: confirmedBooking.seats });
    tracePublicAction("PUBLIC_SEATS_UPDATED", { statusCode, seats: confirmedBooking.seats });

    editingExistingBooking = false;
    pendingBooking = null;
    $("reviewMessage").textContent = "";
    $("confirmationText").textContent = "Reserva alterada com sucesso.";
    $("cancelCode").textContent = confirmedBooking.cancellationToken;
    setWhatsappLink(
      $("driverWhatsappConfirmed"),
      `Olá, ${driverDisplayName || "motorista"}. Acabei de alterar minha reserva ${confirmedBooking.bookingId} no Rota Certa.`,
    );
    showOnly("confirmed");
    window.scrollTo({ top: 0, behavior: "smooth" });
    await refreshTripSilently();
  } catch (error) {
    if (statusCode === 409 && error.code === "insufficient_seats") {
      await refreshTripSilently();
      openTripSeatPicker(false, pendingBooking, true);
      $("seatPickerMessage").textContent = Number.isInteger(error.availableSeats)
        ? seatLimitText(error.availableSeats, true)
        : (error.message || "A disponibilidade mudou.");
    } else {
      $("reviewMessage").textContent = error.message || "Falha ao alterar reserva.";
    }
  } finally {
    $("confirmReserve").disabled = false;
  }
}

async function refreshTripSilently() {
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}`, {
      headers: agendaViewHeaders({ Accept: "application/json" }),
    });
    if (!response.ok) return;
    const body = await response.json();
    trip = body;
    driverProfile = body.driver || driverProfile;
  } catch (_) {}
}

async function cancelReservation() {
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("review", "cancel");
  const bookingId = $("cancelBookingId").value.trim();
  const cancellationToken = $("cancelToken").value.trim();

  if (!tripToken || !bookingId || !cancellationToken) {
    $("cancelMessage").textContent = "Não encontrei os dados necessários para cancelar.";
    return;
  }

  $("cancelReservation").disabled = true;
  $("cancelMessage").textContent = "Cancelando e liberando as vagas…";
  let statusCode = 0;
  tracePublicAction("PUBLIC_RESERVATION_CANCEL_STARTED");

  try {
    const response = await fetch(
      isTesterMode()
        ? `/v1/tester/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`
        : `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`,
      {
        method: "POST",
        headers: authenticatedHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
        body: JSON.stringify({ cancellationToken }),
      },
    );
    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        setError(body.message || "Sessão de teste encerrada. Gere um novo link de teste no aplicativo.");
        return;
      }
      savePassengerSession("");
      passengerViewAccountActivated = true;
      showPrivateAuthGate("review", "cancel");
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível cancelar.");

    try {
      localStorage.removeItem(cancellationStorageKey());
      if (isTesterMode()) {
        localStorage.removeItem(`${bookingStoragePrefix()}booking-${bookingId}`);
        localStorage.removeItem(`${bookingStoragePrefix()}booking-intent-${tripToken}`);
      } else {
        localStorage.removeItem(`rotacerta-booking-${bookingId}`);
        localStorage.removeItem(`rotacerta-booking-intent-${tripToken}`);
      }
    } catch (_) {}

    confirmedBooking = null;
    $("cancelBookingId").value = "";
    $("cancelToken").value = "";
    $("cancelMessage").textContent = "Reserva cancelada. As vagas foram liberadas.";

    tracePublicAction("PUBLIC_RESERVATION_CANCELLED", { statusCode });
    tracePublicAction("PUBLIC_SEATS_UPDATED", { statusCode });

    await loadTrip();
  } catch (error) {
    tracePublicAction("PUBLIC_RESERVATION_CANCEL_FAILED", {
      statusCode,
      reason: statusCode ? `http_${statusCode}` : "network_or_client_error",
    });
    $("cancelMessage").textContent = error.message || "Falha ao cancelar a reserva.";
  } finally {
    $("cancelReservation").disabled = false;
  }
}

function recomputeLoadsAfterBooking(loads, booking) {
  const next = [...loads];
  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === booking.boardingStopId);
  const toIndex = stops.findIndex((s) => s.id === booking.dropoffStopId);
  for (let i = fromIndex; i < toIndex; i += 1) {
    next[i] = Number(next[i] || 0) + Number(booking.seats || 0);
  }
  return next;
}

function bookingStops() {
  const stops = orderedStops();
  return {
    from: stops.find((s) => s.id === confirmedBooking?.boardingStopId),
    to: stops.find((s) => s.id === confirmedBooking?.dropoffStopId),
  };
}

function calendarTimes() {
  const { from, to } = bookingStops();
  const begin = new Date(from?.plannedDepartureMillis || from?.plannedArrivalMillis || trip.departureAtMillis);
  const end = new Date(to?.plannedArrivalMillis || (begin.getTime() + 3600000));
  const format = (date) => date.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
  return `${format(begin)}/${format(end)}`;
}

function openGoogleCalendar() {
  if (!trip || !confirmedBooking) return;
  if (isTesterMode()) {
    showQuickBookingNotice("🧪 Ação simulada", "Nenhum serviço externo foi aberto no Modo Teste.");
    return;
  }
  const { from, to } = bookingStops();
  const query = new URLSearchParams({
    action: "TEMPLATE",
    text: `Carona — ${from?.name || "Embarque"} → ${to?.name || "Destino"}`,
    dates: calendarTimes(),
    details: `Rota Certa\nReserva: ${confirmedBooking.bookingId}\n${location.href}`,
    location: from?.address || from?.name || "",
  });
  location.href = `https://calendar.google.com/calendar/render?${query.toString()}`;
}

function escapeIcs(value) {
  return String(value || "")
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

function downloadIcs() {
  if (!trip || !confirmedBooking) return;
  const { from, to } = bookingStops();
  const [begin, end] = calendarTimes().split("/");
  const ics = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Rota Certa//Agenda de Viagens//PT-BR",
    "BEGIN:VEVENT",
    `UID:${escapeIcs(confirmedBooking.bookingId)}@rotacerta`,
    `DTSTART:${begin}`,
    `DTEND:${end}`,
    `SUMMARY:${escapeIcs(`Carona — ${from?.name || "Embarque"} → ${to?.name || "Destino"}`)}`,
    `LOCATION:${escapeIcs(from?.address || from?.name || "")}`,
    `DESCRIPTION:${escapeIcs(`Rota Certa | Reserva ${confirmedBooking.bookingId}`)}`,
    "END:VEVENT",
    "END:VCALENDAR",
    "",
  ].join("\r\n");

  const blob = new Blob([ics], { type: "text/calendar;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `rota-certa-${confirmedBooking.bookingId}.ics`;
  document.body.appendChild(link);
  link.click();
  const objectUrl = link.href;
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

async function shareCalendarFeed() {
  if (driverUsername.length < 3) return;
  if (isTesterMode()) {
    if ($("subscribeCalendar")) $("subscribeCalendar").textContent = "🧪 Compartilhamento externo simulado";
    return;
  }
  const shortUrl = publicSlug ? `${location.origin}/${encodeURIComponent(publicSlug)}` : "";
  const url = shortUrl || (agendaToken.length >= 16
    ? `${location.origin}/calendar/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}.ics`
    : "");
  if (!url) return;
  const payload = {
    title: "Rota Certa — Agenda de Viagens",
    text: publicSlug ? "Agenda de Viagens." : "Calendário público das viagens.",
    url,
  };
  try {
    if (navigator.share) {
      await navigator.share(payload);
      return;
    }
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      $("subscribeCalendar").textContent = publicSlug ? "Link da Agenda copiado" : "Link do calendário copiado";
      return;
    }
  } catch (_) {}
  location.href = url;
}


function portalHeaders() {
  return authenticatedHeaders({
    Accept: "application/json",
    "Content-Type": "application/json",
  });
}

function setPassengerNotificationBadge(count) {
  passengerUnreadNotificationCount = Math.max(0, Number(count || 0));
  const badge = $("passengerNotificationBadge");
  badge.textContent = passengerUnreadNotificationCount > 99 ? "99+" : String(passengerUnreadNotificationCount);
  show("passengerNotificationBadge", hasPrivatePortalSession() && passengerUnreadNotificationCount > 0);
  show("portalMarkAllNotificationsRead", passengerUnreadNotificationCount > 0);
}

function passengerNotificationTarget(item) {
  if (!item || !item.tripId) return "";
  const query = new URLSearchParams();
  const owner = String(item.driverUsername || driverUsername || "");
  if (owner) query.set("motorista", owner);
  query.set("trip", String(item.tripId));
  return location.origin + "/?" + query.toString();
}

function renderPassengerNotifications(entries, unreadCount) {
  const container = $("portalNotifications");
  container.innerHTML = "";
  const notifications = Array.isArray(entries) ? entries : [];
  passengerUnreadBookingIds = new Set(
    notifications
      .filter((item) => item && !item.read && item.bookingId)
      .map((item) => String(item.bookingId)),
  );
  setPassengerNotificationBadge(unreadCount);
  if (!notifications.length) {
    container.innerHTML = '<p class="muted">Nenhuma notificação.</p>';
    return;
  }
  notifications.slice(0, 50).forEach((item) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "notificationItem" + (item.read ? "" : " notificationItemUnread");
    const title = document.createElement("div");
    title.className = "notificationTitle";
    title.textContent = (item.read ? "" : "● ") + (item.title || "Notificação");
    const message = document.createElement("div");
    message.className = "notificationMessage";
    message.textContent = item.message || "";
    button.append(title, message);
    button.addEventListener("click", async () => {
      if (!hasPrivatePortalSession()) return showPrivateAuthGate("portal");
      try {
        if (!item.read) {
          await fetch(
            (isTesterMode() ? "/v1/tester/me/notifications/" : "/v1/passenger/me/notifications/") + encodeURIComponent(item.id) + "/read",
            {
            method: "POST",
            headers: portalHeaders(),
            body: "{}",
            },
          );
        }
      } catch (_) {}
      const target = passengerNotificationTarget(item);
      if (target) {
        location.href = target;
        return;
      }
      await loadPassengerNotifications({ silent: true });
    });
    container.appendChild(button);
  });
}

async function loadPassengerNotifications(options = {}) {
  if (!hasPrivatePortalSession()) {
    setPassengerNotificationBadge(0);
    return;
  }
  const silent = options.silent === true;
  if (!silent && $("portalNotifications")) {
    $("portalNotifications").innerHTML = '<p class="muted">Carregando notificações…</p>';
  }
  try {
    const endpoint = isTesterMode() ? "/v1/tester/me/notifications" : "/v1/passenger/me/notifications";
    const response = await fetch(endpoint, { headers: portalHeaders() });
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        setError(body.message || "Sessão de teste encerrada.");
      } else {
        savePassengerSession("");
      }
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível carregar as notificações.");
    renderPassengerNotifications(body.notifications, body.unreadCount);
  } catch (error) {
    if (!silent && $("portalNotifications")) {
      $("portalNotifications").innerHTML = '<p class="muted">' + (error.message || "Falha ao carregar notificações.") + "</p>";
    }
  }
}

async function markAllPassengerNotificationsRead() {
  if (!hasPrivatePortalSession()) return;
  $("portalMarkAllNotificationsRead").disabled = true;
  try {
    const endpoint = isTesterMode() ? "/v1/tester/me/notifications/read-all" : "/v1/passenger/me/notifications/read-all";
    const response = await fetch(endpoint, {
      method: "POST",
      headers: portalHeaders(),
      body: "{}",
    });
    if (!response.ok) {
      const body = await response.json();
      throw new Error(body.message || "Não foi possível marcar as notificações.");
    }
    await loadPassengerNotifications({ silent: true });
  } finally {
    $("portalMarkAllNotificationsRead").disabled = false;
  }
}

function openPassengerNotificationCenter() {
  openPassengerPortal();
  setTimeout(() => {
    const card = $("portalNotificationsCard");
    if (card) card.scrollIntoView({ behavior: "smooth", block: "start" });
  }, 0);
}

function savePassengerSession(token) {
  passengerSessionToken = String(token || "");
  try {
    localStorage.removeItem("rotacerta-passenger-session");
    if (passengerSessionToken) sessionStorage.setItem("rotacerta-passenger-session", passengerSessionToken);
    else sessionStorage.removeItem("rotacerta-passenger-session");
  } catch (_) {}
  updateAuthenticatedChrome();
}

function openPassengerPortal() {
  if (!hasPrivatePortalSession() && agendaAuthenticationRequired0428) return showPrivateAuthGate("portal");
  if (!isTesterMode() && passengerSessionToken && passengerAgendaAdmin0418 && (agendaToken || publicSlug)) {
    agendaReturnScrollY0470 = Math.max(0, Math.floor(window.scrollY || 0));
    return loadAgenda({ restoreScrollY: agendaReturnScrollY0470 });
  }
  tracePublicAction("PUBLIC_PASSENGER_PORTAL_OPENED", {
    reason: isTesterMode() ? "tester" : (hasPrivatePortalSession() ? "identified" : "authentication_disabled"),
  });
  showOnly("passengerPortal");
  updateAuthenticatedChrome();

  if (!hasPrivatePortalSession()) {
    $("portalMessage").textContent = "Autenticação desligada. Informe seu WhatsApp apenas para carregar suas viagens particulares; nenhuma senha será solicitada.";
    show("portalLoginBox", true);
    show("portalAuthenticated", false);
    show("portalPasswordWrap0428", false);
    $("portalLogin").textContent = "Continuar sem senha";
    if (passengerSessionContact) $("portalContact").value = maskWhatsapp(passengerSessionContact);
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }

  $("portalMessage").textContent = "";
  show("portalLoginBox", false);
  show("portalAuthenticated", true);
  $("portalLogin").textContent = agendaAuthenticationRequired0428 ? "Entrar" : "Continuar sem senha";
  if (isTesterMode()) {
    $("portalMessage").textContent = "🧪 Esta área mostra somente reservas simuladas desta sessão.";
    show("portalNotificationsCard", true);
    show("portalReferralShare", false);
    show("portalChangePassword", false);
    show("portalNewPassword", false);
  } else if (agendaAuthenticationRequired0428 && passengerMustChangePassword) {
    $("portalPasswordMessage").textContent = "Você entrou com uma senha temporária. Crie uma nova senha.";
  }
  if (!isTesterMode()) validatePassengerSession().catch(() => false);
  loadPassengerCredits();
  loadPassengerBookings();
  loadPassengerNotifications();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function loginPassengerPortal() {
  if (isTesterMode()) return openPassengerPortal();
  const passengerContact = normalizeWhatsapp($("portalContact").value);
  const password = $("portalPassword").value;
  if (!passengerContact || (agendaAuthenticationRequired0428 && password.length < 8)) {
    $("portalMessage").textContent = agendaAuthenticationRequired0428
      ? "Informe seu WhatsApp com DDD e sua senha."
      : "Informe seu WhatsApp com DDD para localizar suas viagens.";
    return;
  }
  $("portalLogin").disabled = true;
  $("portalMessage").textContent = agendaAuthenticationRequired0428 ? "Entrando…" : "Localizando suas viagens…";
  try {
    const payload = { passengerContact, driverUsername, sessionContextId: passengerSessionContextId0427 };
    if (agendaAuthenticationRequired0428) payload.password = password;
    const response = await fetch("/v1/passenger/session", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(payload),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível continuar.");
    savePassengerSession(body.sessionToken);
    savePassengerContact(body.passengerContact || passengerContact);
    passengerMustChangePassword = body.mustChangePassword === true;
    passengerAgendaAdmin0418 = body.agendaAdmin === true;
    agendaAuthenticationRequired0428 = body.authenticationRequired !== false;
    updateAuthenticatedChrome();
    $("portalPassword").value = "";
    $("portalMessage").textContent = "";
    openPassengerPortal();
  } catch (error) {
    $("portalMessage").textContent = error.message || "Falha ao continuar.";
  } finally {
    $("portalLogin").disabled = false;
  }
}

async function registerPassengerPortal() {
  const password = $("portalCreatePassword").value;
  if (!confirmedBooking?.bookingId || !confirmedBooking?.cancellationToken || !confirmedBooking?.passengerContact || !tripToken) {
    $("portalCreateMessage").textContent = "Abra a reserva confirmada neste aparelho para criar o acesso.";
    return;
  }
  if (password.length < 8 || password.length > 72) {
    $("portalCreateMessage").textContent = "Use uma senha de 8 a 72 caracteres.";
    return;
  }
  $("portalRegister").disabled = true;
  $("portalCreateMessage").textContent = "Criando acesso…";
  try {
    const response = await fetch("/v1/passenger/register", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        passengerContact: confirmedBooking.passengerContact,
        password,
        tripToken,
        bookingId: confirmedBooking.bookingId,
        cancellationToken: confirmedBooking.cancellationToken,
      }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível criar o acesso.");
    savePassengerSession(body.sessionToken);
    $("portalCreatePassword").value = "";
    $("portalCreateMessage").textContent = "Acesso criado. Agora esta reserva pode ser consultada em qualquer aparelho.";
  } catch (error) {
    $("portalCreateMessage").textContent = error.message || "Falha ao criar o acesso.";
  } finally {
    $("portalRegister").disabled = false;
  }
}

function portalStopName(tripItem, stopId) {
  return orderedStops(tripItem).find((stop) => stop.id === stopId)?.name || "Parada";
}


async function loadPassengerCredits() {
  if (!hasPrivatePortalSession() || (!isTesterMode() && !driverUsername)) return;
  try {
    const endpoint = isTesterMode()
      ? "/v1/tester/me/credits"
      : `/v1/passenger/me/credits?driverUsername=${encodeURIComponent(driverUsername)}`;
    const response = await fetch(endpoint, { headers: portalHeaders() });
    const body = await response.json();
    if (response.status === 401 && isTesterMode()) {
      saveTesterSession("");
      return setError(body.message || "Sessão de teste encerrada.");
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível carregar seus créditos.");
    passengerCreditBalanceCents = Math.max(0, Number(body.balanceCents || 0));
    $("portalCreditBalance").textContent = formatMoney(passengerCreditBalanceCents);
    $("portalReferralInfo").textContent = isTesterMode()
      ? "🧪 Saldo e movimentações abaixo existem somente nesta simulação."
      : (Number(body.referralCreditCents || 0) > 0
        ? `Cada indicação elegível concluída rende ${formatMoney(body.referralCreditCents)} em créditos.`
        : "O motorista ainda não definiu créditos por indicação.");
    $("bookingCreditBalance").textContent = formatMoney(passengerCreditBalanceCents);
    show("bookingCreditBox", passengerCreditBalanceCents > 0);
    const entries = $("portalCreditEntries");
    entries.innerHTML = "";
    (Array.isArray(body.entries) ? body.entries : []).slice(0, 12).forEach((entry) => {
      const p = document.createElement("p");
      p.className = "muted";
      const amount = Number(entry.amountCents || 0);
      const label = entry.type === "REFERRAL_EARNED"
        ? `Indicação concluída${entry.referredPassengerName ? ` • ${entry.referredPassengerName}` : ""}`
        : entry.type === "BOOKING_CREDIT_USED" || entry.type === "TESTER_BOOKING_CREDIT_USED"
          ? "Créditos usados em viagem"
          : entry.type === "BOOKING_CREDIT_REFUND" || entry.type === "TESTER_BOOKING_CREDIT_REFUND"
            ? "Créditos devolvidos"
            : entry.type === "TESTER_BASELINE"
              ? "Saldo inicial da simulação"
              : "Movimentação de créditos";
      p.textContent = `${amount >= 0 ? "+" : "−"} ${formatMoney(Math.abs(amount))} • ${label}`;
      entries.appendChild(p);
    });
    if (!entries.children.length) entries.innerHTML = '<p class="muted">Você ainda não possui movimentações de créditos.</p>';
  } catch (error) {
    $("portalReferralMessage").textContent = error.message || "Falha ao carregar créditos.";
  }
}

async function sharePassengerReferral() {
  if (isTesterMode()) return;
  if (!passengerSessionToken || !driverUsername) return;
  $("portalReferralShare").disabled = true;
  $("portalReferralMessage").textContent = "Criando seu convite…";
  try {
    const response = await fetch("/v1/passenger/me/referral", {
      method: "POST",
      headers: portalHeaders(),
      body: JSON.stringify({ driverUsername }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível criar o convite.");
    const query = new URLSearchParams({ ref: body.referralCode });
    const link = `${location.origin}/${encodeURIComponent(driverUsername)}?${query.toString()}`;
    const shareData = { title: "Rota Certa", text: "Fui eu quem te indicou para a Agenda Rota Certa. Solicite seu convite por este link:", url: link };
    if (navigator.share) await navigator.share(shareData);
    else {
      await navigator.clipboard.writeText(link);
      $("portalReferralMessage").textContent = "Link de indicação copiado.";
      return;
    }
    $("portalReferralMessage").textContent = "Convite compartilhado.";
  } catch (error) {
    if (error && error.name === "AbortError") $("portalReferralMessage").textContent = "";
    else $("portalReferralMessage").textContent = error.message || "Falha ao compartilhar convite.";
  } finally {
    $("portalReferralShare").disabled = false;
  }
}

async function changePassengerPortalPassword() {
  if (isTesterMode()) return;
  const password = $("portalNewPassword").value;
  if (password.length < 8 || password.length > 72) {
    $("portalPasswordMessage").textContent = "Use uma senha de 8 a 72 caracteres.";
    return;
  }
  $("portalChangePassword").disabled = true;
  $("portalPasswordMessage").textContent = "Alterando senha…";
  try {
    const response = await fetch("/v1/passenger/me/password", {
      method: "POST",
      headers: portalHeaders(),
      body: JSON.stringify({ password }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível alterar a senha.");
    passengerMustChangePassword = false;
    $("portalNewPassword").value = "";
    $("portalPasswordMessage").textContent = "Senha alterada com sucesso.";
  } catch (error) {
    $("portalPasswordMessage").textContent = error.message || "Falha ao alterar a senha.";
  } finally {
    $("portalChangePassword").disabled = false;
  }
}

function passengerOperationalView(booking) {
  const bookingStatus = String(booking.status || "");
  if (bookingStatus === "REJECTED") {
    return { key: "REJECTED", icon: "⚪", title: "SOLICITAÇÃO NÃO APROVADA", message: "O motorista recusou esta solicitação." };
  }
  if (["CANCELLED", "EXPIRED"].includes(bookingStatus) || booking.operationalStatus === "CANCELLED") {
    return { key: "CANCELLED", icon: "❌", title: "RESERVA CANCELADA", message: "Esta reserva não está mais ativa." };
  }
  if (bookingStatus === "REQUESTED") {
    return { key: "PENDING", icon: "🟠", title: "AGUARDANDO APROVAÇÃO DO MOTORISTA", message: "Sua solicitação foi recebida e aguarda a decisão do motorista." };
  }
  const key = String(booking.operationalStatus || (bookingStatus === "CONFIRMED" ? "CONFIRMED" : "PENDING"));
  if (key === "PENDING") return { key, icon: "🟠", title: "AGUARDANDO APROVAÇÃO DO MOTORISTA", message: "Sua solicitação foi recebida." };
  if (key === "AT_LOCATION") return { key, icon: "📍", title: "MOTORISTA NO LOCAL", message: "O motorista informou que chegou ao local combinado." };
  if (key === "IN_CAR") return { key, icon: "🚗", title: "VOCÊ ESTÁ EMBARCADO", message: "Sua viagem está em andamento." };
  if (key === "COMPLETED") return { key, icon: "✅", title: "VIAGEM CONCLUÍDA", message: "Esta viagem foi concluída." };
  return { key: "CONFIRMED", icon: "🟢", title: "RESERVA CONFIRMADA", message: "Sua vaga está confirmada." };
}

function passengerCanCancelBooking(booking) {
  const operational = passengerOperationalView(booking).key;
  return !["IN_CAR", "COMPLETED", "CANCELLED", "REJECTED"].includes(operational) &&
    !["CANCELLED", "EXPIRED"].includes(String(booking.status || ""));
}

function confirmPassengerCancellation() {
  return new Promise((resolve) => {
    const backdrop = document.createElement("div");
    backdrop.style.cssText = "position:fixed;inset:0;background:#0008;display:grid;place-items:center;z-index:9999;padding:20px";
    const dialog = document.createElement("div");
    dialog.className = "card";
    dialog.style.cssText = "width:min(520px,100%);margin:0";
    const title = document.createElement("h2");
    title.textContent = "Cancelar sua reserva?";
    const actions = document.createElement("div");
    actions.className = "actions";
    const back = document.createElement("button");
    back.type = "button";
    back.className = "secondary";
    back.textContent = "Voltar";
    const confirm = document.createElement("button");
    confirm.type = "button";
    confirm.className = "dangerButton";
    confirm.textContent = "Cancelar reserva";
    const finish = (value) => { backdrop.remove(); resolve(value); };
    back.addEventListener("click", () => finish(false));
    confirm.addEventListener("click", () => finish(true));
    backdrop.addEventListener("click", (event) => { if (event.target === backdrop) finish(false); });
    actions.append(back, confirm);
    dialog.append(title, actions);
    backdrop.appendChild(dialog);
    document.body.appendChild(backdrop);
  });
}

function portalStop(tripItem, stopId) {
  return orderedStops(tripItem).find((stop) => stop.id === stopId) || null;
}

function renderPassengerBookings(entries) {
  const container = $("portalBookings");
  container.innerHTML = "";
  if (!entries.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "Nenhuma reserva vinculada a este acesso.";
    container.appendChild(empty);
    return;
  }

  const active = [];
  const history = [];
  entries.forEach((entry) => {
    const view = passengerOperationalView(entry.booking || {});
    (["COMPLETED", "CANCELLED", "REJECTED"].includes(view.key) ? history : active).push(entry);
  });

  const appendSection = (sectionTitle, sectionEntries) => {
    if (!sectionEntries.length) return;
    const heading = document.createElement("h2");
    heading.textContent = sectionTitle;
    heading.style.marginTop = "22px";
    container.appendChild(heading);

    sectionEntries.forEach(({ trip: tripItem, booking }) => {
      const state = passengerOperationalView(booking);
      const card = document.createElement("article");
      card.className = "agendaTrip";
      card.tabIndex = 0;
      card.setAttribute("role", "button");
      card.setAttribute("aria-expanded", "false");

      if (passengerUnreadBookingIds.has(String(booking.id || ""))) {
        const unread = document.createElement("div");
        unread.className = "error";
        unread.textContent = "🔴 VIAGEM ATUALIZADA";
        card.appendChild(unread);
      }

      const status = document.createElement("div");
      status.className = "bigPill";
      status.textContent = state.icon + " " + state.title;
      const when = document.createElement("p");
      when.className = "muted";
      when.textContent = formatDate(tripItem.departureAtMillis);
      const route = document.createElement("div");
      route.className = "agendaDate";
      route.textContent = portalStopName(tripItem, booking.boardingStopId) + " → " + portalStopName(tripItem, booking.dropoffStopId);
      card.append(status, when, route);

      if (Number.isFinite(Number(booking.totalFareCents))) {
        const amount = document.createElement("div");
        amount.className = "priceTotal";
        amount.textContent = formatMoney(Math.max(0, Number(booking.totalFareCents || 0)));
        card.appendChild(amount);
      }
      if (booking.paymentStatus === "PAID") {
        const paid = document.createElement("p");
        paid.className = "success";
        paid.textContent = "💰 PAGAMENTO CONFIRMADO";
        card.appendChild(paid);
      }

      const details = document.createElement("div");
      details.className = "hidden";
      details.style.marginTop = "16px";

      const current = document.createElement("div");
      current.className = "reviewBlock";
      const currentLabel = document.createElement("div");
      currentLabel.className = "reviewLabel";
      currentLabel.textContent = "ESTADO ATUAL";
      const currentValue = document.createElement("div");
      currentValue.className = "reviewValue";
      currentValue.textContent = state.icon + " " + state.title;
      const currentMessage = document.createElement("p");
      currentMessage.className = "muted";
      currentMessage.textContent = state.message;
      current.append(currentLabel, currentValue, currentMessage);
      details.appendChild(current);

      const fromStop = portalStop(tripItem, booking.boardingStopId);
      const toStop = portalStop(tripItem, booking.dropoffStopId);
      const journey = document.createElement("div");
      journey.className = "reviewBlock";
      const journeyLabel = document.createElement("div");
      journeyLabel.className = "reviewLabel";
      journeyLabel.textContent = "DATA E EMBARQUE";
      const journeyValue = document.createElement("div");
      journeyValue.className = "reviewValue";
      journeyValue.textContent = formatDate(tripItem.departureAtMillis);
      const places = document.createElement("p");
      places.className = "muted";
      places.textContent = (fromStop?.name || "Embarque") + " → " + (toStop?.name || "Destino");
      journey.append(journeyLabel, journeyValue, places);
      details.appendChild(journey);

      const reservation = document.createElement("div");
      reservation.className = "reviewBlock";
      const reservationLabel = document.createElement("div");
      reservationLabel.className = "reviewLabel";
      reservationLabel.textContent = "RESERVA";
      const reservationValue = document.createElement("div");
      reservationValue.className = "reviewValue";
      reservationValue.textContent = Math.max(1, Number(booking.seats || 1)) + " lugar(es)" +
        (Number.isFinite(Number(booking.totalFareCents))
          ? " • " + formatMoney(Math.max(0, Number(booking.totalFareCents || 0)))
          : "");
      reservation.append(reservationLabel, reservationValue);
      details.appendChild(reservation);

      const actions = document.createElement("div");
      actions.className = "actions";
      actions.addEventListener("click", (event) => event.stopPropagation());

      if (fromStop && fromStop.address) {
        const map = document.createElement("button");
        map.type = "button";
        map.className = "secondary";
        map.textContent = "📍 Abrir local de embarque";
        map.addEventListener("click", () => {
          const target = "https://www.google.com/maps/search/?api=1&query=" + encodeURIComponent(fromStop.address);
          window.open(target, "_blank", "noopener");
        });
        actions.appendChild(map);
      }

      if (passengerCanCancelBooking(booking)) {
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "dangerButton";
        cancel.textContent = "Cancelar reserva";
        const message = document.createElement("p");
        message.className = "muted";
        cancel.addEventListener("click", async () => {
          if (!(await confirmPassengerCancellation())) return;
          cancel.disabled = true;
          message.textContent = "Cancelando…";
          try {
            const token = tripItem.publicToken || tripItem.tripId;
            const response = await fetch(
              isTesterMode()
                ? "/v1/tester/trips/" + encodeURIComponent(token) + "/bookings/" + encodeURIComponent(booking.id) + "/cancel"
                : "/v1/passenger/me/bookings/" + encodeURIComponent(token) + "/" + encodeURIComponent(booking.id) + "/cancel",
              { method: "POST", headers: portalHeaders(), body: "{}" },
            );
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || "Não foi possível cancelar.");
            await loadPassengerBookings({ silent: true });
            await loadPassengerNotifications({ silent: true });
          } catch (error) {
            message.textContent = error.message || "Falha ao cancelar.";
          } finally {
            cancel.disabled = false;
          }
        });
        actions.append(cancel, message);
      } else if (state.key === "IN_CAR") {
        const locked = document.createElement("p");
        locked.className = "muted";
        locked.textContent = "A viagem já foi iniciada. Fale com o motorista caso precise de ajuda.";
        actions.appendChild(locked);
      }
      if (actions.children.length) details.appendChild(actions);

      const toggle = () => {
        const opening = details.classList.contains("hidden");
        details.classList.toggle("hidden", !opening);
        card.setAttribute("aria-expanded", opening ? "true" : "false");
        if (opening) tracePublicAction("PASSENGER_PORTAL_STATUS_RENDERED", { reason: state.key.toLowerCase() });
      };
      card.addEventListener("click", toggle);
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          toggle();
        }
      });
      card.appendChild(details);
      container.appendChild(card);
    });
  };

  appendSection("Viagens atuais", active);
  appendSection("Histórico", history);
}

async function loadPassengerBookings(options = {}) {
  if (!hasPrivatePortalSession()) return;
  show("portalLoginBox", false);
  show("portalAuthenticated", true);
  const container = $("portalBookings");
  const silent = options.silent === true;
  if (!silent) container.innerHTML = '<p class="muted">Carregando reservas…</p>';
  try {
    const response = await fetch(isTesterMode() ? "/v1/tester/me/bookings" : "/v1/passenger/me/bookings", { headers: portalHeaders() });
    const body = await response.json();
    if (response.status === 401) {
      if (isTesterMode()) {
        saveTesterSession("");
        return setError(body.message || "Sessão de teste encerrada.");
      }
      savePassengerSession("");
      show("portalLoginBox", true);
      show("portalAuthenticated", false);
      $("portalMessage").textContent = body.message || "Entre novamente.";
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível carregar as reservas.");
    renderPassengerBookings(Array.isArray(body.bookings) ? body.bookings : []);
    await loadPassengerCredits();
  } catch (error) {
    container.innerHTML = "";
    const message = document.createElement("p");
    message.className = "muted";
    message.textContent = error.message || "Falha ao carregar reservas.";
    container.appendChild(message);
  }
}

async function logoutPassengerPortal() {
  const token = passengerSessionToken;
  if (token) {
    try {
      await fetch("/v1/passenger/logout", {
        method: "POST",
        headers: authenticatedHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
        body: JSON.stringify({ driverUsername }),
      });
    } catch (_) {}
  }
  savePassengerSession("");
  passengerViewAccountActivated = true;
  passengerAgendaAdmin0418 = false;
  $("portalPassword").value = "";
  $("portalBookings").innerHTML = "";
  $("portalNotifications").innerHTML = '<p class="muted">Nenhuma notificação.</p>';
  setPassengerNotificationBadge(0);
  if (trip) renderTrip();
  else if (agendaToken || publicSlug) loadAgenda();
  else showAccessGate("agenda", "Área privada encerrada neste aparelho.");
}

function closePassengerPortal() {
  if (trip) {
    renderTrip();
  } else if (agendaToken || publicSlug) {
    loadAgenda();
  } else {
    history.back();
  }
}

function goBackToTrip() {
  if (!trip) return;
  renderTrip();
}

function goBackToAgenda() {
  if (history.length > 1) {
    history.back();
  } else {
    location.href = "/";
  }
}

$("accessLogin").addEventListener("click", loginAccessGate);
$("accessContact").addEventListener("input", (event) => { event.target.value = maskWhatsapp(event.target.value); });
$("privateAuthSubmit").addEventListener("click", submitPrivateAuthentication);
$("privateAuthBack").addEventListener("click", closePrivateAuth);
$("referralRequestContact").addEventListener("input", (event) => { event.target.value = maskWhatsapp(event.target.value); });
$("referralRequestSubmit").addEventListener("click", requestReferralInvite);
$("searchFromInput").addEventListener("input", (event) => handleSearchInput("from", event));
$("searchToInput").addEventListener("input", (event) => handleSearchInput("to", event));
$("searchFromInput").addEventListener("focus", () => renderSearchSuggestions("from"));
$("searchToInput").addEventListener("focus", () => renderSearchSuggestions("to"));
$("searchFromInput").addEventListener("keydown", (event) => handleSearchKeydown("from", event));
$("searchToInput").addEventListener("keydown", (event) => handleSearchKeydown("to", event));
document.addEventListener("click", (event) => {
  if (!event.target.closest(".searchSuggestHost")) closeSearchSuggestions();
});
$("searchDeparture").addEventListener("click", openCalendarPicker);
$("searchSubmit").addEventListener("click", submitTripSearch);
$("calendarBack").addEventListener("click", () => renderAgenda(agendaTripsCache));
$("seatBack").addEventListener("click", () => {
  if (seatPickerChannel === "whatsapp") {
    if (seatPickerReturnView === "searchResults") return showOnly("searchResults");
    if (seatPickerReturnView === "agenda") return renderAgenda(agendaTripsCache);
    return renderTrip();
  }
  if (seatPickerMode === "booking" && trip) renderTrip();
  else renderAgenda(agendaTripsCache);
});
$("seatMinus").addEventListener("click", () => changeSeatPicker(-1));
$("seatPlus").addEventListener("click", () => changeSeatPicker(1));
$("seatConfirm").addEventListener("click", confirmSeatPicker);
$("resultsBack").addEventListener("click", () => renderAgenda(agendaTripsCache));
$("openPassengerPortal").addEventListener("click", openPassengerPortal);
$("passengerNotificationsBell").addEventListener("click", openPassengerNotificationCenter);
$("portalMarkAllNotificationsRead").addEventListener("click", markAllPassengerNotificationsRead);
$("portalBack").addEventListener("click", closePassengerPortal);
$("portalLogin").addEventListener("click", loginPassengerPortal);
$("portalLogout").addEventListener("click", logoutPassengerPortal);
$("portalReferralShare").addEventListener("click", sharePassengerReferral);
$("portalChangePassword").addEventListener("click", changePassengerPortalPassword);
$("portalContact").addEventListener("input", (event) => {
  event.target.value = maskWhatsapp(event.target.value);
});
$("driverRatingLine").addEventListener("click", toggleDriverReviews);
$("boarding").addEventListener("change", () => {
  refreshSelectors();
  traceSearchChanged();
});
$("dropoff").addEventListener("change", () => {
  refreshAvailability();
  traceSearchChanged();
});
$("seats").addEventListener("input", refreshAvailability);
$("seats").addEventListener("change", () => {
  refreshAvailability();
  traceSearchChanged();
});
$("contact").addEventListener("input", (event) => {
  event.target.value = maskWhatsapp(event.target.value);
});
$("messageToDriver").addEventListener("input", () => {
  setWhatsappLink($("driverWhatsappReview"), $("messageToDriver").value);
});
$("startBooking").addEventListener("click", () => {
  if (isTesterMode()) return openTripSeatPicker(false);
  const stops = orderedStops(trip);
  let fromIndex = requestedBoardingStopId ? stops.findIndex((stop) => stop.id === requestedBoardingStopId) : 0;
  let toIndex = requestedDropoffStopId ? stops.findIndex((stop) => stop.id === requestedDropoffStopId) : stops.length - 1;
  if (fromIndex < 0) fromIndex = 0;
  if (toIndex <= fromIndex) toIndex = stops.length - 1;
  openWhatsappSeatPicker(trip, fromIndex, toIndex, "trip");
});
$("bookBlaBla").addEventListener("click", () => tracePublicAction("PUBLIC_BLABLACAR_RESERVATION_OPENED"));
$("reserve").addEventListener("click", reviewBooking);
$("quickUndo").addEventListener("click", undoQuickBooking);
$("quickDismiss").addEventListener("click", hideQuickBookingNotice);
$("confirmReserve").addEventListener("click", reserve);
$("editReservation").addEventListener("click", () => {
  showOnly("booking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("backToTrip").addEventListener("click", goBackToTrip);
$("backToAgenda").addEventListener("click", goBackToAgenda);
$("changeReservation").addEventListener("click", () => {
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("review", "edit");
  beginExistingReservationEdit();
});
$("showCancel").addEventListener("click", () => {
  if (!hasPrivatePortalSession()) return showPrivateAuthGate("review", "showCancel");
  showOnly("cancelBooking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("cancelBack").addEventListener("click", () => showOnly("confirmed"));
$("cancelReservation").addEventListener("click", cancelReservation);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
$("downloadIcs").addEventListener("click", downloadIcs);
$("subscribeCalendar").addEventListener("click", shareCalendarFeed);
$("resetTestSimulation").addEventListener("click", resetTesterSimulation);

tracePublicAction("PUBLIC_LINK_OPENED");

setInterval(() => {
  if (passengerSessionToken && !isTesterMode() && !document.hidden) {
    loadPassengerNotifications({ silent: true });
    loadPassengerBookings({ silent: true });
  }
}, 2_500);

window.addEventListener("online", () => {
  if (passengerSessionToken && !isTesterMode()) {
    loadPassengerNotifications({ silent: true });
    loadPassengerBookings({ silent: true });
  }
});

async function bootstrapTesterExperience() {
  if (testerBootstrapToken) {
    showOnly("loading");
    try {
      const response = await fetch("/v1/public/tester/bootstrap", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ bootstrapToken: testerBootstrapToken }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || "Link de teste inválido ou expirado.");
      savePassengerSession("");
      savePassengerContact("");
      saveAgendaViewSession("");
      passengerViewAccountActivated = false;
      passengerMustChangePassword = false;
      saveTesterSession(body.sessionToken, body);
      const cleanUrl = new URL(location.href);
      cleanUrl.searchParams.delete("tester");
      history.replaceState(null, "", cleanUrl.pathname + (cleanUrl.search ? cleanUrl.search : "") + cleanUrl.hash);
    } catch (error) {
      saveTesterSession("");
      setError(error.message || "Não foi possível iniciar o Modo Teste.");
      return true;
    }
  } else if (testerSessionToken) {
    try {
      const response = await fetch("/v1/tester/session", { headers: testerHeaders({ Accept: "application/json" }) });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || "Sessão de teste encerrada.");
      saveTesterSession(testerSessionToken, body);
    } catch (error) {
      saveTesterSession("");
      setError(error.message || "Sessão de teste encerrada.");
      return true;
    }
  }
  if (!isTesterMode()) return false;
  updateTesterChrome();
  if (portalMode) return openPassengerPortal(), true;
  if (tripToken) return await loadTrip(), true;
  if (agendaToken || publicSlug) return await loadAgenda(), true;
  setError("O link de teste não identifica uma agenda válida.");
  return true;
}

function openRequestedAdminTrip0470() {
  if (!requestedAdminTripIdentity0470 || !passengerAgendaAdmin0418) return false;
  const api = globalThis.RotaCertaAgendaAdmin0470;
  if (!api || typeof api.openTrip !== "function") return false;
  api.openTrip(requestedAdminTripIdentity0470, { source: "deep-link", returnScrollY: 0 });
  return true;
}

globalThis.RotaCertaAgendaHome0470 = {
  refreshTrip: async (canonicalTripId, options = {}) => {
    const restoreScrollY = Number.isFinite(Number(options.restoreScrollY)) ? Number(options.restoreScrollY) : window.scrollY;
    await loadAgenda({ restoreScrollY });
    const target = [...document.querySelectorAll("[data-canonical-trip-id]")]
      .find((node) => node.dataset.canonicalTripId === String(canonicalTripId || ""));
    if (target && options.focus !== false) {
      const button = target.querySelector(".adminCardAction0470");
      if (button) button.focus({ preventScroll: true });
    }
    return Boolean(target);
  },
};

async function bootstrapAuthenticatedExperience() {
  updateAuthenticatedChrome();
  updateTesterChrome();
  if (await bootstrapTesterExperience()) return;
  if (!portalMode && !tripToken && !agendaToken && !publicSlug && !referralCode) return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
  if (referralCode && !tripToken && !agendaToken && !publicSlug) return showAccessGate("agenda");
  await validatePassengerSession();
  if (tripToken) return loadTrip();
  if (agendaToken || publicSlug) {
    if (requestedAdminTripIdentity0470 && !passengerAgendaAdmin0418 && agendaAuthenticationRequired0428) {
      return showPrivateAuthGate("portal");
    }
    await loadAgenda();
    if (requestedAdminTripIdentity0470 && passengerAgendaAdmin0418) setTimeout(() => openRequestedAdminTrip0470(), 0);
    return;
  }
  const accessMessage = $("accessMessage").textContent;
  showAccessGate(pendingAuthDestination, accessMessage);
}

bootstrapAuthenticatedExperience();
