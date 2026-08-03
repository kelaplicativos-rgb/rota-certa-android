from pathlib import Path

ROOT = Path.cwd().resolve()
path = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupContract0181Test.kt"
path.write_text(
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacePopupContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun placeAndAlertUseFocusableOverlayWithoutOpeningRotaCerta() {
        assertTrue(service.contains("private fun showSavePlacePopup"))
        assertTrue(service.contains("showSavePlacePopup(coordinate, resolved.addressLine, type)"))
        assertTrue(service.contains("WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(service.contains("Endereco completo"))
        assertTrue(service.contains("Digite um nome ou salve vazio para usar Local salvo."))
        assertTrue(service.contains("Nome do alerta"))
        assertTrue(service.contains("text = \"Cancelar\""))
        assertTrue(service.contains("text = \"Salvar\""))

        val popupStart = service.indexOf("private fun showSavePlacePopup")
        val popupEnd = service.indexOf("private fun hideSavedPlacePopup", popupStart)
        val popupCode = service.substring(popupStart, popupEnd)
        assertFalse(popupCode.contains("startActivity("))
        assertFalse(popupCode.contains("FLAG_NOT_FOCUSABLE"))
    }

    @Test
    fun saveKeepsTheExternalScreenAndPersistsOnlyAfterConfirmation() {
        assertTrue(service.contains("Local salvo pela bolinha sem sair da tela atual."))
        assertTrue(service.contains("Alerta de proximidade salvo pela bolinha sem sair da tela atual."))
        assertTrue(service.contains("setOnClickListener { hideSavedPlacePopup() }"))
        assertTrue(service.contains("repository.addSavedPlace(place)"))
    }
}
''',
    encoding="utf-8",
)
