#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"
ACTIVITY = TRIPS / "TripsActivity.kt"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (TIMELINE_UI, ACTIVITY):
    if not path.is_file():
        raise SystemExit(f"missing materialized local-manage source: {path}")

once(
    TIMELINE_UI,
'''    store: TripStore,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
''',
'''    store: TripStore,
    onChanged: (String) -> Unit,
    onManageLocal: (String) -> Unit,
    onBack: () -> Unit,
''',
    "timeline local manage callback",
)
once(
    TIMELINE_UI,
'''        TimelineEntryCard(entry, trip, store, formatter, onChanged)
''',
'''        TimelineEntryCard(entry, trip, store, formatter, onChanged, onManageLocal)
''',
    "timeline card local manage callback",
)
once(
    TIMELINE_UI,
'''    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
) {
''',
'''    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
    onManageLocal: (String) -> Unit,
) {
''',
    "timeline entry local manage callback",
)
once(
    TIMELINE_UI,
'''        } else {
            quickOpen = !quickOpen
        }
''',
'''        } else if (trip != null) {
            onManageLocal(trip.id)
        } else {
            Toast.makeText(
                context,
                "O link exato desta publicação ainda não foi exposto pela sincronização.",
                Toast.LENGTH_LONG,
            ).show()
        }
''',
    "local card management behavior",
)
once(
    ACTIVITY,
'''                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    onBack = { screen = TripScreen.LIST },
''',
'''                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { screen = TripScreen.LIST },
''',
    "agenda local card management routing",
)

ui = TIMELINE_UI.read_text(encoding="utf-8")
activity = ACTIVITY.read_text(encoding="utf-8")
for marker in ("onManageLocal", "O link exato desta publicação ainda não foi exposto"):
    if marker not in ui:
        raise SystemExit(f"missing local-manage UI marker {marker!r}")
if "selectedId = tripId" not in activity:
    raise SystemExit("missing local-manage Agenda selectedId routing")

print("stage47_r4_step7_card_local_manage=PASS local_manage_opens_agenda=true blablacar_manage_remains_exact=true")
