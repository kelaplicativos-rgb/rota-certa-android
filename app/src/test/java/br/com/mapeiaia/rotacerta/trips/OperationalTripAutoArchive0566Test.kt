package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalTripAutoArchive0566Test {
    private data class Window(
        val id: String,
        val departure: Long,
        val arrival: Long?,
    )

    private fun select(items: List<Window>, now: Long) = operationalArchiveSelection0566(
        items = items,
        nowMillis = now,
        departureAtMillis = Window::departure,
        arrivalAtMillis = Window::arrival,
    )

    @Test
    fun `trip stays active after departure while canonical arrival is still ahead`() {
        val now = 1_000_000L
        val ongoing = Window("ongoing", now - 2L * 60L * 60_000L, now + 2L * 60L * 60_000L)
        val future = Window("future", now + 3L * 60L * 60_000L, now + 5L * 60L * 60_000L)

        val result = select(listOf(future, ongoing), now)

        assertEquals(listOf("ongoing", "future"), result.active.map(Window::id))
        assertTrue(result.archived.isEmpty())
    }

    @Test
    fun `trip remains active during one hour post arrival grace`() {
        val now = 2_000_000L
        val justArrived = Window(
            "grace",
            now - 3L * 60L * 60_000L,
            now - OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577 + 1L,
        )

        val result = select(listOf(justArrived), now)

        assertEquals(listOf("grace"), result.active.map(Window::id))
        assertTrue(result.archived.isEmpty())
    }

    @Test
    fun `trip archives only after canonical arrival plus grace expires`() {
        val now = 3_000_000L
        val expired = Window(
            "expired",
            now - 4L * 60L * 60_000L,
            now - OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577 - 1L,
        )

        val result = select(listOf(expired), now)

        assertTrue(result.active.isEmpty())
        assertEquals(listOf("expired"), result.archived.map(Window::id))
    }

    @Test
    fun `missing arrival uses canonical safe retention instead of archiving at departure`() {
        val now = 20L * 60L * 60_000L
        val stillOperational = Window(
            "unknown-active",
            now - OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 + 1L,
            null,
        )
        val expired = Window(
            "unknown-expired",
            now - OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 - 1L,
            null,
        )

        val result = select(listOf(expired, stillOperational), now)

        assertEquals(listOf("unknown-active"), result.active.map(Window::id))
        assertEquals(listOf("unknown-expired"), result.archived.map(Window::id))
    }

    @Test
    fun `archived trips are ordered from most recent departure to oldest`() {
        val now = 50L * 60L * 60_000L
        val items = listOf(
            Window("oldest", now - 30L * 60L * 60_000L, now - 20L * 60L * 60_000L),
            Window("newest", now - 10L * 60L * 60_000L, now - 5L * 60L * 60_000L),
            Window("middle", now - 20L * 60L * 60_000L, now - 10L * 60L * 60_000L),
        )

        val result = select(items, now)

        assertEquals(listOf("newest", "middle", "oldest"), result.archived.map(Window::id))
    }

    @Test
    fun `operational screen advances clock and passes canonical arrival into archive partition`() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        assertTrue(source.contains("operationalArchiveSelection0566"))
        assertTrue(source.contains("arrivalAtMillis = { row -> row.entry.arrivalAtMillis }"))
        assertTrue(source.contains("System.currentTimeMillis()"))
        assertTrue(source.contains("delay(waitMillis)"))
        assertTrue(source.contains("Viagens arquivadas"))
    }
}
