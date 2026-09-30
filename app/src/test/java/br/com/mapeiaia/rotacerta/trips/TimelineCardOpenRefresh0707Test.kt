package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCardOpenRefresh0707Test {
    @Test
    fun `only external return queues one follow up when the exact card is busy`() {
        assertFalse(
            operationalTripCardRefreshQueuesFollowUp0707(
                OperationalTripCardRefreshReason0707.MANUAL,
            ),
        )
        assertFalse(
            operationalTripCardRefreshQueuesFollowUp0707(
                OperationalTripCardRefreshReason0707.CARD_OPEN,
            ),
        )
        assertTrue(
            operationalTripCardRefreshQueuesFollowUp0707(
                OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR,
            ),
        )
    }

    @Test
    fun `generic resume suppression is one shot and cancellable`() {
        OperationalTimelineExternalResume0707.cancelExternalNavigation()
        assertFalse(OperationalTimelineExternalResume0707.consumeGlobalResumeSuppression())

        OperationalTimelineExternalResume0707.markExternalNavigationStarted()
        assertTrue(OperationalTimelineExternalResume0707.consumeGlobalResumeSuppression())
        assertFalse(OperationalTimelineExternalResume0707.consumeGlobalResumeSuppression())

        OperationalTimelineExternalResume0707.markExternalNavigationStarted()
        OperationalTimelineExternalResume0707.cancelExternalNavigation()
        assertFalse(OperationalTimelineExternalResume0707.consumeGlobalResumeSuppression())
    }

    @Test
    fun `source contract keeps card open and return refresh exact and event driven`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        val browser = File(
            root,
            "app/src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        val activity = File(
            root,
            "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt",
        ).readText()

        assertTrue(browser.contains("CentralDayCommandBridge0552.refreshTripDirect0662"))
        assertTrue(browser.contains("OperationalTripCardRefreshReason0707.CARD_OPEN"))
        assertTrue(browser.contains("OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR"))
        assertTrue(browser.contains("TIMELINE_CARD_OPEN_REFRESH_0707"))
        assertTrue(browser.contains("TIMELINE_CARD_RETURN_REFRESH_0707"))
        assertTrue(browser.contains("TIMELINE_CARD_RETURN_REFRESH_COALESCED_0707"))
        assertTrue(browser.contains("OperationalTripBrowserIntents0564.open"))
        assertTrue(browser.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(browser.contains("noPolling=true"))
        assertTrue(browser.contains("scope=TRIP_ONLY"))
        assertFalse(browser.contains("delay(5_000"))
        assertFalse(browser.contains("delay(5000"))

        assertTrue(activity.contains("OperationalTimelineExternalResume0707.consumeGlobalResumeSuppression()"))
        assertTrue(activity.contains("TIMELINE_CARD_GLOBAL_RESUME_SKIPPED_0707"))
        assertTrue(activity.contains("fullSnapshotReload=false"))
    }
}
