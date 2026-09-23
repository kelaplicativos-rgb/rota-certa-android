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
const passengerSessionKey0625 = "viagemCertaPassengerSession0625";
const passengerLegacySessionKey0623 = "rotaCertaPassengerSession0491:" + driverUsername0569;
const passengerContextKey0623 = "rotaCertaPassengerContext0491:" + driverUsername0569;
let passengerSessionToken0623 = sessionStorage.getItem(passengerSessionKey0625) || sessionStorage.getItem(passengerLegacySessionKey0623) || "";
let passengerAuthenticated0626 = false;
let passengerSessionProbePromise0626 = null;
let bookingSelection0623 = null;
let bookingSeats0623 = 1;
let bookingBusy0623 = false;
let bookingStep0625 = "contact";
let bookingKnownPassenger0625 = false;
let bookingPasswordCreated0625 = false;
let bookingPhone0625 = "";
let bookingName0625 = "";

function passengerSessionContext0623() {
  let value = sessionStorage.getItem(passengerContextKey0623) || "";
  if (!/^[A-Za-z0-9_-]{16,120}$/.test(value)) {
    value = crypto.randomUUID().replace(/-/g, "");
    sessionStorage.setItem(passengerContextKey0623, value);
  }
  return value;
}

function passengerAuthHeaders0626() {
  return passengerSessionToken0623 ? { Authorization: "Bearer " + passengerSessionToken0623 } : {};
}

function syncPassengerNav0623() {
  setVisible0569("passengerNav0589", true);
  setVisible0569("passengerAgendaLogout0589", passengerAuthenticated0626);
  configurePassengerAreaLink0589();
}

async function probePassengerSession0626() {
  if (passengerSessionProbePromise0626) return passengerSessionProbePromise0626;
  passengerSessionProbePromise0626 = (async () => {
    try {
      const response = await fetch("/v1/passenger/me", {
        method: "GET",
        headers: { Accept: "application/json", ...passengerAuthHeaders0626() },
        credentials: "same-origin",
        cache: "no-store",
      });
      passengerAuthenticated0626 = response.ok;
      if (response.ok) {
        passengerSessionToken0623 = "";
        sessionStorage.removeItem(passengerSessionKey0625);
        sessionStorage.removeItem(passengerLegacySessionKey0623);
      } else if (response.status === 401) {
        passengerSessionToken0623 = "";
        sessionStorage.removeItem(passengerSessionKey0625);
        sessionStorage.removeItem(passengerLegacySessionKey0623);
      }
    } catch (_) {
      passengerAuthenticated0626 = Boolean(passengerSessionToken0623);
    }
    syncPassengerNav0623();
    return passengerAuthenticated0626;
  })().finally(() => { passengerSessionProbePromise0626 = null; });
  return passengerSessionProbePromise0626;
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

const BOOKING_STEP_IDS_0625 = {
  contact: "bookingContactStep0625",
  name: "bookingNameStep0625",
  password: "bookingPasswordStep0625",
  confirm: "bookingPasswordConfirmStep0625",
  seats: "bookingSeatsStep0625",
  review: "bookingReviewStep0625",
};

function bookingVisibleFlow0625() {
  if (passengerAuthenticated0626) return ["seats", "review"];
  if (bookingKnownPassenger0625 && bookingPasswordCreated0625) return ["contact", "password", "seats", "review"];
  if (bookingKnownPassenger0625) return ["contact", "password", "confirm", "seats", "review"];
  return ["contact", "name", "password", "confirm", "seats", "review"];
}

function showBookingStep0625(step) {
  bookingStep0625 = step;
  Object.entries(BOOKING_STEP_IDS_0625).forEach(([key, id]) => setVisible0569(id, key === step));
  const flow = bookingVisibleFlow0625();
  const position = Math.max(0, flow.indexOf(step));
  const progress = $0569("bookingProgress0625");
  if (progress) progress.textContent = (position + 1) + " de " + flow.length;
  setBookingStatus0623("");
  const focusByStep = {
    contact: "bookingPhone0623",
    name: "bookingName0623",
    password: "bookingPassword0625",
    confirm: "bookingPasswordConfirm0625",
  };
  const focusId = focusByStep[step];
  if (focusId) window.setTimeout(() => $0569(focusId)?.focus(), 40);
}

function previousBookingStep0625() {
  const flow = bookingVisibleFlow0625();
  const index = flow.indexOf(bookingStep0625);
  if (index > 0) showBookingStep0625(flow[index - 1]);
}

function syncBookingSeatCount0623() {
  const node = $0569("bookingSeatCount0623");
  if (node) node.textContent = bookingSeats0623 === 1 ? "1 lugar" : bookingSeats0623 + " lugares";
  const max = Math.max(1, Number(bookingSelection0623?.availableSeats || 1));
  if ($0569("bookingSeatsMinus0623")) $0569("bookingSeatsMinus0623").disabled = bookingBusy0623 || bookingSeats0623 <= 1;
  if ($0569("bookingSeatsPlus0623")) $0569("bookingSeatsPlus0623").disabled = bookingBusy0623 || bookingSeats0623 >= max;
}

function bookingHelpHref0623() {
  const digits = whatsappDigits0569(publicDriverWhatsapp0569);
  if (!digits || !bookingSelection0623) return "";
  const text = "Olá! Estou tentando reservar pelo Viagem Certa o trecho " +
    bookingSelection0623.from + " → " + bookingSelection0623.to + " e preciso de ajuda.";
  return "https://wa.me/" + digits + "?text=" + encodeURIComponent(text);
}

function resetBookingIdentity0625() {
  bookingKnownPassenger0625 = false;
  bookingPasswordCreated0625 = false;
  bookingPhone0625 = "";
  bookingName0625 = "";
  if ($0569("bookingPassword0625")) $0569("bookingPassword0625").value = "";
  if ($0569("bookingPasswordConfirm0625")) $0569("bookingPasswordConfirm0625").value = "";
}

function closeBooking0623() {
  if (bookingBusy0623) return;
  setVisible0569("bookingModal0623", false);
  document.body.style.overflow = "";
  setBookingStatus0623("");
  resetBookingIdentity0625();
  bookingSelection0623 = null;
}

function prepareBookingWizard0625() {
  bookingSeats0623 = 1;
  syncBookingSeatCount0623();
  if (passengerAuthenticated0626) showBookingStep0625("seats");
  else {
    resetBookingIdentity0625();
    showBookingStep0625("contact");
  }
}

function bookingSummaryText0625() {
  return bookingSelection0623.from + " → " + bookingSelection0623.to +
    " • " + dateLabel0569(bookingSelection0623.departureAtMillis, bookingSelection0623.timezoneId) +
    " • " + segmentAvailabilityLabel0580(bookingSelection0623.availableSeats);
}

function openBooking0623(item, segment, stops, segmentIndex) {
  const tripToken = String(item?.tripId || item?.publicToken || "").trim();
  const fromStop = stops?.[segmentIndex];
  const toStop = stops?.[segmentIndex + 1];
  if (!tripToken || !fromStop?.id || !toStop?.id || Number(segment?.availableSeats || 0) < 1) return;
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
  if ($0569("bookingSummary0623")) $0569("bookingSummary0623").textContent = bookingSummaryText0625();
  const help = $0569("bookingHelp0623");
  const helpHref = bookingHelpHref0623();
  if (help) {
    help.classList.toggle("hidden", !helpHref);
    if (helpHref) help.href = helpHref;
  }
  prepareBookingWizard0625();
  setVisible0569("bookingModal0623", true);
  document.body.style.overflow = "hidden";
}

function openFullTripBooking0623(item) {
  const stops = orderedStops0569(item);
  const rows = publicSegmentRows0580(item, stops);
  if (stops.length < 2 || !rows.length || rows.length !== stops.length - 1) return;
  const available = rows.reduce((minimum, row) => Math.min(minimum, row.availableSeats), Number.POSITIVE_INFINITY);
  if (!Number.isFinite(available) || available < 1) return;
  const tripToken = String(item?.tripId || item?.publicToken || "").trim();
  if (!tripToken || !stops[0]?.id || !stops[stops.length - 1]?.id) return;
  bookingSelection0623 = {
    tripToken,
    from: String(stops[0].name || "Origem"),
    to: String(stops[stops.length - 1].name || "Destino"),
    boardingStopId: String(stops[0].id),
    dropoffStopId: String(stops[stops.length - 1].id),
    availableSeats: available,
    departureAtMillis: Number(item?.departureAtMillis || 0),
    timezoneId: item?.timezoneId || "",
  };
  if ($0569("bookingSummary0623")) $0569("bookingSummary0623").textContent = bookingSummaryText0625();
  const help = $0569("bookingHelp0623");
  const helpHref = bookingHelpHref0623();
  if (help) {
    help.classList.toggle("hidden", !helpHref);
    if (helpHref) help.href = helpHref;
  }
  prepareBookingWizard0625();
  setVisible0569("bookingModal0623", true);
  document.body.style.overflow = "hidden";
}

function setBookingBusy0623(busy) {
  bookingBusy0623 = Boolean(busy);
  ["bookingContactContinue0625","bookingNameContinue0625","bookingPasswordContinue0625",
   "bookingPasswordConfirmContinue0625","bookingSeatsContinue0625","bookingConfirm0625",
   "bookingClose0623","bookingSeatsMinus0623","bookingSeatsPlus0623"].forEach((id) => {
    const node = $0569(id);
    if (node) node.disabled = bookingBusy0623;
  });
  document.querySelectorAll(".bookingBack0625").forEach((node) => { node.disabled = bookingBusy0623; });
  if (!bookingBusy0623) syncBookingSeatCount0623();
}

async function continueBookingContact0625() {
  if (bookingBusy0623 || !bookingSelection0623) return;
  let phone;
  try { phone = normalizePhoneE1640623($0569("bookingPhone0623")?.value || ""); }
  catch (error) {
    setBookingStatus0623(error.message, "error");
    return;
  }
  setBookingBusy0623(true);
  try {
    const response = await fetch("/v1/public/passenger-access/status", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      cache: "no-store",
      body: JSON.stringify({ passengerContact: phone }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(safeMessage0569(body?.message) || "Não foi possível localizar seu cadastro.");
    bookingPhone0625 = phone;
    bookingKnownPassenger0625 = body?.knownPassenger === true;
    bookingPasswordCreated0625 = body?.passwordCreated === true;
    const label = $0569("bookingPasswordLabel0625");
    if (label) label.firstChild.textContent = bookingPasswordCreated0625 ? "Digite sua senha " : "Crie sua senha ";
    showBookingStep0625(bookingKnownPassenger0625 ? "password" : "name");
  } catch (error) {
    setBookingStatus0623(error?.message || "Não foi possível continuar.", "error");
  } finally {
    setBookingBusy0623(false);
  }
}

function continueBookingName0625() {
  const name = String($0569("bookingName0623")?.value || "").trim();
  if (name.length < 2) return setBookingStatus0623("Informe seu nome para continuar.", "error");
  bookingName0625 = name;
  showBookingStep0625("password");
}

function continueBookingPassword0625() {
  const password = String($0569("bookingPassword0625")?.value || "").trim();
  if (!/^\d{4}$/.test(password)) return setBookingStatus0623("Sua senha precisa ter exatamente 4 números.", "error");
  if (bookingPasswordCreated0625) return showBookingStep0625("seats");
  showBookingStep0625("confirm");
}

function continueBookingPasswordConfirm0625() {
  const password = String($0569("bookingPassword0625")?.value || "").trim();
  const confirmation = String($0569("bookingPasswordConfirm0625")?.value || "").trim();
  if (!/^\d{4}$/.test(confirmation)) return setBookingStatus0623("Confirme sua senha de 4 números.", "error");
  if (password !== confirmation) return setBookingStatus0623("As duas senhas precisam ser iguais.", "error");
  showBookingStep0625("seats");
}

function continueBookingSeats0625() {
  const review = $0569("bookingReview0625");
  if (review) review.textContent = bookingSummaryText0625() + " • " + (bookingSeats0623 === 1 ? "1 lugar" : bookingSeats0623 + " lugares");
  showBookingStep0625("review");
}

async function ensurePassengerSession0625() {
  if (passengerAuthenticated0626) return true;
  const password = String($0569("bookingPassword0625")?.value || "").trim();
  const confirmation = String($0569("bookingPasswordConfirm0625")?.value || "").trim();
  const response = await fetch("/v1/public/passenger-password-session", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    cache: "no-store",
    credentials: "same-origin",
    body: JSON.stringify({
      passengerContact: bookingPhone0625,
      displayName: bookingName0625,
      password,
      passwordConfirmation: bookingPasswordCreated0625 ? undefined : confirmation,
      // 0.1.628: a trip-specific login must bind to the concrete trip token.
      // Sending a public slug at the same time can be stale after an alias change.
      publicSlug: bookingSelection0623.tripToken ? undefined : publicSlug0569,
      driverUsername: bookingSelection0623.tripToken ? undefined : driverUsername0569,
      agendaToken: bookingSelection0623.tripToken ? undefined : agendaToken0569,
      tripToken: bookingSelection0623.tripToken,
      sessionContextId: passengerSessionContext0623(),
    }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(safeMessage0569(body?.message) || "Não foi possível entrar com sua senha.");
  passengerSessionToken0623 = String(body?.sessionToken || "");
  if (!/^[A-Za-z0-9_-]{32,200}$/.test(passengerSessionToken0623)) throw new Error("A sessão do passageiro não foi criada.");
  passengerAuthenticated0626 = true;
  sessionStorage.removeItem(passengerSessionKey0625);
  sessionStorage.removeItem(passengerLegacySessionKey0623);
  syncPassengerNav0623();
  return true;
}

const bookingIntentStorageKey0629 = "viagemCertaBookingIntent0629";

function bookingIntentFingerprint0629() {
  if (!bookingSelection0623) return "";
  return [
    bookingSelection0623.tripToken,
    bookingSelection0623.boardingStopId,
    bookingSelection0623.dropoffStopId,
    String(bookingSeats0623),
  ].join("|");
}

function bookingIdempotencyKey0623() {
  const fingerprint = bookingIntentFingerprint0629();
  try {
    const saved = JSON.parse(sessionStorage.getItem(bookingIntentStorageKey0629) || "null");
    if (
      saved &&
      saved.fingerprint === fingerprint &&
      /^[A-Za-z0-9_-]{16,180}$/.test(String(saved.intentId || ""))
    ) return String(saved.intentId);
  } catch (_) {}
  const intentId = "vc0629_" +
    (crypto.randomUUID ? crypto.randomUUID() : (Date.now() + "_" + Math.random().toString(36).slice(2)))
      .replace(/[^A-Za-z0-9_-]/g, "_");
  try {
    sessionStorage.setItem(bookingIntentStorageKey0629, JSON.stringify({
      intentId,
      fingerprint,
      createdAtMillis: Date.now(),
    }));
  } catch (_) {}
  return intentId;
}

function clearBookingIntent0629(intentId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(bookingIntentStorageKey0629) || "null");
    if (!saved || !intentId || String(saved.intentId || "") === intentId) {
      sessionStorage.removeItem(bookingIntentStorageKey0629);
    }
  } catch (_) {
    sessionStorage.removeItem(bookingIntentStorageKey0629);
  }
}

function delayBooking0629(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function submitBookingIntent0629(idempotencyKey) {
  const response = await fetch(
    "/v1/public/trips/" + encodeURIComponent(bookingSelection0623.tripToken) + "/bookings",
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
        ...passengerAuthHeaders0626(),
      },
      credentials: "same-origin",
      cache: "no-store",
      body: JSON.stringify({
        boardingStopId: bookingSelection0623.boardingStopId,
        dropoffStopId: bookingSelection0623.dropoffStopId,
        seats: bookingSeats0623,
        creditToUseCents: 0,
        idempotencyKey,
        clientIntentId: idempotencyKey,
        passengerName: bookingName0625,
      }),
    },
  );
  return { response, body: await response.json().catch(() => ({})) };
}

async function reconcileBookingIntent0629(idempotencyKey) {
  const delays = [0, 350, 900];
  let lastError = null;
  for (const delay of delays) {
    if (delay) await delayBooking0629(delay);
    try {
      const response = await fetch(
        "/v1/passenger/me/booking-intents/" +
          encodeURIComponent(bookingSelection0623.tripToken) + "/" +
          encodeURIComponent(idempotencyKey),
        {
          method: "GET",
          headers: { Accept: "application/json", ...passengerAuthHeaders0626() },
          credentials: "same-origin",
          cache: "no-store",
        },
      );
      const body = await response.json().catch(() => ({}));
      if (response.ok && body?.found === true) return { found: true, body };
      if (response.status === 401) return { found: false, authExpired: true, body };
      if (response.status !== 404) lastError = new Error(safeMessage0569(body?.message) || "Falha ao conferir a reserva.");
    } catch (error) {
      lastError = error;
    }
  }
  return { found: false, error: lastError };
}

function showBookingSuccess0629(idempotencyKey) {
  Object.values(BOOKING_STEP_IDS_0625).forEach((id) => setVisible0569(id, false));
  setBookingStatus0623(
    "✓ Pedido enviado. " + (bookingSeats0623 === 1 ? "Sua vaga está guardada" : "Suas vagas estão guardadas") +
    " enquanto o motorista confirma.",
    "success",
  );
  if ($0569("bookingPassword0625")) $0569("bookingPassword0625").value = "";
  if ($0569("bookingPasswordConfirm0625")) $0569("bookingPasswordConfirm0625").value = "";
  clearBookingIntent0629(idempotencyKey);
  // 0.1.629: visual refresh is best-effort and cannot turn a committed booking into an error.
  void loadAgenda0569(true);
}

async function confirmBooking0625() {
  if (bookingBusy0623 || !bookingSelection0623) return;
  setBookingBusy0623(true);
  setBookingStatus0623("Guardando sua vaga…");
  let idempotencyKey = "";
  let transportAmbiguous = false;
  try {
    await ensurePassengerSession0625();
    idempotencyKey = bookingIdempotencyKey0623();

    let result;
    try {
      result = await submitBookingIntent0629(idempotencyKey);
    } catch (_) {
      transportAmbiguous = true;
      setBookingStatus0623("Confirmando sua reserva…");
      await delayBooking0629(350);
      try {
        // Same intent key: a retry can only converge to the original booking.
        result = await submitBookingIntent0629(idempotencyKey);
      } catch (_) {
        const reconciled = await reconcileBookingIntent0629(idempotencyKey);
        if (reconciled.found) {
          showBookingSuccess0629(idempotencyKey);
          return;
        }
        setBookingStatus0623(
          "Não foi possível confirmar o resultado agora. Tente novamente: a próxima tentativa continuará a mesma reserva, sem criar outra.",
          "error",
        );
        return;
      }
    }

    const { response, body } = result;
    if (!response.ok) {
      if (transportAmbiguous) {
        const reconciled = await reconcileBookingIntent0629(idempotencyKey);
        if (reconciled.found) {
          showBookingSuccess0629(idempotencyKey);
          return;
        }
      }
      if (response.status === 401) {
        passengerAuthenticated0626 = false;
        passengerSessionToken0623 = "";
        sessionStorage.removeItem(passengerSessionKey0625);
        sessionStorage.removeItem(passengerLegacySessionKey0623);
        syncPassengerNav0623();
        resetBookingIdentity0625();
        showBookingStep0625("contact");
        setBookingStatus0623(
          transportAmbiguous
            ? "Sua identificação expirou. Verifique Minha área antes de tentar novamente; a tentativa anterior será preservada."
            : "Sua identificação expirou. Entre novamente para continuar; nenhuma reserva foi enviada.",
          "error",
        );
        return;
      }
      throw new Error(safeMessage0569(body?.message) || "Não foi possível solicitar a reserva.");
    }

    showBookingSuccess0629(idempotencyKey);
  } catch (error) {
    setBookingStatus0623(error?.message || "Não foi possível confirmar a reserva.", "error");
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
  $0569("passengerAgendaLogout0589")?.addEventListener("click", async () => {
    try {
      await fetch("/v1/passenger/logout", {
        method: "POST",
        headers: { Accept: "application/json", ...passengerAuthHeaders0626() },
        credentials: "same-origin",
        cache: "no-store",
      });
    } catch (_) {}
    passengerAuthenticated0626 = false;
    passengerSessionToken0623 = "";
    sessionStorage.removeItem(passengerSessionKey0625);
    sessionStorage.removeItem(passengerLegacySessionKey0623);
    syncPassengerNav0623();
  });
  $0569("bookingClose0623")?.addEventListener("click", closeBooking0623);
  $0569("bookingContactContinue0625")?.addEventListener("click", continueBookingContact0625);
  $0569("bookingNameContinue0625")?.addEventListener("click", continueBookingName0625);
  $0569("bookingPasswordContinue0625")?.addEventListener("click", continueBookingPassword0625);
  $0569("bookingPasswordConfirmContinue0625")?.addEventListener("click", continueBookingPasswordConfirm0625);
  $0569("bookingSeatsContinue0625")?.addEventListener("click", continueBookingSeats0625);
  $0569("bookingConfirm0625")?.addEventListener("click", confirmBooking0625);
  document.querySelectorAll(".bookingBack0625").forEach((node) => node.addEventListener("click", previousBookingStep0625));
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
  [
    ["bookingPhone0623", continueBookingContact0625],
    ["bookingName0623", continueBookingName0625],
    ["bookingPassword0625", continueBookingPassword0625],
    ["bookingPasswordConfirm0625", continueBookingPasswordConfirm0625],
  ].forEach(([id, action]) => $0569(id)?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") action();
  }));
  $0569("bookingModal0623")?.addEventListener("click", (event) => {
    if (event.target === $0569("bookingModal0623")) closeBooking0623();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !$0569("bookingModal0623")?.classList.contains("hidden")) closeBooking0623();
  });
  setVisible0569("loading", true);
  probePassengerSession0626().finally(() => loadAgenda0569(false));
}

function setPassengerAccessMessage0589(message) {
  const node = $0569("passengerAccessMessage0589");
  if (!node) return;
  node.textContent = String(message || "");
  node.classList.toggle("hidden", !message);
}

function configurePassengerAreaLink0589() {
  const link = $0569("passengerAreaLink0589");
  if (!link) return;
  link.href = "/minha-area.html";
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
    const rawPassengerSeats = Math.max(0, Math.min(capacity, Math.floor(Number(segment?.passengerSeats || 0))));
    if (
      !from ||
      !to ||
      !Number.isFinite(Number(segment?.availableSeats)) ||
      !Number.isFinite(Number(segment?.passengerSeats))
    ) return null;
    // 0.1.632: a segment can never render "0/4" together with "LOTADO".
    // Availability remains the booking authority; occupancy is normalized to the
    // same capacity vector while the canonical change watcher fetches the new revision.
    const derivedPassengerSeats = Math.max(0, capacity - availableSeats);
    const passengerSeats = rawPassengerSeats + availableSeats === capacity
      ? rawPassengerSeats
      : derivedPassengerSeats;
    return {
      from,
      to,
      availableSeats,
      passengerSeats,
      capacity,
      projectionAdjusted0632: passengerSeats !== rawPassengerSeats,
    };
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

let agendaChangeCursor0632 = 0;
let agendaChangeWatchRunning0632 = false;

function applyAgendaBody0569(body) {
  publicDriverDisplayName0569 = String(body?.driver?.displayName || driverUsername0569 || "").trim();
  publicDriverWhatsapp0569 = String(body?.driver?.whatsapp || "").trim();
  agendaChangeCursor0632 = Math.max(
    agendaChangeCursor0632,
    Math.max(0, Number(body?.changeCursor0495 || 0)),
  );
  syncWhatsappFab0569();
  renderAgenda0569(body?.trips);
}

function agendaChangesEndpoint0632() {
  if (publicSlug0569) {
    return "/v1/public/agenda/" + encodeURIComponent(publicSlug0569) +
      "/changes?since=" + encodeURIComponent(String(agendaChangeCursor0632));
  }
  if (driverUsername0569 && agendaToken0569.length >= 16) {
    return "/v1/public/drivers/" + encodeURIComponent(driverUsername0569) + "/" +
      encodeURIComponent(agendaToken0569) + "/agenda/changes?since=" +
      encodeURIComponent(String(agendaChangeCursor0632));
  }
  return "";
}

async function fetchAgendaChange0632(endpoint) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 30_000);
  try {
    const response = await fetch(endpoint, {
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: controller.signal,
    });
    const raw = await response.text();
    let body = null;
    try { body = JSON.parse(raw); } catch (_) { body = null; }
    if (!response.ok || !body || typeof body !== "object") {
      throw new Error(safeMessage0569(body?.message) || "Falha no canal de atualização da Agenda.");
    }
    return body;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function watchAgendaCanonicalChanges0632() {
  if (agendaChangeWatchRunning0632) return;
  agendaChangeWatchRunning0632 = true;
  try {
    while (navigator.onLine !== false) {
      if (document.visibilityState !== "visible") {
        await delayBooking0629(700);
        continue;
      }
      const endpoint = agendaChangesEndpoint0632();
      if (!endpoint) return;
      try {
        const change = await fetchAgendaChange0632(endpoint);
        agendaChangeCursor0632 = Math.max(
          agendaChangeCursor0632,
          Math.max(0, Number(change?.cursor || 0)),
        );
        if (change?.changed === true) {
          await loadAgenda0569(true);
        } else if (change?.degraded === true) {
          await delayBooking0629(1200);
        }
      } catch (_) {
        // Long-poll is the primary path; the slower periodic refresh below is the
        // degradation path for temporary network/proxy failures.
        await delayBooking0629(1500);
      }
    }
  } finally {
    agendaChangeWatchRunning0632 = false;
  }
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
void watchAgendaCanonicalChanges0632();
window.setInterval(() => {
  if (document.visibilityState === "visible" && navigator.onLine !== false) loadAgenda0569(true);
}, 15_000);
window.addEventListener("online", () => {
  loadAgenda0569(true);
  void watchAgendaCanonicalChanges0632();
});
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