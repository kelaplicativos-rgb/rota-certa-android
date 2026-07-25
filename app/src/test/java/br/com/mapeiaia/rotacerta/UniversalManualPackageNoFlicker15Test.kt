package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalManualPackageNoFlicker15Test {
    @Test
    fun manuallySavedPackageOverridesLegacyPlatformClassification() {
        assertTrue(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.google.android.apps.nbu.files",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = setOf("com.google.android.apps.nbu.files"),
                packageAllowedByPlatformPolicy = false,
            ),
        )
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "br.com.mapeiaia.rotacerta",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = setOf("br.com.mapeiaia.rotacerta"),
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }

    @Test
    fun viaAppIsNeverAcceptedAsTheLastAddress() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
                Rua das Flores, 10 - Centro
                Avenida Mateo Bei, 3518 - Sao Mateus
                Abrir via app
            """.trimIndent(),
        )

        assertEquals(2, addresses.size)
        assertTrue(addresses.last().startsWith("Avenida Mateo Bei", ignoreCase = true))
        assertFalse(addresses.any { it.equals("Via app", ignoreCase = true) })
    }

    @Test
    fun sameDestinationNeverClearsOrReprocessesBecauseMapScrolled() {
        val action = FarolDisplayStabilityPolicy.decide(
            previousPackageName = "com.exemplo.qualquer",
            previousWindowId = 4,
            activeAddressSignature = "com.exemplo.qualquer|origem|destino",
            currentPackageName = "com.exemplo.qualquer",
            currentWindowId = 9,
            currentAddressSignature = "com.exemplo.qualquer|origem|destino",
            hasTwoAddresses = true,
            eventType = AccessibilityEventFloodGate.TYPE_VIEW_SCROLLED,
        )

        assertEquals(FarolDisplayStabilityPolicy.Action.KeepCurrent, action)
    }

    @Test
    fun temporaryMissingTextConfirmsAbsenceInsteadOfBlinking() {
        val action = FarolDisplayStabilityPolicy.decide(
            previousPackageName = "com.exemplo.qualquer",
            previousWindowId = 4,
            activeAddressSignature = "com.exemplo.qualquer|origem|destino",
            currentPackageName = "com.exemplo.qualquer",
            currentWindowId = 9,
            currentAddressSignature = null,
            hasTwoAddresses = false,
            eventType = AccessibilityEventFloodGate.TYPE_WINDOWS_CHANGED,
        )

        assertEquals(FarolDisplayStabilityPolicy.Action.ConfirmAbsence, action)
        assertTrue(FarolDisplayStabilityPolicy.PARTIAL_ABSENCE_CONFIRM_MILLIS >= 400L)
    }

    @Test
    fun realPackageOrDestinationChangeStillInvalidatesImmediately() {
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearImmediately,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app.a",
                previousWindowId = 1,
                activeAddressSignature = "com.app.a|origem|destino",
                currentPackageName = "com.app.b",
                currentWindowId = 2,
                currentAddressSignature = null,
                hasTwoAddresses = false,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_STATE_CHANGED,
            ),
        )
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearThenProcess,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app.a",
                previousWindowId = 1,
                activeAddressSignature = "com.app.a|origem|destino-1",
                currentPackageName = "com.app.a",
                currentWindowId = 1,
                currentAddressSignature = "com.app.a|origem|destino-2",
                hasTwoAddresses = true,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }

    @Test
    fun selectedPackageIsRemovedOnlyAfterItsLastCardDisappears() {
        val packageName = "com.exemplo.qualquer"
        val template = RideCardTemplate(
            id = "t1",
            name = "Modelo",
            packageName = packageName,
            requiredFeatures = emptyList(),
            sampleHash = 1,
        )
        val capture = AutomaticRideCapture(
            id = "c1",
            createdAtMillis = 1L,
            expiresAtMillis = Long.MAX_VALUE,
            packageName = packageName,
            imageFileName = "c1.jpg",
            textHash = 1,
            textPreview = "Rua A, 1\nRua B, 2",
        )

        assertFalse(CardPackageLifecyclePolicy.shouldRemoveSelectedPackage(packageName, listOf(template), emptyList()))
        assertFalse(CardPackageLifecyclePolicy.shouldRemoveSelectedPackage(packageName, emptyList(), listOf(capture)))
        assertTrue(CardPackageLifecyclePolicy.shouldRemoveSelectedPackage(packageName, emptyList(), emptyList()))
        assertFalse(
            packageName in CardPackageLifecyclePolicy.removePackageIfOrphaned(
                selectedPackages = setOf(packageName, "com.outro"),
                packageName = packageName,
                templates = emptyList(),
                captures = emptyList(),
            ),
        )
    }
}
