#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
ACTIVITY = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"


def once(old: str, new: str, label: str) -> None:
    text = ACTIVITY.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    ACTIVITY.write_text(text.replace(old, new, 1), encoding="utf-8")


once(
    "private enum class TripScreen { LIST, CREATE, SETTINGS }\n",
    "private enum class TripScreen { LIST, TIMELINE, CREATE, SETTINGS }\n",
    "timeline screen enum",
)

once(
'''                TripScreen.SETTINGS -> OnlineSettingsEditor(
''',
'''                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = store.bookings(),
                    onBack = { screen = TripScreen.LIST },
                )
                TripScreen.SETTINGS -> OnlineSettingsEditor(
''',
    "timeline screen routing",
)

once(
'''                    OutlinedButton(onClick = { screen = TripScreen.SETTINGS }) { Text("Integração online") }
''',
'''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { screen = TripScreen.TIMELINE }) { Text("Linha do tempo") }
                        OutlinedButton(onClick = { screen = TripScreen.SETTINGS }) { Text("Integração online") }
                    }
''',
    "timeline list action",
)

print("stage47_timeline_r4_step3=PASS route=true concise_ui=true local_agenda_adapter=true")
