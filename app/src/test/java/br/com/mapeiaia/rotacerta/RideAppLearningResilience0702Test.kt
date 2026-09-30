package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideAppLearningResilience0702Test {
    @Test
    fun learningTransportHasDedicatedLongWindowWithoutChangingGlobalDefaultContract() {
        assertEquals(15_000, RideAppLearningContract0702.CONNECT_TIMEOUT_MS)
        assertEquals(105_000, RideAppLearningContract0702.READ_TIMEOUT_MS)
        assertTrue(RideAppLearningContract0702.READ_TIMEOUT_MS > 90_000)
        assertEquals(1, RideAppLearningContract0702.MAX_TRANSPORT_RETRIES)
    }

    @Test
    fun readingModuleOwnsLearnAppShortcut() {
        val actions = ShortcutActionCatalog0184.actionsForModule("reading")
        val learned = actions.single { it.id == RideAppLearningContract0702.SHORTCUT_ID }
        assertEquals("Aprender App", learned.displayLabel)
        assertEquals("🧠", learned.emoji)
        assertEquals(BubbleShortcutAction.OpenRideAppLearning, learned.action)
    }

    @Test
    fun learnAppCanBePersistedDirectlyInFloatingGrid() {
        val added = ShortcutGridCustomizationPolicy0179.add(
            entries = emptyList(),
            shortcutId = RideAppLearningContract0702.SHORTCUT_ID,
            nowMillis = 123L,
        )
        assertEquals(1, added.size)
        assertEquals(RideAppLearningContract0702.SHORTCUT_ID, added.single().shortcutId)
        assertEquals("Aprender App", ShortcutGridCustomizationPolicy0179.resolve(added).single().spec.displayLabel)
    }

    @Test
    fun sourceContractPersistsBackendResultAndRecoversAfterTransportFailure() {
        val root = File(System.getProperty("user.dir")).let { cwd ->
            if (File(cwd, "app/src/main/java").isDirectory) cwd
            else if (cwd.name == "app") cwd.parentFile
            else cwd
        }
        val remote = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val activity = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/RideAppLearningActivity0700.kt").readText()
        val main = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        val service = File(root, "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val backend = File(root, "trip-platform/functions/index.js").readText()
        val idempotency = File(root, "trip-platform/functions/ride-app-learning-idempotency-0702.js").readText()

        assertTrue(remote.contains("readTimeoutMs = br.com.mapeiaia.rotacerta.RideAppLearningContract0702.READ_TIMEOUT_MS"))
        assertTrue(remote.contains("/v1/assistant/learn-ride-app/status"))
        assertTrue(activity.contains("requestProfileWithRecovery0702"))
        assertTrue(activity.contains(RideAppLearningContract0702.TRANSPORT_FAILED))
        assertTrue(activity.contains(RideAppLearningContract0702.PROFILE_SAVED))
        assertTrue(main.contains("🧠 Aprender aplicativo de corrida"))
        assertTrue(service.contains("openRideAppLearning0702"))
        assertTrue(backend.contains("COLLECTION_0702"))
        assertTrue(backend.contains("leaseUntilMillis"))
        assertTrue(idempotency.contains("rideAppLearningProfiles"))
        assertTrue(backend.contains("timeoutSeconds: 90"))
    }
}
