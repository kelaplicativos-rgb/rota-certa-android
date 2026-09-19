package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class BlaBlaPublicLinkCompleteness0585Test {
    private fun trip(
        tripId: String = "AdminTrip0585ABC123",
        publicUrl: String? = "https://www.blablacar.com.br/trip?id=AdminTrip0585ABC123",
        binding: String = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID,
    ) = BlaBlaCollectorTrip(
        profile_uuid = "profile-0585",
        profile_name = "Motorista",
        date = "2026-09-27",
        departure_time = "10:30",
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_href = "https://www.blablacar.com.br/rides/offer?id=$tripId",
        public_trip_href = publicUrl,
        public_trip_href_source = "test",
        public_trip_href_binding = binding,
        trip_id = tripId,
    )

    @Test
    fun requestedPublicUrlMarksAcceptedTripWithoutPermalinkIncomplete() {
        val missing = collectorMissingPublicLinks0585(
            trips = listOf(trip(publicUrl = null)),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(1, missing)
    }

    @Test
    fun validatedSameIdPermalinkIsComplete() {
        val missing = collectorMissingPublicLinks0585(
            trips = listOf(trip()),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(0, missing)
    }

    @Test
    fun authoritativePublicTokenMayDifferFromAdministrativeTripId() {
        val missing = collectorMissingPublicLinks0585(
            trips = listOf(
                trip(
                    publicUrl = "https://www.blablacar.com.br/trip?id=PassengerToken0585XYZ789",
                    binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
                ),
            ),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(0, missing)
    }

    @Test
    fun untrustedOrMissingIdentityNeverCountsAsComplete() {
        val missing = collectorMissingPublicLinks0585(
            trips = listOf(
                trip(publicUrl = "https://example.com/trip?id=AdminTrip0585ABC123"),
                trip(tripId = "", publicUrl = null),
            ),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
        )

        assertEquals(2, missing)
    }

    @Test
    fun persistedValidatedPermalinkPreventsFalsePartialWhenFreshCaptureMissesIt() {
        val missing = collectorMissingPublicLinks0585(
            trips = listOf(trip(publicUrl = null)),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
            persistedTrips = listOf(
                trip(
                    publicUrl = "https://www.blablacar.com.br/trip?id=PassengerTokenPersisted0585",
                    binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
                ),
            ),
        )

        assertEquals(0, missing)
    }

    @Test
    fun persistedPermalinkCannotCrossProfileOrTripIdentity() {
        val fresh = trip(publicUrl = null)
        val wrongProfile = trip(
            publicUrl = "https://www.blablacar.com.br/trip?id=PassengerTokenWrongProfile0585",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        ).copy(profile_uuid = "different-profile-0585")
        val wrongTrip = trip(
            tripId = "DifferentAdminTrip0585",
            publicUrl = "https://www.blablacar.com.br/trip?id=PassengerTokenWrongTrip0585",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        )

        val missing = collectorMissingPublicLinks0585(
            trips = listOf(fresh),
            selection = BlaBlaDateScopeScriptSelection0449.legacyAll(),
            persistedTrips = listOf(wrongProfile, wrongTrip),
        )

        assertEquals(1, missing)
    }

    @Test
    fun selectiveRunThatDidNotRequestPublicUrlDoesNotDegradeExistingTripState() {
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(
            BlaBlaDateScopeScriptCatalog0449.all - BlaBlaDateScopeScriptCatalog0449.publicUrlRequests,
        )

        val missing = collectorMissingPublicLinks0585(
            trips = listOf(trip(publicUrl = null)),
            selection = selection,
        )

        assertEquals(0, missing)
    }
}
