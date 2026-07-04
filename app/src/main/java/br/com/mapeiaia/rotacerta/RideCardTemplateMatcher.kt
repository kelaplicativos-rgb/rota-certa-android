package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object RideCardTemplateMatcher {
    const val UBER_PACKAGE = "com.ubercab.driver"
    const val NINETY_NINE_PACKAGE = "com.app99.driver"
    const val INDRIVE_PACKAGE = "sinet.startup.indriver"

    private val moneyRegex = Regex("""R\$\s*\d""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:seg|min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val addressRegex = Regex(
        """(?:\b(?:rua|avenida|rodovia|estrada|travessa|alameda|praca|praça|bairro|jardim|cidade|parque|terminal|estacao|estação)\b|\b(?:r|av)\.)""",
        RegexOption.IGNORE_CASE,
    )
    private val mapMarkerRegex = Regex("""(?m)^\s*[ab]\b""", RegexOption.IGNORE_CASE)

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
    private val inDrivePackagePhrases = listOf("pedido de viagem", "ofereca sua tarifa", "ofereça sua tarifa", "preco justo", "preço justo")

    fun inferPackageName(text: String): String? {
        val normalized = text.normalizedForCardMatch()
        return when {
            uberPackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> UBER_PACKAGE
            ninetyNinePackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> NINETY_NINE_PACKAGE
            inDrivePackagePhrases.any { normalized.contains(it.normalizedForCardMatch()) } -> INDRIVE_PACKAGE
            else -> null
        }
    }

    fun createTemplate(packageName: String?, text: String, name: String? = null): RideCardTemplate {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val features = featuresFor(text).toList().sorted()
        val label = name?.takeIf { it.isNotBlank() }
            ?: "Card ${appLabel(normalizedPackage)} ${features.take(2).joinToString(" + ").ifBlank { "manual" }}"
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
        val features = featuresFor(text)
        return templates
            .asSequence()
            .filter { template ->
                template.packageName.isNullOrBlank() || template.packageName.equals(normalizedPackage, ignoreCase = true)
            }
            .mapNotNull { template ->
                val required = template.requiredFeatures.toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(features)
                val score = matched.size.toDouble() / max(required.size, 1)
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .filter { match ->
                val samePackage = match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true
                val required = match.template.requiredFeatures.toSet()
                val requiredStructuralFeatures = structuralFeatures.intersect(required)
                samePackage &&
                    match.score >= MIN_SCORE &&
                    match.matchedFeatures.size >= MIN_FEATURES &&
                    requiredStructuralFeatures.all { it in match.matchedFeatures }
            }
            .maxByOrNull { it.score }
    }

    fun featuresFor(text: String): Set<String> {
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

    private fun appLabel(packageName: String?): String = when (packageName) {
        "com.ubercab.driver" -> "Uber"
        "com.app99.driver" -> "99"
        "sinet.startup.indriver" -> "inDrive"
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
}

data class RideCardTemplateMatch(
    val template: RideCardTemplate,
    val score: Double,
    val matchedFeatures: List<String>,
)
