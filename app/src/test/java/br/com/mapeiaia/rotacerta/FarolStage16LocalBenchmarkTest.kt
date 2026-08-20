package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Informational JVM/CI guardrails only. They never claim physical card-to-color latency. */
class FarolStage16LocalBenchmarkTest {
    private val uber = "com.ubercab.driver"

    private inline fun benchmark(name: String, maxMillis: Long, block: () -> Unit) {
        val started = System.nanoTime()
        block()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        println("STAGE16_BENCHMARK_$name duration_ms=$elapsedMs max_guard_ms=$maxMillis physical_claim=false")
        assertTrue("$name exceeded broad CI regression guard: ${elapsedMs}ms", elapsedMs <= maxMillis)
    }

    private fun evidence(index: Int) = FarolVisibleCardPriorityStage16.BlockEvidence(
        id = "a11y:42/$index",
        parentId = "a11y:42",
        packageName = uber,
        windowId = 42,
        windowLayer = 9,
        depth = 2,
        text = "Rua A, 10\nR$ ${18 + index},00\nRua B, 20",
        source = "Accessibility",
        left = 50,
        top = 100 + index * 5,
        right = 1000,
        bottom = 500 + index * 5,
        syntheticRoot = false,
    )

    @Test fun normalizationLocalGuard() {
        var value = ""
        benchmark("NORMALIZATION", 5_000L) {
            repeat(2_000) {
                value = WrappedAddressTextNormalizer.normalize("Rua A, 10\nR$ 18,00\nRua B, 20")
            }
        }
        assertTrue(value.contains("Rua B"))
    }

    @Test fun visibleWindowSelectionLocalGuard() {
        val windows = listOf(
            FarolVisibleCardPriorityStage16.WindowEvidence(99, "com.android.systemui", 12, FarolVisibleCardPriorityStage16.WindowKind.SYSTEM, true),
            FarolVisibleCardPriorityStage16.WindowEvidence(42, uber, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, true),
            FarolVisibleCardPriorityStage16.WindowEvidence(1, "com.google.android.apps.maps", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, true),
        )
        var result: FarolVisibleCardPriorityStage16.WindowSelection? = null
        benchmark("VISIBLE_WINDOW_SELECTION", 5_000L) {
            repeat(10_000) { result = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(windows, setOf(uber)) }
        }
        assertEquals(uber, result?.authority?.packageName)
    }

    @Test fun exactVisualIdentityLocalGuard() {
        val blocks = (0 until 30).map(::evidence)
        var identity: FarolVisibleCardPriorityStage16.GateSnapshotIdentity? = null
        benchmark("EXACT_VISUAL_IDENTITY", 5_000L) {
            repeat(1_000) {
                identity = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(uber, 7, 42, 12, 5, blocks)
            }
        }
        assertEquals(30, identity?.blocks?.size)
    }

    @Test fun duplicateFastPathLocalGuard() {
        val blocks = listOf(evidence(0))
        val identity = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(uber, 7, 42, 12, 5, blocks)
        var accepted = false
        benchmark("DUPLICATE_FAST_PATH", 5_000L) {
            repeat(10_000) {
                accepted = FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
                    identity, identity, uber, 42, "sig", 123, uber, "sig", 123,
                    routeInFlight = true, stableDecision = false, transientEmptyPending = false,
                )
            }
        }
        assertTrue(accepted)
    }

    @Test fun fullUniversalGateLocalGuard() {
        val block = FarolCardBlock0188(
            id = "card", packageName = uber, windowId = 42, windowLayer = 9, depth = 2,
            text = "Rua Apeninos, 100\nR$ 18,50\nAvenida Paulista, 1000",
            source = FarolEvidenceSource0188.Accessibility, left = 50, top = 100, right = 1000, bottom = 600,
        )
        var authorized = false
        benchmark("FULL_GATE", 15_000L) {
            repeat(100) { authorized = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(block)).authorized }
        }
        assertTrue(authorized)
    }

    @Test fun postRouteFreshnessPolicyLocalGuard() {
        var mayPaint = false
        benchmark("POST_ROUTE_BINDING", 5_000L) {
            repeat(100_000) {
                mayPaint = FarolVisibleCardPriorityStage16.routeResultMayPaint(true, false)
            }
        }
        assertTrue(mayPaint)
    }
}
