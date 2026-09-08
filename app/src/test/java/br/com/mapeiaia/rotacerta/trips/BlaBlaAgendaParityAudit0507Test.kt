package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.RotaCertaTenantIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class BlaBlaAgendaParityAudit0507Test {
    @Test
    fun confirmedEligibleTripsRequireExactlyOneVisibleAgendaCard() {
        val trip = BlaBlaAuditReconciledTrip(
            internalTripId = "canonical-1",
            title = "Origin → Destination",
            departureAt = "2030-01-01T10:00:00Z",
            status = TripStatus.FULL.name,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING.name,
            externalIdentity = BlaBlaAuditExternalIdentity(
                profileUuid = "11111111-1111-4111-8111-111111111111",
                tripId = "provider-trip",
                publicTripHref = "https://www.blablacar.com/trip?id=public-trip",
            ),
            publicationExisting = true,
            publicBookingEnabled = true,
            itineraryAuthoritative = true,
            stops = listOf(
                BlaBlaAuditTripStop("a", 0, "Origin"),
                BlaBlaAuditTripStop("b", 1, "Destination"),
            ),
            inventory = BlaBlaAuditInventory(4, 0, 4, 4, 0, 0, 0, 0, true),
            segmentAvailability = emptyList(),
            reconciliation = BlaBlaAuditReconciliation(
                publicCardTripId = "public-trip",
                matchedBy = listOf("PROFILE_UUID", "PUBLIC_TRIP_HREF"),
                state = "CONFIRMED_STRONG_IDENTITY",
            ),
            continuityPositionState = "ROUTE_ENDPOINTS_KNOWN",
        )
        val collection = BlaBlaAuditableCollectionSnapshot(
            generatedAt = "2030-01-01T00:00:00Z",
            collectorVersion = "test",
            tenant = BlaBlaAuditTenant("tenant"),
            period = BlaBlaAuditPeriod("2030-01-01", "2030-01-01"),
            route = BlaBlaAuditRoute(BlaBlaAuditPlace("Origin"), BlaBlaAuditPlace("Destination")),
            queries = emptyList(),
            publicCards = listOf(
                BlaBlaAuditPublicCard(
                    queryIds = emptyList(),
                    date = "2030-01-01",
                    direction = "OUTBOUND",
                    searchedOrigin = BlaBlaAuditPlace("Origin"),
                    searchedDestination = BlaBlaAuditPlace("Destination"),
                    tripId = "public-trip",
                    profileUuid = "11111111-1111-4111-8111-111111111111",
                    ownership = BlaBlaAuditOwnership("CONFIRMED", "11111111-1111-4111-8111-111111111111", listOf("PROFILE_UUID")),
                    identityKind = "CANONICAL_TRIP_ID",
                ),
            ),
            driverProfiles = emptyList(),
            reconciledTrips = listOf(trip),
            continuityEvidence = emptyList(),
            summary = BlaBlaAuditSummary(0,0,0,0,0,1,1,1,coverageComplete = true),
        )

        val missing = BlaBlaAgendaParityAudit0507.audit(collection, emptyList())
        assertEquals(1, missing.summary.missingFromAgenda)

        val visible = BlaBlaAgendaParityAudit0507.audit(collection, listOf("canonical-1"))
        assertEquals(0, visible.summary.missingFromAgenda)
        assertEquals(0, visible.summary.unexpectedAgendaTrips)
        assertEquals(0, visible.summary.duplicates)

        val duplicate = BlaBlaAgendaParityAudit0507.audit(collection, listOf("canonical-1", "canonical-1"))
        assertEquals(1, duplicate.summary.duplicates)
    }

    @Test
    fun explicitOfflineReasonIsExpectedAbsenceNotParityFailure() {
        val empty = BlaBlaAuditableCollectionSnapshot(
            generatedAt = "",
            collectorVersion = "",
            tenant = BlaBlaAuditTenant("tenant"),
            period = BlaBlaAuditPeriod("", ""),
            route = BlaBlaAuditRoute(BlaBlaAuditPlace(""), BlaBlaAuditPlace("")),
            queries = emptyList(),
            publicCards = emptyList(),
            driverProfiles = emptyList(),
            reconciledTrips = emptyList(),
            continuityEvidence = emptyList(),
            summary = BlaBlaAuditSummary(0,0,0,0,0,0,0,0,coverageComplete = false),
        )
        val result = BlaBlaAgendaParityAudit0507.audit(empty, emptyList(), mapOf("x" to "EXPECTED_ABSENCE_OFFLINE"))
        assertEquals(false, result.summary.coverageComplete)
        assertEquals(0, result.summary.missingFromAgenda)
    }
}
