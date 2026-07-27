package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"

    @Test
    fun onlyPackagePersistedByUserIsReleased() {
        val settings = AppSettings(
            appEnabled = true,
            liveReadingEnabled = true,
            extraMonitoredPackages = "com.exemplo.entregas",
        )
        val selected = CorePackageMonitor.classify("com.exemplo.entregas", ownPackage, settings)
        val other = CorePackageMonitor.classify("com.exemplo.outro", ownPackage, settings)
        assertTrue(selected.canScan)
        assertEquals(CorePackageKind.RideApp, selected.kind)
        assertFalse(other.canScan)
        assertEquals(CorePackageKind.NotMonitored, other.kind)
    }

    @Test
    fun emptySelectionDoesNotReleaseAnyExternalPackage() {
        val settings = AppSettings(appEnabled = true, liveReadingEnabled = true, extraMonitoredPackages = "")
        assertFalse(CorePackageMonitor.classify("com.exemplo.qualquer", ownPackage, settings).canScan)
    }
}
