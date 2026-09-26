package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationWakeup0169Test {
    private val selected = setOf("com.ubercab.driver", "com.app99.driver")

    @Test
    fun selectedNotificationStartsBoundedWakeup() {
        val gate = FarolNotificationWakeGate0169(maxCaptures = 2)
        val token = gate.begin(
            eventType = 64,
            eventPackageName = "com.ubercab.driver",
            selectedPackages = selected,
            ownPackageName = "br.com.mapeiaia.rotacerta",
            workModeEnabled = true,
            liveReadingEnabled = true,
            serviceReady = true,
            bubbleGestureActive = false,
            nowElapsedMillis = 1_000L,
        )
        assertNotNull(token)
        assertEquals(0, gate.reserveCapture(token!!, 1_000L))
        assertEquals(1, gate.reserveCapture(token, 1_100L))
        assertNull(gate.reserveCapture(token, 1_200L))
    }

    @Test
    fun unselectedOrDisabledNotificationNeverWakesOcr() {
        val gate = FarolNotificationWakeGate0169()
        assertNull(gate.begin(64, "com.example.other", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L))
        assertNull(gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", false, true, true, false, 2_000L))
        assertNull(gate.begin(2_048, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 3_000L))
    }

    @Test
    fun duplicateNotificationDoesNotCreateParallelWakeup() {
        val gate = FarolNotificationWakeGate0169(duplicateWindowMillis = 300L)
        val first = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L)
        val duplicate = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_100L)
        val later = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_400L)
        assertNotNull(first)
        assertNull(duplicate)
        assertNotNull(later)
        assertFalse(gate.isCurrent(first!!, 1_401L))
        assertTrue(gate.isCurrent(later!!, 1_401L))
    }

    @Test
    fun passiveLauncherEventsAreDeferredOnlyInsideTokenTtl() {
        val gate = FarolNotificationWakeGate0169(tokenTtlMillis = 1_000L)
        val token = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L)!!
        assertTrue(gate.shouldDeferPassiveRejection("com.android.systemui", "com.sec.android.app.launcher", "br.com.mapeiaia.rotacerta", 1_500L))
        assertFalse(gate.shouldDeferPassiveRejection("sinet.startup.indriver", "sinet.startup.indriver", "br.com.mapeiaia.rotacerta", 1_500L))
        assertFalse(gate.isCurrent(token, 2_001L))
    }
}
