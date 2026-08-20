package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertContains

class FarolDiagnosticSummary0164Test {
    @Test
    fun `regiao inativa destaca bloqueio antes da rota`() {
        val report = listOf(
            "--- GRAVADOR DE VOO DO FAROL 0.1.163 ---",
            "seq=1 | stage=DRIVER_CARD_SESSION_0162 | pacote=sinet.startup.indriver | window=10",
            "seq=2 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=sinet.startup.indriver | ativo=true; pickup=Avenida A, 10; destination=Avenida B, 20; assinatura=x",
            "seq=3 | stage=BUBBLE_CARD_STATE | pacote=sinet.startup.indriver | mudou=true; assinaturaAtual=x",
        ).joinToString("\n")

        val output = FarolDiagnosticSummary0164.withSummary(
            settings = AppSettings(homeTargetEnabled = false, alternativeTargetEnabled = false),
            recorderReport = report,
        )

        assertContains(output, "BLOQUEADA ANTES DA ROTA")
        assertContains(output, "Último destino: Avenida B, 20")
        assertContains(output, "Rota solicitada: false")
        assertContains(output, "Sessões de card registradas: 1")
    }

    @Test
    fun `regiao ativa mostra rota solicitada`() {
        val report = listOf(
            "seq=1 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=com.app99.driver | ativo=true; pickup=Rua A, 1; destination=Rua B, 2; assinatura=x",
            "seq=2 | stage=BUBBLE_CARD_STATE | pacote=com.app99.driver | mudou=true; assinaturaAtual=x",
            "seq=3 | stage=BUBBLE_ROUTE_REQUESTED | pacote=com.app99.driver | destino=Rua B, 2",
        ).joinToString("\n")

        val output = FarolDiagnosticSummary0164.withSummary(
            settings = AppSettings(
                homeTargetEnabled = true,
                homeCoordinate = Coordinate(latitude = -23.0, longitude = -46.0),
            ),
            recorderReport = report,
        )

        assertContains(output, "ROTA SOLICITADA SEM RESPOSTA REGISTRADA")
        assertContains(output, "Rota solicitada: true")
    }

    @Test
    fun `sessao sem enderecos informa ausencia de card completo`() {
        val report = "seq=1 | stage=ACCESSIBILITY_EVENT | pacote=sinet.startup.indriver | type=2048"

        val output = FarolDiagnosticSummary0164.withSummary(AppSettings(), report)

        assertContains(output, "SEM CARD COMPLETO")
        assertContains(output, "Avaliações com dois endereços: 0")
    }
}
