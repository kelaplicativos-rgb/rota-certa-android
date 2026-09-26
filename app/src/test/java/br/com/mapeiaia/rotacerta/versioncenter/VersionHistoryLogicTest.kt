package br.com.mapeiaia.rotacerta.versioncenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionHistoryLogicTest {
    @Test
    fun historyIsSortedNewestFirst() {
        val releases = listOf(
            release("0.1.548", 5840),
            release("0.1.550", 5842),
            release("0.1.549", 5841),
        )

        assertEquals(
            listOf("0.1.550", "0.1.549", "0.1.548"),
            VersionHistoryLogic.sortedReleases(releases).map { it.version },
        )
    }

    @Test
    fun moduleFilterIsDynamicAndCaseInsensitive() {
        val document = ReleaseHistoryDocument(
            releases = listOf(
                release("0.1.550", 5842, listOf("Sobre")),
                release("0.1.549", 5841, listOf("Timeline", "Agenda")),
            ),
        )

        assertEquals(listOf("Agenda", "Sobre", "Timeline"), VersionHistoryLogic.availableModules(document))
        assertEquals(
            listOf("0.1.549"),
            VersionHistoryLogic.filterByModule(document.releases, "timeline").map { it.version },
        )
    }

    @Test
    fun fixedAndValidatedRemainDistinct() {
        val regression = RegressionRecord(
            regressionId = "reg.timeline.example",
            title = "Exemplo",
            description = "Fixture de teste",
            introducedIn = "0.1.547",
            fixedIn = "0.1.549",
            validatedIn = "0.1.550",
            affectedModules = listOf("Timeline"),
            status = "VALIDATED",
        )

        assertEquals(
            "Introduzida em 0.1.547 → corrigida em 0.1.549 → validada em 0.1.550",
            VersionHistoryLogic.lifecycle(regression),
        )
    }

    @Test
    fun fixedButNotValidatedRegressionRemainsInKnownProblems() {
        val regression = RegressionRecord(
            regressionId = "reg.reading.example",
            title = "Exemplo",
            description = "Fixture de teste",
            introducedIn = "0.1.548",
            fixedIn = "0.1.550",
            validatedIn = null,
            affectedModules = listOf("Leitura"),
            status = "FIXED",
        )
        val document = ReleaseHistoryDocument(regressions = listOf(regression))

        assertEquals(listOf(regression), VersionHistoryLogic.activeRegressions(document, "0.1.550"))
    }

    @Test
    fun validatedRegressionLeavesKnownProblemsButStaysInHistory() {
        val regression = RegressionRecord(
            regressionId = "reg.alerts.example",
            title = "Exemplo",
            description = "Fixture de teste",
            introducedIn = "0.1.548",
            fixedIn = "0.1.549",
            validatedIn = "0.1.550",
            affectedModules = listOf("Alertas"),
            status = "VALIDATED",
        )
        val document = ReleaseHistoryDocument(regressions = listOf(regression))

        assertTrue(VersionHistoryLogic.activeRegressions(document, "0.1.549").isNotEmpty())
        assertTrue(VersionHistoryLogic.activeRegressions(document, "0.1.550").isEmpty())
        assertTrue(document.regressions.contains(regression))
    }

    @Test
    fun copiedReportMasksSensitivePatterns() {
        val unsafe = ReleaseRecord(
            version = "0.1.550",
            build = 5842,
            commit = "abcdef",
            branch = "agent/test",
            generatedAt = "12/09/2026 19:00",
            status = "EM_VALIDACAO",
            implemented = listOf(
                "Contato teste@example.com",
                "telefone +55 11 99876-5432",
                "token:segredo",
            ),
        )

        val report = VersionReportBuilder.build(unsafe, emptyList())

        assertFalse(report.contains("teste@example.com"))
        assertFalse(report.contains("99876-5432"))
        assertFalse(report.contains("segredo"))
        assertTrue(report.contains("[email mascarado]"))
        assertTrue(report.contains("[telefone mascarado]"))
        assertTrue(report.contains("[dado sensível mascarado]"))
    }

    private fun release(version: String, build: Int, modules: List<String> = emptyList()) =
        ReleaseRecord(
            version = version,
            build = build,
            modulesAffected = modules,
        )
}
