package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerAdminSelectionPerformance0419Test {
    private val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()

    @Test
    fun passengerRowsStayCollapsedUntilExplicitSelection() {
        val start = ui.indexOf("visibleCandidates0420.forEach { candidate ->")
        val end = ui.indexOf("blockCandidate?.let", start)
        assertTrue(start >= 0 && end > start)
        val loop = ui.substring(start, end)
        assertTrue(loop.contains("val expanded0419 = selectedCandidateKey0419 == candidate.key"))
        assertTrue(loop.contains("selectedCandidateKey0419 = if (expanded0419) null else candidate.key"))
        assertTrue(loop.contains("Gerenciar passageiro"))
        val expanded = loop.indexOf("if (expanded0419) {")
        val accessField = loop.indexOf("label = { Text(\"WhatsApp de acesso\") }")
        assertTrue(expanded >= 0 && accessField > expanded)
        assertFalse(loop.contains("onClick = { openCandidateHistory(candidate) },\n            modifier = Modifier.fillMaxWidth(),\n        ) {"))
    }

    @Test
    fun selectingPassengerExposesAdminActionBeforeSecondaryAccessEditing() {
        val admin = ui.indexOf("Text(\"Administração da Agenda\"")
        val action = ui.indexOf("Definir como administrador", admin)
        val access = ui.indexOf("Text(\"Acesso à Agenda\"", admin)
        assertTrue(admin >= 0 && action > admin)
        assertTrue(access > action)
        assertTrue(ui.contains("Este passageiro ainda não possui acesso online a Minhas Viagens"))
        assertTrue(ui.contains("Você pode salvar a permissão agora"))
    }

    @Test
    fun passengerListIsPagedToAvoidEagerlyComposingTheEntireDatabase() {
        assertTrue(ui.contains("var visibleCandidateLimit0420 by remember(search) { mutableIntStateOf(24) }"))
        assertTrue(ui.contains("candidates.take(visibleCandidateLimit0420)"))
        assertTrue(ui.contains("Mostrar mais passageiros"))
        assertFalse(ui.contains("\n    candidates.forEach { candidate ->"))
    }

    @Test
    fun openingHistoryAndResolvingAdminIdentityStayOffMainThread() {
        val historyStart = ui.indexOf("fun openCandidateHistory")
        val historyEnd = ui.indexOf("if (showHeader)", historyStart)
        val history = ui.substring(historyStart, historyEnd)
        assertTrue(history.contains("withContext(Dispatchers.IO) { canonicalProfile(candidate) }"))
        assertTrue(ui.contains("withContext(Dispatchers.IO) { passengerStore.persistentHistory(id) }"))

        val adminStart = ui.indexOf("Text(\"Administração da Agenda\"")
        val adminEnd = ui.indexOf("access?.referredByContact", adminStart)
        val adminBlock = ui.substring(adminStart, adminEnd)
        assertTrue(adminBlock.contains("withContext(Dispatchers.IO) { canonicalProfile(candidate) }"))
    }
}
