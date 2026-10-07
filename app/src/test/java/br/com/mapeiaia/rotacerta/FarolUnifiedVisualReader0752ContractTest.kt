package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolUnifiedVisualReader0752ContractTest {
    private fun source(relative: String): String =
        File(System.getProperty("user.dir"), relative).readText()

    @Test
    fun farol_reuses_the_same_visual_reader_as_manual_text_and_phone_shortcuts() {
        val reader = source("src/main/java/br/com/mapeiaia/rotacerta/ScreenVisualReader0742.kt")
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        assertTrue(reader.contains("Address"))
        assertTrue(reader.contains("ScreenAddressVisualAuthority0752.orderedAddresses"))
        assertTrue(reader.contains("if (addresses0752.size >= 2)"))
        assertTrue(live.contains("ScreenVisualReader0742(ocrService).read"))
        assertTrue(live.contains("purpose = VisualReadPurpose0742.Address"))
        assertTrue(live.contains("purpose = VisualReadPurpose0742.Phone"))
        assertTrue(live.contains("purpose = VisualReadPurpose0742.FullText"))
    }

    @Test
    fun authorized_root_miss_and_unselected_apps_escalate_to_shared_visual_reader() {
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        val rootMiss = live.indexOf("reason=authorized_root_not_observed")
        val rootEscalation = live.indexOf("scheduleUniversalAddressVisual0752", rootMiss)
        assertTrue(rootMiss >= 0 && rootEscalation > rootMiss)
        val blocked = live.indexOf("\"BUBBLE_PACKAGE_BLOCKED\"")
        val blockedEscalation = live.indexOf("scheduleUniversalAddressVisual0752", blocked)
        assertTrue(blocked >= 0 && blockedEscalation > blocked)
        assertTrue(live.contains("takeManualVisualScreenshot0742("))
        assertTrue(live.contains("takeScreenshotOfWindow"))
    }

    @Test
    fun two_or_more_visual_addresses_can_bypass_selected_app_ownership_only_through_explicit_pair_authority() {
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        val authority = source("src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalAddressPairAuthority0752.kt")
        assertTrue(authority.contains("ANY_APP_TWO_OR_MORE_ADDRESSES_0752"))
        assertTrue(authority.contains("addresses.distinctBy(::canonical).size >= 2"))
        assertTrue(authority.contains("ANY_APP_LAST_ADDRESS_ROUTE_TRIGGER_0752"))
        assertTrue(live.contains("universalAddressPair0752"))
        assertTrue(live.contains("!ownershipStage47.owned && !universalPairOwned0752"))
        assertTrue(live.contains("sourceStage19 = \"SharedVisualReader0752\""))
        assertTrue(live.contains("universalAddressPair0752 = true"))
    }

    @Test
    fun visual_pair_destination_is_last_and_never_haversine_public_km() {
        val authority = source("src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalAddressPairAuthority0752.kt")
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        assertTrue(authority.contains("val destination = ordered.last()"))
        assertTrue(live.contains("FarolRoadKmFinality0713.DistanceAuthority.ROAD_CONFIRMED"))
        assertFalse(live.contains("distanceAuthority0713 = FarolRoadKmFinality0713.DistanceAuthority.HAVERSINE"))
    }
}
