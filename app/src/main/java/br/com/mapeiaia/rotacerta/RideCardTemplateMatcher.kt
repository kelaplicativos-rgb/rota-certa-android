package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object RideCardTemplateMatcher {
    const val UBER_PACKAGE = "com.ubercab.driver"
    const val NINETY_NINE_PACKAGE = "com.app99.driver"
    const val INDRIVE_PACKAGE = "sinet.startup.indriver"
    const val UNIVERSAL_LEARNED_PACKAGE = "br.com.mapeiaia.rotacerta.learned.universal"

    private val moneyRegex = Regex("""R\$\s*\d""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:seg|min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val addressRegex = Regex(
        """(?:\b(?:rua|avenida|rodovia|estrada|travessa|alameda|praca|praça|bairro|jardim|cidade|parque|terminal|estacao|estação)\b|\b(?:r|av)\.)""",
        RegexOption.IGNORE_CASE,
    )
    private val mapMarkerRegex = Regex("""(?m)^\s*[ab]\b""", RegexOption.IGNORE_CASE)

    private val blockedLearningSourcePackages = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "br.com.mapeiaia.rotacerta",
        "com.google.android.apps.nbu.files",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.sec.android.app.myfiles",
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.android.settings",
        "com.samsung.android.app.settings",
    )

    private val stablePhrases = listOf(
        "pedido de viagem",
        "pedidos de viagem",
        "aceitar por",
        "aceitar",
        "selecionar",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "negocia",
        "perfil premium",
        "perfil essencial",
        "uberx",
        "pop expresso",
        "exclusivo",
        "viagem longa",
        "radar de viagens",
        "preco justo",
        "preço justo",
        "dinheiro",
        "pix",
    )

    private val uberPackagePhrases = listOf("uberx", "exclusivo", "viagem longa", "radar de viagens", "pop expresso")
    private val ninetyNinePackagePhrases = listOf("negocia", "perfil premium", "perfil essencial")
    private val inDrivePackagePhrases = listOf(
        "pedido de viagem",
        "pedidos de viagem",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "preco justo",
        "preço justo",
    )

    fun inferPackageName(text: String): String? {
        val normalized = text.normalizedForCardMatch()
        return when {
            uberPackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> UBER_PACKAGE
            ninetyNinePackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> NINETY_NINE_PACKAGE
            inDrivePackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> INDRIVE_PACKAGE
            else -> null
        }
    }

    fun packageNameForLearning(sourcePackageName: String?, text: String): String? {
        val inferredKnownApp = inferPackageName(text)
        if (inferredKnownApp != null) return inferredKnownApp
        if (!looksLikeLearnableRideCard(text)) return null
        val normalizedSource = sourcePackageName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        return if (normalizedSource == null || isBlockedLearningSourcePackage(normalizedSource)) {
            UNIVERSAL_LEARNED_PACKAGE
        } else {
            normalizedSource
        }
    }

    fun isUniversalLearnedPackage(packageName: String?): Boolean =
        packageName?.equals(UNIVERSAL_LEARNED_PACKAGE, ignoreCase = true) == true

    fun looksLikeLearnableRideCard(text: String): Boolean {
        val normalized = text.normalizedForCardMatch()
        if (normalized.length < 35) return false
        val features = featuresFor(text)
        val structuralCount = listOf("valor em reais", "distancia em km", "endereco").count { it in features }
        val hasRouteStructure = "adaptive.route.two_addresses" in features ||
            "adaptive.structure.map_marked_addresses" in features ||
            "marcadores a/b" in features
        val hasRideAction = features.any {
            it == "aceitar" ||
                it == "aceitar por" ||
                it == "selecionar" ||
                it.startsWith("adaptive.action.")
        }
        val hasRidePhrase = features.any {
            it in stablePhrases ||
                it.startsWith("adaptive.phrase.") ||
                it.startsWith("adaptive.payment.") ||
                it.startsWith("adaptive.structure.")
        }
        val hasUsefulRouteData = structuralCount >= 2 && ("endereco" in features || hasRouteStructure)
        return hasUsefulRouteData && (hasRideAction || hasRidePhrase || structuralCount >= 3)
    }

    fun createTemplate(packageName: String?, text: String, name: String? = null): RideCardTemplate {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val features = featuresFor(text).toList().sorted()
        val label = name?.takeIf { it.isNotBlank() }
            ?: "Card ${appLabel(normalizedPackage)} ${features.filterNot { it.startsWith("adaptive.") }.take(2).joinToString(" + ").ifBlank { "manual" }}"
        return RideCardTemplate(
            id = "card-${System.currentTimeMillis()}-${text.stableHash()}",
            name = label.take(80),
            packageName = normalizedPackage,
            requiredFeatures = features,
            sampleHash = text.stableHash(),
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val liveFeatures = deterministicFeaturesFor(text)
        val candidates = templates
            .asSequence()
            .filter { template ->
                template.packageName.isNullOrBlank() ||
                    isUniversalLearnedPackage(template.packageName) ||
                    template.packageName.equals(normalizedPackage, ignoreCase = true)
            }
            .mapNotNull { template ->
                val required = template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .toList()

        return candidates
            .asSequence()
            .filter { match ->
                val samePackage = match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true
                val universalPackage = isUniversalLearnedPackage(match.template.packageName)
                val required = match.template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                val requiredStructuralFeatures = structuralFeatures.intersect(required)
                val structuralOk = requiredStructuralFeatures.all { it in match.matchedFeatures }
                if (universalPackage) {
                    looksLikeLearnableRideCard(text) &&
                        match.score >= UNIVERSAL_MIN_SCORE &&
                        match.matchedFeatures.size >= required.size.coerceAtMost(UNIVERSAL_MIN_FEATURES).coerceAtLeast(MIN_FEATURES) &&
                        structuralOk
                } else {
                    samePackage &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES &&
                        structuralOk
                }
            }
            .maxByOrNull { it.score }
    }

    fun featuresFor(text: String): Set<String> =
        deterministicFeaturesFor(text) + AdaptiveCardLearningEngine.adaptiveFeaturesFor(text)

    private fun deterministicFeaturesFor(text: String): Set<String> {
        val normalized = text.normalizedForCardMatch()
        val features = linkedSetOf<String>()
        stablePhrases.forEach { phrase ->
            val normalizedPhrase = phrase.normalizedForCardMatch()
            if (normalized.contains(normalizedPhrase)) features += normalizedPhrase
        }
        if (moneyRegex.containsMatchIn(text)) features += "valor em reais"
        if (distanceRegex.containsMatchIn(text)) features += "distancia em km"
        if (timeRegex.containsMatchIn(text)) features += "tempo de rota"
        if (addressRegex.containsMatchIn(text)) features += "endereco"
        if (mapMarkerRegex.containsMatchIn(text)) features += "marcadores a/b"
        return features
    }

    private fun isBlockedLearningSourcePackage(packageName: String): Boolean =
        blockedLearningSourcePackages.any { packageName == it || packageName.contains(it) } ||
            packageName.contains("launcher") ||
            packageName.contains("inputmethod")

    private fun appLabel(packageName: String?): String = when (packageName) {
        UBER_PACKAGE -> "Uber"
        NINETY_NINE_PACKAGE -> "99"
        INDRIVE_PACKAGE -> "inDrive"
        UNIVERSAL_LEARNED_PACKAGE -> "Universal"
        else -> packageName ?: "manual"
    }

    private fun String.stableHash(): Int =
        lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .hashCode()

    private fun String.normalizedForCardMatch(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val structuralFeatures = setOf("valor em reais", "distancia em km", "endereco")

    private const val MIN_SCORE = 0.75
    private const val MIN_FEATURES = 3
    private const val UNIVERSAL_MIN_SCORE = 0.95
    private const val UNIVERSAL_MIN_FEATURES = 4
}

data class RideCardTemplateMatch(
    val template: RideCardTemplate,
    val score: Double,
    val matchedFeatures: List<String>,
)
