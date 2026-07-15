// Ajustes de regressao para a liberacao universal:
// - preserva bloqueio de feeds com varias ofertas;
// - evita que mapas/telas estruturais fracas virem card;
// - atualiza testes que ainda exigiam trava por pacote ou crop;
// - torna o patch legado do inDrive idempotente entre test e assemble.

val openAllAppsAllScreensFixPatch by tasks.registering {
    val sourceRoot = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta")
    val testRoot = layout.projectDirectory.dir("src/test/java/br/com/mapeiaia/rotacerta")
    val cardMatchEngineFile = sourceRoot.file("core/CoreCardMatchEngine.kt")
    val templateMatcherFile = sourceRoot.file("RideCardTemplateMatcher.kt")
    val matcherTestFile = testRoot.file("RideCardTemplateMatcherTest.kt")
    val contractTestFile = testRoot.file("core/CoreRideCardContractsTest.kt")
    val legacyInDrivePatchFile = layout.projectDirectory.file("patch-indrive-card-contract-match.gradle.kts")

    inputs.files(cardMatchEngineFile, templateMatcherFile, matcherTestFile, contractTestFile, legacyInDrivePatchFile)
    outputs.upToDateWhen { false }

    fun replaceRequired(file: java.io.File, old: String, replacement: String, marker: String) {
        var text = file.readText()
        if (marker in text) return
        if (old !in text) throw GradleException("Nao encontrei trecho para aplicar $marker em ${file.name}.")
        text = text.replace(old, replacement)
        file.writeText(text)
    }

    doLast {
        replaceRequired(
            cardMatchEngineFile.asFile,
            "        if (isListLikeRideFeed(preparedText, normalizedText)) {\n",
            "        if (isListLikeRideFeed(text, text.normalizedCoreText()) || isListLikeRideFeed(preparedText, normalizedText)) { // open_all_raw_feed_guard_0_1_94\n",
            "open_all_raw_feed_guard_0_1_94",
        )

        replaceRequired(
            templateMatcherFile.asFile,
            "                    (matchedStructural.size >= 2 || matchedStrict.isNotEmpty()) &&\n",
            "                    matchedStrict.size >= 2 && // open_all_strong_visual_signature_0_1_94\n",
            "open_all_strong_visual_signature_0_1_94",
        )

        replaceRequired(
            matcherTestFile.asFile,
            "    fun doesNotMatchDifferentRideAppPackage() {\n",
            "    fun matchesRegisteredCardAcrossDifferentAppPackage() { // open_all_cross_package_test_0_1_94\n",
            "open_all_cross_package_test_0_1_94",
        )
        replaceRequired(
            matcherTestFile.asFile,
            "        assertNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template)))\n",
            "        assertNotNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template)))\n",
            "open_all_cross_package_assert_0_1_94",
        )
        var matcherTest = matcherTestFile.asFile.readText()
        if ("open_all_cross_package_assert_0_1_94" !in matcherTest) {
            matcherTest = matcherTest.replace(
                "        assertNotNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template)))\n",
                "        assertNotNull(RideCardTemplateMatcher.match(sample, \"com.app99.driver\", listOf(template))) // open_all_cross_package_assert_0_1_94\n",
            )
            matcherTestFile.asFile.writeText(matcherTest)
        }

        replaceRequired(
            contractTestFile.asFile,
            "    fun inDriveRejectsWithoutRouteBlock() {\n",
            "    fun inDriveAcceptsRegisteredRouteWithoutLegacyCropFlag() { // open_all_contract_test_0_1_94\n",
            "open_all_contract_test_0_1_94",
        )
        replaceRequired(
            contractTestFile.asFile,
            "        assertFalse(result.accepted)\n        assertFalse(result.isListLike)\n        assertTrue(result.reason.contains(\"sem bloco individual\"))\n",
            "        assertTrue(result.accepted)\n        assertFalse(result.isListLike)\n",
            "open_all_contract_assert_0_1_94",
        )
        var contractTest = contractTestFile.asFile.readText()
        if ("open_all_contract_assert_0_1_94" !in contractTest) {
            contractTest = contractTest.replace(
                "        assertTrue(result.accepted)\n        assertFalse(result.isListLike)\n",
                "        assertTrue(result.accepted) // open_all_contract_assert_0_1_94\n        assertFalse(result.isListLike)\n",
            )
            contractTestFile.asFile.writeText(contractTest)
        }

        var legacyPatch = legacyInDrivePatchFile.asFile.readText()
        if ("open_all_legacy_indrive_idempotence_0_1_94" !in legacyPatch) {
            val target = "        val original = text\n"
            if (target !in legacyPatch) {
                throw GradleException("Nao encontrei ponto para tornar o patch legado inDrive idempotente.")
            }
            legacyPatch = legacyPatch.replaceFirst(
                target,
                target + "\n        if (\"open_all_template_matcher_0_1_94\" in text) return@doLast // open_all_legacy_indrive_idempotence_0_1_94\n",
            )
            legacyInDrivePatchFile.asFile.writeText(legacyPatch)
        }
    }
}

openAllAppsAllScreensFixPatch.configure {
    mustRunAfter("openAllAppsAllScreensPatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(openAllAppsAllScreensFixPatch)
}
