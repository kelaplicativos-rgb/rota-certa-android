package br.com.mapeiaia.rotacerta

/**
 * Produz um resumo autoritativo da sessão atual usando somente a trilha do gravador de voo.
 * Não participa da leitura, do parser, da rota, da decisão ou da renderização da bolinha.
 */
object FarolDiagnosticSummary0164 {
    private val stagePattern = Regex("(?:^|\\|\\s)stage=([A-Z0-9_]+)(?:\\s\\||$)")
    private val packagePattern = Regex("(?:^|\\|\\s)pacote=([^|]+)")
    private val pickupPattern = Regex("(?:^|;\\s*)pickup=([^;|]+)")
    private val destinationPattern = Regex("(?:^|;\\s*)destination=([^;|]+)")
    private val changedPattern = Regex("(?:^|;\\s*)mudou=(true|false)")

    fun withSummary(settings: AppSettings, recorderReport: String): String {
        if (recorderReport.isBlank()) return recorderReport

        val eventLines = recorderReport.lineSequence()
            .filter { it.startsWith("seq=") }
            .toList()

        val cardSessions = eventLines.count { stageOf(it) == "DRIVER_CARD_SESSION_0162" }
        val addressEvaluations = eventLines.filter { stageOf(it) == "BUBBLE_ADDRESS_EVALUATION" }
        val lastChangedCardIndex = eventLines.indexOfLast {
            stageOf(it) == "BUBBLE_CARD_STATE" && changedPattern.find(it)?.groupValues?.getOrNull(1) == "true"
        }.takeIf { it >= 0 } ?: addressEvaluations.lastOrNull()?.let { eventLines.indexOf(it) } ?: -1

        val attemptLines = if (lastChangedCardIndex >= 0) eventLines.drop(lastChangedCardIndex) else eventLines
        val lastAddress = attemptLines.lastOrNull { stageOf(it) == "BUBBLE_ADDRESS_EVALUATION" }
            ?: addressEvaluations.lastOrNull()
        val lastPackage = lastAddress?.let(::packageOf)
            ?: attemptLines.lastOrNull { stageOf(it) == "DRIVER_CARD_SESSION_0162" }?.let(::packageOf)
            ?: "não identificado"
        val pickup = lastAddress?.let { pickupPattern.find(it)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.isNotBlank() } ?: "não identificado"
        val destination = lastAddress?.let { destinationPattern.find(it)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.isNotBlank() } ?: "não identificado"

        val routeRequested = attemptLines.any { stageOf(it) == "BUBBLE_ROUTE_REQUESTED" }
        val cacheHit = attemptLines.any { stageOf(it) == "BUBBLE_CACHE_HIT" || stageOf(it) == "MAPS_ROUTE_CACHE_HIT" }
        val mapsCalled = attemptLines.any {
            val stage = stageOf(it)
            stage == "MAPS_HTTP_REQUEST" || stage == "MAPS_HTTP_RESPONSE" || stage == "MAPS_DRIVING_DISTANCE_REQUEST"
        }
        val decisionApplied = attemptLines.any {
            val stage = stageOf(it)
            stage == "BUBBLE_DECISION_READY" ||
                stage == "BUBBLE_DECISION_PAINTED" ||
                stage == "BUBBLE_DECISION_APPLIED" ||
                stage == "BUBBLE_RESULT_APPLIED"
        }
        val hasAddress = lastAddress != null
        val activeHome = settings.homeTargetEnabled && settings.homeCoordinate != null
        val activePins = settings.alternativeTargetEnabled &&
            WorkRegionTargetPolicy.editablePins(settings).any { it.enabled && it.coordinate != null }
        val activeTargets = activeHome || activePins

        val status = when {
            !hasAddress ->
                "SEM CARD COMPLETO: nenhum par válido de embarque e destino foi confirmado nesta sessão."
            !activeTargets ->
                "BLOQUEADA ANTES DA ROTA: Casa e Alfinetes estavam desativados ou sem coordenada ativa; o Google Maps não deve ser chamado."
            decisionApplied ->
                "DECISÃO APLICADA: a tentativa chegou à decisão e à atualização do farol."
            cacheHit ->
                "ROTA RECUPERADA DO CACHE: a tentativa utilizou distância exata já armazenada."
            routeRequested && mapsCalled ->
                "ROTA CONSULTADA: a tentativa chegou ao Google Maps; consulte os eventos seguintes para o resultado."
            routeRequested ->
                "ROTA SOLICITADA SEM RESPOSTA REGISTRADA: a tentativa saiu do parser, mas não há retorno do Google Maps na trilha."
            else ->
                "ENDEREÇOS CONFIRMADOS, MAS SEM ROTA: a tentativa parou antes de BUBBLE_ROUTE_REQUESTED."
        }

        val summary = buildString {
            appendLine("--- RESUMO AUTORITATIVO DA SESSÃO 0.1.164 ---")
            appendLine("Fonte: gravador de voo da sessão atual; este bloco substitui estados antigos persistidos para diagnóstico.")
            appendLine("Status: $status")
            appendLine("Sessões de card registradas: $cardSessions")
            appendLine("Avaliações com dois endereços: ${addressEvaluations.size}")
            appendLine("Último pacote de corrida: $lastPackage")
            appendLine("Último embarque: $pickup")
            appendLine("Último destino: $destination")
            appendLine("Casa ativa com coordenada: $activeHome")
            appendLine("Alfinete ativo com coordenada: $activePins")
            appendLine("Rota solicitada: $routeRequested")
            appendLine("Cache exato utilizado: $cacheHit")
            appendLine("Google Maps chamado: $mapsCalled")
            appendLine("Decisão aplicada: $decisionApplied")
            appendLine("--- FIM DO RESUMO AUTORITATIVO ---")
        }.trimEnd()

        return "$summary\n\n$recorderReport"
    }

    private fun stageOf(line: String): String? =
        stagePattern.find(line)?.groupValues?.getOrNull(1)

    private fun packageOf(line: String): String =
        packagePattern.find(line)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: "não identificado"
}
