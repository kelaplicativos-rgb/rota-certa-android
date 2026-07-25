#!/usr/bin/env python3
"""Release gate using the exact destinations supplied by the user.

This is intentionally not a mocked route test. It calls the same Google
Geocoding and Routes endpoints used by the Android app and fails the workflow
when any destination cannot produce a driving distance and a green/red result.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

API_KEY = os.environ.get("GOOGLE_MAPS_API_KEY", "").strip()
ANDROID_PACKAGE = "br.com.mapeiaia.rotacerta"
HOME = "R. Lateral, 15 - Cidade São Mateus, São Paulo - SP, 04891-240, Brasil"
RADIUS_KM = 5.0
REPORT_PATH = Path(os.environ.get("REAL_FAROL_REPORT", "real-farol-validation.txt"))

DESTINATIONS = [
    "Rua Joaquim Pereira dos Santos, 527 (Vila Assis Brasil, Mauá - State of São Paulo)",
    "Av. Maria Luiza Americano, 2673 (Cidade Líder), São Paulo - SP",
    "Rua Doutor Virgílio do Nascimento, 638 (Brás, São Paulo - Estado de São Paulo)",
    "Av. Francisco Morais Ramos, 1800 (Jardim Santa Tereza, Rio Grande da Serra - SP, 09450-000)",
    "Rua José Inácio de Oliveira, 18 (Imirim, São Paulo - SP)",
    "Rua John Speers, 1469 (Jardim Helian, São Paulo - SP)",
    "Rua dos Jasmins, 14 (São Rafael, São Paulo - SP)",
    "McDonald's (Avenida Mateo Bei - Cidade São Mateus, São Paulo - SP)",
    "Rua Emília Marengo, 179 (Vila Regente Feijó, São Paulo - SP)",
]


@dataclass(frozen=True)
class Coordinate:
    latitude: float
    longitude: float


def request_json(url: str, *, method: str = "GET", body: dict | None = None, headers: dict[str, str] | None = None) -> dict:
    payload = None if body is None else json.dumps(body).encode("utf-8")
    request_headers = {
        "Accept": "application/json",
        "X-Android-Package": ANDROID_PACKAGE,
    }
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)

    last_error: Exception | None = None
    for attempt in range(1, 3):
        try:
            request = urllib.request.Request(url, data=payload, method=method, headers=request_headers)
            with urllib.request.urlopen(request, timeout=12) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(0.35)
    raise RuntimeError(f"request failed after retry: {last_error}")


def geocode(address: str) -> Coordinate:
    query = urllib.parse.urlencode(
        {
            "address": f"{address}, Brasil",
            "region": "br",
            "language": "pt-BR",
            "key": API_KEY,
        }
    )
    data = request_json(f"https://maps.googleapis.com/maps/api/geocode/json?{query}")
    if data.get("status") != "OK" or not data.get("results"):
        raise RuntimeError(f"geocode status={data.get('status')} error={data.get('error_message', '')}")
    location = data["results"][0]["geometry"]["location"]
    return Coordinate(float(location["lat"]), float(location["lng"]))


def route_distance_km(origin: Coordinate, destination: Coordinate) -> float:
    body = {
        "origin": {"location": {"latLng": {"latitude": origin.latitude, "longitude": origin.longitude}}},
        "destination": {"location": {"latLng": {"latitude": destination.latitude, "longitude": destination.longitude}}},
        "travelMode": "DRIVE",
        "routingPreference": "TRAFFIC_UNAWARE",
        "languageCode": "pt-BR",
        "units": "METRIC",
    }
    data = request_json(
        "https://routes.googleapis.com/directions/v2:computeRoutes",
        method="POST",
        body=body,
        headers={
            "X-Goog-Api-Key": API_KEY,
            "X-Goog-FieldMask": "routes.distanceMeters",
        },
    )
    routes = data.get("routes") or []
    if not routes or "distanceMeters" not in routes[0]:
        raise RuntimeError(f"route response without distanceMeters: {data}")
    return float(routes[0]["distanceMeters"]) / 1000.0


def main() -> int:
    lines = [
        "ROTA CERTA - SIMULACAO REAL DO FAROL",
        f"Casa definida pelo usuario: {HOME}",
        f"Raio: {RADIUS_KM:.1f} km",
        "API: Google Geocoding + Routes computeRoutes",
        "",
    ]
    failures: list[str] = []
    colors: set[str] = set()

    if not API_KEY:
        lines.append("FALHA: GOOGLE_MAPS_API_KEY ausente. Download estavel bloqueado.")
        REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(REPORT_PATH.read_text(encoding="utf-8"))
        return 2

    try:
        home = geocode(HOME)
        lines.append(f"Casa geocodificada: {home.latitude:.7f}, {home.longitude:.7f}")
    except Exception as error:  # noqa: BLE001 - report must preserve exact runtime failure
        lines.append(f"FALHA CASA: {error}")
        REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(REPORT_PATH.read_text(encoding="utf-8"))
        return 3

    for index, address in enumerate(DESTINATIONS, start=1):
        try:
            coordinate = geocode(address)
            distance_km = route_distance_km(coordinate, home)
            color = "VERDE" if distance_km <= RADIUS_KM else "VERMELHO"
            colors.add(color)
            lines.extend(
                [
                    "",
                    f"CASO {index}: {address}",
                    f"Destino B: {coordinate.latitude:.7f}, {coordinate.longitude:.7f}",
                    f"Rota destino B -> casa: {distance_km:.1f} km",
                    f"Farol esperado: {color}",
                    "Km exibido: SIM",
                ]
            )
        except Exception as error:  # noqa: BLE001
            message = f"CASO {index} FALHOU: {address}: {error}"
            failures.append(message)
            lines.extend(["", message])

    if "VERDE" not in colors:
        failures.append("A simulacao nao produziu nenhum caso VERDE.")
    if "VERMELHO" not in colors:
        failures.append("A simulacao nao produziu nenhum caso VERMELHO.")

    lines.extend(
        [
            "",
            f"Casos executados: {len(DESTINATIONS)}",
            f"Casos aprovados: {len(DESTINATIONS) - len([item for item in failures if item.startswith('CASO')])}",
            f"Resultado: {'APROVADO' if not failures else 'REPROVADO'}",
        ]
    )
    if failures:
        lines.append("Download estavel bloqueado pelas falhas acima.")

    REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(REPORT_PATH.read_text(encoding="utf-8"))
    return 0 if not failures else 4


if __name__ == "__main__":
    sys.exit(main())
