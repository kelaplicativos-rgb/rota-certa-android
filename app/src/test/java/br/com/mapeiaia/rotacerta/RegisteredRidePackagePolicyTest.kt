package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegisteredRidePackagePolicyTest {
    @Test
    fun startsWithoutPredefinedRidePackages() {
        val settings = AppSettings()

        assertFalse(settings.monitor99)
        assertFalse(settings.monitorUber)
        assertFalse(settings.monitorInDrive)
        assertEquals("", settings.extraMonitoredPackages)
    }

    @Test
    fun packagesComeOnlyFromRegisteredCardTemplates() {
        val templates = listOf(
            RideCardTemplate(
                id = "1",
                name = "Regional",
                packageName = " com.regional.driver ",
            ),
            RideCardTemplate(
                id = "2",
                name = "Sem pacote",
                packageName = null,
            ),
            RideCardTemplate(
                id = "3",
                name = "Regional duplicado",
                packageName = "COM.REGIONAL.DRIVER",
            ),
        )

        assertEquals(setOf("com.regional.driver"), RegisteredRidePackagePolicy.packagesFromTemplates(templates))
    }
}
