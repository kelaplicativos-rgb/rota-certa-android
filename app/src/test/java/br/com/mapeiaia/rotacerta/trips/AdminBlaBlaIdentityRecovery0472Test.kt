package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminBlaBlaIdentityRecovery0472Test {
    @Test
    fun pendingRecoveryRequiresExactlyOneStrongCollectorIdentity() {
        val assignment = DriverAdminIdentityAssignment0472(
            remoteTripId = "timeline-ext-58a651f70680c843b38f5778",
            expectedProfileUuid = "profile-a",
            candidateTripId = "01a058be-73c8-7845-9ad2-076aaef9883c",
            blablaManageUrl = "https://www.blablacar.com.br/rides/offer/edit/01a058be-73c8-7845-9ad2-076aaef9883c",
            requestRevision = 1,
        )
        val source = BlaBlaCollectorTrip(
            profile_uuid = "PROFILE-A",
            date = "2030-09-06",
            trip_id = assignment.candidateTripId,
        )
        assertEquals(source, adminBlaBlaIdentitySource0472(assignment, listOf(source)))
        assertNull(adminBlaBlaIdentitySource0472(assignment, listOf(source.copy(trip_id = "other"))))
        assertNull(adminBlaBlaIdentitySource0472(assignment, listOf(source.copy(identity_conflict = true))))
        assertNull(adminBlaBlaIdentitySource0472(assignment, listOf(source, source.copy(profile_name = "duplicate"))))
    }

    @Test
    fun recoveryUsesExactRemoteBindingAndPhysicalMatchOnlyAsGuard() {
        val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
        val start = background.indexOf("private suspend fun applyAdminBlaBlaIdentityRecoveries0472")
        val end = background.indexOf("private suspend fun runAdminPublicUrlDelta0465", start)
        assertTrue(start >= 0 && end > start)
        val scope = background.substring(start, end)
        assertTrue(scope.contains("publicExternalBinding(assignment.remoteTripId)"))
        assertTrue(scope.contains("canonicalProjectionPhysicalIdentityCompatible0421"))
        assertTrue(scope.contains("routeHeuristicUsed=false"))
        assertTrue(scope.contains("localApplied = false"))
        assertTrue(scope.contains("promoteExternalIdentity0472"))
        assertTrue(scope.contains("localApplied = true"))
        assertTrue(background.indexOf("applyAdminBlaBlaIdentityRecoveries0472(") <
            background.indexOf("val freshCanonical = reconcileCollectedExternalTrips0403("))
    }

    @Test
    fun editUrlParserRecognizesRealAdministrativeUrlShape() {
        val id = "01a058be-73c8-7845-9ad2-076aaef9883c"
        assertEquals(
            id,
            BlaBlaCollectorUrlModule.editTripId(
                "https://www.blablacar.com.br/rides/offer/edit/$id",
            ),
        )
    }
}
