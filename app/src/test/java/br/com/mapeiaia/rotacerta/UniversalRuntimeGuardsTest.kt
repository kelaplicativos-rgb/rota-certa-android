package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalRuntimeGuardsTest {
    @Test
    fun emptyOcrDoesNotClearValidAccessibilityCard() {
        val gate = UniversalLiveReadGate()

        assertEquals(
            UniversalLiveReadAction.Analyze,
            gate.submit(UniversalLiveReadSource.Accessibility, active = true, nowMillis = 1_000L),
        )
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 1_100L),
        )
    }

    @Test
    fun ocrFallbackSurvivesTemporaryEmptyAccessibility() {
        val gate = UniversalLiveReadGate()

        assertEquals(
            UniversalLiveReadAction.Analyze,
            gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 2_000L),
        )
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Accessibility, active = false, nowMillis = 2_300L),
        )
        assertEquals(
            UniversalLiveReadAction.Clear,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 2_350L),
        )
    }

    @Test
    fun accessibilityKeepsPriorityOverCompetingOcr() {
        val gate = UniversalLiveReadGate()

        gate.submit(UniversalLiveReadSource.Accessibility, active = true, nowMillis = 3_000L)
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 3_200L),
        )
    }

    @Test
    fun emptyAccessibilityFromOwnOverlayIsIgnored() {
        assertTrue(
            UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                text = "",
                rootPackageName = "br.com.mapeiaia.rotacerta",
                effectivePackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun emptyAccessibilityFromExternalAppStillClearsImmediately() {
        assertFalse(
            UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                text = "",
                rootPackageName = "sinet.startup.indriver",
                effectivePackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun nonEmptyAccessibilityIsNeverHiddenByOverlayPolicy() {
        assertFalse(
            UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                text = "Rua A, 10\nRua B, 20",
                rootPackageName = "br.com.mapeiaia.rotacerta",
                effectivePackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun ocrIsPausedWhileAccessibilityOwnsActiveCard() {
        assertFalse(
            UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = true,
                hasActiveAddressSignature = true,
            ),
        )
        assertTrue(
            UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = false,
                hasActiveAddressSignature = true,
            ),
        )
        assertTrue(
            UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = true,
                hasActiveAddressSignature = false,
            ),
        )
    }

    @Test
    fun duplicateHistoryIsBlockedInsideWindow() {
        val deduper = UniversalAnalysisDeduper(duplicateWindowMillis = 60_000L)

        assertTrue(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 10_000L))
        assertFalse(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 10_150L))
        assertTrue(deduper.shouldPersist("outro|verde|4.649", nowMillis = 10_200L))
        assertTrue(deduper.shouldPersist("destino|vermelho|10.057", nowMillis = 70_001L))
    }

    @Test
    fun accessibilityOverlayRootKeepsRideAppAsEffectiveWindow() {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = "br.com.mapeiaia.rotacerta",
            activePackageName = "sinet.startup.indriver",
            lastExternalPackageName = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
        )

        assertEquals("sinet.startup.indriver", resolution.effectivePackageName)
        assertEquals("sinet.startup.indriver", resolution.lastExternalPackageName)
    }

    @Test
    fun realMainActivityOwnsForegroundAndAllowsImmediateClear() {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = "br.com.mapeiaia.rotacerta",
            activePackageName = "br.com.mapeiaia.rotacerta",
            lastExternalPackageName = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
        )

        assertEquals("br.com.mapeiaia.rotacerta", resolution.effectivePackageName)
        assertEquals("sinet.startup.indriver", resolution.lastExternalPackageName)
    }

    @Test
    fun overlayEventIsNotMistakenForMainActivity() {
        assertFalse(
            UniversalWindowPackageResolver.isOwnMainActivityEvent(
                eventPackageName = "br.com.mapeiaia.rotacerta",
                eventClassName = "android.widget.LinearLayout",
                eventType = 32,
                ownPackageName = "br.com.mapeiaia.rotacerta",
                mainActivityClassName = "br.com.mapeiaia.rotacerta.MainActivity",
                windowStateChangedType = 32,
            ),
        )
    }

    @Test
    fun realMainActivityWindowEventIsRecognized() {
        assertTrue(
            UniversalWindowPackageResolver.isOwnMainActivityEvent(
                eventPackageName = "br.com.mapeiaia.rotacerta",
                eventClassName = "br.com.mapeiaia.rotacerta.MainActivity",
                eventType = 32,
                ownPackageName = "br.com.mapeiaia.rotacerta",
                mainActivityClassName = "br.com.mapeiaia.rotacerta.MainActivity",
                windowStateChangedType = 32,
            ),
        )
    }
}
