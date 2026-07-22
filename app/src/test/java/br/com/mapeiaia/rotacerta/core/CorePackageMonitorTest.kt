package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"

    @Test
    fun unrestrictedModeAllowsUnknownAppsThroughUniversalModule() {
        val classification = CorePackageMonitor.classify(
            packageName = "com.google.android.apps.nbu.files",
            ownPackageName = ownPackage,
            settings = AppSettings(
                appEnabled = true,
                restrictToSelectedRideApps = false,
            ),
        )

        assertTrue(classification.canScan)
        assertEquals(CorePackageKind.RideApp, classification.kind)
        assertEquals(CoreRideAppModule.Universal, classification.module)
        assertTrue(classification.reason.contains("Modo universal ativo"))
    }

    @Test
    fun unrestrictedModeAllowsChatAndGalleryAppsForRegisteredCardSearch() {
        val settings = AppSettings(
            appEnabled = true,
            restrictToSelectedRideApps = false,
        )

        listOf(
            "com.openai.chatgpt",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "br.com.tkx.taxi.drivermachine",
        ).forEach { packageName ->
            val classification = CorePackageMonitor.classify(packageName, ownPackage, settings)
            assertTrue("Esperava leitura universal para $packageName", classification.canScan)
            assertEquals(CoreRideAppModule.Universal, classification.module)
        }
    }

    @Test
    fun restrictedModeBlocksUnknownApps() {
        val classification = CorePackageMonitor.classify(
            packageName = "com.google.android.apps.nbu.files",
            ownPackageName = ownPackage,
            settings = AppSettings(
                appEnabled = true,
                restrictToSelectedRideApps = true,
            ),
        )

        assertFalse(classification.canScan)
        assertEquals(CorePackageKind.NotMonitored, classification.kind)
        assertEquals(CoreRideAppModule.Unknown, classification.module)
    }

    @Test
    fun selectedRideAppRemainsAllowedInRestrictedMode() {
        val classification = CorePackageMonitor.classify(
            packageName = "com.app99.driver",
            ownPackageName = ownPackage,
            settings = AppSettings(
                appEnabled = true,
                restrictToSelectedRideApps = true,
                monitor99 = true,
            ),
        )

        assertTrue(classification.canScan)
        assertEquals(CoreRideAppModule.NinetyNine, classification.module)
    }

    @Test
    fun passiveAndSystemPackagesRemainBlockedInUniversalMode() {
        val settings = AppSettings(
            appEnabled = true,
            restrictToSelectedRideApps = false,
        )

        listOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.inputmethod.latin",
            "com.google.android.apps.maps",
            "com.waze",
        ).forEach { packageName ->
            assertFalse(
                "Pacote passivo/sistema nao pode ser lido: $packageName",
                CorePackageMonitor.classify(packageName, ownPackage, settings).canScan,
            )
        }
    }

    @Test
    fun ownAppAndDisabledServiceAreNeverScanned() {
        val universalSettings = AppSettings(
            appEnabled = true,
            restrictToSelectedRideApps = false,
        )
        assertFalse(CorePackageMonitor.classify(ownPackage, ownPackage, universalSettings).canScan)

        val disabled = AppSettings(
            appEnabled = false,
            restrictToSelectedRideApps = false,
        )
        assertFalse(CorePackageMonitor.classify("com.google.android.apps.photos", ownPackage, disabled).canScan)
    }
}
