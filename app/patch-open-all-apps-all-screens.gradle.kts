// Libera o leitor para qualquer aplicativo/tela e remove gates excessivos de pacote,
// classificacao e match. Verde/vermelho ainda depende de um modelo cadastrado com
// dados de rota suficientes para calcular o destino final.

val openAllAppsAllScreensPatch by tasks.registering {
    val sourceRoot = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta")
    val testRoot = layout.projectDirectory.dir("src/test/java/br/com/mapeiaia/rotacerta")
    val packageMonitorFile = sourceRoot.file("core/CorePackageMonitor.kt")
    val screenContractsFile = sourceRoot.file("core/RideScreenContracts.kt")
    val inDriveModuleFile = sourceRoot.file("core/InDriveCoreModule.kt")
    val genericModulesFile = sourceRoot.file("core/GenericRideCoreModules.kt")
    val cardContractsFile = sourceRoot.file("core/CoreRideCardContracts.kt")
    val cardMatchEngineFile = sourceRoot.file("core/CoreCardMatchEngine.kt")
    val templateMatcherFile = sourceRoot.file("RideCardTemplateMatcher.kt")
    val fastMatcherFile = sourceRoot.file("FastRideCardMatcher.kt")
    val packageMonitorTestFile = testRoot.file("core/CorePackageMonitorTest.kt")

    inputs.files(
        packageMonitorFile,
        screenContractsFile,
        inDriveModuleFile,
        genericModulesFile,
        cardContractsFile,
        cardMatchEngineFile,
        templateMatcherFile,
        fastMatcherFile,
        packageMonitorTestFile,
    )
    outputs.upToDateWhen { false }

    fun replaceRequired(file: java.io.File, old: String, replacement: String, marker: String) {
        var text = file.readText()
        if (marker in text) return
        if (old !in text) throw GradleException("Nao encontrei trecho para aplicar $marker em ${file.name}.")
        text = text.replace(old, replacement)
        file.writeText(text)
    }

    fun replaceRegion(
        file: java.io.File,
        startToken: String,
        endToken: String,
        replacement: String,
        marker: String,
    ) {
        val text = file.readText()
        if (marker in text) return
        val start = text.indexOf(startToken)
        val end = if (start >= 0) text.indexOf(endToken, start) else -1
        if (start < 0 || end < 0) throw GradleException("Nao encontrei regiao para aplicar $marker em ${file.name}.")
        file.writeText(text.substring(0, start) + replacement + text.substring(end))
    }

    doLast {
        packageMonitorFile.asFile.writeText(
            """package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.util.Locale

/**
 * Portaria universal do Rota Certa Core.
 * Nenhum pacote instalado e bloqueado antes da leitura. O pacote apenas seleciona
 * o modulo especializado quando conhecido; qualquer outro segue pelo Universal.
 */
object CorePackageMonitor {
    private const val PACKAGE_99_DRIVER = RideCardTemplateMatcher.NINETY_NINE_PACKAGE
    private const val PACKAGE_UBER_DRIVER = RideCardTemplateMatcher.UBER_PACKAGE
    private const val PACKAGE_INDRIVE_DRIVER = RideCardTemplateMatcher.INDRIVE_PACKAGE

    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        if (!settings.appEnabled) {
            return CorePackageClassification(
                packageName = normalize(packageName),
                kind = CorePackageKind.Disabled,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Rota Certa desligado pelo usuario.",
            )
        }

        val normalized = normalize(packageName)
            ?: return CorePackageClassification(
                packageName = null,
                kind = CorePackageKind.Unknown,
                module = CoreRideAppModule.Universal,
                canScan = false,
                reason = "Pacote ainda nao informado pelo Android; nova leitura sera tentada imediatamente.",
            )

        val module = moduleFor(normalized)
        return CorePackageClassification(
            packageName = normalized,
            kind = CorePackageKind.RideApp,
            module = module,
            canScan = true,
            reason = if (module == CoreRideAppModule.Universal) {
                "Leitura universal liberada sem trava de aplicativo: " + normalized + "."
            } else {
                "Leitura liberada no modulo " + module.name + ": " + normalized + "."
            },
        )
    }

    fun selectedRidePackages(settings: AppSettings): Set<String> {
        val packages = mutableSetOf(PACKAGE_99_DRIVER, PACKAGE_UBER_DRIVER, PACKAGE_INDRIVE_DRIVER)
        packages += settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
        return packages
    }

    fun isPassive(packageName: String?, ownPackageName: String): Boolean = false

    fun normalize(packageName: String?): String? =
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

    private fun moduleFor(packageName: String): CoreRideAppModule = when (packageName) {
        PACKAGE_INDRIVE_DRIVER -> CoreRideAppModule.InDrive
        PACKAGE_99_DRIVER -> CoreRideAppModule.NinetyNine
        PACKAGE_UBER_DRIVER -> CoreRideAppModule.Uber
        else -> CoreRideAppModule.Universal
    }
}

enum class CorePackageKind {
    RideApp,
    Passive,
    OwnApp,
    Ignored,
    NotMonitored,
    Disabled,
    Unknown,
}

enum class CoreRideAppModule {
    InDrive,
    NinetyNine,
    Uber,
    Universal,
    Unknown,
}

data class CorePackageClassification(
    val packageName: String?,
    val kind: CorePackageKind,
    val module: CoreRideAppModule,
    val canScan: Boolean,
    val reason: String,
)
// open_all_apps_all_screens_0_1_94
""",
        )

        replaceRequired(
            screenContractsFile.asFile,
            "    val canAnalyzeRoute: Boolean get() = kind == RideScreenKind.OpenRideCard\n",
            "    val canAnalyzeRoute: Boolean get() = true // open_all_screen_classifications_0_1_94\n",
            "open_all_screen_classifications_0_1_94",
        )

        replaceRequired(
            inDriveModuleFile.asFile,
            "        val hasSingularRideTitle = \"pedido de viagem\" in normalized && \"pedidos de viagem\" !in normalized\n",
            "        val hasRideTitle = \"pedido de viagem\" in normalized || \"pedidos de viagem\" in normalized // open_all_indrive_titles_0_1_94\n",
            "open_all_indrive_titles_0_1_94",
        )
        replaceRequired(
            inDriveModuleFile.asFile,
            "        val openSignals = listOf(hasSingularRideTitle, hasAccept, hasOffer, hasMoney, hasRouteKm, hasTwoMarkers, hasDestination)\n",
            "        val openSignals = listOf(hasRideTitle, hasAccept, hasOffer, hasMoney, hasRouteKm, hasTwoMarkers, hasDestination)\n",
            "open_all_indrive_contract_0_1_94",
        )
        replaceRequired(
            inDriveModuleFile.asFile,
            "        val hasIndividualCardContract = hasDestination &&\n            hasPrimaryAction &&\n            hasMoney &&\n            (hasRouteKm || hasTwoMarkers) // indrive_card_family_0_1_85\n",
            "        val hasIndividualCardContract = hasDestination &&\n            (hasPrimaryAction || hasMoney || hasRouteKm || hasTwoMarkers) // indrive_card_family_0_1_85 open_all_indrive_contract_0_1_94\n",
            "open_all_indrive_contract_0_1_94",
        )
        replaceRegion(
            inDriveModuleFile.asFile,
            "    private fun isListing(normalized: String, rawText: String): Boolean {\n",
            "}\n\ninternal fun String.normalizedCoreText()",
            """    private fun isListing(normalized: String, rawText: String): Boolean =
        CoreCardMatchEngine.isListLikeRideFeed(rawText, normalized) // open_all_indrive_listing_0_1_94
""",
            "open_all_indrive_listing_0_1_94",
        )

        replaceRequired(
            genericModulesFile.asFile,
            "        if (listingWords.any { it in normalized } && moneyRegex.findAll(snapshot.text).count() > 1) {\n",
            "        if (CoreCardMatchEngine.isListLikeRideFeed(snapshot.text, normalized)) { // open_all_generic_listing_0_1_94\n",
            "open_all_generic_listing_0_1_94",
        )
        replaceRequired(
            genericModulesFile.asFile,
            "        return if (score >= 0.75) {\n",
            "        return if (hasDestination && score >= 0.25) { // open_all_generic_classifier_0_1_94\n",
            "open_all_generic_classifier_0_1_94",
        )

        listOf(
            "        if (\"card.crop.route_block\" !in features) {\n            return CoreRideCardContractResult.rejected(name, \"inDrive sem bloco individual de rota.\")\n        }\n",
            "        if (\"card.crop.route_block\" !in features) {\n            return CoreRideCardContractResult.rejected(name, \"Uber sem bloco individual de rota.\")\n        }\n",
            "        if (\"card.crop.route_block\" !in features) {\n            return CoreRideCardContractResult.rejected(name, \"99 sem bloco individual de rota.\")\n        }\n",
            "        if (\"card.crop.route_block\" !in features) {\n            return CoreRideCardContractResult.rejected(name, \"Universal sem bloco individual de rota.\")\n        }\n",
        ).forEach { blocked ->
            val file = cardContractsFile.asFile
            val current = file.readText()
            if (blocked in current) file.writeText(current.replace(blocked, ""))
        }
        replaceRequired(
            cardContractsFile.asFile,
            "        val hasRideTitle = \"pedido de viagem\" in normalized && \"pedidos de viagem\" !in normalized\n",
            "        val hasRideTitle = \"pedido de viagem\" in normalized || \"pedidos de viagem\" in normalized // open_all_contract_titles_0_1_94\n",
            "open_all_contract_titles_0_1_94",
        )

        fastMatcherFile.asFile.writeText(
            """package br.com.mapeiaia.rotacerta

import java.util.Locale

object FastRideCardMatcher {
    private val routeSignals = setOf(
        "card.crop.route_block",
        "card.route.address",
        "card.route.two_addresses",
        "card.route.two_distances",
        "card.route.two_times",
        "card.route.ab_markers",
        "card.route.marked_stops",
        "distancia em km",
        "tempo de rota",
        "endereco",
        "marcadores a/b",
    )

    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val liveFeatures = RideCardTemplateMatcher.featuresFor(text)
        val liveCore = liveFeatures.intersect(routeSignals)
        if (liveFeatures.size < 3 || liveCore.size < 2) return null

        return templates
            .asSequence()
            .mapNotNull { template ->
                val required = template.requiredFeatures.filterNot { it.startsWith("adaptive.") }.toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val matchedCore = required.intersect(routeSignals).intersect(liveCore)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 3 || matchedCore.size < 2 || score < 0.25) return@mapNotNull null
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .maxByOrNull { match ->
                match.score + if (match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true) 0.08 else 0.0
            }
    }
}
// open_all_fast_matcher_0_1_94
""",
        )

        replaceRegion(
            templateMatcherFile.asFile,
            "    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {\n",
            "    fun featuresFor(text: String): Set<String>",
            """    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val liveFeatures = deterministicFeaturesFor(text)
        if (liveFeatures.size < 3) return null

        return templates
            .asSequence()
            .mapNotNull { template ->
                val required = template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                RideCardTemplateMatch(template = template, score = score, matchedFeatures = matched.toList().sorted())
            }
            .filter { match ->
                val matched = match.matchedFeatures.toSet()
                val matchedStructural = structuralFeatures.intersect(matched)
                val matchedStrict = strictCardFeatures.intersect(matched)
                match.matchedFeatures.size >= 3 &&
                    (matchedStructural.size >= 2 || matchedStrict.isNotEmpty()) &&
                    match.score >= 0.25
            }
            .maxByOrNull { match ->
                match.score + if (match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true) 0.08 else 0.0
            }
    } // open_all_template_matcher_0_1_94

""",
            "open_all_template_matcher_0_1_94",
        )

        replaceRegion(
            cardMatchEngineFile.asFile,
            "    fun match(\n",
            "    fun isListLikeRideFeed(",
            """    fun match(
        text: String,
        packageName: String?,
        templates: List<RideCardTemplate>,
    ): CoreCardMatchResult {
        val normalizedPackage = CorePackageMonitor.normalize(packageName)
            ?: RideCardTemplateMatcher.UNIVERSAL_LEARNED_PACKAGE
        if (templates.isEmpty()) {
            return CoreCardMatchResult.rejected("Nenhum card cadastrado para comparar com a tela atual.")
        }

        val preparedText = CoreRideTextSanitizer.sanitize(text, normalizedPackage)
        val normalizedText = preparedText.normalizedCoreText()
        if (isListLikeRideFeed(preparedText, normalizedText)) {
            return CoreCardMatchResult.rejected(
                reason = "Foram detectadas varias ofertas ao mesmo tempo; aguardando um card individual visivel.",
                isListLike = true,
                contractName = CoreRideCardContractRegistry.contractFor(normalizedPackage).name,
            )
        }

        val detectedFeatures = RideCardTemplateMatcher.featuresFor(preparedText)
        val routeEvidence = routeSignals.intersect(detectedFeatures)
        val features = if ("card.crop.route_block" in detectedFeatures || routeEvidence.size < 2) {
            detectedFeatures
        } else {
            detectedFeatures + "card.crop.route_block"
        }
        val contract = CoreRideCardContractRegistry.contractFor(normalizedPackage)
        val contractResult = contract.evaluate(preparedText, normalizedPackage, features)
        if (!contractResult.accepted) {
            return CoreCardMatchResult.rejected(
                reason = contractResult.reason,
                isListLike = contractResult.isListLike,
                contractName = contractResult.contractName,
            )
        }

        val match = RideCardTemplateMatcher.match(preparedText, normalizedPackage, templates)
            ?: FastRideCardMatcher.match(preparedText, normalizedPackage, templates)
            ?: registeredFamilyFallback(normalizedPackage, templates, features)
            ?: return CoreCardMatchResult.rejected(
                reason = "A tela foi lida sem trava, mas ainda nao possui semelhanca suficiente com um modelo cadastrado.",
                contractName = contract.name,
            )

        return CoreCardMatchResult.accepted(
            match = match,
            reason = "Modelo cadastrado confirmado sem trava de pacote ou tela.",
            contractName = contract.name,
        )
    } // open_all_core_match_0_1_94

""",
            "open_all_core_match_0_1_94",
        )

        replaceRegion(
            cardMatchEngineFile.asFile,
            "    fun isListLikeRideFeed(text: String, normalizedText: String = text.normalizedCoreText()): Boolean {\n",
            "    private fun hasStrongOpenedCardEvidence(",
            """    fun isListLikeRideFeed(text: String, normalizedText: String = text.normalizedCoreText()): Boolean {
        val normalizedLines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.normalizedCoreText() }
        val singularTitleCount = normalizedLines.count { it == "pedido de viagem" }
        val hasPluralHeader = normalizedLines.any { it == "pedidos de viagem" }
        val acceptButtonCount = acceptButtonRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()
        val endpointCount = routeEndpointSignatures(text).size
        val moneyCount = moneyRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()
        val distanceCount = distanceRegex.findAll(text)
            .map { it.value.normalizedCoreText().replace(" ", "") }
            .distinct()
            .count()

        if (acceptButtonCount >= 2) return true
        if (singularTitleCount >= 2 && endpointCount >= 4) return true
        if (endpointCount >= 4 && moneyCount >= 2 && distanceCount >= 2) return true
        if (hasPluralHeader && moneyCount >= 3 && distanceCount >= 2) return true
        return false
    } // open_all_list_detection_0_1_94

""",
            "open_all_list_detection_0_1_94",
        )

        replaceRegion(
            cardMatchEngineFile.asFile,
            "    private fun registeredFamilyFallback(\n",
            "    private fun routeEndpointSignatures(",
            """    private fun registeredFamilyFallback(
        packageName: String,
        templates: List<RideCardTemplate>,
        liveFeatures: Set<String>,
    ): RideCardTemplateMatch? {
        val liveRouteSignals = routeSignals.intersect(liveFeatures)
        if (liveRouteSignals.size < 2) return null

        return templates
            .asSequence()
            .mapNotNull { template ->
                val required = template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                if (required.isEmpty()) return@mapNotNull null
                val matched = required.intersect(liveFeatures)
                val matchedRouteSignals = routeSignals.intersect(matched)
                val score = matched.size.toDouble() / required.size.coerceAtLeast(1)
                if (matched.size < 3 || matchedRouteSignals.size < 2 || score < 0.25) return@mapNotNull null
                RideCardTemplateMatch(
                    template = template,
                    score = score,
                    matchedFeatures = matched.toList().sorted(),
                )
            }
            .maxByOrNull { match ->
                match.score + if (match.template.packageName?.equals(packageName, ignoreCase = true) == true) 0.08 else 0.0
            }
    } // open_all_family_fallback_0_1_94

""",
            "open_all_family_fallback_0_1_94",
        )

        packageMonitorTestFile.asFile.writeText(
            """package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"
    private val enabled = AppSettings(appEnabled = true, restrictToSelectedRideApps = true)

    @Test
    fun everyReportedPackageIsReleasedForUniversalReading() {
        listOf(
            ownPackage,
            "com.google.android.apps.nbu.files",
            "com.openai.chatgpt",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.maps",
            "com.waze",
            "br.com.tkx.taxi.drivermachine",
        ).forEach { packageName ->
            val classification = CorePackageMonitor.classify(packageName, ownPackage, enabled)
            assertTrue("Pacote deveria estar liberado: " + packageName, classification.canScan)
            assertEquals(CorePackageKind.RideApp, classification.kind)
        }
    }

    @Test
    fun knownRideAppsKeepTheirSpecializedModules() {
        assertEquals(CoreRideAppModule.NinetyNine, CorePackageMonitor.classify("com.app99.driver", ownPackage, enabled).module)
        assertEquals(CoreRideAppModule.Uber, CorePackageMonitor.classify("com.ubercab.driver", ownPackage, enabled).module)
        assertEquals(CoreRideAppModule.InDrive, CorePackageMonitor.classify("sinet.startup.indriver", ownPackage, enabled).module)
    }

    @Test
    fun unknownAppsUseUniversalModule() {
        val classification = CorePackageMonitor.classify("com.example.anyscreen", ownPackage, enabled)
        assertTrue(classification.canScan)
        assertEquals(CoreRideAppModule.Universal, classification.module)
    }

    @Test
    fun onlyTheMasterAppSwitchStopsReading() {
        val disabled = AppSettings(appEnabled = false)
        assertFalse(CorePackageMonitor.classify("com.google.android.apps.photos", ownPackage, disabled).canScan)
    }
}
// open_all_package_tests_0_1_94
""",
        )
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(openAllAppsAllScreensPatch)
}
