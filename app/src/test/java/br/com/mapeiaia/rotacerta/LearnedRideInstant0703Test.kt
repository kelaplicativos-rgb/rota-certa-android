package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedRideInstant0703Test {
    private fun profile(
        destinationLabels: List<String> = emptyList(),
        pickupLabels: List<String> = emptyList(),
        rideAnchors: List<String> = listOf("Nova corrida"),
        actionLabels: List<String> = listOf("Aceitar"),
    ) = RideReaderProfile0700(
        packageName = "com.app99.driver",
        versionName = "test",
        versionCode = 703,
        apkSha256 = "7".repeat(64),
        confidence = 0.93,
        pickupLabels = pickupLabels,
        destinationLabels = destinationLabels,
        rideAnchors = rideAnchors,
        actionLabels = actionLabels,
        resourceHints = listOf("res/layout/ride_card.xml"),
    )

    @Test
    fun explicitDestinationWithoutPickupIsAppliedAndAuthorized() {
        val result = LearnedRideReader0700.apply(
            profile(destinationLabels = listOf("Destino")),
            "com.app99.driver",
            "Nova corrida\nDestino\nRua Jacinto Valedor, 41\nR$ 18,90\n3 min\nAceitar",
        )
        assertTrue(result.applied)
        assertNull(result.pickup)
        assertEquals("Rua Jacinto Valedor, 41", result.destination)
        assertTrue(result.text.contains(LearnedRideReader0700.DESTINATION_ONLY_MARKER))
        assertTrue(LearnedRideInstantPolicy0703.canAuthorizeDestination(result))
    }

    @Test
    fun oneVisibleAddressCanBeRecoveredFromLearnedCardSignals() {
        val result = LearnedRideReader0700.apply(
            profile(),
            "com.app99.driver",
            "Nova corrida\nR$ 18,90\n3 min\nRua Jacinto Valedor, 41\nAceitar",
        )
        assertTrue(result.applied)
        assertEquals("Rua Jacinto Valedor, 41", result.destination)
        assertTrue(result.text.contains(LearnedRideReader0700.UNIQUE_ADDRESS_MARKER))
    }

    @Test
    fun aSingleAddressWithoutRideEvidenceIsNotGuessed() {
        val result = LearnedRideReader0700.apply(profile(), "com.app99.driver", "Rua Jacinto Valedor, 41")
        assertFalse(result.applied)
    }

    @Test
    fun multipleAddressesRemainFailClosedWithoutExplicitDestinationLabel() {
        val result = LearnedRideReader0700.apply(
            profile(),
            "com.app99.driver",
            "Nova corrida\nR$ 18,90\nRua Afonso Pena, 100\nRua Jacinto Valedor, 41\nAceitar",
        )
        assertFalse(result.applied)
    }

    @Test
    fun finalKmIsPreservedOnlyForSameLearnedPackageAndCurrentSession() {
        assertTrue(
            LearnedRideInstantPolicy0703.shouldPreserveFinalOnPartialRead(
                selectedPackage = "com.app99.driver",
                activePackage = "com.app99.driver",
                hasActiveAddress = true,
                finalPaintVisible = true,
                sessionCurrent = true,
                learnedProfileUsable = true,
            ),
        )
        assertFalse(
            LearnedRideInstantPolicy0703.shouldPreserveFinalOnPartialRead(
                selectedPackage = "com.app99.driver",
                activePackage = "outro.driver",
                hasActiveAddress = true,
                finalPaintVisible = true,
                sessionCurrent = true,
                learnedProfileUsable = true,
            ),
        )
    }

    @Test
    fun incompleteAddressGoogleBiasComesOnlyFromConfiguredTargets() {
        val service = GoogleMapsService()
        assertEquals(
            "-23.900500,-46.983300|-23.200500,-46.283300",
            service.targetBiasBounds0703(listOf(Coordinate(latitude = -23.5505, longitude = -46.6333))),
        )
        assertNull(service.targetBiasBounds0703(emptyList()))
    }

    @Test
    fun sourceContractKeepsColorDecisionLocalAndAddsLearnedFastPath() {
        val cwd = File(System.getProperty("user.dir"))
        val root = if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        val live = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val maps = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt").readText()
        assertTrue(live.contains("LearnedRideInstantPolicy0703.DESTINATION_AUTH_MARKER"))
        assertTrue(live.contains("learned-profile-0703"))
        assertTrue(live.contains("LearnedRideInstantPolicy0703.PARTIAL_PRESERVE_MARKER"))
        assertTrue(live.contains("showOverlay(colorChecklist13, distanceChecklist13)"))
        assertTrue(maps.contains("GOOGLE_TARGET_BIAS_0703"))
        assertTrue(maps.contains("resolveGoogleOrigin0697(originAddress, targetHints"))
    }
}
