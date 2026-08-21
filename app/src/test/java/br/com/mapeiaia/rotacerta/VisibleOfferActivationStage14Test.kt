package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleOfferActivationStage14Test {
    private fun admission(
        eventPackage: String? = "com.android.systemui",
        selectedPackage: String = "com.ubercab.driver",
        rootPackage: String? = "com.ubercab.driver",
        eventWindow: Int = 9,
        rootWindow: Int? = 201,
        transient: Boolean = true,
        sessionPackage: String? = null,
        sessionWindow: Int? = null,
    ): FarolRootAdmission0187 = FarolRootSnapshotPolicy0187.evaluate(
        eventPackageName = eventPackage,
        selectedPackageName = selectedPackage,
        rootPackageName = rootPackage,
        eventWindowId = eventWindow,
        rootWindowId = rootWindow,
        transientOverlayEvent = transient,
        activeSessionPackageName = sessionPackage,
        activeSessionWindowId = sessionWindow,
    )

    @Test
    fun selectedVisibleRootBehindSystemUiCanBootstrapWithoutPriorSession() {
        val result = admission()
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun selectedVisibleRootCanReplaceDifferentPreviousDriverSession() {
        val result = admission(
            sessionPackage = "com.app99.driver",
            sessionWindow = 180,
        )
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun sameSelectedSessionStillRejectsWrongRootWindow() {
        val result = admission(
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 199,
        )
        assertFalse(result.accepted)
        assertEquals("transient_root_window_mismatch", result.reason)
    }

    @Test
    fun selectedVisibleRootWithMissingEventPackageCanBootstrapWithoutPriorSession() {
        val result = admission(eventPackage = null, transient = false)
        assertTrue(result.accepted)
        assertEquals("selected_root_without_event_package_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun missingEventPackageStillRejectsWrongWindowInsideSameSelectedSession() {
        val result = admission(
            eventPackage = null,
            transient = false,
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 199,
        )
        assertFalse(result.accepted)
        assertEquals("event_package_missing_root_window_mismatch_stage14", result.reason)
    }

    @Test
    fun externalRootStillFailsClosedEvenDuringSystemUiTransient() {
        val result = admission(rootPackage = "com.sec.android.app.launcher")
        assertFalse(result.accepted)
        assertEquals("root_package_mismatch", result.reason)
    }

    @Test
    fun nonTransientExternalEventStillCannotAuthorizeSelectedRoot() {
        val result = admission(
            eventPackage = "com.sec.android.app.launcher",
            transient = false,
        )
        assertFalse(result.accepted)
        assertEquals("event_package_mismatch", result.reason)
    }

    @Test
    fun sameSelectedSessionAndWindowPreservesExistingTransientContract() {
        val result = admission(
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 201,
        )
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient", result.reason)
    }
}
