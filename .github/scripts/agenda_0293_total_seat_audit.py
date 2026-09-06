from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"anchor count {count} in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, 1))


def insert_before(path: str, anchor: str, block: str) -> None:
    replace_once(path, anchor, block + anchor)


# ---------------------------------------------------------------------------
# Version: the audit fixes are a new physical-test candidate.
# ---------------------------------------------------------------------------
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5585\n        versionName = "0.1.292"',
    '        versionCode = 5586\n        versionName = "0.1.293"',
)

# ---------------------------------------------------------------------------
# Desired-state queue: make latest-wins behavior pure/testable and allow the
# writer Activity to retrieve the exact request selected by the collector UI.
# ---------------------------------------------------------------------------
manual = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaManualSeatAutomation.kt"
insert_before(
    manual,
    "class BlaBlaManualSeatSyncRequestStore(context: Context) {\n",
    '''internal object BlaBlaDesiredSeatQueuePolicy {\n    fun replacePublication(\n        current: List<BlaBlaManualSeatSyncRequest>,\n        request: BlaBlaManualSeatSyncRequest,\n    ): List<BlaBlaManualSeatSyncRequest> = current.filterNot { queued ->\n        queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n    } + request\n}\n\n''',
)
replace_once(
    manual,
    '''    fun peek(): BlaBlaManualSeatSyncRequest? = list().firstOrNull()\n\n    fun enqueue(request: BlaBlaManualSeatSyncRequest) {''',
    '''    fun get(id: String): BlaBlaManualSeatSyncRequest? = list().firstOrNull { it.id == id }\n\n    fun peek(): BlaBlaManualSeatSyncRequest? = list().firstOrNull()\n\n    fun enqueue(request: BlaBlaManualSeatSyncRequest) {''',
)
replace_once(
    manual,
    '''    /** Latest desired state replaces older work for the same strong publication identity. */\n    fun replacePublication(request: BlaBlaManualSeatSyncRequest): List<String> {\n        val current = list()\n        val stale = current.filter { queued ->\n            queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n        }\n        save(current.filterNot { queued ->\n            queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n        } + request)\n        return stale.map(BlaBlaManualSeatSyncRequest::id)\n    }''',
    '''    /** Latest desired state replaces older work for the same strong publication identity. */\n    fun replacePublication(request: BlaBlaManualSeatSyncRequest): List<String> {\n        val current = list()\n        val stale = current.filter { queued ->\n            queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n        }\n        save(BlaBlaDesiredSeatQueuePolicy.replacePublication(current, request))\n        return stale.map(BlaBlaManualSeatSyncRequest::id)\n    }''',
)

# ---------------------------------------------------------------------------
# Reliable writer: exact queue request identity, fail-closed WebView profile,
# and explicit already-synchronized result. The actual +/- one-by-one stepper
# and save->reload->verify flow remain unchanged.
# ---------------------------------------------------------------------------
reliable = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt"
replace_once(
    reliable,
    '''    fun markSynced(profileUuid: String, tripId: String, value: Int) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            desiredPublishedSeats = current?.desiredPublishedSeats ?: value,\n            lastObservedPublishedSeats = value,\n            state = BlaBlaPublicationSeatSyncVisualState.SYNCED,\n            message = "Vagas sincronizadas ✅",\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }''',
    '''    fun markSynced(\n        profileUuid: String,\n        tripId: String,\n        value: Int,\n        message: String = "Vagas sincronizadas ✅",\n    ) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            desiredPublishedSeats = current?.desiredPublishedSeats ?: value,\n            lastObservedPublishedSeats = value,\n            state = BlaBlaPublicationSeatSyncVisualState.SYNCED,\n            message = message,\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }''',
)
insert_before(
    reliable,
    "/** Pure retry/idempotency policy used by the Activity and unit tests. */\n",
    '''internal object BlaBlaReliableSeatRequestSelector {\n    /**\n     * If the launcher supplied an id, only that exact request is valid.\n     * The legacy first-item fallback is retained only for callers without an id.\n     */\n    fun select(\n        queue: List<BlaBlaManualSeatSyncRequest>,\n        requestId: String?,\n    ): BlaBlaManualSeatSyncRequest? {\n        val exactId = requestId?.trim()?.takeIf(String::isNotEmpty)\n        return if (exactId == null) queue.firstOrNull() else queue.firstOrNull { it.id == exactId }\n    }\n}\n\n''',
)
replace_once(
    reliable,
    '''object BlaBlaReliableSeatSyncIntents {\n    fun seatSync(context: Context, account: BlaBlaDynamicAccount): Intent =\n        Intent(context, BlaBlaReliableSeatSyncActivity::class.java)\n            .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)\n}''',
    '''object BlaBlaReliableSeatSyncIntents {\n    const val EXTRA_REQUEST_ID = "blablacar_seat_sync_request_id"\n\n    fun seatSync(\n        context: Context,\n        account: BlaBlaDynamicAccount,\n        requestId: String? = null,\n    ): Intent = Intent(context, BlaBlaReliableSeatSyncActivity::class.java)\n        .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)\n        .apply { requestId?.trim()?.takeIf(String::isNotEmpty)?.let { putExtra(EXTRA_REQUEST_ID, it) } }\n}''',
)
replace_once(
    reliable,
    '''        request = requestStore.peek() ?: run {\n            finishPending("Nenhuma sincronização manual pendente.", rotate = false)\n            return\n        }''',
    '''        val requestedId = intent?.getStringExtra(BlaBlaReliableSeatSyncIntents.EXTRA_REQUEST_ID)\n            ?.trim()\n            ?.takeIf(String::isNotEmpty)\n        request = BlaBlaReliableSeatRequestSelector.select(requestStore.list(), requestedId) ?: run {\n            finishPending(\n                if (requestedId == null) "Nenhuma sincronização manual pendente." else "A sincronização selecionada não está mais pendente.",\n                rotate = false,\n            )\n            return\n        }''',
)
replace_once(
    reliable,
    '''        if (desiredPublishedSeats != null) {\n            publicationSeatStateStore.markSyncing(request.profileUuid, request.tripId, desiredPublishedSeats)\n        }\n\n        val ledgerEntry = if (desiredPublishedSeats == null) ledger.entry(request.localBookingId) else null''',
    '''        if (desiredPublishedSeats != null) {\n            publicationSeatStateStore.markSyncing(request.profileUuid, request.tripId, desiredPublishedSeats)\n        }\n        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {\n            finishPending(\n                "O Android System WebView não oferece o perfil autenticado isolado desta conta; nenhuma vaga foi alterada.",\n                rotate = true,\n            )\n            return\n        }\n\n        val ledgerEntry = if (desiredPublishedSeats == null) ledger.entry(request.localBookingId) else null''',
)
replace_once(
    reliable,
    '''    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {\n        if (request.desiredPublishedSeats != null) {\n            publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, afterSeats)\n        } else if (request.seatDelta < 0) {\n            ledger.markVerifiedDecrease(request)\n        } else {\n            ledger.clearAfterVerifiedReverse(request.localBookingId)\n            bindingStore.remove(request.localBookingId)\n        }\n        requestStore.remove(request.id)\n        attemptStore.clear(request.id)\n        UnifiedDebugEventStore.record(\n            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFIED",\n            packageName,\n            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} alreadyApplied=$alreadyApplied ledger=${request.desiredPublishedSeats == null}",\n        )\n        setResult(\n            RESULT_OK,\n            Intent()\n                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)\n                .putExtra("seat_sync_message", "Sincronizado externamente ✅ • $afterSeats vaga(s) publicadas"),\n        )\n        finish()\n    }''',
    '''    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {\n        val desiredRequest = request.desiredPublishedSeats != null\n        val verifiedMessage = if (desiredRequest && alreadyApplied) {\n            "Nenhuma alteração necessária ✅ • $afterSeats vaga(s) já publicadas"\n        } else {\n            "Vagas sincronizadas ✅ • $afterSeats vaga(s) publicadas"\n        }\n        if (desiredRequest) {\n            publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, afterSeats, verifiedMessage)\n        } else if (request.seatDelta < 0) {\n            ledger.markVerifiedDecrease(request)\n        } else {\n            ledger.clearAfterVerifiedReverse(request.localBookingId)\n            bindingStore.remove(request.localBookingId)\n        }\n        requestStore.remove(request.id)\n        attemptStore.clear(request.id)\n        UnifiedDebugEventStore.record(\n            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFIED",\n            packageName,\n            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} alreadyApplied=$alreadyApplied ledger=${request.desiredPublishedSeats == null}",\n        )\n        setResult(\n            RESULT_OK,\n            Intent()\n                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)\n                .putExtra("seat_sync_message", verifiedMessage),\n        )\n        finish()\n    }''',
)

# ---------------------------------------------------------------------------
# Collector UI: launch the exact request that was selected, not queue.peek().
# Desired-state requests must never display the legacy "0 vaga(s)" message.
# ---------------------------------------------------------------------------
collector_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt"
replace_once(
    collector_ui,
    '''        message = if (pending.seatDelta < 0) {\n            "Ajustando ${-pending.seatDelta} vaga(s) na publicação correta…"\n        } else {\n            "Devolvendo ${pending.seatDelta} vaga(s) à publicação correta…"\n        }''',
    '''        message = pending.desiredPublishedSeats?.let { desired ->\n            "Sincronizando somente as vagas desta publicação • alvo atual: $desired…"\n        } ?: if (pending.seatDelta < 0) {\n            "Ajustando ${-pending.seatDelta} vaga(s) na publicação correta…"\n        } else {\n            "Devolvendo ${pending.seatDelta} vaga(s) à publicação correta…"\n        }''',
)
replace_once(
    collector_ui,
    '''            "origin=$origin request=${pending.id} delta=${pending.seatDelta} profileUuidPresent=true tripIdPresent=true",''',
    '''            "origin=$origin request=${pending.id} delta=${pending.seatDelta} desired=${pending.desiredPublishedSeats ?: -1} profileUuidPresent=true tripIdPresent=true",''',
)
replace_once(
    collector_ui,
    '''        seatSyncLauncher.launch(BlaBlaReliableSeatSyncIntents.seatSync(context, target))''',
    '''        seatSyncLauncher.launch(BlaBlaReliableSeatSyncIntents.seatSync(context, target, pending.id))''',
)

# ---------------------------------------------------------------------------
# Global +Passageiro was the one automatic path still bypassing the desired-state
# bridge. Route it through the exact same callback contract as card-local edits.
# ---------------------------------------------------------------------------
global_flow = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt"
replace_once(
    global_flow,
    '''    onTargetSync: (String?) -> Unit,\n) {''',
    '''    onTargetSync: (TripTimelineEntry, Trip) -> Unit,\n) {''',
)
replace_once(
    global_flow,
    '''                            onBlaBlaSyncRequested = if (timelineStrongExternalTripKey(entry) != null) {\n                                { onTargetSync(canonicalTimelineProfileUuid(entry)) }\n                            } else null,''',
    '''                            onBlaBlaSyncRequested = if (timelineStrongExternalTripKey(entry) != null) {\n                                { onTargetSync(entry, trip) }\n                            } else null,''',
)

timeline = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
replace_once(
    timeline,
    '''        onTargetSync = { profileUuid ->\n            autoSyncProfileUuid = profileUuid\n            autoSyncTripId = null\n            onRequestBlaBlaSync()\n        },''',
    '''        onTargetSync = { entry, selectedTrip ->\n            val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(\n                context = context,\n                entry = entry,\n                trip = selectedTrip,\n                store = store,\n                reason = "automatic_global_passenger_change",\n            )\n            onChanged(result.message)\n            if (result.shouldSync) {\n                autoSyncProfileUuid = canonicalTimelineProfileUuid(entry)\n                autoSyncTripId = null\n                onRequestBlaBlaSync()\n            }\n        },''',
)
replace_once(
    timeline,
    '''            val occupied = entry.maximumOccupiedSeats\n            when (timelineOccupancyReadState(entry)) {''',
    '''            val occupied = seatPlan?.loads?.maxOfOrNull(SegmentLoad::occupiedSeats) ?: entry.maximumOccupiedSeats\n            when (timelineOccupancyReadState(entry)) {''',
)

# ---------------------------------------------------------------------------
# Broad pure regression/audit tests. Android/WebView execution remains a physical
# dependency; these tests prove the domain, queue and strong-identity contracts.
# ---------------------------------------------------------------------------
test_path = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/TripSeatSyncAudit0293Test.kt")
test_path.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripSeatSyncAudit0293Test {
    private val profileA = "7371f028-9c55-4903-8444-308015823efd"
    private val profileB = "175a7068-50d8-40c3-a27a-214b9c6e0461"
    private val stops = listOf(
        TripStop(id = "sa", order = 0, name = "Santo André"),
        TripStop(id = "sp", order = 1, name = "São Paulo"),
        TripStop(id = "ex", order = 2, name = "Extrema"),
        TripStop(id = "pa", order = 3, name = "Pouso Alegre"),
        TripStop(id = "tc", order = 4, name = "Três Corações"),
        TripStop(id = "st", order = 5, name = "São Thomé"),
    )
    private val trip = Trip(
        id = "local-trip",
        title = "Santo André → São Thomé",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = stops,
    )

    @Test
    fun editingTwoSeatsToOneRestoresOnlyAffectedSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "pa", "st", 2)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "pa", "st", 1,
        )
        assertEquals(listOf(4, 4, 4, 3, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun changingBoardingMovesOccupancyToLaterSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "sa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "pa", "st", 1,
        )
        assertEquals(listOf(4, 4, 4, 3, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun changingDropoffReleasesAllLaterSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "sa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip, listOf(original), original, "sa", "pa", 1,
        )
        assertEquals(listOf(3, 3, 3, 4, 4), SeatAvailabilityEngine.segmentLoads(trip, listOf(updated)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun partialPassengerConsumesOnlyFinalSegment() {
        val manual = booking("manual", BookingSource.PRIVATE, "tc", "st", 1)
        assertEquals(listOf(4, 4, 4, 4, 3), SeatAvailabilityEngine.segmentLoads(trip, listOf(manual)).map(SegmentLoad::availableSeats))
    }

    @Test
    fun exactExternalMirrorIsNotDoubleCountedWithManualPassenger() {
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 1).copy(passengerContact = "11999999999")
        val entry = externalEntry(
            profileUuid = profileA,
            tripId = "publication-a",
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Mesmo passageiro",
                    seats = 1,
                    boarding = "São Paulo",
                    dropoff = "Três Corações",
                    phone = "(11) 99999-9999",
                ),
            ),
            bookedSeats = 1,
        )
        val claims = planTimelineExternalCapacityClaims(entry, trip, listOf(manual))
        assertTrue(claims.isEmpty())
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, listOf(manual)).maxOf(SegmentLoad::occupiedSeats))
    }

    @Test
    fun distinctExternalAndManualPassengersAreBothCounted() {
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 1).copy(passengerContact = "11888888888")
        val entry = externalEntry(
            profileUuid = profileA,
            tripId = "publication-a",
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Externo",
                    seats = 1,
                    boarding = "São Paulo",
                    dropoff = "Três Corações",
                    phone = "11999999999",
                ),
            ),
            bookedSeats = 1,
        )
        val claims = planTimelineExternalCapacityClaims(entry, trip, listOf(manual))
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(manual) + claims)
        assertEquals(2, loads[1].occupiedSeats)
        assertEquals(2, loads[2].occupiedSeats)
        assertEquals(2, loads[3].occupiedSeats)
    }

    @Test
    fun repeatedDesiredStateNeverAccumulatesBlindDecrements() {
        val first = BlaBlaReliableSeatSyncPolicy.decideDesired(3, true, true, 2)
        val retryBeforeWrite = BlaBlaReliableSeatSyncPolicy.decideDesired(3, true, true, 2)
        val second = BlaBlaReliableSeatSyncPolicy.decideDesired(2, true, true, 2)
        val third = BlaBlaReliableSeatSyncPolicy.decideDesired(2, true, true, 2)
        assertEquals(2, first.targetSeats)
        assertEquals(2, retryBeforeWrite.targetSeats)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, second.action)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, third.action)
    }

    @Test
    fun rapidManualAndAutomaticTriggersCollapseToLatestDesiredState() {
        val manual = desiredRequest("manual", profileA, "publication-a", 2, "manual_card_shortcut")
        val automatic = desiredRequest("automatic", profileA, "publication-a", 1, "automatic_after_passenger_change")
        val afterManual = BlaBlaDesiredSeatQueuePolicy.replacePublication(emptyList(), manual)
        val afterAutomatic = BlaBlaDesiredSeatQueuePolicy.replacePublication(afterManual, automatic)
        assertEquals(1, afterAutomatic.size)
        assertEquals("automatic", afterAutomatic.single().id)
        assertEquals(1, afterAutomatic.single().desiredPublishedSeats)
    }

    @Test
    fun requestsForDifferentPublicationsAndProfilesStayIndependent() {
        val a = desiredRequest("a", profileA, "publication-a", 2, "manual")
        val b = desiredRequest("b", profileA, "publication-b", 1, "manual")
        val c = desiredRequest("c", profileB, "publication-a", 3, "manual")
        val queue = listOf(a, b).let { BlaBlaDesiredSeatQueuePolicy.replacePublication(it, c) }
        assertEquals(3, queue.size)
        assertTrue(queue.any { it.profileUuid == profileA && it.tripId == "publication-a" })
        assertTrue(queue.any { it.profileUuid == profileA && it.tripId == "publication-b" })
        assertTrue(queue.any { it.profileUuid == profileB && it.tripId == "publication-a" })
    }

    @Test
    fun selectedFreshQueueRequestIsTheExactRequestOpenedByWriter() {
        val retained = desiredRequest("retained", profileA, "publication-a", 2, "retry")
        val fresh = desiredRequest("fresh", profileB, "publication-b", 1, "manual")
        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained, fresh)) { it == "retained" }
        assertEquals("fresh", selected?.id)
        assertEquals(fresh, BlaBlaReliableSeatRequestSelector.select(listOf(retained, fresh), selected?.id))
        assertNull(BlaBlaReliableSeatRequestSelector.select(listOf(retained, fresh), "missing"))
    }

    @Test
    fun sameVisualTripWithDifferentPublicationIdsRemainsDistinct() {
        val a = externalEntry(profileA, "publication-a")
        val b = externalEntry(profileA, "publication-b")
        val targetA = BlaBlaReliableSeatSyncBridge.targetForTimeline(a)
        val targetB = BlaBlaReliableSeatSyncBridge.targetForTimeline(b)
        assertEquals("publication-a", targetA?.tripId)
        assertEquals("publication-b", targetB?.tripId)
        assertFalse(targetA == targetB)
    }

    @Test
    fun samePublicationIdUnderDifferentProfilesRemainsDistinct() {
        val targetA = BlaBlaReliableSeatSyncBridge.targetForTimeline(externalEntry(profileA, "same-id"))
        val targetB = BlaBlaReliableSeatSyncBridge.targetForTimeline(externalEntry(profileB, "same-id"))
        assertEquals(profileA, targetA?.profileUuid)
        assertEquals(profileB, targetB?.profileUuid)
        assertFalse(targetA == targetB)
    }

    @Test
    fun exactOptionsPageAssociationRejectsAnotherPublication() {
        val a = "https://www.blablacar.com.br/rides/offer/edit/publication-a/options"
        val b = "https://www.blablacar.com.br/rides/offer/edit/publication-b/options"
        assertTrue(BlaBlaHarvestAssociation.optionsPageMatches("publication-a", a))
        assertFalse(BlaBlaHarvestAssociation.optionsPageMatches("publication-a", b))
    }

    private fun booking(id: String, source: BookingSource, from: String, to: String, seats: Int) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = source,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )

    private fun externalEntry(
        profileUuid: String,
        tripId: String,
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
        bookedSeats: Int = 0,
    ) = TripTimelineEntry(
        tripId = "timeline:$tripId",
        profileId = profileUuid,
        profileLabel = "Perfil",
        departureAtMillis = trip.departureAtMillis,
        arrivalAtMillis = null,
        origin = "Santo André",
        destination = "São Thomé",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = bookedSeats,
        maximumOccupiedSeats = bookedSeats,
        sourcePassengerSeats = if (bookedSeats > 0) mapOf(BookingSource.BLABLACAR to bookedSeats) else emptyMap(),
        blablaTripId = tripId,
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/$tripId",
        blablaProfileUuid = profileUuid,
        blablaPassengers = passengers,
        blablaPassengerRosterComplete = true,
    )

    private fun desiredRequest(
        id: String,
        profileUuid: String,
        tripId: String,
        desired: Int,
        reason: String,
    ) = BlaBlaManualSeatSyncRequest(
        id = id,
        profileUuid = profileUuid,
        tripId = tripId,
        seatDelta = 0,
        desiredPublishedSeats = desired,
        desiredStateReason = reason,
        localTripId = trip.id,
        localBookingId = "desired:$profileUuid:$tripId",
        source = "DESIRED_STATE",
    )
}
''')
