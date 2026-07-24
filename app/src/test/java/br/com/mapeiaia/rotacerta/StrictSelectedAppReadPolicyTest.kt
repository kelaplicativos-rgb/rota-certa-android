package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictSelectedAppReadPolicyTest {
    private val selected = setOf("com.app99.driver", "sinet.startup.indriver")

    @Test
    fun `allows only an explicitly selected package`() {
        assertTrue(
            StrictSelectedAppReadPolicy.canRead(
                packageName = " COM.APP99.DRIVER ",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }

    @Test
    fun `allows a generic manually selected package even when legacy classification rejects it`() {
        assertTrue(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.google.android.apps.nbu.files",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected + "com.google.android.apps.nbu.files",
                packageAllowedByPlatformPolicy = false,
            ),
        )
    }

    @Test
    fun `blocks an unselected package`() {
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.ubercab.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }

    @Test
    fun `blocks own package even if it appears in the selection`() {
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "br.com.mapeiaia.rotacerta",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected + "br.com.mapeiaia.rotacerta",
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }

    @Test
    fun `blocks when the app or live reading is disabled`() {
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = false,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.app99.driver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = false,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }
}
