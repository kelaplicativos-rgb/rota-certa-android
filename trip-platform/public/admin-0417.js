"use strict";

(() => {
  const byId = (id) => document.getElementById(id);
  const section = byId("agendaAdmin0417");
  const entry = byId("openAgendaAdmin0418");
  if (!section || !entry) return;
  if (globalThis.__rotaCertaAgendaAdminBound0427) return;
  globalThis.__rotaCertaAgendaAdminBound0427 = true;

  function slug0417() {
    const parts = location.pathname.split("/").filter(Boolean);
    if (parts.length !== 1) return "";
    try {
      return decodeURIComponent(parts[0]).normalize("NFD").replace(/[\u0300-\u036f]/g, "")
        .toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 32);
    } catch (_) { return ""; }
  }

  const driverUsername = slug0417();
  if (!driverUsername || driverUsername === "v1" || driverUsername === "calendar") {
    entry.classList.add("hidden");
    return;
  }

  function passengerSessionToken0418() {
    try { return sessionStorage.getItem("rotacerta-passenger-session") || ""; } catch (_) { return ""; }
  }
  let currentTrips = [];
  let currentSelectedTrip0465 = null;
  let currentContextTrip0470 = null;
  let contextReturn0470 = { source: "global", returnScrollY: 0 };
  let contextPollTimer0470 = null;
  let contextHistoryPushed0470 = false;
  let currentSettings = null;
  const adminInFlight0427 = new Set();
  let dashboardLoadPromise0427 = null;

  function newAdminOperationId0427(kind) {
    const suffix = (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function")
      ? globalThis.crypto.randomUUID()
      : Date.now().toString(36) + "_" + Math.random().toString(36).slice(2, 18);
    return String(kind || "admin").replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 24) + "_" + suffix;
  }

  async function runAdminAction0427(key, button, action) {
    if (adminInFlight0427.has(key)) return;
    adminInFlight0427.add(key);
    if (button) button.disabled = true;
    try {
      return await action();
    } finally {
      adminInFlight0427.delete(key);
      if (button) button.disabled = false;
    }
  }

  function hidePublicSections0417() {
    document.querySelectorAll("main > section").forEach((node) => node.classList.add("hidden"));
    const loading = byId("loading");
    const error = byId("error");
    if (loading) loading.classList.add("hidden");
    if (error) error.classList.add("hidden");
    section.classList.remove("hidden");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function leaveAdmin0417() {
    section.classList.add("hidden");
    const portal = byId("openPassengerPortal");
    if (portal) portal.click();
    else location.reload();
  }

  async function api0417(path, options = {}) {
    const token = passengerSessionToken0418();
    const { operationId, ...fetchOptions } = options;
    const headers = {
      "Content-Type": "application/json",
      ...(token ? { "Authorization": "Bearer " + token } : {}),
      "X-Rota-Certa-Admin-Driver": driverUsername,
      ...(operationId ? { "X-Rota-Certa-Operation-Id": operationId } : {}),
      ...(fetchOptions.headers || {}),
    };
    const response = await fetch(path, { ...fetchOptions, headers });
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : await response.text();
    if (!response.ok) {
      const message = body && typeof body === "object" ? body.message : String(body || "Falha na operação.");
      const error = new Error(message);
      error.status = response.status;
      error.code = body && typeof body === "object" ? String(body.error || "") : "";
      error.body = body;
      throw error;
    }
    return body;
  }

  function fmt0417(ms) {
    const value = Number(ms || 0);
    if (!value) return "—";
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value));
  }

  function stateClass0417(state) {
    if (state === "VERIFIED") return "adminStateBlue0417";
    if (state === "PUBLISHED") return "adminStateGreen0465";
    if (state === "PENDING") return "adminStateOrange0417";
    if (state === "DIVERGENT" || state === "ERROR") return "adminStateRed0417";
    return "adminStateGray0417";
  }

  function setMessage0417(text, error = false) {
    const node = byId("adminMessage0417");
    node.textContent = text || "";
    node.className = error ? "error" : "muted";
  }

  function renderOverview0417(data) {
    const counts = data.counts || {};
    byId("adminHealthGrid0417").innerHTML = [
      ["MATCH confirmado", counts.verified || 0, "adminStateBlue0417"],
      ["Publicado • link pendente", counts.published || 0, "adminStateGreen0465"],
      ["Pendente", counts.pending || 0, "adminStateOrange0417"],
      ["Divergente", counts.divergent || 0, "adminStateRed0417"],
      ["Não verificado", counts.unproven || 0, "adminStateGray0417"],
      ["Links válidos", counts.linksValid || 0, ""],
      ["Links pendentes", counts.linksPending || 0, ""],
    ].map(([label, value, cls]) =>
      '<div class="adminMetric0417 '+cls+'"><strong>'+value+'</strong><span>'+label+'</span></div>'
    ).join("");
    const sync = data.lastSync || {};
    byId("adminSyncSummary0417").textContent =
      "Última sincronização: " + fmt0417(sync.finishedAtMillis) +
      " • " + (sync.result || "UNKNOWN") +
      " • alterados " + Number(sync.changed || 0) +
      " • ignorados comprovados " + Number(sync.skipped || 0) +
      " • falhas " + Number(sync.failures || 0) +
      (sync.correlationId ? " • operação " + sync.correlationId : "");
  }

  function renderTrips0417(trips) {
    currentTrips = Array.isArray(trips) ? trips : [];
    const host = byId("adminTrips0417");
    if (!currentTrips.length) {
      host.innerHTML = '<p class="muted">Nenhuma viagem encontrada.</p>';
      return;
    }
    host.innerHTML = currentTrips.map((trip) => {
      const identity = trip.blablaTripId || trip.canonicalTripId || trip.remoteTripId;
      const blablaLinkValid = Boolean(trip.blablaPublicUrl);
      return '<button class="adminTrip0417" type="button" data-trip="'+encodeURIComponent(trip.remoteTripId)+'">' +
        '<span class="adminStateDot0417 '+stateClass0417(trip.attestationState)+'">●</span>' +
        '<span><strong>'+escapeHtml0417(trip.title || identity)+'</strong>' +
        '<small>'+escapeHtml0417(identity)+' • '+fmt0417(trip.departureAtMillis)+'</small>' +
        '<small>'+escapeHtml0417(trip.attestationState || "UNPROVEN")+
          (trip.blablaTripId ? (blablaLinkValid ? " • link BlaBlaCar válido" : " • link BlaBlaCar pendente") : "")+
          '</small></span></button>';
    }).join("");
    host.querySelectorAll("[data-trip]").forEach((button) => {
      button.addEventListener("click", () => {
        const remoteTripId = decodeURIComponent(button.dataset.trip || "");
        const trip = currentTrips.find((item) => item.remoteTripId === remoteTripId);
        const canonicalTripId = trip && (trip.canonicalTripId || trip.remoteTripId);
        if (canonicalTripId) openTripAdminContext0470(canonicalTripId, { source: "global", returnScrollY: window.scrollY });
      });
    });
  }

  function stateLabel0470(state) {
    return ({
      VERIFIED: "🔵 Validada",
      PUBLISHED: "🟢 Publicada",
      PENDING: "🟠 Sincronizando / pendente",
      DIVERGENT: "🔴 Divergente",
      ERROR: "🔴 Erro",
      UNPROVEN: "⚪ Não comprovada",
    })[String(state || "UNPROVEN").toUpperCase()] || "⚪ Não comprovada";
  }

  function humanAuditLabel0470(event) {
    const key = String(event && (event.eventType || event.event) || "").toUpperCase();
    if (key.includes("BLABLACAR_PUBLIC_URL")) return "URL pública BlaBlaCar atualizada";
    if (key.includes("ATTEST")) return "Atestação pública atualizada";
    if (key.includes("PUBLICATION")) return "Publicação da viagem atualizada";
    if (key.includes("BOOKING") && key.includes("APPROV")) return "Reserva aprovada";
    if (key.includes("BOOKING") && key.includes("REJECT")) return "Reserva recusada";
    if (key.includes("PASSENGER") || key.includes("OPERATIONAL")) return "Estado do passageiro atualizado";
    return String(event && (event.eventType || event.event || event.category) || "Evento da viagem");
  }

  function renderTripContext0470(trip) {
    currentContextTrip0470 = trip || null;
    if (!trip) return;
    const stops = Array.isArray(trip.stops) ? trip.stops : [];
    const from = String(stops[0] && stops[0].name || "").trim();
    const to = String(stops[stops.length - 1] && stops[stops.length - 1].name || "").trim();
    byId("adminTripContextTitle0470").textContent = from && to
      ? from + " → " + to
      : (trip.title || "Administrar esta viagem");
    byId("adminTripContextMeta0470").textContent =
      fmt0417(trip.departureAtMillis) + " • " + String(trip.status || "—");
    byId("adminTripContextStatus0470").textContent = stateLabel0470(trip.attestationState);
    byId("adminTripContextIdentity0470").textContent =
      "Identidade canônica: " + String(trip.canonicalTripId || "—") +
      " • revisão " + Number(trip.canonicalRevision || 0);
    const facts = [
      ["Capacidade", Number(trip.capacity || 0)],
      ["Disponibilidade", Number(trip.availableSeatsMinimum || 0) === Number(trip.availableSeatsMaximum || 0)
        ? Number(trip.availableSeatsMaximum || 0) + " vaga(s)"
        : Number(trip.availableSeatsMinimum || 0) + "–" + Number(trip.availableSeatsMaximum || 0) + " vaga(s)"],
      ["Publicação", "revisão " + Number(trip.publicationRevision || 0)],
      ["Última alteração", fmt0417(trip.updatedAtMillis)],
    ];
    byId("adminTripContextFacts0470").innerHTML = facts.map(([label, value]) =>
      '<div class="adminContextFact0470"><small>'+escapeHtml0417(label)+'</small><strong>'+escapeHtml0417(value)+'</strong></div>'
    ).join("");
    selectTripPublicUrlEditor0465(trip);
  }

  function escapeHtml0417(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  function bookingStatusLabel0468(value) {
    const normalized = String(value || "").toUpperCase();
    return ({
      REQUESTED: "AGUARDANDO APROVAÇÃO",
      HELD: "PENDENTE",
      CONFIRMED: "CONFIRMADO",
      CANCELLED: "CANCELADO",
      REJECTED: "RECUSADO",
      EXPIRED: "EXPIRADO",
      AT_LOCATION: "NO LOCAL",
      IN_CAR: "NO CARRO",
      COMPLETED: "CONCLUÍDO",
      PAID: "PAGO",
      UNPAID: "PAGAMENTO PENDENTE",
    })[normalized] || normalized || "—";
  }

  function nextOperational0468(booking) {
    const operational = String(booking && booking.operationalStatus || "CONFIRMED").toUpperCase();
    const payment = String(booking && booking.paymentStatus || "UNPAID").toUpperCase();
    if (operational === "CONFIRMED") return ["AT_LOCATION", "NO LOCAL"];
    if (operational === "AT_LOCATION") return ["IN_CAR", "NO CARRO"];
    if (operational === "IN_CAR" && payment !== "PAID") return ["PAID", "PAGO"];
    if (operational === "IN_CAR" && payment === "PAID") return ["COMPLETED", "CONCLUÍDO"];
    return null;
  }

  function renderAdminTripBookings0468(tripId, bookings) {
    const host = byId("adminTripBookings0468");
    const list = Array.isArray(bookings) ? bookings : [];
    if (!host) return;
    if (!list.length) {
      host.innerHTML = '<p class="muted">Nenhum passageiro ou reserva nesta viagem.</p>';
      return;
    }
    host.innerHTML = list.map((booking) => {
      const id = encodeURIComponent(String(booking.id || ""));
      const status = String(booking.status || "").toUpperCase();
      const operational = String(booking.operationalStatus || "CONFIRMED").toUpperCase();
      const payment = String(booking.paymentStatus || "UNPAID").toUpperCase();
      const requested = status === "REQUESTED";
      const inactive = ["CANCELLED", "REJECTED", "EXPIRED"].includes(status);
      const next = !requested && !inactive ? nextOperational0468(booking) : null;
      const canCancel = !requested && !inactive && operational !== "IN_CAR" && operational !== "COMPLETED";
      const actions = [];
      if (requested) {
        actions.push('<button type="button" class="primary" data-admin-decision0468="APPROVE" data-booking0468="'+id+'">Aprovar</button>');
        actions.push('<button type="button" class="secondary" data-admin-decision0468="REJECT" data-booking0468="'+id+'">Recusar</button>');
      } else if (next) {
        actions.push('<button type="button" class="primary" data-admin-operational0468="'+next[0]+'" data-booking0468="'+id+'">'+next[1]+'</button>');
      }
      if (canCancel) {
        actions.push('<button type="button" class="secondary" data-admin-operational0468="CANCELLED" data-booking0468="'+id+'">Cancelar</button>');
      }
      const contact = String(booking.passengerContact || "").replace(/\D/g, "");
      if (contact.length >= 10 && contact.length <= 15) {
        actions.push('<a class="secondary buttonLike" target="_blank" rel="noopener noreferrer" href="https://wa.me/'+contact+'">WhatsApp</a>');
      }
      return '<div class="adminLogItem0417">' +
        '<strong>'+escapeHtml0417(booking.passengerName || "Passageiro")+'</strong>' +
        '<small>'+bookingStatusLabel0468(status)+' • '+bookingStatusLabel0468(operational)+' • '+bookingStatusLabel0468(payment)+
          ' • '+Number(booking.seats || 1)+' vaga(s)</small>' +
        '<div class="adminToolbar0417">'+actions.join("")+'</div>' +
        '</div>';
    }).join("");

    host.querySelectorAll("[data-admin-decision0468]").forEach((button) => {
      button.addEventListener("click", () => mutateAdminBookingDecision0468(
        tripId,
        decodeURIComponent(button.dataset.booking0468 || ""),
        button.dataset.adminDecision0468 || "",
        button,
      ));
    });
    host.querySelectorAll("[data-admin-operational0468]").forEach((button) => {
      button.addEventListener("click", () => mutateAdminBookingOperational0468(
        tripId,
        decodeURIComponent(button.dataset.booking0468 || ""),
        button.dataset.adminOperational0468 || "",
        button,
      ));
    });
  }

  async function loadAdminTripBookings0468(tripId) {
    const host = byId("adminTripBookings0468");
    if (host) host.innerHTML = '<p class="muted">Carregando reservas canônicas…</p>';
    const response = await api0417("/v1/admin/trips/" + encodeURIComponent(tripId) + "/bookings");
    renderAdminTripBookings0468(tripId, response.bookings);
  }

  async function refreshAfterBookingMutation0470(tripId) {
    await loadAdminTripBookings0468(tripId);
    if (currentContextTrip0470) {
      await Promise.all([
        refreshTripContext0470({ silent: true }),
        loadHistory0417(currentContextTrip0470.canonicalTripId || tripId),
      ]);
      return;
    }
    await loadDashboard0417();
  }

  async function mutateAdminBookingDecision0468(tripId, bookingId, action, button) {
    return runAdminAction0427("decision-"+bookingId, button, async () => {
      setMessage0417(action === "APPROVE" ? "Aprovando reserva…" : "Recusando reserva…");
      try {
        await api0417(
          "/v1/admin/trips/" + encodeURIComponent(tripId) + "/bookings/" + encodeURIComponent(bookingId) + "/decision",
          { method: "POST", operationId: newAdminOperationId0427("booking_decision"), body: JSON.stringify({ action }) },
        );
        setMessage0417(action === "APPROVE" ? "Reserva aprovada no estado canônico." : "Reserva recusada no estado canônico.");
        await refreshAfterBookingMutation0470(tripId);
      } catch (error) { setMessage0417(error.message, true); }
    });
  }

  async function mutateAdminBookingOperational0468(tripId, bookingId, selection, button) {
    return runAdminAction0427("operational-"+bookingId, button, async () => {
      setMessage0417("Atualizando estado operacional…");
      try {
        await api0417(
          "/v1/admin/trips/" + encodeURIComponent(tripId) + "/bookings/" + encodeURIComponent(bookingId) + "/operational",
          { method: "POST", operationId: newAdminOperationId0427("booking_status"), body: JSON.stringify({ selection }) },
        );
        setMessage0417("Estado atualizado no backend canônico.");
        await refreshAfterBookingMutation0470(tripId);
      } catch (error) { setMessage0417(error.message, true); }
    });
  }

  function renderSettings0417(settings) {
    currentSettings = settings || {};
    const visibility = currentSettings.publicVisibility || {};
    document.querySelectorAll("[data-public-field0417]").forEach((input) => {
      input.checked = visibility[input.dataset.publicField0417] !== false;
    });
    const policy = currentSettings.syncPolicy || {};
    byId("adminAutoSync0417").checked = policy.automatic !== false;
    byId("adminSyncInterval0417").value = String(policy.intervalMinutes || 15);
    byId("adminAuthenticationRequired0428").checked = currentSettings.authenticationRequired !== false;

    const selected = new Set(currentSettings.publicProfileUuids || []);
    const profiles = Array.isArray(currentSettings.knownProfiles) ? currentSettings.knownProfiles : [];
    byId("adminProfiles0417").innerHTML = profiles.length
      ? profiles.map((profile) =>
          '<label class="adminCheck0417"><input type="checkbox" data-profile0417="'+escapeHtml0417(profile.uuid)+'" '+
          (selected.has(profile.uuid) ? "checked" : "")+'><span>'+escapeHtml0417(profile.label || profile.uuid)+
          '<small>'+escapeHtml0417(profile.uuid)+'</small></span></label>'
        ).join("")
      : '<p class="muted">Nenhum UUID de perfil forte foi observado nas viagens atuais.</p>';
  }

  function renderLogs0417(events) {
    const list = Array.isArray(events) ? events : [];
    const errorGroups = new Map();
    list.forEach((event) => {
      const text = String(event.event || event.eventType || "") + " " + String(event.reason || event.result || "");
      if (!/fail|error|diverg|invalid|stale|reject|denied/i.test(text)) return;
      const key = String(event.event || event.eventType || "ERRO") + "|" + String(event.reason || event.result || "");
      const current = errorGroups.get(key) || { count: 0, last: 0, label: key.replace("|", " • ") };
      current.count++;
      current.last = Math.max(current.last, Number(event.createdAtMillis || 0));
      errorGroups.set(key, current);
    });
    const grouped = [...errorGroups.values()].sort((a, b) => b.last - a.last);
    byId("adminErrors0417").innerHTML = grouped.length
      ? grouped.map((item) => '<div class="adminLogItem0417"><strong>'+escapeHtml0417(item.label)+'</strong><small>'+
          item.count+' ocorrência(s) • última '+fmt0417(item.last)+'</small></div>').join("")
      : '<p class="muted">Nenhum erro agrupado nos eventos carregados.</p>';
    byId("adminLogs0417").innerHTML = list.length
      ? list.slice(0, 120).map((event) =>
          '<div class="adminLogItem0417"><strong>'+escapeHtml0417(event.event || event.eventType || event.category || "EVENTO")+
          '</strong><small>'+fmt0417(event.createdAtMillis)+' • '+escapeHtml0417(event.reason || event.result || event.source || "")+
          '</small></div>').join("")
      : '<p class="muted">Nenhum evento encontrado.</p>';
  }

  function renderSessions0417(sessions) {
    const list = Array.isArray(sessions) ? sessions : [];
    byId("adminSessions0417").innerHTML = list.length
      ? list.map((item) => '<div class="adminLogItem0417"><strong>'+(
          item.current ? "Esta sessão" : (item.legacyContext ? "Sessão anterior (legada)" : "Outra sessão")
        )+
          '</strong><small>Início '+fmt0417(item.createdAtMillis)+' • última atividade '+fmt0417(item.lastActivityAtMillis)+
          ' • expira '+fmt0417(item.expiresAtMillis)+'</small></div>').join("")
      : '<p class="muted">Nenhuma sessão ativa.</p>';
  }

  async function loadDashboard0417() {
    if (dashboardLoadPromise0427) return dashboardLoadPromise0427;
    dashboardLoadPromise0427 = (async () => {
      setMessage0417("Carregando administração…");
      try {
        const [overview, trips, settings, logs, sessions] = await Promise.all([
        api0417("/v1/admin/overview"),
        api0417("/v1/admin/trips"),
        api0417("/v1/admin/settings"),
        api0417("/v1/admin/logs"),
        api0417("/v1/admin/sessions"),
      ]);
      byId("adminPanel0417").classList.remove("hidden");
      renderOverview0417(overview);
      renderTrips0417(trips.trips);
      renderSettings0417(settings);
      renderLogs0417(logs.events);
      renderSessions0417(sessions.sessions);
        setMessage0417("");
      } catch (error) {
        if (error.status === 401 || error.status === 403) {
          byId("adminPanel0417").classList.add("hidden");
          const roleCard = byId("portalAgendaAdminCard0418");
          if (roleCard && error.status === 403) roleCard.classList.add("hidden");
        }
        setMessage0417(error.message, true);
      }
    })();
    try {
      return await dashboardLoadPromise0427;
    } finally {
      dashboardLoadPromise0427 = null;
    }
  }

  function selectTripPublicUrlEditor0465(tripOrIdentity) {
    const trip = tripOrIdentity && typeof tripOrIdentity === "object"
      ? tripOrIdentity
      : (currentContextTrip0470 && (
          currentContextTrip0470.canonicalTripId === tripOrIdentity ||
          currentContextTrip0470.remoteTripId === tripOrIdentity
        ) ? currentContextTrip0470 : currentTrips.find((item) =>
          item.remoteTripId === tripOrIdentity || item.canonicalTripId === tripOrIdentity
        )) || null;
    currentSelectedTrip0465 = trip;
    const editor = byId("adminTripPublicUrlEditor0465");
    const recovery = byId("adminTripIdentityRecovery0472");
    const unavailable = byId("adminTripPublicUrlUnavailable0470");
    if (!trip) {
      editor.classList.add("hidden");
      if (recovery) recovery.classList.add("hidden");
      return;
    }
    if (!trip.blablaTripId || trip.capabilities && trip.capabilities.canManageBlaBlaLink !== true) {
      editor.classList.add("hidden");
      if (recovery) recovery.classList.remove("hidden");
      if (unavailable) {
        unavailable.textContent = trip.manualBlaBlaIdentityPending0472
          ? "Solicitação pendente. O servidor ainda não promoveu nenhum ID: aguardando o Samsung autenticado reencontrar esta viagem."
          : "Esta viagem ainda não possui identidade forte BlaBlaCar. Cole a URL de “Editar sua carona”; ela será usada apenas como pista para uma confirmação autenticada no Samsung.";
      }
      const manageInput = byId("adminTripManageUrlInput0472");
      if (manageInput) manageInput.value = trip.manualBlaBlaManageUrl0472 || "";
      return;
    }
    if (recovery) recovery.classList.add("hidden");
    editor.classList.remove("hidden");
    byId("adminTripPublicUrlTitle0465").textContent =
      "URL pública BlaBlaCar • " + (trip.title || trip.blablaTripId);
    byId("adminTripPublicUrlHint0465").textContent = trip.attestationState === "PUBLISHED"
      ? "🟢 A viagem está comprovadamente visível na Agenda. Salve a URL para iniciar a validação forte até o estado azul."
      : (trip.attestationState === "VERIFIED"
        ? "🔵 URL, revisão, readback e atestação estão validados para o estado atual."
        : "O servidor não exibirá sucesso forte antes de publicação, readback e atestação confirmarem a revisão atual.");
    byId("adminTripPublicUrlInput0465").value =
      trip.blablaPublicUrl || trip.manualBlaBlaPublicUrl0465 || "";
  }

  async function loadHistory0417(tripIdentity, { scroll = false } = {}) {
    if (!tripIdentity) return;
    try {
      const [response] = await Promise.all([
        api0417("/v1/admin/trips/" + encodeURIComponent(tripIdentity) + "/history"),
        loadAdminTripBookings0468(tripIdentity),
      ]);
      const events = Array.isArray(response.events) ? response.events : [];
      byId("adminTripHistoryTitle0417").textContent = "Histórico / Auditoria";
      byId("adminTripHistory0417").innerHTML = events.length
        ? events.slice().reverse().map((event) => '<div class="adminLogItem0417"><strong>'+escapeHtml0417(humanAuditLabel0470(event))+
          '</strong><small>'+fmt0417(event.createdAtMillis)+' • '+escapeHtml0417(event.reason || event.result || event.source || "")+
          '</small></div>').join("")
        : '<p class="muted">Nenhum evento correlacionado encontrado.</p>';
      if (scroll) byId("adminTripHistory0417").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) { setMessage0417(error.message, true); }
  }

  async function refreshTripContext0470({ silent = false } = {}) {
    if (!currentContextTrip0470 || !currentContextTrip0470.canonicalTripId) return null;
    const canonicalTripId = currentContextTrip0470.canonicalTripId;
    if (!silent) setMessage0417("Atualizando esta viagem…");
    try {
      const response = await api0417("/v1/admin/trips/" + encodeURIComponent(canonicalTripId));
      renderTripContext0470(response.trip);
      if (!silent) setMessage0417("");
      return response.trip;
    } catch (error) {
      if (!silent) setMessage0417(error.message, true);
      return null;
    }
  }

  function scheduleTripContextReadback0470(canonicalTripId) {
    if (contextPollTimer0470) clearTimeout(contextPollTimer0470);
    let attempt = 0;
    const delays = [1800, 3500, 7000, 12000];
    const poll = async () => {
      if (!currentContextTrip0470 || currentContextTrip0470.canonicalTripId !== canonicalTripId) return;
      await refreshTripContext0470({ silent: true });
      await loadHistory0417(canonicalTripId);
      if (currentContextTrip0470 && currentContextTrip0470.attestationState === "VERIFIED") {
        setMessage0417("🔵 Validada: publicação, readback e atestação confirmaram a revisão atual.");
        return;
      }
      if (attempt < delays.length) {
        contextPollTimer0470 = setTimeout(poll, delays[attempt++]);
      }
    };
    contextPollTimer0470 = setTimeout(poll, delays[attempt++]);
  }

  async function openTripAdminContext0470(canonicalTripId, options = {}) {
    const identity = String(canonicalTripId || "").trim().slice(0, 180);
    if (!identity) return;
    contextReturn0470 = {
      source: options.source || "home",
      returnScrollY: Math.max(0, Number(options.returnScrollY || 0)),
    };
    hidePublicSections0417();
    byId("adminPanel0417").classList.add("hidden");
    byId("adminTripContext0470").classList.remove("hidden");
    setMessage0417("Carregando administração desta viagem…");
    try {
      const response = await api0417("/v1/admin/trips/" + encodeURIComponent(identity));
      if (!response.capabilities || response.capabilities.canManageTrip !== true) {
        throw new Error("Você não tem permissão para administrar esta viagem.");
      }
      renderTripContext0470(response.trip);
      await loadHistory0417(response.trip.canonicalTripId);
      setMessage0417("");
      const url = new URL(location.href);
      url.searchParams.set("administrar", response.trip.canonicalTripId);
      const nextUrl = url.pathname + url.search + url.hash;
      contextHistoryPushed0470 = options.source !== "deep-link" && !new URL(location.href).searchParams.get("administrar");
      if (contextHistoryPushed0470) {
        history.pushState({ rotaCertaAdminTrip0470: response.trip.canonicalTripId }, "", nextUrl);
      } else {
        history.replaceState(history.state || null, "", nextUrl);
      }
      byId("adminTripContextBack0470").focus({ preventScroll: true });
    } catch (error) {
      setMessage0417(error.message, true);
      if (error.status === 401 || error.status === 403) {
        byId("adminTripContext0470").classList.add("hidden");
      }
    }
  }

  async function finishCloseTripAdminContext0470({ fromHistory = false } = {}) {
    if (contextPollTimer0470) {
      clearTimeout(contextPollTimer0470);
      contextPollTimer0470 = null;
    }
    const canonicalTripId = currentContextTrip0470 && currentContextTrip0470.canonicalTripId || "";
    currentContextTrip0470 = null;
    currentSelectedTrip0465 = null;
    byId("adminTripContext0470").classList.add("hidden");
    if (!fromHistory) {
      const url = new URL(location.href);
      url.searchParams.delete("administrar");
      history.replaceState(null, "", url.pathname + url.search + url.hash);
    }
    if (contextReturn0470.source === "global") {
      byId("adminPanel0417").classList.remove("hidden");
      setMessage0417("");
      window.scrollTo({ top: contextReturn0470.returnScrollY, behavior: "auto" });
      return;
    }
    section.classList.add("hidden");
    const home = globalThis.RotaCertaAgendaHome0470;
    if (home && typeof home.refreshTrip === "function" && canonicalTripId) {
      await home.refreshTrip(canonicalTripId, { restoreScrollY: contextReturn0470.returnScrollY, focus: true });
      return;
    }
    location.reload();
  }

  async function closeTripAdminContext0470() {
    if (contextHistoryPushed0470) {
      contextHistoryPushed0470 = false;
      history.back();
      return;
    }
    return finishCloseTripAdminContext0470();
  }


  function scheduleTripIdentityRecovery0472(canonicalTripId) {
    let attempt = 0;
    const delays = [1800, 3500, 7000, 12000, 20000];
    const poll = async () => {
      if (!currentContextTrip0470 || currentContextTrip0470.canonicalTripId !== canonicalTripId) return;
      await refreshTripContext0470({ silent: true });
      await loadHistory0417(canonicalTripId);
      if (currentContextTrip0470 && currentContextTrip0470.blablaTripId) {
        setMessage0417("Identidade forte confirmada pelo Samsung. Agora a URL pública BlaBlaCar pode seguir para publicação, readback e validação azul.");
        return;
      }
      if (attempt < delays.length) {
        contextPollTimer0470 = setTimeout(poll, delays[attempt++]);
      } else {
        setMessage0417("A recuperação continua pendente. Nenhum vínculo foi forçado; o Samsung precisa reencontrar exatamente esta viagem em uma coleta autenticada.");
      }
    };
    if (contextPollTimer0470) clearTimeout(contextPollTimer0470);
    contextPollTimer0470 = setTimeout(poll, delays[attempt++]);
  }

  const recoverTripIdentityButton0472 = byId("adminRecoverTripIdentity0472");
  recoverTripIdentityButton0472.addEventListener("click", () =>
    runAdminAction0427("recover-trip-identity", recoverTripIdentityButton0472, async () => {
      const trip = currentSelectedTrip0465;
      if (!trip) return setMessage0417("Selecione uma viagem.", true);
      const blablaManageUrl = byId("adminTripManageUrlInput0472").value.trim();
      if (!blablaManageUrl) return setMessage0417("Cole a URL de “Editar sua carona” desta viagem.", true);
      setMessage0417("Registrando a pista e solicitando confirmação autenticada no Samsung…");
      try {
        const result = await api0417(
          "/v1/admin/trips/" + encodeURIComponent(trip.canonicalTripId || trip.remoteTripId) + "/blablacar-identity-recovery",
          {
            method: "PUT",
            operationId: newAdminOperationId0427("identity_recovery"),
            body: JSON.stringify({
              blablaManageUrl,
              expectedCanonicalRevision: Number(trip.canonicalRevision || 0),
              expectedIdentityRevision0472: Number(trip.manualBlaBlaIdentityRevision0472 || 0),
            }),
          },
        );
        setMessage0417(result.changed
          ? "Solicitação aceita. O ID ainda NÃO foi promovido: o Samsung fará uma coleta autenticada e só vinculará se encontrar uma correspondência forte e fisicamente compatível."
          : "Esta mesma recuperação já estava pendente; nenhum vínculo adicional foi criado.");
        await refreshTripContext0470({ silent: true });
        await loadHistory0417(trip.canonicalTripId || trip.remoteTripId);
        scheduleTripIdentityRecovery0472(trip.canonicalTripId || result.canonicalTripId || "");
      } catch (error) {
        if (error.status === 409 && error.code === "trip_revision_conflict") {
          setMessage0417("Conflito de versão: outra alteração chegou primeiro. A versão atual foi preservada e será recarregada.", true);
          await refreshTripContext0470({ silent: true });
          return;
        }
        setMessage0417(error.message, true);
      }
    }),
  );

  const saveTripPublicUrlButton0465 = byId("adminSaveTripPublicUrl0465");
  saveTripPublicUrlButton0465.addEventListener("click", () =>
    runAdminAction0427("save-trip-public-url", saveTripPublicUrlButton0465, async () => {
      const trip = currentSelectedTrip0465;
      if (!trip) return setMessage0417("Selecione uma viagem.", true);
      const blablaPublicUrl = byId("adminTripPublicUrlInput0465").value.trim();
      if (!blablaPublicUrl) return setMessage0417("Cole a URL pública BlaBlaCar da viagem.", true);
      setMessage0417("Salvando referência externa no Backend Rota Certa…");
      try {
        const result = await api0417(
          "/v1/admin/trips/" + encodeURIComponent(trip.canonicalTripId || trip.remoteTripId) + "/blablacar-public-url",
          {
            method: "PUT",
            operationId: newAdminOperationId0427("public_url"),
            body: JSON.stringify({
              blablaPublicUrl,
              expectedCanonicalRevision: Number(trip.canonicalRevision || 0),
              expectedManualRevision0465: Number(trip.manualBlaBlaPublicUrlRevision0465 || 0),
            }),
          },
        );
        if (result.replayed) {
          setMessage0417("Esta operação já havia sido recebida. Recarregando o estado autoritativo da viagem…");
        } else if (result.changed) {
          setMessage0417("URL aceita pelo backend. Aguardando sincronização, publicação, readback e atestação; o card só ficará azul após confirmação forte.");
        } else {
          setMessage0417("Esta URL já está registrada para a revisão atual.");
        }
        await refreshTripContext0470({ silent: true });
        await loadHistory0417(trip.canonicalTripId || trip.remoteTripId);
        scheduleTripContextReadback0470(trip.canonicalTripId || result.canonicalTripId || "");
      } catch (error) {
        if (error.status === 409 && error.code === "trip_revision_conflict") {
          setMessage0417("Conflito de versão: outra alteração chegou primeiro. A versão atual foi preservada e será recarregada.", true);
          await refreshTripContext0470({ silent: true });
          return;
        }
        if (error.status === 401) {
          setMessage0417("Sua sessão expirou. A URL digitada foi preservada; autentique-se novamente antes de salvar.", true);
          return;
        }
        setMessage0417(error.message, true);
      }
    }),
  );

  entry.addEventListener("click", () => {
    hidePublicSections0417();
    byId("adminTripContext0470").classList.add("hidden");
    byId("adminPanel0417").classList.remove("hidden");
    loadDashboard0417();
  });
  byId("adminBack0417").addEventListener("click", () => {
    if (!byId("adminTripContext0470").classList.contains("hidden")) return closeTripAdminContext0470();
    leaveAdmin0417();
  });
  byId("adminTripContextBack0470").addEventListener("click", closeTripAdminContext0470);
  window.addEventListener("popstate", () => {
    if (!byId("adminTripContext0470").classList.contains("hidden")) {
      contextHistoryPushed0470 = false;
      finishCloseTripAdminContext0470({ fromHistory: true });
    }
  });
  document.querySelectorAll("[data-admin-context-scroll0470]").forEach((button) => {
    button.addEventListener("click", () => {
      const target = byId(button.dataset.adminContextScroll0470 || "");
      if (target) target.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });

  globalThis.RotaCertaAgendaAdmin0470 = {
    openTrip: openTripAdminContext0470,
    closeTrip: closeTripAdminContext0470,
    refreshTrip: refreshTripContext0470,
  };

  byId("adminRefresh0417").addEventListener("click", loadDashboard0417);

  const updateNowButton0427 = byId("adminUpdateNow0417");
  updateNowButton0427.addEventListener("click", () => runAdminAction0427("update-now", updateNowButton0427, async () => {
    setMessage0417("Solicitando atualização real…");
    try {
      const operationId = newAdminOperationId0427("update_now");
      const result = await api0417("/v1/admin/sync/update-now", {
        method: "POST",
        body: "{}",
        operationId,
      });
      setMessage0417("Atualização enviada ao sincronizador canônico. Operação: " + result.correlationId);
      setTimeout(loadDashboard0417, 1800);
    } catch (error) { setMessage0417(error.message, true); }
  }));

  const reconcileButton0427 = byId("adminReconcile0417");
  reconcileButton0427.addEventListener("click", () => runAdminAction0427("reconcile", reconcileButton0427, async () => {
    setMessage0417("Solicitando reconciliação completa…");
    try {
      const operationId = newAdminOperationId0427("reconcile");
      const result = await api0417("/v1/admin/sync/reconcile", {
        method: "POST",
        body: "{}",
        operationId,
      });
      setMessage0417("Reconciliação enviada. Operação: " + result.correlationId);
      setTimeout(loadDashboard0417, 1800);
    } catch (error) { setMessage0417(error.message, true); }
  }));

  const saveSyncButton0427 = byId("adminSaveSync0417");
  saveSyncButton0427.addEventListener("click", () => runAdminAction0427("save-sync", saveSyncButton0427, async () => {
    try {
      const operationId = newAdminOperationId0427("save_sync");
      const result = await api0417("/v1/admin/settings/sync", {
        method: "PUT",
        operationId,
        body: JSON.stringify({
          automatic: byId("adminAutoSync0417").checked,
          intervalMinutes: Number(byId("adminSyncInterval0417").value || 15),
        }),
      });
      if (result.changed === false) {
        setMessage0417("Sincronização já estava com esses valores; nenhuma alteração foi gravada.");
      } else {
        setMessage0417("Configuração enviada ao dispositivo. Operação: " + result.correlationId);
      }
      await loadDashboard0417();
    } catch (error) { setMessage0417(error.message, true); }
  }));

  const saveAuthenticationButton0428 = byId("adminSaveAuthentication0428");
  saveAuthenticationButton0428.addEventListener("click", () => runAdminAction0427("save-authentication", saveAuthenticationButton0428, async () => {
    const publicVisibility = {};
    document.querySelectorAll("[data-public-field0417]").forEach((input) => {
      publicVisibility[input.dataset.publicField0417] = input.checked;
    });
    const publicProfileUuids = [...document.querySelectorAll("[data-profile0417]:checked")]
      .map((input) => input.dataset.profile0417);
    const authenticationRequired = byId("adminAuthenticationRequired0428").checked;
    try {
      await api0417("/v1/admin/settings/public", {
        method: "PUT",
        body: JSON.stringify({ publicVisibility, publicProfileUuids, authenticationRequired }),
      });
      setMessage0417(
        authenticationRequired
          ? "Autenticação ativada. A Agenda voltará a exigir acesso e senha nas áreas privadas."
          : "Autenticação desligada. A Agenda e a Administração agora podem ser abertas sem login; áreas particulares usam apenas o WhatsApp como identificação.",
      );
      location.reload();
    } catch (error) {
      setMessage0417(error.message, true);
    }
  }));

  byId("adminSavePublic0417").addEventListener("click", async () => {
    const publicVisibility = {};
    document.querySelectorAll("[data-public-field0417]").forEach((input) => {
      publicVisibility[input.dataset.publicField0417] = input.checked;
    });
    const publicProfileUuids = [...document.querySelectorAll("[data-profile0417]:checked")]
      .map((input) => input.dataset.profile0417);
    try {
      await api0417("/v1/admin/settings/public", {
        method: "PUT",
        body: JSON.stringify({ publicVisibility, publicProfileUuids }),
      });
      setMessage0417("Visibilidade pública atualizada no servidor.");
      await loadDashboard0417();
    } catch (error) { setMessage0417(error.message, true); }
  });

  byId("adminErrorsOnly0417").addEventListener("change", async () => {
    try {
      const response = await api0417("/v1/admin/logs?errorsOnly=" + (byId("adminErrorsOnly0417").checked ? "true" : "false"));
      renderLogs0417(response.events);
    } catch (error) { setMessage0417(error.message, true); }
  });

  byId("adminExport0417").addEventListener("click", async () => {
    try {
      const token = passengerSessionToken0418();
      const response = await fetch("/v1/admin/export", {
        headers: {
          ...(token ? { "Authorization": "Bearer " + token } : {}),
          "X-Rota-Certa-Admin-Driver": driverUsername,
        },
      });
      if (!response.ok) throw new Error("Não foi possível exportar os logs.");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "rota-certa-logs-" + driverUsername + ".json";
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setMessage0417("Exportação gerada com redação automática de segredos.");
    } catch (error) { setMessage0417(error.message, true); }
  });

  byId("adminBackToTrips0418").addEventListener("click", leaveAdmin0417);
})();
