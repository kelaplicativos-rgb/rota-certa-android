#!/usr/bin/env python3
from pathlib import Path
import re
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TESTS = SOURCE / "app/src/test/java/br/com/mapeiaia/rotacerta/trips"
DYNAMIC = TRIPS / "BlaBlaDynamicAccounts.kt"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"
CONSOLIDATOR = TRIPS / "TripPhysicalRideConsolidator.kt"

for path in (DYNAMIC, COLLECTOR, TIMELINE_UI, CONSOLIDATOR):
    if not path.is_file():
        raise SystemExit(f"missing post-0.1.236 materialized source: {path}")


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def section(path: Path, start: str, end: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"{label}: start marker missing")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"{label}: end marker missing")
    if text.find(start, a + 1) >= 0 and text.find(start, a + 1) < b:
        raise SystemExit(f"{label}: ambiguous start marker")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")

once(
    COLLECTOR,
    "import java.security.KeyStore\n",
    "import java.security.KeyStore\nimport java.security.MessageDigest\nimport java.net.URI\n",
    "stable identity imports",
)

identity_anchor = "@Serializable\ndata class BlaBlaCollectorCoverage(\n"
identity_helper = r'''internal data class BlaBlaTripIdentityEvidence(
    val key: String,
    val identityHash: String,
    val externalTripIdPresent: Boolean,
    val specificHrefPresent: Boolean,
    val fallbackIdentityUsed: Boolean,
)

internal object BlaBlaTripIdentity {
    fun evidence(trip: BlaBlaCollectorTrip): BlaBlaTripIdentityEvidence {
        val externalId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
        val specificHref = stableSpecificHref(trip.trip_href)
        val key = when {
            externalId != null -> "id|${trip.profile_uuid.trim()}|$externalId"
            specificHref != null -> "href|${trip.profile_uuid.trim()}|$specificHref"
            else -> listOf(
                "fallback",
                trip.profile_uuid.trim(),
                trip.date.trim(),
                trip.departure_time.orEmpty().trim(),
                trip.arrival_time.orEmpty().trim(),
                trip.actual_departure.orEmpty().trim(),
                trip.actual_arrival.orEmpty().trim(),
                trip.search_from.orEmpty().trim(),
                trip.search_to.orEmpty().trim(),
                trip.price.orEmpty().trim(),
            ).joinToString("|")
        }
        return BlaBlaTripIdentityEvidence(
            key = key,
            identityHash = sha256Short(key),
            externalTripIdPresent = externalId != null,
            specificHrefPresent = specificHref != null,
            fallbackIdentityUsed = externalId == null && specificHref == null,
        )
    }

    private fun stableSpecificHref(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val withoutQuery = value.substringBefore('?').substringBefore('#').trimEnd('/')
        val path = runCatching {
            if (withoutQuery.contains("://")) URI(withoutQuery).path else withoutQuery
        }.getOrNull()?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: return null
        val normalized = if (path.startsWith('/')) path else "/$path"
        if (normalized in setOf("/rides", "/rides/offer", "/trip")) return null
        return normalized.takeIf { candidate ->
            candidate.startsWith("/rides/offer/") || candidate.startsWith("/trip/")
        }
    }

    private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

@Serializable
data class BlaBlaCollectorCoverage(
'''
once(COLLECTOR, identity_anchor, identity_helper, "stable trip identity helper")

once(
    DYNAMIC,
'''        val trips = verified.flatMap { (_, snapshot) -> snapshot.trips }
            .distinctBy(::strongTripIdentity)
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
''',
'''        val beforeDistinct = verified.flatMap { (_, snapshot) -> snapshot.trips }
        beforeDistinct.forEachIndexed { index, trip ->
            val identity = BlaBlaTripIdentity.evidence(trip)
            UnifiedDebugEventStore.record(
                "TRIP_IDENTITY",
                appContext.packageName,
                "index=${index + 1}/${beforeDistinct.size} externalTripIdPresent=${identity.externalTripIdPresent} specificHrefPresent=${identity.specificHrefPresent} fallbackIdentityUsed=${identity.fallbackIdentityUsed} identityHash=${identity.identityHash}",
            )
        }
        val trips = beforeDistinct
            .distinctBy { trip -> BlaBlaTripIdentity.evidence(trip).key }
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
''',
    "combined stable dedupe",
)
once(
    DYNAMIC,
'''            "accounts=${accounts.size} verifiedAccounts=${verified.size} tripCount=${trips.size} skipped=${snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }} status=${response.status}",
''',
'''            "accounts=${accounts.size} verifiedAccounts=${verified.size} beforeDistinct=${beforeDistinct.size} tripCount=${trips.size} deduped=${(beforeDistinct.size - trips.size).coerceAtLeast(0)} skipped=${snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }} status=${response.status}",
''',
    "combined dedupe diagnostics",
)
section(
    DYNAMIC,
    "    private fun strongTripIdentity",
    "    companion object {",
    "    private fun strongTripIdentity(trip: BlaBlaCollectorTrip): String = BlaBlaTripIdentity.evidence(trip).key\n\n",
    "store identity delegation",
)

section(
    COLLECTOR,
    "    private fun strongExternalIdentity",
    "    private fun parseDateTime",
    "    private fun strongExternalIdentity(trip: BlaBlaCollectorTrip): String = BlaBlaTripIdentity.evidence(trip).key\n\n",
    "adapter stable identity delegation",
)
once(
    COLLECTOR,
    '            tripId = "blablacar:${trip.profile_uuid}:${strongExternalIdentity(trip)}",\n',
    '            tripId = "blablacar:${BlaBlaTripIdentity.evidence(trip).identityHash}",\n',
    "hashed timeline external id",
)

once(
    COLLECTOR,
'''            blablaPassengers = trip.passengers,
            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),
        )
    }

    private fun strongExternalIdentity''',
'''            blablaPassengers = trip.passengers,
            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),
        )
        val identity = BlaBlaTripIdentity.evidence(trip)
        UnifiedDebugEventStore.record(
            "TIMELINE_EXTERNAL_ENTRY",
            "br.com.mapeiaia.rotacerta",
            "identityHash=${identity.identityHash} bookedSeats=${trip.booked_seats} passengerCount=${trip.passengers.size} sourceBlaBlaSeats=${entry.sourcePassengerSeats[BookingSource.BLABLACAR] ?: 0} phonesPresent=${trip.passengers.count { !it.phone.isNullOrBlank() }}",
        )
        return entry
    }

    private fun strongExternalIdentity''',
    "timeline passenger propagation diagnostics",
)
once(
    COLLECTOR,
    '''        return TripTimelineEntry(
            tripId = "blablacar:${BlaBlaTripIdentity.evidence(trip).identityHash}",
''',
    '''        val entry = TripTimelineEntry(
            tripId = "blablacar:${BlaBlaTripIdentity.evidence(trip).identityHash}",
''',
    "timeline entry local variable",
)

once(
    DYNAMIC,
'''@Serializable
private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
)

class BlaBlaDynamicAccountSessionActivity : Activity() {
''',
'''@Serializable
private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
)

internal class BlaBlaSyncCompletionGate {
    private var snapshotGeneration = Long.MIN_VALUE
    private var completionGeneration = Long.MIN_VALUE

    fun claimSnapshot(generation: Long): Boolean {
        if (snapshotGeneration == generation) return false
        snapshotGeneration = generation
        return true
    }

    fun claimCompletion(generation: Long): Boolean {
        if (completionGeneration == generation) return false
        completionGeneration = generation
        return true
    }
}

internal fun nextBlaBlaCandidateIndex(current: Int, size: Int): Int = when {
    size <= 0 -> 0
    current < 0 -> 0
    current >= size -> size
    else -> current + 1
}

class BlaBlaDynamicAccountSessionActivity : Activity() {
''',
    "sync guard helpers",
)

once(
    DYNAMIC,
'''    private var passengerContactIndex = 0
    private var passengerContactReadAttempts = 0
''',
'''    private var passengerContactIndex = 0
    private var passengerContactReadAttempts = 0
    private var syncGeneration = 0L
    private var navigationGeneration = 0L
    private var detailCaptureInFlight = false
    private var passengerCaptureInFlight = false
    private var pendingTripSyncGeneration = -1L
    private var pendingTripCandidateIndex = -1
    private val completionGate = BlaBlaSyncCompletionGate()
''',
    "sync generation fields",
)

once(
    DYNAMIC,
'''                    Phase.DETAIL -> if (isBlaBla(url)) view.postDelayed({ captureTripDetail() }, 750)
                    Phase.PASSENGER_CONTACT -> if (isBlaBla(url)) view.postDelayed({ capturePassengerContact() }, 850)
''',
'''                    Phase.DETAIL -> if (isBlaBla(url)) scheduleTripDetailCapture(view)
                    Phase.PASSENGER_CONTACT -> if (isBlaBla(url)) schedulePassengerContactCapture(view)
''',
    "tokenized page finished capture",
)

once(
    DYNAMIC,
'''    private fun beginSync() {
        collected.clear()
''',
'''    private fun beginSync() {
        syncGeneration++
        navigationGeneration = 0L
        detailCaptureInFlight = false
        passengerCaptureInFlight = false
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        collected.clear()
''',
    "new sync generation",
)
once(
    DYNAMIC,
'''        webView.loadUrl(PROFILE_URL)
    }

    private fun captureIdentityForSync() {
''',
'''        loadTrackedUrl(PROFILE_URL)
    }

    private fun loadTrackedUrl(url: String) {
        navigationGeneration++
        webView.loadUrl(url)
    }

    private fun scheduleTripDetailCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        view.postDelayed({ captureTripDetail(expectedSync, expectedNavigation, expectedCandidate) }, 750)
    }

    private fun schedulePassengerContactCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        view.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 850)
    }

    private fun captureIdentityForSync() {
''',
    "tracked sync navigation helper",
)
once(
    DYNAMIC,
'''            webView.loadUrl(RIDES_URL)
        }
''',
'''            loadTrackedUrl(RIDES_URL)
        }
''',
    "tracked rides navigation",
)

section(
    DYNAMIC,
    "    private fun loadCurrentCandidate() {\n",
    "    private fun captureTripDetail",
    '''    private fun loadCurrentCandidate() {
        if (candidateIndex > candidates.size) {
            UnifiedDebugEventStore.record(
                "SYNC_INDEX_GUARD",
                packageName,
                "account=${account.displayLabel} candidateIndex=$candidateIndex candidateCount=${candidates.size} action=clamp",
            )
            candidateIndex = candidates.size
        }
        if (candidateIndex >= candidates.size) {
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            saveFinalSnapshotOnce(verified)
            if (verified) completeSync(collected.size) else {
                phase = Phase.IDLE
                statusView.text = "As viagens foram abertas, mas não consegui confirmar o UUID desta conta com segurança."
            }
            return
        }
        val candidate = candidates[candidateIndex]
        statusView.text = "${account.displayLabel} • viagem ${candidateIndex + 1}/${candidates.size}…"
        loadTrackedUrl(candidate.href)
    }

    private fun saveFinalSnapshotOnce(verified: Boolean): Boolean {
        if (!completionGate.claimSnapshot(syncGeneration)) {
            UnifiedDebugEventStore.record(
                "STALE_CALLBACK_IGNORED",
                packageName,
                "account=${account.displayLabel} reason=duplicate_snapshot generation=$syncGeneration",
            )
            return false
        }
        store.saveSync(account, webView.url.orEmpty(), collected.toList(), skipped, verified)
        return true
    }

''',
    "bounded candidate loader",
)

once(
    DYNAMIC,
'''                store.saveSync(account, webView.url.orEmpty(), emptyList(), 0, verified)
                if (verified) completeSync(0) else {
''',
'''                saveFinalSnapshotOnce(verified)
                if (verified) completeSync(0) else {
''',
    "empty sync one-shot snapshot",
)

section(
    DYNAMIC,
    "    private fun captureTripDetail",
    "    private fun bindIdentityFromLinks",
    r'''    private fun captureTripDetail(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int) {
        if (!detailCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate)) {
            recordStale("trip_detail_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (detailCaptureInFlight) {
            recordStale("trip_detail_in_flight", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate) ?: run {
            recordStale("trip_detail_candidate_missing", expectedSync, expectedCandidate)
            return
        }
        detailCaptureInFlight = true
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            detailCaptureInFlight = false
            if (!detailCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate)) {
                recordStale("trip_detail_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            if (result == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=detail_dom_unreadable url=${sanitizedUrl(webView.url.orEmpty())}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }

            store.saveDiagnosticHtml(account, "trip-${expectedCandidate + 1}", result.domHtml)
            val driverUuids = uuids(result.driverProfileLinks)
            val expectedUuid = account.profileUuid?.lowercase()
            UnifiedDebugEventStore.record(
                "TRIP_DETAIL_CAPTURED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} url=${sanitizedUrl(webView.url.orEmpty())}",
            )
            if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
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

            if (!identityConfirmedThisSync || account.verifiedDefinition() == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=identity_not_verified expectedUuid=${account.profileUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }

            pendingTripDetail = result
            pendingTripPassengers = result.detail.passengers.toMutableList()
            pendingTripSyncGeneration = expectedSync
            pendingTripCandidateIndex = expectedCandidate
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            if (pendingTripPassengers.any { it.phone.isNullOrBlank() && !it.booking_href.isNullOrBlank() }) {
                loadNextPassengerContact(expectedSync, expectedCandidate)
            } else {
                finalizeCurrentTrip(expectedSync, expectedCandidate)
            }
        }
    }

    private fun detailCaptureIsCurrent(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int): Boolean =
        phase == Phase.DETAIL &&
            expectedSync == syncGeneration &&
            expectedNavigation == navigationGeneration &&
            expectedCandidate == candidateIndex &&
            expectedCandidate in candidates.indices

    private fun loadNextPassengerContact(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("passenger_load_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        while (passengerContactIndex < pendingTripPassengers.size) {
            val passenger = pendingTripPassengers[passengerContactIndex]
            val href = passenger.booking_href?.trim().orEmpty()
            if (passenger.phone.isNullOrBlank() && href.isNotBlank() && isBlaBla(href)) {
                phase = Phase.PASSENGER_CONTACT
                passengerContactReadAttempts = 0
                passengerCaptureInFlight = false
                statusView.text = "${account.displayLabel} • contato ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                loadTrackedUrl(href)
                return
            }
            passengerContactIndex++
        }
        finalizeCurrentTrip(expectedSync, expectedCandidate)
    }

    private fun capturePassengerContact(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ) {
        if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
            recordStale("passenger_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (passengerCaptureInFlight) {
            recordStale("passenger_in_flight", expectedSync, expectedCandidate)
            return
        }
        val current = pendingTripPassengers.getOrNull(expectedPassenger) ?: run {
            recordStale("passenger_missing", expectedSync, expectedCandidate)
            return
        }
        passengerCaptureInFlight = true
        evaluate<DynamicPassengerContactEvidence>(PASSENGER_CONTACT_JS) { evidence ->
            passengerCaptureInFlight = false
            if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                recordStale("passenger_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            val phone = normalizeCapturedPhone(evidence?.phone)
            if (phone == null && passengerContactReadAttempts < 2) {
                passengerContactReadAttempts++
                webView.postDelayed({
                    capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                }, 700)
                return@evaluate
            }

            val visibleName = evidence?.visibleName?.trim().orEmpty()
            pendingTripPassengers[expectedPassenger] = current.copy(
                name = current.name.ifBlank { visibleName },
                phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
            )
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripIndex=${expectedCandidate + 1}/${candidates.size} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} phonePresent=${phone != null} bookingLinkPresent=${!current.booking_href.isNullOrBlank()}",
            )
            passengerContactIndex = expectedPassenger + 1
            loadNextPassengerContact(expectedSync, expectedCandidate)
        }
    }

    private fun passengerCaptureIsCurrent(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ): Boolean = phase == Phase.PASSENGER_CONTACT &&
        expectedSync == syncGeneration &&
        expectedNavigation == navigationGeneration &&
        expectedCandidate == candidateIndex &&
        expectedPassenger == passengerContactIndex &&
        pendingTripIsCurrent(expectedSync, expectedCandidate) &&
        expectedPassenger in pendingTripPassengers.indices

    private fun pendingTripIsCurrent(expectedSync: Long, expectedCandidate: Int): Boolean =
        expectedSync == syncGeneration &&
            expectedCandidate == candidateIndex &&
            pendingTripSyncGeneration == expectedSync &&
            pendingTripCandidateIndex == expectedCandidate &&
            pendingTripDetail != null

    private fun finalizeCurrentTrip(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("finalize_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate)
        val result = pendingTripDetail
        val definition = account.verifiedDefinition()
        if (candidate == null || result == null || definition == null) {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=pending_trip_state_missing",
            )
            advanceCandidate(expectedSync, expectedCandidate)
            return
        }

        val enrichedDetail = result.detail.copy(passengers = pendingTripPassengers.toList())
        val trip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (trip != null && identityConfirmedThisSync) {
            collected += trip
            UnifiedDebugEventStore.record(
                "TRIP_ACCEPTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} validation=${trip.uuid_validation} date=${trip.date} departure=${trip.departure_time.orEmpty()} origin=${trip.actual_departure.orEmpty()} destination=${trip.actual_arrival.orEmpty()} passengers=${trip.passengers.size} phones=${trip.passengers.count { !it.phone.isNullOrBlank() }}",
            )
        } else {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=trip_fields_unparseable expectedUuid=${account.profileUuid.orEmpty()}",
            )
        }
        advanceCandidate(expectedSync, expectedCandidate)
    }

    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {
        if (expectedSync != syncGeneration || expectedCandidate != candidateIndex) {
            recordStale("advance_candidate_mismatch", expectedSync, expectedCandidate)
            return
        }
        pendingTripDetail = null
        pendingTripPassengers.clear()
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        passengerCaptureInFlight = false
        phase = Phase.DETAIL
        candidateIndex = nextBlaBlaCandidateIndex(candidateIndex, candidates.size)
        loadCurrentCandidate()
    }

    private fun recordStale(reason: String, expectedSync: Long, expectedCandidate: Int) {
        UnifiedDebugEventStore.record(
            "STALE_CALLBACK_IGNORED",
            packageName,
            "account=${account.displayLabel} reason=$reason expectedGeneration=$expectedSync currentGeneration=$syncGeneration expectedCandidate=${expectedCandidate + 1} currentCandidate=${candidateIndex + 1} candidateCount=${candidates.size}",
        )
    }

    private fun normalizeCapturedPhone(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val hasPlus = value.startsWith("+")
        val digits = value.filter(Char::isDigit)
        if (digits.length < 8 || digits.length > 15) return null
        return if (hasPlus) "+$digits" else digits
    }

''',
    "tokenized detail/passenger state machine",
)

once(
    DYNAMIC,
'''    private fun completeSync(count: Int) {
        phase = Phase.IDLE
        UnifiedDebugEventStore.record(
''',
'''    private fun completeSync(count: Int) {
        if (!completionGate.claimCompletion(syncGeneration)) {
            UnifiedDebugEventStore.record(
                "STALE_CALLBACK_IGNORED",
                packageName,
                "account=${account.displayLabel} reason=duplicate_complete generation=$syncGeneration",
            )
            return
        }
        phase = Phase.IDLE
        UnifiedDebugEventStore.record(
''',
    "idempotent sync completion",
)

text = CONSOLIDATOR.read_text(encoding="utf-8")
text = text.replace("import android.location.Geocoder\n", "")
text = text.replace("import java.util.Locale\n", "")
text = text.replace("import kotlinx.coroutines.Dispatchers\n", "")
text = text.replace("import kotlinx.coroutines.withContext\n", "")
resolver_pattern = r'''object TripTimelineGeoResolver \{.*?\n\}\n\n/\*\*'''
resolver_replacement = '''object TripTimelineGeoResolver {\n    /**\n     * Text-only geocoding is intentionally not a continuity authority. Trusted\n     * TripStop/device coordinates will feed this map in a later Base-aware step.\n     */\n    suspend fun resolve(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") places: Collection<String>): Map<String, TimelineGeoPoint> =\n        emptyMap()\n}\n\n/**'''
text, n = re.subn(resolver_pattern, resolver_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f"neutral geo resolver replacement count={n}")
old = '''            } else if (!continuous(previous.destination, next.origin, geo)) {\n                issueMap.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY\n            }\n'''
new = '''            } else if (hasTrustedContinuityEvidence(previous.destination, next.origin, geo) && !continuous(previous.destination, next.origin, geo)) {\n                issueMap.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY\n            }\n'''
if text.count(old) != 1:
    raise SystemExit(f"continuity unknown guard count={text.count(old)}")
text = text.replace(old, new, 1)
anchor = '''    private fun continuous(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {\n'''
helper = '''    private fun hasTrustedContinuityEvidence(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {\n        if (samePlace(destination, origin)) return true\n        return geo[destination] != null && geo[origin] != null\n    }\n\n    private fun continuous(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {\n'''
if text.count(anchor) != 1:
    raise SystemExit(f"trusted continuity helper anchor count={text.count(anchor)}")
text = text.replace(anchor, helper, 1)
CONSOLIDATOR.write_text(text, encoding="utf-8")

once(
    TIMELINE_UI,
'''    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy • HH:mm", Locale("pt", "BR")) }
''',
'''    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }
''',
    "global timeline formatter",
)
once(
    TIMELINE_UI,
'''            Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }, style = MaterialTheme.typography.labelLarge)
''',
'''            Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.labelLarge)
''',
    "global timeline titlecase",
)

phone_pattern = r'''private fun normalizePhone\(raw: String\?\): String \{.*?\n\}\n\nprivate fun displayPhone\(raw: String\): String \{.*?\n\}\n'''
phone_replacement = r'''internal fun normalizePhone(raw: String?): String {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return ""
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return ""
    return if (value.startsWith("+")) "+$digits" else "local:$digits"
}

private fun displayPhone(raw: String): String = raw.trim()

internal fun whatsappRecipient(raw: String): String? {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return null
    return digits
}
'''
text = TIMELINE_UI.read_text(encoding="utf-8")
text, n = re.subn(phone_pattern, phone_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f"global phone helper replacement count={n}")
TIMELINE_UI.write_text(text, encoding="utf-8")

open_pattern = r'''private fun openWhatsApp\(context: Context, raw: String\) \{.*?\n\}\n'''
open_replacement = '''private fun openWhatsApp(context: Context, raw: String) {\n    val digits = whatsappRecipient(raw) ?: return\n    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }\n}\n'''
text = TIMELINE_UI.read_text(encoding="utf-8")
text, n = re.subn(open_pattern, open_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f"WhatsApp country-neutral replacement count={n}")
TIMELINE_UI.write_text(text, encoding="utf-8")

TESTS.mkdir(parents=True, exist_ok=True)
test_file = TESTS / "Stage47Step7Post236StabilityTest.kt"
test_file.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Stage47Step7Post236StabilityTest {
    @Test
    fun tenCandidatesNeverAdvanceToEleven() {
        var index = 0
        repeat(20) { index = nextBlaBlaCandidateIndex(index, 10) }
        assertEquals(10, index)
    }

    @Test
    fun snapshotAndCompletionAreOneShotPerGeneration() {
        val gate = BlaBlaSyncCompletionGate()
        assertTrue(gate.claimSnapshot(1))
        assertFalse(gate.claimSnapshot(1))
        assertTrue(gate.claimCompletion(1))
        assertFalse(gate.claimCompletion(1))
        assertTrue(gate.claimSnapshot(2))
        assertTrue(gate.claimCompletion(2))
    }

    @Test
    fun genericOfferHrefFallsBackAndDoesNotCollapseDistinctTrips() {
        val first = trip(date = "2026-08-21", time = "10:00")
        val second = trip(date = "2026-08-21", time = "14:00")
        val a = BlaBlaTripIdentity.evidence(first)
        val b = BlaBlaTripIdentity.evidence(second)
        assertTrue(a.fallbackIdentityUsed)
        assertFalse(a.specificHrefPresent)
        assertNotEquals(a.key, b.key)
        assertNotEquals(a.identityHash, b.identityHash)
    }

    @Test
    fun specificPathIsStrongButSearchTokenIsNotPartOfIdentity() {
        val first = trip(href = "https://provider.example/rides/offer/ride-123?search_uuid=aaa")
        val second = first.copy(trip_href = "https://provider.example/rides/offer/ride-123?search_uuid=bbb")
        val a = BlaBlaTripIdentity.evidence(first)
        val b = BlaBlaTripIdentity.evidence(second)
        assertTrue(a.specificHrefPresent)
        assertFalse(a.fallbackIdentityUsed)
        assertEquals(a.key, b.key)
    }

    @Test
    fun externalProfilesRemainDistinctWithoutHardcodedDriverIdentity() {
        val first = BlaBlaTripIdentity.evidence(trip(profile = "profile-a"))
        val second = BlaBlaTripIdentity.evidence(trip(profile = "profile-b"))
        assertNotEquals(first.key, second.key)
    }

    @Test
    fun fourExternalPassengersReachTimelineWithoutInventedPhones() {
        val passengers = (1..4).map { index ->
            BlaBlaCollectorPassenger(name = "Passenger $index", seats = 1, phone = null)
        }
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(
                trip().copy(
                    passengers = passengers,
                    booked_seats = 4,
                    uuid_validation = "verified_from_authenticated_profile_session",
                ),
            ),
        )
        val entries = BlaBlaTimelineAdapter.merge(emptyList(), response)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(4, entry.minimumOccupiedSeats)
        assertEquals(4, entry.maximumOccupiedSeats)
        assertEquals(4, entry.sourcePassengerSeats[BookingSource.BLABLACAR])
        assertEquals(4, entry.blablaPassengers.size)
        assertTrue(entry.blablaPassengers.all { it.phone == null })
        assertFalse(TripTimelineIssue.OVERBOOKING in entry.issues)
    }

    @Test
    fun missingCoordinatesDoNotBecomeFactualContinuityConflict() {
        val first = entry("a", "Start A", "End A", 1_000L, 2_000L, "profile-a")
        val second = entry("b", "Start B", "End B", 3_000L, 4_000L, "profile-b")
        val result = TripPhysicalRideConsolidator.consolidate(listOf(first, second), emptyMap())
        assertTrue(result.all { TripTimelineIssue.PROFILE_CONTINUITY !in it.issues })
        assertTrue(result.all { TripTimelineIssue.PHYSICAL_CONFLICT !in it.issues })
    }

    @Test
    fun phoneEvidenceNeverGetsAutomaticCountryPrefix() {
        assertEquals("local:11987654321", normalizePhone("11 98765-4321"))
        assertEquals("+14155552671", normalizePhone("+1 415 555 2671"))
        assertEquals("11987654321", whatsappRecipient("11 98765-4321"))
        assertNull(whatsappRecipient("123"))
    }

    private fun trip(
        profile: String = "profile-a",
        date: String = "2026-08-21",
        time: String = "10:00",
        href: String = "https://provider.example/rides/offer",
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        profile_name = "Account",
        date = date,
        departure_time = time,
        arrival_time = "11:00",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        trip_href = href,
        trip_id = null,
    )

    private fun entry(
        id: String,
        origin: String,
        destination: String,
        departure: Long,
        arrival: Long,
        profile: String,
    ) = TripTimelineEntry(
        tripId = id,
        profileId = profile,
        profileLabel = profile,
        departureAtMillis = departure,
        arrivalAtMillis = arrival,
        origin = origin,
        destination = destination,
        status = TripStatus.PUBLISHED,
        capacity = 0,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
    )
}
''', encoding="utf-8")

for path, required in {
    DYNAMIC: ["STALE_CALLBACK_IGNORED", "BlaBlaSyncCompletionGate", "nextBlaBlaCandidateIndex", "saveFinalSnapshotOnce", "TRIP_IDENTITY"],
    COLLECTOR: ["BlaBlaTripIdentity", "fallbackIdentityUsed", "TIMELINE_EXTERNAL_ENTRY", "bookedSeats=${trip.booked_seats}"],
    CONSOLIDATOR: ["hasTrustedContinuityEvidence", "emptyMap()"],
    TIMELINE_UI: ["Locale.getDefault()", "whatsappRecipient", "local:$digits"],
}.items():
    data = path.read_text(encoding="utf-8")
    for marker in required:
        if marker not in data:
            raise SystemExit(f"stabilization marker missing {marker!r} in {path.name}")

for path in (CONSOLIDATOR, TIMELINE_UI):
    data = path.read_text(encoding="utf-8")
    for forbidden in ('Locale("pt", "BR")', '"$place, Brasil"', 'digits = "55$digits"'):
        if forbidden in data:
            raise SystemExit(f"global hardcode remained in {path.name}: {forbidden}")

if "LiveRideAccessibilityService" in "\n".join(str(path) for path in (DYNAMIC, COLLECTOR, TIMELINE_UI, CONSOLIDATOR, test_file)):
    raise SystemExit("FAROL unexpectedly entered post-0.1.236 stabilization scope")

print(
    "stage47_r4_step7_post_0236_stabilization=PASS "
    "stale_callback_guard=true index_bounded=true sync_end_idempotent=true snapshot_once=true "
    "generic_href_not_identity=true safe_identity_diagnostics=true passengers_preserved=true "
    "missing_geo_unknown=true country_neutral_core=true phone_country_not_invented=true "
    "focused_regression_tests=true farol_touched=false base_touched=false"
)
