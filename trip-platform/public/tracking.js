"use strict";

(() => {
  const marker = "PERMANENT_FAMILY_GPS_0681";
  const passengerLiveOnlyMarker0691 = "PASSENGER_LIVE_ONLY_0691";
  const familyLiveFirstMarker0692 = "FAMILY_LIVE_FIRST_0692";
  const pathParts = location.pathname.split("/").filter(Boolean);
  const familyUsername = pathParts.length === 2 && String(pathParts[1]).toLowerCase() === "gps"
    ? String(pathParts[0] || "").toLowerCase().replace(/[^a-z0-9_-]/g, "")
    : "";
  const familyMode0681 = Boolean(familyUsername);
  const token = familyMode0681 ? "" : (location.hash || "").slice(1).trim();
  const familyStorageKey0681 = familyMode0681 ? "rota_certa_family_session_0681_" + familyUsername : "";
  let familySession0681 = familyMode0681 ? readFamilySession0681() : "";

  const title = document.getElementById("title");
  const status = document.getElementById("status");
  const privacy = document.getElementById("privacy");
  const deviceUpdated = document.getElementById("deviceUpdated");
  const gpsUpdated = document.getElementById("gpsUpdated");
  const started = document.getElementById("started");
  const battery = document.getElementById("battery");
  const distanceBox = document.getElementById("distanceBox");
  const distance = document.getElementById("distance");
  const destinationBox = document.getElementById("destinationBox");
  const destinationLabel = document.getElementById("destinationLabel");
  const centerVehicle0692 = document.getElementById("centerVehicle");
  const historyToggle0692 = document.getElementById("historyToggle");
  const fallback = document.getElementById("fallback");
  const mapsLink = document.getElementById("mapsLink");
  const familyAuth0681 = document.getElementById("familyAuth");
  const familyPin0681 = document.getElementById("familyPin");
  const familyAuthorize0681 = document.getElementById("familyAuthorize");
  const trackingFooter0681 = document.getElementById("trackingFooter");
  const mapWrap0692 = document.getElementById("mapWrap");
  const trackingSummary0692 = document.getElementById("trackingSummary");

  let timer = null;
  let map = null;
  let route = null;
  let currentMarker = null;
  let destinationMarker = null;
  let routePoints0670 = [];
  let lastRoutePointMillis0670 = 0;
  let refreshInFlight0670 = false;
  let authInFlight0681 = false;
  let familyHistoryMode0692 = false;
  let followLive0692 = true;
  let currentLivePosition0692 = null;
  let currentPassengerPosition0691 = null;
  let lastRenderedData0692 = null;
  let programmaticCameraMove0692 = false;
  let historyBoundsApplied0692 = false;

  function readFamilySession0681() {
    try {
      return localStorage.getItem(familyStorageKey0681) || "";
    } catch (_) {
      return "";
    }
  }

  function saveFamilySession0681(value) {
    familySession0681 = String(value || "");
    try {
      if (familySession0681) localStorage.setItem(familyStorageKey0681, familySession0681);
      else localStorage.removeItem(familyStorageKey0681);
    } catch (_) {}
  }

  function formatTime(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat("pt-BR", { hour:"2-digit", minute:"2-digit", second:"2-digit" }).format(new Date(value));
  }

  function formatDistance(meters) {
    if (meters == null || meters === "" || !Number.isFinite(Number(meters))) return "—";
    const value = Number(meters);
    return value < 1000 ? `${Math.max(0, Math.round(value))} m` : `${(value / 1000).toFixed(1).replace(".", ",")} km`;
  }

  function ageLabel(value) {
    if (!value) return "Aguardando GPS";
    const seconds = Math.max(0, Math.floor((Date.now() - Number(value)) / 1000));
    if (seconds < 15) return "Atualizado agora";
    if (seconds < 60) return `Atualizado há ${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    return `Sem nova posição há ${minutes} min`;
  }

  function showFamilyAuth0681(message) {
    if (!familyMode0681) return;
    familyAuth0681.hidden = false;
    if (mapWrap0692) mapWrap0692.hidden = true;
    if (trackingSummary0692) trackingSummary0692.hidden = true;
    fallback.hidden = true;
    title.textContent = "🛡 GPS familiar permanente";
    privacy.textContent = "Endereço permanente do motorista. Este navegador precisa ser autorizado apenas no primeiro acesso.";
    trackingFooter0681.textContent = "Endereço permanente protegido. Não compartilhe o código familiar com pessoas não autorizadas.";
    status.textContent = message || "Informe o código familiar para autorizar este navegador.";
    status.className = "warn";
  }

  function hideFamilyAuth0681() {
    if (!familyMode0681) return;
    familyAuth0681.hidden = true;
    if (mapWrap0692) mapWrap0692.hidden = false;
    if (trackingSummary0692) trackingSummary0692.hidden = false;
    trackingFooter0681.textContent = "Endereço familiar permanente e protegido. Ao abrir, o mapa mostra primeiro onde o motorista está agora.";
    setTimeout(() => { if (map) map.invalidateSize(); }, 0);
  }

  async function authorizeFamily0681() {
    if (!familyMode0681 || authInFlight0681) return;
    const pin = String(familyPin0681.value || "").replace(/\D/g, "").slice(0, 6);
    if (!/^\d{6}$/.test(pin)) {
      showFamilyAuth0681("Digite o código familiar de 6 dígitos.");
      return;
    }
    authInFlight0681 = true;
    familyAuthorize0681.disabled = true;
    status.textContent = "Confirmando autorização…";
    status.className = "warn";
    try {
      const response = await fetch("/v1/public/tracking/family/session", {
        method:"POST",
        cache:"no-store",
        headers:{ "Accept":"application/json", "Content-Type":"application/json" },
        body:JSON.stringify({ username:familyUsername, pin }),
      });
      if (response.status === 410) {
        status.textContent = "🔴 O motorista não está compartilhando a localização neste momento.";
        status.className = "ended";
        return;
      }
      if (response.status === 429) {
        status.textContent = "Muitas tentativas. Aguarde alguns minutos.";
        status.className = "ended";
        return;
      }
      if (!response.ok) {
        status.textContent = response.status === 401 ? "Código familiar incorreto." : "Não foi possível autorizar este navegador.";
        status.className = "ended";
        return;
      }
      const data = await response.json();
      saveFamilySession0681(data.sessionToken);
      familyPin0681.value = "";
      familyHistoryMode0692 = false;
      followLive0692 = true;
      clearRoute0692();
      hideFamilyAuth0681();
      await refresh();
    } catch (_) {
      status.textContent = "Sem conexão com o servidor. Tente novamente.";
      status.className = "warn";
    } finally {
      authInFlight0681 = false;
      familyAuthorize0681.disabled = false;
    }
  }

  function ensureMap() {
    if (map || !window.L) return Boolean(map);
    map = L.map("map", { zoomControl:true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom:19,
      attribution:'&copy; OpenStreetMap contributors',
    }).addTo(map);
    map.on("dragstart", () => {
      if (!familyHistoryMode0692) followLive0692 = false;
    });
    map.on("zoomstart", () => {
      if (!programmaticCameraMove0692 && !familyHistoryMode0692) followLive0692 = false;
    });
    return true;
  }

  function runProgrammaticCamera0692(action) {
    programmaticCameraMove0692 = true;
    try {
      action();
    } finally {
      setTimeout(() => { programmaticCameraMove0692 = false; }, 0);
    }
  }

  function clearRoute0692() {
    routePoints0670 = [];
    lastRoutePointMillis0670 = 0;
    historyBoundsApplied0692 = false;
    if (route) {
      route.remove();
      route = null;
    }
  }

  function centerPassenger0691() {
    centerLive0692();
  }

  function centerLive0692() {
    if (!map || !currentLivePosition0692) return;
    if (familyMode0681 && familyHistoryMode0692) {
      familyHistoryMode0692 = false;
      clearRoute0692();
      if (historyToggle0692) historyToggle0692.textContent = "🗺 Ver trajeto";
    }
    followLive0692 = true;
    runProgrammaticCamera0692(() => {
      map.setView(currentLivePosition0692, Math.max(16, map.getZoom() || 16), { animate:true });
    });
  }

  function renderMap(data) {
    const passenger = data.scope === "PASSENGER";
    const family = familyMode0681 && data.scope === "FAMILY";
    const liveFirst = passenger || (family && !familyHistoryMode0692);
    const current = data.current;
    if (!ensureMap()) {
      fallback.hidden = false;
      if (current) {
        mapsLink.href = `https://www.google.com/maps?q=${encodeURIComponent(current.latitude + "," + current.longitude)}`;
      }
      return;
    }
    fallback.hidden = true;

    if (passenger) {
      if (route) {
        route.remove();
        route = null;
      }
    } else if (family && !familyHistoryMode0692) {
      if (route) {
        route.remove();
        route = null;
      }
    } else {
      const latLngs = routePoints0670.map((p) => [Number(p.latitude), Number(p.longitude)]);
      if (route) route.remove();
      route = latLngs.length > 1 ? L.polyline(latLngs, { weight:5, opacity:.82 }).addTo(map) : null;
    }

    if (current) {
      const here = [Number(current.latitude), Number(current.longitude)];
      if (currentMarker) currentMarker.setLatLng(here);
      else currentMarker = L.marker(here).addTo(map).bindPopup(passenger ? "Localização ao vivo" : "Posição atual do motorista");
      mapsLink.href = `https://www.google.com/maps?q=${encodeURIComponent(here.join(","))}`;
      if (passenger || family) currentLivePosition0692 = here;
      if (passenger) currentPassengerPosition0691 = here;
      if (liveFirst && followLive0692) {
        runProgrammaticCamera0692(() => {
          map.setView(here, Math.max(16, map.getZoom() || 16), { animate:true });
        });
      }
    }

    if (data.destination) {
      const dest = [Number(data.destination.latitude), Number(data.destination.longitude)];
      if (destinationMarker) destinationMarker.setLatLng(dest);
      else destinationMarker = L.marker(dest).addTo(map).bindPopup(data.destination.label || "Desembarque");
    } else if (destinationMarker) {
      destinationMarker.remove();
      destinationMarker = null;
    }

    if (liveFirst) {
      if (!current && data.destination) {
        runProgrammaticCamera0692(() => map.setView([Number(data.destination.latitude), Number(data.destination.longitude)], 15));
      }
      return;
    }

    if (family && familyHistoryMode0692 && !historyBoundsApplied0692) {
      const boundsPoints = routePoints0670.map((p) => [Number(p.latitude), Number(p.longitude)]);
      if (current) boundsPoints.push([Number(current.latitude), Number(current.longitude)]);
      if (boundsPoints.length > 1) {
        runProgrammaticCamera0692(() => map.fitBounds(boundsPoints, { padding:[24,24], maxZoom:16 }));
      } else if (boundsPoints.length === 1) {
        runProgrammaticCamera0692(() => map.setView(boundsPoints[0], 15));
      }
      historyBoundsApplied0692 = true;
    }
  }

  function render(data) {
    hideFamilyAuth0681();
    lastRenderedData0692 = data;
    const passenger = data.scope === "PASSENGER";
    const family = familyMode0681 && data.scope === "FAMILY";
    title.textContent = passenger
      ? "🚗 Seu motorista está a caminho"
      : family
        ? (familyHistoryMode0692 ? "🗺 Histórico do motorista" : "📍 Localização do motorista")
        : "🛡 GPS Tracker de segurança";
    privacy.textContent = passenger
      ? "Localização ao vivo, sem rastro. Este link será encerrado automaticamente quando o desembarque for confirmado."
      : family
        ? (familyHistoryMode0692
            ? "Histórico da sessão atual. Toque em Voltar ao Ao Vivo para acompanhar novamente a posição real."
            : "Ao Vivo primeiro: a posição atual fica centralizada. O histórico só é carregado quando você pedir.")
        : "Acompanhamento familiar ao vivo da sessão atual, incluindo o trajeto já percorrido.";

    const deviceAt = Number(data.lastDeviceHeartbeatAtMillis || data.lastUpdatedAtMillis || 0);
    const gpsAt = Number(data.lastGpsAtMillis || (data.current && data.current.recordedAtMillis) || 0);
    const deviceState = String(data.deviceState || "WAITING");
    const gpsState = String(data.gpsState || "WAITING");
    if (deviceState === "CONNECTED" && gpsState === "FRESH") {
      status.textContent = family && familyHistoryMode0692
        ? "🗺 HISTÓRICO • posição atual preservada"
        : "🟢 AO VIVO • localização atual";
      status.className = "status";
    } else if (deviceState === "CONNECTED") {
      status.textContent = "🟡 Aparelho conectado • GPS sem posição recente";
      status.className = "warn";
    } else if (deviceState === "DELAYED") {
      status.textContent = "🟠 Comunicação instável com o aparelho";
      status.className = "warn";
    } else if (deviceState === "OFFLINE") {
      status.textContent = "🔴 OFFLINE • última posição conhecida preservada";
      status.className = "ended";
    } else {
      status.textContent = "Aguardando o GPS Tracker conectar…";
      status.className = "warn";
    }

    deviceUpdated.textContent = deviceAt ? ageLabel(deviceAt) : "Aguardando conexão";
    gpsUpdated.textContent = gpsAt ? ageLabel(gpsAt) : "Aguardando GPS";
    started.textContent = formatTime(data.startedAtMillis);
    battery.textContent = data.batteryPercent == null ? "—" : `${Math.round(Number(data.batteryPercent))}%`;
    distanceBox.hidden = !passenger;
    distance.textContent = !data.current || data.distanceToDestinationMeters == null
      ? "Aguardando localização…"
      : formatDistance(data.distanceToDestinationMeters);
    destinationBox.hidden = !passenger || !data.destination;
    destinationLabel.textContent = data.destination && data.destination.label
      ? String(data.destination.label)
      : "Destino da viagem";
    centerVehicle0692.hidden = !(passenger || family) || !data.current || (family && familyHistoryMode0692);
    historyToggle0692.hidden = !family;
    if (family) historyToggle0692.textContent = familyHistoryMode0692 ? "← Voltar ao Ao Vivo" : "🗺 Ver trajeto";
    renderMap(data);
  }

  function mergeRoutePoints0670(incoming) {
    const merged = new Map();
    routePoints0670.forEach((p) => {
      const key = [Number(p.recordedAtMillis || 0), Number(p.latitude).toFixed(7), Number(p.longitude).toFixed(7)].join("|");
      merged.set(key, p);
    });
    (Array.isArray(incoming) ? incoming : []).forEach((p) => {
      if (!Number.isFinite(Number(p.latitude)) || !Number.isFinite(Number(p.longitude))) return;
      const key = [Number(p.recordedAtMillis || 0), Number(p.latitude).toFixed(7), Number(p.longitude).toFixed(7)].join("|");
      merged.set(key, p);
    });
    routePoints0670 = Array.from(merged.values()).sort((a, b) =>
      Number(a.recordedAtMillis || 0) - Number(b.recordedAtMillis || 0)
    );
    const last = routePoints0670.at(-1);
    if (last) lastRoutePointMillis0670 = Number(last.recordedAtMillis || lastRoutePointMillis0670);
  }

  async function fetchTrackingPage0670(sinceMillis) {
    if (familyMode0681) {
      const params = new URLSearchParams();
      if (familyHistoryMode0692) params.set("trace", "1");
      if (familyHistoryMode0692 && sinceMillis > 0) params.set("since", String(sinceMillis));
      const suffix = params.toString() ? "?" + params.toString() : "";
      return fetch(`/v1/public/tracking/family/${encodeURIComponent(familyUsername)}${suffix}`, {
        cache:"no-store",
        headers:{
          "Accept":"application/json",
          "X-Rota-Certa-Family-Session":familySession0681,
        },
      });
    }
    const suffix = sinceMillis > 0 ? `?since=${encodeURIComponent(sinceMillis)}` : "";
    return fetch(`/v1/public/tracking/${encodeURIComponent(token)}${suffix}`, {
      cache:"no-store",
      headers:{ "Accept":"application/json" },
    });
  }

  async function refresh() {
    if (refreshInFlight0670) return;
    if (familyMode0681) {
      if (!familySession0681) {
        showFamilyAuth0681();
        return;
      }
    } else if (!token || !/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      status.textContent = "Link inválido.";
      status.className = "ended";
      return;
    }

    refreshInFlight0670 = true;
    try {
      let data = null;
      let since0670 = familyMode0681 && !familyHistoryMode0692 ? 0 : lastRoutePointMillis0670;
      for (let page0670 = 0; page0670 < 3; page0670 += 1) {
        const response = await fetchTrackingPage0670(since0670);
        if (familyMode0681 && (response.status === 401 || response.status === 403)) {
          saveFamilySession0681("");
          showFamilyAuth0681("Autorize novamente este navegador com o código familiar.");
          return;
        }
        if (response.status === 410) {
          let ended0691 = null;
          try { ended0691 = await response.json(); } catch (_) {}
          const passengerArrived0691 = ended0691 && ended0691.error === "passenger_arrived";
          status.textContent = familyMode0681
            ? "🔴 O motorista não está compartilhando a localização neste momento."
            : passengerArrived0691
              ? "✅ Passageiro chegou ao destino • acompanhamento encerrado"
              : "Compartilhamento encerrado.";
          status.className = passengerArrived0691 ? "status" : "ended";
          privacy.textContent = passengerArrived0691
            ? "O desembarque foi confirmado. Este link não fornece mais coordenadas."
            : privacy.textContent;
          centerVehicle0692.hidden = true;
          historyToggle0692.hidden = true;
          if (!familyMode0681 && timer) clearInterval(timer);
          return;
        }
        if (response.status === 404) {
          status.textContent = "Aguardando o acompanhamento ficar disponível no servidor…";
          status.className = "warn";
          return;
        }
        if (!response.ok) throw new Error("http_" + response.status);

        data = await response.json();
        const passenger = data.scope === "PASSENGER";
        const familyLive = familyMode0681 && data.scope === "FAMILY" && !familyHistoryMode0692;
        if (passenger || familyLive) {
          clearRoute0692();
          render(data);
          break;
        }

        mergeRoutePoints0670(data.points);
        const next0670 = Number(data.nextSinceMillis || lastRoutePointMillis0670 || 0);
        if (!data.hasMorePoints || next0670 <= since0670) {
          render(data);
          break;
        }
        since0670 = next0670;
      }
    } catch (_) {
      status.textContent = "Sem conexão com o servidor. A última posição exibida permanece válida.";
      status.className = "warn";
    } finally {
      refreshInFlight0670 = false;
    }
  }

  async function toggleFamilyHistory0692() {
    if (!familyMode0681 || !familySession0681) return;
    familyHistoryMode0692 = !familyHistoryMode0692;
    clearRoute0692();
    if (!familyHistoryMode0692) {
      followLive0692 = true;
      if (historyToggle0692) historyToggle0692.textContent = "🗺 Ver trajeto";
      if (lastRenderedData0692) render(lastRenderedData0692);
      centerLive0692();
    } else {
      followLive0692 = false;
      historyBoundsApplied0692 = false;
      if (historyToggle0692) historyToggle0692.textContent = "← Voltar ao Ao Vivo";
    }
    await refresh();
  }

  if (familyAuthorize0681) {
    familyAuthorize0681.addEventListener("click", authorizeFamily0681);
  }
  if (familyPin0681) {
    familyPin0681.addEventListener("input", () => {
      familyPin0681.value = String(familyPin0681.value || "").replace(/\D/g, "").slice(0, 6);
    });
    familyPin0681.addEventListener("keydown", (event) => {
      if (event.key === "Enter") void authorizeFamily0681();
    });
  }
  if (centerVehicle0692) {
    centerVehicle0692.addEventListener("click", centerLive0692);
  }
  if (historyToggle0692) {
    historyToggle0692.addEventListener("click", () => { void toggleFamilyHistory0692(); });
  }

  if (familyMode0681) {
    trackingFooter0681.textContent = "Endereço familiar permanente e protegido. Ao Vivo é a tela principal; o histórico é carregado somente quando solicitado.";
    if (!familySession0681) showFamilyAuth0681();
    else hideFamilyAuth0681();
  }

  void marker;
  void currentPassengerPosition0691;
  void centerPassenger0691;
  void passengerLiveOnlyMarker0691;
  void familyLiveFirstMarker0692;
  refresh();
  timer = setInterval(refresh, 5000);
})();
