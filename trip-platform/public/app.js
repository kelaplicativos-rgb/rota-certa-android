"use strict";

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

const RESERVED_PUBLIC_SLUGS = new Set(["v1", "calendar"]);

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
const agendaToken = (params.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
const publicSlug = publicSlugFromPath();
const queryDriverUsername = normalizePublicSlug(params.get("motorista") || "");
const driverUsername = queryDriverUsername || publicSlug;

function show(id, visible = true) {
  const node = $(id);
  if (node) node.classList.toggle("hidden", !visible);
}

function setError(message) {
  $("error").textContent = message;
  show("error", true);
  show("loading", false);
  show("agenda", false);
}

function formatTime(ms) {
  const value = Number(ms || 0);
  if (!value) return "";
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatMoney(cents) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .format(Math.max(0, Number(cents || 0)) / 100);
}

function authoritativeUpdatedLabel0491(ms) {
  const value = Number(ms || 0);
  if (!Number.isFinite(value) || value <= 0) return "";
  return "Atualizado às " + new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function agendaDateLabel0473(ms) {
  const date = new Date(Number(ms || 0));
  if (!Number.isFinite(date.getTime())) return "";
  const now = new Date();
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const targetStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const deltaDays = Math.round((targetStart - dayStart) / 86400000);
  if (deltaDays === -1) return "Ontem";
  if (deltaDays === 0) return "Hoje";
  if (deltaDays === 1) return "Amanhã";
  const weekdays = ["Dom.", "Seg.", "Ter.", "Qua.", "Qui.", "Sex.", "Sáb."];
  const months = ["Jan.", "Fev.", "Mar.", "Abr.", "Mai.", "Jun.", "Jul.", "Ago.", "Set.", "Out.", "Nov.", "Dez."];
  const yearSuffix = date.getFullYear() !== now.getFullYear() ? " " + date.getFullYear() : "";
  return weekdays[date.getDay()] + " " + String(date.getDate()).padStart(2, "0") + " " + months[date.getMonth()] + yearSuffix;
}

function orderedStops(source) {
  return [...(source?.stops || [])].sort((a, b) => Number(a.order) - Number(b.order));
}

function agendaSegmentMoment0473(item, stop, index, fallbackMillis) {
  if (!stop) return Number(fallbackMillis || 0);
  if (index === 0) {
    return Number(stop.plannedDepartureMillis || stop.plannedArrivalMillis || fallbackMillis || 0);
  }
  return Number(stop.plannedArrivalMillis || stop.plannedDepartureMillis || fallbackMillis || 0);
}

function agendaDurationBetween0473(startMillis, endMillis) {
  const start = Number(startMillis || 0);
  const end = Number(endMillis || 0);
  if (!start || !end || end <= start) return "";
  const minutes = Math.round((end - start) / 60000);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? hours + "h" + String(rest).padStart(2, "0") : minutes + " min";
}

function seatRange(item) {
  const serverMinimum = Number(item.availableSeatsMinimum);
  const serverMaximum = Number(item.availableSeatsMaximum);
  if (Number.isFinite(serverMinimum) && Number.isFinite(serverMaximum)) {
    return { minimum: Math.max(0, serverMinimum), maximum: Math.max(0, serverMaximum) };
  }
  const loads = Array.isArray(item.segmentLoads) ? item.segmentLoads.map(Number) : [];
  if (!loads.length) {
    const capacity = Math.max(0, Number(item.capacity || 0));
    return { minimum: capacity, maximum: capacity };
  }
  const available = loads.map((load) => Math.max(0, Number(item.capacity || 0) - load));
  return { minimum: Math.min(...available), maximum: Math.max(...available) };
}

function isFullTrip(item) {
  const range = seatRange(item);
  return item?.isFull === true || item?.status === "FULL" || (range.minimum === 0 && range.maximum === 0);
}

function exactAvailabilityLabel(available) {
  const count = Math.max(0, Number(available || 0));
  if (count === 0) return "LOTADO";
  return count === 1 ? "1 vaga disponível" : count + " vagas disponíveis";
}

function publicAvailabilityLabel(item) {
  if (item?.capacityReliable !== true) return "Disponibilidade sendo atualizada";
  const range = seatRange(item);
  if (range.minimum === 0 && range.maximum === 0) return "LOTADO";
  if (range.minimum === range.maximum) return exactAvailabilityLabel(range.maximum);
  return range.minimum + "–" + range.maximum + " vagas disponíveis por trecho";
}

function publicSegmentRows0484(item) {
  if (item?.capacityReliable !== true || !Array.isArray(item?.segmentAvailability)) return [];
  return item.segmentAvailability
    .filter((segment) =>
      segment &&
      String(segment.from || "").trim() &&
      String(segment.to || "").trim() &&
      Number.isFinite(Number(segment.availableSeats)),
    )
    .map((segment) => ({
      from: String(segment.from).trim(),
      to: String(segment.to).trim(),
      availableSeats: Math.max(0, Math.floor(Number(segment.availableSeats))),
      passengerSeats: segment.passengerSeats != null && Number.isFinite(Number(segment.passengerSeats))
        ? Math.max(0, Math.floor(Number(segment.passengerSeats)))
        : null,
    }));
}

function segmentAvailabilityLabel0484(availableSeats) {
  const count = Math.max(0, Math.floor(Number(availableSeats || 0)));
  if (count === 0) return "LOTADO";
  if (count === 1) return "1 vaga";
  return count + " vagas";
}

function fullFareFor(item) {
  const stops = orderedStops(item);
  return stops.slice(0, -1).reduce(
    (sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)),
    0,
  );
}

function normalizedSeatCount(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function appendSegmentPassengerDots0489(container, passengerSeats) {
  if (passengerSeats == null) {
    container.setAttribute("aria-label", "Ocupação de passageiros indisponível neste trecho");
    return;
  }
  const count = normalizedSeatCount(passengerSeats);
  container.setAttribute(
    "aria-label",
    count === 1 ? "1 passageiro confirmado neste trecho" : count + " passageiros confirmados neste trecho",
  );
  const shown = Math.min(4, count);
  for (let index = 0; index < shown; index += 1) {
    const dot = document.createElement("span");
    dot.className = "agendaPassengerDot0473";
    dot.setAttribute("aria-hidden", "true");
    container.appendChild(dot);
  }
  if (count > 4) {
    const more = document.createElement("span");
    more.className = "agendaPassengerMore0473";
    more.setAttribute("aria-hidden", "true");
    more.textContent = "+" + (count - 4);
    container.appendChild(more);
  }
}

function publicCardEligible0475(item) {
  return PUBLIC_AGENDA_CARD_STATUSES_0469.has(String(item?.status || "").toUpperCase()) &&
    orderedStops(item).length >= 2;
}

function agendaLongDateLabel0480(ms) {
  const date = new Date(Number(ms || 0));
  if (!Number.isFinite(date.getTime())) return "";
  const weekdays = ["Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"];
  const months = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
  return weekdays[date.getDay()] + ", " + date.getDate() + " de " + months[date.getMonth()] + " de " + date.getFullYear();
}

function agendaStopMoment0480(item, stop, index, lastIndex) {
  if (!stop) return 0;
  if (index === 0) {
    return Number(stop.plannedDepartureMillis || stop.plannedArrivalMillis || item.departureAtMillis || 0);
  }
  if (index === lastIndex) {
    return Number(stop.plannedArrivalMillis || stop.plannedDepartureMillis || 0);
  }
  return Number(stop.plannedArrivalMillis || stop.plannedDepartureMillis || 0);
}

function toggleAgendaTripDetails0480(card, dateNode, detailsNode, hintNode) {
  const expanded = card.getAttribute("aria-expanded") !== "true";
  card.setAttribute("aria-expanded", expanded ? "true" : "false");
  card.classList.toggle("agendaTripExpanded0480", expanded);
  detailsNode.setAttribute("aria-hidden", expanded ? "false" : "true");
  dateNode.textContent = expanded ? dateNode.dataset.expandedLabel : dateNode.dataset.compactLabel;
  hintNode.textContent = expanded ? "Recolher trajeto" : "Ver paradas";
}

function renderAgendaCards(entries, container) {
  entries.forEach((item) => {
    const stops = orderedStops(item);
    const fromIndex = 0;
    const toIndex = stops.length - 1;
    const from = stops[fromIndex]?.name || "Origem";
    const to = stops[toIndex]?.name || "Destino";
    const fare = fullFareFor(item);
    const full = isFullTrip(item);

    const card = document.createElement("article");
    card.className = full ? "agendaTrip agendaTripFull" : "agendaTrip";
    card.setAttribute("data-card-surface", "canonical-trip-0473");
    card.setAttribute("role", "button");
    card.setAttribute("tabindex", "0");
    card.setAttribute("aria-expanded", "false");
    card.setAttribute("aria-label", "Ver detalhes da viagem de " + from + " para " + to);
    const startStop0473 = stops[fromIndex];
    const endStop0473 = stops[toIndex];
    const startMillis0473 = agendaSegmentMoment0473(item, startStop0473, 0, item.departureAtMillis);
    const endMillis0473 = agendaSegmentMoment0473(item, endStop0473, toIndex, 0);
    const duration0473 = agendaDurationBetween0473(startMillis0473, endMillis0473);

    const canonicalVisual0473 = document.createElement("div");
    canonicalVisual0473.className = "agendaCanonicalVisual0473";

    const date = document.createElement("div");
    date.className = "agendaDate0473";
    date.dataset.compactLabel = agendaDateLabel0473(item.departureAtMillis);
    date.dataset.expandedLabel = agendaLongDateLabel0480(item.departureAtMillis);
    date.textContent = date.dataset.compactLabel;

    const journey0473 = document.createElement("div");
    journey0473.className = "agendaJourney0473";

    const startTime0473 = document.createElement("div");
    startTime0473.className = "agendaJourneyTime0473 agendaJourneyStart0473";
    const startClock0473 = document.createElement("strong");
    startClock0473.textContent = formatTime(startMillis0473 || item.departureAtMillis);
    startTime0473.appendChild(startClock0473);
    if (duration0473) {
      const durationNode0473 = document.createElement("small");
      durationNode0473.className = "agendaJourneyDuration0473";
      durationNode0473.textContent = duration0473;
      startTime0473.appendChild(durationNode0473);
    }

    const rail0473 = document.createElement("div");
    rail0473.className = "agendaJourneyRail0473";
    rail0473.innerHTML = '<span class="agendaJourneyDot0473"></span><span class="agendaJourneyLine0473"></span><span class="agendaJourneyDot0473"></span>';
    rail0473.setAttribute("aria-hidden", "true");

    const startCity0473 = document.createElement("div");
    startCity0473.className = "agendaJourneyCity0473 agendaJourneyStartCity0473";
    startCity0473.textContent = from;

    const endTime0473 = document.createElement("div");
    endTime0473.className = "agendaJourneyTime0473 agendaJourneyEnd0473";
    const endClock0473 = document.createElement("strong");
    endClock0473.textContent = formatTime(endMillis0473);
    endTime0473.appendChild(endClock0473);

    const endCity0473 = document.createElement("div");
    endCity0473.className = "agendaJourneyCity0473 agendaJourneyEndCity0473";
    endCity0473.textContent = to;

    journey0473.append(startTime0473, rail0473, startCity0473, endTime0473, endCity0473);

    const expandedItinerary0480 = document.createElement("div");
    expandedItinerary0480.className = "agendaExpandedItinerary0480";
    expandedItinerary0480.setAttribute("aria-hidden", "true");

    stops.forEach((stop, index) => {
      const row0480 = document.createElement("div");
      row0480.className = "agendaExpandedStop0480";
      if (index === 0) row0480.classList.add("agendaExpandedStopFirst0480");
      if (index === toIndex) row0480.classList.add("agendaExpandedStopLast0480");

      const time0480 = document.createElement("div");
      time0480.className = "agendaExpandedStopTime0480";
      const moment0480 = agendaStopMoment0480(item, stop, index, toIndex);
      if (moment0480) {
        const clock0480 = document.createElement("strong");
        clock0480.textContent = formatTime(moment0480);
        time0480.appendChild(clock0480);
      }
      if (index === 0 && duration0473) {
        const duration0480 = document.createElement("small");
        duration0480.className = "agendaExpandedDuration0480";
        duration0480.textContent = duration0473;
        time0480.appendChild(duration0480);
      }

      const rail0480 = document.createElement("div");
      rail0480.className = "agendaExpandedStopRail0480";
      rail0480.setAttribute("aria-hidden", "true");
      const dot0480 = document.createElement("span");
      dot0480.className = "agendaExpandedStopDot0480";
      rail0480.appendChild(dot0480);
      if (index < toIndex) {
        const line0480 = document.createElement("span");
        line0480.className = "agendaExpandedStopLine0480";
        rail0480.appendChild(line0480);
      }

      const body0480 = document.createElement("div");
      body0480.className = "agendaExpandedStopBody0480";
      const city0480 = document.createElement("strong");
      city0480.className = "agendaExpandedStopCity0480";
      city0480.textContent = String(stop?.name || (index === 0 ? "Origem" : (index === toIndex ? "Destino" : "Parada")));
      body0480.appendChild(city0480);

      const address0480 = String(stop?.address || "").trim();
      if (address0480) {
        const addressNode0480 = document.createElement("div");
        addressNode0480.className = "agendaExpandedStopAddress0480";
        addressNode0480.textContent = address0480;
        body0480.appendChild(addressNode0480);
      }

      row0480.append(time0480, rail0480, body0480);
      expandedItinerary0480.appendChild(row0480);
    });

    const segmentAvailability0484 = document.createElement("section");
    segmentAvailability0484.className = "agendaSegmentAvailability0484";

    const segmentTitle0484 = document.createElement("h3");
    segmentTitle0484.className = "agendaSegmentAvailabilityTitle0484";
    segmentTitle0484.textContent = "Vagas por trecho";
    segmentAvailability0484.appendChild(segmentTitle0484);

    const segmentRows0484 = publicSegmentRows0484(item);
    const expectedSegmentRows0484 = Math.max(0, stops.length - 1);
    if (item?.capacityReliable !== true) {
      const pending0484 = document.createElement("div");
      pending0484.className = "agendaSegmentAvailabilityPending0484";
      pending0484.textContent = "Disponibilidade sendo atualizada";
      segmentAvailability0484.appendChild(pending0484);
    } else if (expectedSegmentRows0484 < 1 || segmentRows0484.length !== expectedSegmentRows0484) {
      const unavailable0484 = document.createElement("div");
      unavailable0484.className = "agendaSegmentAvailabilityPending0484";
      unavailable0484.textContent = "Disponibilidade por trecho indisponível";
      segmentAvailability0484.appendChild(unavailable0484);
    } else {
      segmentRows0484.forEach((segment) => {
        const row0484 = document.createElement("div");
        row0484.className = "agendaSegmentAvailabilityRow0484";

        const route0484 = document.createElement("span");
        route0484.className = "agendaSegmentAvailabilityRoute0484";
        route0484.textContent = segment.from + " → " + segment.to;

        const passengers0489 = document.createElement("span");
        passengers0489.className = "agendaSegmentPassengers0489";
        appendSegmentPassengerDots0489(passengers0489, segment.passengerSeats);

        const seats0484 = document.createElement("strong");
        seats0484.className = "agendaSegmentAvailabilitySeats0484";
        seats0484.textContent = segmentAvailabilityLabel0484(segment.availableSeats);

        row0484.append(route0484, passengers0489, seats0484);
        segmentAvailability0484.appendChild(row0484);
      });
    }
    expandedItinerary0480.appendChild(segmentAvailability0484);

    const bottom = document.createElement("div");
    bottom.className = "agendaBottom0473";

    const occupancy0473 = document.createElement("div");
    occupancy0473.className = "agendaOccupancy0473";
    const car0473 = document.createElement("span");
    car0473.className = "agendaCar0473";
    car0473.setAttribute("role", "img");
    car0473.setAttribute("aria-label", "Viagem de carro");
    car0473.textContent = "🚗";
    occupancy0473.appendChild(car0473);

    const summary0473 = document.createElement("div");
    summary0473.className = "agendaCardSummary0473";
    const availability0473 = document.createElement("span");
    availability0473.className = "agendaCardAvailability0473";
    availability0473.textContent = publicAvailabilityLabel(item);
    summary0473.appendChild(availability0473);
    const updatedLabel0491 = authoritativeUpdatedLabel0491(item.updatedAtMillis);
    if (updatedLabel0491) {
      const updated0491 = document.createElement("small");
      updated0491.className = "agendaCardAvailability0473";
      updated0491.textContent = updatedLabel0491;
      summary0473.appendChild(updated0491);
    }
    if (fare > 0) {
      const price = document.createElement("span");
      price.className = "agendaCardPrice0473";
      price.textContent = formatMoney(fare);
      summary0473.appendChild(price);
    }

    const expandHint0480 = document.createElement("div");
    expandHint0480.className = "agendaExpandHint0480";
    expandHint0480.textContent = "Ver paradas";
    expandHint0480.setAttribute("aria-hidden", "true");

    bottom.append(occupancy0473, summary0473);
    canonicalVisual0473.append(date, journey0473, expandedItinerary0480, bottom, expandHint0480);
    card.appendChild(canonicalVisual0473);

    if (full) {
      const fullWord = document.createElement("div");
      fullWord.className = "fullWord";
      fullWord.textContent = "LOTADO";
      card.appendChild(fullWord);
    }

    card.addEventListener("click", (event) => {
      if (event.target.closest("a,button,input,select,textarea")) return;
      toggleAgendaTripDetails0480(card, date, expandedItinerary0480, expandHint0480);
    });
    card.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      toggleAgendaTripDetails0480(card, date, expandedItinerary0480, expandHint0480);
    });

    container.appendChild(card);
  });
}

function renderAgenda(trips) {
  const visibleTrips = trips
    .filter(publicCardEligible0475)
    .sort((a, b) => Number(a.departureAtMillis || 0) - Number(b.departureAtMillis || 0));

  $("agendaTrips").innerHTML = "";
  if (!visibleTrips.length) {
    const empty = document.createElement("div");
    empty.className = "card muted";
    empty.textContent = "Nenhuma próxima viagem encontrada.";
    $("agendaTrips").appendChild(empty);
  } else {
    renderAgendaCards(visibleTrips, $("agendaTrips"));
  }
  show("loading", false);
  show("error", false);
  show("agenda", true);
}

let agendaLoadInFlight0491 = false;

function configurePassengerAreaLink0491() {
  const link = $("passengerAreaLink0491");
  if (!link || driverUsername.length < 3) return;
  link.href = "/minha-area.html?motorista=" + encodeURIComponent(driverUsername);
}

async function loadAgenda(silent0491 = false) {
  if (agendaLoadInFlight0491) return;
  if (driverUsername.length < 3 || (!publicSlug && agendaToken.length < 16)) {
    if (!silent0491) setError("Este link não identifica uma Agenda de Viagens válida.");
    return;
  }
  agendaLoadInFlight0491 = true;
  try {
    const endpoint = publicSlug
      ? "/v1/public/agenda/" + encodeURIComponent(publicSlug)
      : "/v1/public/drivers/" + encodeURIComponent(driverUsername) + "/" + encodeURIComponent(agendaToken) + "/agenda";
    const response = await fetch(endpoint, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Agenda indisponível.");
    const displayName = String(body?.driver?.displayName || driverUsername || "").trim();
    $("driverName").textContent = displayName ? "Viagens com " + displayName : "";
    renderAgenda(Array.isArray(body.trips) ? body.trips : []);
  } catch (error) {
    if (!silent0491) setError(error.message || "Não foi possível carregar a Agenda de Viagens.");
  } finally {
    agendaLoadInFlight0491 = false;
  }
}

configurePassengerAreaLink0491();
loadAgenda(false);
window.setInterval(() => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda(true);
}, 15000);
window.addEventListener("online", () => loadAgenda(true));
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda(true);
});


