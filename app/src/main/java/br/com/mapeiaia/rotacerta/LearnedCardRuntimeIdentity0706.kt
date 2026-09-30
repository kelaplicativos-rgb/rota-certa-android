package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * 0.1.706 — identidade runtime de card para aplicativos aprendidos.
 *
 * O Reader V2 precisa conseguir dizer "há um card deste aplicativo na tela" antes de
 * conseguir extrair o destino. Esta classe NÃO autoriza rota, cor, raio ou quilometragem:
 * ela apenas mantém o pipeline local vivo e permite que Accessibility/OCR completem a leitura.
 */
object LearnedCardRuntimeIdentity0706 {
    const val CONTRACT_MARKER = "LEARNED_CARD_RUNTIME_IDENTITY_0706"
    const val MATCHED_MARKER = "CARD_PROFILE_MATCHED_0706"
    const val OCR_PENDING_MARKER = "LEARNED_CARD_OCR_PENDING_0706"
    const val PARTIAL_NO_CLEAR_MARKER = "LEARNED_CARD_PARTIAL_NO_CLEAR_0706"
    const val PAID_AI_BYPASS_MARKER = "LEARNED_CARD_PAID_AI_BYPASS_0706"

    enum class Outcome {
        MATCHED,
        POSSIBLE,
        NO_MATCH,
    }

    data class Decision(
        val outcome: Outcome,
        val score: Int,
        val addressCount: Int,
        val learnedCueMatches: Int,
        val actionVisible: Boolean,
        val fareVisible: Boolean,
        val metricVisible: Boolean,
        val rideCueVisible: Boolean,
        val reason: String,
    ) {
        val matched: Boolean get() = outcome == Outcome.MATCHED
        val possible: Boolean get() = outcome != Outcome.NO_MATCH
    }

    fun evaluate(
        profile: RideReaderProfile0700?,
        packageName: String?,
        rawText: String,
    ): Decision {
        if (profile == null || !profile.usable || profile.profileVersion < 2) {
            return noMatch("profile_v2_unavailable")
        }
        val expected = normalize(profile.packageName)
        val actual = normalize(packageName)
        if (expected == null || actual == null || expected != actual) {
            return noMatch("package_mismatch")
        }
        if (rawText.isBlank()) return noMatch("empty_runtime_text")

        val canonicalText = LearnedRideReader0700.canonical(rawText)
        if (canonicalText.isBlank()) return noMatch("empty_canonical_text")

        val learnedCues = (
            profile.rideAnchors +
                profile.actionLabels +
                profile.fareLabels +
                profile.distanceLabels +
                profile.destinationLabels +
                profile.pickupLabels
            )
            .asSequence()
            .map(LearnedRideReader0700::canonical)
            .filter { it.length in 3..96 }
            .distinct()
            .take(160)
            .toList()

        val learnedCueMatches = learnedCues.count(canonicalText::contains).coerceAtMost(8)
        val actionVisible = ACTION_REGEX.containsMatchIn(rawText)
        val fareVisible = FARE_REGEX.containsMatchIn(rawText)
        val metricVisible = METRIC_REGEX.containsMatchIn(rawText)
        val rideCueVisible = RIDE_CUES.any(canonicalText::contains)
        val navigationNoise = NAVIGATION_NOISE.any(canonicalText::contains)
        val addresses = UniversalScreenAddressParser.findAddresses(
            WrappedAddressTextNormalizer.normalize(rawText),
        ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(LearnedRideReader0700::canonical)
        val addressCount = addresses.size

        // Uma tela de mapa/pesquisa não vira card apenas por pertencer ao package aprendido.
        if (navigationNoise && !actionVisible && !rideCueVisible && learnedCueMatches == 0) {
            return Decision(
                Outcome.NO_MATCH, 0, addressCount, learnedCueMatches,
                actionVisible, fareVisible, metricVisible, rideCueVisible,
                "navigation_surface_without_card_semantics",
            )
        }

        var score = 0
        if (learnedCueMatches > 0) score += minOf(4, learnedCueMatches + 1)
        if (actionVisible) score += 3
        if (fareVisible) score += 2
        if (metricVisible) score += 1
        if (rideCueVisible) score += 2
        if (addressCount == 1) score += 1
        if (addressCount == 2) score += 2
        if (addressCount >= 3) score -= 4

        // Três formas seguras de admitir a presença sem autorizar a rota:
        // 1) ação + preço; 2) endereço + preço + métrica; 3) tipo/âncora de corrida + preço + métrica.
        val strongRuntimeCard =
            (actionVisible && fareVisible) ||
                (addressCount in 1..2 && fareVisible && metricVisible) ||
                (rideCueVisible && fareVisible && metricVisible)
        val learnedRuntimeCard = learnedCueMatches > 0 &&
            (actionVisible || fareVisible || metricVisible || addressCount in 1..2)

        val matched = addressCount < 3 && score >= 5 && (strongRuntimeCard || learnedRuntimeCard)
        if (matched) {
            return Decision(
                Outcome.MATCHED, score, addressCount, learnedCueMatches,
                actionVisible, fareVisible, metricVisible, rideCueVisible,
                "learned_runtime_card_confirmed",
            )
        }

        val possible = score >= 3 && (
            learnedCueMatches > 0 ||
                actionVisible ||
                (rideCueVisible && (fareVisible || metricVisible))
            )
        return Decision(
            if (possible) Outcome.POSSIBLE else Outcome.NO_MATCH,
            score,
            addressCount,
            learnedCueMatches,
            actionVisible,
            fareVisible,
            metricVisible,
            rideCueVisible,
            if (possible) "partial_learned_card_evidence" else "insufficient_card_evidence",
        )
    }

    private fun noMatch(reason: String) = Decision(
        outcome = Outcome.NO_MATCH,
        score = 0,
        addressCount = 0,
        learnedCueMatches = 0,
        actionVisible = false,
        fareVisible = false,
        metricVisible = false,
        rideCueVisible = false,
        reason = reason,
    )

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)

    private val ACTION_REGEX = Regex(
        "(?iu)\\b(?:aceitar|accept|recusar|reject|rejeitar|ofere(?:ç|c)a|offer|confirmar|buscar passageiro)\\b",
    )
    private val FARE_REGEX = Regex("(?iu)R\\$\\s*\\d")
    private val METRIC_REGEX = Regex("(?iu)\\b\\d+(?:[.,]\\d+)?\\s*(?:km|min|minutos?|m)\\b")

    private val RIDE_CUES = listOf(
        "uberx", "uber comfort", "comfort", "priority", "uber pet", "uber moto", "uber flash",
        "99pop", "99 moto", "corrida", "nova corrida", "viagem", "nova viagem",
        "pedido de viagem", "pedido de corrida", "oferta", "passageiro",
    ).map(LearnedRideReader0700::canonical)

    private val NAVIGATION_NOISE = listOf(
        "barra de pesquisa", "pesquisa por voz", "street view", "continue por",
        "vire a esquerda", "vire a direita", "trajeto alternativo", "iniciar navegacao",
        "sua localizacao", "google maps",
    ).map(LearnedRideReader0700::canonical)
}
