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
    fun allTripsOverflowExposesConfigurationAndCentralAutomaticSyncOnly() {
        val activity = source("TripsActivity.kt")
        val actions = activity
            .substringAfter("TripScreen.TIMELINE -> listOf(")
            .substringBefore("TripScreen.PUBLIC_SEARCH ->")

        assertTrue(actions.contains("AgendaHeaderAction0396(\"Contas e navegadores\")"))
        assertTrue(actions.contains("AgendaHeaderAction0396(\"Sincronização automática\")"))
        assertTrue(activity.contains("TripScreen.ACCOUNTS_BROWSERS -> BlaBlaAccountsAndBrowsersScreen0399()"))
        listOf("Sincronizar agora", "Sincronizar BlaBlaCar", "Publicar agenda", "Limpar Timeline", "Limpar Agenda").forEach { label ->
            assertFalse(actions.contains("AgendaHeaderAction0396(\"$label\""), "Legacy action returned: $label")
        }
    }

    @Test
    fun restoredScreenReusesCanonicalAccountsBrowserProfilesAndSessionsWithoutSyncTrigger() {
        val screen = source("BlaBlaAccountsBrowsersUi0399.kt")
        val accountAuthority = source("BlaBlaDynamicAccounts.kt")
        val sessionAuthority = source("BlaBlaCollectorSessionModule.kt")
        val legacyUi = source("TripBlaBlaCollectorUi.kt")
        val timeline = source("TripTimelineUi.kt")

        assertTrue(screen.contains("BlaBlaDynamicAccountRegistry(context)"))
        assertTrue(screen.contains("BlaBlaDynamicSessionStore(context)"))
        assertTrue(screen.contains("BlaBlaDynamicSessionIntents.login(context, account)"))
        assertTrue(screen.contains("DynamicAccountRow("))
        assertTrue(legacyUi.contains("Perfil do navegador: \${account.webProfileName}"))
        assertTrue(accountAuthority.contains("profileUuid: String?"))
        assertTrue(accountAuthority.contains("webProfileName: String"))
        assertTrue(sessionAuthority.contains("BlaBlaDynamicSessionSnapshot"))
        assertFalse(screen.contains("BlaBlaDynamicSessionIntents.sync"))
        assertFalse(screen.contains("AgendaBackgroundSync0392"))
        assertFalse(screen.contains("Sincronizar todas as contas"))
        assertFalse(screen.contains("Sincronizar por data/período"))
        assertFalse(screen.contains("Tentar vagas pendentes"))
        assertFalse(timeline.contains("BlaBlaCollectorPanel("))
    }

    @Test
    fun accountAndSessionAuthoritiesUseExistingTenantStoragePolicyAndPreserveLegacyKeys() {
        val accountAuthority = source("BlaBlaDynamicAccounts.kt")
        val sessionAuthority = source("BlaBlaCollectorSessionModule.kt")
        assertTrue(accountAuthority.contains("RotaCertaTenantRegistry(appContext).activeScope()"))
        assertTrue(accountAuthority.contains("tenantScope.key(KEY_ACCOUNTS)"))
        assertTrue(sessionAuthority.contains("RotaCertaTenantRegistry(appContext).activeScope()"))
        assertTrue(sessionAuthority.contains("tenantScope.keyAlias(\"blablacar-dynamic-session-\$id\")"))
        assertTrue(sessionAuthority.contains("tenantScope.keyAlias(id)"))
    }
}
