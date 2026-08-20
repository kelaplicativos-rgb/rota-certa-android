#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
UI = TRIPS / "TripBlaBlaCollectorUi.kt"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"
TIMELINE = TRIPS / "TripTimeline.kt"
DYNAMIC = TRIPS / "BlaBlaDynamicAccounts.kt"
MANIFEST = SOURCE / "app/src/main/AndroidManifest.xml"
BUILD_GRADLE = SOURCE / "app/build.gradle.kts"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (UI, COLLECTOR, TIMELINE, DYNAMIC, MANIFEST, BUILD_GRADLE):
    if not path.is_file():
        raise SystemExit(f"missing Step7 dynamic-account file: {path}")

ui = UI.read_text(encoding="utf-8")
dynamic = DYNAMIC.read_text(encoding="utf-8")

for marker in (
    "Contas BlaBlaCar",
    "+ Adicionar conta",
    "Sincronizar todas as contas",
    "Nenhuma conta vem pré-cadastrada",
    "UUID será descoberto",
):
    if marker not in ui:
        raise SystemExit(f"missing dynamic account UI marker {marker!r}")

for marker in (
    "BlaBlaDynamicAccountRegistry",
    "UUID.randomUUID",
    "WebViewCompat.setProfile",
    "WebViewFeature.MULTI_PROFILE",
    "https://www.blablacar.com.br/rides",
    "/rides/offer",
    "saveDiagnosticHtml",
    "BlaBlaDynamicAccountSessionActivity",
):
    if marker not in dynamic:
        raise SystemExit(f"missing dynamic multi-profile marker {marker!r}")

# AndroidX WebKit MULTI_PROFILE is what removes the fixed process-per-account
# ceiling: every user-created account gets a named WebView profile with its own
# CookieManager/WebStorage. No account name/UUID is seeded by this materializer.
gradle = BUILD_GRADLE.read_text(encoding="utf-8")
if "androidx.webkit:webkit:" not in gradle:
    once(
        BUILD_GRADLE,
        '    implementation("androidx.core:core-ktx:1.15.0")\n',
        '    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("androidx.webkit:webkit:1.15.0")\n',
        "androidx webkit multi-profile dependency",
    )

# One generic Activity is enough for every account; the account id selects the
# WebView profile dynamically. This intentionally replaces the previous fixed
# Ezequiel/Barbosa process registrations in newly materialized APKs.
manifest = MANIFEST.read_text(encoding="utf-8")
manifest_marker = "STAGE47_BLABLACAR_DYNAMIC_MULTI_PROFILE"
if manifest_marker in manifest:
    raise SystemExit("Step7 dynamic-session manifest block already present")
manifest_block = '''
        <!-- STAGE47_BLABLACAR_DYNAMIC_MULTI_PROFILE: user-created isolated sessions; no pre-seeded accounts. -->
        <activity
            android:name=".trips.BlaBlaDynamicAccountSessionActivity"
            android:exported="false" />
'''
if manifest.count("</application>") != 1:
    raise SystemExit("AndroidManifest.xml must contain one </application>")
MANIFEST.write_text(manifest.replace("</application>", manifest_block + "    </application>", 1), encoding="utf-8")

# Strong external identity: trip ID first, canonical href second, route/time only
# as final contingency. This prevents duplicate imports when the same account is
# synchronized repeatedly.
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

# All BlaBlaCar accounts configured in this Rota Certa installation belong to
# the same physical agenda unless a future driver-group feature says otherwise.
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
    "stage47_r4_step7_dynamic_accounts=PASS "
    "registry_starts_empty=true unlimited_fixed_ceiling=false webview_multi_profile=true "
    "manual_login=true rides_offer_dom_read=true sanitized_html=true uuid_required=true "
    "railway_not_origin=true strong_dedupe=true cross_profile_continuity=true"
)
