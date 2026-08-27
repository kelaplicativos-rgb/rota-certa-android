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

function show(id, visible = true) {
  const element = $(id);
  if (element) element.classList.toggle("hidden", !visible);
}

function setError(message) {
  $("error").textContent = message;
  show("error", true);
  show("loading", false);
}

function scrollToSection(id) {
  const element = $(id);
  if (!element) return;
  element.scrollIntoView({ behavior: "smooth", block: "start" });
}

function stepTo(id) {
  ["stepBoarding", "stepDropoff", "stepSeats", "stepDetails"].forEach((step) => show(step, step === id));
  requestAnimationFrame(() => scrollToSection("booking"));
}

function formatDate(ms) {
  return new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(ms));
}

function formatDay(ms) {
  return new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "short" })
    .format(new Date(ms))
    .replace(/\./g, "");
}

function formatTime(ms) {
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(new Date(ms));
}

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
  if (digits.length <= 6) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}

function orderedStops() {
  return [...(trip?.stops || [])].sort((a, b) => a.order - b.order);
}

function seatRange(item) {
  const loads = Array.isArray(item.segmentLoads) ? item.segmentLoads.map(Number) : [];
  if (!loads.length) return { minimum: Number(item.capacity || 0), maximum: Number(item.capacity || 0) };
  const available = loads.map((load) => Math.max(0, Number(item.capacity || 0) - load));
  return { minimum: Math.min(...available), maximum: Math.max(...available) };
}

function fullTripFare(item) {
  const stops = Array.isArray(item?.stops) ? [...item.stops].sort((a, b) => a.order - b.order) : [];
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
  return stops.slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
}

function formatMoney(cents) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Math.max(0, Number(cents || 0)) / 100);
}

function choiceButton(label, selected, onClick, ariaLabel = label) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `choice${selected ? " selected" : ""}`;
  button.textContent = label;
  button.setAttribute("aria-label", ariaLabel);
  button.setAttribute("aria-pressed", selected ? "true" : "false");
  button.addEventListener("click", onClick);
  return button;
}

function summaryRows(container, rows) {
  container.innerHTML = "";
  rows.filter((row) => row && row.value !== null && row.value !== undefined && row.value !== "").forEach((row) => {
    const div = document.createElement("div");
    div.className = "summaryRow";
    const label = document.createElement("span");
    label.textContent = row.label;
    const value = document.createElement("strong");
    value.textContent = String(row.value);
    div.append(label, value);
    container.appendChild(div);
  });
}

async function protectedPublicFetch(url, options) {
  const security = window.RotaCertaFirebaseSecurity;
  if (!security || !security.fetchProtected) {
    throw new Error("Não conseguimos validar este navegador. Atualize a página e tente novamente.");
  }
  try {
    return await security.fetchProtected(url, options);
  } catch (_) {
    throw new Error("Não conseguimos validar este navegador. Atualize a página e tente novamente.");
  }
}

async function loadAgenda() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return setError("Este link de viagens não está válido.");
  try {
    const response = await fetch(`/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`, {
      headers: { Accept: "application/json" },
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não conseguimos abrir as viagens agora.");
    driverDisplayName = body.driver?.displayName || driverUsername;
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    setError(error.message || "Não conseguimos abrir as viagens agora. Tente novamente.");
  }
}

function renderAgenda(trips) {
  show("loading", false);
  show("error", false);
  show("agenda", true);
  $("pageTitle").textContent = "Reserve sua viagem";
  $("pageHint").textContent = trips.length ? "Escolha uma viagem abaixo." : "Veja novamente em breve.";
  $("driverName").textContent = driverDisplayName ? `Com ${driverDisplayName}` : "";

  const container = $("agendaTrips");
  container.innerHTML = "";

  if (!trips.length) {
    const empty = document.createElement("div");
    empty.className = "card emptyState";
    const icon = document.createElement("div");
    icon.className = "emptyIcon";
    icon.textContent = "🚗";
    const title = document.createElement("h2");
    title.textContent = "Nenhuma viagem disponível agora";
    const text = document.createElement("p");
    text.className = "muted";
    text.textContent = "Volte novamente em breve para ver as próximas viagens.";
    empty.append(icon, title, text);
    container.appendChild(empty);
    return;
  }

  trips.forEach((item) => {
    const link = document.createElement("a");
    link.className = "tripCard";
    const owner = item.driverUsername || driverUsername;
    const token = item.publicToken || item.tripId;
    const next = new URLSearchParams({ motorista: owner, trip: token });
    if (agendaToken) next.set("agenda", agendaToken);
    link.href = `/?${next.toString()}`;
    link.setAttribute("aria-label", `Reservar ${item.title || "viagem"} em ${formatDay(item.departureAtMillis)}`);

    const date = document.createElement("div");
    date.className = "dateBadge";
    date.textContent = formatDay(item.departureAtMillis);

    const stops = Array.isArray(item.stops) ? [...item.stops].sort((a, b) => a.order - b.order) : [];
    const route = document.createElement("div");
    route.className = "routeBig";
    const origin = document.createElement("span");
    origin.textContent = stops[0]?.name || item.title || "Origem";
    const arrow = document.createElement("span");
    arrow.className = "arrow";
    arrow.textContent = "↓";
    const destination = document.createElement("span");
    destination.textContent = stops[stops.length - 1]?.name || "Destino";
    route.append(origin, arrow, destination);

    const meta = document.createElement("div");
    meta.className = "tripMeta";
    const time = document.createElement("span");
    time.className = "pill";
    time.textContent = `🕐 ${formatTime(item.departureAtMillis)}`;
    meta.appendChild(time);

    const range = seatRange(item);
    const seats = document.createElement("span");
    seats.className = "pill";
    seats.textContent = range.minimum > 0
      ? `💺 ${range.minimum} vaga${range.minimum === 1 ? "" : "s"}`
      : "💺 Consulte as vagas";
    meta.appendChild(seats);

    const fare = fullTripFare(item);
    if (fare > 0) {
      const price = document.createElement("span");
      price.className = "pill";
      price.textContent = `💰 ${formatMoney(fare)}`;
      meta.appendChild(price);
    }

    const action = document.createElement("span");
    action.className = "primary";
    action.textContent = "RESERVAR";

    link.append(date, route, meta, action);
    container.appendChild(link);
  });
}

async function shareCalendarFeed() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return;
  const url = `${location.origin}/calendar/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}.ics`;
  const payload = { title: "Rota Certa — viagens", text: "Adicionar viagens ao meu calendário.", url };
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
  } catch (_) {
    // If sharing is cancelled, leave the page unchanged.
    return;
  }
  location.href = url;
}

async function loadTrip() {
  if (tripToken.length < 16) return setError("Este link de viagem não está válido.");
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}`, { headers: { Accept: "application/json" } });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Esta viagem não está disponível.");
    trip = body;
    renderTrip();
  } catch (error) {
    setError(error.message || "Não conseguimos abrir esta viagem. Tente novamente.");
  }
}

function renderTrip() {
  show("loading", false);
  show("error", false);
  show("trip", true);
  show("booking", true);
  driverDisplayName = trip.driverDisplayName || driverDisplayName || driverUsername;
  $("pageTitle").textContent = "Reserve sua viagem";
  $("pageHint").textContent = "Só falta escolher os detalhes.";
  $("driverName").textContent = driverDisplayName ? `Com ${driverDisplayName}` : "";
  $("status").textContent = trip.status === "FULL" ? "Vagas por embarque" : "Vagas abertas";
  $("title").textContent = trip.title || "Sua viagem";
  $("departure").textContent = `${formatDay(trip.departureAtMillis)} • ${formatTime(trip.departureAtMillis)}`;
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

  if (agendaToken && driverUsername) show("backAgenda", true);
  renderBoardingChoices();
  restoreCancellation();
  restoreExistingBooking();
}

function validBoardingStops() {
  const stops = orderedStops();
  return stops.slice(0, -1).filter((stop, fromIndex) =>
    stops.some((_, toIndex) => toIndex > fromIndex && availableFor(fromIndex, toIndex) > 0)
  );
}

function renderBoardingChoices() {
  const choices = $("boardingChoices");
  choices.innerHTML = "";
  const valid = validBoardingStops();
  valid.forEach((stop) => {
    choices.appendChild(choiceButton(stop.name, $("boarding").value === stop.id, () => selectBoarding(stop.id)));
  });
  if (valid.length === 1) {
    selectBoarding(valid[0].id, true);
  } else {
    stepTo("stepBoarding");
  }
}

function selectBoarding(stopId, automatic = false) {
  $("boarding").value = stopId;
  renderDropoffChoices(automatic);
}

function validDropoffStops() {
  const stops = orderedStops();
  const fromIndex = stops.findIndex((stop) => stop.id === $("boarding").value);
  return stops.filter((stop, toIndex) => toIndex > fromIndex && availableFor(fromIndex, toIndex) > 0);
}

function renderDropoffChoices(automatic = false) {
  const dropoff = $("dropoff");
  dropoff.innerHTML = "";
  const choices = $("dropoffChoices");
  choices.innerHTML = "";
  const valid = validDropoffStops();

  valid.forEach((stop) => {
    const option = document.createElement("option");
    option.value = stop.id;
    option.textContent = stop.name;
    dropoff.appendChild(option);
    choices.appendChild(choiceButton(stop.name, dropoff.value === stop.id, () => selectDropoff(stop.id)));
  });

  if (valid.length === 1) {
    selectDropoff(valid[0].id, true);
  } else if (valid.length > 1) {
    stepTo("stepDropoff");
  } else if (!automatic) {
    $("bookingMessage").textContent = "Não há vagas a partir deste embarque. Escolha outro local.";
    stepTo("stepBoarding");
  }
}

function selectDropoff(stopId) {
  $("dropoff").value = stopId;
  refreshAvailability();
  renderSeatChoices();
  stepTo("stepSeats");
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
  const invalidSelection = available < 1 || requested > available;
  seatsInput.setAttribute("aria-invalid", invalidSelection ? "true" : "false");
  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa` : "";
  $("availability").textContent = available > 0
    ? `${available} vaga${available === 1 ? "" : "s"} disponível${available === 1 ? "" : "is"}${fareText}`
    : "Essa opção acabou de ficar sem vagas.";
  return { available, farePerSeatCents };
}

function renderSeatChoices() {
  const result = refreshAvailability();
  const available = result?.available || 0;
  const maxChoices = available;
  const container = $("seatChoices");
  container.innerHTML = "";
  for (let count = 1; count <= maxChoices; count += 1) {
    container.appendChild(choiceButton(String(count), Number($("seats").value) === count, () => selectSeats(count), `${count} pessoa${count === 1 ? "" : "s"}`));
  }
}

function selectSeats(count) {
  $("seats").value = String(count);
  renderSeatChoices();
  stepTo("stepDetails");
  setTimeout(() => $("name").focus({ preventScroll: true }), 200);
}

function cancellationStorageKey() {
  return `rotacerta-booking-trip-${tripToken}`;
}

function restoreCancellation() {
  let saved = null;
  try {
    saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null");
  } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return;
  $("cancelBookingId").value = saved.bookingId;
  $("cancelToken").value = saved.cancellationToken;
  show("cancelBooking", true);
}

function restoreExistingBooking() {
  let saved = null;
  try {
    saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null");
  } catch (_) {}
  if (!saved?.bookingId || !saved?.cancellationToken) return;
  confirmedBooking = saved;
  $("confirmationText").textContent = "Pronto! Sua vaga já está reservada neste aparelho.";
  $("cancelCode").textContent = saved.cancellationToken;
  renderConfirmedSummary(saved);
  show("confirmed", true);
  show("booking", false);
  show("review", false);
  show("cancelBooking", true);
  if (agendaToken && driverUsername) show("newReservation", true);
}

function requestIdentity(payload) {
  const fingerprint = JSON.stringify(payload);
  const key = `rotacerta-booking-intent-${tripToken}`;
  let saved = null;
  try {
    saved = JSON.parse(localStorage.getItem(key) || "null");
  } catch (_) {}
  if (saved?.fingerprint === fingerprint && saved?.idempotencyKey) return saved.idempotencyKey;
  const idempotencyKey = (crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`
  ).replace(/[^A-Za-z0-9_-]/g, "_");
  try {
    localStorage.setItem(key, JSON.stringify({ fingerprint, idempotencyKey }));
  } catch (_) {}
  return idempotencyKey;
}

function reviewBooking() {
  if (!trip) return;
  const name = $("name").value.trim();
  const passengerContact = normalizeWhatsapp($("contact").value);
  const seats = Number($("seats").value || 0);
  $("bookingMessage").textContent = "";

  if (!name) {
    $("bookingMessage").textContent = "Digite seu nome para continuar.";
    $("name").focus();
    return;
  }
  if (!passengerContact) {
    $("bookingMessage").textContent = "Confira seu WhatsApp. Digite o DDD e o número.";
    $("contact").focus();
    return;
  }
  if (!$("boarding").value || !$("dropoff").value || seats < 1) {
    $("bookingMessage").textContent = "Escolha sua viagem novamente.";
    stepTo("stepBoarding");
    return;
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

  summaryRows($("reviewText"), [
    { label: "Data", value: formatDay(trip.departureAtMillis) },
    { label: "Horário", value: formatTime(trip.departureAtMillis) },
    { label: "Embarque", value: from },
    { label: "Destino", value: to },
    { label: "Nome", value: name },
    { label: "Pessoas", value: seats },
    farePerSeatCents > 0 ? { label: "Por pessoa", value: formatMoney(farePerSeatCents) } : null,
    totalFareCents > 0 ? { label: "Total", value: formatMoney(totalFareCents) } : null,
  ]);

  show("review", true);
  show("booking", false);
  scrollToSection("review");
}

async function reserve() {
  if (!trip || !pendingBooking) return;
  $("confirmReserve").disabled = true;
  $("confirmReserve").textContent = "CONFIRMANDO…";
  $("reviewMessage").textContent = "Só um momento. Estamos confirmando sua reserva.";
  const idempotencyKey = requestIdentity(pendingBooking);

  try {
    const response = await protectedPublicFetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ ...pendingBooking, idempotencyKey }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não conseguimos confirmar agora.");

    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      boardingStopId: pendingBooking.boardingStopId,
      dropoffStopId: pendingBooking.dropoffStopId,
      seats: pendingBooking.seats,
      passengerName: pendingBooking.passengerName,
      farePerSeatCents: Number(body.farePerSeatCents || 0),
      totalFareCents: Number(body.totalFareCents || 0),
    };

    try {
      localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }));
      localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking));
    } catch (_) {}

    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    $("reviewMessage").textContent = "";
    $("confirmationText").textContent = body.replayed
      ? "Pronto! Esta reserva já estava confirmada. Nenhuma reserva duplicada foi criada."
      : "Pronto! Sua vaga foi reservada.";
    $("cancelCode").textContent = body.cancellationToken;
    renderConfirmedSummary(confirmedBooking);

    show("confirmed", true);
    show("booking", false);
    show("review", false);
    show("cancelBooking", true);
    if (agendaToken && driverUsername) show("newReservation", true);

    trip.segmentLoads = recomputeLoadsAfterBooking(trip.segmentLoads || [], confirmedBooking);
    pendingBooking = null;
    scrollToSection("confirmed");
  } catch (error) {
    $("reviewMessage").textContent = error.message || "Não conseguimos confirmar agora. Confira sua internet e tente novamente.";
  } finally {
    $("confirmReserve").disabled = false;
    $("confirmReserve").textContent = "CONFIRMAR RESERVA";
  }
}

function renderConfirmedSummary(booking) {
  if (!trip || !booking) return;
  const stops = orderedStops();
  const from = stops.find((stop) => stop.id === booking.boardingStopId);
  const to = stops.find((stop) => stop.id === booking.dropoffStopId);
  summaryRows($("confirmedSummary"), [
    { label: "Data", value: formatDay(trip.departureAtMillis) },
    { label: "Horário", value: formatTime(trip.departureAtMillis) },
    { label: "Embarque", value: from?.name || "Embarque" },
    { label: "Destino", value: to?.name || "Destino" },
    { label: "Pessoas", value: booking.seats || 1 },
    Number(booking.totalFareCents || 0) > 0 ? { label: "Total", value: formatMoney(booking.totalFareCents) } : null,
  ]);
}

async function cancelReservation() {
  const bookingId = $("cancelBookingId").value.trim();
  const cancellationToken = $("cancelToken").value.trim();
  if (!tripToken || !bookingId || !cancellationToken) {
    $("cancelMessage").textContent = "Confira o número da reserva e o código de cancelamento.";
    return;
  }

  $("cancelReservation").disabled = true;
  $("cancelMessage").textContent = "Cancelando sua reserva…";
  try {
    const response = await protectedPublicFetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ cancellationToken }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não conseguimos cancelar agora.");

    try {
      localStorage.removeItem(cancellationStorageKey());
      localStorage.removeItem(`rotacerta-booking-${bookingId}`);
      localStorage.removeItem(`rotacerta-booking-intent-${tripToken}`);
    } catch (_) {}

    confirmedBooking = null;
    $("cancelBookingId").value = "";
    $("cancelToken").value = "";
    $("cancelMessage").textContent = "Reserva cancelada. As vagas foram liberadas.";
    show("confirmed", false);
    await loadTrip();
  } catch (error) {
    $("cancelMessage").textContent = error.message || "Não conseguimos cancelar agora. Tente novamente.";
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
    text: `Viagem — ${from?.name || "Embarque"} → ${to?.name || "Destino"}`,
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
    `SUMMARY:${escapeIcs(`Viagem — ${from?.name || "Embarque"} → ${to?.name || "Destino"}`)}`,
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

function goToAgenda() {
  if (!agendaToken || !driverUsername) return;
  const query = new URLSearchParams({ motorista: driverUsername, agenda: agendaToken });
  location.href = `/?${query.toString()}`;
}

$("contact").addEventListener("input", (event) => {
  event.target.value = maskWhatsapp(event.target.value);
});
$("reserve").addEventListener("click", reviewBooking);
$("confirmReserve").addEventListener("click", reserve);
$("editReservation").addEventListener("click", () => {
  show("review", false);
  show("booking", true);
  stepTo("stepBoarding");
});
$("cancelReservation").addEventListener("click", cancelReservation);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
$("downloadIcs").addEventListener("click", downloadIcs);
$("subscribeCalendar").addEventListener("click", shareCalendarFeed);
$("backAgenda").addEventListener("click", goToAgenda);
$("newReservation").addEventListener("click", goToAgenda);

if (tripToken) {
  loadTrip();
} else if (agendaToken) {
  loadAgenda();
} else {
  setError("Este link não mostra nenhuma viagem. Peça um novo link ao motorista.");
}
