package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSupportConsent0746ContractTest {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun pushNeverStartsCollectionBeforeUserConsent() {
        val messaging = source("RotaCertaBookingMessagingService.kt")
        val coversBlock = messaging.substringAfter("if (isStandaloneCoversRemoteEvent0736(event))")
            .substringBefore("if (isBlaBlaRemoteTripQueryEvent0737(event))")
        val tripBlock = messaging.substringAfter("if (isBlaBlaRemoteTripQueryEvent0737(event))")
            .substringBefore("val remoteTripId")

        assertTrue(coversBlock.contains("RemoteSupportNotification0744.show"))
        assertTrue(tripBlock.contains("RemoteSupportNotification0744.show"))
        assertFalse(coversBlock.contains("StandaloneCoversRemoteScheduler0736.enqueue"))
        assertFalse(tripBlock.contains("BlaBlaRemoteTripQueryScheduler0737.enqueue"))
        assertTrue(coversBlock.contains("collectionStarted=false"))
        assertTrue(tripBlock.contains("collectionStarted=false"))
    }

    @Test
    fun onlyAcceptStartsTheMatchingRemoteJob() {
        val consent = source("RemoteSupportConsent0746.kt")
        assertTrue(consent.contains("if (action == ACTION_ACCEPT)"))
        assertTrue(consent.contains("StandaloneCoversRemoteScheduler0736.enqueue(context, jobId)"))
        assertTrue(consent.contains("BlaBlaRemoteTripQueryScheduler0737.enqueue(context, jobId)"))
        assertTrue(consent.contains("REMOTE_SUPPORT_ACCEPTED_0746"))
    }

    @Test
    fun declineNeverCollectsAndClosesServerJob() {
        val consent = source("RemoteSupportConsent0746.kt")
        assertTrue(consent.contains("REMOTE_ACCESS_DECLINED_BY_USER"))
        assertTrue(consent.contains("submitStandaloneCoversResult0736"))
        assertTrue(consent.contains("submitBlaBlaRemoteTripQueryResult0737"))
        assertTrue(consent.contains("collectionStarted=false"))
    }

    @Test
    fun notificationSurfaceIsOnlyAcceptOrDecline() {
        val notification = source("RemoteSupportNotification0744.kt")
        assertTrue(notification.contains(".setContentTitle(\"Solicitação de acesso remoto\")"))
        assertTrue(notification.contains(".setContentText(\"ACEITAR ou RECUSAR\")"))
        assertTrue(notification.contains("\"ACEITAR\""))
        assertTrue(notification.contains("\"RECUSAR\""))
        assertFalse(notification.contains("Abrir Central"))
        assertFalse(notification.contains("Gerar e compartilhar"))
        assertFalse(notification.contains("setContentIntent"))
    }
}
