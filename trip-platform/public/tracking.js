"use strict";

(() => {
  const marker = "LIVE_GPS_TRACKER_PUBLIC_0670";
  const token = (location.hash || "").slice(1).trim();
  const title = document.getElementById("title");
  const status = document.getElementById("status");
  const privacy = document.getElementById("privacy");
  const deviceUpdated = document.getElementById("deviceUpdated");
  const gpsUpdated = document.getElementById("gpsUpdated");
  const started = document.getElementById("started");
  const battery = document.getElementById("battery");
  const distanceBox = document.getElementById("distanceBox");
  const distance = document.getElementById("distance");
  const fallback = document.getElementById("fallback");
  const mapsLink = document.getElementById("mapsLink");
  let timer = null;
  let map = null;
  let route = null;
  let currentMarker = null;
  let destinationMarker = null;
  let routePoints0670 = [];
  let lastRoutePointMillis0670 = 0;
  let refreshInFlight0670 = false;

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

  function ensureMap() {
    if (map || !window.L) return Boolean(map);
    map = L.map("map", { zoomControl:true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom:19,
      attribution:'&copy; OpenStreetMap contributors',
    }).addTo(map);
    return true;
  }

  function renderMap(data) {
    const points = routePoints0670;
    const current = data.current;
    if (!ensureMap()) {
      fallback.hidden = false;
      if (current) {
        mapsLink.href = `https://www.google.com/maps?q=${encodeURIComponent(current.latitude + "," + current.longitude)}`;
      }
      return;
    }
    fallback.hidden = true;
    const latLngs = points.map((p) => [Number(p.latitude), Number(p.longitude)]);
    if (route) route.remove();
    if (latLngs.length > 1) route = L.polyline(latLngs, { weight:5, opacity:.82 }).addTo(map);

    if (current) {
      const here = [Number(current.latitude), Number(current.longitude)];
      if (currentMarker) currentMarker.setLatLng(here);
      else currentMarker = L.marker(here).addTo(map).bindPopup("Posição atual");
      mapsLink.href = `https://www.google.com/maps?q=${encodeURIComponent(here.join(","))}`;
    }
    if (data.destination) {
      const dest = [Number(data.destination.latitude), Number(data.destination.longitude)];
      if (destinationMarker) destinationMarker.setLatLng(dest);
      else destinationMarker = L.marker(dest).addTo(map).bindPopup(data.destination.label || "Destino");
    }

    const boundsPoints = latLngs.slice();
    if (data.destination) boundsPoints.push([Number(data.destination.latitude), Number(data.destination.longitude)]);
    if (boundsPoints.length > 1) map.fitBounds(boundsPoints, { padding:[24,24], maxZoom:16 });
    else if (boundsPoints.length === 1) map.setView(boundsPoints[0], 15);
  }

  function render(data) {
    const passenger = data.scope === "PASSENGER";
    title.textContent = passenger ? "🚗 Seu motorista está a caminho" : "🛡 GPS Tracker de segurança";
    privacy.textContent = passenger
      ? "Este link mostra somente o trecho compartilhado para esta viagem e será encerrado no desembarque ou ao expirar."
      : "Acompanhamento familiar ao vivo da sessão atual, incluindo o trajeto já percorrido.";

    const deviceAt = Number(data.lastDeviceHeartbeatAtMillis || data.lastUpdatedAtMillis || 0);
    const gpsAt = Number(data.lastGpsAtMillis || (data.current && data.current.recordedAtMillis) || 0);
    const deviceState = String(data.deviceState || "WAITING");
    const gpsState = String(data.gpsState || "WAITING");
    if (deviceState === "CONNECTED" && gpsState === "FRESH") {
      status.textContent = "🟢 GPS Tracker ativo • atualização automática";
      status.className = "status";
    } else if (deviceState === "CONNECTED") {
      status.textContent = "🟡 Aparelho conectado • aguardando GPS";
      status.className = "warn";
    } else if (deviceState === "DELAYED") {
      status.textContent = "🟠 Comunicação instável com o aparelho";
      status.className = "warn";
    } else if (deviceState === "OFFLINE") {
      status.textContent = "🔴 Sem comunicação com o aparelho • última posição preservada";
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
    const suffix = sinceMillis > 0 ? `?since=${encodeURIComponent(sinceMillis)}` : "";
    const response = await fetch(`/v1/public/tracking/${encodeURIComponent(token)}${suffix}`, {
      cache:"no-store",
      headers:{ "Accept":"application/json" },
    });
    return response;
  }

  async function refresh() {
    if (refreshInFlight0670) return;
    if (!token || !/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      status.textContent = "Link inválido.";
      status.className = "ended";
      return;
    }
    refreshInFlight0670 = true;
    try {
      let data = null;
      let since0670 = lastRoutePointMillis0670;
      for (let page0670 = 0; page0670 < 3; page0670 += 1) {
        const response = await fetchTrackingPage0670(since0670);
        if (response.status === 410) {
          status.textContent = "Compartilhamento encerrado.";
          status.className = "ended";
          if (timer) clearInterval(timer);
          return;
        }
        if (response.status === 404) {
          status.textContent = "Aguardando o link ficar disponível no servidor…";
          status.className = "warn";
          return;
        }
        if (!response.ok) throw new Error("http_" + response.status);
        data = await response.json();
        mergeRoutePoints0670(data.points);
        const next0670 = Number(data.nextSinceMillis || lastRoutePointMillis0670 || 0);
        if (!data.hasMorePoints || next0670 <= since0670) break;
        since0670 = next0670;
      }
      if (data) render(data);
    } catch (_) {
      status.textContent = "Sem conexão com o servidor. A última posição exibida permanece válida.";
      status.className = "warn";
    } finally {
      refreshInFlight0670 = false;
    }
  }

  void marker;
  refresh();
  timer = setInterval(refresh, 5000);
})();