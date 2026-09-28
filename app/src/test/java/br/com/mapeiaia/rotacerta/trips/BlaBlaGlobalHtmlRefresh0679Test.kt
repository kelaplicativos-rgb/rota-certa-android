package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaGlobalHtmlRefresh0679Test {
    private fun manifest(
        profileStatus: String = BlaBlaRidesSnapshotStatus0526.COMPLETE,
        tripStatus: String = BlaBlaRidesSnapshotStatus0526.COMPLETE,
        result: String = "COMPLETE",
    ) = BlaBlaRidesSnapshotManifest0526(
        captureId = "capture_0679",
        startedAt = "2026-09-28T12:00:00Z",
        result = result,
        profiles = listOf(
            BlaBlaRidesSnapshotProfile0526(
                accountKey = "account",
                status = profileStatus,
                tripCaptures0605 = listOf(
                    BlaBlaRidesTripCapture0605(
                        tripId = "trip-1",
                        status = tripStatus,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun hundredPercentRequiresAccountsCardsAndCanonicalFinalCommit0679() {
        assertTrue(evaluateGlobalHtmlRefresh0679(manifest(), 1, canonicalCommitted = true).complete)
        assertFalse(evaluateGlobalHtmlRefresh0679(manifest(), 2, canonicalCommitted = true).complete)
        assertFalse(evaluateGlobalHtmlRefresh0679(manifest(), 1, canonicalCommitted = false).complete)
        assertFalse(
            evaluateGlobalHtmlRefresh0679(
                manifest(profileStatus = BlaBlaRidesSnapshotStatus0526.INCOMPLETE),
                1,
                canonicalCommitted = true,
            ).complete,
        )
        assertFalse(
            evaluateGlobalHtmlRefresh0679(
                manifest(tripStatus = "INCOMPLETE"),
                1,
                canonicalCommitted = true,
            ).complete,
        )
        assertFalse(
            evaluateGlobalHtmlRefresh0679(
                manifest(result = "PARTIAL_SUCCESS"),
                1,
                canonicalCommitted = true,
            ).complete,
        )
    }

    @Test
    fun globalEntryPointsShareOnePersistentAuthoritativeCapture0679() {
        val worker = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaGlobalHtmlRefresh0679.kt",
        ).readText()
        val accountsUi = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAccountsBrowsersUi0399.kt",
        ).readText()
        val coordinator = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()

        assertTrue(worker.contains("BlaBlaRidesSnapshotCoordinator0526.captureAll("))
        assertTrue(worker.contains("OneTimeWorkRequestBuilder<BlaBlaGlobalHtmlRefreshWorker0679>()"))
        assertTrue(worker.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(worker.contains("setForeground(foregroundInfo0679("))
        assertTrue(worker.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(worker.contains("CANONICAL_FINAL_COMMIT_NOT_CONFIRMED"))
        assertTrue(worker.contains("MULTI_PROFILE_UNAVAILABLE"))
        assertTrue(accountsUi.contains("BlaBlaGlobalHtmlRefresh0679.enqueue("))
        assertFalse(accountsUi.contains("BlaBlaRidesSnapshotCoordinator0526.captureAll(context)"))
        assertTrue(coordinator.contains("onGlobalCommitResult0679: (Boolean) -> Unit = {}"))
        assertTrue(coordinator.contains("onGlobalCommitResult0679(committed)"))
    }

    @Test
    fun headerShowsRefreshProgressAndPersistentAttention0679() {
        val header = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt",
        ).readText()
        val activity = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt",
        ).readText()

        assertTrue(header.contains("Icons.Filled.Refresh"))
        assertTrue(header.contains("CircularProgressIndicator("))
        assertTrue(header.contains("text = \"⚠️?\""))
        assertTrue(header.contains("Atualizar todos os cards BlaBlaCar por HTML"))
        assertTrue(header.indexOf("onGlobalHtmlRefreshClick0679") < header.indexOf("Icons.Filled.Notifications"))
        assertTrue(activity.contains("BlaBlaGlobalHtmlRefresh0679.state(activity).collectAsState()"))
        assertTrue(activity.contains("source = \"agenda_header\""))
        assertTrue(activity.contains("globalHtmlRefreshState0679 = globalHtmlRefresh0679"))
        assertTrue(activity.contains("onGlobalHtmlRefreshClick0679 = requestGlobalHtmlRefresh0679"))
    }
}
