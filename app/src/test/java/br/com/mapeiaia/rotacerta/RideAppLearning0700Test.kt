package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideAppLearning0700Test {
    private fun profile(
        pkg: String = "br.com.regional.driver",
        destinationLabels: List<String> = listOf("Destino", "ENDERECO_DESTINO"),
        pickupLabels: List<String> = listOf("Origem"),
    ) = RideReaderProfile0700(
        packageName = pkg,
        versionName = "1.0",
        versionCode = 10,
        apkSha256 = "a".repeat(64),
        confidence = 0.93,
        destinationLabels = destinationLabels,
        pickupLabels = pickupLabels,
        resourceHints = listOf("res/layout/aceitar_corrida.xml"),
    )

    @Test
    fun learnedReaderNormalizesKnownAppCardLocally() {
        val result = LearnedRideReader0700.apply(
            profile(),
            "br.com.regional.driver",
            "Nova corrida\nOrigem\nRua A, 10\nDestino: Rua B, 20\nR$ 32,00",
        )
        assertTrue(result.applied)
        assertEquals("Rua A, 10", result.pickup)
        assertEquals("Rua B, 20", result.destination)
        assertTrue(result.text.contains("Origem: Rua A, 10"))
        assertTrue(result.text.contains("Destino: Rua B, 20"))
        assertTrue(result.text.contains(LearnedRideReader0700.APPLIED_MARKER))
    }

    @Test
    fun learnedReaderNeverCrossesPackageBoundary() {
        val result = LearnedRideReader0700.apply(
            profile(),
            "outro.app.driver",
            "Destino: Rua B, 20",
        )
        assertFalse(result.applied)
        assertEquals("Destino: Rua B, 20", result.text)
    }

    @Test
    fun analyzerKeepsRideSemanticEntriesAndRejectsNoise() {
        assertTrue(RideApkAnalyzer0700.isRelevantEntry("res/layout/corrida_taxista_detalhe.xml"))
        assertTrue(RideApkAnalyzer0700.isRelevantSemanticEvidence("ENDERECO_DESTINO"))
        assertTrue(RideApkAnalyzer0700.isRelevantSemanticEvidence("DISTANCIA_KM"))
        assertFalse(RideApkAnalyzer0700.isRelevantSemanticEvidence("androidx.compose.runtime"))
    }

    @Test
    fun sourceContractUsesOpenAiLearningWithoutExecutingImportedApk() {
        val root = File(System.getProperty("user.dir")).let { cwd ->
            if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        }
        val activity = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/RideAppLearningActivity0700.kt").readText()
        val analyzer = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/RideApkAnalyzer0700.kt").readText()
        val live = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val remote = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

        assertTrue(activity.contains("ActivityResultContracts.OpenDocument"))
        assertTrue(activity.contains("learnRideApp0700"))
        assertTrue(analyzer.contains(RideApkAnalyzer0700.NO_EXECUTION_MARKER))
        assertTrue(analyzer.contains(RideApkAnalyzer0700.DOSSIER_ONLY_MARKER))
        assertTrue(live.contains("LearnedRideReader0700.apply"))
        assertTrue(remote.contains("/v1/assistant/learn-ride-app"))
    }
}
