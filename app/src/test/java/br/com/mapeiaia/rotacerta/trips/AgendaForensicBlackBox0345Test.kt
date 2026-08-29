package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.FarolMaximumForensicsStage38
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaForensicBlackBox0345Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun userActionsHaveSemanticEventsWithoutRemovingCoordinateFallback() {
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        val actions = source("br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt")
        assertTrue(trace.contains("USER_OPEN_CAPACITY"))
        assertTrue(trace.contains("USER_OPEN_PASSENGERS"))
        assertTrue(trace.contains("USER_SYNC_ALL"))
        assertTrue(trace.contains("USER_SYNC_TODAY"))
        assertTrue(trace.contains("USER_CLEAR_TIMELINE"))
        assertTrue(trace.contains("USER_OPEN_DATE_PICKER"))
        assertTrue(trace.contains("USER_BACK"))
        assertTrue(trace.contains("AGENDA_INTERACTION"))
        assertTrue(actions.contains("AgendaTrace.action(context, action.traceKey, action.label)"))
    }

    @Test
    fun everyLongOperationHasStandardTerminalVocabularyAndCausalIds() {
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        assertTrue(trace.contains("traceId"))
        assertTrue(trace.contains("operationId"))
        assertTrue(trace.contains("parentOperationId"))
        assertTrue(trace.contains("\\\"OPERATION_START\\\""))
        assertTrue(trace.contains("\\\"OPERATION_END\\\""))
        assertTrue(trace.contains("\\\"OPERATION_ERROR\\\""))
        assertTrue(trace.contains("\\\"OPERATION_CANCELLED\\\""))
    }

    @Test
    fun agendaOpenChainCoversRequestActivityCompositionLayoutFrameAndMetric() {
        val main = source("br/com/mapeiaia/rotacerta/MainActivity.kt")
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        listOf(
            "AGENDA_OPEN_REQUESTED",
            "AGENDA_SHORTCUT_DISPATCH",
            "MAIN_ACTIVITY_RECEIVED_AGENDA_REQUEST",
            "AGENDA_START_ACTIVITY_REQUEST",
            "AGENDA_START_ACTIVITY_RETURN",
        ).forEach { assertTrue(main.contains(it) || trace.contains(it)) }
        listOf(
            "TRIPS_ACTIVITY_ONCREATE_START",
            "TRIPS_ACTIVITY_ONCREATE_END",
            "TRIPS_ACTIVITY_BEFORE_SET_CONTENT",
            "TRIPS_ACTIVITY_AFTER_SET_CONTENT",
            "AGENDA_FIRST_COMPOSITION",
        ).forEach { assertTrue(activity.contains(it)) }
        assertTrue(trace.contains("AGENDA_FIRST_LAYOUT"))
        assertTrue(trace.contains("AGENDA_FIRST_FRAME_DRAWN"))
        assertTrue(trace.contains("AGENDA_FIRST_INTERACTIVE_FRAME"))
        assertTrue(trace.contains("AGENDA_OPEN_TOTAL_MS"))
    }

    @Test
    fun capacityEmptyToPersistedValueAndSaveAreObservable() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(activity.contains("collectAsState(initial = AppSettings())"))
        assertTrue(activity.contains("CAPACITY_INITIAL_STATE"))
        assertTrue(activity.contains("CAPACITY_LOCAL_SETTINGS_REQUEST"))
        assertTrue(activity.contains("CAPACITY_LOCAL_SETTINGS_RECEIVED"))
        assertTrue(activity.contains("CAPACITY_FIRST_VALUE_MS"))
        assertTrue(activity.contains("CAPACITY_RENDER_UPDATED"))
        assertTrue(timeline.contains("CAPACITY_SCREEN_OPENED"))
        assertTrue(timeline.contains("CAPACITY_FIELD_RENDERED"))
        assertTrue(timeline.contains("CAPACITY_FIELD_CHANGED_BY_USER"))
        assertTrue(timeline.contains("CAPACITY_SAVE_REQUESTED"))
        assertTrue(timeline.contains("CAPACITY_LOCAL_SAVE"))
        assertTrue(activity.contains("CAPACITY_PUBLIC_SYNC_TRIGGERED"))
        assertTrue(activity.contains("CAPACITY_PUBLIC_SYNC"))
        assertTrue(activity.contains("CAPACITY_REMOTE_CONFIRMATION"))
    }

    @Test
    fun bookingReconcileHasPhasesDurationsSeatQueueAndSlowThresholds() {
        val source = source("br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt")
        listOf(
            "BOOKING_RECONCILE",
            "BOOKING_REMOTE_FETCH",
            "BOOKING_LOCAL_READ",
            "BOOKING_COMPARE",
            "BOOKING_IMPORT",
            "BOOKING_SEAT_SYNC_QUEUE",
            "1_000L, 2_000L, 5_000L, 10_000L",
        ).forEach { assertTrue(source.contains(it)) }
    }

    @Test
    fun publicAgendaAutoSyncHasEveryRequiredPhaseAndSummaryCounters() {
        val source = source("br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt")
        listOf(
            "PUBLIC_AGENDA_SYNC",
            "PROFILE_SYNC",
            "PASSENGER_DIRECTORY_SYNC",
            "LOCAL_TRIPS_DISCOVERY",
            "LOCAL_TRIP_PUBLISH",
            "LOCAL_CAPACITY_CLAIMS",
            "CONNECTED_ACCOUNTS_READ",
            "EXTERNAL_TRIPS_DISCOVERY",
            "EXTERNAL_TRIP_PUBLISH",
            "EXTERNAL_TRIP_UPDATE_RETRY",
            "EXTERNAL_TRIP_UPDATE_END",
            "EXTERNAL_CAPACITY_CLAIMS",
            "PUBLIC_EXTERNAL_BINDING_SAVE",
            "PUBLIC_AGENDA_SYNC_RESULT",
            "preservedShape",
            "retries=",
        ).forEach { assertTrue(source.contains(it)) }
    }

    @Test
    fun timelineStartupMergeConsolidationSortAndRenderAreObservable() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(activity.contains("TIMELINE_STARTUP"))
        assertTrue(activity.contains("TIMELINE_LOCAL_TRIPS_LOAD"))
        assertTrue(activity.contains("TIMELINE_LOCAL_BOOKINGS_LOAD"))
        assertTrue(activity.contains("TIMELINE_PUBLIC_BOOKING_RECONCILE_START"))
        assertTrue(activity.contains("TIMELINE_PUBLIC_BOOKING_RECONCILE_END"))
        assertTrue(timeline.contains("TIMELINE_MERGE"))
        assertTrue(timeline.contains("TIMELINE_PHYSICAL_CONSOLIDATION"))
        assertTrue(timeline.contains("TIMELINE_SORT"))
        assertTrue(timeline.contains("TIMELINE_RENDER"))
        assertTrue(timeline.contains("TIMELINE_RENDER_STATE"))
    }

    @Test
    fun jankAndEmptyVisualStateAreEventDrivenWithoutPollingOrScreenshots() {
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        assertTrue(trace.contains("Choreographer.FrameCallback"))
        assertTrue(trace.contains("AGENDA_JANK_FRAME_100MS"))
        assertTrue(trace.contains("AGENDA_JANK_FRAME_250MS"))
        assertTrue(trace.contains("AGENDA_JANK_FRAME_500MS"))
        assertTrue(trace.contains("AGENDA_JANK_FREEZE"))
        assertTrue(trace.contains("AGENDA_EMPTY_VISUAL_STATE"))
        assertTrue(trace.contains("AGENDA_EMPTY_VISUAL_STATE_LONG"))
        assertFalse(trace.contains("while (true)"))
        assertFalse(trace.contains("screenshot"))
        assertFalse(trace.contains("OCR"))
    }

    @Test
    fun reportStartsWithAgendaSummaryCausalChainAndDetailedEvents() {
        val report = source("br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt")
        val main = source("br/com/mapeiaia/rotacerta/MainActivity.kt")
        assertTrue(report.contains("--- RESUMO FORENSE DA AGENDA ---"))
        assertTrue(report.contains("--- CADEIA CAUSAL DA AGENDA ---"))
        assertTrue(report.contains("--- EVENTOS DETALHADOS DA AGENDA ---"))
        assertTrue(report.contains("SLOW_OPERATION"))
        assertTrue(report.contains("UI_FREEZE"))
        assertTrue(report.contains("CAPACITY_LATE_RENDER"))
        assertTrue(report.contains("START_WITHOUT_END"))
        assertTrue(report.contains("PUBLIC_AGENDA_SYNC_TOO_LONG"))
        assertTrue(main.contains("AgendaForensicReportBuilder.freezeSnapshot()"))
        assertTrue(main.indexOf("AgendaForensicReportBuilder.build(context)") < main.indexOf("RESUMO TÉCNICO UNIFICADO"))
    }

    @Test
    fun Stage38ExportRedactsRawExternalTextPhonesEmailsUrlsAndSecrets() {
        FarolMaximumForensicsStage38.resetForTests()
        FarolMaximumForensicsStage38.record(
            atNs = 10L,
            wallMs = 10L,
            stage = "ACCESSIBILITY_EVENT",
            packageName = "other.app",
            details = "eventText=SEGREDO EXTERNO; phone=11999998888; mail=pessoa@example.com; url=https://private.invalid/path; token=abc123",
        )
        val report = FarolMaximumForensicsStage38.exportReport()
        assertFalse(report.contains("SEGREDO EXTERNO"))
        assertFalse(report.contains("11999998888"))
        assertFalse(report.contains("pessoa@example.com"))
        assertFalse(report.contains("https://private.invalid/path"))
        assertFalse(report.contains("abc123"))
        assertTrue(report.contains("eventText=[texto mascarado]"))
        assertTrue(report.contains("[telefone mascarado]"))
        assertTrue(report.contains("[email mascarado]"))
        assertTrue(report.contains("[url mascarada]"))
        assertTrue(report.contains("token=[segredo mascarado]"))
    }

    @Test
    fun unifiedStoreIsBoundedFailOpenAndReportsDropsAndObserverCost() {
        val source = source("br/com/mapeiaia/rotacerta/UnifiedDebugLog.kt")
        assertTrue(source.contains("const val MAX_EVENTS = 6_000"))
        assertTrue(source.contains("while (events.size >= MAX_EVENTS)"))
        assertTrue(source.contains("droppedEvents += 1L"))
        assertTrue(source.contains("recordOverheadTotalNs"))
        assertTrue(source.contains("recordMedianNs"))
        assertTrue(source.contains("recordP95Ns"))
        assertTrue(source.contains("runCatching"))
        assertTrue(source.contains("fun recordAlways("))
    }

    @Test
    fun crashRecoveryUsesMemoryHotPathAsyncPersistenceAndMarksIncompleteOperation() {
        val source = source("br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt")
        assertTrue(source.contains("checkpointPersistence=memory_hot_path_async_coalesced"))
        assertTrue(source.contains("ioExecutor"))
        assertTrue(source.contains("persistScheduled"))
        assertTrue(source.contains("OPERATION_INCOMPLETE_DUE_PROCESS_TERMINATION"))
        assertFalse(source.contains("readLines(Charsets.UTF_8)"))
        assertFalse(source.contains("error.message"))
        assertFalse(source.contains("error.localizedMessage"))
    }

    @Test
    fun publicAccessForensicsRecordsOnlySafeStatusEvents() {
        val app = File("../trip-platform/public/app.js").readText()
        val api = File("../trip-platform/functions/index.js").readText()
        listOf(
            "PUBLIC_ACCESS_CONTACT_SUBMITTED",
            "PUBLIC_ACCESS_GRANTED",
            "PUBLIC_ACCESS_DENIED",
            "PUBLIC_PRIVATE_AUTH_SHOWN",
            "PUBLIC_PRIVATE_AUTH_SUCCESS",
            "PUBLIC_PRIVATE_AUTH_FAILED",
            "PUBLIC_PASSENGER_PORTAL_OPENED",
        ).forEach {
            assertTrue(app.contains(it))
            assertTrue(api.contains(it))
        }
        assertTrue(api.contains("tripRefHash"))
        assertTrue(api.contains("agendaRefHash"))
        assertFalse(api.contains("passengerContact,\n    event"))
    }

    @Test
    fun reportExportDoesNotEnableDiagnosticGateOrMutateFunctionalState() {
        val main = source("br/com/mapeiaia/rotacerta/MainActivity.kt")
        val report = source("br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt")
        assertTrue(main.contains("AgendaForensicReportBuilder.freezeSnapshot()"))
        assertFalse(report.contains("DiagnosticRuntimeGate.setEnabled"))
        assertFalse(report.contains("saveTrip("))
        assertFalse(report.contains("saveSettings("))
        assertFalse(report.contains("sync("))
    }

    @Test
    fun agendaTraceDoesNotConsumeTouchesAndFarolFunctionalServiceIsOutsideChangeSurface() {
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        assertTrue(trace.contains("method.invoke(current"))
        assertTrue(trace.contains("if (motion?.actionMasked == MotionEvent.ACTION_UP)"))
        assertFalse(trace.contains("return true // consume"))
        assertTrue(trace.contains("UnifiedDebugEventStore.recordAlways"))
        assertFalse(trace.contains("LiveRideAccessibilityService"))
    }
    @Test
    fun forensicSummaryReportsDroppedEventsAndAllObserverCostMetrics() {
        val report = source("br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt")
        listOf(
            "debugEventsRecorded",
            "debugEventsDropped",
            "debugBufferCapacity",
            "debugRecordMedianNs",
            "debugRecordP95Ns",
            "debugRecordMaxNs",
            "debugTotalOverheadNs",
        ).forEach { assertTrue(report.contains(it)) }
    }

    @Test
    fun slowBookingReconcileExposesOneTwoFiveAndTenSecondThresholds() {
        val source = source("br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt")
        assertTrue(source.contains("1_000L"))
        assertTrue(source.contains("2_000L"))
        assertTrue(source.contains("5_000L"))
        assertTrue(source.contains("10_000L"))
        assertTrue(source.contains("BOOKING_RECONCILE_SLOW_"))
    }

    @Test
    fun incompleteOperationsAreVisibleInCurrentSnapshotAndAfterProcessTermination() {
        val report = source("br/com/mapeiaia/rotacerta/trips/AgendaForensicReport.kt")
        val crash = source("br/com/mapeiaia/rotacerta/trips/AgendaSyncCrashTrace.kt")
        assertTrue(report.contains("operations START sem conclusão"))
        assertTrue(report.contains("START_WITHOUT_END"))
        assertTrue(crash.contains("OPERATION_INCOMPLETE_DUE_PROCESS_TERMINATION"))
        assertTrue(crash.contains("AgendaTrace.activeOperationSummary()"))
    }

    @Test
    fun publicDebugBackendStoresOnlyHashedTargetReferences() {
        val api = File("../trip-platform/functions/index.js").readText()
        assertTrue(api.contains("tripRefHash: tripToken ? sha256Hex"))
        assertTrue(api.contains("agendaRefHash: agendaToken ? sha256Hex"))
        assertTrue(api.contains("targetRefHash"))
        assertFalse(api.contains("passengerContact: req.body && req.body.passengerContact"))
        assertFalse(api.contains("password: req.body && req.body.password"))
    }

    @Test
    fun legacyDebugCanRemainDisabledWhileAgendaBlackBoxStillCompilesAndRecordsInMemory() {
        val unified = source("br/com/mapeiaia/rotacerta/UnifiedDebugLog.kt")
        val trace = source("br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt")
        assertTrue(unified.contains("DiagnosticRuntimeGate.isEnabled"))
        assertTrue(unified.contains("fun recordAlways("))
        assertTrue(trace.contains("UnifiedDebugEventStore.recordAlways"))
        assertFalse(trace.contains("DiagnosticRuntimeGate.isEnabled"))
    }

}
