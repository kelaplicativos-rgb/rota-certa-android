package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaSilentPublicSync0386Test {
    private fun activitySource(): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

    @Test
    fun successfulPublicAgendaSyncDoesNotRenderStatusCard() {
        val activity = activitySource()
        val start = activity.indexOf("publicAgendaSyncCoordinator.completions.collect")
        val end = activity.indexOf("BookingRealtimeEvents0356.changes.collect", start)
        assertTrue(start >= 0 && end > start)
        val block = activity.substring(start, end)

        assertFalse(
            block.contains("Agenda pública atualizada:"),
            "Successful background sync must not create the large Agenda status card",
        )
        assertTrue(block.contains("result.localPublished + result.externalPublished > 0"))
        assertTrue(block.contains("refresh()"))
        assertTrue(block.contains("message = null"))
    }

    @Test
    fun realPublicAgendaFailuresRemainVisible() {
        val activity = activitySource()
        assertTrue(
            activity.contains("Não foi possível enviar as viagens para a Agenda Pública. Tente abrir a Agenda novamente."),
        )
        assertTrue(
            activity.contains("Não foi possível sincronizar a Agenda Pública. A próxima mudança real tentará novamente."),
        )
    }

    @Test
    fun successfulSyncStillKeepsInternalAuditEvidence() {
        val activity = activitySource()
        assertTrue(activity.contains("timeline_public_agenda_coordinator_result local="))
        assertTrue(activity.contains("durationMs=\${completion.durationMs}"))
        assertTrue(activity.contains("BookingPushRegistration0304.ensureRegistered(activity, store)"))
    }
}
