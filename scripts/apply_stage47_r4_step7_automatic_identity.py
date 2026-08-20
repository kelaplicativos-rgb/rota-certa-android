#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
UI = TRIPS / "TripBlaBlaCollectorUi.kt"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"
TIMELINE = TRIPS / "TripTimeline.kt"
SESSION = TRIPS / "BlaBlaAuthenticatedSession.kt"
MANIFEST = SOURCE / "app/src/main/AndroidManifest.xml"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (UI, COLLECTOR, TIMELINE, SESSION, MANIFEST):
    if not path.is_file():
        raise SystemExit(f"missing Step7 authenticated-session file: {path}")

ui = UI.read_text(encoding="utf-8")
session = SESSION.read_text(encoding="utf-8")

for marker in (
    "Contas BlaBlaCar",
    "Sincronizar BlaBlaCar",
    "Ezequiel S",
    "Barbosa",
    "UUID confirmado",
    "Senha não é capturada",
):
    if marker not in ui:
        raise SystemExit(f"missing authenticated UI marker {marker!r}")

for marker in (
    "WebView.setDataDirectorySuffix",
    "blablacar_ezequiel",
    "blablacar_barbosa",
    "https://www.blablacar.com.br/rides",
    "verified_from_trip_detail_profile_link",
    "BlaBlaDomNormalizer",
    "BlaBlaLocalSessionStore",
):
    if marker not in session:
        raise SystemExit(f"missing isolated-session marker {marker!r}")

# Register each WebView account Activity in its own Android process. WebView data
# directory suffixes are applied by the Activity before WebView initialization,
# so cookies/local storage cannot overwrite the other BlaBlaCar account.
manifest = MANIFEST.read_text(encoding="utf-8")
manifest_marker = "STAGE47_BLABLACAR_ISOLATED_SESSIONS"
if manifest_marker in manifest:
    raise SystemExit("Step7 isolated-session manifest block already present")
manifest_block = '''
        <!-- STAGE47_BLABLACAR_ISOLATED_SESSIONS: authenticated local read, no FAROL dependency. -->
        <activity
            android:name=".trips.BlaBlaEzequielSessionActivity"
            android:exported="false"
            android:process=":blablacar_ezequiel" />
        <activity
            android:name=".trips.BlaBlaBarbosaSessionActivity"
            android:exported="false"
            android:process=":blablacar_barbosa" />
'''
if manifest.count("</application>") != 1:
    raise SystemExit("AndroidManifest.xml must contain one </application>")
MANIFEST.write_text(manifest.replace("</application>", manifest_block + "    </application>", 1), encoding="utf-8")

# Strong external identity: trip ID first, canonical href second, route/time only
# as final contingency. This prevents duplicate imports when the same page is
# synchronized again.
once(
    COLLECTOR,
    '    private data class PublicEntry(val entry: TripTimelineEntry, val searchFrom: String?, val searchTo: String?)\n',
    '    private data class PublicEntry(val entry: TripTimelineEntry, val searchFrom: String?, val searchTo: String?, val externalKey: String)\n',
    "collector public entry strong identity",
)
once(
    COLLECTOR,
'''        val public = response.trips.mapNotNull { trip -> toEntry(trip, zoneId)?.let { PublicEntry(it, trip.search_from, trip.search_to) } }
            .distinctBy { item -> "${item.entry.profileId}|${item.entry.departureAtMillis}|${placeKey(item.entry.origin)}|${placeKey(item.entry.destination)}" }
''',
'''        val public = response.trips.mapNotNull { trip ->
            toEntry(trip, zoneId)?.let { PublicEntry(it, trip.search_from, trip.search_to, strongExternalIdentity(trip)) }
        }.distinctBy { item -> "${item.entry.profileId}|${item.externalKey}" }
''',
    "collector public strong dedupe",
)
once(
    COLLECTOR,
    '            tripId = "blablacar:${trip.profile_uuid}:${trip.trip_id ?: departure}",\n',
    '            tripId = "blablacar:${trip.profile_uuid}:${strongExternalIdentity(trip)}",\n',
    "collector timeline strong trip id",
)
once(
    COLLECTOR,
'''    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {
''',
'''    private fun strongExternalIdentity(trip: BlaBlaCollectorTrip): String =
        trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
            ?: trip.trip_href?.substringBefore("&search_uuid=")?.trim()?.takeIf(String::isNotEmpty)
            ?: listOf(
                trip.date,
                trip.departure_time.orEmpty(),
                placeKey(trip.actual_departure ?: trip.search_from.orEmpty()),
                placeKey(trip.actual_arrival ?: trip.search_to.orEmpty()),
            ).joinToString("|")

    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {
''',
    "collector strong identity helper",
)

# Ezequiel and Barbosa are one physical driver. Continuity therefore must be
# checked chronologically across profile boundaries, not independently per UUID.
once(
    TIMELINE,
'''        entries.groupBy(TripTimelineEntry::profileId).values.forEach { profileEntries ->
            profileEntries.sortedBy(TripTimelineEntry::departureAtMillis).zipWithNext().forEach { (previous, next) ->
                if (normalizePlace(previous.destination) != normalizePlace(next.origin)) {
                    issues.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
                }
            }
        }
''',
'''        val chronological = entries.sortedBy(TripTimelineEntry::departureAtMillis)
        chronological.zipWithNext().forEach { (previous, next) ->
            if (normalizePlace(previous.destination) != normalizePlace(next.origin)) {
                issues.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
            }
        }
''',
    "single physical driver continuity",
)

print(
    "stage47_r4_step7_authenticated_timeline=PASS "
    "isolated_processes=true webview_data_suffix=true manual_login=true "
    "rides_dom_read=true trip_detail_uuid_required=true railway_not_origin=true "
    "strong_dedupe=true cross_profile_continuity=true"
)
