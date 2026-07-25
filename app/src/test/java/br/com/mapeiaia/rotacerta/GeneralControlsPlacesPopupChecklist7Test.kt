package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralControlsPlacesPopupChecklist7Test {
    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists) ?: error("$name não encontrado")

    private fun place(
        id: String,
        name: String,
        type: SavedPlaceType = SavedPlaceType.Place,
        address: String = name,
    ) = SavedPlace(
        id = id,
        name = name,
        type = type,
        address = address,
        coordinate = Coordinate(-23.0, -46.0),
    )

    @Test
    fun highlightedSavedPlaceStartsEmptyButAlertKeepsSuggestedName() {
        val savedPlace = place("1", "Local salvo")
        val alert = place("2", "Alerta", SavedPlaceType.ProximityAlert)

        assertEquals("", SavedPlaceUiPolicy.initialDraftName(savedPlace, highlighted = true))
        assertEquals("Local salvo", SavedPlaceUiPolicy.initialDraftName(savedPlace, highlighted = false))
        assertEquals("Alerta", SavedPlaceUiPolicy.initialDraftName(alert, highlighted = true))
        assertFalse(SavedPlaceUiPolicy.canSave(savedPlace, "   "))
        assertTrue(SavedPlaceUiPolicy.canSave(alert, "   "))
    }

    @Test
    fun savedPlacesAreSortedAlphabeticallyIgnoringAccentsAndCase() {
        val sorted = SavedPlaceUiPolicy.sortedByName(
            listOf(
                place("3", "Zoológico"),
                place("2", "Água Branca"),
                place("1", "avenida Central"),
            ),
        )

        assertEquals(listOf("Água Branca", "avenida Central", "Zoológico"), sorted.map { it.name })
    }

    @Test
    fun popupScaleHasAccessibleRangeWithoutTouchingFarolSettings() {
        assertEquals(0.90, PopupAppearanceStore.MIN_SCALE, 0.0001)
        assertEquals(1.60, PopupAppearanceStore.MAX_SCALE, 0.0001)
        assertEquals(1.00, PopupAppearanceStore.DEFAULT_SCALE, 0.0001)
    }

    @Test
    fun generatedUiMovesReadingAndPermissionAndSupportsKeyboardDone() {
        val main = sourceFile("MainActivity.kt").readText()
        val catalog = sourceFile("BubbleShortcutModule.kt").readText()
        val overlay = sourceFile("BubbleShortcutOverlayController.kt").readText()
        val savedPlaceModule = sourceFile("SavedPlaceBubbleShortcutModule.kt").readText()

        assertTrue("leitura deve estar em controles gerais", "general_controls_final_checklist_7" in main)
        assertTrue("permissão deve estar junto aos controles", "Permissão de acessibilidade" in main)
        assertTrue("Enter deve salvar o nome", "KeyboardActions(onDone = { saveName() })" in main)
        assertTrue("lista deve usar ordenação alfabética", "SavedPlaceUiPolicy.sortedByName" in main)
        assertTrue("aparência deve expor escala", "popup_scale_ui_final_checklist_7" in main)
        assertFalse("leitura não pode continuar no popup", "ReadingBubbleShortcutModule," in catalog)
        assertFalse("permissão não pode continuar no popup", "PermissionsBubbleShortcutModule," in catalog)
        assertTrue("popup grande deve trocar para duas colunas", "LARGE_SCALE_TWO_COLUMNS" in overlay)
        assertTrue("novo local precisa nascer sem nome", "defaultName = \"\"" in savedPlaceModule)
    }
}
