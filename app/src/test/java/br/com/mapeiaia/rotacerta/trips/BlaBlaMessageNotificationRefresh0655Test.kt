package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaMessageNotificationRefresh0655Test {
    @Test
    fun notificationFilterAcceptsOnlyBlaBlaCarPackages() {
        assertTrue(isBlaBlaNotificationPackage0655("com.comuto"))
        assertTrue(isBlaBlaNotificationPackage0655("com.comuto.beta"))
        assertTrue(isBlaBlaNotificationPackage0655("com.blablacar"))
        assertFalse(isBlaBlaNotificationPackage0655("br.com.mapeiaia.rotacerta"))
        assertFalse(isBlaBlaNotificationPackage0655("com.whatsapp"))
    }

    @Test
    fun parsesConversationAndObservedGroupFromRealMessagesHrefShape() {
        val href =
            "https://www.blablacar.com.br/messages/show/" +
                "01a0c63d-f537-7d53-bf6f-b044620b75cd_1-c3f0d2e512bc3134950245666"

        assertEquals(
            "01a0c63d-f537-7d53-bf6f-b044620b75cd_1-c3f0d2e512bc3134950245666",
            messageConversationKey0655(href),
        )
        assertEquals(
            "01a0c63d-f537-7d53-bf6f-b044620b75cd",
            messageGroupKey0655(href),
        )
    }

    @Test
    fun firstSuccessfulMessagesHtmlBecomesBaselineWithoutRefreshingCards() {
        val incoming = snapshot(
            at = 100L,
            entry("conversation-a", "2026-09-26T08:47:53Z"),
        )

        val delta = messageIndexDelta0655(null, incoming)

        assertTrue(delta.baseline)
        assertTrue(delta.changed.isEmpty())
        assertEquals(1, delta.merged.entries.size)
    }

    @Test
    fun newerActivityChangesOnlyThatConversation() {
        val previous = snapshot(
            at = 100L,
            entry("conversation-a", "2026-09-26T08:40:00Z"),
            entry("conversation-b", "2026-09-26T08:45:00Z"),
        )
        val incoming = snapshot(
            at = 200L,
            entry("conversation-a", "2026-09-26T08:47:53Z"),
            entry("conversation-b", "2026-09-26T08:45:00Z"),
        )

        val delta = messageIndexDelta0655(previous, incoming)

        assertFalse(delta.baseline)
        assertEquals(listOf("conversation-a"), delta.changed.map { it.conversationKey })
        assertEquals(
            "2026-09-26T08:47:53Z",
            delta.merged.entries.single { it.conversationKey == "conversation-a" }.lastActivity,
        )
    }

    @Test
    fun staleIncomingActivityNeverRollsBackLatestWinsSnapshot() {
        val previous = snapshot(
            at = 200L,
            entry("conversation-a", "2026-09-26T08:47:53Z"),
        )
        val incoming = snapshot(
            at = 300L,
            entry("conversation-a", "2026-09-26T08:40:00Z"),
        )

        val delta = messageIndexDelta0655(previous, incoming)

        assertTrue(delta.changed.isEmpty())
        assertEquals(
            "2026-09-26T08:47:53Z",
            delta.merged.entries.single().lastActivity,
        )
    }

    @Test
    fun newConversationAfterBaselineIsAnInvalidation() {
        val previous = snapshot(
            at = 100L,
            entry("conversation-a", "2026-09-26T08:40:00Z"),
        )
        val incoming = snapshot(
            at = 200L,
            entry("conversation-a", "2026-09-26T08:40:00Z"),
            entry("conversation-new", "2026-09-26T08:50:00Z"),
        )

        val delta = messageIndexDelta0655(previous, incoming)

        assertEquals(listOf("conversation-new"), delta.changed.map { it.conversationKey })
    }

    @Test
    fun parsesPortugueseRouteFromMessagesIndex() {
        assertEquals(
            "tres coracoes" to "sao paulo",
            messageRouteEndpoints0655("Três Corações → São Paulo"),
        )
    }

    private fun entry(
        conversation: String,
        activity: String,
    ) = BlaBlaMessageIndexEntry0655(
        conversationKey = conversation,
        href = "https://www.blablacar.com.br/messages/show/" + conversation,
        passengerName = "Sandra",
        routeText = "Três Corações → São Paulo",
        lastActivity = activity,
    )

    private fun snapshot(
        at: Long,
        vararg entries: BlaBlaMessageIndexEntry0655,
    ) = BlaBlaMessageIndexSnapshot0655(
        capturedAtMillis = at,
        entries = entries.toList(),
    )
}
