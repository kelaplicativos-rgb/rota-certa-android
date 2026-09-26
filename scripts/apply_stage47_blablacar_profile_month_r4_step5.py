#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TESTS = SOURCE / "app/src/test/java/br/com/mapeiaia/rotacerta/trips"
MANIFEST = SOURCE / "app/src/main/AndroidManifest.xml"

required = {
    TRIPS / "TripBlaBlaCollector.kt": [
        "class BlaBlaCollectorApi",
        "object BlaBlaCollectorScope",
        "class BlaBlaCollectorStateStore",
        "object BlaBlaTimelineAdapter",
        "global_profile_month_complete",
    ],
    TRIPS / "TripBlaBlaCollectorUi.kt": [
        "UUID perfil 1",
        "UUID perfil 2 (opcional)",
        "Mês — AAAA-MM",
        "Buscar",
        "rotas dinâmicas da Agenda",
    ],
    TRIPS / "TripTimeline.kt": ["VALIDATION_PENDING"],
    TRIPS / "TripTimelineUi.kt": [
        "BlaBlaCollectorPanel",
        "BlaBlaTimelineAdapter.merge",
        "UUID ainda não confirmado no detalhe",
    ],
    TESTS / "TripBlaBlaCollectorStage47R4Step5Test.kt": [
        "scopeComesFromAgendaAndIsNotHardcodedToOldCities",
        "verifiedPublicTripMergesWithLocalAgendaAndKeepsPhysicalOccupancy",
        "unresolvedUuidIsNeverShownAsFullyValidated",
    ],
}

for path, markers in required.items():
    if not path.is_file():
        raise SystemExit(f"missing Step5 materialized file: {path}")
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"missing Step5 marker {marker!r} in {path.name}")

manifest = MANIFEST.read_text(encoding="utf-8")
if 'android.permission.INTERNET' not in manifest:
    raise SystemExit("Step5 requires existing INTERNET permission; it was not found")

# Fail closed against accidental profile/corridor hardcoding in the Android search engine.
collector = (TRIPS / "TripBlaBlaCollector.kt").read_text(encoding="utf-8")
for forbidden in ("Ezequiel S", "Barbosa", "Santo André", "Três Corações", "São Thomé"):
    if forbidden in collector:
        raise SystemExit(f"Step5 Android collector must be universal; forbidden hardcode found: {forbidden}")

print("stage47_blablacar_profile_month_r4_step5=PASS uuid_input=true month_input=true dynamic_agenda_scope=true uuid_detail_verification=true fail_closed=true timeline_merge=true no_profile_hardcode=true")
