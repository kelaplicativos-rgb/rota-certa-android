package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher

object InDriveCoreModule : RideAppCoreModule {
    override val moduleName: String = "inDrive"
    override val packageNames: Set<String> = setOf(RideCardTemplateMatcher.INDRIVE_PACKAGE)

    private val acceptButtonRegex = Regex("aceitar\\s+por\\s+r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val offerButtonRegex = Regex("ofere[cç]a\\s+sua\\s+tarifa", RegexOption.IGNORE_CASE)
    private val farePerKmRegex = Regex("r\\$\\s*\\d+(?:[,.]\\d+)?\\s*/\\s*km", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("r\\$\\s*\\d", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("\\b\\d+(?:[,.]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE)
    private val markerLineRegex = Regex("(?m)^\\s*[ab]\\s+.{5,}", RegexOption.IGNORE_CASE)

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification {
        val normalized = snapshot.text.normalizedCoreText()
        if (snapshot.text.isBlank()) {
            return RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "Texto do inDrive ainda vazio; aguardando card individual aberto.",
                confidence = 0.1,
            )
        }
        if (isListing(normalized, snapshot.text)) {
            return RideScreenClassification(
                kind = RideScreenKind.RideListing,
                packageName = snapshot.packageName,
                reason = "Listagem/feed do inDrive detectado; nao calcular km em cards dentro da lista.",
                confidence = 0.95,
            )
        }

        val hasRideTitle = "pedido de viagem" in normalized || "pedidos de viagem" in normalized // open_all_indrive_titles_0_1_94
        val hasAccept = acceptButtonRegex.containsMatchIn(snapshot.text) || "aceitar por" in normalized
        val hasOffer = offerButtonRegex.containsMatchIn(snapshot.text) || "ofereca sua tarifa" in normalized || "ofereça sua tarifa" in normalized
        val hasPrimaryAction = hasAccept || hasOffer
        val hasMoney = moneyRegex.containsMatchIn(snapshot.text)
        val hasRouteKm = distanceRegex.containsMatchIn(snapshot.text) || farePerKmRegex.containsMatchIn(snapshot.text)
        val markerCount = markerLineRegex.findAll(snapshot.text).count()
        val hasTwoMarkers = markerCount >= 2 || (!snapshot.fields.pickup.isNullOrBlank() && !snapshot.fields.destination.isNullOrBlank())
        val hasDestination = !snapshot.fields.destination.isNullOrBlank()

        val openSignals = listOf(hasRideTitle, hasAccept, hasOffer, hasMoney, hasRouteKm, hasTwoMarkers, hasDestination)
        val score = openSignals.count { it } / openSignals.size.toDouble()
        val hasIndividualCardContract = hasDestination &&
            (hasPrimaryAction || hasMoney || hasRouteKm || hasTwoMarkers) // indrive_card_family_0_1_85 open_all_indrive_contract_0_1_94

        return if (hasIndividualCardContract) {
            RideScreenClassification(
                kind = RideScreenKind.OpenRideCard,
                packageName = snapshot.packageName,
                reason = "Card individual aberto do inDrive confirmado pelo Rota Certa Core.",
                confidence = maxOf(score, 0.90),
            )
        } else {
            RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "inDrive lido parcialmente; ainda nao confirmou card individual aberto.",
                confidence = score,
            )
        }
    }

    private fun isListing(normalized: String, rawText: String): Boolean =
        CoreCardMatchEngine.isListLikeRideFeed(rawText, normalized) // open_all_indrive_listing_0_1_94
}

internal fun String.normalizedCoreText(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
