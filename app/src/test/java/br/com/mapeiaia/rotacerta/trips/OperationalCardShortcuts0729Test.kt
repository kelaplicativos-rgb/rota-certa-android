package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalCardShortcuts0729Test {
    private val operationalSource =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

    @Test
    fun activeViagensSurfaceRendersBothRequestedShortcutsInCardHeader() {
        assertTrue(operationalSource.contains("TIMELINE_MANUAL_PASSENGER_SHORTCUT_0728"))
        assertTrue(operationalSource.contains("TIMELINE_PUBLIC_BLABLACAR_SHORTCUT_0728"))
        assertTrue(operationalSource.contains("contentDescription = \"Adicionar passageiro por fora\""))
        assertTrue(operationalSource.contains("contentDescription = \"Ver anúncio público na BlaBlaCar\""))
        assertTrue(operationalSource.contains("OperationalTripBrowserCard0563("))
    }

    @Test
    fun manualShortcutUsesCanonicalPassengerDialogFromOperationalCard() {
        assertTrue(operationalSource.contains("openManualPassenger0729"))
        assertTrue(operationalSource.contains("prepareTimelineTripForPassenger(entry, store0654)"))
        assertTrue(operationalSource.contains("TimelineCardQuickPassengerDialog("))
        assertTrue(operationalSource.contains("canonicalBookings0494 = bookings0654"))
        assertTrue(operationalSource.contains("directPassengerTrip0729"))
    }

    @Test
    fun publicShortcutUsesCanonicalPublicHrefNotAdministrativeTripHref() {
        assertTrue(operationalSource.contains("canonicalTimelineBlaBlaPublicHref0490(entry)"))
        assertTrue(operationalSource.contains("openPublicTripBlaBla(context0729, canonicalPublicHref0729)"))
        assertTrue(operationalSource.contains("enabled = canonicalPublicHref0729 != null"))
    }

    @Test
    fun passengerSectionDoesNotCreateDuplicateSecondShortcutBar() {
        assertTrue(operationalSource.contains("showTripActions0549 = false"))
        assertFalse(operationalSource.contains("showTripActions0549 = true"))
    }
}
