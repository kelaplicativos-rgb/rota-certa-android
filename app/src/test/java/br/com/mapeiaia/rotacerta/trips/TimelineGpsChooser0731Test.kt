package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimelineGpsChooser0731Test {
    private fun uiSource(): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

    @Test
    fun manualPassengerUsesSelectedTripStopsAsNavigationTargets() {
        val row = EnhancedPassengerCardRow(
            name = "Manual",
            phone = null,
            seats = 1,
            boarding = "Rodoviária de Três Corações",
            dropoff = "Posto Marins",
            sources = setOf(BookingSource.PRIVATE),
        )

        val pickup = assertNotNull(passengerPickupMapTarget(row))
        val dropoff = assertNotNull(passengerDropoffMapTarget(row))

        assertEquals("Rodoviária de Três Corações", pickup.query)
        assertEquals("Posto Marins", dropoff.query)
    }

    @Test
    fun navigationUsesAndroidChooserAndNeverForcesGoogleMaps() {
        val source = uiSource()
        val start = source.indexOf("private fun passengerNavigationChooser0731(")
        val end = source.indexOf("private fun openExternalPassengerBlaBla(", start)
        assertTrue(start >= 0 && end > start)
        val navigationBlock = source.substring(start, end)

        assertTrue(navigationBlock.contains("Intent.createChooser"))
        assertTrue(navigationBlock.contains("Intent(Intent.ACTION_VIEW, passengerMapUri0513(target))"))
        assertTrue(navigationBlock.contains("Escolher app de navegação"))
        assertFalse(navigationBlock.contains("setPackage("))
        assertFalse(navigationBlock.contains("com.google.android.apps.maps"))
    }

    @Test
    fun timelinePickupAndDropoffShortcutsDoNotFallbackToAddressEditor() {
        val source = uiSource()

        assertTrue(source.contains("openPassengerPickupShortcut0731"))
        assertTrue(source.contains("openPassengerDropoffShortcut0731"))
        assertFalse(source.contains("else boardingAddressEditRow ="))
        assertFalse(source.contains("else dropoffAddressEditRow ="))
        assertTrue(source.contains("onClick = { openPassengerPickupShortcut0731(passenger) }"))
        assertTrue(source.contains("onClick = { openPassengerDropoffShortcut0731(passenger) }"))
    }
}
