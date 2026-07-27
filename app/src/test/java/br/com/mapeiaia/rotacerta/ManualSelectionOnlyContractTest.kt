package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSelectionOnlyContractTest {
    @Test
    fun onlyExplicitlySelectedPackageCanBeRead() {
        val selected = setOf("com.exemplo.entregas")
        assertTrue(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.exemplo.entregas",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.exemplo.nao.selecionado",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }
}
