package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideAppLearningV20704Test {
    private fun profile() = RideReaderProfile0700(
        packageName = "com.app99.driver",
        versionName = "test",
        versionCode = 704,
        apkSha256 = "7".repeat(64),
        profileVersion = 2,
        confidence = 0.94,
        rideAnchors = listOf("Nova corrida"),
        actionLabels = listOf("Aceitar"),
        resourceHints = listOf("res/layout/ride_card.xml"),
    )

    @Test
    fun twoAddressCardCanAuthorizeDestinationOnlyWithStrongRideEvidence() {
        val result = LearnedRideReader0700.apply(
            profile(),
            "com.app99.driver",
            "Nova corrida\nRua Afonso Pena, 100\nRua Jacinto Valedor, 41\nR$ 18,90\n3 min\nAceitar",
        )
        assertTrue(result.applied)
        assertTrue(result.destination.orEmpty().contains("Jacinto"))
        assertTrue(result.text.contains(LearnedRideReader0700.TWO_ADDRESS_MARKER_0704))
    }

    @Test
    fun twoAddressesWithoutRideActionRemainFailClosed() {
        val result = LearnedRideReader0700.apply(
            profile().copy(rideAnchors = emptyList(), actionLabels = emptyList(), resourceHints = emptyList()),
            "com.app99.driver",
            "Rua Afonso Pena, 100\nRua Jacinto Valedor, 41",
        )
        assertFalse(result.applied)
    }

    @Test
    fun realCardCaptureEnrichesReaderWithoutOwningColorDecision() {
        val capture = ManualAppScreenCapture(
            id = "card-1",
            packageName = "com.app99.driver",
            textPreview = "Nova corrida\nOrigem\nRua Afonso Pena, 100\nDestino\nRua Jacinto Valedor, 41\nR$ 18,90\nAceitar",
            imagePath = null,
            createdAtMillis = 123L,
        )
        val enriched = RuntimeRideCardEvidence0704.enrich(
            packageName = "com.app99.driver",
            stored = profile().copy(destinationLabels = emptyList(), pickupLabels = emptyList()),
            captures = listOf(capture),
        )
        assertNotNull(enriched)
        assertTrue(enriched!!.destinationLabels.any { it.contains("Destino", ignoreCase = true) })
        assertTrue(enriched.pickupLabels.any { it.contains("Origem", ignoreCase = true) })
        assertTrue(enriched.resourceHints.contains(RuntimeRideCardEvidence0704.CONTRACT_MARKER))
    }

    @Test
    fun rankedEvidencePrefersAppSpecificDestinationOverGenericLibraryNoise() {
        val specific = RideApkAnalyzer0700.scoreSemanticEvidence0704(
            "sinet.startup.inDriver.driver.order.destination_address",
            "sinet.startup.inDriver",
        )
        val generic = RideApkAnalyzer0700.scoreSemanticEvidence0704(
            "androidx.compose.runtime.distance",
            "sinet.startup.inDriver",
        )
        assertTrue(specific > generic)
    }

    @Test
    fun textAssetsExposeLocalizedRideSemantics() {
        val values = RideApkAnalyzer0700.extractTextAssetStrings0704(
            """{"destination":"Destino","accept":"Aceitar corrida","noise":"hello"}""".toByteArray(),
        ).toList()
        assertTrue(values.any { it.contains("Destino") })
        assertTrue(values.any { it.contains("Aceitar") })
    }

    @Test
    fun sourceContractScansSplitsAndRanksGloballyInsteadOfFirstComeFirstServed() {
        val root = File(System.getProperty("user.dir")).let { cwd ->
            if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        }
        val analyzer = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/RideApkAnalyzer0700.kt").readText()
        val reader = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/RideAppLearning0700.kt").readText()

        assertTrue(analyzer.contains("splitSourceDirs"))
        assertTrue(analyzer.contains(RideApkAnalyzer0700.V2_MARKER))
        assertTrue(analyzer.contains(RideApkAnalyzer0700.TEXT_ASSET_MARKER))
        assertTrue(analyzer.contains("EvidenceRanker0704"))
        assertTrue(analyzer.contains("MAX_SINGLE_ARCHIVE_BYTES"))
        assertFalse(analyzer.contains("MAX_APK_BYTES = 250L"))
        assertFalse(analyzer.contains("MAX_DEX_BYTES_TOTAL"))
        assertTrue(reader.contains(RuntimeRideCardEvidence0704.CONTRACT_MARKER))
        assertTrue(reader.contains(LearnedRideReader0700.TWO_ADDRESS_MARKER_0704))
    }
}
