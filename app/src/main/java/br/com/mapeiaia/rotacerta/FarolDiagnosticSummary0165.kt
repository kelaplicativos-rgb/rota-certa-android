package br.com.mapeiaia.rotacerta

/**
 * Reconstrói tentativas independentes do farol a partir da trilha da sessão.
 * Não participa da leitura, do parser, da rota, da decisão ou do desenho da bolinha.
 */
object FarolDiagnosticSummary0165 {
    private val stagePattern = Regex("(?:^|\\|\\s)stage=([A-Z0-9_]+)(?:\\s\\||$)")
    private val packagePattern = Regex("(?:^|\\|\\s)pacote=([^|]+)")
    private val monoPattern = Regex("(?:^|\\|\\s)mono_ns=(\\d+)")
    private val pickupPattern = Regex("(?:^|[;|]\\s*)pickup=([^;|]+)")
    private val destinationPattern = Regex("(?:^|[;|]\\s*)destination=([^;|]+)")
    private val activePattern = Regex("(?:^|[;|]\\s*)ativo=(true|false)")
    private val changedPattern = Regex("(?:^|[;|]\\s*)mudou=(true|false)")
    private val colorPattern = Regex("(?:^|[;|]\\s*)cor=([^;|]+)")
    private val distancePattern = Regex("(?:^|[;|]\\s*)distancia=([0-9]+(?:[.,][0-9]+)?)")
    private val distancesPattern = Regex("(?:^|[;|]\\s*)(?:distances|values)=\\[([^]]+)]")
    private val httpCodePattern = Regex("(?:^|[;|]\\s*)code=(\\d+)")
    private val reasonPattern = Regex("(?:^|[;|]\\s*)reason=([^;|]+)")

    private data class Event(
        val index: Int,
        val line: String,
        val stage: String,
        val packageName: String,
        val monoNs: Long?,
    )

    private data class Attempt(
        val startIndex: Int,
        val endIndexExclusive: Int,
        val packageName: String,
        val pickup: String,
        val destination: String,
        val events: List<Event>,
    )

    fun withSummary(settings: AppSettings, recorderReport: String): String {
        if (recorderReport.isBlank()) return recorderReport

        val events = recorderReport.lineSequence()
            .filter { it.startsWith("seq=") }
            .mapIndexedNotNull { index, line ->
                val stage = stageOf(line) ?: return@mapIndexedNotNull null
                Event(
                    index = index,
                    line = line,
                    stage = stage,
                    packageName = packageOf(line),
                    monoNs = monoPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull(),
                )
            }
            .toList()

        val attempts = buildAttempts(events)
        val latestAttempt = attempts.lastOrNull()
        val paintedDecisions = events.filter { it.stage == "BUBBLE_DECISION_PAINTED" }
        val latestDecision = paintedDecisions.lastOrNull()
        val latestDecisionAttempt = latestDecision?.let { decision ->
            attempts.lastOrNull { decision.index in it.startIndex until it.endIndexExclusive }
        }

        val activeHome = settings.homeTargetEnabled && settings.homeCoordinate != null
        val activePins = settings.alternativeTargetEnabled &&
            WorkRegionTargetPolicy.editablePins(settings).any { it.enabled && it.coordinate != null }
        val activeTargets = activeHome || activePins

        val overallStatus = when {
            latestDecision != null ->
                "SESSÃO COM DECISÃO VÁLIDA: ao menos uma rota foi concluída e o farol foi atualizado."
            latestAttempt == null ->
                "SEM TENTATIVA COMPLETA: nenhum card com dois endereços e mudança real de destino foi confirmado."
            else -> latestAttemptStatus(latestAttempt, activeTargets)
        }

        val summary = buildString {
            appendLine("--- RESUMO AUTORITATIVO POR TENTATIVAS 0.1.165 ---")
            appendLine("Fonte: eventos da sessão atual agrupados por mudança real de destino; leituras incompletas posteriores não apagam decisões válidas anteriores.")
            appendLine("Resultado geral: $overallStatus")
            appendLine("Sessões de card registradas: ${events.count { it.stage == "DRIVER_CARD_SESSION_0162" }}")
            appendLine("Avaliações ativas com dois endereços: ${events.count(::isActiveAddressEvaluation)}")
            appendLine("Tentativas reconhecidas: ${attempts.size}")
            appendLine("Decisões pintadas na sessão: ${paintedDecisions.size}")
            appendLine("Casa ativa com coordenada: $activeHome")
            appendLine("Alfinete ativo com coordenada: $activePins")

            appendLine("--- ÚLTIMA DECISÃO VÁLIDA ---")
            if (latestDecision == null) {
                appendLine("Nenhuma decisão verde/vermelha foi pintada nesta sessão.")
            } else {
                appendLine("Pacote: ${latestDecision.packageName}")
                appendLine("Destino: ${latestDecisionAttempt?.destination ?: "não identificado"}")
                appendLine("Cor: ${detail(latestDecision.line, colorPattern) ?: "não identificada"}")
                appendLine("Distância: ${detail(latestDecision.line, distancePattern)?.replace(',', '.') ?: "não registrada"} km")
            }

            appendLine("--- ÚLTIMA TENTATIVA RECONHECIDA ---")
            if (latestAttempt == null) {
                appendLine("Nenhuma tentativa completa foi encontrada.")
            } else {
                appendAttempt(latestAttempt, activeTargets)
            }
            appendLine("--- FIM DO RESUMO AUTORITATIVO ---")
        }.trimEnd()

        return "$summary\n\n$recorderReport"
    }

    private fun buildAttempts(events: List<Event>): List<Attempt> {
        val changedIndices = events.indices.filter { index ->
            val event = events[index]
            event.stage == "BUBBLE_CARD_STATE" &&
                detail(event.line, changedPattern) == "true"
        }

        return changedIndices.mapIndexedNotNull { position, changedIndex ->
            val changed = events[changedIndex]
            val addressIndex = (changedIndex - 1 downTo maxOf(0, changedIndex - 16)).firstOrNull { candidateIndex ->
                val candidate = events[candidateIndex]
                candidate.packageName == changed.packageName && isActiveAddressEvaluation(candidate)
            } ?: return@mapIndexedNotNull null

            val address = events[addressIndex]
            val pickup = detail(address.line, pickupPattern)?.trim().orEmpty()
            val destination = detail(address.line, destinationPattern)?.trim().orEmpty()
            if (pickup.isBlank() || destination.isBlank()) return@mapIndexedNotNull null

            val end = changedIndices.getOrNull(position + 1) ?: events.size
            Attempt(
                startIndex = address.index,
                endIndexExclusive = events.getOrNull(end)?.index ?: events.size,
                packageName = changed.packageName,
                pickup = pickup,
                destination = destination,
                events = events.subList(addressIndex, end),
            )
        }
    }

    private fun StringBuilder.appendAttempt(attempt: Attempt, activeTargets: Boolean) {
        val routeRequested = attempt.events.firstOrNull { it.stage == "BUBBLE_ROUTE_REQUESTED" }
        val routeStartIndex = routeRequested?.index ?: attempt.startIndex
        val response = attempt.events.lastOrNull {
            it.index >= routeStartIndex && it.stage == "MAPS_HTTP_RESPONSE"
        }
        val parsed = attempt.events.lastOrNull {
            it.index >= routeStartIndex &&
                (it.stage == "MAPS_HTTP_PARSED" ||
                    it.stage == "MAPS_ROUTE_NETWORK_RESULT" ||
                    it.stage == "MAPS_ROUTE_MATRIX_COMPLETE")
        }
        val decision = attempt.events.lastOrNull { it.stage == "BUBBLE_DECISION_PAINTED" }
        val clearBeforeResponse = response?.let { http ->
            attempt.events.lastOrNull { event ->
                event.index >= routeStartIndex &&
                    event.index < http.index &&
                    event.stage == "BUBBLE_CLEAR_REQUEST" &&
                    detail(event.line, reasonPattern)?.contains("Janela fora", ignoreCase = true) == true
            }
        }
        val cacheHit = attempt.events.any {
            it.stage == "BUBBLE_CACHE_HIT" || it.stage == "MAPS_ROUTE_CACHE_HIT"
        }
        val distance = parsed?.let(::distanceListFirst)
            ?: decision?.let { detail(it.line, distancePattern)?.replace(',', '.') }
        val deltaMs = if (response?.monoNs != null && clearBeforeResponse?.monoNs != null) {
            ((response.monoNs - clearBeforeResponse.monoNs) / 1_000_000L).coerceAtLeast(0L)
        } else null

        appendLine("Status: ${latestAttemptStatus(attempt, activeTargets)}")
        appendLine("Pacote: ${attempt.packageName}")
        appendLine("Embarque: ${attempt.pickup}")
        appendLine("Destino: ${attempt.destination}")
        appendLine("Rota solicitada: ${routeRequested != null}")
        appendLine("Cache exato utilizado: $cacheHit")
        appendLine("Google Maps respondeu: ${response != null}")
        appendLine("Código HTTP: ${response?.let { detail(it.line, httpCodePattern) } ?: "não registrado"}")
        appendLine("Distância retornada: ${distance?.let { "$it km" } ?: "não registrada"}")
        appendLine("Decisão aplicada nesta tentativa: ${decision != null}")
        if (clearBeforeResponse != null) {
            appendLine("Limpeza anterior à resposta: ${detail(clearBeforeResponse.line, reasonPattern) ?: "motivo não registrado"}")
            appendLine("Intervalo entre limpeza e resposta: ${deltaMs?.let { "$it ms" } ?: "não calculado"}")
        }
    }

    private fun latestAttemptStatus(attempt: Attempt, activeTargets: Boolean): String {
        val routeRequested = attempt.events.firstOrNull { it.stage == "BUBBLE_ROUTE_REQUESTED" }
        val routeStartIndex = routeRequested?.index ?: attempt.startIndex
        val response = attempt.events.lastOrNull {
            it.index >= routeStartIndex && it.stage == "MAPS_HTTP_RESPONSE"
        }
        val decision = attempt.events.lastOrNull { it.stage == "BUBBLE_DECISION_PAINTED" }
        val clearBeforeResponse = response?.let { http ->
            attempt.events.any { event ->
                event.index >= routeStartIndex &&
                    event.index < http.index &&
                    event.stage == "BUBBLE_CLEAR_REQUEST" &&
                    detail(event.line, reasonPattern)?.contains("Janela fora", ignoreCase = true) == true
            }
        } == true
        val cacheHit = attempt.events.any {
            it.stage == "BUBBLE_CACHE_HIT" || it.stage == "MAPS_ROUTE_CACHE_HIT"
        }

        return when {
            decision != null ->
                "DECISÃO APLICADA: a rota foi concluída e o farol foi pintado."
            response != null && clearBeforeResponse ->
                "RESPOSTA DESCARTADA APÓS SAÍDA/OCULTAÇÃO DO CARD: o Google Maps respondeu, mas a janela já havia sido limpa."
            response != null ->
                "GOOGLE MAPS RESPONDEU, MAS A DECISÃO NÃO FOI APLICADA: verifique cancelamento ou validade da geração."
            cacheHit ->
                "ROTA RECUPERADA DO CACHE, SEM PINTURA REGISTRADA NESTA TENTATIVA."
            routeRequested != null ->
                "ROTA SOLICITADA SEM RESPOSTA REGISTRADA."
            !activeTargets ->
                "BLOQUEADA ANTES DA ROTA: Casa e Alfinetes estavam desativados ou sem coordenada ativa."
            else ->
                "ENDEREÇOS CONFIRMADOS, MAS SEM ROTA: a tentativa parou antes de BUBBLE_ROUTE_REQUESTED."
        }
    }

    private fun isActiveAddressEvaluation(event: Event): Boolean =
        event.stage == "BUBBLE_ADDRESS_EVALUATION" &&
            detail(event.line, activePattern) == "true" &&
            !detail(event.line, pickupPattern).isNullOrBlank() &&
            !detail(event.line, destinationPattern).isNullOrBlank()

    private fun distanceListFirst(event: Event): String? {
        val raw = detail(event.line, distancesPattern) ?: return null
        return raw.split(',').firstOrNull()?.trim()?.replace(',', '.')?.takeIf { it.isNotBlank() }
    }

    private fun stageOf(line: String): String? =
        stagePattern.find(line)?.groupValues?.getOrNull(1)

    private fun packageOf(line: String): String =
        packagePattern.find(line)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: "não identificado"

    private fun detail(line: String, pattern: Regex): String? =
        pattern.find(line)?.groupValues?.getOrNull(1)?.trim()
}
