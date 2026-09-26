package br.com.mapeiaia.rotacerta

import java.util.Locale

object PerformanceDiagnosticReporter {
    private val ridePackages = setOf(
        "",
        "",
        "",
    )

    fun build(eventsText: String): String {
        val events = eventsText
            .lines()
            .mapNotNull(::parseEvent)
            .filter { event -> event.raw.isNotBlank() }
        if (events.isEmpty()) return "sem eventos suficientes para medir desempenho"

        val firstRideVisible = events.firstOrNull { it.hasRideWindow() || it.hasRideActivePackage() }
            ?: events.firstOrNull { it.message.contains("process.start") && ridePackages.any { pkg -> "package=$pkg" in it.raw } }
            ?: return "sem janela real de app de corrida monitorado; havia apenas texto antigo/stale de app de corrida em outra tela"

        val firstAccessibility = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("accessibility.collect") || Regex("accLen=[1-9]").containsMatchIn(it.raw)
        }
        val firstOcr = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("screenshot.ocr success") || Regex("ocrLen=[1-9]").containsMatchIn(it.raw)
        }
        val firstProcess = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("process.start") }
                val firstAnalysis = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("analysis.start") }
        val firstRoute = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("route.distance") }
        val firstDecision = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("decision.result") }
        val firstOverlay = events.firstOrNullAfter(firstRideVisible.timeMillis) {
            it.message.contains("overlay.apply") || it.message.contains("global.overlay request") || it.isVisibleDecisionColor()
        }
        val firstGreenOrRed = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.isVisibleDecisionColor() }
        val firstYellowAfterDecision = if (firstGreenOrRed != null) {
            events.firstOrNullAfter(firstGreenOrRed.timeMillis + 1L) {
                it.isRideWindowState() && it.raw.contains("color=amarelo") && it.raw.contains("km=none")
            }
        } else null

        val rideWindowEvents = events.filter { it.timeMillis >= firstRideVisible.timeMillis && it.isRideWindowState() }
        val greenCount = rideWindowEvents.count { it.raw.contains("color=verde") }
        val redCount = rideWindowEvents.count { it.raw.contains("color=vermelho") }
        val yellowWithoutKmCount = rideWindowEvents.count { it.raw.contains("color=amarelo") && it.raw.contains("km=none") }
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
            firstYellowAfterDecision = firstYellowAfterDecision,
            greenCount = greenCount,
            redCount = redCount,
            yellowWithoutKmCount = yellowWithoutKmCount,
        )

        return buildString {
            appendLine("primeira janela real do app/card monitorado: ${formatEvent(firstRideVisible)}")
            appendLine("primeira leitura acessibilidade: ${formatDelta(firstAccessibility, firstRideVisible)}")
            appendLine("primeiro OCR com texto: ${formatDelta(firstOcr, firstRideVisible)}")
            appendLine("primeiro processamento: ${formatDelta(firstProcess, firstRideVisible)}")
            appendLine("primeira analise de rota: ${formatDelta(firstAnalysis, firstRideVisible)}")
            appendLine("primeira rota/distancia calculada: ${formatDelta(firstRoute, firstRideVisible)}")
            appendLine("primeira decisao: ${formatDelta(firstDecision, firstRideVisible)}")
            appendLine("primeira aplicacao de cor/km: ${formatDelta(firstOverlay, firstRideVisible)}")
            appendLine("primeiro verde/vermelho visivel: ${formatDelta(firstGreenOrRed, firstRideVisible)}")
            appendLine("primeiro amarelo sem km depois da decisao: ${formatDelta(firstYellowAfterDecision, firstGreenOrRed)}")
            appendLine("contagem_na_janela_corrida verde=${greenCount}; vermelho=${redCount}; amarelo_sem_km=${yellowWithoutKmCount}; passivos_bloqueados=${passiveBlockedCount}; decisoes_preservadas=${keepDecisionCount}")
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
        firstYellowAfterDecision: PerfEvent?,
        greenCount: Int,
        redCount: Int,
        yellowWithoutKmCount: Int,
    ): String {
        if (firstGreenOrRed == null) return "nao chegou a aplicar verde/vermelho na janela real do app de corrida; investigar OCR, extração de endereços e geocodificação"
        if (yellowWithoutKmCount > greenCount + redCount && firstYellowAfterDecision != null) {
            return "decisao boa ainda esta sendo perdida por texto/hash transitorio; preservar verde/vermelho ate confirmar novo card real"
        }
        if (totalToFirstColor != null && totalToFirstColor > 2_000L) {
            return when {
                firstOcr == null -> "OCR/texto nao apareceu no relatorio antes da decisao; medir screenshot/OCR imediatamente"
                firstAnalysis == null -> "OCR apareceu, mas analise nao ficou registrada; medir parser de endereços"
                firstDecision == null -> "analise iniciou, mas decisao nao ficou registrada; medir geocode/rota/Google Maps"
                firstOverlay == null -> "decisao ocorreu, mas overlay nao registrou aplicacao; medir showOverlay/bolinha"
                else -> "tempo ate primeira cor foi ${formatDuration(totalToFirstColor)}; comparar deltas acima para localizar etapa lenta"
            }
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

    private fun PerfEvent.isVisibleDecisionColor(): Boolean =
        isRideWindowState() && (raw.contains("color=verde") || raw.contains("color=vermelho"))

    private fun PerfEvent.isRideWindowState(): Boolean = raw.contains("bubble_state") && (hasRideWindow() || hasRideActivePackage())

    private fun PerfEvent.hasRideWindow(): Boolean = ridePackages.any { pkg -> "window=$pkg" in raw }

    private fun PerfEvent.hasRideActivePackage(): Boolean = ridePackages.any { pkg -> "active=$pkg" in raw }

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
