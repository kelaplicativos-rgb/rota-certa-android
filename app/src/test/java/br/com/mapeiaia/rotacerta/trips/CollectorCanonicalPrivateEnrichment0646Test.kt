package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectorCanonicalPrivateEnrichment0646Test {
    private fun externalBooking(
        contact: String = "",
        fare: Long? = null,
        boardingAddress: String = "",
    ) = Booking(
        id = "booking-0646",
        tripId = "trip-0646",
        passengerName = "Passageiro",
        passengerContact = contact,
        boardingStopId = "from",
        dropoffStopId = "to",
        seats = 1,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.BLABLACAR,
        capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
        fareMinorUnits = fare,
        fareCurrencyCode = if (fare != null) "BRL" else "",
        boardingAddress = boardingAddress,
    )

    @Test
    fun missingOperationalPrivateFieldsRequireComplement0646() {
        assertTrue(
            CollectorPrivateEnrichment0646.needsPrivateEnrichment(
                listOf(externalBooking()),
            ),
        )
        assertTrue(
            CollectorPrivateEnrichment0646.needsPrivateEnrichment(
                listOf(externalBooking(contact = "+5511999999999", fare = 5_800L)),
            ),
        )
        assertFalse(
            CollectorPrivateEnrichment0646.needsPrivateEnrichment(
                listOf(
                    externalBooking(
                        contact = "+5511999999999",
                        fare = 5_800L,
                        boardingAddress = "Terminal Rodoviário do Tietê",
                    ),
                ),
            ),
        )
    }

    @Test
    fun privateStorePathCannotMutateTripRevisionOrOperationalFields0646() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt",
        ).readText()
        val start = source.indexOf("internal fun savePrivateBookingEnrichment0646(")
        val end = source.indexOf("internal fun saveBookingsBatch(", start)
        assertTrue(start >= 0 && end > start)
        val body = source.substring(start, end)

        assertTrue(body.contains("existing.copy("))
        assertTrue(body.contains("passengerContact = existing.passengerContact.ifBlank"))
        assertTrue(body.contains("fareMinorUnits = existing.fareMinorUnits"))
        assertTrue(body.contains("boardingAddress = existing.boardingAddress.ifBlank"))
        assertTrue(body.contains("dropoffAddress = existing.dropoffAddress.ifBlank"))
        assertFalse(body.contains("saveTrip("))
        assertFalse(body.contains("refreshCanonicalTripStateBatch0395("))
        assertFalse(body.contains("reconcileBookingDerivedInventory("))
        assertFalse(body.contains("deleteBooking("))
        assertFalse(body.contains("capacity ="))
        assertFalse(body.contains("status ="))
        assertTrue(body.contains("tripRevisionPreserved=true"))
        assertTrue(body.contains("publicMirrorPreserved=true"))
    }

    @Test
    fun collectorDeltaIsPrivateOnlyAndScopeAware0646() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()
        val start = source.indexOf("if (reason == \"blablacar_collection_result\")")
        val end = source.indexOf("val targetRemoteTripId", start)
        assertTrue(start >= 0 && end > start)
        val body = source.substring(start, end)

        assertTrue(body.contains("CollectorPrivateEnrichment0646.enrich("))
        assertTrue(body.contains("allowedDates = collectorPrivateScope0646.dates"))
        assertTrue(body.contains("allowedProfileUuid = collectorPrivateScope0646.profileUuid"))
        assertTrue(body.contains("allowedTripId = collectorPrivateScope0646.tripId"))
        assertTrue(body.contains("tripWrite=false tombstone=false siblingWrite=false"))
        assertTrue(body.contains("htmlTripAuthorityPreserved=true"))
        assertFalse(body.contains("reconcileCollectedExternalTrips0403("))
        assertFalse(body.contains("saveTrip("))
    }

    @Test
    fun exactCardHtmlPaintsFirstThenComplementsOnlyItsPassengers0646() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()
        val start = source.indexOf("internal suspend fun refreshCanonicalTripFromCollector0517(")
        val end = source.indexOf("fun enqueueRecoveryIfNeeded", start)
        assertTrue(start >= 0 && end > start)
        val body = source.substring(start, end)

        val localCommit = body.indexOf("TargetedTripRefreshEvents0645.notifyCanonicalCommitted(")
        val publicDrain = body.indexOf("TripMutationCoordinator0387(appContext, store).drainPending(")
        val privateCheck = body.indexOf("CollectorPrivateEnrichment0646.needsPrivateEnrichment(")
        val headless = body.indexOf("reverifyTripHeadless0407(")
        assertTrue(localCommit >= 0)
        assertTrue(publicDrain > localCommit)
        assertTrue(privateCheck > publicDrain)
        assertTrue(headless > privateCheck)
        assertTrue(body.contains("enabledScripts = CollectorPrivateEnrichment0646.readOnlyPassengerScripts"))
        assertTrue(body.contains("exactSessionCollectorSource0646()"))
        assertTrue(body.contains("target_card_private_enrichment_0646"))
        assertTrue(body.contains("targeted_html_canonicalized_private_enrichment_complete"))
    }

    @Test
    fun dateAndExactCollectorCheckpointsCarryTheirOwnScope0646() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt",
        ).readText()

        assertTrue(source.contains("profileUuid0646 = account.profileUuid.orEmpty()"))
        assertTrue(source.contains("tripId0646 = targetTripId"))
        assertTrue(source.contains("dates0646 = targetDates"))
    }

    @Test
    fun enrichmentScriptSetIsReadOnlyPassengerFocused0646() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/CollectorPrivateEnrichment0646.kt",
        ).readText()

        assertTrue(source.contains("BlaBlaBrowserRequest.PASSENGER_CONTACT"))
        assertTrue(source.contains("BlaBlaBrowserRequest.PASSENGER_FARE"))
        assertTrue(source.contains("BlaBlaBrowserRequest.PASSENGER_ADDRESSES"))
        assertFalse(source.contains("BlaBlaBrowserRequest.SEAT_CHANGE"))
        assertFalse(source.contains("BlaBlaBrowserRequest.SEAT_SAVE"))
        assertFalse(source.contains("BlaBlaBrowserRequest.BOOST_SET_STATE"))
        assertFalse(source.contains("BlaBlaBrowserRequest.BOOST_SAVE"))
    }
}
