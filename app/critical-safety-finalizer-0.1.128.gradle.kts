// Rota Certa 0.1.128 — finalizador de seguranca da etapa critica.
// Deve ser aplicado por ultimo no build principal, depois do patch de tela bloqueada.
//
// Impede quatro regressoes:
// 1) a protecao especifica do keyguard nao amplia a tolerancia GLOBAL;
// 2) a captura automatica nao ocupa a mesma trava usada pelo OCR;
// 3) patches legados nao removem o match obrigatorio do mesmo pacote;
// 4) testes gerados nao voltam a exigir match entre aplicativos diferentes.

fun replaceFinalFunction128(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao final ausente: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo final ausente: $signature")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao final ausente: $signature")
}

fun patchCriticalSafetyFinalizer128(
    serviceFile: java.io.File,
    guardFile: java.io.File,
    matcherFile: java.io.File,
    matcherTestFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no finalizador 0.1.128.")
    if (!guardFile.exists()) throw GradleException("UniversalRuntimeGuards.kt ausente no finalizador 0.1.128.")
    if (!matcherFile.exists()) throw GradleException("RideCardTemplateMatcher.kt ausente no finalizador 0.1.128.")
    if (!matcherTestFile.exists()) throw GradleException("RideCardTemplateMatcherTest.kt ausente no finalizador 0.1.128.")

    var guard = guardFile.readText()
    guard = guard.replace(
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 12_000L // locked_popup_grace_0_1_128",
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 2_500L // global_route_grace_restored_0_1_128",
    )
    if ("const val ROUTE_INFLIGHT_GRACE_MILLIS = 12_000L" in guard) {
        throw GradleException("A tolerancia global de rota ainda foi ampliada para 12 segundos.")
    }
    if ("global_route_grace_restored_0_1_128" !in guard) {
        throw GradleException("A tolerancia global original nao foi restaurada.")
    }
    guardFile.writeText(guard)

    var service = serviceFile.readText()
    if ("automatic_capture_independent_gate_0_1_128" !in service) {
        val fieldAnchor = "    private var lastAutomaticCaptureAtMillis128: Long = 0L\n"
        if (fieldAnchor !in service) throw GradleException("Campos da captura automatica nao foram encontrados.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + "    private val automaticCaptureInProgress128 = java.util.concurrent.atomic.AtomicBoolean(false) // automatic_capture_independent_gate_0_1_128\n",
        )

        val functionStart = service.indexOf("    private fun requestAutomaticRideCapture128(")
        val functionEnd = if (functionStart >= 0) {
            service.indexOf("    private fun universalResolvedForegroundPackage(): String?", functionStart)
        } else {
            -1
        }
        if (functionStart < 0 || functionEnd < 0) {
            throw GradleException("Funcao de captura automatica nao encontrada.")
        }
        var functionRegion = service.substring(functionStart, functionEnd)
        functionRegion = functionRegion
            .replace(
                "if (!screenshotInProgress.compareAndSet(false, true)) return",
                "if (!automaticCaptureInProgress128.compareAndSet(false, true)) return",
            )
            .replace("screenshotInProgress.set(false)", "automaticCaptureInProgress128.set(false)")
        if ("screenshotInProgress" in functionRegion) {
            throw GradleException("A captura automatica ainda disputa a trava do OCR.")
        }
        if ("automaticCaptureInProgress128" !in functionRegion) {
            throw GradleException("A trava independente nao foi ligada a captura automatica.")
        }
        service = service.substring(0, functionStart) + functionRegion + service.substring(functionEnd)
    }

    if ("screenshotInProgress" !in service) {
        throw GradleException("A trava original do OCR foi removida indevidamente.")
    }
    serviceFile.writeText(service)

    var matcher = matcherFile.readText()
    val matcherReplacement = """    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: UNIVERSAL_LEARNED_PACKAGE
        val liveFeatures = deterministicFeaturesFor(text)
        if (liveFeatures.size < 3) return null

        return templates
            .asSequence()
            .filter { template ->
                template.packageName?.trim()?.lowercase(Locale.ROOT) == normalizedPackage
            } // final_same_package_template_filter_0_1_128
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
                val required = match.template.requiredFeatures
                    .filterNot { it.startsWith("adaptive.") }
                    .toSet()
                val matched = match.matchedFeatures.toSet()
                val matchedStrict = strictCardFeatures.intersect(matched)
                val strongInDriveLockedPopup128 =
                    normalizedPackage == INDRIVE_PACKAGE &&
                        "card.contract.indrive_opened_single" in liveFeatures &&
                        ("card.contract.indrive_opened_single" in required ||
                            "card.contract.indrive_individual" in required) &&
                        "card.route.two_addresses" in liveFeatures &&
                        "valor em reais" in liveFeatures &&
                        ("aceitar por" in liveFeatures ||
                            "ofereca sua tarifa" in liveFeatures ||
                            "ofereça sua tarifa" in liveFeatures)
                val universalManualCrop128 =
                    normalizedPackage == UNIVERSAL_LEARNED_PACKAGE &&
                        "card.crop.route_block" in liveFeatures &&
                        "card.crop.route_block" in required &&
                        "card.route.marked_stops" in liveFeatures &&
                        "card.route.marked_stops" in required
                strongInDriveLockedPopup128 || // final_indrive_locked_popup_match_0_1_128
                    universalManualCrop128 || // final_universal_manual_crop_0_1_128
                    (match.matchedFeatures.size >= 3 &&
                        matchedStrict.size >= 2 &&
                        match.score >= 0.25)
            }
            .maxByOrNull { it.score }
    } // final_manual_same_package_matcher_0_1_128
"""
    matcher = replaceFinalFunction128(
        matcher,
        "    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch?",
        matcherReplacement,
    )
    listOf(
        "final_same_package_template_filter_0_1_128",
        "final_indrive_locked_popup_match_0_1_128",
        "final_universal_manual_crop_0_1_128",
        "final_manual_same_package_matcher_0_1_128",
        "indrive_locked_popup_two_addresses_0_1_128",
    ).forEach { marker ->
        if (marker !in matcher) throw GradleException("Matcher final 0.1.128 incompleto: $marker")
    }
    matcherFile.writeText(matcher)

    var matcherTest = matcherTestFile.readText()
    matcherTest = matcherTest
        .replace(
            "fun matchesRegisteredCardAcrossDifferentAppPackage() { // open_all_cross_package_test_0_1_94",
            "fun doesNotMatchDifferentRideAppPackage() { // final_same_package_test_0_1_128",
        )
        .replace(
            "assertNotNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template))) // open_all_cross_package_assert_0_1_94",
            "assertNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template))) // final_same_package_assert_0_1_128",
        )
        .replace(
            "assertNotNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template)))",
            "assertNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template))) // final_same_package_assert_0_1_128",
        )
    if ("matchesRegisteredCardAcrossDifferentAppPackage" in matcherTest) {
        throw GradleException("Teste legado ainda exige match entre aplicativos diferentes.")
    }
    if ("final_same_package_assert_0_1_128" !in matcherTest) {
        throw GradleException("Teste final de mesmo pacote nao foi restaurado.")
    }
    matcherTestFile.writeText(matcherTest)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchCriticalSafetyFinalizer128(
            serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            guardFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt").asFile,
            matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt").asFile,
            matcherTestFile = layout.projectDirectory.file("src/test/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcherTest.kt").asFile,
        )
    }
}
