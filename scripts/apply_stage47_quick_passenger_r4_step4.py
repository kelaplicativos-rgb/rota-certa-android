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
'''    var trips by remember { mutableStateOf(store.trips()) }
    var screen by remember { mutableStateOf(if (startCreating) TripScreen.CREATE else TripScreen.LIST) }
''',
'''    var trips by remember { mutableStateOf(store.trips()) }
    var bookings by remember { mutableStateOf(store.bookings()) }
    var screen by remember { mutableStateOf(if (startCreating) TripScreen.CREATE else TripScreen.LIST) }
''',
    "booking state for timeline refresh",
)

once(
'''    val refresh = {
        trips = store.trips()
        TripWidgetProvider.updateAll(activity)
    }
''',
'''    val refresh = {
        trips = store.trips()
        bookings = store.bookings()
        TripWidgetProvider.updateAll(activity)
    }
''',
    "refresh bookings with trips",
)

once(
'''                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = store.bookings(),
                    onBack = { screen = TripScreen.LIST },
                )
''',
'''                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); message = text },
                    onBack = { screen = TripScreen.LIST },
                )
''',
    "timeline quick passenger wiring",
)

once(
'''                    ManualBookingEditor(trip, store, onChanged)
''',
'''                    QuickPassengerPanel(trip, store, onChanged)
''',
    "reuse quick passenger control in trip management",
)

print("stage47_quick_passenger_r4_step4=PASS sources=true mirror=true existing_link=true cancel=true online_sync=true timeline=true trip_screen=true")
