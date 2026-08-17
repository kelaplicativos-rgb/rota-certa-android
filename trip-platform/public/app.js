"use strict";

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const token = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
let trip = null;
let confirmedBooking = null;

function show(id, visible = true) { $(id).classList.toggle("hidden", !visible); }
function setError(message) { $("error").textContent = message; show("error", true); show("loading", false); }
function formatDate(ms) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "full", timeStyle: "short" }).format(new Date(ms)); }
function orderedStops() { return [...(trip?.stops || [])].sort((a, b) => a.order - b.order); }

function availableFor(fromIndex, toIndex) {
  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;
  let available = trip.capacity;
  for (let i = fromIndex; i < toIndex; i += 1) {
    available = Math.min(available, trip.capacity - Number((trip.segmentLoads || [])[i] || 0));
  }
  return Math.max(0, available);
}

function refreshSelectors() {
  const stops = orderedStops();
  const boarding = $("boarding");
  const dropoff = $("dropoff");
  const fromIndex = Math.max(0, boarding.selectedIndex);
  dropoff.innerHTML = "";
  stops.forEach((stop, index) => {
    if (index <= fromIndex) return;
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
  $("availability").textContent = `${available} lugar(es) disponível(is) neste trecho`;
  $("reserve").disabled = available < Number($("seats").value || 1);
}

async function loadTrip() {
  if (token.length < 16) return setError("Link de viagem inválido.");
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(token)}`, { headers: { Accept: "application/json" } });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Viagem indisponível.");
    trip = body;
    renderTrip();
  } catch (error) {
    setError(error.message || "Não foi possível carregar a viagem.");
  }
}

function renderTrip() {
  show("loading", false);
  show("trip", true);
  show("booking", true);
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
}

async function reserve() {
  if (!trip) return;
  const name = $("name").value.trim();
  const contact = $("contact").value.trim();
  const seats = Number($("seats").value || 1);
  if (!name) {
    $("bookingMessage").textContent = "Informe seu nome.";
    return;
  }
  $("reserve").disabled = true;
  $("bookingMessage").textContent = "Confirmando sem ultrapassar a capacidade do trecho…";
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(token)}/bookings`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        passengerName: name,
        passengerContact: contact,
        boardingStopId: $("boarding").value,
        dropoffStopId: $("dropoff").value,
        seats,
      }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível reservar.");
    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      boardingStopId: $("boarding").value,
      dropoffStopId: $("dropoff").value,
      seats,
    };
    try { localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: token, cancellationToken: body.cancellationToken })); } catch (_) {}
    $("bookingMessage").textContent = "";
    $("confirmationText").textContent = `Reserva ${body.bookingId} confirmada para ${seats} lugar(es).`;
    $("cancelCode").textContent = body.cancellationToken;
    show("confirmed", true);
    show("booking", false);
    trip.segmentLoads = recomputeLoadsAfterBooking(trip.segmentLoads || [], confirmedBooking);
  } catch (error) {
    $("bookingMessage").textContent = error.message || "Falha ao confirmar reserva.";
    refreshAvailability();
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
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

$("boarding").addEventListener("change", refreshSelectors);
$("dropoff").addEventListener("change", refreshAvailability);
$("seats").addEventListener("change", refreshAvailability);
$("reserve").addEventListener("click", reserve);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
$("downloadIcs").addEventListener("click", downloadIcs);
loadTrip();
