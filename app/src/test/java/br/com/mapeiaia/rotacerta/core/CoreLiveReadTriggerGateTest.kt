package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLiveReadTriggerGateTest {
    @Test
    fun firstMonitoredEventIsImmediateAndBurstIsGrouped() {
        val gate = CoreLiveReadTriggerGate(duplicateWindowMs = 120L)

        val first = gate.decide(
            eventPackageName = "sinet.startup.indriver",
            rootPackageName = "sinet.startup.indriver",
            eventType = 0x800,
            eventPackageIsMonitored = true,
            rootPackageIsMonitored = true,
            nowMillis = 1_000L,
        )
        val duplicate = gate.decide(
            eventPackageName = "sinet.startup.indriver",
            rootPackageName = "sinet.startup.indriver",
            eventType = 0x800,
            eventPackageIsMonitored = true,
            rootPackageIsMonitored = true,
            nowMillis = 1_040L,
        )
        val afterWindow = gate.decide(
            eventPackageName = "sinet.startup.indriver",
            rootPackageName = "sinet.startup.indriver",
            eventType = 0x800,
            eventPackageIsMonitored = true,
            rootPackageIsMonitored = true,
            nowMillis = 1_121L,
        )

        assertEquals(CoreLiveReadTriggerAction.Analyze, first.action)
        assertEquals(CoreLiveReadTriggerAction.IgnoreDuplicate, duplicate.action)
        assertEquals(CoreLiveReadTriggerAction.Analyze, afterWindow.action)
    }

    @Test
    fun windowBoundaryAlwaysPassesImmediately() {
        val gate = CoreLiveReadTriggerGate(duplicateWindowMs = 500L)
        gate.decide("sinet.startup.indriver", "sinet.startup.indriver", 0x800, true, true, 2_000L)

        val boundary = gate.decide(
            eventPackageName = "sinet.startup.indriver",
            rootPackageName = "sinet.startup.indriver",
            eventType = CoreLiveReadTriggerGate.TYPE_WINDOW_STATE_CHANGED,
            eventPackageIsMonitored = true,
            rootPackageIsMonitored = true,
            nowMillis = 2_010L,
        )

        assertEquals(CoreLiveReadTriggerAction.Analyze, boundary.action)
    }

    @Test
    fun passiveScreenStillFlowsToImmediateCleanup() {
        val gate = CoreLiveReadTriggerGate()

        val passive = gate.decide(
            eventPackageName = "com.android.settings",
            rootPackageName = "com.android.settings",
            eventType = CoreLiveReadTriggerGate.TYPE_WINDOW_STATE_CHANGED,
            eventPackageIsMonitored = false,
            rootPackageIsMonitored = false,
            nowMillis = 3_000L,
        )

        assertEquals(CoreLiveReadTriggerAction.LetPassiveFlow, passive.action)
    }
}
