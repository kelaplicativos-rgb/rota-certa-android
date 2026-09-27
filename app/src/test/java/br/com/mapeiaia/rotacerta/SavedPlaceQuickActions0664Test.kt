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
    fun savedPlaceSearchResultExposesExactlyGpsCopyAndSendActions() {
        val block = source.substringAfter("private fun SavedPlaceSearchResult138(place: SavedPlace)")
            .substringBefore("@Composable\nprivate fun SavedPlaceEditor")

        assertTrue(block.contains("savedPlaceAddressText0664(place)"))
        assertTrue(block.contains("copySavedPlaceAddress0664(context, place)"))
        assertTrue(block.contains("shareSavedPlace0664(context, place)"))
        assertEquals(1, Regex("""Text\("GPS"\)""").findAll(block).count())
        assertEquals(1, Regex("""Text\("Copiar"\)""").findAll(block).count())
        assertEquals(1, Regex("""Text\("Enviar"\)""").findAll(block).count())
        assertEquals(3, Regex("""modifier = Modifier\.weight\(1f\)""").findAll(block).count())
    }

    @Test
    fun copyWritesOnlyAddressAndShowsShortConfirmation() {
        val block = source.substringAfter("private fun copySavedPlaceAddress0664")
            .substringBefore("private fun shareSavedPlace0664")

        assertTrue(block.contains("val address = savedPlaceAddressText0664(place)"))
        assertTrue(block.contains("clipboard.setPrimaryClip(ClipData.newPlainText(\"Endereço\", address))"))
        assertTrue(block.contains("\"Endereço copiado\""))
    }

    @Test
    fun sendUsesNativeAndroidChooserWithNameAndAddress() {
        val block = source.substringAfter("private fun shareSavedPlace0664")
            .substringBefore("private fun openSavedPlaceInGps")

        assertTrue(block.contains("Intent(Intent.ACTION_SEND)"))
        assertTrue(block.contains("type = \"text/plain\""))
        assertTrue(block.contains("putExtra(Intent.EXTRA_TEXT, \"\$name\\n\$address\")"))
        assertTrue(block.contains("Intent.createChooser(sendIntent, \"Enviar local\")"))
    }

    @Test
    fun legacySavedPlaceWithoutAddressFallsBackToCoordinates() {
        val block = source.substringAfter("private fun savedPlaceAddressText0664")
            .substringBefore("private fun copySavedPlaceAddress0664")

        assertTrue(block.contains("place.address.trim().ifBlank { formatCoordinate(place.coordinate) }"))
    }
}
