package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaSyncCrashTrace0322Test {
    @Test
    fun authenticatedSyncInstallsAgendaOnlyMainThreadCrashGuard() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(source.contains("AgendaSyncCrashGuard.install(this)"))
        assertTrue(source.contains("if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC)"))
        assertTrue(source.contains("syncCrashSnapshot()"))
        assertTrue(source.contains("AgendaSyncCrashTraceStore.checkpoint"))
    }

    @Test
    fun crashGuardRestoresOnlyAfterNormalActivityDestruction() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val destroy = source.substringAfter("override fun onDestroy() {").substringBefore("private fun syncCrashSnapshot")
        assertTrue(destroy.contains("webView.destroy()"))
        assertTrue(destroy.contains("super.onDestroy()"))
        assertTrue(destroy.contains("syncCrashGuard?.close()"))
        assertTrue(destroy.indexOf("super.onDestroy()") < destroy.indexOf("syncCrashGuard?.close()"))
    }

    @Test
    fun persistedCrashEvidenceNeverStoresThrowableMessageOrPassengerValues() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt").readText()
        assertTrue(source.contains("error.javaClass.name"))
        assertTrue(source.contains("messageCaptured=false"))
        assertTrue(source.contains("personalValuesCaptured=false"))
        assertFalse(source.contains("error.message"))
        assertFalse(source.contains("error.localizedMessage"))
    }

    @Test
    fun manualReportIncludesSyncCrashEvidenceInsideExistingNetworkSection() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaNetworkDiagnosticRecorder.kt").readText()
        assertTrue(source.contains("AgendaSyncCrashTraceStore.export(context)"))
        assertTrue(source.contains("--- AGENDA SYNC CRASH TRACE ---"))
    }
}
