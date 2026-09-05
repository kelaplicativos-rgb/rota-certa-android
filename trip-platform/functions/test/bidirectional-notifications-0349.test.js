"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");
const androidApi = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"), "utf8");
const androidUi = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripsActivity.kt"), "utf8");
const messaging = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "RotaCertaBookingMessagingService.kt"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, "missing block start: " + start);
  assert.ok(to > from, "missing block end: " + end);
  return source.slice(from, to);
}

test("central audit events and persistent notifications share the real mutation transaction", () => {
  assert.match(api, /tripChangeEvents/);
  assert.match(api, /tripNotifications/);
  assert.match(api, /writeChangeEventAndNotifications\(tx/);
  assert.match(api, /changeVersion/);
  assert.match(api, /recipientKey/);
  assert.match(api, /affectedPassengerIds/);
});

test("authenticated My Trips edit and cancel both notify the driver through existing FCM", () => {
  const edit = block(api, "async function updatePassengerBooking", "async function cancelPassengerBooking");
  const cancel = block(api, "async function cancelPassengerBooking", "async function upsertDriverCapacityBooking");
  assert.match(edit, /PASSENGER_MY_TRIPS_EDIT/);
  assert.match(edit, /sendDriverBookingPush/);
  assert.match(edit, /event: "reservation_changed"/);
  assert.match(cancel, /PASSENGER_MY_TRIPS_CANCEL/);
  assert.match(cancel, /sendDriverBookingPush/);
  assert.match(cancel, /event: "reservation_cancelled"/);
  assert.match(edit, /assertNoOverbooking/);
  assert.match(cancel, /reconciledSegmentCapacity/);
});

test("driver protected booking mutation uses driver auth and not an undefined passenger session", () => {
  const admin = block(api, "async function mutateProtectedBooking", "async function updatePassengerBooking");
  assert.match(admin, /const driver = driverOverride0468 \|\| await requireDriver\(req, res\)/);
  assert.match(admin, /const adminActor0468 = driverOverride0468 && driverOverride0468\.adminActor0468 === true/);
  assert.match(admin, /trip_owner_mismatch/);
  assert.doesNotMatch(admin, /requirePassengerDriverAccess/);
  assert.doesNotMatch(admin, /\bsession\b/);
  assert.match(admin, /BOOKING_CANCELLED_BY_DRIVER/);
  assert.match(admin, /BOOKING_CHANGED_BY_DRIVER/);
  assert.match(admin, /passengerRecipients/);
});

test("driver trip changes notify only affected active passenger identities", () => {
  const update = block(api, "async function updateDriverTrip", "async function getPublicTrip");
  assert.match(update, /tripRelevantChanges/);
  assert.match(update, /TRIP_TIME_CHANGED/);
  assert.match(update, /TRIP_CANCELLED/);
  assert.match(update, /passengerRecipients/);
  assert.match(update, /passengerId/);
  assert.match(update, /CANCELLED/);
  assert.match(update, /EXPIRED/);
});

test("unread count is derived from read state and ownership is canonical", () => {
  assert.match(api, /notifications\.filter\(\(item\) => !item\.read\)\.length/);
  assert.match(api, /passenger-id:/);
  assert.match(api, /driver:/);
  assert.match(api, /\/v1\/passenger\/me\/notifications/);
  assert.match(api, /\/v1\/driver\/notifications/);
  assert.match(api, /read-all/);
});

test("passenger Minhas Viagens exposes bell badge persistent list and polling", () => {
  assert.match(html, /id="passengerNotificationsBell"/);
  assert.match(html, /id="passengerNotificationBadge"/);
  assert.match(html, />Minhas Viagens</);
  assert.match(html, /id="portalNotifications"/);
  assert.match(html, /id="portalMarkAllNotificationsRead"/);
  assert.match(web, /loadPassengerNotifications/);
  assert.match(web, /markAllPassengerNotificationsRead/);
  assert.match(web, /2_500/);
  assert.match(web, /passengerNotificationTarget/);
});

test("Android driver exposes notification center and stable push replacement id", () => {
  assert.match(androidApi, /data class DriverNotificationsResponse/);
  assert.match(androidApi, /listDriverNotifications/);
  assert.match(androidApi, /markDriverNotificationRead/);
  assert.match(androidApi, /markAllDriverNotificationsRead/);
  assert.match(androidUi, /driverUnreadCount/);
  assert.match(androidUi, /refreshDriverNotifications/);
  assert.match(androidUi, /Marcar todas como lidas/);
  assert.match(androidUi, /TripScreen\.NOTIFICATIONS/);
  assert.match(androidUi, /openNotifications0396/);
  assert.match(androidUi, /Notificações/);
  assert.match(messaging, /\(event \+ ":" \+ bookingId\)\.hashCode\(\)/);
  assert.doesNotMatch(messaging, /System\.currentTimeMillis\(\) \/ 60_000L/);
});

test("notification pipeline does not alter FAROL source", () => {
  assert.doesNotMatch(api, /LiveRideAccessibilityService/);
  assert.doesNotMatch(web, /LiveRideAccessibilityService/);
});
