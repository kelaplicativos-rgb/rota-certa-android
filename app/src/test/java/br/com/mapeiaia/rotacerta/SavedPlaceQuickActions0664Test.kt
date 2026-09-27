package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedPlaceQuickActions0664Test {
    private val source by lazy {
        File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    }

    @Test
    fun savedPlaceSearchResultStillExposesExactlyGpsCopyAndSendActions() {
        val block = source.substringAfter("private fun SavedPlaceSearchResult138(place: SavedPlace)")
            .substringBefore("@Composable\nprivate fun SavedPlaceEditor")

        assertTrue(block.contains("savedPlaceAddressText0664(place)"))
        assertTrue(block.contains("copySavedPlace0665(context, place)"))
        assertTrue(block.contains("shareSavedPlace0665(context, place)"))
        assertEquals(1, Regex("""Text\("GPS",""").findAll(block).count())
        assertEquals(1, Regex("""Text\("Copiar",""").findAll(block).count())
        assertEquals(1, Regex("""Text\("Enviar",""").findAll(block).count())
        assertEquals(3, Regex("""Modifier\.weight\(1f\)\.height\(44\.dp\)""").findAll(block).count())
    }

    @Test
    fun legacySavedPlaceWithoutAddressStillFallsBackToCoordinates() {
        val block = source.substringAfter("private fun savedPlaceAddressText0664")
            .substringBefore("private fun savedPlaceGoogleMapsLink0665")

        assertTrue(block.contains("place.address.trim().ifBlank { formatCoordinate(place.coordinate) }"))
    }
}
