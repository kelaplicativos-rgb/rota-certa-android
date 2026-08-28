package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaSyncCrashTrace0322Test {
    @Test
    fun authenticatedSyncInstallsAgendaProcessCrashGuardAndPersistsCheckpoint() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(source.contains("AgendaSyncCrashGuard.install(this)"))
        assertTrue(source.contains("if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC)"))
        assertTrue(source.contains("syncCrashSnapshot()"))
        assertTrue(source.contains("AgendaSyncCrashTraceStore.checkpoint("))
        assertTrue(source.contains("this,"))
    }

    @Test
    fun crashGuardCoversProcessThreadsAndRestoresDefaultHandler() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt").readText()
        assertTrue(source.contains("Thread.setDefaultUncaughtExceptionHandler(handler)"))
        assertTrue(source.contains("Thread.getDefaultUncaughtExceptionHandler()"))
        assertTrue(source.contains("Thread.setDefaultUncaughtExceptionHandler(originalDefault)"))
        assertFalse(source.contains("thread.uncaughtExceptionHandler = handler"))
    }

    @Test
    fun checkpointAndPreviousCrashEvidenceSurviveAProcessRestart() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt").readText()
        assertTrue(source.contains("CHECKPOINT_FILE_NAME"))
        assertTrue(source.contains("persistCheckpoint(context, lastCheckpoint)"))
        assertTrue(source.contains("lastCheckpoint="))
        assertTrue(source.contains("checkpointFile(context)"))
        assertFalse(source.contains("traceFile(context).delete()"))
    }

    @Test
    fun timelineParentGuardCoversCrashBeforeSyncActivityAndPersistsBreadcrumbs() {
        val trace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        assertTrue(trace.contains("class AgendaTimelineCrashGuard"))
        assertTrue(trace.contains("class AgendaTimelineUncaughtHandler"))
        assertTrue(trace.contains("BREADCRUMB_FILE_NAME"))
        assertTrue(trace.contains("--- AGENDA SYNC BREADCRUMBS ---"))
        assertTrue(activity.contains("AgendaTimelineCrashGuard.install(this)"))
        assertTrue(activity.contains("timeline_pull_requested"))
        assertTrue(activity.contains("timeline_pull_before_public_booking_reconcile"))
        assertTrue(activity.contains("timeline_pull_after_blabla_token"))
        assertTrue(activity.contains("timeline_public_agenda_effect_begin"))
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
