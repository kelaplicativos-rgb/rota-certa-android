package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BlaBlaCollectorMainThread0588Test {
    @Test
    fun timelineStartupReusesDurableCombinedSnapshotBeforeSessionRebuild() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt",
        ).readText()
        val functionStart = source.indexOf("fun lastResponseRecoveringDynamicSessions()")
        val functionEnd = source.indexOf("internal fun clearSynchronizedTimelineData()", functionStart)
        assertTrue(functionStart >= 0)
        assertTrue(functionEnd > functionStart)
        val body = source.substring(functionStart, functionEnd)

        val durableGuard = body.indexOf("persisted?.trips?.isNotEmpty() == true")
        val combinedRebuild = body.indexOf("combinedResponse(accounts)")
        assertTrue(durableGuard >= 0)
        assertTrue(combinedRebuild > durableGuard)
    }

    @Test
    fun accountCompletionCallbacksPublishCombinedTimelineOffMain() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt",
        ).readText()
        assertTrue(source.contains("publicationScope0588.launch"))
        assertTrue(source.contains("BLABLACAR_TIMELINE_PROGRESS_OFF_MAIN_0588"))
        assertTrue(source.contains("publishCurrentSessionsAsync0588("))

        val accountFinished = source.substring(
            source.indexOf("fun onAccountFinished("),
            source.indexOf("fun onAccountTemporarilyRestricted0426(", source.indexOf("fun onAccountFinished(")),
        )
        assertTrue(accountFinished.contains("publishCurrentSessionsAsync0588("))
        assertTrue(!accountFinished.contains("publishCurrentSessions(appContext"))
    }
}
