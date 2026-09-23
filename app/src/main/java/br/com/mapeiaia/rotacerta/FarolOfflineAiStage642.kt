package br.com.mapeiaia.rotacerta

import android.graphics.Bitmap
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * Stage642 — inteligência visual/semântica 100% local para recuperar destino quando a árvore de
 * Accessibility não entrega texto suficiente.
 *
 * A rede neural usada para ler a imagem é o ML Kit Text Recognition já embarcado no APK pelo
 * OcrService. Este estágio nunca chama OpenAI, Google Maps, Nominatim ou qualquer serviço remoto:
 * recebe somente o bitmap e o resultado OCR local, identifica evidência de card de corrida e
 * escolhe o destino final com geometria + semântica.
 */
object FarolOfflineAiStage642 {
    const val CONTRACT_MARKER = "FAROL_OFFLINE_AI_STAGE642"
    const val NO_OPENAI_MARKER = "NO_OPENAI_NO_REMOTE_AI_STAGE642"
    const val LOCAL_NEURAL_OCR_MARKER = "BUNDLED_MLKIT_NEURAL_OCR_STAGE642"
    const val VISUAL_RECOVERY_MARKER = "LOCAL_SEMANTIC_OCR_RECOVERY_STAGE642"
    const val LAST_DESTINATION_MARKER = "LAST_ADDRESS_LOCAL_SEMANTIC_CLASSIFIER_STAGE642"

    data class Candidate(
        val address: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Double,
        val explicitDestination: Boolean,
    )

    data class Recognition(
        val recognizedRideCard: Boolean,
        val confidence: Int,
        val destination: Candidate?,
        val pickup: Candidate?,
        val addressCount: Int,
        val rideAnchorCount: Int,
        val visualSimilarity: Double?,
        val reason: String,
    )

    private val rideAnchorPatterns = listOf(
        Regex("\\baceitar\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:ofere[cç]a|oferta|corrida|viagem|pedido)\\b", RegexOption.IGNORE_CASE),
        Regex("R\\$\\s*\\d", RegexOption.IGNORE_CASE),
        Regex("\\b\\d+(?:[.,]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{1,3}\\s*(?:min|minutos?)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b[1-5][.,]\\d\\b"),
    )
    private val destinationMarker = Regex(
        "\\b(?:destino(?:\\s+final)?|para\\s+onde|chegada|desembarque|ponto\\s+de\\s+chegada)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val pickupMarker = Regex(
        "\\b(?:origem|embarque|de\\s+onde|partida|ponto\\s+de\\s+partida)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun recognize(
        bitmap: Bitmap,
        structured: OcrStructuredText0188,
    ): Recognition = recognizeFromEvidence(
        structured = structured,
        screenHeight = bitmap.height,
    )

    internal fun recognizeFromEvidence(
        structured: OcrStructuredText0188,
        screenHeight: Int,
    ): Recognition {
        val anchors = rideAnchorPatterns.count { it.containsMatchIn(structured.text) }
        val candidates = collectCandidates(structured, screenHeight)
        val destination = candidates.maxWithOrNull(
            compareBy<Candidate> { it.score }
                .thenBy { it.bottom }
                .thenBy { it.top },
        )
        val pickup = candidates
            .sortedWith(compareBy<Candidate> { it.top }.thenBy { it.left })
            .firstOrNull { candidate ->
                destination == null || canonical(candidate.address) != canonical(destination.address)
            }

        val semanticStrong = destination != null && (
            anchors >= 2 ||
                (anchors >= 1 && candidates.size >= 2) ||
                (destination.explicitDestination && anchors >= 1)
            )
        val recognized = semanticStrong

        val confidence = (
            35 +
                anchors * 9 +
                candidates.size.coerceAtMost(3) * 7 +
                if (destination?.explicitDestination == true) 12 else 0
            ).coerceIn(0, 99)

        return Recognition(
            recognizedRideCard = recognized,
            confidence = confidence,
            destination = destination,
            pickup = pickup,
            addressCount = candidates.size,
            rideAnchorCount = anchors,
            visualSimilarity = null,
            reason = when {
                destination == null -> "no_local_destination"
                recognized -> "semantic_local_match"
                else -> "insufficient_local_card_evidence"
            },
        )
    }

    fun augmentForRoute(
        structured: OcrStructuredText0188,
        recognition: Recognition,
    ): OcrStructuredText0188 {
        if (!recognition.recognizedRideCard || recognition.destination == null) return structured
        val additions = ArrayList<OcrTextBlock0188>(2)
        recognition.pickup?.let { candidate ->
            additions += OcrTextBlock0188(
                id = "offline-ai-642-pickup",
                text = "Embarque: ${candidate.address}",
                left = candidate.left,
                top = candidate.top,
                right = candidate.right,
                bottom = candidate.bottom,
            )
        }
        recognition.destination.let { candidate ->
            additions += OcrTextBlock0188(
                id = "offline-ai-642-destination",
                text = "Destino: ${candidate.address}",
                left = candidate.left,
                top = candidate.top,
                right = candidate.right,
                bottom = candidate.bottom,
            )
        }
        val appendedText = buildString {
            append(structured.text)
            recognition.pickup?.let { append("\nEmbarque: ").append(it.address) }
            append("\nDestino: ").append(recognition.destination.address)
        }
        return OcrStructuredText0188(
            text = appendedText,
            blocks = (structured.blocks + additions).take(96),
        )
    }

    private fun collectCandidates(
        structured: OcrStructuredText0188,
        screenHeight: Int,
    ): List<Candidate> {
        val output = ArrayList<Candidate>()
        val safeHeight = max(screenHeight, 1)
        structured.blocks.forEachIndexed { index, block ->
            val addresses = UniversalScreenAddressParser.findAddresses(block.text)
            addresses.forEach { address ->
                val normalizedBottom = block.bottom.coerceAtLeast(block.top).toDouble() / safeHeight.toDouble()
                var score = normalizedBottom.coerceIn(0.0, 1.5) * 24.0
                if (destinationMarker.containsMatchIn(block.text)) score += 32.0
                if (pickupMarker.containsMatchIn(block.text)) score -= 8.0
                if (UniversalScreenAddressParser.isCompleteNumberedAddress(address)) score += 9.0
                score += index.coerceAtMost(30) * 0.35
                output += Candidate(
                    address = address,
                    left = block.left,
                    top = block.top,
                    right = block.right,
                    bottom = block.bottom,
                    score = score,
                    explicitDestination = destinationMarker.containsMatchIn(block.text),
                )
            }
        }

        // ML Kit may return one large block. Preserve the parser's reading order as a local-only
        // fallback and deliberately prefer the LAST recognized address.
        if (output.isEmpty()) {
            UniversalScreenAddressParser.findAddresses(structured.text).forEachIndexed { index, address ->
                output += Candidate(
                    address = address,
                    left = 0,
                    top = index,
                    right = 0,
                    bottom = index,
                    score = index * 12.0,
                    explicitDestination = false,
                )
            }
        }

        return output
            .groupBy { canonical(it.address) }
            .mapNotNull { (_, same) -> same.maxByOrNull(Candidate::score) }
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
