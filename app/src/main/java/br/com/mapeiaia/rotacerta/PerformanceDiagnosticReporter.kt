package br.com.mapeiaia.rotacerta

import java.util.Locale

object PerformanceDiagnosticReporter {
    private val ridePackages = setOf(
        "sinet.startup.indriver",
        "com.ubercab.driver",
        "com.app99.driver",
    )

    fun build(eventsText: String): String {
        val events = eventsText
            .lines()
            .mapNotNull(::parseEvent)
            .filter { event -> event.raw.isNotBlank() }
        if (events.isEmpty()) return "sem eventos suficientes para medir desempenho"

        val rideEvents = events.filter { event -> ridePackages.any { pkg -> pkg in event.raw } }
        if (rideEvents.isEmpty()) return "sem eventos de app de corrida monitorado"

        val firstRideVisible = rideEvents.firstOrNull { event ->
            event.raw.contains("window=") || event.message.contains("event package=") || event.message.contains("process.start")
        } ?: rideEvents.first()
        val firstAccessibility = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("accessibility.collect") || it.raw.contains("accLen=")
        }
        val firstOcr = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("screenshot.ocr success") || Regex("ocrLen=[1-9]").containsMatchIn(it.raw)
        }
        val firstProcess = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("process.start") }
        val firstModelMatch = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("card_model.match") }
        val firstAnalysis = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("analysis.start") }
        val firstRoute = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("route.distance") }
        val firstDecision = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("decision.result") }
        val firstOverlay = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("overlay.apply") || it.message.contains("global.overlay request") ||
                it.raw.contains("color=verde") || it.raw.contains("color=vermelho")
        }
        val firstGreenOrRed = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.raw.contains("color=verde") || it.raw.contains("color=vermelho")
        }
        val firstYellowAfterDecision = if (firstGreenOrRed != null) {
            events.firstOrNullAfter(firstGreenOrRed.timeMillis + 1L) {
                it.raw.contains("window=") && it.raw.contains("color=amarelo") && it.raw.contains("km=none")
            }
        } else null

        val greenCount = events.count { it.raw.contains("color=verde") }
        val redCount = events.count { it.raw.contains("color=vermelho") }
        val yellowWithoutKmCount = events.count { it.raw.contains("color=amarelo") && it.raw.contains("km=none") }
        val passiveBlockedCount = events.count { it.message.contains("blocked ignored monitored_root") }
        val keepDecisionCount = events.count { it.message.contains("keep_active_decision") || it.message.contains("keep_decision=true") }

        val totalToFirstColor = firstGreenOrRed?.deltaFrom(firstRideVisible)
        val probableBottleneck = probableBottleneck(
            totalToFirstColor = totalToFirstColor,
            firstOcr = firstOcr,
            firstAnalysis = firstAnalysis,
            firstDecision = firstDecision,
            firstOverlay = firstOverlay,
            firstGreenOrRed = firstGreenOrRed,
            firstRideVisible = firstRideVisible,
            firstYellowAfterDecision = firstYellowAfterDecision,
            greenCount = greenCount,
            redCount = redCount,
            yellowWithoutKmCount = yellowWithoutKmCount,
        )

        return buildString {
            appendLine("primeiro app/card monitorado: ${formatEvent(firstRideVisible)}")
            appendLine("primeira leitura acessibilidade: ${formatDelta(firstAccessibility, firstRideVisible)}")
            appendLine("primeiro OCR com texto: ${formatDelta(firstOcr, firstRideVisible)}")
            appendLine("primeiro processamento: ${formatDelta(firstProcess, firstRideVisible)}")
            appendLine("primeiro modelo de card confirmado: ${formatDelta(firstModelMatch, firstRideVisible)}")
            appendLine("primeira analise de rota: ${formatDelta(firstAnalysis, firstRideVisible)}")
            appendLine("primeira rota/distancia calculada: ${formatDelta(firstRoute, firstRideVisible)}")
            appendLine("primeira decisao: ${formatDelta(firstDecision, firstRideVisible)}")
            appendLine("primeira aplicacao de cor/km: ${formatDelta(firstOverlay, firstRideVisible)}")
            appendLine("primeiro verde/vermelho visivel: ${formatDelta(firstGreenOrRed, firstRideVisible)}")
            appendLine("primeiro amarelo sem km depois da decisao: ${formatDelta(firstYellowAfterDecision, firstGreenOrRed)}")
            appendLine("contagem verde=${greenCount}; vermelho=${redCount}; amarelo_sem_km=${yellowWithoutKmCount}; passivos_bloqueados=${passiveBlockedCount}; decisoes_preservadas=${keepDecisionCount}")
            appendLine("gargalo provavel: $probableBottleneck")
        }.trim()
    }

    private fun probableBottleneck(
        totalToFirstColor: Long?,
        firstOcr: PerfEvent?,
        firstAnalysis: PerfEvent?,
        firstDecision: PerfEvent?,
        firstOverlay: PerfEvent?,
        firstGreenOrRed: PerfEvent?,
        firstRideVisible: PerfEvent,
        firstYellowAfterDecision: PerfEvent?,
        greenCount: Int,
        redCount: Int,
        yellowWithoutKmCount: Int,
    ): String {
        if (firstGreenOrRed == null) return "nao chegou a aplicar verde/vermelho; investigar OCR, modelo do card e geocodificacao"
        if (totalToFirstColor != null && totalToFirstColor > 2_000L) {
            return when {
                firstOcr == null -> "OCR/texto nao apareceu no relatorio antes da decisao; medir screenshot/OCR imediatamente"
                firstAnalysis == null -> "OCR apareceu, mas analise nao ficou registrada; medir parser/modelo do card"
                firstDecision == null -> "analise iniciou, mas decisao nao ficou registrada; medir geocode/rota/Google Maps"
                firstOverlay == null -> "decisao ocorreu, mas overlay nao registrou aplicacao; medir showOverlay/bolinha"
                else -> "tempo ate primeira cor foi ${formatDuration(totalToFirstColor)}; comparar deltas acima para localizar etapa lenta"
            }
        }
        if (yellowWithoutKmCount > greenCount + redCount && firstYellowAfterDecision != null) {
            return "decisao boa ainda esta sendo perdida por texto/hash transitório; preservar verde/vermelho ate confirmar novo card real"
        }
        return "sem gargalo unico evidente; tempos principais abaixo de 2s no trecho capturado"
    }

    private fun formatEvent(event: PerfEvent): String = "${event.timeMillis} ${event.source} ${event.message.take(140)}"

    private fun formatDelta(event: PerfEvent?, baseline: PerfEvent?): String = when {
        event == null -> "nao registrado"
        baseline == null -> formatEvent(event)
        else -> "${event.deltaFrom(baseline)} ms (${formatDuration(event.deltaFrom(baseline))}) - ${event.source} ${event.message.take(120)}"
    }

    private fun formatDuration(millis: Long): String = String.format(Locale("pt", "BR"), "%.3f s", millis / 1000.0)

    private fun List<PerfEvent>.firstOrNullAfter(timeMillis: Long, predicate: (PerfEvent) -> Boolean): PerfEvent? =
        firstOrNull { it.timeMillis >= timeMillis && predicate(it) }

    private fun PerfEvent.deltaFrom(other: PerfEvent): Long = timeMillis - other.timeMillis

    private fun parseEvent(line: String): PerfEvent? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        val firstSpace = trimmed.indexOf(' ')
        if (firstSpace <= 0) return null
        val time = trimmed.substring(0, firstSpace).toLongOrNull() ?: return null
        val rest = trimmed.substring(firstSpace + 1).trim()
        val secondSpace = rest.indexOf(' ')
        val source = if (secondSpace > 0) rest.substring(0, secondSpace) else "unknown"
        val message = if (secondSpace > 0) rest.substring(secondSpace + 1) else rest
        return PerfEvent(time, source, message, trimmed)
    }

    private data class PerfEvent(
        val timeMillis: Long,
        val source: String,
        val message: String,
        val raw: String,
    )
}
