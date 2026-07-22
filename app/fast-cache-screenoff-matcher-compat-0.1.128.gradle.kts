// Compatibilidade final para o matcher gerado antes do patch 0.1.128.
// Outros patches historicos podem reescrever internamente a funcao match. Em vez
// de depender da formatacao intermediaria, esta etapa substitui somente a funcao
// publica por uma versao final, deterministica e restrita ao modelo do mesmo app.

fun replaceMatcherFunction128(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao match nao encontrada na compatibilidade 0.1.128.")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo da funcao match nao encontrado na compatibilidade 0.1.128.")
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
    throw GradleException("Fim da funcao match nao encontrado na compatibilidade 0.1.128.")
}

fun patchInDriveFamilyMatcherCompat128(matcherFile: java.io.File) {
    if (!matcherFile.exists()) throw GradleException("RideCardTemplateMatcher.kt ausente na compatibilidade 0.1.128.")
    var matcher = matcherFile.readText()
    if ("indrive_same_package_family_match_0_1_128" in matcher) return

    val replacement = """    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val liveFeatures = deterministicFeaturesFor(text)
        if ("card.crop.route_block" !in liveFeatures) return null
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
                RideCardTemplateMatch(
                    template = template,
                    score = score,
                    matchedFeatures = matched.toList().sorted(),
                )
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
                val requiredCardFeatures = required.filter { it.startsWith("card.") }.toSet()
                val requiredStructuralFeatures = structuralFeatures.intersect(required)
                val structuralOk = requiredStructuralFeatures.all { it in match.matchedFeatures }
                val cropOk = "card.crop.route_block" in match.matchedFeatures &&
                    requiredCardFeatures.filter { it in strictCardFeatures }.all { it in match.matchedFeatures }
                val standardSamePackageMatch128 = samePackage &&
                    cropOk &&
                    (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                    match.score >= 0.72 &&
                    match.matchedFeatures.size >= 4
                val inDriveFamilySignals128 = listOf(
                    "pedido de viagem",
                    "pedidos de viagem",
                    "aceitar por",
                    "ofereca sua tarifa",
                    "preco justo",
                ).count { signal -> signal in liveFeatures }
                val inDriveSamePackageFamily128 = samePackage &&
                    normalizedPackage == INDRIVE_PACKAGE &&
                    "card.crop.route_block" in liveFeatures &&
                    "card.route.two_addresses" in liveFeatures &&
                    inDriveFamilySignals128 >= 2 &&
                    match.matchedFeatures.size >= 3
                if (universalPackage) {
                    looksLikeLearnableRideCard(text) &&
                        cropOk &&
                        match.score >= 0.82 &&
                        match.matchedFeatures.size >= required.size.coerceAtMost(4).coerceAtLeast(4)
                } else {
                    standardSamePackageMatch128 || inDriveSamePackageFamily128 // indrive_same_package_family_match_0_1_128
                }
            }
            .maxByOrNull { it.score }
    }
"""

    matcher = replaceMatcherFunction128(
        matcher,
        "    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch?",
        replacement,
    )
    if ("indrive_same_package_family_match_0_1_128" !in matcher) {
        throw GradleException("Compatibilidade do matcher 0.1.128 nao foi aplicada.")
    }
    matcherFile.writeText(matcher)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchInDriveFamilyMatcherCompat128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt").asFile,
        )
    }
}
