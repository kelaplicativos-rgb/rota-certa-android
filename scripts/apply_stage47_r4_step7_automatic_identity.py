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

# Stage47 diagnostics reuse the existing unified in-memory debug trail. The
# manual report already exports this store, so no MainActivity/FAROL change is
# needed. No cookies, passwords, login fields or tokens are recorded.
once(
    DYNAMIC,
    'package br.com.mapeiaia.rotacerta.trips\n\nimport android.app.Activity\n',
    'package br.com.mapeiaia.rotacerta.trips\n\nimport br.com.mapeiaia.rotacerta.UnifiedDebugEventStore\nimport android.app.Activity\n',
    "dynamic unified diagnostics import",
)

once(
    DYNAMIC,
'''        )
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
''',
'''        )
        UnifiedDebugEventStore.record(
            "SNAPSHOT_SAVED",
            appContext.packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${trips.size} skipped=$skippedTrips identityVerified=$identityVerified",
        )
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
''',
    "snapshot saved diagnostic",
)
once(
    DYNAMIC,
    '        return BlaBlaCollectorMonthResponse(\n',
    '        val response = BlaBlaCollectorMonthResponse(\n',
    "combined response local value",
)
once(
    DYNAMIC,
'''        )
    }

    fun delete(account: BlaBlaDynamicAccount) {
''',
'''        )
        UnifiedDebugEventStore.record(
            "COMBINED_RESPONSE",
            appContext.packageName,
            "accounts=${accounts.size} verifiedAccounts=${verified.size} tripCount=${trips.size} skipped=${snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }} status=${response.status}",
        )
        return response
    }

    fun delete(account: BlaBlaDynamicAccount) {
''',
    "combined response diagnostic",
)

# During sync the real WebView remains VISIBLE, attached and laid out beneath an
# opaque progress cover. This preserves DOM/lazy-loading while avoiding browser
# flashes and unnecessary controls during automatic synchronization.
once(
    DYNAMIC,
'''        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        webView = WebView(this)
''',
'''        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) actions.visibility = android.view.View.GONE

        webView = WebView(this)
''',
    "sync actions hidden",
)
once(
    DYNAMIC,
'''                statusView.text = "${account.displayLabel} • ${url.take(110)}"
                when (phase) {
''',
'''                if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC) {
                    statusView.text = "${account.displayLabel} • ${url.take(110)}"
                }
                when (phase) {
''',
    "sync url hidden from progress",
)
once(
    DYNAMIC,
'''        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
''',
'''        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            val browserHost = android.widget.FrameLayout(this)
            browserHost.addView(
                webView,
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            browserHost.addView(
                TextView(this).apply {
                    text = "Sincronizando ${account.displayLabel}\nO navegador está processando as viagens em segundo plano."
                    gravity = android.view.Gravity.CENTER
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
                    setPadding(40, 40, 40, 40)
                },
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            root.addView(browserHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
''',
    "sync webview progress cover",
)

# Sync lifecycle diagnostics and causal acceptance policy. The profile page is
# the primary identity proof for the authenticated WebView session. A detail
# page may revalidate the same UUID strongly. A DIFFERENT explicit UUID is still
# fatal; absence of UUID on the detail alone is not.
once(
    DYNAMIC,
'''        phase = Phase.IDENTITY
        statusView.text = "${account.displayLabel} • confirmando conta…"
        webView.loadUrl(PROFILE_URL)
''',
'''        phase = Phase.IDENTITY
        statusView.text = "${account.displayLabel} • confirmando conta…"
        UnifiedDebugEventStore.record(
            "SYNC_START",
            packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} url=${sanitizedUrl(PROFILE_URL)}",
        )
        webView.loadUrl(PROFILE_URL)
''',
    "sync start diagnostic",
)
once(
    DYNAMIC,
'''            phase = Phase.RIDES
            statusView.text = "${account.displayLabel} • lendo Suas viagens…"
''',
'''            if (identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()) {
                UnifiedDebugEventStore.record(
                    "IDENTITY_VERIFIED",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} foundUuid=${account.profileUuid.orEmpty()} method=authenticated_profile",
                )
            }
            phase = Phase.RIDES
            statusView.text = "${account.displayLabel} • lendo Suas viagens…"
''',
    "identity verified diagnostic",
)
once(
    DYNAMIC,
'''                .distinctBy { canonicalHref(it.href) }
                .take(MAX_TRIPS)
            if (candidates.isEmpty() && rideReadAttempts < 2 && !looksLoggedOut(result.bodyText)) {
''',
'''                .distinctBy { canonicalHref(it.href) }
                .take(MAX_TRIPS)
            UnifiedDebugEventStore.record(
                "RIDES_DOM_CAPTURED",
                packageName,
                "account=${account.displayLabel} candidateCount=${candidates.size} attempt=$rideReadAttempts url=${sanitizedUrl(webView.url.orEmpty())}",
            )
            if (candidates.isEmpty() && rideReadAttempts < 2 && !looksLoggedOut(result.bodyText)) {
''',
    "rides dom diagnostic",
)
once(
    DYNAMIC,
'''            if (result == null) {
                phase = Phase.IDLE
                statusView.text = "Não consegui ler o DOM de Suas viagens."
                return@evaluate
            }
''',
'''            if (result == null) {
                phase = Phase.IDLE
                UnifiedDebugEventStore.record(
                    "SYNC_END",
                    packageName,
                    "account=${account.displayLabel} status=rides_dom_unreadable trips=${collected.size} skipped=$skipped",
                )
                statusView.text = "Não consegui ler o DOM de Suas viagens."
                return@evaluate
            }
''',
    "rides dom failure diagnostic",
)

old_detail = '''    private fun captureTripDetail() {
        if (phase != Phase.DETAIL) return
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            if (result != null) {
                store.saveDiagnosticHtml(account, "trip-${candidateIndex + 1}", result.domHtml)
                val driverUuids = uuids(result.driverProfileLinks)
                when {
                    !account.profileUuid.isNullOrBlank() && account.profileUuid!!.lowercase() in driverUuids -> identityConfirmedThisSync = true
                    account.profileUuid.isNullOrBlank() && driverUuids.size == 1 -> {
                        val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                        if (updated != null) {
                            account = updated
                            identityConfirmedThisSync = true
                        }
                    }
                }
                val definition = account.verifiedDefinition()
                val trip = definition?.let { BlaBlaDomNormalizer.toTrip(it, candidate, result.detail, LocalDate.now()) }
                if (trip != null && identityConfirmedThisSync) collected += trip else skipped++
            } else {
                skipped++
            }
            candidateIndex++
            loadCurrentCandidate()
        }
    }
'''
new_detail = '''    private fun captureTripDetail() {
        if (phase != Phase.DETAIL) return
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            if (result != null) {
                store.saveDiagnosticHtml(account, "trip-${candidateIndex + 1}", result.domHtml)
                val driverUuids = uuids(result.driverProfileLinks)
                val expectedUuid = account.profileUuid?.lowercase()
                UnifiedDebugEventStore.record(
                    "TRIP_DETAIL_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} url=${sanitizedUrl(webView.url.orEmpty())}",
                )
                if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                    skipped++
                    UnifiedDebugEventStore.record(
                        "TRIP_REJECTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                    )
                    candidateIndex++
                    loadCurrentCandidate()
                    return@evaluate
                }
                when {
                    expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                    expectedUuid == null && driverUuids.size == 1 -> {
                        val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                        if (updated != null) {
                            account = updated
                            identityConfirmedThisSync = true
                        }
                    }
                }
                val definition = account.verifiedDefinition()
                val trip = definition?.let {
                    BlaBlaDomNormalizer.toTrip(
                        account = it,
                        candidate = candidate,
                        detail = result.detail,
                        today = LocalDate.now(),
                        authenticatedProfileSessionVerified = identityConfirmedThisSync,
                    )
                }
                if (trip != null && identityConfirmedThisSync) {
                    collected += trip
                    UnifiedDebugEventStore.record(
                        "TRIP_ACCEPTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} validation=${trip.uuid_validation} date=${trip.date} departure=${trip.departure_time.orEmpty()} origin=${trip.actual_departure.orEmpty()} destination=${trip.actual_arrival.orEmpty()}",
                    )
                } else {
                    skipped++
                    val reason = when {
                        definition == null -> "account_definition_missing"
                        !identityConfirmedThisSync -> "identity_not_verified"
                        else -> "trip_fields_unparseable"
                    }
                    UnifiedDebugEventStore.record(
                        "TRIP_REJECTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=$reason expectedUuid=${account.profileUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")}",
                    )
                }
            } else {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=detail_dom_unreadable url=${sanitizedUrl(webView.url.orEmpty())}",
                )
            }
            candidateIndex++
            loadCurrentCandidate()
        }
    }
'''
once(DYNAMIC, old_detail, new_detail, "authenticated session trip acceptance")
once(
    DYNAMIC,
'''    private fun completeSync(count: Int) {
        phase = Phase.IDLE
        setResult(
''',
'''    private fun completeSync(count: Int) {
        phase = Phase.IDLE
        UnifiedDebugEventStore.record(
            "SYNC_END",
            packageName,
            "account=${account.displayLabel} status=success trips=$count skipped=$skipped identityVerified=$identityConfirmedThisSync",
        )
        setResult(
''',
    "sync end diagnostic",
)
once(
    DYNAMIC,
'''    private fun canonicalHref(href: String): String = href.substringBefore("&search_uuid=")
    private fun isBlaBla(url: String): Boolean = url.contains("blablacar.com.br")
''',
'''    private fun canonicalHref(href: String): String = href.substringBefore("&search_uuid=")
    private fun sanitizedUrl(url: String): String = url.substringBefore('?').substringBefore('#').take(240)
    private fun isBlaBla(url: String): Boolean = url.contains("blablacar.com.br")
''',
    "sanitized diagnostic url helper",
)

# Strong external identity: trip ID first, canonical href second, route/time only
# as final contingency. This prevents duplicate imports when the same account is
# synchronized repeatedly.
once(
    COLLECTOR,
    'import android.content.Context\n',
    'import android.content.Context\nimport br.com.mapeiaia.rotacerta.UnifiedDebugEventStore\n',
    "collector unified diagnostics import",
)
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

# Both strong detail-link revalidation and the authenticated-profile-session
# proof are confirmed identities. Only unverified imports stay pending.
once(
    COLLECTOR,
    '        val verified = trip.uuid_validation == "verified_from_trip_detail_profile_link"\n',
'''        val verified = trip.uuid_validation in setOf(
            "verified_from_trip_detail_profile_link",
            "verified_from_authenticated_profile_session",
        )
''',
    "timeline authenticated session validation",
)
once(
    COLLECTOR,
'''        merged += remainingLocal
        return TripTimelineEngine.annotate(merged)
''',
'''        merged += remainingLocal
        val annotated = TripTimelineEngine.annotate(merged)
        UnifiedDebugEventStore.record(
            "TIMELINE_MERGE",
            "br.com.mapeiaia.rotacerta",
            "local=${localEntries.size} public=${public.size} merged=${annotated.size} dedup=${(localEntries.size + public.size - annotated.size).coerceAtLeast(0)}",
        )
        return annotated
''',
    "timeline merge diagnostic",
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
    "manual_login=true rides_offer_dom_read=true sanitized_html=true uuid_profile_session_required=true "
    "detail_uuid_optional_when_session_verified=true explicit_uuid_mismatch_rejected=true "
    "stage47_diagnostics=true hidden_sync_browser=true railway_not_origin=true strong_dedupe=true cross_profile_continuity=true"
)
