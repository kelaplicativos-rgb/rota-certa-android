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
    fun ocrFallbackRequiresTwoEmptyFramesBeforeClear() {
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
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 2_350L),
        )
        assertEquals(
            UniversalLiveReadAction.Clear,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 2_700L),
        )
    }

    @Test
    fun activeFrameCancelsPendingEmptyConfirmation() {
        val gate = UniversalLiveReadGate()

        gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 4_000L)
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 4_300L),
        )
        assertEquals(
            UniversalLiveReadAction.Analyze,
            gate.submit(UniversalLiveReadSource.Ocr, active = true, nowMillis = 4_500L),
        )
        assertEquals(
            UniversalLiveReadAction.Ignore,
            gate.submit(UniversalLiveReadSource.Ocr, active = false, nowMillis = 4_800L),
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
    fun emptyAccessibilityFromExternalAppStillReachesSourceGate() {
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
    fun passivePackagesDoNotStartLiveScanning() {
        listOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.documentsui",
            "com.google.android.inputmethod.latin",
        ).forEach { packageName ->
            assertFalse(
                UniversalFastReadPolicy.shouldScanLivePackage(
                    packageName = packageName,
                    ownPackageName = "br.com.mapeiaia.rotacerta",
                ),
            )
        }
    }

    @Test
    fun rideAndImageViewerPackagesRemainReadable() {
        listOf(
            "sinet.startup.indriver",
            "com.ubercab.driver",
            "com.app99.driver",
            "com.google.android.apps.nbu.files",
        ).forEach { packageName ->
            assertTrue(
                UniversalFastReadPolicy.shouldScanLivePackage(
                    packageName = packageName,
                    ownPackageName = "br.com.mapeiaia.rotacerta",
                ),
            )
        }
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
    fun activeOcrUsesSlowerWatchdogWithoutDelayingFirstRead() {
        assertEquals(300L, UniversalFastReadPolicy.minimumOcrIntervalMillis(false))
        assertEquals(650L, UniversalFastReadPolicy.minimumOcrIntervalMillis(true))
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

    @Test
    fun ocrOwnedCardThrottlesOnlyRedundantAccessibilityPolling() {
        assertEquals(
            120L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = false,
                hasActiveAddressSignature = false,
            ),
        )
        assertEquals(
            120L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = true,
                hasActiveAddressSignature = true,
            ),
        )
        assertEquals(
            650L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = false,
                hasActiveAddressSignature = true,
            ),
        )
    }


    @Test
    fun ordinaryProductPhotoWithAddressesNeverBecomesRideCard() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = """
                MPR-2012
                SISTEMA OPERACIONAL: ANDROID
                PROJETOR MULTIMIDIA 4000 LUMENS
                POTENCIA TOTAL: 110W
                R$620.00
            """.trimIndent(),
            addresses = listOf(
                "Rua Baltazar Vidal 95",
                "Rua Coelho Lisboa, 419",
                "Rua Agave Dragao 81",
                "Rua Azevedo Soares, 1500",
                "Rua.luli",
            ),
            destination = "Rua.luli",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertFalse(decision.accepted)
        assertEquals("logradouro_deformado", decision.reason)
    }

    @Test
    fun addressListWithoutRideOfferEvidenceIsRejected() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = """
                Rua Baltazar Vidal 95
                Rua Coelho Lisboa, 419
                Rua Agave Dragao 81
                Rua Azevedo Soares, 1500
            """.trimIndent(),
            addresses = listOf("Rua Baltazar Vidal 95", "Rua Azevedo Soares, 1500"),
            destination = "Rua Azevedo Soares, 1500",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertFalse(decision.accepted)
        assertEquals("sem_evidencia_de_corrida", decision.reason)
    }

    @Test
    fun real99ScreenshotInImageViewerHasStrongRideEvidence() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = """
                Dinheiro
                R$8,76
                R$2,08/km
                4,76 493 corridas
                Perfil Essencial
                9min (1,3km) Area de risco
                Yogui Stilo e Sports, Avenida Mateo Bei, 2651 - Cidade Sao Mateus
                9min (2,9km)
                Condominio Parque Residencial Santa Barbara, Cidade Satelite San
            """.trimIndent(),
            addresses = listOf(
                "Avenida Mateo Bei, 2651 - Cidade Sao Mateus",
                "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            ),
            destination = "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertTrue(decision.accepted)
        assertTrue(decision.score >= 3)
    }

    @Test
    fun knownRideAppKeepsFastFallbackWithPartialOcr() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = """
                R$18,50
                Rua A, 10
                Rua B, 20
            """.trimIndent(),
            addresses = listOf("Rua A, 10", "Rua B, 20"),
            destination = "Rua B, 20",
            packageName = "com.app99.driver",
        )

        assertTrue(decision.accepted)
    }

    @Test
    fun malformedStreetIsRejectedEvenWithRideMetrics() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = "R$20,00 8min 3,2km Perfil Premium Rua A, 10 Rua.luli",
            addresses = listOf("Rua A, 10", "Rua.luli"),
            destination = "Rua.luli",
            packageName = "com.app99.driver",
        )

        assertFalse(decision.accepted)
        assertEquals("logradouro_deformado", decision.reason)
    }


    @Test
    fun passiveObservedWindowBlocksOcrEvenWhenResolvedRootStillShowsRideApp() {
        val token = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = "com.android.systemui",
            resolvedPackageName = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
            screenGeneration = 10L,
            windowGeneration = 4L,
        )
        assertEquals(null, token)
    }

    @Test
    fun windowRoundTripInvalidatesOcrRequest() {
        val token = requireNotNull(
            UniversalFastReadPolicy.createOcrRequestToken(
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
        assertFalse(
            UniversalFastReadPolicy.isOcrRequestFresh(
                token = token,
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 6L,
            ),
        )
    }

    @Test
    fun sameWindowAndScreenGenerationKeepsOcrFresh() {
        val token = requireNotNull(
            UniversalFastReadPolicy.createOcrRequestToken(
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
        assertTrue(
            UniversalFastReadPolicy.isOcrRequestFresh(
                token = token,
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
    }


    @Test
    fun foreignEventCannotCancelFreshRouteInFlight() {
        assertTrue(
            UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 10_000L,
                nowMillis = 10_700L,
                activeRidePackageName = "sinet.startup.indriver",
                incomingPackageName = "com.openai.chatgpt",
            ),
        )
    }

    @Test
    fun emptyReadCannotCancelFreshRouteInFlight() {
        assertTrue(
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 20_000L,
                nowMillis = 21_000L,
            ),
        )
    }

    @Test
    fun protectionEndsAfterRouteOrGraceWindow() {
        assertFalse(
            UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
                hasActiveAddressSignature = true,
                routeInFlight = false,
                lastActiveReadAtMillis = 30_000L,
                nowMillis = 30_100L,
                activeRidePackageName = "sinet.startup.indriver",
                incomingPackageName = "com.openai.chatgpt",
            ),
        )
        assertFalse(
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 30_000L,
                nowMillis = 33_000L,
            ),
        )
    }

}
