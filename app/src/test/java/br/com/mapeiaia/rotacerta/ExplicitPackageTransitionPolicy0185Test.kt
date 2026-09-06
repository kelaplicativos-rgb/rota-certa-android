package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitPackageTransitionPolicy0185Test {
    private val selected = setOf("sinet.startup.indriver", "com.app99.driver")
    private val own = "br.com.mapeiaia.rotacerta"

    @Test
    fun rejectsDocumentsUiEvenWhenAStaleSelectedRootCouldStillExist() {
        assertTrue(
            ExplicitPackageTransitionPolicy0185.shouldReject(
                eventPackageName = "com.google.android.documentsui",
                selectedPackages = selected,
                ownPackageName = own,
                isTransientOverlay = { false },
            ),
        )
    }

    @Test
    fun keepsSelectedOwnAndTransientPackages() {
        assertFalse(ExplicitPackageTransitionPolicy0185.shouldReject("sinet.startup.inDriver", selected, own) { false })
        assertFalse(ExplicitPackageTransitionPolicy0185.shouldReject(own, selected, own) { false })
        assertFalse(ExplicitPackageTransitionPolicy0185.shouldReject("com.android.systemui", selected, own) { it == "com.android.systemui" })
        assertFalse(ExplicitPackageTransitionPolicy0185.shouldReject(null, selected, own) { false })
    }
}
