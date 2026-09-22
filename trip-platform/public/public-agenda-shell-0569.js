"use strict";

const PUBLIC_AGENDA_CARD_STATUSES_0569 = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);
const RESERVED_PUBLIC_SLUGS_0569 = new Set(["v1", "calendar"]);
const $0569 = (id) => document.getElementById(id);

function normalizePublicSlug0569(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

function publicSlugFromPath0569() {
  const parts = location.pathname.split("/").filter(Boolean);
  if (parts.length !== 1) return "";
  let raw = parts[0];
  try { raw = decodeURIComponent(raw); } catch (_) { return ""; }
  const normalized = normalizePublicSlug0569(raw);
  if (normalized.length < 3 || RESERVED_PUBLIC_SLUGS_0569.has(normalized)) return "";
  return normalized;
}

const params0569 = new URLSearchParams(location.search);
const publicSlug0569 = publicSlugFromPath0569();
const queryDriver0569 = normalizePublicSlug0569(params0569.get("motorista") || "");
const driverUsername0569 = queryDriver0569 || publicSlug0569;
const agendaToken0569 = String(params0569.get("agenda") || "").replace(/[^A-Za-z0-9_-]/g, "");
let agendaLoadInFlight0569 = false;
let publicDriverWhatsapp0569 = "";
let publicDriverDisplayName0569 = "";
const agendaViewStorageKey0589 = "viagem-certa:agenda-view:0589:" + (driverUsername0569 || publicSlug0569 || "unknown");
let agendaViewToken0589 = sessionStorage.getItem(agendaViewStorageKey0589) || "";
let passengerAccessInFlight0589 = false;

function setPassengerAccessMessage0589(message) {
  const node = $0569("passengerAccessMessage0589");
  if (!node) return;
  node.textContent = String(message || "");
  node.classList.toggle("hidden", !message);
}

function configurePassengerAreaLink0589() {
  const link = $0569("passengerAreaLink0589");
  if (!link || !driverUsername0569) return;
  link.href = "/minha-area.html?motorista=" + encodeURIComponent(driverUsername0569);
}

function showPassengerAccessGate0589(message = "") {
  setVisible0569("accessGate0589", true);
  setVisible0569("passengerNav0589", false);
  setVisible0569("loading", false);
  setVisible0569("agenda", false);
  setVisible0569("error", false);
  setPassengerAccessMessage0589(message);
}

function showPassengerAgendaAccess0589() {
  setVisible0569("accessGate0589", false);
  setVisible0569("passengerNav0589", true);
  setPassengerAccessMessage0589("");
  configurePassengerAreaLink0589();
}

function clearPassengerAgendaAccess0589() {
  agendaViewToken0589 = "";
  sessionStorage.removeItem(agendaViewStorageKey0589);
}

async function requestPassengerAgendaAccess0589() {
  if (passengerAccessInFlight0589) return;
  const input = $0569("passengerWhatsapp0589");
  const button = $0569("passengerAccessContinue0589");
  const passengerContact = String(input?.value || "").trim();
  if (!passengerContact) {
    setPassengerAccessMessage0589("Informe seu WhatsApp para continuar.");
    return;
  }
  passengerAccessInFlight0589 = true;
  if (button) button.disabled = true;
  setPassengerAccessMessage0589("");
  try {
    const response = await fetch("/v1/public/passenger-access", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      cache: "no-store",
      body: JSON.stringify({
        passengerContact,
        publicSlug: publicSlug0569,
        driverUsername: driverUsername0569,
        agendaToken: agendaToken0569,
      }),
    });
    const raw = await response.text();
    let body = null;
    try { body = JSON.parse(raw); } catch (_) { body = null; }
    if (!response.ok) {
      throw new Error(safeMessage0569(body?.message) || "Não foi possível liberar o acesso com este WhatsApp.");
    }
    const viewToken = String(body?.viewToken || "");
    if (!/^[A-Za-z0-9_-]{32,200}$/.test(viewToken)) {
      throw new Error("Não foi possível confirmar o acesso agora.");
    }
    agendaViewToken0589 = viewToken;
    sessionStorage.setItem(agendaViewStorageKey0589, agendaViewToken0589);
    if (input) input.value = "";
    showPassengerAgendaAccess0589();
    setVisible0569("loading", true);
    await loadAgenda0569(false);
  } catch (error) {
    clearPassengerAgendaAccess0589();
    showPassengerAccessGate0589(error?.message || "Não foi possível confirmar seu acesso.");
  } finally {
    passengerAccessInFlight0589 = false;
    if (button) button.disabled = false;
  }
}

function initPassengerAccess0589() {
  configurePassengerAreaLink0589();
  $0569("passengerAccessContinue0589")?.addEventListener("click", requestPassengerAgendaAccess0589);
  $0569("passengerWhatsapp0589")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") requestPassengerAgendaAccess0589();
  });
  $0569("passengerAgendaLogout0589")?.addEventListener("click", () => {
    clearPassengerAgendaAccess0589();
    publicDriverWhatsapp0569 = "";
    syncWhatsappFab0569();
    showPassengerAccessGate0589("");
    $0569("passengerWhatsapp0589")?.focus();
  });
  if (agendaViewToken0589) {
    showPassengerAgendaAccess0589();
    setVisible0569("loading", true);
    loadAgenda0569(false);
  } else {
    showPassengerAccessGate0589("");
  }
}

function setVisible0569(id, visible) {
  const node = $0569(id);
  if (node) node.classList.toggle("hidden", !visible);
}

function showError0569(message) {
  const node = $0569("error");
  if (node) node.textContent = message || "Não foi possível carregar as viagens.";
  setVisible0569("loading", false);
  setVisible0569("agenda", false);
  setVisible0569("error", true);
}

function timezone0569(raw) {
  const candidate = String(raw || "").trim();
  const fallback = Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Sao_Paulo";
  if (!candidate) return fallback;
  try {
    new Intl.DateTimeFormat("pt-BR", { timeZone: candidate }).format(new Date(0));
    return candidate;
  } catch (_) {
    return fallback;
  }
}

function dateParts0569(ms, timezoneId) {
  const value = Number(ms || 0);
  if (!Number.isFinite(value) || value <= 0) return null;
  const timeZone = timezone0569(timezoneId);
  const parts = {};
  new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(value)).forEach((part) => {
    if (part.type !== "literal") parts[part.type] = part.value;
  });
  const year = Number(parts.year);
  const month = Number(parts.month);
  const day = Number(parts.day);
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) return null;
  const utcDay = Date.UTC(year, month - 1, day);
  return { year, month: month - 1, day, weekday: new Date(utcDay).getUTCDay() };
}

function dateLabel0569(ms, timezoneId) {
  const parts = dateParts0569(ms, timezoneId);
  if (!parts) return "";
  const weekdays = ["Dom.", "Seg.", "Ter.", "Qua.", "Qui.", "Sex.", "Sáb."];
  const months = ["Jan.", "Fev.", "Mar.", "Abr.", "Mai.", "Jun.", "Jul.", "Ago.", "Set.", "Out.", "Nov.", "Dez."];
  return `${weekdays[parts.weekday]} ${String(parts.day).padStart(2, "0")} ${months[parts.month]} ${parts.year}`;
}

function timeLabel0569(ms, timezoneId) {
  const value = Number(ms || 0);
  if (!Number.isFinite(value) || value <= 0) return "";
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: timezone0569(timezoneId),
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function durationLabel0569(startMillis, endMillis) {
  const start = Number(startMillis || 0);
  const end = Number(endMillis || 0);
  if (!start || !end || end <= start) return "";
  const minutes = Math.round((end - start) / 60000);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${minutes} min`;
  return rest ? `${hours}h${String(rest).padStart(2, "0")}` : `${hours}h`;
}

function orderedStops0569(item) {
  return [...(Array.isArray(item?.stops) ? item.stops : [])]
    .sort((a, b) => Number(a?.order || 0) - Number(b?.order || 0));
}

function startMillis0569(item, firstStop) {
  return Number(item?.departureAtMillis || firstStop?.plannedDepartureMillis || firstStop?.plannedArrivalMillis || 0);
}

function endMillis0569(lastStop) {
  return Number(lastStop?.plannedArrivalMillis || lastStop?.plannedDepartureMillis || 0);
}

function isOfficialBlaBlaHost0569(hostname) {
  const labels = String(hostname || "").toLowerCase().replace(/^\.+|\.+$/g, "").split(".").filter(Boolean);
  const root = labels[0] === "www" ? labels.slice(1) : labels;
  if (root[0] !== "blablacar") return false;
  const suffix = root.slice(1);
  if (suffix.length === 1) return suffix[0] === "com" || /^[a-z]{2}$/.test(suffix[0]);
  return suffix.length === 2 && ["com", "co"].includes(suffix[0]) && /^[a-z]{2}$/.test(suffix[1]);
}

function validatedBlaBlaPublicUrl0569(raw) {
  const value = String(raw || "").trim().slice(0, 1200);
  if (!value) return "";
  try {
    const url = new URL(value);
    if (!["http:", "https:"].includes(url.protocol) || !isOfficialBlaBlaHost0569(url.hostname)) return "";
    if (url.username || url.password || (url.port && !["80", "443"].includes(url.port))) return "";
    const normalizedPath = url.pathname.replace(/\/+$/, "").toLowerCase();
    let publicId = "";
    if (normalizedPath === "/trip") {
      publicId = String(url.searchParams.get("id") || "").trim();
    } else if (normalizedPath.startsWith("/trip/")) {
      const match = url.pathname.match(/\/trip\/([^/?#]+)/i);
      publicId = match ? String(match[1] || "").trim() : "";
    } else {
      return "";
    }
    if (!/^[A-Za-z0-9_-]{6,}$/.test(publicId)) return "";
    const forbidden = new Set(["requested_seats", "search_origin", "search_uuid"]);
    for (const key of Array.from(url.searchParams.keys())) {
      if (forbidden.has(String(key).toLowerCase())) url.searchParams.delete(key);
    }
    const sourceParam = String(url.searchParams.get("source") || "").trim().toUpperCase();
    if (sourceParam && sourceParam !== "CARPOOLING") return "";
    url.protocol = "https:";
    if (url.port === "80" || url.port === "443") url.port = "";
    url.hash = "";
    return url.toString();
  } catch (_) {
    return "";
  }
}

function whatsappDigits0569(raw) {
  const digits = String(raw || "").replace(/\D/g, "");
  return digits.length >= 10 && digits.length <= 15 ? digits : "";
}

function publicWhatsappHref0569() {
  const digits = whatsappDigits0569(publicDriverWhatsapp0569);
  if (!digits) return "";
  const message = "Olá! Vi suas viagens na Agenda Rota Certa e gostaria de falar sobre uma reserva.";
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
}

function segmentBoardingMillis0584(item, stops, segmentIndex) {
  const index = Math.max(0, Math.floor(Number(segmentIndex || 0)));
  const stop = Array.isArray(stops) ? stops[index] : null;
  if (index === 0) {
    return Number(item?.departureAtMillis || stop?.plannedDepartureMillis || stop?.plannedArrivalMillis || 0);
  }
  return Number(stop?.plannedDepartureMillis || stop?.plannedArrivalMillis || item?.departureAtMillis || 0);
}

function segmentWhatsappMessage0584(item, segment, stops, segmentIndex) {
  const from = String(segment?.from || "").trim();
  const to = String(segment?.to || "").trim();
  const availableSeats = Math.max(0, Math.floor(Number(segment?.availableSeats || 0)));
  if (!from || !to || availableSeats < 1) return "";
  const boardingMillis = segmentBoardingMillis0584(item, stops, segmentIndex);
  const date = dateLabel0569(boardingMillis, item?.timezoneId);
  const time = timeLabel0569(boardingMillis, item?.timezoneId);
  const when = [date, time ? "às " + time : ""].filter(Boolean).join(" ");
  return `Olá! Quero reservar 1 lugar no trecho ${from} → ${to}${when ? ", com embarque " + when : ""}. Enviei esta solicitação pela Agenda Rota Certa.`;
}

function segmentWhatsappHref0584(item, segment, stops, segmentIndex) {
  const digits = whatsappDigits0569(publicDriverWhatsapp0569);
  const message = segmentWhatsappMessage0584(item, segment, stops, segmentIndex);
  if (!digits || !message) return "";
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
}

function syncWhatsappFab0569() {
  const fab = $0569("whatsappFab0569");
  if (!fab) return;
  const href = publicWhatsappHref0569();
  if (!href) {
    fab.removeAttribute("href");
    fab.classList.add("hidden");
    fab.setAttribute("aria-hidden", "true");
    return;
  }
  fab.href = href;
  fab.rel = "noopener noreferrer";
  fab.classList.remove("hidden");
  fab.setAttribute("aria-hidden", "false");
}

function cardEligible0569(item) {
  return PUBLIC_AGENDA_CARD_STATUSES_0569.has(String(item?.status || "").toUpperCase()) && orderedStops0569(item).length >= 2;
}

function publicSegmentRows0580(item, stops) {
  const expected = Math.max(0, (Array.isArray(stops) ? stops.length : 0) - 1);
  const capacity = Math.max(0, Math.floor(Number(item?.capacity || 0)));
  if (item?.capacityReliable !== true || expected < 1 || !Array.isArray(item?.segmentAvailability)) return [];
  if (item.segmentAvailability.length !== expected) return [];
  return item.segmentAvailability.map((segment) => {
    const from = String(segment?.from || "").trim();
    const to = String(segment?.to || "").trim();
    const availableSeats = Math.max(0, Math.min(capacity, Math.floor(Number(segment?.availableSeats || 0))));
    const passengerSeats = Math.max(0, Math.min(capacity, Math.floor(Number(segment?.passengerSeats || 0))));
    if (
      !from ||
      !to ||
      !Number.isFinite(Number(segment?.availableSeats)) ||
      !Number.isFinite(Number(segment?.passengerSeats))
    ) return null;
    return { from, to, availableSeats, passengerSeats, capacity };
  }).filter(Boolean);
}

function segmentAvailabilityLabel0580(availableSeats) {
  const available = Math.max(0, Math.floor(Number(availableSeats || 0)));
  if (available === 0) return "LOTADO";
  if (available === 1) return "1 vaga";
  return available + " vagas";
}

function segmentSeatDots0580(capacity, availableSeats) {
  const total = Math.max(0, Math.floor(Number(capacity || 0)));
  const available = Math.max(0, Math.min(total, Math.floor(Number(availableSeats || 0))));
  const occupied = Math.max(0, total - available);
  return "●".repeat(occupied) + "○".repeat(available);
}

function appendSegmentAvailability0580(card, item, stops) {
  const section = document.createElement("section");
  section.className = "agendaSegments0580";
  section.setAttribute("aria-label", "Vagas por trecho");

  const title = document.createElement("strong");
  title.className = "agendaSegmentsTitle0580";
  title.textContent = "Vagas por trecho";
  section.appendChild(title);

  const rows = publicSegmentRows0580(item, stops);
  if (!rows.length) {
    const pending = document.createElement("div");
    pending.className = "agendaSegmentPending0580";
    pending.textContent = "Disponibilidade por trecho indisponível";
    section.appendChild(pending);
    card.appendChild(section);
    return;
  }

  rows.forEach((segment, segmentIndex) => {
    const row = document.createElement("div");
    row.className = "agendaSegmentRow0580";

    const route = document.createElement("span");
    route.className = "agendaSegmentRoute0580";
    route.textContent = segment.from + " → " + segment.to;

    const dots = document.createElement("span");
    dots.className = "agendaSegmentDots0580";
    dots.setAttribute("aria-hidden", "true");
    dots.textContent = segmentSeatDots0580(segment.capacity, segment.availableSeats);

    const occupancy = document.createElement("span");
    occupancy.className = "agendaSegmentOccupancy0596";
    occupancy.textContent = "👥 " + segment.passengerSeats + "/" + segment.capacity;
    occupancy.setAttribute(
      "aria-label",
      segment.passengerSeats + " de " + segment.capacity + " lugares ocupados neste trecho",
    );

    const seats = document.createElement("strong");
    seats.className = "agendaSegmentSeats0580";
    seats.textContent = segmentAvailabilityLabel0580(segment.availableSeats);

    row.append(route, dots, occupancy, seats);

    const reservationHref = segment.availableSeats > 0
      ? segmentWhatsappHref0584(item, segment, stops, segmentIndex)
      : "";
    if (reservationHref) {
      const reserve = document.createElement("a");
      reserve.className = "agendaSegmentReserve0584";
      reserve.href = reservationHref;
      reserve.rel = "noopener noreferrer";
      reserve.textContent = "Reserve Já";
      reserve.setAttribute(
        "aria-label",
        `Reserve Já pelo WhatsApp o trecho ${segment.from} para ${segment.to}`,
      );
      row.appendChild(reserve);
    } else {
      const unavailable = document.createElement("button");
      unavailable.className = "agendaSegmentReserve0584 agendaSegmentReserveUnavailable0584";
      unavailable.type = "button";
      unavailable.disabled = true;
      unavailable.textContent = "Indisponível";
      unavailable.setAttribute(
        "aria-label",
        `Trecho ${segment.from} para ${segment.to} indisponível para reserva`,
      );
      row.appendChild(unavailable);
    }

    section.appendChild(row);
  });
  card.appendChild(section);
}

const AGENDA_CARD_REFRESH_KEY_0596 = "rota_certa_agenda_card_refresh_0596";
let agendaCardRefreshArmed0596 = false;

function armAgendaCardRefresh0596() {
  agendaCardRefreshArmed0596 = true;
  try {
    window.sessionStorage.setItem(AGENDA_CARD_REFRESH_KEY_0596, "1");
  } catch (_) {
    // Storage is only an optimization; focus/pageshow refresh remains available.
  }
}

function consumeAgendaCardRefresh0596() {
  let armed = agendaCardRefreshArmed0596;
  try {
    if (window.sessionStorage.getItem(AGENDA_CARD_REFRESH_KEY_0596) === "1") armed = true;
    if (armed) window.sessionStorage.removeItem(AGENDA_CARD_REFRESH_KEY_0596);
  } catch (_) {
    // Fall back to the in-memory marker.
  }
  agendaCardRefreshArmed0596 = false;
  if (armed && document.visibilityState === "visible" && navigator.onLine !== false) {
    loadAgenda0569(true);
  }
}

function appendJourney0569(card, item, firstStop, lastStop) {
  const start = startMillis0569(item, firstStop);
  const end = endMillis0569(lastStop);
  const duration = durationLabel0569(start, end);
  const journey = document.createElement("div");
  journey.className = "agendaJourney0569";
  const startTime = document.createElement("div");
  startTime.className = "agendaTime0569 agendaStartTime0569";
  const startClock = document.createElement("strong");
  startClock.textContent = timeLabel0569(start, item?.timezoneId);
  startTime.appendChild(startClock);
  if (duration) {
    const durationNode = document.createElement("small");
    durationNode.textContent = duration;
    startTime.appendChild(durationNode);
  }
  const rail = document.createElement("div");
  rail.className = "agendaRail0569";
  rail.setAttribute("aria-hidden", "true");
  rail.innerHTML = '<span class="agendaDot0569"></span><span class="agendaLine0569"></span><span class="agendaDot0569"></span>';
  const startCity = document.createElement("div");
  startCity.className = "agendaCity0569 agendaStartCity0569";
  startCity.textContent = String(firstStop?.name || "Origem").trim();
  const endTime = document.createElement("div");
  endTime.className = "agendaTime0569 agendaEndTime0569";
  const endClock = document.createElement("strong");
  endClock.textContent = timeLabel0569(end, item?.timezoneId);
  endTime.appendChild(endClock);
  const endCity = document.createElement("div");
  endCity.className = "agendaCity0569 agendaEndCity0569";
  endCity.textContent = String(lastStop?.name || "Destino").trim();
  journey.append(startTime, rail, startCity, endTime, endCity);
  card.appendChild(journey);
}

function renderTripCard0569(item) {
  const stops = orderedStops0569(item);
  const firstStop = stops[0];
  const lastStop = stops[stops.length - 1];
  const from = String(firstStop?.name || "Origem").trim();
  const to = String(lastStop?.name || "Destino").trim();
  const publicUrl = validatedBlaBlaPublicUrl0569(item?.blablaPublicUrl);
  const card = document.createElement("article");
  card.className = "agendaTrip0569";
  card.dataset.cardSurface = "public-shell-0569";
  card.setAttribute("aria-label", `Viagem ${from} para ${to}`);
  const top = document.createElement("div");
  top.className = "agendaTop0569";
  const date = document.createElement("strong");
  date.className = "agendaDate0569";
  date.textContent = dateLabel0569(item?.departureAtMillis, item?.timezoneId);
  const driver = document.createElement("span");
  driver.className = "agendaDriver0569";
  driver.textContent = String(item?.blablaProfileName || "").trim() || publicDriverDisplayName0569 || driverUsername0569;
  top.append(date, driver);
  card.appendChild(top);
  appendJourney0569(card, item, firstStop, lastStop);
  appendSegmentAvailability0580(card, item, stops);

  const actions = document.createElement("div");
  actions.className = "agendaCardActions0584";
  if (publicUrl) {
    const viewRide = document.createElement("a");
    viewRide.className = "agendaViewRide0584";
    viewRide.href = publicUrl;
    viewRide.rel = "noopener noreferrer";
    viewRide.textContent = "Ver carona";
    viewRide.setAttribute("aria-label", `Ver carona ${from} para ${to} na BlaBlaCar`);
    viewRide.addEventListener("click", armAgendaCardRefresh0596);
    actions.appendChild(viewRide);
  } else {
    const unavailable = document.createElement("span");
    unavailable.className = "agendaViewRide0584 agendaViewRideUnavailable0584";
    unavailable.textContent = "Ver carona";
    unavailable.setAttribute("aria-disabled", "true");
    actions.appendChild(unavailable);
  }
  card.appendChild(actions);
  return card;
}

function renderAgenda0569(trips) {
  const container = $0569("agendaTrips");
  if (!container) return;
  const visible = (Array.isArray(trips) ? trips : []).filter(cardEligible0569)
    .sort((a, b) => Number(a?.departureAtMillis || 0) - Number(b?.departureAtMillis || 0));
  container.replaceChildren();
  if (!visible.length) {
    const empty = document.createElement("div");
    empty.className = "empty0569";
    empty.textContent = "Nenhuma próxima viagem encontrada.";
    container.appendChild(empty);
  } else {
    visible.forEach((item) => container.appendChild(renderTripCard0569(item)));
  }
  setVisible0569("loading", false);
  setVisible0569("error", false);
  setVisible0569("agenda", true);
}

function safeMessage0569(raw) {
  const value = String(raw || "").replace(/[\r\n\t]+/g, " ").trim();
  if (!value || /<html|<!doctype|<head|<body/i.test(value)) return "";
  return value.slice(0, 180);
}

async function fetchJson0569(url, timeoutMillis = 12000, extraHeaders = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMillis);
  try {
    const response = await fetch(url, {
      headers: { Accept: "application/json", ...extraHeaders },
      cache: "no-store",
      signal: controller.signal,
    });
    const raw = await response.text();
    let body = null;
    try { body = JSON.parse(raw); } catch (_) { body = null; }
    if (!response.ok) {
      const error = new Error(safeMessage0569(body?.message) || `Viagem Certa temporariamente indisponível (HTTP ${response.status}).`);
      error.status = response.status;
      error.code = String(body?.code || "");
      throw error;
    }
    if (!body || typeof body !== "object" || !Array.isArray(body.trips)) {
      throw new Error("Viagem Certa temporariamente indisponível: resposta inválida.");
    }
    return body;
  } finally {
    window.clearTimeout(timeout);
  }
}

function primaryEndpoint0569() {
  if (publicSlug0569) return `/v1/public/agenda/${encodeURIComponent(publicSlug0569)}`;
  if (driverUsername0569 && agendaToken0569.length >= 16) {
    return `/v1/public/drivers/${encodeURIComponent(driverUsername0569)}/${encodeURIComponent(agendaToken0569)}/agenda`;
  }
  return "";
}

function applyAgendaBody0569(body) {
  publicDriverDisplayName0569 = String(body?.driver?.displayName || driverUsername0569 || "").trim();
  publicDriverWhatsapp0569 = String(body?.driver?.whatsapp || "").trim();
  syncWhatsappFab0569();
  renderAgenda0569(body?.trips);
}

async function loadAgenda0569(silent = false) {
  if (agendaLoadInFlight0569) return;
  if (!agendaViewToken0589) {
    showPassengerAccessGate0589("");
    return;
  }
  const endpoint = primaryEndpoint0569();
  if (!endpoint) {
    if (!silent) showError0569("Este link não identifica uma área Viagem Certa válida.");
    return;
  }
  agendaLoadInFlight0569 = true;
  try {
    showPassengerAgendaAccess0589();
    applyAgendaBody0569(await fetchJson0569(endpoint, 12000, {
      "X-Rota-Certa-Agenda-View-Token": agendaViewToken0589,
    }));
  } catch (primaryError) {
    if (primaryError?.status === 401 || primaryError?.status === 403) {
      clearPassengerAgendaAccess0589();
      showPassengerAccessGate0589(primaryError?.message || "Informe seu WhatsApp novamente para continuar.");
      return;
    }
    if (!silent) showError0569(primaryError?.message || "Não foi possível carregar as viagens.");
  } finally {
    agendaLoadInFlight0569 = false;
  }
}

initPassengerAccess0589();
window.setInterval(() => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda0569(true);
}, 2000);
window.addEventListener("online", () => loadAgenda0569(true));
window.addEventListener("pageshow", () => {
  if (navigator.onLine !== false) {
    consumeAgendaCardRefresh0596();
    loadAgenda0569(true);
  }
});
window.addEventListener("focus", () => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) {
    consumeAgendaCardRefresh0596();
  }
});
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) {
    consumeAgendaCardRefresh0596();
    loadAgenda0569(true);
  }
});