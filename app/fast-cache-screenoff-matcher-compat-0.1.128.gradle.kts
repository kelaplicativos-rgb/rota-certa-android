// Compatibilidade estrutural para o matcher gerado antes do patch final 0.1.128.
// Localiza o ramo por estrutura de chaves, sem depender da formatacao produzida
// por patches anteriores.

fun patchInDriveFamilyMatcherCompat128(matcherFile: java.io.File) {
    if (!matcherFile.exists()) throw GradleException("RideCardTemplateMatcher.kt ausente na compatibilidade 0.1.128.")
    var matcher = matcherFile.readText()
    if ("indrive_same_package_family_match_0_1_128" in matcher) return

    val universalBranchStart128 = matcher.indexOf("if (universalPackage) {")
    if (universalBranchStart128 < 0) throw GradleException("Ramo universal do match nao encontrado na compatibilidade 0.1.128.")
    val elseStart128 = matcher.indexOf("} else {", universalBranchStart128)
    if (elseStart128 < 0) throw GradleException("Ramo de pacote especifico nao encontrado na compatibilidade 0.1.128.")
    val elseBrace128 = matcher.indexOf('{', elseStart128)
    if (elseBrace128 < 0) throw GradleException("Chave do ramo especifico nao encontrada na compatibilidade 0.1.128.")

    var depth128 = 0
    var elseEnd128 = -1
    var cursor128 = elseBrace128
    while (cursor128 < matcher.length) {
        when (matcher[cursor128]) {
            '{' -> depth128 += 1
            '}' -> {
                depth128 -= 1
                if (depth128 == 0) {
                    elseEnd128 = cursor128
                    break
                }
            }
        }
        cursor128 += 1
    }
    if (elseEnd128 < 0) throw GradleException("Fim do ramo especifico nao encontrado na compatibilidade 0.1.128.")

    val lineStart128 = matcher.lastIndexOf('\n', elseStart128).let { if (it < 0) 0 else it + 1 }
    val indentation128 = matcher.substring(lineStart128, elseStart128)
    val replacement128 = """} else {
${indentation128}    val standardSamePackageMatch128 = samePackage &&
${indentation128}        cropOk &&
${indentation128}        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
${indentation128}        match.score >= MIN_SCORE &&
${indentation128}        match.matchedFeatures.size >= MIN_FEATURES
${indentation128}    val inDriveFamilySignals128 = listOf(
${indentation128}        "pedido de viagem",
${indentation128}        "pedidos de viagem",
${indentation128}        "aceitar por",
${indentation128}        "ofereca sua tarifa",
${indentation128}        "preco justo",
${indentation128}    ).count { signal -> signal in liveFeatures }
${indentation128}    val inDriveSamePackageFamily128 = samePackage &&
${indentation128}        normalizedPackage == INDRIVE_PACKAGE &&
${indentation128}        "card.crop.route_block" in liveFeatures &&
${indentation128}        "card.route.two_addresses" in liveFeatures &&
${indentation128}        inDriveFamilySignals128 >= 2 &&
${indentation128}        match.matchedFeatures.size >= 3
${indentation128}    standardSamePackageMatch128 || inDriveSamePackageFamily128 // indrive_same_package_family_match_0_1_128
${indentation128}}"""

    matcher = matcher.substring(0, elseStart128) + replacement128 + matcher.substring(elseEnd128 + 1)
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
