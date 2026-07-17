package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveFailureTraceStoreTest {
    @Before
    fun setUp() {
        LiveFailureTraceStore.clear()
    }

    @Test
    fun reportKeepsWholeRideAttemptAfterWindowIsCleared() {
        LiveFailureTraceStore.recordRead(
            source = "Accessibility",
            packageName = "sinet.startup.indriver",
            text = "Travessa Voa Voa Beija-Flor 37\nRua Erundina (Sao Paulo - SP)",
            addresses = listOf(
                "Travessa Voa Voa Beija-Flor 37",
                "Rua Erundina (Sao Paulo - SP)",
            ),
            destination = "Rua Erundina (Sao Paulo - SP)",
            active = true,
            screenHash = 123,
            generation = 9,
            nowMillis = 1_000L,
        )
        LiveFailureTraceStore.recordGeocode(
            label = "destination",
            query = "Rua Erundina (Sao Paulo - SP)",
            coordinate = "-23.5,-46.4",
            elapsedMillis = 80,
            nowMillis = 1_080L,
        )
        LiveFailureTraceStore.recordRoute(
            label = "home",
            distanceKm = 12.345,
            elapsedMillis = 120,
            nowMillis = 1_200L,
        )
        LiveFailureTraceStore.recordDecision(
            color = "vermelho",
            distanceKm = 12.345,
            reason = "Destino fora do raio.",
            nowMillis = 1_220L,
        )
        LiveFailureTraceStore.recordTrace(
            message = "universal.clear immediate=true reason=Tela do proprio Rota Certa.",
            packageName = "br.com.mapeiaia.rotacerta",
            nowMillis = 1_300L,
        )

        val report = LiveFailureTraceStore.exportReport(nowMillis = 2_000L)

        assertTrue(report.contains("Rua Erundina (Sao Paulo - SP)"))
        assertTrue(report.contains("Cor final: vermelho"))
        assertTrue(report.contains("12,345"))
        assertTrue(report.contains("Tela do proprio Rota Certa"))
        assertTrue(report.contains("nenhuma falha final registrada"))
    }

    @Test
    fun reportExplainsWhenParserFoundOnlyOneAddress() {
        LiveFailureTraceStore.recordRead(
            source = "Ocr",
            packageName = "com.google.android.apps.photos",
            text = "Pedido de viagem\nRua Erundina",
            addresses = listOf("Rua Erundina"),
            destination = null,
            active = false,
            screenHash = 77,
            generation = 3,
            nowMillis = 5_000L,
        )

        val report = LiveFailureTraceStore.exportReport(nowMillis = 5_500L)

        assertTrue(report.contains("parser encontrou somente 1 endereco"))
        assertTrue(report.contains("--- TEXTO DO OCR ---"))
        assertTrue(report.contains("Pedido de viagem"))
        assertFalse(report.contains("BACKUP INTERNO"))
    }

    @Test
    fun repeatedIdenticalTraceIsCollapsed() {
        LiveFailureTraceStore.recordTrace("universal.event package=x type=2048", nowMillis = 10_000L)
        LiveFailureTraceStore.recordTrace("universal.event package=x type=2048", nowMillis = 10_100L)
        LiveFailureTraceStore.recordTrace("universal.event package=x type=2048", nowMillis = 10_200L)

        val report = LiveFailureTraceStore.exportReport(nowMillis = 10_300L)

        assertTrue(report.contains("repeticoes=3"))
    }
}
