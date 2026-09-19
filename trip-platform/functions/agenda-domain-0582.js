"use strict";

/**
 * Pure public Agenda lifecycle policy.
 *
 * New documents must carry agendaVisibleUntilMillis0581, materialized by the
 * canonical Android domain. Stop-based calculation exists only for legacy
 * documents that predate the canonical cutoff.
 */
const PUBLIC_AGENDA_ARRIVAL_GRACE_MILLIS_0577 = 60 * 60 * 1000;
const PUBLIC_AGENDA_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 = 12 * 60 * 60 * 1000;

function canonicalAgendaVisibleUntil0582(data) {
  const canonicalCutoff = Math.max(
    0,
    Math.floor(Number(
      data && data.agendaVisibleUntilMillis0581 ||
      data && data.canonicalPublicProjection0434 && data.canonicalPublicProjection0434.agendaVisibleUntilMillis0581 ||
      0,
    )),
  );
  if (canonicalCutoff > 0) return canonicalCutoff;

  const departure = Math.max(0, Number(data && data.departureAtMillis || 0));
  if (!departure) return 0;
  const stops = Array.isArray(data && data.stops)
    ? [...data.stops].sort((a, b) => Number(a && a.order || 0) - Number(b && b.order || 0))
    : [];
  const lastStop = stops.length ? stops[stops.length - 1] : null;
  const candidates = [
    Number(data && data.arrivalAtMillis || 0),
    Number(lastStop && lastStop.plannedArrivalMillis || 0),
    Number(lastStop && lastStop.plannedDepartureMillis || 0),
  ].filter((value) => Number.isFinite(value) && value >= departure);
  const arrival = candidates.length ? Math.max(...candidates) : 0;
  return arrival >= departure
    ? arrival + PUBLIC_AGENDA_ARRIVAL_GRACE_MILLIS_0577
    : departure + PUBLIC_AGENDA_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577;
}

function canonicalAgendaLifecycleDecision0582(data, nowMillis = Date.now()) {
  const visibleUntilMillis = canonicalAgendaVisibleUntil0582(data);
  const departureAtMillis = Math.max(0, Number(data && data.departureAtMillis || 0));
  if (!visibleUntilMillis || !departureAtMillis) {
    return {
      visible: false,
      visibleUntilMillis: 0,
      reasonCode: "AGENDA_VISIBILITY_INVALID_TIME",
    };
  }
  const now = Number(nowMillis || 0);
  const visible = now <= visibleUntilMillis;
  return {
    visible,
    visibleUntilMillis,
    reasonCode: !visible
      ? "AGENDA_VISIBILITY_EXPIRED"
      : now < departureAtMillis
        ? "AGENDA_VISIBILITY_FUTURE_TRIP"
        : "AGENDA_VISIBILITY_ACTIVE_TRIP",
  };
}

module.exports = {
  PUBLIC_AGENDA_ARRIVAL_GRACE_MILLIS_0577,
  PUBLIC_AGENDA_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577,
  canonicalAgendaVisibleUntil0582,
  canonicalAgendaLifecycleDecision0582,
};
