package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineProfile0536Test {
    private val accounts = listOf(
        BlaBlaDynamicAccount(
            id = "account-a",
            label = "Conta operacional A",
            webProfileName = "web-a",
            profileUuid = "uuid-a",
            profileName = "Motorista Alfa",
        ),
        BlaBlaDynamicAccount(
            id = "account-b",
            label = "Conta operacional B",
            webProfileName = "web-b",
            profileUuid = "uuid-b",
            profileName = "Motorista Beta",
        ),
    )

    @Test
    fun canonicalTripProfileIdsRemainDistinct() {
        assertEquals("uuid-a", canonicalTimelineProfileId0536(" UUID-A ", "local"))
        assertEquals("uuid-b", canonicalTimelineProfileId0536("uuid-b", "local"))
        assertFalse(
            canonicalTimelineProfileId0536("uuid-a", "local") ==
                canonicalTimelineProfileId0536("uuid-b", "local"),
        )
    }

    @Test
    fun driverDisplayNameUsesOnlyMatchingPersistentIdentity() {
        assertEquals("Motorista Alfa", timelineDriverProfileLabel0536("UUID-A", "account-b", accounts))
        assertEquals("Motorista Beta", timelineDriverProfileLabel0536("uuid-b", "account-a", accounts))
    }

    @Test
    fun unresolvedCanonicalUuidNeverBorrowsAnotherDriver() {
        assertEquals("Perfil BlaBlaCar", timelineDriverProfileLabel0536("uuid-missing", "account-a", accounts))
    }

    @Test
    fun stableTripIdKeepsExplicitExpansionAcrossEntryRefreshes() {
        val expanded = setOf(timelineExpansionKey0536(" trip-canonical-1 "))
        assertTrue(timelineExpansionKey0536("trip-canonical-1") in expanded)
        assertFalse(timelineExpansionKey0536("trip-canonical-2") in expanded)
    }

    @Test
    fun durationIsRenderedFromCanonicalTimes() {
        assertEquals("2h05", timelineDurationLabel0536(1_000L, 1_000L + 125L * 60_000L))
        assertEquals("—", timelineDurationLabel0536(1_000L, null))
    }
}
