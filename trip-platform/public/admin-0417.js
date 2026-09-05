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
      button.addEventListener("click", () => loadHistory0417(decodeURIComponent(button.dataset.trip || "")));
    });
  }

  function escapeHtml0417(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#039;");
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

  function selectTripPublicUrlEditor0465(remoteTripId) {
    const trip = currentTrips.find((item) => item.remoteTripId === remoteTripId) || null;
    currentSelectedTrip0465 = trip;
    const editor = byId("adminTripPublicUrlEditor0465");
    if (!trip || !trip.blablaTripId) {
      editor.classList.add("hidden");
      return;
    }
    editor.classList.remove("hidden");
    byId("adminTripPublicUrlTitle0465").textContent =
      "URL pública BlaBlaCar • " + (trip.title || trip.blablaTripId);
    byId("adminTripPublicUrlHint0465").textContent = trip.attestationState === "PUBLISHED"
      ? "Esta viagem está verde: a Agenda já confere. Cole o link público BlaBlaCar e salve para a Timeline republicar e atestar em azul."
      : "O link salvo entra primeiro na Timeline canônica; azul só aparece depois do novo readback público.";
    byId("adminTripPublicUrlInput0465").value =
      trip.blablaPublicUrl || trip.manualBlaBlaPublicUrl0465 || "";
  }

  async function loadHistory0417(remoteTripId) {
    if (!remoteTripId) return;
    selectTripPublicUrlEditor0465(remoteTripId);
    setMessage0417("Carregando histórico da viagem…");
    try {
      const response = await api0417("/v1/admin/trips/" + encodeURIComponent(remoteTripId) + "/history");
      const events = Array.isArray(response.events) ? response.events : [];
      byId("adminTripHistoryTitle0417").textContent = "Histórico da viagem";
      byId("adminTripHistory0417").innerHTML = events.length
        ? events.map((event) => '<div class="adminLogItem0417"><strong>'+escapeHtml0417(event.event || event.eventType || "EVENTO")+
          '</strong><small>'+fmt0417(event.createdAtMillis)+' • '+escapeHtml0417(event.reason || event.result || event.source || "")+
          '</small></div>').join("")
        : '<p class="muted">Nenhum evento correlacionado encontrado.</p>';
      setMessage0417("");
      byId("adminTripHistory0417").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) { setMessage0417(error.message, true); }
  }

  const saveTripPublicUrlButton0465 = byId("adminSaveTripPublicUrl0465");
  saveTripPublicUrlButton0465.addEventListener("click", () =>
    runAdminAction0427("save-trip-public-url", saveTripPublicUrlButton0465, async () => {
      const trip = currentSelectedTrip0465;
      if (!trip) return setMessage0417("Selecione uma viagem.", true);
      const blablaPublicUrl = byId("adminTripPublicUrlInput0465").value.trim();
      if (!blablaPublicUrl) return setMessage0417("Cole a URL pública BlaBlaCar da viagem.", true);
      setMessage0417("Salvando na Timeline canônica…");
      try {
        const result = await api0417(
          "/v1/admin/trips/" + encodeURIComponent(trip.remoteTripId) + "/blablacar-public-url",
          {
            method: "PUT",
            operationId: newAdminOperationId0427("public_url"),
            body: JSON.stringify({ blablaPublicUrl }),
          },
        );
        setMessage0417(result.changed
          ? "URL salva. O card foi enviado à Timeline; o azul aparecerá após o readback confirmar a nova revisão."
          : "Esta URL já está aplicada à viagem.");
        setTimeout(loadDashboard0417, 1200);
        setTimeout(loadDashboard0417, 3500);
        setTimeout(loadDashboard0417, 8000);
      } catch (error) { setMessage0417(error.message, true); }
    }),
  );

  entry.addEventListener("click", () => {
    hidePublicSections0417();
    loadDashboard0417();
  });
  byId("adminBack0417").addEventListener("click", leaveAdmin0417);

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
