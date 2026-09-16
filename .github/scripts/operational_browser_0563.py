#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRIPS_ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
BUILD = ROOT / "app/build.gradle.kts"


def patch_trips_activity() -> bool:
    text = TRIPS_ACTIVITY.read_text()
    if "TripScreen.TIMELINE -> OperationalAllTripsBrowserScreen0563(" in text:
        return False

    start_marker = "                TripScreen.TIMELINE -> Column(\n"
    end_marker = "                TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410("
    start = text.find(start_marker)
    end = text.find(end_marker, start + 1)
    if start < 0 or end < 0 or end <= start:
        raise SystemExit("TripsActivity TIMELINE block not found; refusing non-deterministic patch")

    replacement = '''                TripScreen.TIMELINE -> OperationalAllTripsBrowserScreen0563(
                    trips = trips,
                    bookings = bookings,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onMessage = { text -> message = text },
                    onFirstUsableFrame = { renderedItems ->
                        AgendaTrace.reportTimelineFirstUsableFrame(
                            activity = activity,
                            traceId = traceId,
                            renderedItems = renderedItems,
                        ) {
                            if (timelineStartupEnded.compareAndSet(false, true)) {
                                AgendaTrace.operationEnd(
                                    activity,
                                    timelineStartupOperation,
                                    result = "operational_browser_ready",
                                    processedCount = renderedItems,
                                )
                            }
                        }
                    },
                )
'''
    text = text[:start] + replacement + text[end:]
    TRIPS_ACTIVITY.write_text(text)
    return True


def patch_version() -> bool:
    text = BUILD.read_text()
    original = text
    text = text.replace('val releaseVersionCode = 5_852', 'val releaseVersionCode = 5_854', 1)
    text = text.replace('val releaseVersionName = "0.1.561"', 'val releaseVersionName = "0.1.563"', 1)
    if text == original:
        if 'val releaseVersionCode = 5_854' in text and 'val releaseVersionName = "0.1.563"' in text:
            return False
        raise SystemExit("release version baseline not found; refusing ambiguous version patch")
    BUILD.write_text(text)
    return True


changed = []
if patch_trips_activity():
    changed.append(str(TRIPS_ACTIVITY.relative_to(ROOT)))
if patch_version():
    changed.append(str(BUILD.relative_to(ROOT)))

print("operational-browser-0563 patch complete")
for path in changed:
    print("changed:", path)
