package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCardRuntimeContinuity0707Test {
    @Test
    fun freshLeaseHoldsOnlyForSameVisiblePackage() {
        val lease = LearnedCardRuntimeContinuity0707.renew("com.ubercab.driver", 10_000L)
        assertTrue(
            LearnedCardRuntimeContinuity0707.shouldHold(
                lease, "com.ubercab.driver", 11_200L, surfaceActive = true,
            ),
        )
        assertFalse(
            LearnedCardRuntimeContinuity0707.shouldHold(
                lease, "sinet.startup.indriver", 11_200L, surfaceActive = true,
            ),
        )
        assertFalse(
            LearnedCardRuntimeContinuity0707.shouldHold(
                lease, "com.ubercab.driver", 11_200L, surfaceActive = false,
            ),
        )
    }

    @Test
    fun expiredLeaseCannotKeepOldCardAlive() {
        val lease = LearnedCardRuntimeContinuity0707.renew("com.ubercab.driver", 10_000L)
        assertFalse(
            LearnedCardRuntimeContinuity0707.shouldHold(
                lease,
                "com.ubercab.driver",
                10_000L + LearnedCardRuntimeContinuity0707.LEASE_MS + 1L,
                surfaceActive = true,
            ),
        )
    }

    @Test
    fun liveServiceChecksLearnedContinuityBeforeHardClear() {
        val root = File(System.getProperty("user.dir")).let { cwd ->
            if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        }
        val live = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val routeNull = live.indexOf("if (routeAuthorization0188 == null)")
        val hold = live.indexOf("if (learnedCardRuntimeHold0707)", startIndex = routeNull)
        val hardClear = live.indexOf("hardClearUniversalTwoAddress(", startIndex = routeNull)
        assertTrue(routeNull >= 0)
        assertTrue(hold > routeNull)
        assertTrue(hardClear > hold)
        assertTrue(live.contains("learnedNoCandidateHold0707"))
        assertTrue(live.contains("LearnedCardRuntimeContinuity0707.HOLD_MARKER"))
    }
}
