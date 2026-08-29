"use strict";

const DateContract = window.RotaCertaDateContract;
if (!DateContract) throw new Error("Rota Certa date contract unavailable");

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const driverUsername = (params.get("motorista") || "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 32);
const portalMode = params.get("portal") === "1";
const requestedBoardingStopId = (params.get("embarque") || "").replace(/[^A-Za-z0-9_-]/g, "");
const requestedDropoffStopId = (params.get("destino") || "").replace(/[^A-Za-z0-9_-]/g, "");
const requestedSeats = Math.max(1, Math.min(9, Number(params.get("lugares") || 1) || 1));
const referralCode = (params.get("ref") || "").replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);

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
let passengerSessionContact = (() => {
  try { return localStorage.getItem("rotacerta-passenger-contact") || ""; } catch (_) { return ""; }
})();
let agendaTripsCache = [];
let pendingAuthDestination = portalMode ? "portal" : (tripToken ? "trip" : "agenda");
let calendarPickerTarget = "departure";
let seatPickerDraft = 1;
let passengerCreditBalanceCents = 0;
let passengerMustChangePassword = false;
let passengerViewAccountActivated = false;
let pendingPrivateAction = "";

function localTodayKey() {
  return DateContract.todayKey();
}

const searchState = {
  from: "",
  to: "",
  departure: localTodayKey(),
  returnDate: "",
  seats: 1,
};

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
  const payload = {
    event,
    sessionId: publicDebugSessionId,
    screen: tripToken ? "trip" : (agendaToken ? "agenda" : "unknown"),
    tripToken: tripToken || "",
    agendaToken: tripToken ? "" : (agendaToken || ""),
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
  return item?.isFull === true || item?.status === "FULL" || item?.canReserve === false ||
    (range.minimum === 0 && range.maximum === 0);
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
  return Math.max(0, available);
}

function fareFor(fromIndex, toIndex) {
  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;
  const stops = orderedStops();
  return stops.slice(fromIndex, toIndex)
    .reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
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
  if (!trip) return `Olá, ${driverDisplayName || "motorista"}. Estou falando pela Agenda Pública do Rota Certa.`;
  const { from, to } = routeLabel(trip);
  return `Olá, ${driverDisplayName || "motorista"}. Estou falando pela Agenda Pública do Rota Certa sobre a viagem ${from} → ${to}, ${formatDate(trip.departureAtMillis)}.`;
}


function authenticatedHeaders(extra = {}) {
  const headers = { ...extra };
  if (passengerSessionToken) headers.Authorization = `Bearer ${passengerSessionToken}`;
  return headers;
}

function agendaViewHeaders(extra = {}) {
  const headers = { ...extra };
  if (passengerAgendaViewToken) headers["X-Rota-Certa-Agenda-View-Token"] = passengerAgendaViewToken;
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
  show("openPassengerPortal", Boolean(passengerAgendaViewToken || passengerSessionToken));
}

function showAccessGate(destination = pendingAuthDestination, message = "") {
  pendingAuthDestination = destination || "agenda";
  pendingPrivateAction = "";
  showOnly("accessGate");
  updateAuthenticatedChrome();
  const hasPublicTarget = Boolean(driverUsername && (agendaToken || tripToken));
  show("accessLoginBox", hasPublicTarget);
  show("referralRequestBox", Boolean(referralCode && driverUsername));
  $("accessMessage").textContent = message;
  if (passengerSessionContact && !$("accessContact").value) $("accessContact").value = maskWhatsapp(passengerSessionContact);
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function requestPublicAgendaAccess(contactInput = "") {
  const passengerContact = normalizeWhatsapp(contactInput || $("accessContact").value || passengerSessionContact);
  if (!passengerContact) {
    $("accessMessage").textContent = "Informe seu WhatsApp com DDD.";
    return false;
  }
  if (!driverUsername || (!agendaToken && !tripToken)) {
    $("accessMessage").textContent = "Este link não identifica uma agenda válida.";
    return false;
  }
  $("accessLogin").disabled = true;
  $("accessMessage").textContent = "Verificando acesso…";
  try {
    const response = await fetch("/v1/public/passenger-access", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ passengerContact, driverUsername, agendaToken, tripToken }),
    });
    const body = await response.json();
    if (!response.ok) {
      saveAgendaViewSession("");
      $("accessMessage").textContent = body.message || "Seu acesso a esta agenda não está disponível.";
      return false;
    }
    savePassengerContact(body.passengerContact || passengerContact);
    saveAgendaViewSession(body.viewToken);
    passengerViewAccountActivated = body.accountActivated === true;
    $("accessMessage").textContent = "";
    await continueAfterViewAccess();
    return true;
  } catch (error) {
    $("accessMessage").textContent = error.message || "Não foi possível verificar o acesso.";
    return false;
  } finally {
    $("accessLogin").disabled = false;
  }
}

async function validatePassengerSession() {
  if (!passengerSessionToken) return false;
  try {
    const response = await fetch("/v1/passenger/me", { headers: authenticatedHeaders({ Accept: "application/json" }) });
    const body = await response.json();
    if (!response.ok) {
      savePassengerSession("");
      return false;
    }
    savePassengerContact(body.passengerContact || passengerSessionContact);
    passengerMustChangePassword = body.mustChangePassword === true;
    passengerViewAccountActivated = true;
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
  if (agendaToken) return loadAgenda();
  return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
}

async function continueAfterAuthentication() {
  updateAuthenticatedChrome();
  const resume = pendingPrivateAction;
  pendingPrivateAction = "";
  if (resume === "reserve") return reserve();
  if (resume === "update") return updateExistingReservation();
  if (resume === "cancel") return cancelReservation();
  if (resume === "edit") return beginExistingReservationEdit();
  if (resume === "showCancel") {
    showOnly("cancelBooking");
    window.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }
  if (pendingAuthDestination === "portal") return openPassengerPortal();
  if (pendingAuthDestination === "booking") return openBookingFlow();
  if (pendingAuthDestination === "review") {
    showOnly("review");
    return;
  }
  if (pendingAuthDestination === "trip" && tripToken) return loadTrip();
  if (agendaToken) return loadAgenda();
  return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
}

async function loginAccessGate() {
  await requestPublicAgendaAccess();
}

function showPrivateAuthGate(destination = "portal", resumeAction = "") {
  if (!passengerAgendaViewToken || !passengerSessionContact) {
    return showAccessGate(destination, "Informe seu WhatsApp para continuar.");
  }
  pendingAuthDestination = destination;
  pendingPrivateAction = resumeAction;
  $("privateAuthContact").value = maskWhatsapp(passengerSessionContact);
  $("privateAuthPassword").value = "";
  $("privateAuthPasswordConfirm").value = "";
  $("privateAuthMessage").textContent = "";
  show("privateAuthConfirmWrap", !passengerViewAccountActivated);
  $("privateAuthTitle").textContent = passengerViewAccountActivated ? "Entre para continuar" : "Crie sua senha para continuar";
  $("privateAuthIntro").textContent = passengerViewAccountActivated
    ? "Sua senha protege reservas e informações privadas."
    : "Esta será a senha da sua área particular. Seu cadastro atual será mantido.";
  $("privateAuthSubmit").textContent = passengerViewAccountActivated ? "Entrar" : "Criar senha e continuar";
  showOnly("privateAuth");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function submitPrivateAuthentication() {
  const passengerContact = passengerSessionContact;
  const password = $("privateAuthPassword").value;
  const confirmation = $("privateAuthPasswordConfirm").value;
  if (!passengerContact || password.length < 8 || password.length > 72) {
    $("privateAuthMessage").textContent = "Use uma senha de 8 a 72 caracteres.";
    return;
  }
  if (!passengerViewAccountActivated && password !== confirmation) {
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
      body: JSON.stringify({ passengerContact, password, driverUsername }),
    });
    const body = await response.json();
    if (!response.ok) {
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
  if (agendaToken && agendaTripsCache.length) return renderAgenda(agendaTripsCache);
  if (agendaToken) return loadAgenda();
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

async function loadAgenda() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return setError("Link de agenda inválido.");
  let statusCode = 0;
  try {
    const response = await fetch(
      `/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`,
      { headers: agendaViewHeaders({ Accept: "application/json" }) },
    );
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok && (response.status === 401 || response.status === 403)) {
      saveAgendaViewSession("");
      return showAccessGate("agenda", body.message || "Informe seu WhatsApp novamente.");
    }
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    driverProfile = body.driver || {};
    driverDisplayName = driverProfile.displayName || driverUsername;
    tracePublicAction("PUBLIC_AGENDA_LOADED", { statusCode });
    agendaTripsCache = Array.isArray(body.trips) ? body.trips : [];
    renderAgenda(agendaTripsCache);
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
  const returnValue = $("searchReturnValue");
  returnValue.textContent = searchState.returnDate ? formatSearchDate(searchState.returnDate) : "Data";
  returnValue.classList.toggle("searchPlaceholder", !searchState.returnDate);
  $("searchSeatsValue").textContent = searchState.seats === 1 ? "1 passageiro" : `${searchState.seats} passageiros`;
}

function openCalendarPicker(target) {
  calendarPickerTarget = target;
  $("calendarTitle").textContent = target === "returnDate" ? "Quando você volta?" : "Quando você vai?";
  show("calendarNoReturn", target === "returnDate");
  renderCalendarMonths();
  showOnly("calendarPicker");
  window.scrollTo({ top: 0, behavior: "auto" });
}

function renderCalendarMonths() {
  const container = $("calendarMonths");
  container.innerHTML = "";
  let minimumKey = localTodayKey();
  if (
    calendarPickerTarget === "returnDate" &&
    DateContract.normalizeKey(searchState.departure) &&
    DateContract.compareKeys(searchState.departure, minimumKey) >= 0
  ) {
    minimumKey = searchState.departure;
  }
  const minimum = DateContract.parseKey(minimumKey);
  const monthStart = new Date(minimum.getFullYear(), minimum.getMonth(), 1);
  const selected = calendarPickerTarget === "returnDate" ? searchState.returnDate : searchState.departure;
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
  if (calendarPickerTarget === "returnDate") searchState.returnDate = key;
  else {
    searchState.departure = key;
    if (searchState.returnDate && DateContract.isBefore(searchState.returnDate, key)) searchState.returnDate = "";
  }
  updateSearchUi();
  renderAgenda(agendaTripsCache);
}

function clearReturnDate() {
  searchState.returnDate = "";
  updateSearchUi();
  renderAgenda(agendaTripsCache);
}

function openSeatPicker() {
  seatPickerDraft = searchState.seats;
  $("seatPickerValue").textContent = String(seatPickerDraft);
  showOnly("seatPicker");
}

function maxAgendaCapacity() {
  return Math.max(1, Math.min(9, ...agendaTripsCache.map((item) => Number(item.capacity || 1))));
}

function changeSeatPicker(delta) {
  seatPickerDraft = Math.max(1, Math.min(maxAgendaCapacity(), seatPickerDraft + delta));
  $("seatPickerValue").textContent = String(seatPickerDraft);
}

function confirmSeatPicker() {
  searchState.seats = seatPickerDraft;
  updateSearchUi();
  renderAgenda(agendaTripsCache);
}

function stopMatchesSearch(stop, query) {
  const needle = normalizeSearchText(query);
  if (!needle) return false;
  const name = normalizeSearchText(stop && stop.name);
  const address = normalizeSearchText(stop && stop.address);
  return name === needle || name.includes(needle) || address.includes(needle);
}

function availableForTripSegment(item, fromIndex, toIndex) {
  if (fromIndex < 0 || toIndex <= fromIndex) return 0;
  let available = Number(item.capacity || 0);
  for (let index = fromIndex; index < toIndex; index += 1) {
    available = Math.min(available, Number(item.capacity || 0) - Number((item.segmentLoads || [])[index] || 0));
  }
  return Math.max(0, available);
}

function matchTripSegment(item, fromQuery, toQuery) {
  const stops = orderedStops(item);
  const fromIndex = stops.findIndex((stop) => stopMatchesSearch(stop, fromQuery));
  if (fromIndex < 0) return null;
  const toIndex = stops.findIndex((stop, index) => index > fromIndex && stopMatchesSearch(stop, toQuery));
  if (toIndex < 0) return null;
  return { item, fromIndex, toIndex, available: availableForTripSegment(item, fromIndex, toIndex) };
}

function searchDirection(fromQuery, toQuery, dateKey, seats) {
  const routeMatches = agendaTripsCache.map((item) => matchTripSegment(item, fromQuery, toQuery)).filter(Boolean);
  if (!routeMatches.length) {
    return { matches: [], reason: "O local informado não faz parte do percurso disponível nesta data." };
  }
  const dated = routeMatches.filter((entry) => dateKeyFromMillis(entry.item.departureAtMillis) === dateKey);
  if (!dated.length) {
    return { matches: [], reason: "Nenhuma viagem publicada para esse trecho nessa data." };
  }
  const available = dated.filter((entry) => entry.available >= seats && !isFullTrip(entry.item));
  if (!available.length) {
    return { matches: [], reason: `Não há ${seats} lugar(es) disponível(is) nesse trecho para essa data.` };
  }
  return { matches: available, reason: "" };
}

function renderSearchSummary() {
  const summary = $("searchSummary");
  summary.innerHTML = "";
  const route = document.createElement("div");
  route.className = "searchSummaryRoute";
  route.textContent = `${searchState.from} → ${searchState.to}`;
  const meta = document.createElement("div");
  meta.className = "searchSummaryMeta";
  const parts = [formatSearchDate(searchState.departure), searchState.seats === 1 ? "1 passageiro" : `${searchState.seats} passageiros`];
  if (searchState.returnDate) parts.push(`volta ${formatSearchDate(searchState.returnDate)}`);
  meta.textContent = parts.join(" • ");
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
  if (normalizeSearchText(searchState.from) === normalizeSearchText(searchState.to)) {
    $("searchMessage").textContent = "Origem e destino precisam ser diferentes.";
    return;
  }
  const outbound = searchDirection(searchState.from, searchState.to, searchState.departure, searchState.seats);
  const returning = searchState.returnDate
    ? searchDirection(searchState.to, searchState.from, searchState.returnDate, searchState.seats)
    : null;
  renderSearchSummary();
  renderDirectionResult("outboundResult", "Ida", outbound);
  if (returning) {
    show("returnResult", true);
    renderDirectionResult("returnResult", "Volta", returning);
  } else {
    show("returnResult", false);
    $("returnResult").innerHTML = "";
  }
  showOnly("searchResults");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function swapSearchRoute() {
  const previous = searchState.from;
  searchState.from = searchState.to;
  searchState.to = previous;
  updateSearchUi();
}

function renderAgenda(trips) {
  showOnly("agenda");
  $("driverName").textContent = driverDisplayName ? `Viagens com ${driverDisplayName}` : "Próximas viagens";
  updateSearchUi();
  const container = $("agendaTrips");
  container.innerHTML = "";
  if (!trips.length) {
    const empty = document.createElement("div");
    empty.className = "card muted";
    empty.textContent = "Nenhuma próxima viagem publicada no momento.";
    container.appendChild(empty);
    return;
  }
  renderAgendaCards(trips.map((item) => ({ item })), container, false);
}

function renderAgendaCards(entries, container, filtered = false) {
  entries.forEach((entry) => {
    const item = entry.item || entry;
    const full = isFullTrip(item);
    const range = seatRange(item);
    const stops = orderedStops(item);
    const from = stops[0]?.name || "Origem";
    const to = stops[stops.length - 1]?.name || "Destino";
    const fare = fullFareFor(item);
    const card = document.createElement(full ? "article" : "a");
    card.className = full ? "agendaTrip agendaTripFull" : "agendaTrip";

    if (full) {
      card.setAttribute("aria-disabled", "true");
      card.setAttribute("tabindex", "-1");
    } else {
      const owner = item.driverUsername || driverUsername;
      const next = new URLSearchParams({ motorista: owner, trip: item.publicToken || item.tripId });
      if (filtered && Number.isInteger(entry.fromIndex) && Number.isInteger(entry.toIndex)) {
        next.set("embarque", stops[entry.fromIndex]?.id || "");
        next.set("destino", stops[entry.toIndex]?.id || "");
        next.set("lugares", String(searchState.seats));
      }
      card.href = `/?${next.toString()}`;
      card.addEventListener("click", () => tracePublicAction("PUBLIC_TRIP_SELECTED"));
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
    if (filtered && Number.isFinite(Number(entry.available))) {
      seats.textContent = `🪑 ${Math.max(0, Number(entry.available))} vaga(s) neste trecho`;
    } else {
      seats.textContent = full ? "🪑 0 vagas" : (range.minimum === range.maximum ? `🪑 ${range.maximum} vaga(s)` : `🪑 ${range.minimum}–${range.maximum} vagas`);
    }
    meta.append(time, seats);
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
    const action = document.createElement("div");
    action.className = "agendaAction";
    action.textContent = full ? "CHEIO" : "VER DETALHES";
    if (full) {
      const fullWord = document.createElement("div");
      fullWord.className = "fullWord";
      fullWord.textContent = "Cheio";
      bottom.appendChild(fullWord);
    }
    card.append(date, routeFrom, arrow, routeTo, meta, bottom, action);
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
      saveAgendaViewSession("");
      return showAccessGate("trip", body.message || "Informe seu WhatsApp novamente.");
    }
    if (!response.ok) throw new Error(body.message || "Viagem indisponível.");
    trip = body;
    driverProfile = body.driver || {};
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

  $("status").textContent = full ? "Cheio" : "Disponível";
  $("status").classList.toggle("statusFull", full);
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

  $("tripAvailability").textContent = full
    ? "🪑 Cheio • 0 vagas"
    : (range.minimum === range.maximum
      ? `🪑 ${range.maximum} vaga(s) disponível(is)`
      : `🪑 ${range.minimum}–${range.maximum} vagas por trecho`);

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

  show("tripSticky", !full && trip.canReserve !== false);
  $("startBooking").disabled = full || trip.canReserve === false;
  if (full) {
    $("startBooking").textContent = "Cheio";
  } else {
    $("startBooking").textContent = "Fazer pedido de reserva";
  }

  prepareBookingSelectors();
  restoreCancellation();
  if (!restoreExistingBooking()) setWhatsappLink($("driverWhatsappTrip"), defaultDriverMessage());
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
  addFact("Capacidade", `${trip.capacity} lugar(es)`);
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

function openBookingFlow() {
  if (!trip || isFullTrip(trip) || trip.canReserve === false) return;
  editingExistingBooking = false;
  $("confirmReserve").textContent = "Fazer pedido de reserva";
  if (passengerSessionContact) {
    $("contact").value = maskWhatsapp(passengerSessionContact);
    $("contact").readOnly = true;
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
  if (available > 0 && requested > available) {
    requested = available;
    seatsInput.value = String(available);
  }

  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa` : "";
  $("availability").textContent = available > 0
    ? `${available} lugar(es) disponível(is) neste trecho${fareText}`
    : "Sem vagas neste trecho. Escolha outro embarque ou destino.";
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
  const name = $("name").value.trim();
  const passengerContact = normalizeWhatsapp($("contact").value);
  const seats = Number($("seats").value || 0);

  if (!name) return void ($("bookingMessage").textContent = "Informe seu nome.");
  if (!passengerContact) return void ($("bookingMessage").textContent = "Informe seu WhatsApp com DDD.");
  if (passengerSessionContact && passengerContact !== passengerSessionContact) {
    return void ($("bookingMessage").textContent = "Use o mesmo WhatsApp do seu acesso.");
  }
  if (!$("boarding").value || !$("dropoff").value || seats < 1) {
    return void ($("bookingMessage").textContent = "Escolha um trecho com vagas.");
  }

  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  const from = stops[fromIndex]?.name || "Embarque";
  const to = stops[toIndex]?.name || "Destino";
  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const totalFareCents = farePerSeatCents * seats;
  const requestedCreditCents = Math.max(0, Math.round(Number($("creditToUse").value || 0) * 100));
  const creditToUseCents = editingExistingBooking ? 0 : Math.min(requestedCreditCents, passengerCreditBalanceCents, totalFareCents);
  const amountDueCents = Math.max(0, totalFareCents - creditToUseCents);

  pendingBooking = {
    passengerName: name,
    passengerContact,
    boardingStopId: $("boarding").value,
    dropoffStopId: $("dropoff").value,
    seats,
    creditToUseCents,
  };

  tracePublicAction("PUBLIC_RESERVATION_STARTED", { seats, fromIndex, toIndex });

  $("reviewRoute").textContent = `${from} → ${to}`;
  $("reviewDate").textContent = formatDate(trip.departureAtMillis);
  $("reviewStops").textContent = `${from} → ${to}`;
  $("reviewSeats").textContent = seats === 1 ? "1 lugar" : `${seats} lugares`;
  if (totalFareCents > 0) {
    $("reviewPrice").textContent = creditToUseCents > 0
      ? `${formatMoney(totalFareCents)} • créditos −${formatMoney(creditToUseCents)} • a pagar ${formatMoney(amountDueCents)}`
      : formatMoney(totalFareCents);
  } else {
    $("reviewPrice").textContent = "Valor não informado";
  }
  $("reviewPayment").textContent = amountDueCents === 0 && totalFareCents > 0
    ? "Esta viagem ficará integralmente coberta pelos seus créditos."
    : (driverProfile.paymentInstructions || "Forma de pagamento não informada pelo motorista.");

  const suggested = `Olá, ${driverDisplayName || "motorista"}. Sou ${name}. Fiz um pedido para ${from} → ${to}, ${formatDate(trip.departureAtMillis)}, para ${seats} lugar(es).`;
  $("messageToDriver").value = suggested;
  $("messageHeading").textContent = `Mande uma mensagem para ${driverDisplayName || "o motorista"}`;

  const hasWhatsapp = setWhatsappLink($("driverWhatsappReview"), suggested);
  show("whatsappUnavailable", !hasWhatsapp);

  $("confirmReserve").textContent = editingExistingBooking ? "Confirmar alteração" : "Fazer pedido de reserva";
  $("bookingMessage").textContent = "";
  showOnly("review");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function requestIdentity(payload) {
  const fingerprint = JSON.stringify(payload);
  const key = `rotacerta-booking-intent-${tripToken}`;
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
  if (!trip || !pendingBooking) return;
  if (!passengerSessionToken) return showPrivateAuthGate("review", "reserve");
  if (editingExistingBooking && confirmedBooking?.bookingId && confirmedBooking?.cancellationToken) {
    return updateExistingReservation();
  }

  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Enviando seu pedido…";
  const idempotencyKey = requestIdentity(pendingBooking);
  const debugStops = orderedStops();
  const fromIndex = debugStops.findIndex((s) => s.id === pendingBooking.boardingStopId);
  const toIndex = debugStops.findIndex((s) => s.id === pendingBooking.dropoffStopId);
  let statusCode = 0;

  tracePublicAction("PUBLIC_RESERVATION_REQUEST_SENT", {
    seats: pendingBooking.seats,
    fromIndex,
    toIndex,
  });

  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": idempotencyKey,
        ...(passengerSessionToken ? { Authorization: `Bearer ${passengerSessionToken}` } : {}),
      },
      body: JSON.stringify({ ...pendingBooking, idempotencyKey }),
    });
    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      savePassengerSession("");
      passengerViewAccountActivated = true;
      showPrivateAuthGate("review", "reserve");
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível reservar.");

    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      passengerName: pendingBooking.passengerName,
      passengerContact: pendingBooking.passengerContact,
      boardingStopId: pendingBooking.boardingStopId,
      dropoffStopId: pendingBooking.dropoffStopId,
      seats: pendingBooking.seats,
      farePerSeatCents: Number(body.farePerSeatCents || 0),
      totalFareCents: Number(body.totalFareCents || 0),
      creditAppliedCents: Number(body.creditAppliedCents || 0),
      amountDueCents: Number(body.amountDueCents || 0),
    };

    try {
      localStorage.setItem(
        `rotacerta-booking-${body.bookingId}`,
        JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }),
      );
      localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking));
    } catch (_) {}

    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    $("cancelCode").textContent = body.cancellationToken;

    const total = Number(body.totalFareCents || 0);
    const credits = Number(body.creditAppliedCents || 0);
    const due = Number(body.amountDueCents || 0);
    const confirmedFare = total > 0
      ? credits > 0
        ? ` Valor da viagem: ${formatMoney(total)}. Créditos usados: ${formatMoney(credits)}. A pagar: ${formatMoney(due)}.`
        : ` Valor total: ${formatMoney(total)}.`
      : "";
    $("confirmationText").textContent = body.replayed
      ? `Esta reserva já estava confirmada. Nenhuma duplicata foi criada.${confirmedFare}`
      : `Seu pedido foi confirmado para ${pendingBooking.seats} lugar(es).${confirmedFare}`;

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

    const confirmationWhatsappMessage =
      `Olá, ${driverDisplayName || "motorista"}. Minha reserva pelo Rota Certa foi confirmada. Reserva ${confirmedBooking.bookingId}.`;
    setWhatsappLink($("driverWhatsappConfirmed"), confirmationWhatsappMessage);

    passengerCreditBalanceCents = Math.max(0, passengerCreditBalanceCents - credits);
    loadPassengerCredits();
    pendingBooking = null;
    showOnly("confirmed");
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (error) {
    tracePublicAction("PUBLIC_RESERVATION_FAILED", {
      statusCode,
      reason: statusCode ? `http_${statusCode}` : "network_or_client_error",
      seats: pendingBooking?.seats || 0,
      fromIndex,
      toIndex,
    });
    $("reviewMessage").textContent = error.message || "Falha ao confirmar reserva.";
    if (statusCode === 409) await loadTrip();
  } finally {
    $("confirmReserve").disabled = false;
  }
}

function cancellationStorageKey() {
  return `rotacerta-booking-trip-${tripToken}`;
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
  $("confirmationText").textContent = "Sua reserva já está confirmada neste aparelho.";
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
  $("name").value = confirmedBooking.passengerName || "";
  $("contact").value = maskWhatsapp(confirmedBooking.passengerContact || "");
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
  if (!passengerSessionToken) return showPrivateAuthGate("review", "update");

  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Atualizando sua reserva…";
  let statusCode = 0;

  try {
    const response = await fetch(
      `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(confirmedBooking.bookingId)}`,
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
      savePassengerSession("");
      passengerViewAccountActivated = true;
      showPrivateAuthGate("review", "update");
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível alterar a reserva.");

    confirmedBooking = {
      ...confirmedBooking,
      passengerName: pendingBooking.passengerName,
      passengerContact: pendingBooking.passengerContact,
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
    $("reviewMessage").textContent = error.message || "Falha ao alterar reserva.";
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
  if (!passengerSessionToken) return showPrivateAuthGate("review", "cancel");
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
      `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`,
      {
        method: "POST",
        headers: authenticatedHeaders({ "Content-Type": "application/json", Accept: "application/json" }),
        body: JSON.stringify({ cancellationToken }),
      },
    );
    statusCode = response.status;
    const body = await response.json();
    if (response.status === 401) {
      savePassengerSession("");
      passengerViewAccountActivated = true;
      showPrivateAuthGate("review", "cancel");
      return;
    }
    if (!response.ok) throw new Error(body.message || "Não foi possível cancelar.");

    try {
      localStorage.removeItem(cancellationStorageKey());
      localStorage.removeItem(`rotacerta-booking-${bookingId}`);
      localStorage.removeItem(`rotacerta-booking-intent-${tripToken}`);
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
  if (driverUsername.length < 3 || agendaToken.length < 16) return;
  const url = `${location.origin}/calendar/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}.ics`;
  const payload = {
    title: "Rota Certa — Agenda de Viagens",
    text: "Calendário público das viagens.",
    url,
  };
  try {
    if (navigator.share) {
      await navigator.share(payload);
      return;
    }
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      $("subscribeCalendar").textContent = "Link do calendário copiado";
      return;
    }
  } catch (_) {}
  location.href = url;
}


function portalHeaders() {
  return {
    Accept: "application/json",
    "Content-Type": "application/json",
    Authorization: `Bearer ${passengerSessionToken}`,
  };
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
  if (!passengerSessionToken) return showPrivateAuthGate("portal");
  showOnly("passengerPortal");
  $("portalMessage").textContent = "";
  show("portalLoginBox", false);
  show("portalAuthenticated", true);
  if (passengerMustChangePassword) $("portalPasswordMessage").textContent = "Você entrou com uma senha temporária. Crie uma nova senha.";
  loadPassengerCredits();
  loadPassengerBookings();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function loginPassengerPortal() {
  const passengerContact = normalizeWhatsapp($("portalContact").value);
  const password = $("portalPassword").value;
  if (!passengerContact || password.length < 8) {
    $("portalMessage").textContent = "Informe seu WhatsApp com DDD e sua senha.";
    return;
  }
  $("portalLogin").disabled = true;
  $("portalMessage").textContent = "Entrando…";
  try {
    const response = await fetch("/v1/passenger/session", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ passengerContact, password, driverUsername }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível entrar.");
    savePassengerSession(body.sessionToken);
    savePassengerContact(body.passengerContact || passengerContact);
    passengerMustChangePassword = body.mustChangePassword === true;
    $("portalPassword").value = "";
    $("portalMessage").textContent = "";
    await loadPassengerBookings();
  } catch (error) {
    $("portalMessage").textContent = error.message || "Falha ao entrar.";
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
  if (!passengerSessionToken || !driverUsername) return;
  try {
    const response = await fetch(`/v1/passenger/me/credits?driverUsername=${encodeURIComponent(driverUsername)}`, { headers: portalHeaders() });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível carregar seus créditos.");
    passengerCreditBalanceCents = Math.max(0, Number(body.balanceCents || 0));
    $("portalCreditBalance").textContent = formatMoney(passengerCreditBalanceCents);
    $("portalReferralInfo").textContent = Number(body.referralCreditCents || 0) > 0
      ? `Cada indicação elegível concluída rende ${formatMoney(body.referralCreditCents)} em créditos.`
      : "O motorista ainda não definiu créditos por indicação.";
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
        : entry.type === "BOOKING_CREDIT_USED"
          ? "Créditos usados em viagem"
          : entry.type === "BOOKING_CREDIT_REFUND"
            ? "Créditos devolvidos"
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
    const query = new URLSearchParams({ motorista: driverUsername, ref: body.referralCode });
    if (agendaToken) query.set("agenda", agendaToken);
    const link = `${location.origin}/?${query.toString()}`;
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

  entries.forEach(({ trip: tripItem, booking }) => {
    const card = document.createElement("article");
    card.className = "agendaTrip";
    const route = document.createElement("div");
    route.className = "agendaDate";
    route.textContent = `${portalStopName(tripItem, booking.boardingStopId)} → ${portalStopName(tripItem, booking.dropoffStopId)}`;
    const when = document.createElement("p");
    when.className = "muted";
    when.textContent = formatDate(tripItem.departureAtMillis);
    const status = document.createElement("div");
    status.className = "bigPill";
    status.textContent = `${booking.status || "CONFIRMED"} • ${booking.seats || 1} lugar(es)`;
    card.append(route, when, status);

    const active = !["CANCELLED", "EXPIRED"].includes(String(booking.status || ""));
    if (active) {
      const stops = orderedStops(tripItem);
      const fromSelect = document.createElement("select");
      const toSelect = document.createElement("select");
      stops.slice(0, -1).forEach((stop) => {
        const option = document.createElement("option");
        option.value = stop.id;
        option.textContent = `Embarque: ${stop.name}`;
        option.selected = stop.id === booking.boardingStopId;
        fromSelect.appendChild(option);
      });
      const refreshTo = () => {
        const fromIndex = stops.findIndex((stop) => stop.id === fromSelect.value);
        const current = toSelect.value || booking.dropoffStopId;
        toSelect.innerHTML = "";
        stops.forEach((stop, index) => {
          if (index <= fromIndex) return;
          const option = document.createElement("option");
          option.value = stop.id;
          option.textContent = `Destino: ${stop.name}`;
          option.selected = stop.id === current;
          toSelect.appendChild(option);
        });
      };
      fromSelect.addEventListener("change", refreshTo);
      refreshTo();

      const seatsInput = document.createElement("input");
      seatsInput.type = "number";
      seatsInput.min = "1";
      seatsInput.max = String(Math.max(1, Number(tripItem.capacity || 1)));
      seatsInput.value = String(Math.max(1, Number(booking.seats || 1)));
      seatsInput.inputMode = "numeric";

      const message = document.createElement("p");
      message.className = "muted";
      const save = document.createElement("button");
      save.type = "button";
      save.className = "secondary";
      save.textContent = "Salvar alteração";
      save.addEventListener("click", async () => {
        save.disabled = true;
        message.textContent = "Salvando…";
        try {
          const token = tripItem.publicToken || tripItem.tripId;
          const response = await fetch(
            `/v1/passenger/me/bookings/${encodeURIComponent(token)}/${encodeURIComponent(booking.id)}`,
            {
              method: "PUT",
              headers: portalHeaders(),
              body: JSON.stringify({
                passengerName: booking.passengerName,
                boardingStopId: fromSelect.value,
                dropoffStopId: toSelect.value,
                seats: Number(seatsInput.value || 1),
              }),
            },
          );
          const body = await response.json();
          if (!response.ok) throw new Error(body.message || "Não foi possível alterar.");
          message.textContent = "Reserva alterada.";
          await loadPassengerBookings();
        } catch (error) {
          message.textContent = error.message || "Falha ao alterar.";
        } finally {
          save.disabled = false;
        }
      });

      const cancel = document.createElement("button");
      cancel.type = "button";
      cancel.className = "dangerButton";
      cancel.textContent = "Cancelar reserva";
      cancel.addEventListener("click", async () => {
        if (!window.confirm("Cancelar esta reserva e liberar as vagas?")) return;
        cancel.disabled = true;
        message.textContent = "Cancelando…";
        try {
          const token = tripItem.publicToken || tripItem.tripId;
          const response = await fetch(
            `/v1/passenger/me/bookings/${encodeURIComponent(token)}/${encodeURIComponent(booking.id)}/cancel`,
            { method: "POST", headers: portalHeaders(), body: "{}" },
          );
          const body = await response.json();
          if (!response.ok) throw new Error(body.message || "Não foi possível cancelar.");
          await loadPassengerBookings();
        } catch (error) {
          message.textContent = error.message || "Falha ao cancelar.";
        } finally {
          cancel.disabled = false;
        }
      });

      const actions = document.createElement("div");
      actions.className = "actions";
      actions.append(fromSelect, toSelect, seatsInput, save, cancel, message);
      card.appendChild(actions);
    }
    container.appendChild(card);
  });
}

async function loadPassengerBookings() {
  if (!passengerSessionToken) return;
  show("portalLoginBox", false);
  show("portalAuthenticated", true);
  const container = $("portalBookings");
  container.innerHTML = '<p class="muted">Carregando reservas…</p>';
  try {
    const response = await fetch("/v1/passenger/me/bookings", { headers: portalHeaders() });
    const body = await response.json();
    if (response.status === 401) {
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

function logoutPassengerPortal() {
  savePassengerSession("");
  passengerViewAccountActivated = true;
  $("portalPassword").value = "";
  $("portalBookings").innerHTML = "";
  if (trip) renderTrip();
  else if (agendaToken) loadAgenda();
  else showAccessGate("agenda", "Área privada encerrada neste aparelho.");
}

function closePassengerPortal() {
  if (trip) {
    renderTrip();
  } else if (agendaToken) {
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
$("searchFromInput").addEventListener("input", (event) => { searchState.from = event.target.value; $("searchMessage").textContent = ""; });
$("searchToInput").addEventListener("input", (event) => { searchState.to = event.target.value; $("searchMessage").textContent = ""; });
$("searchFromInput").addEventListener("keydown", (event) => { if (event.key === "Enter") submitTripSearch(); });
$("searchToInput").addEventListener("keydown", (event) => { if (event.key === "Enter") submitTripSearch(); });
$("searchDeparture").addEventListener("click", () => openCalendarPicker("departure"));
$("searchReturn").addEventListener("click", () => openCalendarPicker("returnDate"));
$("searchSeats").addEventListener("click", openSeatPicker);
$("searchSubmit").addEventListener("click", submitTripSearch);
$("calendarBack").addEventListener("click", () => renderAgenda(agendaTripsCache));
$("calendarNoReturn").addEventListener("click", clearReturnDate);
$("seatBack").addEventListener("click", () => renderAgenda(agendaTripsCache));
$("seatMinus").addEventListener("click", () => changeSeatPicker(-1));
$("seatPlus").addEventListener("click", () => changeSeatPicker(1));
$("seatConfirm").addEventListener("click", confirmSeatPicker);
$("resultsBack").addEventListener("click", () => renderAgenda(agendaTripsCache));
$("openPassengerPortal").addEventListener("click", openPassengerPortal);
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
  if (!passengerSessionToken) return showPrivateAuthGate("booking");
  openBookingFlow();
});
$("reserve").addEventListener("click", reviewBooking);
$("confirmReserve").addEventListener("click", reserve);
$("editReservation").addEventListener("click", () => {
  showOnly("booking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("backToTrip").addEventListener("click", goBackToTrip);
$("backToAgenda").addEventListener("click", goBackToAgenda);
$("changeReservation").addEventListener("click", () => {
  if (!passengerSessionToken) return showPrivateAuthGate("review", "edit");
  beginExistingReservationEdit();
});
$("showCancel").addEventListener("click", () => {
  if (!passengerSessionToken) return showPrivateAuthGate("review", "showCancel");
  showOnly("cancelBooking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("cancelBack").addEventListener("click", () => showOnly("confirmed"));
$("cancelReservation").addEventListener("click", cancelReservation);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
$("downloadIcs").addEventListener("click", downloadIcs);
$("subscribeCalendar").addEventListener("click", shareCalendarFeed);

tracePublicAction("PUBLIC_LINK_OPENED");

async function bootstrapAuthenticatedExperience() {
  updateAuthenticatedChrome();
  if (!portalMode && !tripToken && !agendaToken && !referralCode) return setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
  if (referralCode && !tripToken && !agendaToken) return showAccessGate("agenda");
  await validatePassengerSession();
  if (passengerSessionContact) {
    const opened = await requestPublicAgendaAccess(passengerSessionContact);
    if (opened) return;
  }
  const accessMessage = $("accessMessage").textContent;
  showAccessGate(pendingAuthDestination, accessMessage);
}

bootstrapAuthenticatedExperience();
