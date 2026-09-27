package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedPlaceShareLink0665Test {
    private val source by lazy {
        File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    }

    @Test
    fun sharePayloadContainsNameAddressAndClickableHttpsMapsLink() {
        val helpers = source.substringAfter("private fun savedPlaceGoogleMapsLink0665")
            .substringBefore("private fun openSavedPlaceInGps")

        assertTrue(helpers.contains("https://www.google.com/maps/search/?api=1&query=\$latitude,\$longitude"))
        assertTrue(helpers.contains("val address = savedPlaceAddressText0664(place)"))
        assertTrue(helpers.contains("val mapsLink = savedPlaceGoogleMapsLink0665(place)"))
        assertTrue(helpers.contains("return \"📍 \$name\\n\$address\\n\$mapsLink\""))
        assertTrue(helpers.contains("putExtra(Intent.EXTRA_TEXT, payload)"))
        assertTrue(helpers.contains("ClipData.newPlainText(\"Local\", payload)"))
        assertFalse(helpers.contains("putExtra(Intent.EXTRA_TEXT, \"\$name\\n\$address\")"))
    }

    @Test
    fun copyAndSendUseTheSameSinglePayload() {
        val copy = source.substringAfter("private fun copySavedPlace0665")
            .substringBefore("private fun shareSavedPlace0665")
        val share = source.substringAfter("private fun shareSavedPlace0665")
            .substringBefore("private fun openSavedPlaceInGps")

        assertTrue(copy.contains("val payload = savedPlaceShareText0665(place)"))
        assertTrue(share.contains("val payload = savedPlaceShareText0665(place)"))
        assertTrue(copy.contains("\"Local copiado com link\""))
        assertTrue(share.contains("Intent.createChooser(sendIntent, \"Enviar local\")"))
    }

    @Test
    fun actionRowIsCompactAndNeverWrapsLabels() {
        val block = source.substringAfter("private fun SavedPlaceSearchResult138(place: SavedPlace)")
            .substringBefore("@Composable\nprivate fun SavedPlaceEditor")

        assertEquals(3, Regex("""Modifier\.weight\(1f\)\.height\(44\.dp\)""").findAll(block).count())
        assertEquals(3, Regex("""contentPadding = PaddingValues\(horizontal = 6\.dp, vertical = 0\.dp\)""").findAll(block).count())
        assertEquals(3, Regex("""maxLines = 1, softWrap = false""").findAll(block).count())
        assertTrue(block.contains("horizontalArrangement = Arrangement.spacedBy(6.dp)"))
    }
}
