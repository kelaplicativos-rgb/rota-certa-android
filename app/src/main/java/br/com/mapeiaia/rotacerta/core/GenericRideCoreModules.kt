package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher

object UberCoreModule : RideAppCoreModule {
    override val moduleName: String = "Uber"
    override val packageNames: Set<String> = setOf(RideCardTemplateMatcher.UBER_PACKAGE)

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification =
        GenericCoreClassifier.classifyKnownApp(snapshot, moduleName)
}

object NinetyNineCoreModule : RideAppCoreModule {
    override val moduleName: String = "99"
    override val packageNames: Set<String> = setOf(RideCardTemplateMatcher.NINETY_NINE_PACKAGE)

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification =
        GenericCoreClassifier.classifyKnownApp(snapshot, moduleName)
}

object UniversalCoreModule : RideAppCoreModule {
    override val moduleName: String = "Universal"
    override val packageNames: Set<String> = emptySet()

    override fun supports(packageName: String?): Boolean = true

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification =
        GenericCoreClassifier.classifyKnownApp(snapshot, moduleName)
}

private object GenericCoreClassifier {
    private val moneyRegex = Regex("r\\$\\s*\\d", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("\\b\\d+(?:[,.]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE)
    private val markerLineRegex = Regex("(?m)^\\s*[ab]\\s+.{5,}", RegexOption.IGNORE_CASE)
    private val listingWords = listOf("lista", "solicitacoes", "solicitações", "pedidos", "viagens disponiveis", "viagens disponíveis")

    fun classifyKnownApp(snapshot: RideScreenSnapshot, moduleName: String): RideScreenClassification {
        val normalized = snapshot.text.normalizedCoreText()
        if (snapshot.text.isBlank()) {
            return RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "$moduleName: texto vazio; aguardando card individual.",
                confidence = 0.1,
            )
        }
        if (listingWords.any { it in normalized } && moneyRegex.findAll(snapshot.text).count() > 1) {
            return RideScreenClassification(
                kind = RideScreenKind.RideListing,
                packageName = snapshot.packageName,
                reason = "$moduleName: listagem detectada; nao calcular km em card de lista.",
                confidence = 0.85,
            )
        }
        val hasDestination = !snapshot.fields.destination.isNullOrBlank()
        val hasMoney = moneyRegex.containsMatchIn(snapshot.text)
        val hasDistance = distanceRegex.containsMatchIn(snapshot.text)
        val hasMarkers = markerLineRegex.findAll(snapshot.text).count() >= 2 ||
            (!snapshot.fields.pickup.isNullOrBlank() && !snapshot.fields.destination.isNullOrBlank())
        val score = listOf(hasDestination, hasMoney, hasDistance, hasMarkers).count { it } / 4.0
        return if (score >= 0.75) {
            RideScreenClassification(
                kind = RideScreenKind.OpenRideCard,
                packageName = snapshot.packageName,
                reason = "$moduleName: card individual provavel confirmado pelo Core.",
                confidence = score,
            )
        } else {
            RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "$moduleName: leitura parcial; aguardando card individual completo.",
                confidence = score,
            )
        }
    }
}
