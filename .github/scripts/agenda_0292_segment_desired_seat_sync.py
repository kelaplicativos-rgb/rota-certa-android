from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"anchor count {count} in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def insert_before(path: str, anchor: str, block: str) -> None:
    replace_once(path, anchor, block + anchor)


def insert_after(path: str, anchor: str, block: str) -> None:
    replace_once(path, anchor, anchor + block)


# ---------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5584\n        versionName = "0.1.291"',
    '        versionCode = 5585\n        versionName = "0.1.292"',
)

# ---------------------------------------------------------------------------
# Persist absolute desired state on the existing seat-sync request queue.
# Legacy delta fields remain readable only to avoid corrupting old SharedPrefs.
# ---------------------------------------------------------------------------
manual = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaManualSeatAutomation.kt"
replace_once(
    manual,
    '''    /** Negative removes externally offered places; positive gives them back. */\n    val seatDelta: Int,\n    val localTripId: String,''',
    '''    /** Legacy delta retained only for requests persisted before 0.1.292. */\n    val seatDelta: Int,\n    /** Absolute number of places that should be published after current segment reconciliation. */\n    val desiredPublishedSeats: Int? = null,\n    val desiredStateReason: String = "",\n    val localTripId: String,''',
)
insert_after(
    manual,
    '''    fun enqueue(request: BlaBlaManualSeatSyncRequest) {\n        save(list() + request)\n    }\n''',
    '''\n    /** Latest desired state replaces older work for the same strong publication identity. */\n    fun replacePublication(request: BlaBlaManualSeatSyncRequest): List<String> {\n        val current = list()\n        val stale = current.filter { queued ->\n            queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n        }\n        save(current.filterNot { queued ->\n            queued.profileUuid.equals(request.profileUuid, ignoreCase = true) && queued.tripId == request.tripId\n        } + request)\n        return stale.map(BlaBlaManualSeatSyncRequest::id)\n    }\n\n    fun discardLegacyDeltaRequests(): List<String> {\n        val current = list()\n        val legacy = current.filter { it.desiredPublishedSeats == null }\n        if (legacy.isNotEmpty()) save(current.filter { it.desiredPublishedSeats != null })\n        return legacy.map(BlaBlaManualSeatSyncRequest::id)\n    }\n''',
)

# ---------------------------------------------------------------------------
# Keep SeatAvailabilityEngine as the only physical-capacity calculation engine.
# Add only an edit validator that removes the booking being edited before asking
# the existing segment engine whether the new seats/route fit.
# ---------------------------------------------------------------------------
quick = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassenger.kt"
insert_before(
    quick,
    '''    fun activeReservedSeatLinks(bookings: List<Booking>, nowMillis: Long = System.currentTimeMillis()): List<Booking> =\n''',
    '''    fun updateManualBooking(\n        trip: Trip,\n        existingBookings: List<Booking>,\n        booking: Booking,\n        boardingStopId: String,\n        dropoffStopId: String,\n        seats: Int,\n        nowMillis: Long = System.currentTimeMillis(),\n    ): Booking {\n        require(booking.tripId == trip.id) { "A reserva pertence a outra viagem." }\n        require(booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER)) { "Somente passageiro manual pode ser editado aqui." }\n        require(booking.capacityClaimType == CapacityClaimType.PASSENGER) { "A reserva não representa um passageiro." }\n        require(seats in 1..trip.capacity) { "Quantidade de lugares inválida." }\n        val availability = SeatAvailabilityEngine.availability(\n            trip = trip,\n            bookings = existingBookings.filterNot { it.id == booking.id },\n            boardingStopId = boardingStopId,\n            dropoffStopId = dropoffStopId,\n            requestedSeats = seats,\n            nowMillis = nowMillis,\n        )\n        require(availability.canBook) { "Somente ${availability.availableSeats} vaga(s) disponível(is) nesse trecho." }\n        return booking.copy(\n            boardingStopId = boardingStopId,\n            dropoffStopId = dropoffStopId,\n            seats = seats,\n            status = BookingStatus.CONFIRMED,\n            updatedAtMillis = nowMillis,\n        )\n    }\n\n''',
)

# ---------------------------------------------------------------------------
# Segment planning: reuse existing external capacity claims + SeatAvailabilityEngine.
# Strong duplicate rule: same phone + same route + same seats is not counted twice.
# ---------------------------------------------------------------------------
global_flow = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt"
replace_once(
    global_flow,
    '''    val mappedExternalBySegment = IntArray(segmentCount)\n    var mappedPassengerSeats = 0\n    val passengerSeatEvidence = entry.blablaPassengers.sumOf { it.seats.coerceAtLeast(1) }\n    entry.blablaPassengers.forEach { passenger ->\n        val seats = passenger.seats.coerceAtLeast(1)\n        val boardingIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.boarding))\n        val dropoffIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.dropoff))\n        if (boardingIndex >= 0 && dropoffIndex > boardingIndex) {\n            mappedPassengerSeats += seats\n            for (segment in boardingIndex until dropoffIndex) mappedExternalBySegment[segment] += seats\n        }\n    }\n''',
    '''    val mappedExternalBySegment = IntArray(segmentCount)\n    var mappedPassengerSeats = 0\n    val passengerSeatEvidence = entry.blablaPassengers.sumOf { it.seats.coerceAtLeast(1) }\n    val activeManualPassengers = existingBookings.filter { booking ->\n        booking.tripId == trip.id &&\n            booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&\n            booking.capacityClaimType == CapacityClaimType.PASSENGER &&\n            booking.seats > 0 &&\n            isActive(booking)\n    }\n    entry.blablaPassengers.forEach { passenger ->\n        val seats = passenger.seats.coerceAtLeast(1)\n        val boardingIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.boarding))\n        val dropoffIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.dropoff))\n        if (boardingIndex >= 0 && dropoffIndex > boardingIndex) {\n            mappedPassengerSeats += seats\n            val externalPhone = passenger.phone?.filter(Char::isDigit)?.takeIf { it.length >= 8 }\n            val exactLocalMirror = externalPhone != null && activeManualPassengers.any { booking ->\n                val localPhone = booking.passengerContact.filter(Char::isDigit)\n                val localFrom = stops.indexOfFirst { it.id == booking.boardingStopId }\n                val localTo = stops.indexOfFirst { it.id == booking.dropoffStopId }\n                localPhone == externalPhone && booking.seats == seats && localFrom == boardingIndex && localTo == dropoffIndex\n            }\n            if (!exactLocalMirror) {\n                for (segment in boardingIndex until dropoffIndex) mappedExternalBySegment[segment] += seats\n            }\n        }\n    }\n''',
)
insert_before(
    global_flow,
    '''internal fun prepareTimelineTripForPassenger(\n''',
    '''internal fun isTimelineExternalCapacityClaim(booking: Booking): Boolean =\n    booking.capacityClaimType == CapacityClaimType.RESERVED_SEAT &&\n        booking.sourceReference.startsWith(EXTERNAL_CAPACITY_PREFIX)\n\ninternal data class TimelineSeatSyncPlan(\n    val loads: List<SegmentLoad>,\n    val desiredPublishedSeats: Int,\n    val localTripId: String,\n)\n\n/**\n * Computes the single publication target from the bottleneck of the already-existing\n * per-segment physical engine. External roster must be complete; otherwise we fail\n * closed instead of inventing segment availability.\n */\ninternal fun timelineDesiredSeatSyncPlan(\n    entry: TripTimelineEntry,\n    trip: Trip?,\n    store: TripStore,\n): TimelineSeatSyncPlan? {\n    if (timelineStrongExternalTripKey(entry) == null || entry.capacity !in 1..999) return null\n    if (entry.blablaPassengerRosterComplete != true) return null\n\n    val base = when {\n        trip != null -> trip.copy(capacity = entry.capacity)\n        else -> buildTimelineExternalBackingTrip(entry, entry.capacity)\n    }\n    val working = augmentExternalBackingStops(base, entry).copy(capacity = entry.capacity)\n    val existing = trip?.let { store.bookingsFor(it.id) }.orEmpty()\n    val localClaims = existing.filterNot(::isTimelineExternalCapacityClaim)\n    val externalClaims = planTimelineExternalCapacityClaims(entry, working, localClaims)\n    val loads = SeatAvailabilityEngine.segmentLoads(working, localClaims + externalClaims)\n    if (loads.isEmpty()) return null\n    return TimelineSeatSyncPlan(\n        loads = loads,\n        desiredPublishedSeats = loads.minOf(SegmentLoad::availableSeats).coerceIn(0, entry.capacity),\n        localTripId = working.id,\n    )\n}\n\n''',
)
replace_once(
    global_flow,
    '''        if (trip.status == TripStatus.DRAFT) trip = trip.copy(status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED)\n        if (trip.capacity !in 1..999 && entry.capacity in 1..999) trip = trip.copy(capacity = entry.capacity)\n        trip = store.saveTrip(trip)\n\n        val previousClaims = store.bookingsFor(trip.id).filter {\n            it.capacityClaimType == CapacityClaimType.RESERVED_SEAT &&\n                it.sourceReference.startsWith(EXTERNAL_CAPACITY_PREFIX)\n        }\n''',
    '''        if (trip.status == TripStatus.DRAFT) trip = trip.copy(status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED)\n        if (entry.capacity in 1..999 && trip.capacity != entry.capacity) trip = trip.copy(capacity = entry.capacity)\n        trip = store.saveTrip(trip)\n\n        val previousClaims = store.bookingsFor(trip.id).filter(::isTimelineExternalCapacityClaim)\n''',
)
replace_once(
    global_flow,
    '''                    "Passageiros adicionados aqui ocupam a Agenda imediatamente. Quando esta publicação BlaBlaCar tem identidade forte, salvar reduz as vagas externas e remover devolve somente uma redução já comprovada.",''',
    '''                    "Passageiros adicionados aqui ocupam a Agenda imediatamente. A sincronização externa recalcula o estado desejado por trecho e ajusta somente as vagas da publicação exata.",''',
)

# ---------------------------------------------------------------------------
# Existing reliable sync: add visual state persistence and absolute-state policy.
# The DOM stepper remains exactly the same (+/- one by one to target).
# ---------------------------------------------------------------------------
reliable = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt"
insert_before(
    reliable,
    '''internal enum class BlaBlaReliableSeatSyncAction {\n''',
    '''@Serializable\ninternal enum class BlaBlaPublicationSeatSyncVisualState {\n    AVAILABLE,\n    SYNCING,\n    SYNCED,\n    PENDING,\n    ERROR,\n}\n\n@Serializable\ninternal data class BlaBlaPublicationSeatSyncState(\n    val profileUuid: String,\n    val tripId: String,\n    val desiredPublishedSeats: Int? = null,\n    val lastObservedPublishedSeats: Int? = null,\n    val state: BlaBlaPublicationSeatSyncVisualState = BlaBlaPublicationSeatSyncVisualState.AVAILABLE,\n    val message: String = "Sincronizar somente as vagas deste card",\n    val updatedAtMillis: Long = System.currentTimeMillis(),\n)\n\ninternal class BlaBlaPublicationSeatSyncStateStore(context: Context) {\n    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }\n\n    fun get(profileUuid: String, tripId: String): BlaBlaPublicationSeatSyncState? = list().firstOrNull {\n        it.profileUuid.equals(profileUuid, ignoreCase = true) && it.tripId == tripId\n    }\n\n    fun markDesired(profileUuid: String, tripId: String, desired: Int, message: String) = update(\n        BlaBlaPublicationSeatSyncState(\n            profileUuid = profileUuid,\n            tripId = tripId,\n            desiredPublishedSeats = desired,\n            state = BlaBlaPublicationSeatSyncVisualState.PENDING,\n            message = message,\n        ),\n    )\n\n    fun markSyncing(profileUuid: String, tripId: String, desired: Int) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            desiredPublishedSeats = desired,\n            state = BlaBlaPublicationSeatSyncVisualState.SYNCING,\n            message = "Sincronizando somente as vagas…",\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }\n\n    fun markObserved(profileUuid: String, tripId: String, observed: Int) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            lastObservedPublishedSeats = observed,\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }\n\n    fun markSynced(profileUuid: String, tripId: String, value: Int) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            desiredPublishedSeats = current?.desiredPublishedSeats ?: value,\n            lastObservedPublishedSeats = value,\n            state = BlaBlaPublicationSeatSyncVisualState.SYNCED,\n            message = "Vagas sincronizadas ✅",\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }\n\n    fun markPending(profileUuid: String, tripId: String, message: String, desired: Int? = null) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            desiredPublishedSeats = desired ?: current?.desiredPublishedSeats,\n            state = BlaBlaPublicationSeatSyncVisualState.PENDING,\n            message = message,\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }\n\n    fun markError(profileUuid: String, tripId: String, message: String) = mutate(profileUuid, tripId) { current ->\n        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(\n            state = BlaBlaPublicationSeatSyncVisualState.ERROR,\n            message = message,\n            updatedAtMillis = System.currentTimeMillis(),\n        )\n    }\n\n    private fun mutate(\n        profileUuid: String,\n        tripId: String,\n        transform: (BlaBlaPublicationSeatSyncState?) -> BlaBlaPublicationSeatSyncState,\n    ) {\n        update(transform(get(profileUuid, tripId)))\n    }\n\n    private fun update(value: BlaBlaPublicationSeatSyncState) {\n        val next = list().filterNot {\n            it.profileUuid.equals(value.profileUuid, ignoreCase = true) && it.tripId == value.tripId\n        } + value\n        prefs.edit().putString(KEY, json.encodeToString(next)).apply()\n    }\n\n    private fun list(): List<BlaBlaPublicationSeatSyncState> = runCatching {\n        json.decodeFromString<List<BlaBlaPublicationSeatSyncState>>(prefs.getString(KEY, "[]") ?: "[]")\n    }.getOrDefault(emptyList())\n\n    companion object {\n        private const val PREFS = "rota_certa_blablacar_publication_seat_sync_state_v1"\n        private const val KEY = "states"\n    }\n}\n\n''',
)
insert_before(
    reliable,
    '''}\n\ndata class BlaBlaManualSeatCancellationResult(\n''',
    '''\n    fun decideDesired(\n        currentSeats: Int,\n        canAdd: Boolean,\n        canRemove: Boolean,\n        desiredPublishedSeats: Int,\n    ): BlaBlaReliableSeatSyncDecision {\n        if (currentSeats < 0 || desiredPublishedSeats < 0) {\n            return BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.INVALID)\n        }\n        return when {\n            currentSeats == desiredPublishedSeats -> BlaBlaReliableSeatSyncDecision(\n                BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED,\n                desiredPublishedSeats,\n            )\n            currentSeats > desiredPublishedSeats && canRemove -> BlaBlaReliableSeatSyncDecision(\n                BlaBlaReliableSeatSyncAction.APPLY_TARGET,\n                desiredPublishedSeats,\n            )\n            currentSeats < desiredPublishedSeats && canAdd -> BlaBlaReliableSeatSyncDecision(\n                BlaBlaReliableSeatSyncAction.APPLY_TARGET,\n                desiredPublishedSeats,\n            )\n            else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE, desiredPublishedSeats)\n        }\n    }\n''',
)
# The preceding insertion lands before the object's closing brace anchor; repair exact placement if needed by checking marker order later.
insert_after(
    reliable,
    '''data class BlaBlaManualSeatCancellationResult(\n    val shouldSync: Boolean,\n    val message: String,\n)\n''',
    '''\ninternal data class BlaBlaDesiredSeatSyncRequestResult(\n    val shouldSync: Boolean,\n    val message: String,\n    val desiredPublishedSeats: Int? = null,\n)\n''',
)
insert_after(
    reliable,
    '''    fun targetForTimeline(entry: TripTimelineEntry): BlaBlaManualSeatExternalTarget? {\n        val profile = entry.blablaProfileUuid\n            ?.trim()\n            ?.lowercase(Locale.ROOT)\n            ?.takeIf(::isCanonicalUuid)\n            ?: return null\n        val tripId = entry.blablaTripId?.trim()?.takeIf(String::isNotEmpty)\n            ?: BlaBlaTripIdentity.externalTripIdFromHref(entry.blablaTripHref)\n            ?: return null\n        return BlaBlaManualSeatExternalTarget(profile, tripId)\n    }\n''',
    '''\n    fun enqueueDesiredStateForTimeline(\n        context: Context,\n        entry: TripTimelineEntry,\n        trip: Trip?,\n        store: TripStore,\n        reason: String,\n    ): BlaBlaDesiredSeatSyncRequestResult {\n        val target = targetForTimeline(entry) ?: return BlaBlaDesiredSeatSyncRequestResult(\n            shouldSync = false,\n            message = "Vagas aguardando sincronização ⚠️ • identidade forte da publicação indisponível.",\n        )\n        val statusStore = BlaBlaPublicationSeatSyncStateStore(context)\n        val plan = timelineDesiredSeatSyncPlan(entry, trip, store)\n        if (plan == null) {\n            val message = if (entry.blablaPassengerRosterComplete != true) {\n                "Vagas aguardando sincronização ⚠️ • passageiros externos ainda aguardam leitura completa por trecho."\n            } else {\n                "Vagas aguardando sincronização ⚠️ • não foi possível calcular o estado físico por trecho."\n            }\n            statusStore.markPending(target.profileUuid, target.tripId, message)\n            return BlaBlaDesiredSeatSyncRequestResult(false, message)\n        }\n\n        val requestStore = BlaBlaManualSeatSyncRequestStore(context)\n        val attemptStore = BlaBlaManualSeatSyncAttemptStore(context)\n        val request = BlaBlaManualSeatSyncRequest(\n            profileUuid = target.profileUuid,\n            tripId = target.tripId,\n            seatDelta = 0,\n            desiredPublishedSeats = plan.desiredPublishedSeats,\n            desiredStateReason = reason,\n            localTripId = plan.localTripId,\n            localBookingId = "desired:${target.profileUuid}:${target.tripId}",\n            source = "DESIRED_STATE",\n        )\n        requestStore.replacePublication(request).forEach(attemptStore::clear)\n        val message = "Estado desejado calculado: ${plan.desiredPublishedSeats} vaga(s) • conferindo somente as vagas da publicação correta…"\n        statusStore.markDesired(target.profileUuid, target.tripId, plan.desiredPublishedSeats, message)\n        UnifiedDebugEventStore.record(\n            "EXTERNAL_SEAT_DESIRED_STATE_QUEUED",\n            context.packageName,\n            "reason=$reason desired=${plan.desiredPublishedSeats} segments=${plan.loads.size} profileUuidPresent=true tripIdPresent=true request=${request.id}",\n        )\n        return BlaBlaDesiredSeatSyncRequestResult(true, message, plan.desiredPublishedSeats)\n    }\n''',
)

# Activity state store property and initialization.
replace_once(
    reliable,
    '''    private lateinit var ledger: BlaBlaManualSeatSyncLedger\n    private lateinit var request: BlaBlaManualSeatSyncRequest\n''',
    '''    private lateinit var ledger: BlaBlaManualSeatSyncLedger\n    private lateinit var publicationSeatStateStore: BlaBlaPublicationSeatSyncStateStore\n    private lateinit var request: BlaBlaManualSeatSyncRequest\n''',
)
replace_once(
    reliable,
    '''        ledger = BlaBlaManualSeatSyncLedger(this)\n        account = registry.get''',
    '''        ledger = BlaBlaManualSeatSyncLedger(this)\n        publicationSeatStateStore = BlaBlaPublicationSeatSyncStateStore(this)\n        account = registry.get''',
)
replace_once(
    reliable,
    '''        if (request.source !in setOf(BookingSource.PRIVATE.name, BookingSource.OTHER.name) || request.seatDelta == 0) {\n            finishInvalid("Operação externa inválida; ela foi descartada para não bloquear a fila.")\n            return\n        }\n\n        val ledgerEntry = ledger.entry(request.localBookingId)\n        if (\n            request.seatDelta < 0 &&''',
    '''        val desiredPublishedSeats = request.desiredPublishedSeats\n        if (desiredPublishedSeats == null && (request.source !in setOf(BookingSource.PRIVATE.name, BookingSource.OTHER.name) || request.seatDelta == 0)) {\n            finishInvalid("Operação externa legada inválida; ela foi descartada para não bloquear a fila.")\n            return\n        }\n        if (desiredPublishedSeats != null && desiredPublishedSeats < 0) {\n            finishInvalid("Estado desejado de vagas inválido.")\n            return\n        }\n        if (desiredPublishedSeats != null) {\n            publicationSeatStateStore.markSyncing(request.profileUuid, request.tripId, desiredPublishedSeats)\n        }\n\n        val ledgerEntry = if (desiredPublishedSeats == null) ledger.entry(request.localBookingId) else null\n        if (\n            desiredPublishedSeats == null &&\n            request.seatDelta < 0 &&''',
)
replace_once(
    reliable,
    '''        if (request.seatDelta > 0) {\n            if (ledgerEntry == null) {''',
    '''        if (desiredPublishedSeats == null && request.seatDelta > 0) {\n            if (ledgerEntry == null) {''',
)
# Record desired on start.
replace_once(
    reliable,
    '''            "request=${request.id} booking=${request.localBookingId} delta=${request.seatDelta} tripIdPresent=true profileUuidPresent=true",''',
    '''            "request=${request.id} booking=${request.localBookingId} delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} tripIdPresent=true profileUuidPresent=true",''',
)
# Use desired-state decision and persist observed value.
replace_once(
    reliable,
    '''                    val decision = BlaBlaReliableSeatSyncPolicy.decide(\n                        currentSeats = state.seats,\n                        canAdd = state.canAdd,\n                        canRemove = state.canRemove,\n                        seatDelta = request.seatDelta,\n                        attempt = existingAttempt,\n                    )\n''',
    '''                    request.desiredPublishedSeats?.let { desired ->\n                        publicationSeatStateStore.markObserved(request.profileUuid, request.tripId, state.seats)\n                    }\n                    val decision = request.desiredPublishedSeats?.let { desired ->\n                        BlaBlaReliableSeatSyncPolicy.decideDesired(\n                            currentSeats = state.seats,\n                            canAdd = state.canAdd,\n                            canRemove = state.canRemove,\n                            desiredPublishedSeats = desired,\n                        )\n                    } ?: BlaBlaReliableSeatSyncPolicy.decide(\n                        currentSeats = state.seats,\n                        canAdd = state.canAdd,\n                        canRemove = state.canRemove,\n                        seatDelta = request.seatDelta,\n                        attempt = existingAttempt,\n                    )\n''',
)
replace_once(
    reliable,
    '''                        "request=${request.id} current=${state.seats} delta=${request.seatDelta} action=${decision.action.name} target=${decision.targetSeats ?: -1} attempt=${existingAttempt != null} compensation=${existingAttempt?.compensateAfterCancellation == true}",''',
    '''                        "request=${request.id} current=${state.seats} delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} action=${decision.action.name} target=${decision.targetSeats ?: -1} attempt=${existingAttempt != null} compensation=${existingAttempt?.compensateAfterCancellation == true}",''',
)
# For absolute state, do not mutate per-passenger decrease ledger.
replace_once(
    reliable,
    '''    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {\n        if (request.seatDelta < 0) {\n            ledger.markVerifiedDecrease(request)\n        } else {\n            ledger.clearAfterVerifiedReverse(request.localBookingId)\n            bindingStore.remove(request.localBookingId)\n        }\n''',
    '''    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {\n        if (request.desiredPublishedSeats != null) {\n            publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, afterSeats)\n        } else if (request.seatDelta < 0) {\n            ledger.markVerifiedDecrease(request)\n        } else {\n            ledger.clearAfterVerifiedReverse(request.localBookingId)\n            bindingStore.remove(request.localBookingId)\n        }\n''',
)
replace_once(
    reliable,
    '''        UnifiedDebugEventStore.record(\n            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFIED",\n            packageName,\n            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} alreadyApplied=$alreadyApplied ledger=true",\n        )\n''',
    '''        UnifiedDebugEventStore.record(\n            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFIED",\n            packageName,\n            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} alreadyApplied=$alreadyApplied ledger=${request.desiredPublishedSeats == null}",\n        )\n''',
)
replace_once(
    reliable,
    '''    private fun completeNoOp(message: String) {\n        if (::request.isInitialized) {\n            requestStore.remove(request.id)\n            attemptStore.clear(request.id)\n            if (request.seatDelta > 0) bindingStore.remove(request.localBookingId)\n        }\n''',
    '''    private fun completeNoOp(message: String) {\n        if (::request.isInitialized) {\n            requestStore.remove(request.id)\n            attemptStore.clear(request.id)\n            if (request.desiredPublishedSeats != null) {\n                publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, request.desiredPublishedSeats!!)\n            } else if (request.seatDelta > 0) {\n                bindingStore.remove(request.localBookingId)\n            }\n        }\n''',
)
replace_once(
    reliable,
    '''    private fun finishInvalid(message: String) {\n        if (::request.isInitialized) {\n            requestStore.remove(request.id)\n            attemptStore.clear(request.id)\n        }\n''',
    '''    private fun finishInvalid(message: String) {\n        if (::request.isInitialized) {\n            requestStore.remove(request.id)\n            attemptStore.clear(request.id)\n            if (request.desiredPublishedSeats != null && ::publicationSeatStateStore.isInitialized) {\n                publicationSeatStateStore.markError(request.profileUuid, request.tripId, "Falha ao sincronizar vagas ❌ • $message")\n            }\n        }\n''',
)
insert_after(
    reliable,
    '''    private fun finishPending(message: String, rotate: Boolean) {\n        if (::request.isInitialized) {\n''',
    '''            if (request.desiredPublishedSeats != null && ::publicationSeatStateStore.isInitialized) {\n                publicationSeatStateStore.markPending(\n                    request.profileUuid,\n                    request.tripId,\n                    "Vagas aguardando sincronização ⚠️ • $message",\n                    request.desiredPublishedSeats,\n                )\n            }\n''',
)
replace_once(
    reliable,
    '''            attempt.seatDelta == request.seatDelta\n''',
    '''            attempt.seatDelta == request.seatDelta &&\n            (request.desiredPublishedSeats == null || attempt.targetSeats == request.desiredPublishedSeats)\n''',
)

# ---------------------------------------------------------------------------
# Quick passenger save/remove: local occupancy first, then delegate desired-state
# recalculation to the card callback. No blind per-booking delta is queued here.
# ---------------------------------------------------------------------------
quick_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt"
replace_once(
    quick_ui,
    '''                }.onSuccess {\n                    val external = if (onBlaBlaSyncRequested != null) {\n                        BlaBlaReliableSeatSyncBridge.enqueueForManualBooking(\n                            context = context,\n                            trip = trip,\n                            booking = plan.passenger,\n                            seatDelta = -plan.passenger.seats,\n                            explicitTarget = externalSeatTarget,\n                        )\n                    } else null\n                    name = ""\n''',
    '''                }.onSuccess {\n                    name = ""\n''',
)
replace_once(
    quick_ui,
    '''                    onChanged(\n                        when {\n                            external != null -> "Passageiro particular adicionado. Ocupação interna recalculada • ajustando ${plan.passenger.seats} vaga(s) na mesma publicação BlaBlaCar…"\n                            onBlaBlaSyncRequested != null -> "Passageiro particular adicionado. Ocupação interna recalculada • sincronização externa pendente ⚠️"\n                            else -> "Passageiro particular adicionado. Ocupação interna recalculada."\n                        },\n                    )\n                    onBlaBlaSyncRequested?.invoke()\n''',
    '''                    onChanged("Passageiro particular adicionado. Ocupação física por trecho recalculada.")\n                    onBlaBlaSyncRequested?.invoke()\n''',
)
replace_once(
    quick_ui,
    '''                                .onSuccess {\n                                    val cancellation = if (onBlaBlaSyncRequested != null) {\n                                        BlaBlaReliableSeatSyncBridge.onManualBookingCancelled(\n                                            context = context,\n                                            trip = trip,\n                                            booking = booking,\n                                            explicitTarget = externalSeatTarget,\n                                        )\n                                    } else {\n                                        BlaBlaManualSeatCancellationResult(\n                                            shouldSync = false,\n                                            message = "Passageiro manual cancelado. Vaga interna liberada.",\n                                        )\n                                    }\n                                    onChanged(cancellation.message)\n                                    if (cancellation.shouldSync) onBlaBlaSyncRequested?.invoke()\n                                }\n''',
    '''                                .onSuccess {\n                                    onChanged("Passageiro manual cancelado. Ocupação física por trecho recalculada.")\n                                    onBlaBlaSyncRequested?.invoke()\n                                }\n''',
)

# ---------------------------------------------------------------------------
# Timeline: configured vehicle capacity always wins, compact segment availability,
# and a seat-only callback that first queues absolute desired state.
# ---------------------------------------------------------------------------
timeline_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
replace_once(
    timeline_ui,
    '''    return entries.map { entry -> if (entry.capacity > 0) entry else entry.copy(capacity = vehicleCapacity) }\n''',
    '''    return entries.map { entry -> if (entry.capacity == vehicleCapacity) entry else entry.copy(capacity = vehicleCapacity) }\n''',
)
replace_once(
    timeline_ui,
    '''    val dark = isSystemInDarkTheme()\n    val profileColors = timelineProfileCardColors(profileColorSlot, dark)\n    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }\n''',
    '''    val context = LocalContext.current\n    val dark = isSystemInDarkTheme()\n    val profileColors = timelineProfileCardColors(profileColorSlot, dark)\n    val seatPlan = timelineDesiredSeatSyncPlan(entry, trip, store)\n    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }\n    var showSeatDetails by remember(entry.tripId) { mutableStateOf(false) }\n\n    fun requestSeatOnlySync(selectedTrip: Trip?, reason: String) {\n        val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(\n            context = context,\n            entry = entry,\n            trip = selectedTrip,\n            store = store,\n            reason = reason,\n        )\n        onChanged(result.message)\n        if (result.shouldSync) onManualSeatSyncRequested()\n    }\n''',
)
# Add compact seat availability after occupancy display.
insert_before(
    timeline_ui,
    '''            val sourceLine = entry.sourcePassengerSeats.filterValues { it > 0 }.entries.joinToString(" • ") { (source, seats) ->\n''',
    '''            TextButton(onClick = { showSeatDetails = true }) {\n                Text(if (seatPlan != null) "💺 ${seatPlan.desiredPublishedSeats}" else "💺 ⏳")\n            }\n\n''',
)
replace_once(
    timeline_ui,
    '''                onChanged = onChanged,\n                onSyncExactCard = onSyncExactCard,\n                onAddManualPassenger = {\n''',
    '''                onChanged = onChanged,\n                onSyncExactCard = onSyncExactCard,\n                onSyncSeatsOnly = { requestSeatOnlySync(trip, "manual_card_shortcut") },\n                onAddManualPassenger = {\n''',
)
replace_once(
    timeline_ui,
    '''            onChanged = onChanged,\n            onTargetSync = onManualSeatSyncRequested,\n            onDismiss = { directPassengerTrip = null },\n''',
    '''            onChanged = onChanged,\n            onTargetSync = { requestSeatOnlySync(selectedTrip, "automatic_after_passenger_change") },\n            onDismiss = { directPassengerTrip = null },\n''',
)
insert_before(
    timeline_ui,
    '''    directPassengerTrip?.let { selectedTrip ->\n''',
    '''    if (showSeatDetails) {\n        AlertDialog(\n            onDismissRequest = { showSeatDetails = false },\n            title = { Text("VAGAS POR TRECHO") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {\n                    if (seatPlan == null) {\n                        Text("Leitura por trecho pendente. O Rota Certa não vai inventar disponibilidade enquanto os passageiros externos não estiverem completos.")\n                    } else {\n                        seatPlan.loads.forEach { load ->\n                            Text("${load.from.name} → ${load.to.name}    ${load.availableSeats} vaga(s)")\n                        }\n                        Text("💺 ${seatPlan.desiredPublishedSeats} = menor disponibilidade física relevante", style = MaterialTheme.typography.bodySmall)\n                    }\n                }\n            },\n            confirmButton = { TextButton(onClick = { showSeatDetails = false }) { Text("Fechar") } },\n        )\n    }\n\n''',
)

# ---------------------------------------------------------------------------
# Passenger row: persistent seat-only shortcut/status even with zero passengers,
# edit seats/boarding/dropoff, and cancellation triggers desired-state recalculation.
# ---------------------------------------------------------------------------
passenger_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
replace_once(
    passenger_ui,
    '''import androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.Icon\n''',
    '''import androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\nimport androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.Icon\n''',
)
replace_once(
    passenger_ui,
    '''    onChanged: (String) -> Unit,\n    onSyncExactCard: (() -> Unit)? = null,\n    onAddManualPassenger: (() -> Unit)? = null,\n) {\n''',
    '''    onChanged: (String) -> Unit,\n    onSyncExactCard: (() -> Unit)? = null,\n    onSyncSeatsOnly: (() -> Unit)? = null,\n    onAddManualPassenger: (() -> Unit)? = null,\n) {\n''',
)
replace_once(
    passenger_ui,
    '''    if (hasExternalTripActionEvidence(entry)) {\n        TripBlaBlaTripActionRow(entry, onSyncExactCard, onAddManualPassenger)\n    }\n''',
    '''    if (hasExternalTripActionEvidence(entry)) {\n        TripBlaBlaTripActionRow(entry, onSyncExactCard, onSyncSeatsOnly, onAddManualPassenger)\n    }\n''',
)
replace_once(
    passenger_ui,
    '''    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n''',
    '''    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n    var editManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n''',
)
replace_once(
    passenger_ui,
    '''                    if (manualBooking != null) {\n                        TextButton(onClick = {\n                            cancelManualRow = row\n                            profileRow = null\n                        }) { Text("Cancelar / excluir desta viagem") }\n                    }\n''',
    '''                    if (manualBooking != null) {\n                        TextButton(onClick = {\n                            editManualRow = row\n                            profileRow = null\n                        }) { Text("Editar lugares / trecho") }\n                        TextButton(onClick = {\n                            cancelManualRow = row\n                            profileRow = null\n                        }) { Text("Cancelar / excluir desta viagem") }\n                    }\n''',
)
replace_once(
    passenger_ui,
    '''                        store.saveBooking(selectedBooking.copy(status = BookingStatus.CANCELLED))\n                        val cancellation = BlaBlaReliableSeatSyncBridge.onManualBookingCancelled(\n                            context = context,\n                            trip = selectedTrip,\n                            booking = selectedBooking,\n                            explicitTarget = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry),\n                        )\n                        UnifiedDebugEventStore.record(\n                            "AGENDA_MANUAL_PASSENGER_CANCELLED",\n                            context.packageName,\n                            "timeline=true seats=${selectedBooking.seats} shouldSync=${cancellation.shouldSync}",\n                        )\n                        cancelManualRow = null\n                        onChanged(cancellation.message)\n                        if (cancellation.shouldSync) onSyncExactCard?.invoke()\n''',
    '''                        store.saveBooking(selectedBooking.copy(status = BookingStatus.CANCELLED))\n                        UnifiedDebugEventStore.record(\n                            "AGENDA_MANUAL_PASSENGER_CANCELLED",\n                            context.packageName,\n                            "timeline=true seats=${selectedBooking.seats} desiredStateRecalculation=true",\n                        )\n                        cancelManualRow = null\n                        onChanged("Passageiro manual cancelado. Ocupação física por trecho recalculada.")\n                        onSyncSeatsOnly?.invoke()\n''',
)
# Insert edit dialog before cancel dialog.
insert_before(
    passenger_ui,
    '''    cancelManualRow?.let { row ->\n''',
    '''    editManualRow?.let { row ->\n        val currentTrip = trip\n        val booking = currentTrip?.let { selectedTrip ->\n            row.localBookingId?.let { bookingId ->\n                store.bookingsFor(selectedTrip.id).firstOrNull { candidate ->\n                    candidate.id == bookingId &&\n                        candidate.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&\n                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&\n                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)\n                }\n            }\n        }\n        if (currentTrip != null && booking != null) {\n            ManualPassengerOccupancyEditorDialog(\n                trip = currentTrip,\n                booking = booking,\n                existingBookings = store.bookingsFor(currentTrip.id),\n                onDismiss = { editManualRow = null },\n                onSave = { updated ->\n                    store.saveBooking(updated)\n                    editManualRow = null\n                    onChanged("Passageiro atualizado. Ocupação física por trecho recalculada.")\n                    onSyncSeatsOnly?.invoke()\n                },\n                onError = onChanged,\n            )\n        } else {\n            editManualRow = null\n        }\n    }\n\n''',
)
# Add editor composable before fare editor.
insert_before(
    passenger_ui,
    '''@Composable\nprivate fun PassengerFareEditorDialog(\n''',
    '''@Composable\nprivate fun ManualPassengerOccupancyEditorDialog(\n    trip: Trip,\n    booking: Booking,\n    existingBookings: List<Booking>,\n    onDismiss: () -> Unit,\n    onSave: (Booking) -> Unit,\n    onError: (String) -> Unit,\n) {\n    val stops = trip.stops.sortedBy(TripStop::order)\n    var seats by remember(booking.id) { mutableStateOf(booking.seats) }\n    var fromId by remember(booking.id) { mutableStateOf(booking.boardingStopId) }\n    var toId by remember(booking.id) { mutableStateOf(booking.dropoffStopId) }\n    var fromOpen by remember(booking.id) { mutableStateOf(false) }\n    var toOpen by remember(booking.id) { mutableStateOf(false) }\n    val fromIndex = stops.indexOfFirst { it.id == fromId }\n    val toIndex = stops.indexOfFirst { it.id == toId }\n    val valid = fromIndex >= 0 && toIndex > fromIndex\n    val availability = if (valid) runCatching {\n        SeatAvailabilityEngine.availability(\n            trip,\n            existingBookings.filterNot { it.id == booking.id },\n            fromId,\n            toId,\n            seats,\n        )\n    }.getOrNull() else null\n\n    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text("Editar lugares / trecho") },\n        text = {\n            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                Text(booking.passengerName)\n                Column {\n                    OutlinedButton(onClick = { fromOpen = true }, modifier = Modifier.fillMaxWidth()) {\n                        Text("Embarque: ${stops.firstOrNull { it.id == fromId }?.name ?: "Selecionar"}")\n                    }\n                    DropdownMenu(expanded = fromOpen, onDismissRequest = { fromOpen = false }) {\n                        stops.dropLast(1).forEach { stop ->\n                            DropdownMenuItem(\n                                text = { Text(stop.name) },\n                                onClick = {\n                                    fromId = stop.id\n                                    if (stops.indexOfFirst { it.id == toId } <= stop.order) toId = ""\n                                    fromOpen = false\n                                },\n                            )\n                        }\n                    }\n                }\n                Column {\n                    OutlinedButton(enabled = fromIndex >= 0, onClick = { toOpen = true }, modifier = Modifier.fillMaxWidth()) {\n                        Text("Destino: ${stops.firstOrNull { it.id == toId }?.name ?: "Selecionar"}")\n                    }\n                    DropdownMenu(expanded = toOpen, onDismissRequest = { toOpen = false }) {\n                        stops.filterIndexed { index, _ -> fromIndex >= 0 && index > fromIndex }.forEach { stop ->\n                            DropdownMenuItem(text = { Text(stop.name) }, onClick = { toId = stop.id; toOpen = false })\n                        }\n                    }\n                }\n                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {\n                    OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }\n                    Text(if (seats == 1) "1 lugar" else "$seats lugares")\n                    OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }\n                }\n                Text(\n                    when {\n                        !valid -> "Selecione embarque e destino."\n                        availability?.canBook == true -> "${availability.availableSeats} vaga(s) disponíveis neste trecho antes da alteração."\n                        else -> "Sem capacidade física para essa alteração."\n                    },\n                    style = MaterialTheme.typography.bodySmall,\n                )\n            }\n        },\n        confirmButton = {\n            TextButton(\n                enabled = valid && availability?.canBook == true,\n                onClick = {\n                    runCatching {\n                        QuickPassengerEngine.updateManualBooking(\n                            trip = trip,\n                            existingBookings = existingBookings,\n                            booking = booking,\n                            boardingStopId = fromId,\n                            dropoffStopId = toId,\n                            seats = seats,\n                        )\n                    }.onSuccess(onSave).onFailure { onError(it.message ?: "Não foi possível atualizar o passageiro.") }\n                },\n            ) { Text("Salvar") }\n        },\n        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },\n    )\n}\n\n''',
)
# Replace action row signature/body start so it can display status and invoke seat-only sync.
replace_once(
    passenger_ui,
    '''private fun TripBlaBlaTripActionRow(\n    entry: TripTimelineEntry,\n    onSyncExactCard: (() -> Unit)?,\n    onAddManualPassenger: (() -> Unit)?,\n) {\n    val context = LocalContext.current\n    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.End,\n        verticalAlignment = Alignment.CenterVertically,\n    ) {\n''',
    '''private fun TripBlaBlaTripActionRow(\n    entry: TripTimelineEntry,\n    onSyncExactCard: (() -> Unit)?,\n    onSyncSeatsOnly: (() -> Unit)?,\n    onAddManualPassenger: (() -> Unit)?,\n) {\n    val context = LocalContext.current\n    val target = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry)\n    val seatStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }\n    val seatState = target?.let { seatStateStore.get(it.profileUuid, it.tripId) }\n    val seatLabel = when (seatState?.state) {\n        BlaBlaPublicationSeatSyncVisualState.SYNCING -> "💺⏳"\n        BlaBlaPublicationSeatSyncVisualState.SYNCED -> "💺✅"\n        BlaBlaPublicationSeatSyncVisualState.PENDING -> "💺⚠️"\n        BlaBlaPublicationSeatSyncVisualState.ERROR -> "💺❌"\n        BlaBlaPublicationSeatSyncVisualState.AVAILABLE, null -> "💺🔄"\n    }\n    Column(modifier = Modifier.fillMaxWidth()) {\n        Row(\n            modifier = Modifier.fillMaxWidth(),\n            horizontalArrangement = Arrangement.End,\n            verticalAlignment = Alignment.CenterVertically,\n        ) {\n''',
)
# Add seat button immediately before full-card sync button.
insert_before(
    passenger_ui,
    '''        if (onSyncExactCard != null && !entry.blablaProfileUuid.isNullOrBlank() && !entry.blablaTripId.isNullOrBlank()) {\n''',
    '''        if (onSyncSeatsOnly != null && target != null) {\n            TextButton(\n                enabled = seatState?.state != BlaBlaPublicationSeatSyncVisualState.SYNCING,\n                onClick = {\n                    UnifiedDebugEventStore.record(\n                        "AGENDA_SEAT_ONLY_SYNC_REQUESTED",\n                        context.packageName,\n                        "profileUuidPresent=true tripIdPresent=true",\n                    )\n                    onSyncSeatsOnly()\n                },\n                contentPadding = COMPACT_ACTION_PADDING,\n            ) { Text(seatLabel) }\n        }\n''',
)
# Close Column and show explanation/status after the row. Anchor at end of action row before next data class.
replace_once(
    passenger_ui,
    '''        }\n    }\n}\n\ninternal data class PassengerPickupMapTarget(val query: String)\n''',
    '''        }\n        }\n        Text(\n            seatState?.message ?: "💺🔄 Sincronizar somente as vagas deste card",\n            style = MaterialTheme.typography.bodySmall,\n            modifier = Modifier.fillMaxWidth(),\n        )\n    }\n}\n\ninternal data class PassengerPickupMapTarget(val query: String)\n''',
)

# ---------------------------------------------------------------------------
# Collector UI: legacy deltas are retired on upgrade, and a successful seat-only
# write does not start a full account synchronization afterward.
# ---------------------------------------------------------------------------
collector_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt"
replace_once(
    collector_ui,
    '''        message = seatMessage\n        onChanged(seatMessage)\n        if (result.resultCode == Activity.RESULT_OK && !accountId.isNullOrBlank()) {\n            // Read-after-write: after the exact options page verified the new value,\n            // refresh only the authenticated account that owns that publication.\n            syncDateScope = null\n            syncQueue = listOf(accountId)\n            syncCursor = 0\n            syncing = true\n            archiving = false\n        }\n''',
    '''        message = seatMessage\n        onChanged(seatMessage)\n        // Seat-only writer already reloads the exact options page and verifies the\n        // published number. Do not chain a full trip/account collector sync here.\n        refresh()\n''',
)
insert_after(
    collector_ui,
    '''    // Discard snapshots from the old hard-coded two-account candidate. The\n    // dynamic registry is authoritative from this version onward and starts empty.\n    LaunchedEffect(Unit) {\n''',
    '''        val retired = manualSeatStore.discardLegacyDeltaRequests()\n        retired.forEach(manualSeatAttemptStore::clear)\n        if (retired.isNotEmpty()) {\n            UnifiedDebugEventStore.record(\n                "EXTERNAL_SEAT_LEGACY_DELTA_RETIRED",\n                context.packageName,\n                "count=${retired.size} reason=desired_state_migration",\n            )\n        }\n''',
)

# ---------------------------------------------------------------------------
# Focused tests for the user's concrete segment examples + desired-state idempotency.
# ---------------------------------------------------------------------------
test_path = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/TripSegmentSeatSync0292Test.kt")
if test_path.exists():
    raise SystemExit("0292 test already exists")
test_path.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripSegmentSeatSync0292Test {
    private val stops = listOf(
        TripStop(id = "sa", order = 0, name = "Santo André"),
        TripStop(id = "sp", order = 1, name = "São Paulo"),
        TripStop(id = "ex", order = 2, name = "Extrema"),
        TripStop(id = "pa", order = 3, name = "Pouso Alegre"),
        TripStop(id = "tc", order = 4, name = "Três Corações"),
        TripStop(id = "st", order = 5, name = "São Thomé"),
    )
    private val trip = Trip(
        id = "trip",
        title = "Santo André → São Thomé",
        departureAtMillis = 1L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = stops,
    )

    @Test
    fun mixedExternalAndManualPassengersAreCalculatedPerSegment() {
        val bookings = listOf(
            booking("a", BookingSource.BLABLACAR, "sa", "pa", 1),
            booking("b", BookingSource.BLABLACAR, "sp", "tc", 1),
            booking("c", BookingSource.PRIVATE, "pa", "st", 2),
            booking("d", BookingSource.PRIVATE, "tc", "st", 1),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(3, 2, 2, 1, 1), loads.map(SegmentLoad::availableSeats))
        assertEquals(1, loads.minOf(SegmentLoad::availableSeats))
    }

    @Test
    fun seatBecomesAvailableAgainAfterPassengerDropoff() {
        val bookings = listOf(booking("a", BookingSource.PRIVATE, "sa", "pa", 1))
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(3, 3, 3, 4, 4), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun editingOneSeatToTwoRechecksOnlyAffectedSegments() {
        val original = booking("manual", BookingSource.PRIVATE, "pa", "st", 1)
        val updated = QuickPassengerEngine.updateManualBooking(
            trip = trip,
            existingBookings = listOf(original),
            booking = original,
            boardingStopId = "pa",
            dropoffStopId = "st",
            seats = 2,
        )
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(updated))
        assertEquals(listOf(4, 4, 4, 2, 2), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun cancellationImmediatelyReturnsPhysicalCapacity() {
        val active = booking("manual", BookingSource.PRIVATE, "tc", "st", 3)
        val cancelled = active.copy(status = BookingStatus.CANCELLED)
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, listOf(active)).last().availableSeats)
        assertEquals(4, SeatAvailabilityEngine.segmentLoads(trip, listOf(cancelled)).last().availableSeats)
    }

    @Test
    fun duplicateOccupancyGroupIsNotCountedTwice() {
        val external = booking("external", BookingSource.BLABLACAR, "sp", "tc", 2).copy(occupancyGroupId = "same-seat")
        val manual = booking("manual", BookingSource.PRIVATE, "sp", "tc", 2).copy(occupancyGroupId = "same-seat")
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(external, manual))
        assertEquals(2, loads[1].occupiedSeats)
        assertEquals(2, loads[2].occupiedSeats)
    }

    @Test
    fun desiredStateIsIdempotentAndNeverBlindlyDecrements() {
        val first = BlaBlaReliableSeatSyncPolicy.decideDesired(3, canAdd = true, canRemove = true, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, first.action)
        assertEquals(2, first.targetSeats)

        val repeated = BlaBlaReliableSeatSyncPolicy.decideDesired(2, canAdd = true, canRemove = true, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, repeated.action)
        assertEquals(2, repeated.targetSeats)
    }

    @Test
    fun desiredStateCanRestorePlacesWithoutRememberingOldDelta() {
        val restore = BlaBlaReliableSeatSyncPolicy.decideDesired(1, canAdd = true, canRemove = true, desiredPublishedSeats = 4)
        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, restore.action)
        assertEquals(4, restore.targetSeats)
    }

    @Test
    fun unavailableEditorFailsPendingWithoutPretendingSuccess() {
        val decision = BlaBlaReliableSeatSyncPolicy.decideDesired(3, canAdd = true, canRemove = false, desiredPublishedSeats = 2)
        assertEquals(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE, decision.action)
    }

    @Test
    fun configuredCapacityAlwaysOverridesExternalOrStaleCardCapacity() {
        val entry = TripTimelineEntry(
            tripId = "x",
            profileId = "p",
            profileLabel = "P",
            departureAtMillis = 1L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 2,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
        )
        assertEquals(4, applyConfiguredVehicleCapacity(listOf(entry), 4).single().capacity)
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
}
''')
