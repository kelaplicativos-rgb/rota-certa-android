from pathlib import Path

SOURCE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt')
s = SOURCE.read_text()

if 'CARD_TRAVERSAL_COMPLETE' in s and 'MAX_TRIPS' not in s:
    raise SystemExit(0)

def once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    s = s.replace(old, new, 1)

def between(start: str, end: str, replacement: str, label: str) -> None:
    global s
    i = s.find(start)
    if i < 0:
        raise SystemExit(f'{label}: start not found')
    j = s.find(end, i)
    if j < 0:
        raise SystemExit(f'{label}: end not found')
    s = s[:i] + replacement.rstrip() + '\n\n' + s[j:]

once(
'''private data class DynamicRideList(
    val candidates: List<BlaBlaDomRideCandidate> = emptyList(),
    val bodyText: String = "",
    val domHtml: String = "",
)''',
'''private data class DynamicRideList(
    val candidates: List<BlaBlaDomRideCandidate> = emptyList(),
    val bodyText: String = "",
    val scrollY: Int = 0,
    val scrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
    val domHtml: String = "",
)''', 'ride-model')

once(
'''    private var rideReadAttempts = 0
    private var tripRosterReadAttempts = 0''',
'''    private var rideReadAttempts = 0
    private val completedCardTraversalKeys = linkedSetOf<String>()
    private var currentCardTraversalKey = ""
    private var ridesResumeScrollY = 0
    private var ridesRestorePending = false
    private var ridesBottomStablePasses = 0
    private var tripRosterReadAttempts = 0''', 'state')

once(
'''        rideReadAttempts = 0
        tripRosterReadAttempts = 0''',
'''        rideReadAttempts = 0
        completedCardTraversalKeys.clear()
        currentCardTraversalKey = ""
        ridesResumeScrollY = 0
        ridesRestorePending = false
        ridesBottomStablePasses = 0
        tripRosterReadAttempts = 0''', 'reset')

between('    private fun captureRideList() {', '    private fun loadCurrentCandidate() {', '''    private fun captureRideList() {
        if (phase != Phase.RIDES) return
        if (ridesRestorePending && ridesResumeScrollY > 0) {
            ridesRestorePending = false
            webView.evaluateJavascript("window.scrollTo(0, ${ridesResumeScrollY.coerceAtLeast(0)}); 'ok';") {
                webView.postDelayed({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
            }
            return
        }
        evaluate<DynamicRideList>(RIDE_LIST_JS) { result ->
            if (result == null) {
                blockSyncWithoutCurrentCard("rides_dom_unreadable")
                return@evaluate
            }
            store.saveDiagnosticHtml(account, "rides", result.domHtml)
            val visible = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    isBlaBla(href) && (href.contains("/rides/offer") || href.contains("/trip?") || href.contains("/trip/"))
                }
                .distinctBy { canonicalHref(it.href) }
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visible.size} completed=${completedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} pastDateFilter=false fixedTripLimit=false",
            )
            if (visible.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                webView.postDelayed({ captureRideList() }, 1200)
                return@evaluate
            }
            if (visible.isEmpty() && looksLoggedOut(result.bodyText)) {
                blockSyncWithoutCurrentCard("rides_session_logged_out")
                return@evaluate
            }
            val next = visible.firstOrNull { candidate ->
                val key = tripTraversalKey(candidate)
                key.isNotBlank() && key !in completedCardTraversalKeys
            }
            if (next != null) {
                rideReadAttempts = 0
                ridesBottomStablePasses = 0
                ridesResumeScrollY = result.scrollY.coerceAtLeast(0)
                currentCardTraversalKey = tripTraversalKey(next)
                candidates = listOf(next)
                candidateIndex = 0
                phase = Phase.DETAIL
                UnifiedDebugEventStore.record(
                    "CARD_TRAVERSAL_START",
                    packageName,
                    "account=${account.displayLabel} order=${completedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=true",
                )
                loadCurrentCandidate()
                return@evaluate
            }
            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluate
            }
            if (!result.atBottom) {
                val viewport = result.viewportHeight.coerceAtLeast(600)
                val maxScroll = (result.scrollHeight - 1).coerceAtLeast(0)
                val target = (result.scrollY + maxOf(600, viewport * 3 / 4)).coerceAtMost(maxScroll)
                if (target <= result.scrollY && result.scrollHeight > result.viewportHeight) {
                    blockSyncWithoutCurrentCard("rides_scroll_no_progress")
                    return@evaluate
                }
                ridesResumeScrollY = target
                UnifiedDebugEventStore.record(
                    "RIDES_TRAVERSAL_SCROLL",
                    packageName,
                    "account=${account.displayLabel} from=${result.scrollY} to=$target completed=${completedCardTraversalKeys.size}",
                )
                webView.evaluateJavascript("window.scrollTo(0, $target); 'ok';") {
                    webView.postDelayed({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
                }
                return@evaluate
            }
            if (ridesBottomStablePasses < REQUIRED_STABLE_BOTTOM_PASSES) {
                ridesBottomStablePasses++
                webView.postDelayed({ captureRideList() }, RIDES_BOTTOM_SETTLE_MS)
                return@evaluate
            }
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            saveFinalSnapshotOnce(verified)
            if (verified) {
                UnifiedDebugEventStore.record(
                    "RIDES_TRAVERSAL_COMPLETE",
                    packageName,
                    "account=${account.displayLabel} completedCards=${completedCardTraversalKeys.size} pastDateFilter=false fixedTripLimit=false",
                )
                completeSync(collected.size)
            } else {
                blockSyncWithoutCurrentCard("identity_not_verified_after_traversal")
            }
        }
    }''', 'capture-list')

between('    private fun loadCurrentCandidate() {', '    private fun saveFinalSnapshotOnce(', '''    private fun loadCurrentCandidate() {
        val candidate = candidates.getOrNull(candidateIndex)
        if (candidate == null || currentCardTraversalKey.isBlank()) {
            blockSyncWithoutCurrentCard("current_card_missing")
            return
        }
        statusView.text = "${account.displayLabel} • card ${completedCardTraversalKeys.size + 1} • lendo completo…"
        UnifiedDebugEventStore.record(
            "TRIP_DETAIL_REQUIRED",
            packageName,
            "account=${account.displayLabel} order=${completedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()} batchShortcut=false",
        )
        loadTrackedUrl(candidate.href)
    }''', 'load-card')

once(
'''            pendingTripPassengerCardIndexes.clear()
            result.passengerHrefs.forEach { target ->
                if (!target.startsWith(CARD_TARGET_PREFIX)) return@forEach
                val cardIndex = target.removePrefix(CARD_TARGET_PREFIX).toIntOrNull() ?: return@forEach
                if (cardIndex in pendingTripPassengers.indices) pendingTripPassengerCardIndexes[cardIndex] = cardIndex
            }''',
'''            pendingTripPassengerCardIndexes.clear()
            pendingTripPassengers.indices.forEach { rowIndex ->
                pendingTripPassengerCardIndexes[rowIndex] = rowIndex
            }''', 'passenger-ui-order')

once(
'''        return passenger.phone.isNullOrBlank() || metadata?.fareMinorUnits == null || metadata?.hasBoardingCoordinates != true''',
'''        return passenger.phone.isNullOrBlank() || metadata?.fareMinorUnits == null || metadata?.boardingAddress.isNullOrBlank()''', 'required-evidence')

once(
'''                BlaBlaDirectPassengerStep.SKIP -> {
                    if (!hasBookingHref && cardIndex == null) {
                        UnifiedDebugEventStore.record(
                            "PASSENGER_CONTACT_SKIPPED",
                            packageName,
                            "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${passengerContactIndex + 1}/${pendingTripPassengers.size} reason=no_individual_target",
                        )
                    }
                    passengerContactIndex++
                }''',
'''                BlaBlaDirectPassengerStep.SKIP -> {
                    UnifiedDebugEventStore.record(
                        "PASSENGER_EVIDENCE_INCOMPLETE",
                        packageName,
                        "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${passengerContactIndex + 1}/${pendingTripPassengers.size} reason=no_individual_target action=block_card",
                    )
                    skipped++
                    blockCurrentCard(expectedSync, expectedCandidate, "passenger_individual_target_missing")
                    return
                }''', 'skip-block')

once(
'''            passengerContactIndex = expectedPassenger + 1
            passengerCardReadAttempts = 0
            loadNextPassengerContact(expectedSync, expectedCandidate)''',
'''            skipped++
            passengerCardReadAttempts = 0
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_card_not_clickable")''', 'card-click-block')

once(
'''            passengerContactIndex = expectedPassenger + 1
            passengerContactReadAttempts = 0
            loadNextPassengerContact(expectedSync, expectedCandidate)
            return''',
'''            skipped++
            passengerContactReadAttempts = 0
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_page_identity_unproven")
            return''', 'identity-block')

between('    private fun capturePassengerContact(', '    private fun passengerCaptureIsCurrent(', '''    private fun capturePassengerContact(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ) {
        if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
            recordStale("passenger_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (passengerCaptureInFlight) return
        val current = pendingTripPassengers.getOrNull(expectedPassenger) ?: run {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_missing")
            return
        }
        passengerCaptureInFlight = true
        evaluate<DynamicPassengerContactEvidence>(PASSENGER_CONTACT_JS) { evidence ->
            passengerCaptureInFlight = false
            if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                recordStale("passenger_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            if (evidence == null) {
                if (passengerContactReadAttempts < MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS) {
                    passengerContactReadAttempts++
                    webView.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
                    return@evaluate
                }
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_evidence_unreadable")
                return@evaluate
            }
            evidence.domHtml.takeIf(String::isNotBlank)?.let { html ->
                store.saveDiagnosticHtml(account, "card-${completedCardTraversalKeys.size + 1}-passenger-${expectedPassenger + 1}", html)
            }
            val effectivePhone = current.phone?.takeIf(String::isNotBlank) ?: normalizeCapturedPhone(evidence.phone)
            saveCapturedPassengerFare(current.booking_href, evidence)
            saveCapturedPassengerBoardingEvidence(current.booking_href, evidence)
            val metadata = passengerIdentityStore.externalMetadata(externalPassengerReservationKey(account.profileUuid, current.booking_href))
            val farePresent = metadata?.fareMinorUnits != null
            val routePresent = !current.boarding.isNullOrBlank() && !current.dropoff.isNullOrBlank()
            val resolvedName = current.name.ifBlank { evidence.visibleName.trim() }
            val htmlPresent = evidence.domHtml.isNotBlank()
            val requiredComplete = resolvedName.isNotBlank() && routePresent && farePresent && htmlPresent
            if (!requiredComplete && passengerContactReadAttempts < MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS) {
                passengerContactReadAttempts++
                webView.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
                return@evaluate
            }
            if (!requiredComplete) {
                UnifiedDebugEventStore.record(
                    "PASSENGER_EVIDENCE_INCOMPLETE",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} namePresent=${resolvedName.isNotBlank()} routePresent=$routePresent farePresent=$farePresent htmlPresent=$htmlPresent action=block_card",
                )
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_required_evidence_incomplete")
                return@evaluate
            }
            pendingTripPassengers[expectedPassenger] = current.copy(name = resolvedName, phone = effectivePhone)
            val metadataAfter = passengerIdentityStore.externalMetadata(externalPassengerReservationKey(account.profileUuid, current.booking_href))
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} phonePresent=${effectivePhone != null} farePresent=${metadataAfter?.fareMinorUnits != null} routePresent=$routePresent addressPresent=${!metadataAfter?.boardingAddress.isNullOrBlank()} coordinatePresent=${metadataAfter?.hasBoardingCoordinates == true} bookingLinkPresent=${!current.booking_href.isNullOrBlank()} htmlCaptured=$htmlPresent sequential=true",
            )
            passengerContactIndex = expectedPassenger + 1
            passengerContactReadAttempts = 0
            loadNextPassengerContact(expectedSync, expectedCandidate)
        }
    }''', 'passenger-capture')

between('    private fun finalizeCurrentTrip(expectedSync: Long, expectedCandidate: Int) {', '    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {', '''    private fun finalizeCurrentTrip(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("finalize_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate)
        val result = pendingTripDetail
        val definition = account.verifiedDefinition()
        if (candidate == null || result == null || definition == null) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "pending_trip_state_missing")
            return
        }
        val rosterState = blaBlaDirectRosterState(
            passengerCount = pendingTripPassengers.size,
            rosterComplete = result.detail.passengerRosterComplete,
            explicitEmpty = result.explicitEmptyRoster,
        )
        if (rosterState == BlaBlaDirectRosterState.UNKNOWN) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "finalize_unknown_roster")
            return
        }
        val enrichedDetail = result.detail.copy(passengers = pendingTripPassengers.toList())
        val candidateTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href)
        val detailTripId = BlaBlaTripIdentity.externalTripIdFromHref(enrichedDetail.url)
        if (candidateTripId == null || detailTripId == null || candidateTripId != detailTripId) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "detail_trip_id_mismatch")
            return
        }
        val trip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (trip == null || !identityConfirmedThisSync) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "trip_fields_unparseable")
            return
        }
        collected += trip
        UnifiedDebugEventStore.record(
            "TRIP_ACCEPTED",
            packageName,
            "account=${account.displayLabel} order=${completedCardTraversalKeys.size + 1} tripId=${trip.trip_id.orEmpty()} date=${trip.date} passengers=${trip.passengers.size} rosterComplete=${trip.passenger_roster_complete} sequential=true",
        )
        completeCurrentCard(expectedSync, expectedCandidate)
    }''', 'finalize')

between('    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {', '    private fun recordStale(', '''    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {
        blockCurrentCard(expectedSync, expectedCandidate, "previous_trip_rejection")
    }

    private fun completeCurrentCard(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate) || currentCardTraversalKey.isBlank()) {
            blockCurrentCard(expectedSync, expectedCandidate, "completion_context_invalid")
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()
        completedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(
            "CARD_TRAVERSAL_COMPLETE",
            packageName,
            "account=${account.displayLabel} order=${completedCardTraversalKeys.size} tripId=$tripId passengers=${pendingTripPassengers.size} result=complete nextCardAllowed=true",
        )
        clearPendingCardState()
        currentCardTraversalKey = ""
        candidates = emptyList()
        candidateIndex = 0
        phase = Phase.RIDES
        ridesRestorePending = ridesResumeScrollY > 0
        loadTrackedUrl(RIDES_URL)
    }

    private fun blockCurrentCard(expectedSync: Long, expectedCandidate: Int, reason: String) {
        if (expectedSync != syncGeneration || expectedCandidate != candidateIndex) {
            recordStale("block_card_mismatch_$reason", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()
        UnifiedDebugEventStore.record(
            "CARD_TRAVERSAL_BLOCKED",
            packageName,
            "account=${account.displayLabel} order=${completedCardTraversalKeys.size + 1} tripId=$tripId reason=$reason completedCards=${completedCardTraversalKeys.size} nextCardAllowed=false",
        )
        saveFinalSnapshotOnce(identityConfirmedThisSync && !account.profileUuid.isNullOrBlank())
        phase = Phase.IDLE
        statusView.text = "${account.displayLabel} • card incompleto ⚠️ • sincronização interrompida"
    }

    private fun blockSyncWithoutCurrentCard(reason: String) {
        UnifiedDebugEventStore.record(
            "SYNC_BLOCKED",
            packageName,
            "account=${account.displayLabel} reason=$reason completedCards=${completedCardTraversalKeys.size} nextCardAllowed=false",
        )
        saveFinalSnapshotOnce(identityConfirmedThisSync && !account.profileUuid.isNullOrBlank())
        phase = Phase.IDLE
    }

    private fun clearPendingCardState() {
        pendingTripDetail = null
        pendingTripPassengers.clear()
        pendingTripPassengerCardIndexes.clear()
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        passengerCardReadAttempts = 0
        tripRosterReadAttempts = 0
        passengerCaptureInFlight = false
        passengerCardCaptureInFlight = false
    }

    private fun tripTraversalKey(candidate: BlaBlaDomRideCandidate): String {
        BlaBlaTripIdentity.externalTripIdFromHref(candidate.href)?.takeIf(String::isNotBlank)?.let { return "id|$it" }
        return canonicalHref(candidate.href).trim().takeIf(String::isNotBlank)?.let { "href|$it" }.orEmpty()
    }''', 'advance')

once(
'''        private const val MAX_TRIPS = 80
        private const val MAX_TRIP_ROSTER_READ_ATTEMPTS = 5''',
'''        private const val MAX_RIDES_EMPTY_READ_ATTEMPTS = 3
        private const val REQUIRED_STABLE_BOTTOM_PASSES = 2
        private const val RIDES_SCROLL_SETTLE_MS = 750L
        private const val RIDES_BOTTOM_SETTLE_MS = 1200L
        private const val MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS = 3
        private const val MAX_TRIP_ROSTER_READ_ATTEMPTS = 5''', 'constants')

once(
'''                candidates: fromRoots.concat(fallback),
                bodyText: clean(document.body && document.body.innerText).slice(0, 16000),
                domHtml: html.slice(0, 350000)''',
'''                candidates: fromRoots.concat(fallback),
                bodyText: clean(document.body && document.body.innerText).slice(0, 16000),
                scrollY: Math.max(0, Math.round(window.scrollY || window.pageYOffset || 0)),
                scrollHeight: Math.max(0, Math.round(document.documentElement.scrollHeight || document.body.scrollHeight || 0)),
                viewportHeight: Math.max(0, Math.round(window.innerHeight || document.documentElement.clientHeight || 0)),
                atBottom: Math.ceil((window.scrollY || window.pageYOffset || 0) + (window.innerHeight || document.documentElement.clientHeight || 0)) >= Math.max(document.documentElement.scrollHeight || 0, document.body.scrollHeight || 0) - 8,
                domHtml: html.slice(0, 350000)''', 'scroll-js')

for marker in ['CARD_TRAVERSAL_START', 'CARD_TRAVERSAL_COMPLETE', 'CARD_TRAVERSAL_BLOCKED', 'RIDES_TRAVERSAL_SCROLL', 'fixedTripLimit=false', 'dateIgnored=true', 'batchShortcut=false']:
    if marker not in s:
        raise SystemExit(f'missing marker: {marker}')
if 'MAX_TRIPS' in s:
    raise SystemExit('MAX_TRIPS still present')
SOURCE.write_text(s)
