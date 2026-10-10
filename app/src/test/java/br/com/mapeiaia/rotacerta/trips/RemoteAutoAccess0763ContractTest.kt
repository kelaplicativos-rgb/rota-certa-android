package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteAutoAccess0763ContractTest {
    private fun src(file: String) =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$file").readText()

    @Test
    fun autoAccessDefaultsToOffAndIsScopedToTheActiveTenant() {
        val store = src("RemoteSupportAutoAccess0763.kt")
        assertTrue(store.contains("activeScope()"))
        assertTrue(store.contains("tenantScope.key(KEY_ENABLED)"))
        assertTrue(store.contains("getBoolean(tenantScope.key(KEY_ENABLED), false)"))
        assertTrue(store.contains("fun setEnabled(context: Context, value: Boolean)"))
        assertTrue(store.contains("REMOTE_AUTO_ACCESS_REVOKED_0763"))
        assertTrue(store.contains("standalone_covers_collect"))
        assertTrue(store.contains("blablacar_trip_query_collect"))
        assertTrue(store.contains("remote_health_collect"))
        assertFalse(store.contains("mediaProjection"))
        assertFalse(store.contains("createScreenCaptureIntent"))
    }

    @Test
    fun onlyExplicitOptInCanBypassAppPopupForReadOnlyCollection() {
        val notification = src("RemoteSupportNotification0744.kt")
        val service = src("RemoteCoversPollService0758.kt")
        val receiver = src("RemoteSupportConsent0746.kt")
        assertTrue(notification.contains("RemoteSupportAutoAccess0763.enabled(app, event)"))
        assertTrue(notification.contains("RemoteSupportConsentReceiver0746.EXTRA_AUTO_APPROVED, true"))
        assertTrue(service.contains("if (RemoteSupportNotification0744.show(this, event, jobId)) return"))
        assertTrue(receiver.contains("!RemoteSupportAutoAccess0763.enabled(context, event)"))
        assertTrue(receiver.contains("RemoteSupportNotification0744.show(context, event, jobId)"))
        assertTrue(receiver.contains("if (action == ACTION_ACCEPT)"))
        assertFalse(service.contains("RemoteHealthScheduler0747.enqueue"))
    }

    @Test
    fun driverHasSingleToggleAndServerAcknowledgedAccess() {
        val screen = src("StandaloneRemoteAccessActions0739.kt")
        val collector = src("TripBlaBlaCollectorUi.kt")
        assertTrue(screen.contains("Switch("))
        assertTrue(screen.contains("RemoteAccessDesiredState0771.request(context, requested)"))
        assertTrue(screen.contains("autoAccess = requested"))
        assertTrue(screen.contains("api.setRemoteAccessState0764(enabled)"))
        assertTrue(screen.contains("Não altera"))
        assertFalse(collector.contains("Ativar escuta remota (sem FCM)"))
        assertTrue(collector.contains("StandaloneRemoteAccessActions0739("))
        assertTrue(screen.contains("ContextCompat.startForegroundService"))
        assertTrue(screen.contains("context.stopService"))
        val poller = src("RemoteCoversPollService0758.kt")
        assertTrue(poller.contains("RemoteSupportAutoAccess0763.enabled(applicationContext"))
        val consent = src("RemoteSupportConsent0746.kt")
        assertTrue(consent.contains("RemoteSupportAutoAccess0763.enabled(app, event)"))
    }
}
