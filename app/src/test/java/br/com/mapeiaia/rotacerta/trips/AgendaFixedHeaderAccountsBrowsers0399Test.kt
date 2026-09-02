package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaFixedHeaderAccountsBrowsers0399Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun sharedHeaderIsScaffoldTopBarAndContentOwnsScrolling() {
        val activity = source("TripsActivity.kt")
        val header = source("AgendaHeaderNavigation0396.kt")
        val shell = activity.substringAfter("AgendaModuleDrawer0396(")

        assertTrue(shell.contains("topBar = {"))
        assertTrue(shell.contains("AgendaModuleHeader0396("))
        assertTrue(shell.indexOf("AgendaModuleHeader0396(") < shell.indexOf(".verticalScroll(rememberScrollState())"))
        assertTrue(header.contains(".statusBarsPadding()"))
        assertFalse(header.contains("scrollBehavior"))
        assertFalse(header.contains("nestedScroll"))
        assertFalse(header.contains("horizontalScroll"))
    }

    @Test
    fun allTripsOverflowContainsOnlyContextActions() {
        val activity = source("TripsActivity.kt")
        val actions = activity
            .substringAfter("TripScreen.TIMELINE -> listOf(")
            .substringBefore("else -> emptyList()")

        listOf(
            "Nova viagem",
            "Adicionar passageiro",
            "Vagas extra",
            "Próximas / arquivadas",
            "Baixar Timeline",
            "Fixar atalho",
        ).forEach { label ->
            assertTrue(actions.contains("AgendaHeaderAction0396(\"$label\")"), "Context action missing: $label")
        }
        listOf(
            "Contas e navegadores",
            "Sincronização automática",
            "Notificações",
            "Veículo",
            "⬇️ Baixar Timeline",
        ).forEach { label ->
            assertFalse(actions.contains("AgendaHeaderAction0396(\"$label\")"), "Misclassified action returned: $label")
        }
    }

    @Test
    fun timelineDownloadLivesInOverflowAndKeepsExistingMechanism() {
        val activity = source("TripsActivity.kt")
        val timeline = source("TripTimelineUi.kt")
        val download = source("AgendaTimelineDownload0398.kt")
        val header = source("AgendaHeaderNavigation0396.kt")
        val actions = activity
            .substringAfter("TripScreen.TIMELINE -> listOf(")
            .substringBefore("else -> emptyList()")

        assertTrue(actions.contains("AgendaHeaderAction0396(\"Baixar Timeline\")"))
        assertTrue(header.contains("DOWNLOAD_TIMELINE"))
        assertTrue(activity.contains("AgendaTimelineCommand0396.DOWNLOAD_TIMELINE"))
        assertTrue(timeline.contains("AgendaTimelineDownloadAction0399("))
        assertTrue(timeline.contains("triggerToken = downloadRequestToken0399"))
        assertTrue(download.contains("ActivityResultContracts.CreateDocument(\"application/json\")"))
        assertFalse(download.contains("Button("))
    }

    @Test
    fun accountsAndBrowsersAreEmbeddedInAutomaticSyncAndReuseCanonicalAuthorities() {
        val automatic = source("AgendaAutomaticSyncUi0397.kt")
        val screen = source("BlaBlaAccountsBrowsersUi0399.kt")
        val accountAuthority = source("BlaBlaDynamicAccounts.kt")
        val sessionAuthority = source("BlaBlaCollectorSessionModule.kt")

        assertTrue(automatic.contains("BlaBlaAccountsAndBrowsersScreen0399()"))
        assertTrue(screen.contains("BlaBlaDynamicAccountRegistry(context)"))
        assertTrue(screen.contains("BlaBlaDynamicSessionStore(context)"))
        assertTrue(screen.contains("BlaBlaDynamicSessionIntents.login(context, account)"))
        assertTrue(accountAuthority.contains("RotaCertaTenantRegistry(appContext).activeScope()"))
        assertTrue(accountAuthority.contains("tenantScope.key(KEY_ACCOUNTS)"))
        assertTrue(sessionAuthority.contains("RotaCertaTenantRegistry(appContext).activeScope()"))
        assertFalse(screen.contains("AgendaBackgroundSync0392"))
    }
}
