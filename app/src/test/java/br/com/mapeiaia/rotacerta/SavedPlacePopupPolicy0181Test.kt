package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlacePopupPolicy0181Test {
    @Test
    fun blankPlaceNameUsesLocalSalvo() {
        assertEquals(
            "Local salvo",
            SavedPlacePopupPolicy0181.savedName("   ", SavedPlaceType.Place),
        )
    }

    @Test
    fun blankAlertNameUsesAlertDefault() {
        assertEquals(
            "Alerta de proximidade",
            SavedPlacePopupPolicy0181.savedName("", SavedPlaceType.ProximityAlert),
        )
    }

    @Test
    fun typedNameIsTrimmedAndPreserved() {
        assertEquals(
            "Casa da Ana",
            SavedPlacePopupPolicy0181.savedName("  Casa da Ana  ", SavedPlaceType.Place),
        )
    }

    @Test
    fun fullResolvedAddressIsPreferred() {
        assertEquals(
            "Rod. Fernao Dias, 850 - Jardim Fernandao, Pouso Alegre - MG, 37550-000",
            SavedPlacePopupPolicy0181.displayAddress(
                "  Rod. Fernao Dias, 850 - Jardim Fernandao, Pouso Alegre - MG, 37550-000  ",
                "-22.00000, -45.00000",
            ),
        )
    }
}
