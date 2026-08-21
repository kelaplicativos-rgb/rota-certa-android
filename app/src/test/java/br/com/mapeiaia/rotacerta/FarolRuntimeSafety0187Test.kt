package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRuntimeSafety0187Test {
    @Test
    fun monotonicAgeRejectsMissingAndFutureTimestamps() {
        assertNull(FarolElapsedTimePolicy0187.ageMillis(1_000L, 0L))
        assertNull(FarolElapsedTimePolicy0187.ageMillis(1_000L, 1_001L))
        assertTrue(FarolElapsedTimePolicy0187.isWithin(1_500L, 1_000L, 500L))
        assertFalse(FarolElapsedTimePolicy0187.isWithin(1_501L, 1_000L, 500L))
    }

    @Test
    fun recoveredCardMustRemainBoundToSamePackageSessionWindowAndGenerations() {
        val binding = FarolRecoveryBinding0187("sinet.startup.indriver", 7L, 4923, 11L, 3L, "abc")
        fun fresh(
            pkg: String? = "sinet.startup.inDriver",
            session: Long = 7L,
            window: Int = 4923,
            screen: Long = 11L,
            windowGeneration: Long = 3L,
            signature: String = "abc",
        ) = FarolRecoveryBindingPolicy0187.isFresh(
            binding, pkg, session, window, screen, windowGeneration, signature,
        )

        assertTrue(fresh())
        assertFalse(fresh(session = 8L))
        assertFalse(fresh(window = 4915))
        assertFalse(fresh(screen = 12L))
        assertFalse(fresh(windowGeneration = 4L))
        assertFalse(fresh(signature = "other-card"))
        assertFalse(fresh(pkg = "com.app99.driver"))
    }

    @Test
    fun externalBurstIsCollapsedOnlyAfterTheFarolIsAlreadyIdle() {
        val gate = FarolExternalPackageEventGate0187(duplicateWindowMillis = 900L)
        assertTrue(gate.shouldHandle("com.android.settings", 1, 2048, alreadyIdle = false, nowElapsedMillis = 1_000L))
        assertFalse(gate.shouldHandle("com.android.settings", 2, 32, alreadyIdle = true, nowElapsedMillis = 1_100L))
        assertTrue(gate.shouldHandle("com.sec.android.app.launcher", 2, 32, alreadyIdle = true, nowElapsedMillis = 1_200L))
        gate.reset()
        assertTrue(gate.shouldHandle("com.android.settings", 3, 2048, alreadyIdle = true, nowElapsedMillis = 1_250L))
    }
    @Test
    fun rootSnapshotRequiresTheSelectedPackageAndTheSameWindow() {
        fun admission(
            eventPackage: String? = "sinet.startup.indriver",
            rootPackage: String? = "sinet.startup.indriver",
            eventWindow: Int = 42,
            rootWindow: Int? = 42,
            transient: Boolean = false,
            sessionPackage: String? = "sinet.startup.indriver",
            sessionWindow: Int? = 42,
        ) = FarolRootSnapshotPolicy0187.evaluate(
            eventPackageName = eventPackage,
            selectedPackageName = "sinet.startup.indriver",
            rootPackageName = rootPackage,
            eventWindowId = eventWindow,
            rootWindowId = rootWindow,
            transientOverlayEvent = transient,
            activeSessionPackageName = sessionPackage,
            activeSessionWindowId = sessionWindow,
        )

        assertTrue(admission().accepted)
        assertFalse(admission(rootPackage = "com.android.systemui").accepted)
        assertFalse(admission(rootWindow = null).accepted)
        assertFalse(admission(eventWindow = 41).accepted)
        assertTrue(admission(eventPackage = null).accepted)
        assertFalse(admission(eventPackage = null, sessionWindow = 41).accepted)
        assertTrue(admission(eventPackage = "com.android.systemui", transient = true).accepted)
        // Stage 14: a previous selected-driver session must not prevent the currently
        // visible selected root from bootstrapping a new transient popup session.
        assertTrue(admission(eventPackage = "com.android.systemui", transient = true, sessionPackage = "com.app99.driver").accepted)
    }

    @Test
    fun accessibilityReadMustKeepPackageSessionWindowAndGenerations() {
        val binding = FarolReadBinding0187(
            packageName = "com.app99.driver",
            sessionGeneration = 9L,
            windowId = 0,
            screenGeneration = 17L,
            windowGeneration = 4L,
        )
        fun fresh(
            pkg: String? = "com.app99.driver",
            session: Long = 9L,
            window: Int? = 0,
            screen: Long = 17L,
            windowGeneration: Long = 4L,
        ) = FarolReadBindingPolicy0187.isFresh(
            binding = binding,
            currentPackageName = pkg,
            currentSessionGeneration = session,
            currentWindowId = window,
            currentScreenGeneration = screen,
            currentWindowGeneration = windowGeneration,
        )

        assertTrue(fresh())
        assertFalse(fresh(pkg = "com.ubercab.driver"))
        assertFalse(fresh(session = 10L))
        assertFalse(fresh(window = 1))
        assertFalse(fresh(screen = 18L))
        assertFalse(fresh(windowGeneration = 5L))
    }

    @Test
    fun rejectedSnapshotNeverClearsVisualWithoutPositiveScreenEvidence() {
        assertTrue(
            FarolRejectedSnapshotPolicy0187Phase3.effect("transient_root_window_mismatch") ==
                FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT,
        )
        assertTrue(
            FarolRejectedSnapshotPolicy0187Phase3.effect("transient_without_selected_session") ==
                FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT,
        )
        assertTrue(
            FarolRejectedSnapshotPolicy0187Phase3.effect("event_package_missing_without_same_session") ==
                FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT,
        )
        assertTrue(
            FarolRejectedSnapshotPolicy0187Phase3.effect("root_package_mismatch") ==
                FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT,
        )
        assertTrue(
            FarolRejectedSnapshotPolicy0187Phase3.effect("event_root_window_mismatch") ==
                FarolRejectedSnapshotEffect0187Phase3.INVALIDATE_READ_KEEP_VISUAL,
        )
    }



    @Test
    fun decisionResultRequiresTheSameSessionWindowAndBothGenerations() {
        val binding = FarolDecisionBinding0187Phase4(
            packageName = "sinet.startup.indriver",
            sessionGeneration = 12L,
            windowId = 77,
            screenGeneration = 31L,
            windowGeneration = 8L,
            screenHash = 991,
            addressSignature = "destination-a",
        )
        fun fresh(
            pkg: String? = "sinet.startup.inDriver",
            session: Long = 12L,
            window: Int? = 77,
            screen: Long = 31L,
            windowGeneration: Long = 8L,
            screenHash: Int? = 991,
            signature: String? = "destination-a",
        ) = FarolDecisionBindingPolicy0187Phase4.isFresh(
            binding = binding,
            currentPackageName = pkg,
            currentSessionGeneration = session,
            currentWindowId = window,
            currentScreenGeneration = screen,
            currentWindowGeneration = windowGeneration,
            currentScreenHash = screenHash,
            currentAddressSignature = signature,
        )

        assertTrue(fresh())
        assertFalse(fresh(session = 13L))
        assertFalse(fresh(window = 78))
        assertFalse(fresh(screen = 32L))
        assertFalse(fresh(windowGeneration = 9L))
        assertFalse(fresh(screenHash = 992))
        assertFalse(fresh(signature = "destination-b"))
        assertFalse(fresh(pkg = "com.app99.driver"))
    }

}
