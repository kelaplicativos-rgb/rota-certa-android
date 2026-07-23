package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalIntegrationChecklist9Test {
    private fun source(name: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists)?.readText() ?: error("$name não encontrado")

    @Test
    fun accessiblePopupRemainsUsableWithFifteenModules() {
        val overlay = source("BubbleShortcutOverlayController.kt")

        assertTrue("popup grande precisa de ScrollView", "import android.widget.ScrollView" in overlay)
        assertTrue("altura precisa respeitar a tela", "maxMenuHeight" in overlay)
        assertTrue("altura visível precisa ser limitada", "visibleMenuHeight" in overlay)
        assertTrue("rolagem precisa ser condicional", "needsVerticalScroll" in overlay)
        assertTrue("barra vertical precisa permanecer disponível", "isVerticalScrollBarEnabled = true" in overlay)
    }

    @Test
    fun homeAndPinsAreResolvedBeforeTheFarolNeedsThem() {
        val main = source("MainActivity.kt")
        val service = source("LiveRideAccessibilityService.kt")

        assertTrue("Casa precisa ser validada ao salvar", "home_target_pre_resolved_checklist_9" in main)
        assertTrue("Casa precisa usar o resolvedor antes do farol", "workRegionAddressResolverChecklist9.resolve" in main)
        assertTrue("Casa precisa salvar pelo callback validado", "onSave = ::saveHomeAddressValidatedChecklist9" in main)
        assertTrue("Enter precisa salvar a Casa", "keyboardActions = KeyboardActions(" in main)

        val routeStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val routeEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", routeStart)
        assertTrue("bloco final da rota precisa existir", routeStart >= 0 && routeEnd > routeStart)
        val route = service.substring(routeStart, routeEnd)
        assertEquals(1, Regex("drivingDistancesFromAddressKm\\(").findAll(route).count())
        assertTrue("Casa e alfinetes precisam usar o motor final", "decideWorkRegion(" in route)
        assertFalse("não pode haver rota sequencial por alvo", "routeDistanceKm(" in route)
    }

    @Test
    fun manualTripCopyDoesNotOpenContinuousReadingOutsideSelectedRideApps() {
        val service = source("LiveRideAccessibilityService.kt")
        val manualStart = service.indexOf("private fun copyTripConfirmationFromBubbleChecklist8()")
        val manualEnd = service.indexOf("private fun open", manualStart)
        assertTrue("fluxo manual precisa existir", manualStart >= 0 && manualEnd > manualStart)
        val manual = service.substring(manualStart, manualEnd)

        assertTrue("leitura manual própria ausente", "collectTripConfirmationVisibleTextChecklist8()" in manual)
        assertTrue("marcador da leitura manual ausente", "manual_trip_tree_read_checklist_8" in manual)
        assertFalse("não pode depender da portaria do farol", "collectVisibleTextForAction()" in manual)
        assertFalse("não pode salvar conversa", "repository." in manual)
        assertFalse("não pode enviar automaticamente", "startActivity(" in manual)

        val scanStart = service.indexOf("private fun startContinuousScan()")
        val alertStart = service.indexOf("private fun startProximityAlertMonitor()", scanStart)
        assertTrue(scanStart >= 0 && alertStart > scanStart)
        val scan = service.substring(scanStart, alertStart)
        assertFalse("confirmação não pode entrar no loop", "TripConfirmationFormatter" in scan)
    }

    @Test
    fun finalPopupCatalogHasOnlyTheIntendedOperationalModules() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }

        assertEquals(15, ids.size)
        assertTrue("Copiar viagem ausente", "copy_trip_confirmation" in ids)
        assertTrue("Respostas rápidas ausentes", "quick_replies" in ids)
        assertFalse("Leitura deve ficar em Controles gerais", "reading" in ids)
        assertFalse("Permissão deve ficar em Controles gerais", "permissions" in ids)
        assertEquals(ids.size, ids.distinct().size)
    }
}
