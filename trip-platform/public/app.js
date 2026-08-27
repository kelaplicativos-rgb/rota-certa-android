"use strict";

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const driverUsername = (params.get("motorista") || "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 32);
let driverDisplayName = "";
let trip = null;
let confirmedBooking = null;
let pendingBooking = null;

const publicDebugSessionId = (() => {
  try {
    const key = "rotacerta-public-debug-session";
    const current = sessionStorage.getItem(key);
    if (current && /^[A-Za-z0-9_-]{16,80}$/.test(current)) return current;
    const next = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}_${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
    sessionStorage.setItem(key, next);
    return next;
  } catch (_) {
    return `s_${Date.now()}_${Math.random().toString(36).slice(2)}`.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
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

function show(id, visible = true) { $(id).classList.toggle("hidden", !visible); }
function setError(message) { $("error").textContent = message; show("error", true); show("loading", false); }
function formatDate(ms) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "full", timeStyle: "short" }).format(new Date(ms)); }
function formatDay(ms) { return new Intl.DateTimeFormat("pt-BR", { weekday: "short", day: "2-digit", month: "short" }).format(new Date(ms)).toUpperCase().replace(/\./g, ""); }
function normalizeWhatsapp(value) {
  let digits = String(value || "").replace(/\D/g, "");
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) digits = digits.slice(2);
  return digits.length === 10 || digits.length === 11 ? `+55${digits}` : "";
}
function maskWhatsapp(value) {
  let digits = String(value || "").replace(/\D/g, "");
  if (digits.startsWith("55") && digits.length > 11) digits = digits.slice(2);
  digits = digits.slice(0, 11);
  if (digits.length <= 2) return digits;
  if (digits.length <= 6) return `(${digits.slice(0,2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) return `(${digits.slice(0,2)}) ${digits.slice(2,6)}-${digits.slice(6)}`;
  return `(${digits.slice(0,2)}) ${digits.slice(2,7)}-${digits.slice(7)}`;
}
function orderedStops() { return [...(trip?.stops || [])].sort((a, b) => a.order - b.order); }

function seatRange(item) {
  const loads = Array.isArray(item.segmentLoads) ? item.segmentLoads.map(Number) : [];
  if (!loads.length) return { minimum: item.capacity, maximum: item.capacity };
  const available = loads.map((load) => Math.max(0, Number(item.capacity) - load));
  return { minimum: Math.min(...available), maximum: Math.max(...available) };
}

function availableFor(fromIndex, toIndex) {
  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;
  let available = trip.capacity;
  for (let i = fromIndex; i < toIndex; i += 1) {
    available = Math.min(available, trip.capacity - Number((trip.segmentLoads || [])[i] || 0));
  }
  return Math.max(0, available);
}

function fareFor(fromIndex, toIndex) {
  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;
  const stops = orderedStops();
  return stops.slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
}

function formatMoney(cents) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Math.max(0, Number(cents || 0)) / 100);
}

async function loadAgenda() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return setError("Link de agenda inválido.");
  let statusCode = 0;
  try {
    const response = await fetch(`/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`, { headers: { Accept: "application/json" } });
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    driverDisplayName = body.driver?.displayName || driverUsername;
    tracePublicAction("PUBLIC_AGENDA_LOADED", { statusCode });
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    tracePublicAction("PUBLIC_AGENDA_LOAD_FAILED", { statusCode, reason: "client_load_error" });
    setError(error.message || "Não foi possível carregar a agenda.");
  }
}

function renderAgenda(trips) {
  show("loading", false);
  show("agenda", true);
  $("driverName").textContent = driverDisplayName ? `Motorista: ${driverDisplayName}` : "";
  const container = $("agendaTrips");
  container.innerHTML = "";
  if (!trips.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "Nenhuma próxima viagem publicada no momento.";
    container.appendChild(empty);
    return;
  }
  trips.forEach((item) => {
    const link = document.createElement("a");
    link.className = "agendaTrip";
    const owner = item.driverUsername || driverUsername;
    link.href = `/?motorista=${encodeURIComponent(owner)}&trip=${encodeURIComponent(item.publicToken || item.tripId)}`;
    const route = document.createElement("div");
    route.className = "agendaRoute";
    route.textContent = `${formatDay(item.departureAtMillis)} — ${item.title || (item.stops || []).map((stop) => stop.name).filter(Boolean).join(" → ")}`;
    const meta = document.createElement("div");
    meta.className = "agendaMeta";
    const range = seatRange(item);
    const seats = range.minimum === range.maximum
      ? `${range.maximum}/${item.capacity} vagas livres`
      : `vagas por trecho: ${range.minimum}–${range.maximum}/${item.capacity}`;
    meta.textContent = `${formatDate(item.departureAtMillis)} • ${seats}`;
    link.append(route, meta);
    link.addEventListener("click", () => tracePublicAction("PUBLIC_TRIP_SELECTED"));
    container.appendChild(link);
  });
}

async function shareCalendarFeed() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return;
  const url = `${location.origin}/calendar/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}.ics`;
  const payload = { title: "Rota Certa — Agenda de Viagens", text: "Calendário público somente com viagens publicadas.", url };
  try {
    if (navigator.share) {
      await navigator.share(payload);
      return;
    }
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      $("subscribeCalendar").textContent = "Link .ics copiado";
      return;
    }
  } catch (_) {
    // Fall through to opening the standard iCalendar feed.
  }
  location.href = url;
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

async function loadTrip() {
  if (tripToken.length < 16) return setError("Link de viagem inválido.");
  let statusCode = 0;
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}`, { headers: { Accept: "application/json" } });
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Viagem indisponível.");
    trip = body;
    tracePublicAction("PUBLIC_TRIP_LOADED", { statusCode });
    renderTrip();
  } catch (error) {
    tracePublicAction("PUBLIC_TRIP_LOAD_FAILED", { statusCode, reason: "client_load_error" });
    setError(error.message || "Não foi possível carregar a viagem.");
  }
}

function renderTrip() {
  show("loading", false);
  show("trip", true);
  show("booking", true);
  show("cancelBooking", true);
  driverDisplayName = trip.driverDisplayName || driverDisplayName || driverUsername;
  $("driverName").textContent = driverDisplayName ? `Motorista: ${driverDisplayName}` : "";
  $("status").textContent = trip.status === "FULL" ? "Lotação por trechos" : "Reservas abertas";
  $("title").textContent = trip.title;
  $("departure").textContent = `Saída prevista: ${formatDate(trip.departureAtMillis)}`;
  $("notes").textContent = trip.notes || "";
  const route = $("route");
  route.innerHTML = "";
  const stops = orderedStops();
  stops.forEach((stop) => {
    const div = document.createElement("div");
    div.className = "stop";
    const strong = document.createElement("strong");
    strong.textContent = stop.name;
    div.appendChild(strong);
    if (stop.address && stop.address !== stop.name) {
      const small = document.createElement("div");
      small.className = "muted";
      small.textContent = stop.address;
      div.appendChild(small);
    }
    route.appendChild(div);
  });
  const boarding = $("boarding");
  boarding.innerHTML = "";
  stops.slice(0, -1).forEach((stop) => {
    const option = document.createElement("option");
    option.value = stop.id;
    option.textContent = stop.name;
    boarding.appendChild(option);
  });
  refreshSelectors();
  restoreCancellation();
  restoreExistingBooking();
}

function cancellationStorageKey() { return `rotacerta-booking-trip-${tripToken}`; }

function restoreCancellation() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return;
  $("cancelBookingId").value = saved.bookingId;
  $("cancelToken").value = saved.cancellationToken;
  show("cancelBooking", true);
}

function restoreExistingBooking() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return;
  confirmedBooking = saved;
  $("confirmationText").textContent = "Sua reserva já está confirmada neste aparelho.";
  $("cancelCode").textContent = saved.cancellationToken;
  show("confirmed", true);
  show("booking", false);
  show("review", false);
}

function requestIdentity(payload) {
  const fingerprint = JSON.stringify(payload);
  const key = `rotacerta-booking-intent-${tripToken}`;
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(key) || "null"); } catch (_) {}
  if (saved?.fingerprint === fingerprint && saved?.idempotencyKey) return saved.idempotencyKey;
  const idempotencyKey = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9_-]/g, "_");
  try { localStorage.setItem(key, JSON.stringify({ fingerprint, idempotencyKey })); } catch (_) {}
  return idempotencyKey;
}

function reviewBooking() {
  if (!trip) return;
  const name = $("name").value.trim();
  const passengerContact = normalizeWhatsapp($("contact").value);
  const seats = Number($("seats").value || 0);
  if (!name) return void ($("bookingMessage").textContent = "Informe seu nome.");
  if (!passengerContact) return void ($("bookingMessage").textContent = "Informe seu WhatsApp com DDD.");
  if (!$("boarding").value || !$("dropoff").value || seats < 1) return void ($("bookingMessage").textContent = "Escolha um trecho com vagas.");
  pendingBooking = { passengerName: name, passengerContact, boardingStopId: $("boarding").value, dropoffStopId: $("dropoff").value, seats };
  const stops = orderedStops();
  const debugFromIndex = stops.findIndex((s) => s.id === pendingBooking.boardingStopId);
  const debugToIndex = stops.findIndex((s) => s.id === pendingBooking.dropoffStopId);
  tracePublicAction("PUBLIC_RESERVATION_STARTED", { seats, fromIndex: debugFromIndex, toIndex: debugToIndex });
  const fromIndex = stops.findIndex((s) => s.id === pendingBooking.boardingStopId);
  const toIndex = stops.findIndex((s) => s.id === pendingBooking.dropoffStopId);
  const from = stops[fromIndex]?.name || "Embarque";
  const to = stops[toIndex]?.name || "Destino";
  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const totalFareCents = farePerSeatCents * seats;
  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa • total ${formatMoney(totalFareCents)}` : "";
  $("reviewText").textContent = `${formatDate(trip.departureAtMillis)} • ${from} → ${to} • ${seats} lugar(es)${fareText} • ${name}`;
  show("review", true);
  $("review").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function reserve() {
  if (!trip || !pendingBooking) return;
  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Confirmando sua vaga…";
  const idempotencyKey = requestIdentity(pendingBooking);
  const debugStops = orderedStops();
  const debugFromIndex = debugStops.findIndex((s) => s.id === pendingBooking.boardingStopId);
  const debugToIndex = debugStops.findIndex((s) => s.id === pendingBooking.dropoffStopId);
  let statusCode = 0;
  tracePublicAction("PUBLIC_RESERVATION_REQUEST_SENT", {
    seats: pendingBooking.seats,
    fromIndex: debugFromIndex,
    toIndex: debugToIndex,
  });
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ ...pendingBooking, idempotencyKey }),
    });
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível reservar.");
    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      boardingStopId: pendingBooking.boardingStopId,
      dropoffStopId: pendingBooking.dropoffStopId,
        seats: pendingBooking.seats,
        farePerSeatCents: Number(body.farePerSeatCents || 0),
        totalFareCents: Number(body.totalFareCents || 0),
      };
    try {
      localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }));
      localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking));
    } catch (_) {}
    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    $("bookingMessage").textContent = "";
    $("reviewMessage").textContent = "";
      const confirmedFare = Number(body.totalFareCents || 0) > 0 ? ` Valor total: ${formatMoney(body.totalFareCents)}.` : "";
      $("confirmationText").textContent = body.replayed
        ? `✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada.${confirmedFare}`
        : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).${confirmedFare}`;
    $("cancelCode").textContent = body.cancellationToken;
    show("confirmed", true);
    show("booking", false);
    show("review", false);
    show("cancelBooking", true);
    trip.segmentLoads = recomputeLoadsAfterBooking(trip.segmentLoads || [], confirmedBooking);
    tracePublicAction("PUBLIC_RESERVATION_CREATED", {
      statusCode,
      seats: confirmedBooking.seats,
      fromIndex: debugFromIndex,
      toIndex: debugToIndex,
      replayed: body.replayed === true,
    });
    tracePublicAction("PUBLIC_SEATS_UPDATED", {
      statusCode,
      seats: confirmedBooking.seats,
      fromIndex: debugFromIndex,
      toIndex: debugToIndex,
    });
    pendingBooking = null;
  } catch (error) {
    tracePublicAction("PUBLIC_RESERVATION_FAILED", {
      statusCode,
      reason: statusCode ? `http_${statusCode}` : "network_or_client_error",
      seats: pendingBooking?.seats || 0,
      fromIndex: debugFromIndex,
      toIndex: debugToIndex,
    });
    $("reviewMessage").textContent = error.message || "Falha ao confirmar reserva.";
    await loadTrip();
  } finally {
    $("confirmReserve").disabled = false;
  }
}

async function cancelReservation() {
  const bookingId = $("cancelBookingId").value.trim();
  const cancellationToken = $("cancelToken").value.trim();
  if (!tripToken || !bookingId || !cancellationToken) {
    $("cancelMessage").textContent = "Informe a reserva e o código particular de cancelamento.";
    return;
  }
  $("cancelReservation").disabled = true;
  $("cancelMessage").textContent = "Cancelando e liberando as vagas do trecho…";
  let statusCode = 0;
  tracePublicAction("PUBLIC_RESERVATION_CANCEL_STARTED");
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ cancellationToken }),
    });
    statusCode = response.status;
    const body = await response.json();
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
    show("confirmed", false);
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
  for (let i = fromIndex; i < toIndex; i += 1) next[i] = Number(next[i] || 0) + booking.seats;
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
  return String(value || "").replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
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

$("boarding").addEventListener("change", () => { refreshSelectors(); traceSearchChanged(); });
$("dropoff").addEventListener("change", () => { refreshAvailability(); traceSearchChanged(); });
$("seats").addEventListener("input", refreshAvailability);
$("seats").addEventListener("change", () => { refreshAvailability(); traceSearchChanged(); });
$("contact").addEventListener("input", (event) => { event.target.value = maskWhatsapp(event.target.value); });
$("reserve").addEventListener("click", reviewBooking);
$("confirmReserve").addEventListener("click", reserve);
$("editReservation").addEventListener("click", () => show("review", false));
$("cancelReservation").addEventListener("click", cancelReservation);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
$("downloadIcs").addEventListener("click", downloadIcs);
$("subscribeCalendar").addEventListener("click", shareCalendarFeed);

tracePublicAction("PUBLIC_LINK_OPENED");

if (tripToken) {
  loadTrip();
} else if (agendaToken) {
  loadAgenda();
} else {
  setError("Este link não identifica uma agenda ou viagem do Rota Certa.");
}
