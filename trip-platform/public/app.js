"use strict";

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const driverUsername = (params.get("motorista") || "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 32);

let driverDisplayName = "";
let driverProfile = {};
let trip = null;
let confirmedBooking = null;
let pendingBooking = null;
let editingExistingBooking = false;

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

const mainSections = ["agenda", "trip", "booking", "review", "confirmed", "cancelBooking"];

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

async function loadAgenda() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return setError("Link de agenda inválido.");
  let statusCode = 0;
  try {
    const response = await fetch(
      `/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`,
      { headers: { Accept: "application/json" } },
    );
    statusCode = response.status;
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    driverProfile = body.driver || {};
    driverDisplayName = driverProfile.displayName || driverUsername;
    tracePublicAction("PUBLIC_AGENDA_LOADED", { statusCode });
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    tracePublicAction("PUBLIC_AGENDA_LOAD_FAILED", { statusCode, reason: "client_load_error" });
    setError(error.message || "Não foi possível carregar a agenda.");
  }
}

function renderAgenda(trips) {
  showOnly("agenda");
  $("driverName").textContent = driverDisplayName ? `Viagens com ${driverDisplayName}` : "Próximas viagens";
  const container = $("agendaTrips");
  container.innerHTML = "";

  if (!trips.length) {
    const empty = document.createElement("div");
    empty.className = "card muted";
    empty.textContent = "Nenhuma próxima viagem publicada no momento.";
    container.appendChild(empty);
    return;
  }

  trips.forEach((item) => {
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
      card.href = `/?motorista=${encodeURIComponent(owner)}&trip=${encodeURIComponent(item.publicToken || item.tripId)}`;
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
    seats.textContent = full
      ? "🪑 0 vagas"
      : (range.minimum === range.maximum
        ? `🪑 ${range.maximum} vaga(s)`
        : `🪑 ${range.minimum}–${range.maximum} vagas`);

    meta.append(time, seats);

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
      { headers: { Accept: "application/json" } },
    );
    statusCode = response.status;
    const body = await response.json();
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

    row.append(time, dotCol, detail);
    container.appendChild(row);
  });
}

function renderDriverProfile() {
  $("driverCardName").textContent = driverDisplayName || "Motorista Rota Certa";

  const ratingParts = [];
  if (driverProfile.rating) ratingParts.push(`★ ${driverProfile.rating}`);
  if (Number(driverProfile.reviewCount || 0) > 0) ratingParts.push(`${driverProfile.reviewCount} avaliações`);
  if (ratingParts.length) {
    $("driverRatingLine").textContent = ratingParts.join(" • ");
    show("driverRatingLine", true);
  } else {
    show("driverRatingLine", false);
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
  refreshSelectors();
}

function openBookingFlow() {
  if (!trip || isFullTrip(trip) || trip.canReserve === false) return;
  editingExistingBooking = false;
  $("confirmReserve").textContent = "Fazer pedido de reserva";
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
  if (!$("boarding").value || !$("dropoff").value || seats < 1) {
    return void ($("bookingMessage").textContent = "Escolha um trecho com vagas.");
  }

  pendingBooking = {
    passengerName: name,
    passengerContact,
    boardingStopId: $("boarding").value,
    dropoffStopId: $("dropoff").value,
    seats,
  };

  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === pendingBooking.boardingStopId);
  const toIndex = stops.findIndex((s) => s.id === pendingBooking.dropoffStopId);
  const from = stops[fromIndex]?.name || "Embarque";
  const to = stops[toIndex]?.name || "Destino";
  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const totalFareCents = farePerSeatCents * seats;

  tracePublicAction("PUBLIC_RESERVATION_STARTED", { seats, fromIndex, toIndex });

  $("reviewRoute").textContent = `${from} → ${to}`;
  $("reviewDate").textContent = formatDate(trip.departureAtMillis);
  $("reviewStops").textContent = `${from} → ${to}`;
  $("reviewSeats").textContent = seats === 1 ? "1 lugar" : `${seats} lugares`;
  $("reviewPrice").textContent = totalFareCents > 0 ? formatMoney(totalFareCents) : "Valor não informado";
  $("reviewPayment").textContent = driverProfile.paymentInstructions || "Forma de pagamento não informada pelo motorista.";

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
      },
      body: JSON.stringify({ ...pendingBooking, idempotencyKey }),
    });
    statusCode = response.status;
    const body = await response.json();
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

    const confirmedFare = Number(body.totalFareCents || 0) > 0
      ? ` Valor total: ${formatMoney(body.totalFareCents)}.`
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

  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Atualizando sua reserva…";
  let statusCode = 0;

  try {
    const response = await fetch(
      `/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(confirmedBooking.bookingId)}`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          ...pendingBooking,
          cancellationToken: confirmedBooking.cancellationToken,
        }),
      },
    );

    statusCode = response.status;
    const body = await response.json();
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
      headers: { Accept: "application/json" },
    });
    if (!response.ok) return;
    const body = await response.json();
    trip = body;
    driverProfile = body.driver || driverProfile;
  } catch (_) {}
}

async function cancelReservation() {
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
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ cancellationToken }),
      },
    );
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
$("startBooking").addEventListener("click", openBookingFlow);
$("reserve").addEventListener("click", reviewBooking);
$("confirmReserve").addEventListener("click", reserve);
$("editReservation").addEventListener("click", () => {
  showOnly("booking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("backToTrip").addEventListener("click", goBackToTrip);
$("backToAgenda").addEventListener("click", goBackToAgenda);
$("changeReservation").addEventListener("click", beginExistingReservationEdit);
$("showCancel").addEventListener("click", () => {
  showOnly("cancelBooking");
  window.scrollTo({ top: 0, behavior: "smooth" });
});
$("cancelBack").addEventListener("click", () => showOnly("confirmed"));
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
