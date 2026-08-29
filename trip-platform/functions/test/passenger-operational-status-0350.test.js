"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const domain = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripDomain.kt"), "utf8");
const timeline = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerTimelineUi.kt"), "utf8");
const completion = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerCompletionService.kt"), "utf8");
const identityStore = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerIdentityStore.kt"), "utf8");
const exactCancel = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaBlockedPassengerCancellation.kt"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, "missing start " + start);
  assert.ok(to > from, "missing end " + end);
  return source.slice(from, to);
}

test("BookingStatus remains reservation/capacity only and operational/payment state is separate", () => {
  const bookingStatus = block(domain, "enum class BookingStatus", "enum class PassengerOperationalStatus");
  assert.match(bookingStatus, /REQUESTED/);
  assert.match(bookingStatus, /HELD/);
  assert.match(bookingStatus, /CONFIRMED/);
  assert.match(bookingStatus, /CANCELLED/);
  assert.match(bookingStatus, /EXPIRED/);
  assert.doesNotMatch(bookingStatus, /AT_LOCATION|IN_CAR|PAID|COMPLETED/);
  assert.match(domain, /enum class PassengerOperationalStatus/);
  assert.match(domain, /AT_LOCATION/);
  assert.match(domain, /IN_CAR/);
  assert.match(domain, /COMPLETED/);
  assert.match(domain, /enum class PassengerPaymentStatus/);
  assert.match(domain, /PAID/);
});

test("new Rota Certa booking starts operationally pending without changing confirmed capacity claim", () => {
  assert.match(api, /status: "CONFIRMED",[\s\S]{0,200}operationalStatus: "PENDING"/);
  assert.match(api, /paymentStatus: "UNPAID"/);
});

test("driver operational endpoint writes the same booking and emits persistent passenger notification", () => {
  const op = block(api, "async function mutateDriverPassengerOperationalStatus", "async function mutateProtectedBooking");
  assert.match(op, /bookingRef/);
  assert.match(op, /TIMELINE_PASSENGER_STATUS/);
  assert.match(op, /PASSENGER_STATUS_CONFIRMED/);
  assert.match(op, /PASSENGER_AT_LOCATION/);
  assert.match(op, /PASSENGER_IN_CAR/);
  assert.match(op, /PASSENGER_PAYMENT_CONFIRMED/);
  assert.match(op, /PASSENGER_COMPLETED/);
  assert.match(op, /writeChangeEventAndNotifications/);
  assert.match(op, /selection === "PAID" \? beforeOperational/);
});

test("passenger cannot cancel normally after boarding or completion", () => {
  const cancel = block(api, "async function cancelPassengerBooking", "async function upsertDriverCapacityBooking");
  assert.match(cancel, /operationalStatus === "IN_CAR"/);
  assert.match(cancel, /operationalStatus === "COMPLETED"/);
  assert.match(cancel, /passenger_cancel_locked_after_boarding/);
  assert.match(web, /!\["IN_CAR", "COMPLETED", "CANCELLED"\]\.includes\(operational\)/);
  assert.match(web, /A viagem já foi iniciada\. Fale com o motorista/);
});

test("Timeline exposes one status menu and old standalone completion shortcut is gone", () => {
  assert.match(timeline, /Text\("Confirmado"\)/);
  assert.match(timeline, /Text\("No local"\)/);
  assert.match(timeline, /Text\("No carro"\)/);
  assert.match(timeline, /Text\("Pago"\)/);
  assert.match(timeline, /Text\("Concluído"\)/);
  assert.match(timeline, /Text\("Cancelar"\)/);
  assert.match(timeline, /completionService\.confirm\(entry, passenger\)/);
  assert.doesNotMatch(timeline, /Text\(if \(completed\) "✅" else "☑️"\)/);
  assert.match(completion, /Single authority for the per-passenger/);
});

test("Minhas Viagens renders active/history states, payment in parallel and near-realtime refresh", () => {
  assert.match(web, /AGUARDANDO CONFIRMAÇÃO/);
  assert.match(web, /RESERVA CONFIRMADA/);
  assert.match(web, /MOTORISTA NO LOCAL/);
  assert.match(web, /VOCÊ ESTÁ EMBARCADO/);
  assert.match(web, /VIAGEM CONCLUÍDA/);
  assert.match(web, /PAGAMENTO CONFIRMADO/);
  assert.match(web, /Viagens atuais/);
  assert.match(web, /Histórico/);
  assert.match(web, /2_500/);
  assert.match(web, /window\.addEventListener\("online"/);
  assert.match(web, /VIAGEM ATUALIZADA/);
});

test("pure BlaBlaCar occurrence uses exact external metadata and never creates a capacity Booking", () => {
  assert.match(identityStore, /data class ExternalPassengerMetadata/);
  assert.match(identityStore, /val operationalStatus: PassengerOperationalStatus/);
  assert.match(identityStore, /val paymentStatus: PassengerPaymentStatus/);
  assert.match(timeline, /authority=EXTERNAL_RESERVATION_METADATA/);
  assert.match(timeline, /passengerStore\.saveExternalMetadata/);
  assert.match(timeline, /BookingSource\.BLABLACAR in passenger\.sources/);
  assert.doesNotMatch(timeline, /CapacityClaimType\.OPERATIONAL_ONLY/);
});

test("completed occurrence cannot regress or be cancelled retroactively", () => {
  const op = block(api, "async function mutateDriverPassengerOperationalStatus", "async function mutateProtectedBooking");
  const admin = block(api, "async function mutateProtectedBooking", "async function updatePassengerBooking");
  assert.match(op, /passenger_operational_completed/);
  assert.match(admin, /completed_booking_not_cancelable/);
  assert.match(timeline, /A conclusão é permanente/);
  assert.match(exactCancel, /reason=occurrence_completed/);
});

test("exact BlaBlaCar cancellation fails closed and verifies absence before local cancellation", () => {
  assert.match(exactCancel, /BlaBlaExactPassengerCancellationCoordinator/);
  assert.match(exactCancel, /identity_not_exact/);
  assert.match(exactCancel, /profileUuid == null \|\| tripId == null \|\| externalId == null \|\| bookingHref == null \|\| account == null/);
  assert.match(exactCancel, /verificationMisses >= 2/);
  assert.match(exactCancel, /PASSENGER_EXTERNAL_CANCEL_VERIFIED/);
  assert.match(exactCancel, /status = BookingStatus\.CANCELLED/);
});

test("operational implementation does not touch FAROL code path", () => {
  for (const source of [api, web, timeline, identityStore, exactCancel]) {
    assert.doesNotMatch(source, /LiveRideAccessibilityService/);
  }
});
