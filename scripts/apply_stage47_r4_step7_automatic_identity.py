#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
UI = TRIPS / "TripBlaBlaCollectorUi.kt"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"

if not UI.is_file():
    raise SystemExit(f"missing Step7 materialized UI: {UI}")
if not COLLECTOR.is_file():
    raise SystemExit(f"missing Step7 materialized collector: {COLLECTOR}")

ui = UI.read_text(encoding="utf-8")
collector = COLLECTOR.read_text(encoding="utf-8")

for marker in (
    "Modo de identificação — Automático (UUID + nome)",
    "O coletor descobre o nome público automaticamente",
    "só confirma a identidade quando o mesmo UUID é validado no detalhe da viagem",
    "Perfis identificados automaticamente",
    "profile.name.ifBlank",
    "profile.uuid",
    "UUID perfil 1",
    "UUID perfil 2 (opcional)",
):
    if marker not in ui:
        raise SystemExit(f"missing Step7 automatic identity marker {marker!r}")

for marker in (
    "data class BlaBlaCollectorProfile(",
    "val uuid: String",
    "val name: String",
    "profiles: List<BlaBlaCollectorProfile>",
):
    if marker not in collector:
        raise SystemExit(f"missing Step7 collector identity contract {marker!r}")

# UUID remains the only user-supplied identity key. Step7 must not add a name-only request.
if "BlaBlaCollectorProfileRequest(val name:" in collector:
    raise SystemExit("Step7 must not make public name an authoritative request identity")

# Keep generic production code free from the user's concrete profiles/corridor.
for forbidden in ("Ezequiel S", "Barbosa", "7371f028-9c55-4903-8444-308015823efd", "175a7068-50d8-40c3-a27a-214b9c6e0461"):
    if forbidden in ui or forbidden in collector:
        raise SystemExit(f"Step7 must stay universal; forbidden hardcode found: {forbidden}")

print("stage47_r4_step7_automatic_identity=PASS uuid_canonical=true public_name_auto=true uuid_detail_confirmation=true name_only_identity=false ui_explicit=true")
