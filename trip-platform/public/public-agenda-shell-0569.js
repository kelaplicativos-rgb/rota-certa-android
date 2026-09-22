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

const sharedTripToken0623 = String(params0569.get("viagem") || "").replace(/[^A-Za-z0-9_-]/g, "");
const passengerSessionKey0623 = "rotaCertaPassengerSession0491:" + driverUsername0569;
const passengerContextKey0623 = "rotaCertaPassengerContext0491:" + driverUsername0569;
let passengerSessionToken0623 = sessionStorage.getItem(passengerSessionKey0623) || "";
let bookingSelection0623 = null;
let bookingSeats0623 = 1;
let phoneConfirmation0623 = null;
let recaptchaVerifier0623 = null;
let bookingBusy0623 = false;

function passengerSessionContext0623() {
  let value = sessionStorage.getItem(passengerContextKey0623) || "";
  if (!/^[A-Za-z0-9_-]{16,120}$/.test(value)) {
    value = crypto.randomUUID().replace(/-/g, "");
    sessionStorage.setItem(passengerContextKey0623, value);
  }
  return value;
}

function syncPassengerNav0623() {
  setVisible0569("passengerNav0589", Boolean(passengerSessionToken0623));
  configurePassengerAreaLink0589();
}

function normalizePhoneE1640623(raw) {
  const text = String(raw || "").trim();
  let digits = text.replace(/\D/g, "");
  if (text.startsWith("+")) {
    if (!/^[1-9]\d{7,14}$/.test(digits)) throw new Error("Confira o número do WhatsApp com DDD.");
    return "+" + digits;
  }
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) return "+" + digits;
  if (/^\d{10,11}$/.test(digits)) return "+55" + digits;
  throw new Error("Confira o número do WhatsApp com DDD.");
}

function setBookingStatus0623(message, kind = "") {
  const node = $0569("bookingStatus0623");
  if (!node) return;
  node.textContent = String(message || "");
  node.classList.toggle("hidden", !message);
  node.classList.toggle("error0623", kind === "error");
  node.classList.toggle("success0623", kind === "success");
}

function syncBookingSeatCount0623() {
  const node = $0569("bookingSeatCount0623");
  if (node) node.textContent = bookingSeats0623 === 1 ? "1 lugar" : bookingSeats0623 + " lugares";
  const max = Math.max(1, Number(bookingSelection0623?.availableSeats || 1));
  if ($0569("bookingSeatsMinus0623")) $0569("bookingSeatsMinus0623").disabled = bookingSeats0623 <= 1;
  if ($0569("bookingSeatsPlus0623")) $0569("bookingSeatsPlus0623").disabled = bookingSeats0623 >= max;
}

function bookingHelpHref0623() {
  const digits = whatsappDigits0569(publicDriverWhatsapp0569);
  if (!digits || !bookingSelection0623) return "";
  const text = "Olá! Estou tentando reservar pelo Viagem Certa o trecho " +
    bookingSelection0623.from + " → " + bookingSelection0623.to + " e preciso de ajuda.";
  return "https://wa.me/" + digits + "?text=" + encodeURIComponent(text);
}

function showBookingIdentityStep0623() {
  setVisible0569("bookingIdentityStep0623", true);
  setVisible0569("bookingOtpStep0623", false);
  phoneConfirmation0623 = null;
  if (recaptchaVerifier0623) {
    try { recaptchaVerifier0623.clear(); } catch (_) {}
    recaptchaVerifier0623 = null;
  }
}

function closeBooking0623() {
  if (bookingBusy0623) return;
  setVisible0569("bookingModal0623", false);
  document.body.style.overflow = "";
  showBookingIdentityStep0623();
  setBookingStatus0623("");
  bookingSelection0623 = null;
}

function openBooking0623(item, segment, stops, segmentIndex) {
  const tripToken = String(item?.tripId || item?.publicToken || "").trim();
  const fromStop = stops?.[segmentIndex];
  const toStop = stops?.[segmentIndex + 1];
  if (!tripToken || !fromStop?.id || !toStop?.id || Number(segment?.availableSeats || 0) < 1) {
    return;
  }
  bookingSelection0623 = {
    tripToken,
    from: String(segment.from || fromStop.name || "Origem").trim(),
    to: String(segment.to || toStop.name || "Destino").trim(),
    boardingStopId: String(fromStop.id),
    dropoffStopId: String(toStop.id),
    availableSeats: Math.max(1, Math.floor(Number(segment.availableSeats || 1))),
    departureAtMillis: Number(item?.departureAtMillis || 0),
    timezoneId: item?.timezoneId || "",
  };
  bookingSeats0623 = 1;
  const summary = $0569("bookingSummary0623");
  if (summary) {
    summary.textContent = bookingSelection0623.from + " → " + bookingSelection0623.to +
      " • " + dateLabel0569(bookingSelection0623.departureAtMillis, bookingSelection0623.timezoneId) +
      " • " + segmentAvailabilityLabel0580(bookingSelection0623.availableSeats);
  }
  syncBookingSeatCount0623();
  showBookingIdentityStep0623();
  setBookingStatus0623("");
  const help = $0569("bookingHelp0623");
  const helpHref = bookingHelpHref0623();
  if (help) {
    help.classList.toggle("hidden", !helpHref);
    if (helpHref) help.href = helpHref;
  }
  setVisible0569("bookingModal0623", true);
  document.body.style.overflow = "hidden";
  window.setTimeout(() => $0569("bookingName0623")?.focus(), 50);
}

function openFullTripBooking0623(item) {
  const stops = orderedStops0569(item);
  const rows = publicSegmentRows0580(item, stops);
  if (stops.length < 2 || !rows.length || rows.length !== stops.length - 1) return;
  const available = rows.reduce((minimum, row) => Math.min(minimum, row.availableSeats), Number.POSITIVE_INFINITY);
  if (!Number.isFinite(available) || available < 1) return;
  const full = { from: stops[0].name, to: stops[stops.length - 1].name, availableSeats: available };
  const tripToken = String(item?.tripId || item?.publicToken || "").trim();
  if (!tripToken || !stops[0]?.id || !stops[stops.length - 1]?.id) return;
  bookingSelection0623 = {
    tripToken,
    from: String(full.from || "Origem"),
    to: String(full.to || "Destino"),
    boardingStopId: String(stops[0].id),
    dropoffStopId: String(stops[stops.length - 1].id),
    availableSeats: available,
    departureAtMillis: Number(item?.departureAtMillis || 0),
    timezoneId: item?.timezoneId || "",
  };
  bookingSeats0623 = 1;
  const summary = $0569("bookingSummary0623");
  if (summary) summary.textContent = bookingSelection0623.from + " → " + bookingSelection0623.to +
    " • " + dateLabel0569(bookingSelection0623.departureAtMillis, bookingSelection0623.timezoneId) +
    " • " + segmentAvailabilityLabel0580(available);
  syncBookingSeatCount0623();
  showBookingIdentityStep0623();
  setBookingStatus0623("");
  const help = $0569("bookingHelp0623");
  const helpHref = bookingHelpHref0623();
  if (help) {
    help.classList.toggle("hidden", !helpHref);
    if (helpHref) help.href = helpHref;
  }
  setVisible0569("bookingModal0623", true);
  document.body.style.overflow = "hidden";
  window.setTimeout(() => $0569("bookingName0623")?.focus(), 50);
}

function setBookingBusy0623(busy) {
  bookingBusy0623 = Boolean(busy);
  ["bookingSendCode0623","bookingConfirm0623","bookingBack0623","bookingClose0623",
   "bookingSeatsMinus0623","bookingSeatsPlus0623"].forEach((id) => {
    const node = $0569(id);
    if (node) node.disabled = bookingBusy0623;
  });
  if (!bookingBusy0623) syncBookingSeatCount0623();
}

async function sendBookingCode0623() {
  if (bookingBusy0623 || !bookingSelection0623) return;
  const displayName = String($0569("bookingName0623")?.value || "").trim();
  if (displayName.length < 2) {
    setBookingStatus0623("Informe seu nome para continuar.", "error");
    $0569("bookingName0623")?.focus();
    return;
  }
  let phone;
  try { phone = normalizePhoneE1640623($0569("bookingPhone0623")?.value || ""); }
  catch (error) {
    setBookingStatus0623(error.message, "error");
    $0569("bookingPhone0623")?.focus();
    return;
  }
  if (!window.firebase?.auth) {
    setBookingStatus0623("A confirmação por telefone está temporariamente indisponível. Use “Preciso de ajuda pelo WhatsApp”.", "error");
    return;
  }

  setBookingBusy0623(true);
  setBookingStatus0623("Enviando o código de segurança…");
  try {
    if (recaptchaVerifier0623) {
      try { recaptchaVerifier0623.clear(); } catch (_) {}
    }
    firebase.auth().languageCode = "pt-br";
    recaptchaVerifier0623 = new firebase.auth.RecaptchaVerifier("bookingSendCode0623", { size: "invisible" });
    phoneConfirmation0623 = await firebase.auth().signInWithPhoneNumber(phone, recaptchaVerifier0623);
    setVisible0569("bookingIdentityStep0623", false);
    setVisible0569("bookingOtpStep0623", true);
    setBookingStatus0623("Código enviado. Digite os 6 números recebidos por SMS.");
    window.setTimeout(() => $0569("bookingOtp0623")?.focus(), 50);
  } catch (error) {
    if (recaptchaVerifier0623) {
      try { recaptchaVerifier0623.clear(); } catch (_) {}
      recaptchaVerifier0623 = null;
    }
    const code = String(error?.code || "");
    const message = code.includes("too-many-requests")
      ? "Muitas tentativas neste número. Aguarde um pouco antes de pedir outro código."
      : code.includes("invalid-phone-number")
        ? "Confira o número do WhatsApp com DDD."
        : "Não conseguimos enviar o código agora. Confira o número ou use a ajuda pelo WhatsApp.";
    setBookingStatus0623(message, "error");
  } finally {
    setBookingBusy0623(false);
  }
}

function bookingIdempotencyKey0623() {
  return "vc0623_" + (crypto.randomUUID ? crypto.randomUUID() : (Date.now() + "_" + Math.random().toString(36).slice(2)))
    .replace(/[^A-Za-z0-9_-]/g, "_");
}

async function confirmAndReserve0623() {
  if (bookingBusy0623 || !bookingSelection0623 || !phoneConfirmation0623) return;
  const code = String($0569("bookingOtp0623")?.value || "").replace(/\D/g, "");
  if (!/^\d{6}$/.test(code)) {
    setBookingStatus0623("Digite os 6 números do código recebido.", "error");
    $0569("bookingOtp0623")?.focus();
    return;
  }
  const displayName = String($0569("bookingName0623")?.value || "").trim();
  setBookingBusy0623(true);
  setBookingStatus0623("Confirmando seu telefone e guardando a vaga…");
  try {
    const credential = await phoneConfirmation0623.confirm(code);
    const firebaseIdToken = await credential.user.getIdToken(true);
    const sessionResponse = await fetch("/v1/public/passenger-phone-session", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: "Bearer " + firebaseIdToken },
      cache: "no-store",
      body: JSON.stringify({
        displayName,
        publicSlug: publicSlug0569,
        driverUsername: driverUsername0569,
        agendaToken: agendaToken0569,
        tripToken: bookingSelection0623.tripToken,
        sessionContextId: passengerSessionContext0623(),
      }),
    });
    const sessionBody = await sessionResponse.json().catch(() => ({}));
    if (!sessionResponse.ok) throw new Error(safeMessage0569(sessionBody?.message) || "Não foi possível confirmar seu acesso.");
    passengerSessionToken0623 = String(sessionBody.sessionToken || "");
    if (!/^[A-Za-z0-9_-]{32,200}$/.test(passengerSessionToken0623)) throw new Error("A confirmação do telefone não gerou um acesso válido.");
    sessionStorage.setItem(passengerSessionKey0623, passengerSessionToken0623);
    syncPassengerNav0623();

    const idempotencyKey = bookingIdempotencyKey0623();
    const bookingResponse = await fetch(
      "/v1/public/trips/" + encodeURIComponent(bookingSelection0623.tripToken) + "/bookings",
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
          Authorization: "Bearer " + passengerSessionToken0623,
        },
        cache: "no-store",
        body: JSON.stringify({
          boardingStopId: bookingSelection0623.boardingStopId,
          dropoffStopId: bookingSelection0623.dropoffStopId,
          seats: bookingSeats0623,
          creditToUseCents: 0,
          idempotencyKey,
          passengerName: displayName,
        }),
      },
    );
    const bookingBody = await bookingResponse.json().catch(() => ({}));
    if (!bookingResponse.ok) throw new Error(safeMessage0569(bookingBody?.message) || "Não foi possível solicitar a reserva.");

    setVisible0569("bookingOtpStep0623", false);
    setVisible0569("bookingIdentityStep0623", false);
    setBookingStatus0623(
      "✓ Pedido enviado. " +
      (bookingSeats0623 === 1 ? "Sua vaga está" : "Suas vagas estão") +
      " guardada" + (bookingSeats0623 === 1 ? "" : "s") +
      " enquanto o motorista confirma. Você receberá a resposta no Viagem Certa.",
      "success",
    );
    await loadAgenda0569(true);
    window.setTimeout(() => {
      if ($0569("bookingModal0623") && !$0569("bookingModal0623").classList.contains("hidden")) {
        setBookingBusy0623(false);
      }
    }, 250);
  } catch (error) {
    const codeText = String(error?.code || "");
    const message = codeText.includes("invalid-verification-code") || codeText.includes("code-expired")
      ? "Esse código não é válido ou expirou. Volte e solicite outro."
      : (error?.message || "Não foi possível concluir a reserva.");
    setBookingStatus0623(message, "error");
  } finally {
    setBookingBusy0623(false);
  }
}

function shareTripUrl0623(item) {
  const token = String(item?.tripId || item?.publicToken || "").trim();
  const url = new URL(location.href);
  if (token) url.searchParams.set("viagem", token);
  return url.toString();
}

async function shareTrip0623(item) {
  const stops = orderedStops0569(item);
  const from = String(stops[0]?.name || "Origem");
  const to = String(stops[stops.length - 1]?.name || "Destino");
  const url = shareTripUrl0623(item);
  const title = "Viagem Certa — " + from + " → " + to;
  const text = "Veja os detalhes e reserve seu lugar nesta viagem.";
  try {
    if (navigator.share) {
      await navigator.share({ title, text, url });
      return;
    }
    await navigator.clipboard.writeText(url);
    window.alert("Link da viagem copiado.");
  } catch (error) {
    if (error?.name !== "AbortError") {
      try {
        await navigator.clipboard.writeText(url);
        window.alert("Link da viagem copiado.");
      } catch (_) {}
    }
  }
}

function initSelfBooking0623() {
  setVisible0569("accessGate0589", false);
  syncPassengerNav0623();
  $0569("passengerAgendaLogout0589")?.addEventListener("click", () => {
    passengerSessionToken0623 = "";
    sessionStorage.removeItem(passengerSessionKey0623);
    syncPassengerNav0623();
  });
  $0569("bookingClose0623")?.addEventListener("click", closeBooking0623);
  $0569("bookingBack0623")?.addEventListener("click", () => {
    if (bookingBusy0623) return;
    showBookingIdentityStep0623();
    setBookingStatus0623("");
  });
  $0569("bookingSeatsMinus0623")?.addEventListener("click", () => {
    if (bookingBusy0623) return;
    bookingSeats0623 = Math.max(1, bookingSeats0623 - 1);
    syncBookingSeatCount0623();
  });
  $0569("bookingSeatsPlus0623")?.addEventListener("click", () => {
    if (bookingBusy0623) return;
    const max = Math.max(1, Number(bookingSelection0623?.availableSeats || 1));
    bookingSeats0623 = Math.min(max, bookingSeats0623 + 1);
    syncBookingSeatCount0623();
  });
  $0569("bookingSendCode0623")?.addEventListener("click", sendBookingCode0623);
  $0569("bookingConfirm0623")?.addEventListener("click", confirmAndReserve0623);
  $0569("bookingOtp0623")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") confirmAndReserve0623();
  });
  $0569("bookingModal0623")?.addEventListener("click", (event) => {
    if (event.target === $0569("bookingModal0623")) closeBooking0623();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$0569("bookingModal0623")?.classList.contains("hidden")) closeBooking0623();
  });
  setVisible0569("loading", true);
  loadAgenda0569(false);
}

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

    if (segment.availableSeats > 0) {
      const reserve = document.createElement("button");
      reserve.className = "agendaSegmentReserve0584";
      reserve.type = "button";
      reserve.textContent = "Reserve Já";
      reserve.setAttribute(
        "aria-label",
        `Reservar o trecho ${segment.from} para ${segment.to} no Viagem Certa`,
      );
      reserve.addEventListener("click", (event) => {
        event.stopPropagation();
        openBooking0623(item, segment, stops, segmentIndex);
      });
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
  card.className = "agendaTrip0569 agendaTripClickable0596";
  card.dataset.cardSurface = "public-shell-0569";
  card.dataset.tripToken0623 = String(item?.tripId || item?.publicToken || "");
  card.setAttribute("aria-label", `Viagem ${from} para ${to}. Toque para reservar.`);
  card.tabIndex = 0;
  const top = document.createElement("div");
  top.className = "agendaTop0569";
  const date = document.createElement("strong");
  date.className = "agendaDate0569";
  date.textContent = dateLabel0569(item?.departureAtMillis, item?.timezoneId);
  const topActions = document.createElement("div");
  topActions.className = "agendaTopActions0623";
  const driver = document.createElement("span");
  driver.className = "agendaDriver0569";
  driver.textContent = String(item?.blablaProfileName || "").trim() || publicDriverDisplayName0569 || driverUsername0569;
  const share = document.createElement("button");
  share.className = "agendaShare0623";
  share.type = "button";
  share.textContent = "↗";
  share.setAttribute("aria-label", `Compartilhar viagem ${from} para ${to}`);
  share.addEventListener("click", (event) => {
    event.stopPropagation();
    shareTrip0623(item);
  });
  topActions.append(driver, share);
  top.append(date, topActions);
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
    viewRide.textContent = "Ver anúncio na BlaBlaCar";
    viewRide.setAttribute("aria-label", `Ver anúncio da viagem ${from} para ${to} na BlaBlaCar`);
    viewRide.addEventListener("click", armAgendaCardRefresh0596);
    actions.appendChild(viewRide);
  } else {
    const unavailable = document.createElement("span");
    unavailable.className = "agendaViewRide0584 agendaViewRideUnavailable0584";
    unavailable.textContent = "Ver anúncio na BlaBlaCar";
    unavailable.setAttribute("aria-disabled", "true");
    actions.appendChild(unavailable);
  }
  card.appendChild(actions);
  card.addEventListener("click", (event) => {
    if (event.target?.closest?.("a,button")) return;
    openFullTripBooking0623(item);
  });
  card.addEventListener("keydown", (event) => {
    if ((event.key === "Enter" || event.key === " ") && !event.target?.closest?.("a,button")) {
      event.preventDefault();
      openFullTripBooking0623(item);
    }
  });
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
    if (sharedTripToken0623) {
      window.requestAnimationFrame(() => {
        const target = [...container.querySelectorAll("[data-trip-token-0623]")]
          .find((node) => node.dataset.tripToken0623 === sharedTripToken0623);
        if (target) {
          target.scrollIntoView({ behavior: "smooth", block: "center" });
          target.style.boxShadow = "0 0 0 4px #d9cff8";
          window.setTimeout(() => { target.style.boxShadow = ""; }, 2400);
        }
      });
    }
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
  const endpoint = primaryEndpoint0569();
  if (!endpoint) {
    if (!silent) showError0569("Este link não identifica uma área Viagem Certa válida.");
    return;
  }
  agendaLoadInFlight0569 = true;
  try {
    applyAgendaBody0569(await fetchJson0569(endpoint, 12000));
  } catch (primaryError) {
    if (!silent) showError0569(primaryError?.message || "Não foi possível carregar as viagens.");
  } finally {
    agendaLoadInFlight0569 = false;
  }
}

initSelfBooking0623();
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