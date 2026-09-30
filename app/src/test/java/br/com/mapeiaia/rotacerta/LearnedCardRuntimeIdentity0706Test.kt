package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCardRuntimeIdentity0706Test {
    private fun uberProfile() = RideReaderProfile0700(
        packageName = "com.ubercab.driver",
        versionName = "test",
        versionCode = 706,
        apkSha256 = "7".repeat(64),
        profileVersion = 2,
        confidence = 0.96,
        rideAnchors = listOf("UberX", "Priority"),
        actionLabels = listOf("Aceitar"),
        fareLabels = listOf("R$"),
        distanceLabels = listOf("km", "min"),
        resourceHints = listOf("ride_card", "trip_offer"),
    )

    @Test
    fun learnedUberCardIsRecognizedBeforeDestinationExists() {
        val decision = LearnedCardRuntimeIdentity0706.evaluate(
            uberProfile(),
            "com.ubercab.driver",
            "UberX\nR$ 26,00\n3 min\n2,4 km\nAceitar",
        )
        assertTrue(decision.matched)
        assertTrue(decision.actionVisible)
        assertTrue(decision.fareVisible)
    }

    @Test
    fun priorityLayoutWithoutAddressStillKeepsLocalOcrPipelineAlive() {
        val decision = LearnedCardRuntimeIdentity0706.evaluate(
            uberProfile(),
            "com.ubercab.driver",
            "Priority\nR$ 14,05\n6 min\n2,7 km\nAceitar",
        )
        assertTrue(decision.matched)
        assertTrue(decision.addressCount == 0)
    }

    @Test
    fun learnedCardWithSingleDestinationIsMatchedWithoutRequiringPickup() {
        val decision = LearnedCardRuntimeIdentity0706.evaluate(
            uberProfile(),
            "com.ubercab.driver",
            "UberX\nR$ 29,02\n5 min\nRua Jacinto Valedor, 41\nAceitar",
        )
        assertTrue(decision.matched)
    }

    @Test
    fun wrongPackageCannotBorrowLearnedIdentity() {
        val decision = LearnedCardRuntimeIdentity0706.evaluate(
            uberProfile(),
            "sinet.startup.indriver",
            "UberX\nR$ 26,00\n3 min\nAceitar",
        )
        assertFalse(decision.matched)
    }

    @Test
    fun navigationSurfaceDoesNotBecomeCardFromPackageAlone() {
        val decision = LearnedCardRuntimeIdentity0706.evaluate(
            uberProfile(),
            "com.ubercab.driver",
            "Google Maps\nSua localização\nBarra de pesquisa\nContinue por 2 km",
        )
        assertFalse(decision.matched)
    }

    @Test
    fun sourceContractKeepsIdentitySeparateFromRouteAuthorization() {
        val root = File(System.getProperty("user.dir")).let { cwd ->
            if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        }
        val source = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LearnedCardRuntimeIdentity0706.kt").readText()
        val live = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("NÃO autoriza rota, cor, raio ou quilometragem"))
        assertTrue(source.contains("CARD_PROFILE_MATCHED_0706"))
        assertTrue(source.contains("LEARNED_CARD_PAID_AI_BYPASS_0706"))
        assertTrue(live.contains("LearnedCardRuntimeIdentity0706.MATCHED_MARKER"))
        assertTrue(live.contains("LearnedCardRuntimeIdentity0706.PAID_AI_BYPASS_MARKER"))
        assertTrue(live.contains("authorizeRoute0188("))
    }
}
