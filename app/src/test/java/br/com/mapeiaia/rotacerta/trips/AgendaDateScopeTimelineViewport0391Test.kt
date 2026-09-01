package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaDateScopeTimelineViewport0391Test {
    @Test
    fun dateScopedProgressAndSuccessStayInsideDialogWhileFailuresCanRemainVisible() {
        assertFalse(
            shouldExposeDateScopedCollectorStatusOutsideDialog0391(
                dateScoped = true,
                failure = false,
            ),
        )
        assertTrue(
            shouldExposeDateScopedCollectorStatusOutsideDialog0391(
                dateScoped = true,
                failure = true,
            ),
        )
        assertTrue(
            shouldExposeDateScopedCollectorStatusOutsideDialog0391(
                dateScoped = false,
                failure = false,
            ),
        )
    }

    @Test
    fun dateScopedCollectorUsesLocalOnlyPolicyAtEveryProgressAndSuccessBoundary() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()

        assertTrue(source.contains("AGENDA_DATE_SCOPE_STATUS_LOCAL_ONLY_0391"))
        assertTrue(source.contains("phase=requested timelineBanner=false"))
        assertTrue(source.contains("phase=account_launch timelineBanner=false"))
        assertTrue(source.contains("phase=account_complete timelineBanner=false"))
        assertTrue(source.contains("phase=published dateCount="))

        val publishCombined = source
            .substringAfter("fun publishCombined(messagePrefix: String)")
            .substringBefore("fun advanceSyncQueue()")
        assertTrue(publishCombined.contains("shouldExposeDateScopedCollectorStatusOutsideDialog0391"))
        assertTrue(publishCombined.contains("scopeDates.isNotEmpty()"))
        assertTrue(publishCombined.contains("onChanged(message.orEmpty())"))
    }

    @Test
    fun collectorFailureStillEscapesDialogAndTimelineListKeepsItsWeightedViewport() {
        val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        val failedSession = collector
            .substringAfter("} else {\n                syncing = false\n                archiving = false\n                syncDateScope = null")
            .substringBefore("} else if (accountId != null)")
        assertTrue(failedSession.contains("onChanged(message.orEmpty())"))

        assertTrue(activity.contains("modifier = Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(activity.contains("listModifier = Modifier.weight(1f)"))
        assertTrue(timeline.contains("modifier = listModifier.fillMaxWidth()"))
        assertTrue(timeline.contains("LazyColumn("))
    }
}
