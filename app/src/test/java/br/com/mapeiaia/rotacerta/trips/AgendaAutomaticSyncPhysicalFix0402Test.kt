package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaAutomaticSyncPhysicalFix0402Test {
    private val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
    private val publicAgenda = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
    private val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
    private val backend = File("../trip-platform/functions/index.js").takeIf { it.exists() }?.readText()
        ?: File("trip-platform/functions/index.js").readText()
    private val reservationAlert = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ReservationRequestsHomeAlert0356.kt").readText()

    @Test
    fun fullReconcileUsesWorkManagerLongRunningDataSyncWithoutLaunchingUi() {
        assertTrue(background.contains("setForeground(agendaBackgroundSyncForegroundInfo0402"))
        assertTrue(background.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("androidx.work.impl.foreground.SystemForegroundService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(background.contains("startActivity("))
    }

    @Test
    fun headlessCollectorKeepsSameStateMachineButAvoidsVisualPayload() {
        assertTrue(collector.contains("visualHost == null"))
        assertTrue(collector.contains("loadsImagesAutomatically = false"))
        assertTrue(collector.contains("blockNetworkImage = true"))
        assertTrue(collector.contains("BLABLACAR_HEADLESS_WEBVIEW_TUNED_0402"))
    }

    @Test
    fun oneAuthenticatedServerReadIsAuthorityForLegacyStopsAndNoOpRevision() {
        assertTrue(remote.contains("listDriverTripSyncStates0402"))
        assertTrue(remote.contains("/v1/driver/trips/sync-state"))
        assertTrue(backend.contains("listDriverTripSyncState0402"))
        assertTrue(backend.contains("capacitySnapshotRevision"))
        assertTrue(publicAgenda.contains("remoteSyncStates0402"))
        assertTrue(publicAgenda.contains("PUBLIC_CAPACITY_CANONICAL_SHAPE_0434"))
        assertFalse(publicAgenda.contains("PUBLIC_CAPACITY_SERVER_SHAPE_REUSED_0402"))
        assertFalse(publicAgenda.contains("firstRequestUsesServerCanonical=true"))
        assertTrue(publicAgenda.contains("expectedPublicProjectionJson0434"))
    }

    @Test
    fun cardVerifyAcquiresOnlyMissingPublicUrlWithBoundedHeadlessSession() {
        assertTrue(background.contains("reverifyCanonicalMirror0435"))
        assertTrue(background.contains("PublicMirrorAttestationCoordinator0411.attest"))
        assertTrue(background.contains("canonicalBoundBlaBlaPublicUrl0423(canonical.blablaPublicUrl, target.tripId).isNullOrBlank()"))
        assertTrue(background.contains("BlaBlaAutomaticCollectionCoordinator0400.reverifyTripHeadless0407"))
        assertTrue(background.contains("timeoutMillis = CARD_PUBLIC_URL_TARGET_TIMEOUT_MS_0442"))
        assertTrue(CARD_PUBLIC_URL_TARGET_TIMEOUT_MS_0442 <= 45_000L)
        assertTrue(background.contains("STALE_DURABLE_WORK_0435"))
        assertTrue(background.contains("val targetedRetryable = false"))
    }
    @Test
    fun reservationHomeComposableNeverOwnsNetworkReconcileLifecycle() {
        assertFalse(reservationAlert.contains("BookingPushRegistration0304.ensureRegistered"))
        assertFalse(reservationAlert.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
        assertTrue(reservationAlert.contains("BookingRealtimeEvents0356.changes.collect"))
        assertTrue(background.contains("BookingPushRegistration0304.ensureRegistered"))
        assertTrue(background.contains("PublicBookingRemoteSync0296.pullAndReconcile"))
    }

    @Test
    fun localIncrementalPathStillDoesNotRequestFullCollection() {
        assertTrue(agendaBackgroundSyncMode0392("trip_mutation") == AgendaBackgroundSyncMode0392.DELTA_ONLY)
        assertTrue(agendaBackgroundSyncMode0392("timeline_open") == AgendaBackgroundSyncMode0392.DELTA_ONLY)
        assertTrue(agendaBackgroundSyncMode0392("timeline_pull_refresh") == AgendaBackgroundSyncMode0392.DELTA_ONLY)
    }
}
