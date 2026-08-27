package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicLinkDebug0302Test {
    @Test
    fun remoteDebugModelCarriesOnlyDiagnosticMetadata() {
        val event = RemotePublicDebugEvent(
            id = "evt-1",
            event = "PUBLIC_RESERVATION_CREATED",
            source = "server",
            sessionId = "session-1",
            targetType = "trip",
            targetRefHash = "abc123",
            screen = "trip",
            reason = "",
            statusCode = 201,
            seats = 2,
            fromIndex = 0,
            toIndex = 1,
            replayed = false,
            createdAtMillis = 1234L,
        )
        assertEquals("PUBLIC_RESERVATION_CREATED", event.event)
        assertEquals("abc123", event.targetRefHash)
        assertEquals(2, event.seats)
        assertFalse(event.replayed)
        assertTrue(DriverPublicDebugEventsResponse(listOf(event)).events.isNotEmpty())
    }
}
