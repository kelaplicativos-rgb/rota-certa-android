package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage40AuthorityRedContractTest {
    private fun root(): File {
        val c = File(System.getProperty("user.dir"))
        return if (File(c, "app/src/main/java").isDirectory) c else if (c.name == "app") c.parentFile else c
    }
    private fun src(name: String) = File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()
    private fun service() = src("LiveRideAccessibilityService.kt")

    @Test fun selectionEmptyIsAbsoluteOff() {
        val a = FarolRuntimeAuthorityStage36.Authority(1000L)
        a.updateSelection(setOf("com.ubercab.driver")); a.setUsageAccess(true); a.observeAccessibility("com.ubercab.driver")
        assertFalse(a.updateSelection(emptySet()).enabled)
    }

    @Test fun nonSelectedVisualPackageIsProvenanceOnlyWhileReadingIsOn() {
        val a = FarolRuntimeAuthorityStage36.Authority(1000L)
        a.updateSelection(setOf("com.ubercab.driver")); a.setUsageAccess(true); a.observeAccessibility("com.ubercab.driver")
        assertTrue(a.observeWindowBoundary("com.openai.chatgpt").enabled)
    }

    @Test fun runningAppProcessesRemainsShadowOnly() {
        assertTrue(src("FarolPresenceAuthorityStage30.kt").contains("RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"))
    }

    @Test fun googleDrivingRouteRemainsDistanceAuthority() {
        assertTrue(service().contains("drivingDistancesFromAddressKm("))
    }

    @Test fun orangeCannotBePublicFarolState() {
        assertFalse(service().contains("showOverlay(RadarColor.Orange"))
    }

    @Test fun oneFinalVisualAuthorityMustOwnPublicPaint() {
        assertTrue(service().contains("FarolVisualStateAuthorityStage40"))
    }

    @Test fun positivePresenceCannotRemainArmedForever() {
        assertTrue(src("FarolRuntimeAuthorityStage36.kt").contains("expireStalePresence"))
    }

    @Test fun twoVisibleAddressesRemainMinimumForFinalCandidate() {
        assertTrue(src("FarolCausalCorrectionStage21.kt").contains("addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES"))
    }
}
