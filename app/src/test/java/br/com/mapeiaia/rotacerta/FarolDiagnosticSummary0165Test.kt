package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertContains

class FarolDiagnosticSummary0165Test {
    private val activeSettings = AppSettings(
        homeTargetEnabled = true,
        homeCoordinate = Coordinate(latitude = -23.60001, longitude = -46.48469),
    )

    @Test
    fun `decisao valida permanece visivel quando tentativa seguinte e cancelada antes da resposta`() {
        val report = listOf(
            "seq=1 | mono_ns=1000000 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=sinet.startup.indriver | ativo=true; pickup=Rua A, 1; destination=Rua B, 2; assinatura=a",
            "seq=2 | mono_ns=2000000 | stage=BUBBLE_CARD_STATE | pacote=sinet.startup.indriver | mudou=true; assinaturaAtual=a",
            "seq=3 | mono_ns=3000000 | stage=BUBBLE_ROUTE_REQUESTED | pacote=sinet.startup.indriver | destino=Rua B, 2",
            "seq=4 | mono_ns=4000000 | stage=MAPS_HTTP_RESPONSE | pacote=nao informado | code=200",
            "seq=5 | mono_ns=5000000 | stage=MAPS_HTTP_PARSED | pacote=nao informado | distances=[5.363]",
            "seq=6 | mono_ns=6000000 | stage=BUBBLE_DECISION_PAINTED | pacote=sinet.startup.indriver | cor=Green; distancia=5.363",
            "seq=7 | mono_ns=7000000 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=sinet.startup.indriver | ativo=true; pickup=Rua C, 3; destination=Rua D, 4; assinatura=b",
            "seq=8 | mono_ns=8000000 | stage=BUBBLE_CARD_STATE | pacote=sinet.startup.indriver | mudou=true; assinaturaAtual=b",
            "seq=9 | mono_ns=9000000 | stage=BUBBLE_ROUTE_REQUESTED | pacote=sinet.startup.indriver | destino=Rua D, 4",
            "seq=10 | mono_ns=10000000 | stage=BUBBLE_CLEAR_REQUEST | pacote=com.android.systemui | reason=Janela fora do aplicativo de corrida selecionado: com.android.systemui.",
            "seq=11 | mono_ns=41000000 | stage=MAPS_HTTP_RESPONSE | pacote=nao informado | code=200",
            "seq=12 | mono_ns=42000000 | stage=MAPS_HTTP_PARSED | pacote=nao informado | distances=[4.161]",
        ).joinToString("\n")

        val output = FarolDiagnosticSummary0165.withSummary(activeSettings, report)

        assertContains(output, "SESSÃO COM DECISÃO VÁLIDA")
        assertContains(output, "Última decisão válida".uppercase())
        assertContains(output, "Destino: Rua B, 2")
        assertContains(output, "Distância: 5.363 km")
        assertContains(output, "RESPOSTA DESCARTADA APÓS SAÍDA/OCULTAÇÃO DO CARD")
        assertContains(output, "Destino: Rua D, 4")
        assertContains(output, "Distância retornada: 4.161 km")
        assertContains(output, "Intervalo entre limpeza e resposta: 31 ms")
    }

    @Test
    fun `leitura incompleta posterior nao substitui ultima tentativa real`() {
        val report = listOf(
            "seq=1 | mono_ns=1 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=sinet.startup.indriver | ativo=true; pickup=Rua A, 1; destination=Rua B, 2; assinatura=a",
            "seq=2 | mono_ns=2 | stage=BUBBLE_CARD_STATE | pacote=sinet.startup.indriver | mudou=true; assinaturaAtual=a",
            "seq=3 | mono_ns=3 | stage=BUBBLE_ROUTE_REQUESTED | pacote=sinet.startup.indriver | destino=Rua B, 2",
            "seq=4 | mono_ns=4 | stage=BUBBLE_DECISION_PAINTED | pacote=sinet.startup.indriver | cor=Red; distancia=22.1",
            "seq=5 | mono_ns=5 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=com.app99.driver | ativo=false; pickup=; destination=; assinatura=",
            "seq=6 | mono_ns=6 | stage=BUBBLE_CARD_STATE | pacote=com.app99.driver | mudou=true; assinaturaAtual=ruido",
        ).joinToString("\n")

        val output = FarolDiagnosticSummary0165.withSummary(activeSettings, report)

        assertContains(output, "Tentativas reconhecidas: 1")
        assertContains(output, "Pacote: sinet.startup.indriver")
        assertContains(output, "Destino: Rua B, 2")
        assertContains(output, "Decisões pintadas na sessão: 1")
    }

    @Test
    fun `regiao inativa continua explicando bloqueio antes da rota`() {
        val report = listOf(
            "seq=1 | mono_ns=1 | stage=BUBBLE_ADDRESS_EVALUATION | pacote=com.app99.driver | ativo=true; pickup=Rua A, 1; destination=Rua B, 2; assinatura=a",
            "seq=2 | mono_ns=2 | stage=BUBBLE_CARD_STATE | pacote=com.app99.driver | mudou=true; assinaturaAtual=a",
        ).joinToString("\n")

        val output = FarolDiagnosticSummary0165.withSummary(AppSettings(), report)

        assertContains(output, "BLOQUEADA ANTES DA ROTA")
        assertContains(output, "Rota solicitada: false")
    }
}
