package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolOfflineAi0642Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test
    fun local_semantic_classifier_prefers_last_destination_and_recognizes_ride() {
        val structured = OcrStructuredText0188(
            text = """
                Pedido de corrida
                R$ 42
                18 km
                Aceitar
                Rua das Flores, 120
                Rua Boa Vista, 31 - São Paulo - SP
            """.trimIndent(),
            blocks = listOf(
                OcrTextBlock0188("b0", "Pedido de corrida R$ 42 18 km Aceitar", 40, 100, 1000, 350),
                OcrTextBlock0188("b1", "Embarque Rua das Flores, 120", 60, 900, 1000, 1040),
                OcrTextBlock0188("b2", "Destino Rua Boa Vista, 31 - São Paulo - SP", 60, 1320, 1000, 1500),
            ),
        )
        val result = FarolOfflineAiStage642.recognizeFromEvidence(
            structured = structured,
            screenHeight = 2340,
            visualSimilarity = null,
        )
        assertTrue(result.recognizedRideCard)
        assertTrue(result.confidence >= 60)
        assertEquals("Rua Boa Vista, 31 - São Paulo - SP", result.destination?.address)
        assertEquals("Rua das Flores, 120", result.pickup?.address)
        assertTrue(result.rideAnchorCount >= 3)
    }

    @Test
    fun explicit_destination_marker_has_semantic_weight() {
        val structured = OcrStructuredText0188(
            text = "R$ 25\nAceitar\nDestino: Avenida Mateo Bei, 1800 - São Paulo - SP",
            blocks = listOf(
                OcrTextBlock0188("d", "Destino: Avenida Mateo Bei, 1800 - São Paulo - SP", 30, 700, 1000, 820),
                OcrTextBlock0188("noise", "Rua Qualquer, 99", 30, 1600, 1000, 1720),
            ),
        )
        val result = FarolOfflineAiStage642.recognizeFromEvidence(structured, 2340, null)
        assertTrue(result.recognizedRideCard)
        assertEquals("Avenida Mateo Bei, 1800 - São Paulo - SP", result.destination?.address)
        assertTrue(result.destination?.explicitDestination == true)
    }

    @Test
    fun ordinary_screen_with_address_but_without_ride_or_visual_evidence_is_rejected() {
        val structured = OcrStructuredText0188(
            text = "Endereço cadastrado\nRua das Acácias, 500",
            blocks = listOf(OcrTextBlock0188("a", "Rua das Acácias, 500", 10, 100, 900, 220)),
        )
        val result = FarolOfflineAiStage642.recognizeFromEvidence(structured, 2340, null)
        assertFalse(result.recognizedRideCard)
        assertNotNull(result.destination)
    }

    @Test
    fun trained_visual_similarity_can_recover_transient_text_without_two_anchors() {
        val structured = OcrStructuredText0188(
            text = "Rua Revoadas, 58\nAceitar",
            blocks = listOf(OcrTextBlock0188("a", "Rua Revoadas, 58", 20, 1200, 900, 1340)),
        )
        val result = FarolOfflineAiStage642.recognizeFromEvidence(structured, 2340, 0.84)
        assertTrue(result.recognizedRideCard)
        assertEquals("visual_local_match", result.reason)
    }

    @Test
    fun route_augmentation_is_grounded_in_original_candidate_bounds() {
        val structured = OcrStructuredText0188(
            text = "R$ 35\nAceitar\nRua Afonso, 10\nRua Bertioga, 90",
            blocks = listOf(
                OcrTextBlock0188("p", "Rua Afonso, 10", 100, 700, 900, 820),
                OcrTextBlock0188("d", "Rua Bertioga, 90", 100, 1200, 900, 1320),
            ),
        )
        val recognition = FarolOfflineAiStage642.recognizeFromEvidence(structured, 2340, 0.80)
        val augmented = FarolOfflineAiStage642.augmentForRoute(structured, recognition)
        assertTrue(augmented.text.contains("Destino: Rua Bertioga, 90"))
        val destination = augmented.blocks.first { it.id == "offline-ai-642-destination" }
        assertEquals(1200, destination.top)
        assertEquals(1320, destination.bottom)
    }

    @Test
    fun hamming_similarity_primitive_is_deterministic() {
        assertEquals(0, FarolOfflineAiStage642.hammingHex64("0000000000000000", "0000000000000000"))
        assertEquals(64, FarolOfflineAiStage642.hammingHex64("0000000000000000", "ffffffffffffffff"))
    }

    @Test
    fun service_runs_offline_ai_after_structural_signature_miss_and_before_giving_up() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun admitTrainedCardStage640")
        val end = live.indexOf("private fun masterResetCardAdmissionStage639", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("scheduleOfflineAiAdmission642(packageName639"))
        assertTrue(live.contains("S642_OFFLINE_AI_CARD_ADMITTED"))
        assertTrue(live.contains("ocrService.extractStructuredText(localBitmap642)"))
        assertTrue(live.contains("FarolOfflineAiStage642.augmentForRoute"))
        assertTrue(live.contains("googleMapsService.resolveFarolCoordinateInstant642("))
    }

    @Test
    fun offline_ai_stage_has_no_remote_or_openai_dependency() {
        val ai = src("FarolOfflineAiStage642.kt")
        assertTrue(ai.contains("NO_OPENAI_NO_REMOTE_AI_STAGE642"))
        assertFalse(ai.contains("HttpURLConnection"))
        assertFalse(ai.contains("java.net.URL"))
        assertFalse(ai.contains("api.openai.com"))
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("com.google.mlkit:text-recognition:16.0.1"))
        assertFalse(gradle.contains("play-services-mlkit-text-recognition"))
    }

    @Test
    fun learned_atlas_is_local_hashed_and_non_expiring() {
        val atlas = src("OfflineAddressAtlas642.kt")
        assertTrue(atlas.contains("ATLAS_LOOKUP_ZERO_NETWORK_STAGE642"))
        assertTrue(atlas.contains("MessageDigest.getInstance(\"SHA-256\")"))
        assertFalse(atlas.contains("HttpURLConnection"))
        assertFalse(atlas.contains("URL("))
        assertFalse(atlas.contains("isExpired("))
        val maps = src("GoogleMapsService.kt")
        assertTrue(maps.contains("OFFLINE_ATLAS_HIT_0642"))
        assertTrue(maps.contains("OFFLINE_ATLAS_LEARNED_0642"))
        assertTrue(maps.contains("resolveFarolCoordinateInstant642"))
    }

    @Test
    fun release_metadata_is_0642_5933() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("releaseVersionName = \"0.1.642\""))
        assertTrue(gradle.contains("releaseVersionCode = 5_933"))
        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.642\""))
        assertTrue(history.contains("\"build\": 5933"))
        assertTrue(history.contains("offline"))
        assertTrue(history.contains("OpenAI"))
    }
}
