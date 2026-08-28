package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaDurability0275Test {
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun groupedDateHeaderResolvesTomorrowEvenWhenCardStartsWithTimeOnlyEvidence() {
        assertEquals(
            LocalDate.of(2026, 8, 25),
            BlaBlaDomNormalizer.parseDate("10:30 | 14:30 | Ter., 25 de agosto", today),
        )
    }

    @Test
    fun rideListCollectorUsesNearestPrecedingCalendarHeaderAsFallback() {
        val source = File("src/main/assets/blablacar/scripts/ride_list.js").readText()
        assertTrue(source.contains("looksLikeCalendarDate"))
        assertTrue(source.contains("nearestPrecedingDateEvidence"))
        assertTrue(source.contains("node.compareDocumentPosition(root) & Node.DOCUMENT_POSITION_FOLLOWING"))
    }

    @Test
    fun clearingTimelineIsVisualOnlyAndPreservesPassengerHistoryAndLocalBookings() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val storeSource = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()
        assertTrue(source.contains("archiveStore.clearExternal(physical)"))
        assertTrue(source.contains("fun clearExternal(entries: List<TripTimelineEntry>)"))
        assertTrue(source.contains(".filter(::hasExternalPublication)"))
        assertTrue(source.contains("TIMELINE_VISUAL_CLEARED_BY_USER"))
        assertTrue(source.contains("passengerHistoryPreserved=true"))
        assertTrue(source.contains("localBookingsPreserved=true"))
        assertTrue(!source.contains("store.clearTimelineLocalData()"))
        assertTrue(storeSource.contains("Timeline cleanup must never delete canonical local trips"))
        assertTrue(storeSource.contains("fun clearTimelineLocalData(): Pair<Int, Int> = 0 to 0"))
    }

    @Test
    fun directionLabelsAreExplicitAndFailClosedWhenUnknown() {
        assertEquals("↑ IDA", timelineDirectionDisplayLabel(TimelineDirectionState.OUTBOUND))
        assertEquals("↓ VOLTA", timelineDirectionDisplayLabel(TimelineDirectionState.INBOUND))
        assertEquals("↔ NEUTRA", timelineDirectionDisplayLabel(TimelineDirectionState.NEUTRAL))
        assertEquals(null, timelineDirectionDisplayLabel(TimelineDirectionState.UNKNOWN))
    }

    @Test
    fun publicSearchRemainsAttachedToAgendaToolbar() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()
        assertTrue(source.contains("Consulta pública"))
        assertTrue(source.contains("BlaBlaPublicSearchStore"))
        assertTrue(source.contains("BlaBlaPublicSearchPanel"))
    }
}
