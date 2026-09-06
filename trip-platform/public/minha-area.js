"use strict";

const $ = (id) => document.getElementById(id);
const params0491 = new URLSearchParams(location.search);

function normalizeDriver0491(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

const driverUsername0491 = normalizeDriver0491(params0491.get("motorista") || "");
const sessionKey0491 = "rotaCertaPassengerSession0491:" + driverUsername0491;
const contextKey0491 = "rotaCertaPassengerContext0491:" + driverUsername0491;
let sessionToken0491 = sessionStorage.getItem(sessionKey0491) || "";
let refreshInFlight0491 = false;
let pollHandle0491 = 0;

function show0491(id, visible = true) {
  const node = $(id);
  if (node) node.classList.toggle("hidden", !visible);
}

function message0491(id, text, success = false) {
  const node = $(id);
  if (!node) return;
  node.textContent = text || "";
  node.classList.toggle("hidden", !text);
  node.classList.toggle("success", Boolean(text && success));
  node.classList.toggle("error", Boolean(text && !success));
}

function sessionContext0491() {
  let value = sessionStorage.getItem(contextKey0491) || "";
  if (!/^[A-Za-z0-9_-]{16,120}$/.test(value)) {
    value = crypto.randomUUID().replace(/-/g, "");
    sessionStorage.setItem(contextKey0491, value);
  }
  return value;
}

async function request0491(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (sessionToken0491) headers.Authorization = "Bearer " + sessionToken0491;
  let body = options.body;
  if (body && typeof body !== "string") {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  const response = await fetch(path, {
    ...options,
    headers,
    body,
    cache: "no-store",
  });
  let payload = {};
  try { payload = await response.json(); } catch (_) {}
  if (!response.ok) {
    const error = new Error(payload.message || "Não foi possível concluir esta operação.");
    error.status = response.status;
    error.code = payload.code || "";
    throw error;
  }
  return payload;
}

function formatDateTime0491(ms) {
  const value = Number(ms || 0);
  if (!value) return "";
  return new Intl.DateTimeFormat("pt-BR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatShortTime0491(ms) {
  const value = Number(ms || 0);
  if (!value) return "";
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function bookingStatusLabel0491(booking) {
  const operational = String(booking?.operationalStatus || "").toUpperCase();
  const status = String(booking?.status || "").toUpperCase();
  const payment = String(booking?.paymentStatus || "").toUpperCase();
  if (status === "CANCELLED" || operational === "CANCELLED") return "Cancelada";
  if (operational === "COMPLETED") return "Concluída";
  if (operational === "IN_CAR") return "Em viagem";
  if (operational === "AT_LOCATION") return "Motorista no local";
  if (payment === "PAID") return "Pagamento confirmado";
  if (status === "REQUESTED" || operational === "PENDING") return "Aguardando confirmação";
  return "Confirmada";
}

function isHistorical0491(entry) {
  const status = String(entry?.booking?.status || "").toUpperCase();
  const operational = String(entry?.booking?.operationalStatus || "").toUpperCase();
  const tripStatus = String(entry?.trip?.status || "").toUpperCase();
  return status === "CANCELLED" ||
    operational === "CANCELLED" ||
    operational === "COMPLETED" ||
    tripStatus === "CANCELLED" ||
    Number(entry?.trip?.departureAtMillis || 0) < Date.now() - 6 * 60 * 60 * 1000;
}

function renderBooking0491(entry) {
  const trip = entry?.trip || {};
  const booking = entry?.booking || {};
  const card = document.createElement("article");
  card.className = "trip";

  const date = document.createElement("div");
  date.className = "tripDate";
  date.textContent = formatDateTime0491(trip.departureAtMillis) || "Data em atualização";

  const route = document.createElement("div");
  route.className = "route";
  const from = String(booking.boarding || "").trim();
  const to = String(booking.dropoff || "").trim();
  route.textContent = from && to ? from + " → " + to : String(trip.title || "Sua viagem");

  const facts = document.createElement("div");
  facts.className = "facts";
  const seats = Math.max(0, Number(booking.seats || 0));
  const seatLine = document.createElement("span");
  seatLine.textContent = seats === 1 ? "1 lugar" : seats + " lugares";
  facts.appendChild(seatLine);

  const status = document.createElement("span");
  status.className = "pill";
  status.textContent = bookingStatusLabel0491(booking);

  card.append(date, route, facts, status);
  return card;
}

function renderBookings0491(entries) {
  const upcoming = $("upcoming0491");
  const history = $("history0491");
  upcoming.innerHTML = "";
  history.innerHTML = "";

  const active = entries.filter((entry) => !isHistorical0491(entry))
    .sort((a, b) => Number(a?.trip?.departureAtMillis || 0) - Number(b?.trip?.departureAtMillis || 0));
  const past = entries.filter(isHistorical0491)
    .sort((a, b) => Number(b?.trip?.departureAtMillis || 0) - Number(a?.trip?.departureAtMillis || 0));

  const renderList = (root, list, emptyText) => {
    if (!list.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = emptyText;
      root.appendChild(empty);
      return;
    }
    list.forEach((entry) => root.appendChild(renderBooking0491(entry)));
  };

  renderList(upcoming, active, "Nenhuma próxima viagem.");
  renderList(history, past, "Nenhuma viagem anterior.");
}

function renderNotifications0491(items, unreadCount) {
  const root = $("notifications0491");
  root.innerHTML = "";
  const visible = Array.isArray(items) ? items.slice(0, 30) : [];

  if (!visible.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "Nenhuma atualização.";
    root.appendChild(empty);
  } else {
    visible.forEach((item) => {
      const notice = document.createElement("article");
      notice.className = item?.read ? "notice" : "notice noticeUnread";

      const title = document.createElement("div");
      title.className = "noticeTitle";
      title.textContent = String(item?.title || "Atualização da viagem");

      const body = document.createElement("div");
      body.textContent = String(item?.message || "");

      const time = document.createElement("div");
      time.className = "noticeTime";
      time.textContent = formatShortTime0491(item?.createdAtMillis);

      notice.append(title, body, time);
      root.appendChild(notice);
    });
  }

  $("markRead0491").classList.toggle("hidden", Number(unreadCount || 0) <= 0);
}

function enterPrivateMode0491() {
  show0491("loginPanel0491", false);
  show0491("privatePanel0491", true);
}

function leavePrivateMode0491() {
  sessionToken0491 = "";
  sessionStorage.removeItem(sessionKey0491);
  show0491("privatePanel0491", false);
  show0491("loginPanel0491", true);
  if (pollHandle0491) window.clearInterval(pollHandle0491);
  pollHandle0491 = 0;
}

async function refreshPrivateArea0491(silent = false) {
  if (!sessionToken0491 || refreshInFlight0491) return;
  refreshInFlight0491 = true;
  const scoped = "?driverUsername=" + encodeURIComponent(driverUsername0491);
  try {
    const [me, bookings, notifications] = await Promise.all([
      request0491("/v1/passenger/me" + scoped),
      request0491("/v1/passenger/me/bookings" + scoped),
      request0491("/v1/passenger/me/notifications" + scoped),
    ]);
    enterPrivateMode0491();
    show0491("passwordPanel0491", me?.mustChangePassword === true);
    renderBookings0491(Array.isArray(bookings?.bookings) ? bookings.bookings : []);
    renderNotifications0491(notifications?.notifications || [], notifications?.unreadCount || 0);
    $("refreshMessage0491").textContent = "Atualizado às " + new Intl.DateTimeFormat("pt-BR", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date());

    if (!pollHandle0491) {
      pollHandle0491 = window.setInterval(() => {
        if (document.visibilityState === "visible" && navigator.onLine !== false) {
          refreshPrivateArea0491(true);
        }
      }, 10000);
    }
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      leavePrivateMode0491();
      if (!silent) message0491("loginMessage0491", error.message || "Entre novamente.");
    } else if (!silent) {
      $("refreshMessage0491").textContent = error.message || "Atualização temporariamente indisponível.";
    }
  } finally {
    refreshInFlight0491 = false;
  }
}

async function login0491() {
  message0491("loginMessage0491", "");
  const contact = $("contact0491").value.trim();
  const password = $("password0491").value;
  if (!contact || password.length < 8) {
    return message0491("loginMessage0491", "Informe seu telefone/WhatsApp e a senha.");
  }

  $("login0491").disabled = true;
  try {
    const result = await request0491("/v1/passenger/session", {
      method: "POST",
      body: {
        passengerContact: contact,
        password,
        driverUsername: driverUsername0491,
        sessionContextId: sessionContext0491(),
      },
    });
    sessionToken0491 = String(result?.sessionToken || "");
    if (!sessionToken0491) throw new Error("Sessão não recebida.");
    sessionStorage.setItem(sessionKey0491, sessionToken0491);
    $("password0491").value = "";
    await refreshPrivateArea0491(false);
  } catch (error) {
    message0491("loginMessage0491", error.message || "Não foi possível entrar.");
  } finally {
    $("login0491").disabled = false;
  }
}

async function changePassword0491() {
  message0491("passwordMessage0491", "");
  const password = $("newPassword0491").value;
  const confirmation = $("newPasswordConfirm0491").value;
  if (password.length < 8 || password !== confirmation) {
    return message0491("passwordMessage0491", "Use pelo menos 8 caracteres e confirme a mesma senha.");
  }

  $("changePassword0491").disabled = true;
  try {
    await request0491("/v1/passenger/me/password", {
      method: "POST",
      body: { password },
    });
    $("newPassword0491").value = "";
    $("newPasswordConfirm0491").value = "";
    show0491("passwordPanel0491", false);
  } catch (error) {
    message0491("passwordMessage0491", error.message || "Não foi possível alterar a senha.");
  } finally {
    $("changePassword0491").disabled = false;
  }
}

async function markRead0491() {
  try {
    await request0491(
      "/v1/passenger/me/notifications/read-all?driverUsername=" + encodeURIComponent(driverUsername0491),
      { method: "POST" },
    );
    await refreshPrivateArea0491(true);
  } catch (_) {}
}

async function logout0491() {
  const scoped = "?driverUsername=" + encodeURIComponent(driverUsername0491);
  try {
    await request0491("/v1/passenger/logout" + scoped, {
      method: "POST",
      body: { driverUsername: driverUsername0491 },
    });
  } catch (_) {}
  leavePrivateMode0491();
}

function init0491() {
  if (driverUsername0491.length < 3) {
    $("contextError0491").textContent = "Abra Minha Área a partir da Agenda Pública.";
    show0491("contextError0491", true);
    show0491("loginPanel0491", false);
    return;
  }

  $("backToAgenda0491").href = "/" + encodeURIComponent(driverUsername0491);
  $("login0491").addEventListener("click", login0491);
  $("password0491").addEventListener("keydown", (event) => {
    if (event.key === "Enter") login0491();
  });
  $("changePassword0491").addEventListener("click", changePassword0491);
  $("markRead0491").addEventListener("click", markRead0491);
  $("logout0491").addEventListener("click", logout0491);
  window.addEventListener("online", () => refreshPrivateArea0491(true));
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && navigator.onLine !== false) {
      refreshPrivateArea0491(true);
    }
  });

  if (sessionToken0491) refreshPrivateArea0491(false);
}

init0491();
