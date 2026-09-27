"use strict";

(() => {
  const marker = "LIVE_TRACKING_PUBLIC_0669";
  const token = (location.hash || "").slice(1).trim();
  const title = document.getElementById("title");
  const status = document.getElementById("status");
  const privacy = document.getElementById("privacy");
  const updated = document.getElementById("updated");
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
    const points = Array.isArray(data.points) ? data.points.filter((p) =>
      Number.isFinite(Number(p.latitude)) && Number.isFinite(Number(p.longitude))
    ) : [];
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
    title.textContent = passenger ? "🚗 Seu motorista está a caminho" : "🛡 Acompanhamento de segurança";
    privacy.textContent = passenger
      ? "Este link mostra somente o trecho compartilhado para esta viagem e será encerrado no destino ou ao expirar."
      : "Acompanhamento familiar da sessão atual, incluindo o trajeto já percorrido.";
    const waitingForPassengerGps = passenger && !data.current;
    const age = data.lastUpdatedAtMillis ? Date.now() - Number(data.lastUpdatedAtMillis) : Number.POSITIVE_INFINITY;
    status.textContent = waitingForPassengerGps
      ? "Aguardando localização do motorista…"
      : age <= 30000 ? "● " + ageLabel(data.lastUpdatedAtMillis) : ageLabel(data.lastUpdatedAtMillis);
    status.className = waitingForPassengerGps || age > 120000 ? "warn" : "status";
    updated.textContent = waitingForPassengerGps ? "Aguardando GPS" : formatTime(data.lastUpdatedAtMillis);
    started.textContent = formatTime(data.startedAtMillis);
    battery.textContent = data.batteryPercent == null ? "—" : `${Math.round(Number(data.batteryPercent))}%`;
    distanceBox.hidden = !passenger;
    distance.textContent = waitingForPassengerGps || data.distanceToDestinationMeters == null
      ? "Aguardando localização…"
      : formatDistance(data.distanceToDestinationMeters);
    renderMap(data);
  }

  async function refresh() {
    if (!token || !/^[A-Za-z0-9_-]{22,180}$/.test(token)) {
      status.textContent = "Link inválido.";
      status.className = "ended";
      return;
    }
    try {
      const response = await fetch(`/v1/public/tracking/${encodeURIComponent(token)}`, {
        cache:"no-store",
        headers:{ "Accept":"application/json" },
      });
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
      const data = await response.json();
      render(data);
    } catch (_) {
      status.textContent = "Sem conexão com o servidor. A última posição exibida permanece válida.";
      status.className = "warn";
    }
  }

  void marker;
  refresh();
  timer = setInterval(refresh, 5000);
})();