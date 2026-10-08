package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSupportAttention0743ContractTest {
    private fun trips(name: String) =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    private fun diagnostics(name: String) =
        File("src/main/java/br/com/mapeiaia/rotacerta/diagnostics/$name").readText()

    @Test
    fun globalBellIsIndependentFromRemoteAttentionState() {
        val header = trips("AgendaHeaderNavigation0396.kt")
        val activity = trips("TripsActivity.kt")
        assertTrue(header.contains("Color(0xFFFF9800)"))
        assertTrue(header.contains("if (unread > 0)"))
        assertFalse(header.contains("remote-support-orange-pulse-0743"))
        assertFalse(header.contains("if (unread > 0 || remoteAttentionNeeded0743)"))
        assertTrue(activity.contains("remoteSupportAttention0743.ready"))
        assertTrue(activity.contains("Verificar conexão remota"))
    }

    @Test
    fun notificationCenterOwnsShortRemoteRecoveryPath() {
        val activity = trips("TripsActivity.kt")
        assertTrue(activity.contains("Verificar conexão remota"))
        assertTrue(activity.contains("Gerar e compartilhar diagnóstico"))
        assertTrue(activity.contains("Abrir Central de Saúde"))
    }

    @Test
    fun pushRegistrationDrivesAttentionWithoutLoggingPrivateCredential() {
        val messaging = trips("RotaCertaBookingMessagingService.kt")
        val support = trips("RemoteSupportAttention0743.kt")
        assertTrue(messaging.contains("RemoteSupportAttention0743.markPending"))
        assertTrue(messaging.contains("RemoteSupportAttention0743.markReady"))
        assertTrue(messaging.contains("REMOTE_SUPPORT_FCM_TOKEN_UNAVAILABLE_0743"))
        assertFalse(messaging.contains("details = token"))
        assertFalse(support.contains("accessToken"))
        assertFalse(support.contains("refreshUrl"))
    }

    @Test
    fun diagnosticFabricConvergesIntoExistingUnifiedRecorder() {
        val fabric = diagnostics("RcDiagnosticFabric0741.kt")
        assertTrue(fabric.contains("UnifiedDebugEventStore.recordAlways"))
        assertTrue(fabric.contains("DiagnosticModule0507.BLABLACAR"))
    }
}
