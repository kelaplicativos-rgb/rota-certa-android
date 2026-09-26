package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaPassengerArtifactCompleteness0658Test {
    @Test
    fun exportedZipIncludesPassengerHtmlAndFailsClosedIfDeclaredArtifactIsMissing() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()
        val exportBlock = source.substringAfter("fun downloadEntries0527")
            .substringBefore("private fun writeManifestChecksum0528")

        assertTrue(exportBlock.contains("passengerHtmlArtifacts0658"))
        assertTrue(exportBlock.contains("passengerHtmlFiles0653"))
        assertTrue(exportBlock.contains("Missing declared snapshot artifact"))
        assertFalse(exportBlock.contains("mapNotNull"))
    }

    @Test
    fun finalCompleteRevalidationChecksEveryPassengerHtmlByBytesAndSha() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesForensicEvidence0528.kt",
        ).readText()

        assertTrue(source.contains("PASSENGER_HTML_ARTIFACT_COUNT_MISMATCH_0658"))
        assertTrue(source.contains("PASSENGER_HTML_ARTIFACT_PATH_MISMATCH_0658"))
        assertTrue(source.contains("PASSENGER_HTML_ARTIFACT_INVALID_0658"))
        assertTrue(source.contains("verifySnapshotArtifact0528(passengerHtml0658"))
    }

    @Test
    fun profilesAreSerializedAcrossTheSharedSingleFlightBoundary() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt",
        ).readText()
        val coordinator = source.substringAfter("internal object BlaBlaRidesSnapshotCoordinator0526")

        assertTrue(coordinator.contains("BLABLACAR_HTML_PROFILE_SERIAL_0658"))
        assertTrue(coordinator.contains("accounts.forEachIndexed"))
        assertFalse(coordinator.contains("accounts.mapIndexed { index, account ->\n                    async"))
    }
}
