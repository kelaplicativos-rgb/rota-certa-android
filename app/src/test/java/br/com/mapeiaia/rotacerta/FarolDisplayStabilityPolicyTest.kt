package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FarolDisplayStabilityPolicyTest {
    @Test
    fun sameAddressesWithPriceOrTimerChangesKeepTheCurrentDecision() {
        val first = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "com.app99.driver",
            savedPackages = setOf("com.app99.driver"),
            text = "Rua A, 10\nRua B, 20\nR$ 18\n00:15",
        )
        val second = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "com.app99.driver",
            savedPackages = setOf("com.app99.driver"),
            text = "Rua A, 10\nRua B, 20\nR$ 22\n00:14",
        )

        assertEquals(first.addressSignature, second.addressSignature)
        assertEquals(first.screenHash, second.screenHash)
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ProcessCurrent,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app99.driver",
                previousWindowId = 7,
                activeAddressSignature = first.addressSignature,
                currentPackageName = "com.app99.driver",
                currentWindowId = 7,
                currentAddressSignature = second.addressSignature,
                hasTwoAddresses = true,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }

    @Test
    fun partialReadFromSameWindowConfirmsAbsenceInsteadOfBlinking() {
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ConfirmAbsence,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "sinet.startup.indriver",
                previousWindowId = 3,
                activeAddressSignature = "sinet.startup.indriver|a|b",
                currentPackageName = "sinet.startup.indriver",
                currentWindowId = 3,
                currentAddressSignature = null,
                hasTwoAddresses = false,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }

    @Test
    fun newDestinationClearsImmediatelyBeforeProcessing() {
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearThenProcess,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app99.driver",
                previousWindowId = 7,
                activeAddressSignature = "com.app99.driver|rua-a|rua-b",
                currentPackageName = "com.app99.driver",
                currentWindowId = 7,
                currentAddressSignature = "com.app99.driver|rua-a|rua-c",
                hasTwoAddresses = true,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }

    @Test
    fun packageWindowOrScrollChangesClearImmediately() {
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearImmediately,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app99.driver",
                previousWindowId = 7,
                activeAddressSignature = "signature",
                currentPackageName = "com.app99.driver",
                currentWindowId = 8,
                currentAddressSignature = null,
                hasTwoAddresses = false,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOWS_CHANGED,
            ),
        )
        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearImmediately,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.app99.driver",
                previousWindowId = 7,
                activeAddressSignature = "signature",
                currentPackageName = "com.app99.driver",
                currentWindowId = 7,
                currentAddressSignature = null,
                hasTwoAddresses = false,
                eventType = AccessibilityEventFloodGate.TYPE_VIEW_SCROLLED,
            ),
        )
    }

    @Test
    fun stableHashChangesOnlyWhenAddressSignatureChanges() {
        val sameA = FarolDisplayStabilityPolicy.stableScreenHash("com.app99.driver", "a|b")
        val sameB = FarolDisplayStabilityPolicy.stableScreenHash("com.app99.driver", "a|b")
        val changed = FarolDisplayStabilityPolicy.stableScreenHash("com.app99.driver", "a|c")
        assertEquals(sameA, sameB)
        assertNotEquals(sameA, changed)
    }
}
