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
    private val acceptButtonRegex = Regex("aceitar\\s+por\\s+r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val farePerKmRegex = Regex("r\\$\\s*\\d+(?:[,.]\\d+)?\\s*/\\s*km", RegexOption.IGNORE_CASE)
    private val inDriveOfferButtonRegex = Regex("ofere[cç]a\\s+sua\\s+tarifa", RegexOption.IGNORE_CASE)
    private val routeMarkerInlineRegex = Regex("(?m)^\\s*[ab]\\s+.{5,}", RegexOption.IGNORE_CASE)

    private val timeDistanceLineRegex = Regex(
        """\b\d{1,3}\s*(?:seg|min|minuto|minutos)\s*\(\s*\d+(?:[,.]\d+)?\s*km\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val addressRegex = Regex(
        """(?:\b(?:rua|avenida|rodovia|estrada|travessa|alameda|praca|praça|bairro|jardim|cidade|parque|terminal|estacao|estação|condominio|condomínio)\b|\b(?:r|av)\.)""",
        RegexOption.IGNORE_CASE,
    )
    private val mapMarkerRegex = Regex("""(?m)^\s*[ab]\b""", RegexOption.IGNORE_CASE)

    private val blockedLearningSourcePackages = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "br.com.mapeiaia.rotacerta",
        "br.com.mapeiaia.rotacerta.learned.popup",
        "com.openai.chatgpt",
        "com.google.android.apps.nbu.files",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.sec.android.app.myfiles",
        "com.sec.android.gallery3d",
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.android.settings",
        "com.samsung.android.app.settings",
    )

    private val ownAppMarkers = listOf(
        "rota certa",
        "relatorio manual",
        "gerar relatorio",
        "modelos de cards",
        "configuracoes principais",
        "backup interno",
        "pacotes monitorados",
        "leitura ao vivo",
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
        "area de risco",
        "área de risco",
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
        "area de risco",
        "área de risco",
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
        if (normalized.length < 24) return false
        if (ownAppMarkers.any { marker -> marker in normalized }) return false
        val features = featuresFor(text)
        return "card.crop.route_block" in features
    }

    fun createTemplate(packageName: String?, text: String, name: String? = null): RideCardTemplate {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val features = featuresFor(text).toList().sorted()
        val label = name?.takeIf { it.isNotBlank() }
            ?: "Card ${appLabel(normalizedPackage)} ${features.filter { it.startsWith("card.") }.take(2).joinToString(" + ").ifBlank { "recorte" }}"
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
        if ("card.crop.route_block" !in liveFeatures) return null

        return templates.asSequence()
            .filter { template ->
                template.packageName.isNullOrBlank() ||
                    isUniversalLearnedPackage(template.packageName) ||
                    template.packageName.equals(normalizedPackage, ignoreCase = true)
            }
            .mapNotNull { template ->
                val universalPackage = isUniversalLearnedPackage(template.packageName)
                val evaluation = RealWorldRideCardMatchPolicy.evaluate(
                    requiredFeatures = template.requiredFeatures.toSet(),
                    liveFeatures = liveFeatures,
                    samePackage = template.packageName?.equals(normalizedPackage, ignoreCase = true) == true,
                    universalPackage = universalPackage,
                    learnableLiveCard = looksLikeLearnableRideCard(text),
                )
                if (!evaluation.accepted) return@mapNotNull null
                RideCardTemplateMatch(
                    template = template,
                    score = evaluation.score,
                    matchedFeatures = evaluation.matchedFeatures.toList().sorted(),
                )
            }
            .maxByOrNull(RideCardTemplateMatch::score)
    } // real_world_match_policy_checklist_11

    fun featuresFor(text: String): Set<String> = deterministicFeaturesFor(text)

    private fun deterministicFeaturesFor(text: String): Set<String> {
        val normalized = text.normalizedForCardMatch()
        val features = linkedSetOf<String>()
        stablePhrases.forEach { phrase ->
            val normalizedPhrase = phrase.normalizedForCardMatch()
            if (normalized.contains(normalizedPhrase)) features += normalizedPhrase
        }
        val moneyCount = moneyRegex.findAll(text).count()
        val distanceCount = distanceRegex.findAll(text).count()
        val timeCount = timeRegex.findAll(text).count()
        val timeDistanceCount = timeDistanceLineRegex.findAll(text).count()
        val addressCount = addressRegex.findAll(text).count()
        val markerCount = mapMarkerRegex.findAll(text).count()
        val hasMarkers = markerCount > 0
        val hasTwoMarkers = markerCount >= 2
        val endpointTextLines = routeEndpointTextLineCount(text)
        val hasRiskArea = "area de risco" in normalized

        if (moneyCount > 0) features += "valor em reais"
        if (distanceCount > 0) features += "distancia em km"
        if (timeCount > 0) features += "tempo de rota"
        if (addressCount > 0 || endpointTextLines >= 2) features += "endereco"
        if (hasMarkers) features += "marcadores a/b"

        if (timeDistanceCount >= 1) features += "card.route.time_distance"
        if (timeDistanceCount >= 2) features += "card.route.two_time_distance"
        if (distanceCount >= 2) features += "card.route.two_distances"
        if (timeCount >= 2) features += "card.route.two_times"
        if (addressCount >= 1 || endpointTextLines >= 1) features += "card.route.address"
        if (addressCount >= 2 || endpointTextLines >= 2) features += "card.route.two_addresses"
        if (hasMarkers) features += "card.route.ab_markers"
        if (hasTwoMarkers && endpointTextLines >= 2) features += "card.route.marked_stops"
        if (hasRiskArea) features += "card.risk_area"

        val hasRouteBlock = isRouteCardCrop(
            normalized = normalized,
            timeDistanceCount = timeDistanceCount,
            timeCount = timeCount,
            distanceCount = distanceCount,
            addressCount = addressCount,
            hasMarkers = hasMarkers,
            hasTwoMarkers = hasTwoMarkers,
            endpointTextLines = endpointTextLines,
        )
        if (hasRouteBlock) features += "card.crop.route_block"
        if (isInDriveIndividualContract(normalized, text, moneyCount, distanceCount, addressCount, markerCount, endpointTextLines)) {
            features += "card.contract.indrive_individual"
        }
        if (isInDriveOpenedCardContract(normalized, text, moneyCount, distanceCount, addressCount, markerCount, endpointTextLines)) {
            features += "card.contract.indrive_opened_single"
        }
        return features
    }

    private fun isInDriveListingScreen(normalized: String, rawText: String): Boolean {
        val rideTitleCount = Regex("pedido[s]?\\s+de\\s+viagem", RegexOption.IGNORE_CASE).findAll(rawText).count()
        val acceptCount = acceptButtonRegex.findAll(rawText).count()
        val offerCount = inDriveOfferButtonRegex.findAll(rawText).count()
        val hasPluralHeader = "pedidos de viagem" in normalized
        val hasListWords = listOf("filtrar", "ordenar", "mais pedidos", "novos pedidos", "solicitacoes", "solicitações").any { it in normalized }
        return hasPluralHeader || hasListWords || rideTitleCount > 1 || acceptCount > 1 || offerCount > 1
    }

    private fun isInDriveOpenedCardContract(
        normalized: String,
        rawText: String,
        moneyCount: Int,
        distanceCount: Int,
        addressCount: Int,
        markerCount: Int,
        endpointTextLines: Int,
    ): Boolean {
        if (isInDriveListingScreen(normalized, rawText)) return false
        val hasSingularRideTitle = "pedido de viagem" in normalized && "pedidos de viagem" !in normalized
        val hasAccept = acceptButtonRegex.containsMatchIn(rawText) || "aceitar por" in normalized
        val hasOffer = inDriveOfferButtonRegex.containsMatchIn(rawText) || "ofereca sua tarifa" in normalized || "ofereça sua tarifa" in normalized
        val hasPrimaryAction = hasAccept || hasOffer
        val hasFarePerKm = farePerKmRegex.containsMatchIn(rawText)
        val hasTwoEndpoints = endpointTextLines >= 2 || markerCount >= 2 || routeMarkerInlineRegex.findAll(rawText).count() >= 2 || addressCount >= 2 // indrive_markerless_endpoints_0_1_87
        val hasEnoughAddress = addressCount >= 1 || endpointTextLines >= 2
        val hasRouteStructure = hasTwoEndpoints && hasEnoughAddress
        return hasRouteStructure &&
            hasPrimaryAction &&
            moneyCount >= 1 &&
            (distanceCount >= 1 || hasFarePerKm || hasSingularRideTitle) // indrive_card_family_contract_0_1_85
    }

    private fun isInDriveIndividualContract(
        normalized: String,
        rawText: String,
        moneyCount: Int,
        distanceCount: Int,
        addressCount: Int,
        markerCount: Int,
        endpointTextLines: Int,
    ): Boolean {
        if (ownAppMarkers.any { marker -> marker in normalized }) return false
        if (isInDriveListingScreen(normalized, rawText)) return false
        val hasRideTitle = "pedido de viagem" in normalized && "pedidos de viagem" !in normalized
        val hasAccept = acceptButtonRegex.containsMatchIn(rawText) || "aceitar por" in normalized
        val hasOffer = inDriveOfferButtonRegex.containsMatchIn(rawText) || "ofereca sua tarifa" in normalized || "ofereça sua tarifa" in normalized
        val hasPrimaryAction = hasAccept || hasOffer
        val hasMoney = moneyCount >= 1
        val hasRouteKm = distanceCount >= 1 || farePerKmRegex.containsMatchIn(rawText)
        val hasTwoEndpoints = endpointTextLines >= 2 || markerCount >= 2 || routeMarkerInlineRegex.findAll(rawText).count() >= 2 || addressCount >= 2 // indrive_markerless_endpoints_0_1_87
        val hasAddress = addressCount >= 1 || endpointTextLines >= 2
        val hasRouteStructure = hasTwoEndpoints && hasAddress
        return hasRouteStructure &&
            hasPrimaryAction &&
            hasMoney &&
            (hasRouteKm || hasRideTitle) // indrive_card_family_contract_0_1_85
    }

    private fun isRouteCardCrop(
        normalized: String,
        timeDistanceCount: Int,
        timeCount: Int,
        distanceCount: Int,
        addressCount: Int,
        hasMarkers: Boolean,
        hasTwoMarkers: Boolean,
        endpointTextLines: Int,
    ): Boolean {
        val markerlessInDriveIndividual =
            "pedido de viagem" in normalized &&
                "pedidos de viagem" !in normalized &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized) &&
                addressCount >= 2 &&
                distanceCount >= 1
        if (markerlessInDriveIndividual) return true // indrive_markerless_route_block_0_1_87
        if (ownAppMarkers.any { marker -> marker in normalized }) return false
        if (timeDistanceCount >= 2 && addressCount >= 1) return true
        if (timeDistanceCount >= 1 && addressCount >= 2) return true
        if (hasMarkers && distanceCount >= 1 && addressCount >= 2) return true
        if (hasTwoMarkers && endpointTextLines >= 2) return true
        if (timeCount >= 2 && distanceCount >= 2 && addressCount >= 1) return true
        val inDriveMarkerlessOffer128 =
            ("pedido de viagem" in normalized || "pedidos de viagem" in normalized) &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized || "preco justo" in normalized) &&
                (addressCount >= 2 || endpointTextLines >= 2)
        if (inDriveMarkerlessOffer128) return true // indrive_markerless_offer_crop_0_1_128
        return false
    }

    private fun routeEndpointTextLineCount(text: String): Int {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var count = 0
        for (index in lines.indices) {
            val line = lines[index]
            if (line.equals("A", ignoreCase = true) || line.equals("B", ignoreCase = true)) {
                val next = lines.getOrNull(index + 1).orEmpty()
                if (next.length >= 5) count += 1
            } else if (Regex("""^[AB]\s+.+""", RegexOption.IGNORE_CASE).matches(line) && line.length >= 7) {
                count += 1
            }
        }
        return count
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

    private val structuralFeatures = setOf("distancia em km", "tempo de rota", "endereco")
    private val strictCardFeatures = setOf(
        "card.crop.route_block",
        "card.route.two_time_distance",
        "card.route.two_distances",
        "card.route.two_times",
        "card.route.two_addresses",
        "card.route.ab_markers",
        "card.route.marked_stops",
        "card.risk_area",
    )

    private const val MIN_SCORE = 0.72
    private const val MIN_FEATURES = 4
    private const val UNIVERSAL_MIN_SCORE = 0.82
    private const val UNIVERSAL_MIN_FEATURES = 4
}

data class RideCardTemplateMatch(
    val template: RideCardTemplate,
    val score: Double,
    val matchedFeatures: List<String>,
)
