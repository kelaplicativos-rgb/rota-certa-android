package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AgendaPublicOpenDrawer0487Test {
    private val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val automaticSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaAutomaticSyncUi0397.kt").readText()

    @Test
    fun drawerUsesConfiguredPublicAgendaActionInsteadOfInventingAnotherRoute() {
        assertTrue(header.contains("Text(\"Abrir Agenda Pública\""))
        assertTrue(header.contains("if (publicAgendaEnabled)"))
        assertTrue(header.contains("onOpenPublicAgenda()"))
        assertTrue(activity.contains("publicAgendaEnabled = drawerOnlineSettings0397.configured"))
        assertTrue(activity.contains("!drawerOnlineSettings0397.publicAgendaUrl.isNullOrBlank()"))
        assertTrue(activity.contains("message = openPublicAgenda0397(activity, store)"))
    }

    @Test
    fun drawerAndBlaBlaCarButtonShareOneOpeningAuthority() {
        assertTrue(automaticSync.contains("internal fun openPublicAgenda0397("))
        assertTrue(automaticSync.contains("val url = store.onlineSettings().publicAgendaUrl"))
        assertTrue(automaticSync.contains("Intent(Intent.ACTION_VIEW, Uri.parse(url))"))
        assertTrue(automaticSync.contains("message = openPublicAgenda0397(context, store)"))
        assertTrue(automaticSync.contains("Text(\"ABRIR AGENDA PÚBLICA\")"))
    }
}
