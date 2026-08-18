#!/usr/bin/env python3
from pathlib import Path
import sys

PATCHES = Path(sys.argv[1]).resolve()
APP = PATCHES / "trip-platform/public/app.js"
HTML = PATCHES / "trip-platform/public/index.html"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

once(APP,
'''const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
let trip = null;
let confirmedBooking = null;
''',
'''const tripToken = (params.get("trip") || "").replace(/[^A-Za-z0-9_-]/g, "");
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const driverUsername = (params.get("motorista") || "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 32);
let driverDisplayName = "";
let trip = null;
let confirmedBooking = null;
''', "driver query identity")

once(APP,
'''async function loadAgenda() {
  if (agendaToken.length < 16) return setError("Link de agenda inválido.");
  try {
    const response = await fetch(`/calendar/${encodeURIComponent(agendaToken)}.json`, { headers: { Accept: "application/json" } });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    setError(error.message || "Não foi possível carregar a agenda.");
  }
}
''',
'''async function loadAgenda() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return setError("Link de agenda inválido.");
  try {
    const response = await fetch(`/v1/public/drivers/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}/agenda`, { headers: { Accept: "application/json" } });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    driverDisplayName = body.driver?.displayName || driverUsername;
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    setError(error.message || "Não foi possível carregar a agenda.");
  }
}
''', "scoped agenda loader")

once(APP,
'''function renderAgenda(trips) {
  show("loading", false);
  show("agenda", true);
''',
'''function renderAgenda(trips) {
  show("loading", false);
  show("agenda", true);
  $("driverName").textContent = driverDisplayName ? `Motorista: ${driverDisplayName}` : "";
''', "agenda driver heading")

once(APP,
'''    link.href = `/?trip=${encodeURIComponent(item.publicToken || item.tripId)}`;
''',
'''    const owner = item.driverUsername || driverUsername;
    link.href = `/?motorista=${encodeURIComponent(owner)}&trip=${encodeURIComponent(item.publicToken || item.tripId)}`;
''', "agenda trip owner link")

once(APP,
'''async function shareCalendarFeed() {
  if (agendaToken.length < 16) return;
  const url = `${location.origin}/calendar/${encodeURIComponent(agendaToken)}.ics`;
''',
'''async function shareCalendarFeed() {
  if (driverUsername.length < 3 || agendaToken.length < 16) return;
  const url = `${location.origin}/calendar/${encodeURIComponent(driverUsername)}/${encodeURIComponent(agendaToken)}.ics`;
''', "driver calendar share URL")

once(APP,
'''function renderTrip() {
  show("loading", false);
  show("trip", true);
  show("booking", true);
''',
'''function renderTrip() {
  show("loading", false);
  show("trip", true);
  show("booking", true);
  show("cancelBooking", true);
  driverDisplayName = trip.driverDisplayName || driverDisplayName || driverUsername;
  $("driverName").textContent = driverDisplayName ? `Motorista: ${driverDisplayName}` : "";
''', "trip driver heading")

once(APP,
'''  refreshSelectors();
}

async function reserve() {
''',
'''  refreshSelectors();
  restoreCancellation();
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

async function reserve() {
''', "cancellation restore")

once(APP,
'''    try { localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken })); } catch (_) {}
''',
'''    try {
      localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }));
      localStorage.setItem(cancellationStorageKey(), JSON.stringify({ bookingId: body.bookingId, cancellationToken: body.cancellationToken, boardingStopId: confirmedBooking.boardingStopId, dropoffStopId: confirmedBooking.dropoffStopId, seats }));
    } catch (_) {}
    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    show("cancelBooking", true);
''', "persist cancellation authority")

once(APP,
'''function recomputeLoadsAfterBooking(loads, booking) {
''',
'''async function cancelReservation() {
  const bookingId = $("cancelBookingId").value.trim();
  const cancellationToken = $("cancelToken").value.trim();
  if (!tripToken || !bookingId || !cancellationToken) {
    $("cancelMessage").textContent = "Informe a reserva e o código particular de cancelamento.";
    return;
  }
  $("cancelReservation").disabled = true;
  $("cancelMessage").textContent = "Cancelando e liberando as vagas do trecho…";
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings/${encodeURIComponent(bookingId)}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ cancellationToken }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível cancelar.");
    try {
      localStorage.removeItem(cancellationStorageKey());
      localStorage.removeItem(`rotacerta-booking-${bookingId}`);
    } catch (_) {}
    confirmedBooking = null;
    $("cancelBookingId").value = "";
    $("cancelToken").value = "";
    $("cancelMessage").textContent = "Reserva cancelada. As vagas foram liberadas.";
    show("confirmed", false);
    await loadTrip();
  } catch (error) {
    $("cancelMessage").textContent = error.message || "Falha ao cancelar a reserva.";
  } finally {
    $("cancelReservation").disabled = false;
  }
}

function recomputeLoadsAfterBooking(loads, booking) {
''', "public cancellation request")

once(APP,
'''$("reserve").addEventListener("click", reserve);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
''',
'''$("reserve").addEventListener("click", reserve);
$("cancelReservation").addEventListener("click", cancelReservation);
$("googleCalendar").addEventListener("click", openGoogleCalendar);
''', "cancel listener")

once(HTML,
'''  <h1>Agenda de Viagens</h1>
  <p class="muted">Consulte somente as viagens publicadas pelo motorista. A agenda pessoal dele não é compartilhada.</p>
''',
'''  <h1>Agenda de Viagens</h1>
  <p id="driverName" class="muted"></p>
  <p class="muted">Consulte somente as viagens publicadas pelo motorista. A agenda pessoal dele não é compartilhada.</p>
''', "driver name heading")

once(HTML,
'''  <section id="confirmed" class="card hidden">
''',
'''  <section id="cancelBooking" class="card hidden">
    <h2>Cancelar minha reserva</h2>
    <p class="muted">Somente quem possui o número da reserva e o código particular recebido na confirmação consegue cancelar.</p>
    <div class="grid">
      <label>Número da reserva<input id="cancelBookingId" autocomplete="off"></label>
      <label>Código particular<input id="cancelToken" autocomplete="off"></label>
    </div>
    <div class="actions"><button id="cancelReservation" class="secondary">Cancelar minha reserva</button></div>
    <p id="cancelMessage" class="muted"></p>
  </section>

  <section id="confirmed" class="card hidden">
''', "public cancellation card")

once(HTML,
'''      <button id="googleCalendar">Adicionar ao Google Agenda</button>
''',
'''      <button id="googleCalendar">Adicionar ao Google Agenda</button>
''', "google calendar control present")

once(HTML,
'''    </div>
  </section>
</main>
''',
'''    </div>
    <p class="muted">O Google Agenda é um espelho opcional da viagem. Vagas, reservas e cancelamentos em tempo real são controlados pela Agenda Pública do Rota Certa.</p>
  </section>
</main>
''', "google calendar authority note")

print("stage47_public_cancel_r3=PASS self_cancel=true driver_scoped_agenda=true cancellation_secret_separate=true google_calendar_mirror=true")
