package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"
    private val enabled = AppSettings(appEnabled = true, restrictToSelectedRideApps = true)

    @Test
    fun everyReportedPackageIsReleasedForUniversalReading() {
        listOf(
            ownPackage,
            "com.google.android.apps.nbu.files",
            "com.openai.chatgpt",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.maps",
            "com.waze",
            "br.com.tkx.taxi.drivermachine",
        ).forEach { packageName ->
            val classification = CorePackageMonitor.classify(packageName, ownPackage, enabled)
            assertTrue("Pacote deveria estar liberado: " + packageName, classification.canScan)
            assertEquals(CorePackageKind.RideApp, classification.kind)
        }
    }

    @Test
    fun knownRideAppsKeepTheirSpecializedModules() {
        assertEquals(CoreRideAppModule.NinetyNine, CorePackageMonitor.classify("com.app99.driver", ownPackage, enabled).module)
        assertEquals(CoreRideAppModule.Uber, CorePackageMonitor.classify("com.ubercab.driver", ownPackage, enabled).module)
        assertEquals(CoreRideAppModule.InDrive, CorePackageMonitor.classify("sinet.startup.indriver", ownPackage, enabled).module)
    }

    @Test
    fun unknownAppsUseUniversalModule() {
        val classification = CorePackageMonitor.classify("com.example.anyscreen", ownPackage, enabled)
        assertTrue(classification.canScan)
        assertEquals(CoreRideAppModule.Universal, classification.module)
    }

    @Test
    fun onlyTheMasterAppSwitchStopsReading() {
        val disabled = AppSettings(appEnabled = false)
        assertFalse(CorePackageMonitor.classify("com.google.android.apps.photos", ownPackage, disabled).canScan)
    }
}
// open_all_package_tests_0_1_94
