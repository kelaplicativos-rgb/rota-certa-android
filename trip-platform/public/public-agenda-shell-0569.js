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
  return `${weekdays[parts.weekday]} ${String(parts.day).padStart(2, "0")} ${months[parts.month]}`;
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
    if (!from || !to || !Number.isFinite(Number(segment?.availableSeats))) return null;
    return { from, to, availableSeats, capacity };
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

  rows.forEach((segment) => {
    const row = document.createElement("div");
    row.className = "agendaSegmentRow0580";

    const route = document.createElement("span");
    route.className = "agendaSegmentRoute0580";
    route.textContent = segment.from + " → " + segment.to;

    const dots = document.createElement("span");
    dots.className = "agendaSegmentDots0580";
    dots.setAttribute("aria-hidden", "true");
    dots.textContent = segmentSeatDots0580(segment.capacity, segment.availableSeats);

    const seats = document.createElement("strong");
    seats.className = "agendaSegmentSeats0580";
    seats.textContent = segmentAvailabilityLabel0580(segment.availableSeats);

    row.append(route, dots, seats);
    section.appendChild(row);
  });
  card.appendChild(section);
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
  const card = document.createElement(publicUrl ? "a" : "article");
  card.className = "agendaTrip0569";
  card.dataset.cardSurface = "public-shell-0569";
  if (publicUrl) {
    card.href = publicUrl;
    card.rel = "noopener noreferrer";
    card.setAttribute("aria-label", `Abrir viagem ${from} para ${to} na BlaBlaCar`);
  } else {
    card.setAttribute("aria-disabled", "true");
    card.setAttribute("aria-label", `Viagem ${from} para ${to}`);
  }
  const top = document.createElement("div");
  top.className = "agendaTop0569";
  const date = document.createElement("strong");
  date.className = "agendaDate0569";
  date.textContent = dateLabel0569(item?.departureAtMillis, item?.timezoneId);
  const driver = document.createElement("span");
  driver.className = "agendaDriver0569";
  driver.textContent = publicDriverDisplayName0569 || driverUsername0569;
  top.append(date, driver);
  card.appendChild(top);
  appendJourney0569(card, item, firstStop, lastStop);
  appendSegmentAvailability0580(card, item, stops);
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

async function fetchJson0569(url, timeoutMillis = 12000) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMillis);
  try {
    const response = await fetch(url, { headers: { Accept: "application/json" }, cache: "no-store", signal: controller.signal });
    const raw = await response.text();
    let body = null;
    try { body = JSON.parse(raw); } catch (_) { body = null; }
    if (!response.ok) throw new Error(safeMessage0569(body?.message) || `Agenda temporariamente indisponível (HTTP ${response.status}).`);
    if (!body || typeof body !== "object" || !Array.isArray(body.trips)) throw new Error("Agenda temporariamente indisponível: resposta inválida.");
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

function fallbackEndpoint0569() {
  const slug = normalizePublicSlug0569(publicSlug0569 || driverUsername0569);
  if (slug.length < 3 || RESERVED_PUBLIC_SLUGS_0569.has(slug)) return "";
  return `/__agenda_fallback/${encodeURIComponent(slug)}.json?ts=${Date.now()}`;
}

function applyAgendaBody0569(body) {
  publicDriverDisplayName0569 = String(body?.driver?.displayName || driverUsername0569 || "").trim();
  publicDriverWhatsapp0569 = String(body?.driver?.whatsapp || "").trim();
  syncWhatsappFab0569();
  renderAgenda0569(body?.trips);
}

async function loadAgenda0569(silent = false) {
  if (agendaLoadInFlight0569) return;
  const endpoint = primaryEndpoint0569();
  if (!endpoint) {
    if (!silent) showError0569("Este link não identifica uma Agenda de Viagens válida.");
    return;
  }
  agendaLoadInFlight0569 = true;
  try {
    applyAgendaBody0569(await fetchJson0569(endpoint));
  } catch (primaryError) {
    const fallback = fallbackEndpoint0569();
    if (fallback) {
      try {
        applyAgendaBody0569(await fetchJson0569(fallback, 8000));
        return;
      } catch (_) {}
    }
    if (!silent) showError0569(primaryError?.message || "Não foi possível carregar as viagens.");
  } finally {
    agendaLoadInFlight0569 = false;
  }
}

loadAgenda0569(false);
window.setInterval(() => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda0569(true);
}, 15000);
window.addEventListener("online", () => loadAgenda0569(true));
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda0569(true);
});