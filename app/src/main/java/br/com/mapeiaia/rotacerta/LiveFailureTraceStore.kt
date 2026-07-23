package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trilha leve, em memoria, voltada a uma tentativa real de leitura da bolinha.
 *
 * Diferente do log global, este armazenamento agrupa acessibilidade, OCR, parser,
 * geocodificacao, rota, decisao e desenho dentro da mesma sessao. O arquivo so e
 * montado quando o usuario toca em "Gerar relatorio para anexar".
 */
object LiveFailureTraceStore {
    private const val MAX_SESSIONS = 10
    private const val MAX_EVENTS_PER_SESSION = 240
    private const val MAX_TEXT_LENGTH = 8_000
    private const val MAX_DETAIL_LENGTH = 1_400
    private const val EVENT_DEDUPE_WINDOW_MS = 1_200L
    private const val EMPTY_READ_NEW_SESSION_GAP_MS = 1_800L

    private val lock = Any()
    private val sessions = mutableListOf<TraceSession>()
    private var nextSessionId = 1L
    private var currentSessionId: Long? = null

    fun clear() {
        synchronized(lock) {
            sessions.clear()
            currentSessionId = null
            nextSessionId = 1L
        }
    }

    fun recordRead(
        source: String,
        packageName: String?,
        text: String,
        addresses: List<String>,
        destination: String?,
        active: Boolean,
        screenHash: Int?,
        generation: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        val cleanText = text.trim().take(MAX_TEXT_LENGTH)
        val cleanAddresses = addresses
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinctBy(::canonical)
        val addressSignature = cleanAddresses.joinToString("|") { canonical(it) }
        val cleanPackage = packageName?.trim().orEmpty()
        val normalizedSource = source.trim().ifBlank { "Unknown" }

        synchronized(lock) {
            val session = sessionForReadLocked(
                packageName = cleanPackage,
                text = cleanText,
                addressSignature = addressSignature,
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            val cleanDestination = destination?.trim()?.takeIf { it.isNotBlank() }
            val hasStoredRead = !session.accessibility?.text.isNullOrBlank() || !session.ocr?.text.isNullOrBlank()
            val samePackage = cleanPackage.isBlank() || session.packageName.isBlank() || cleanPackage == session.packageName
            if (cleanPackage.isNotBlank() && (session.packageName.isBlank() || cleanAddresses.isNotEmpty() || !hasStoredRead)) {
                session.packageName = cleanPackage
            }
            if (screenHash != null && (cleanAddresses.isNotEmpty() || session.screenHash == null || samePackage)) session.screenHash = screenHash
            if (generation != null && (cleanAddresses.isNotEmpty() || session.generation == null || samePackage)) session.generation = generation
            if (cleanAddresses.isNotEmpty() || (session.addresses.isEmpty() && samePackage)) {
                session.activeTrigger = active
                session.addresses = cleanAddresses
                session.addressSignature = addressSignature
                session.destination = cleanDestination
            } // session_retains_meaningful_read_v2

            val snapshot = ReadSnapshot(
                capturedAtMillis = nowMillis,
                hash = cleanText.hashCode(),
                text = cleanText,
            )
            val previous = when (normalizedSource.lowercase(Locale.ROOT)) {
                "ocr" -> session.ocr
                else -> session.accessibility
            }
            val mayReplaceStoredSource = cleanText.isNotBlank() &&
                (cleanAddresses.isNotEmpty() || previous == null || samePackage)
            if (mayReplaceStoredSource) {
                when (normalizedSource.lowercase(Locale.ROOT)) {
                    "ocr" -> session.ocr = snapshot
                    else -> session.accessibility = snapshot
                }
            }

            val readChanged = previous?.hash != snapshot.hash ||
                session.lastReadAddressSignature != addressSignature ||
                session.lastReadActive != active ||
                session.lastReadDestination != session.destination
            if (readChanged) {
                session.lastReadAddressSignature = addressSignature
                session.lastReadActive = active
                session.lastReadDestination = session.destination
                addEventLocked(
                    session = session,
                    stage = "READ_${normalizedSource.uppercase(Locale.ROOT)}",
                    details = buildString {
                        append("text_len=${cleanText.length}; text_hash=${snapshot.hash}; ")
                        append("addresses=${cleanAddresses.size}; active=$active; ")
                        append("destination=${session.destination ?: "nao identificado"}; ")
                        append("screen_hash=${screenHash ?: "nao informado"}; generation=${generation ?: "nao informada"}")
                    },
                    nowMillis = nowMillis,
                )
            }
        }
    }

    fun recordTrace(
        message: String,
        packageName: String? = null,
        generation: Long? = null,
        screenHash: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        val clean = oneLine(message)
        if (clean.isBlank()) return
        synchronized(lock) {
            val session = currentSessionLocked() ?: newSessionLocked(
                packageName = packageName.orEmpty(),
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            if (!packageName.isNullOrBlank() &&
                session.addresses.isEmpty() &&
                session.accessibility?.text.isNullOrBlank() &&
                session.ocr?.text.isNullOrBlank()
            ) session.packageName = packageName // session_preserves_origin_package_v2
            if (generation != null) session.generation = generation
            if (screenHash != null) session.screenHash = screenHash

            val stage = classifyTrace(clean)
            when {
                clean.contains("universal.result applied color=") -> {
                    session.finalColor = valueAfter(clean, "color=")?.substringBefore(' ')
                    session.finalDistanceKm = valueAfter(clean, "km=")?.substringBefore(' ')
                    session.failureHint = null
                }
                clean.contains("discarded", ignoreCase = true) ||
                    clean.contains("failed", ignoreCase = true) ||
                    clean.contains("error", ignoreCase = true) -> {
                    session.failureHint = clean.take(MAX_DETAIL_LENGTH)
                }
                clean.contains("universal.clear immediate=true") -> {
                    session.endedAtMillis = nowMillis
                    session.endReason = valueAfter(clean, "reason=") ?: clean
                }
            }
            addEventLocked(session, stage, clean, nowMillis)
        }
    }

    fun recordStep(
        stage: String,
        details: String,
        packageName: String? = null,
        generation: Long? = null,
        screenHash: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        synchronized(lock) {
            val session = currentSessionLocked() ?: newSessionLocked(
                packageName = packageName.orEmpty(),
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            if (!packageName.isNullOrBlank() &&
                session.addresses.isEmpty() &&
                session.accessibility?.text.isNullOrBlank() &&
                session.ocr?.text.isNullOrBlank()
            ) session.packageName = packageName // session_preserves_origin_package_v2
            if (generation != null) session.generation = generation
            if (screenHash != null) session.screenHash = screenHash
            val cleanStage = stage.trim().ifBlank { "STEP" }.uppercase(Locale.ROOT)
            val cleanDetails = oneLine(details)
            if (cleanStage.contains("ERROR") || cleanStage.contains("FAIL") || cleanStage.contains("REJECT")) {
                session.failureHint = cleanDetails
            }
            addEventLocked(session, cleanStage, cleanDetails, nowMillis)
        }
    }

    fun recordGeocode(
        label: String,
        query: String,
        coordinate: String?,
        elapsedMillis: Long,
        packageName: String? = null,
        generation: Long? = null,
        screenHash: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        val result = coordinate?.takeIf { it.isNotBlank() } ?: "nao localizado"
        synchronized(lock) {
            val session = currentSessionLocked() ?: newSessionLocked(
                packageName = packageName.orEmpty(),
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            session.geocodes[label] = "query=${oneLine(query).take(500)}; resultado=$result; tempo=${elapsedMillis}ms"
            if (coordinate.isNullOrBlank()) {
                session.failureHint = "Geocodificacao de $label nao encontrou coordenada para: ${oneLine(query).take(500)}"
            }
            addEventLocked(
                session,
                "GEOCODE_${label.uppercase(Locale.ROOT)}",
                session.geocodes.getValue(label),
                nowMillis,
            )
        }
    }

    fun recordRoute(
        label: String,
        distanceKm: Double?,
        elapsedMillis: Long,
        packageName: String? = null,
        generation: Long? = null,
        screenHash: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        val distanceText = distanceKm?.let { String.format(Locale("pt", "BR"), "%.3f km", it) } ?: "nao calculada"
        synchronized(lock) {
            val session = currentSessionLocked() ?: newSessionLocked(
                packageName = packageName.orEmpty(),
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            session.routes[label] = "distancia=$distanceText; tempo=${elapsedMillis}ms"
            if (distanceKm == null) session.failureHint = "Rota $label nao retornou distancia."
            addEventLocked(
                session,
                "ROUTE_${label.uppercase(Locale.ROOT)}",
                session.routes.getValue(label),
                nowMillis,
            )
        }
    }

    fun recordDecision(
        color: String,
        distanceKm: Double?,
        reason: String,
        packageName: String? = null,
        generation: Long? = null,
        screenHash: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!DiagnosticRuntimeGate.isEnabled()) return
        synchronized(lock) {
            val session = currentSessionLocked() ?: newSessionLocked(
                packageName = packageName.orEmpty(),
                screenHash = screenHash,
                generation = generation,
                nowMillis = nowMillis,
            )
            session.lastAtMillis = nowMillis
            session.finalColor = color
            session.finalDistanceKm = distanceKm?.let { String.format(Locale("pt", "BR"), "%.3f", it) }
            session.decisionReason = oneLine(reason)
            session.failureHint = if (color.equals("verde", true) || color.equals("vermelho", true)) null else session.failureHint
            addEventLocked(
                session,
                "DECISION",
                "color=$color; km=${session.finalDistanceKm ?: "nao exibido"}; reason=${session.decisionReason}",
                nowMillis,
            )
        }
    }

    fun exportReport(nowMillis: Long = System.currentTimeMillis()): String = synchronized(lock) {
        val selected = selectRelevantSessionLocked()
            ?: return@synchronized "Nenhuma sessao de leitura foi registrada nesta execucao."
        buildString {
            appendLine("Sessao selecionada: #${selected.id}")
            appendLine("Inicio: ${formatDate(selected.startedAtMillis)}")
            appendLine("Ultimo evento: ${formatDate(selected.lastAtMillis)}")
            appendLine("Idade no momento da exportacao: ${nowMillis - selected.lastAtMillis} ms")
            appendLine("Duracao observada: ${selected.lastAtMillis - selected.startedAtMillis} ms")
            appendLine("Pacote/janela: ${selected.packageName.ifBlank { "nao informado" }}")
            appendLine("Geracao: ${selected.generation ?: "nao informada"}")
            appendLine("Hash da tela: ${selected.screenHash ?: "nao informado"}")
            appendLine("Gatilho ativo: ${selected.activeTrigger}")
            appendLine("Enderecos detectados: ${selected.addresses.size}")
            appendLine("Destino escolhido: ${selected.destination ?: "nao identificado"}")
            appendLine("Cor final: ${selected.finalColor ?: "nao aplicada"}")
            appendLine("Km final: ${selected.finalDistanceKm ?: "nao exibido"}")
            appendLine("Motivo da decisao: ${selected.decisionReason ?: "nao registrado"}")
            appendLine("Fim/limpeza: ${selected.endReason ?: "sessao ainda sem limpeza registrada"}")
            appendLine("Falha provavel: ${inferFailure(selected)}")
            appendLine()

            appendLine("--- LINHA DO TEMPO DA SESSAO ---")
            if (selected.events.isEmpty()) {
                appendLine("sem eventos")
            } else {
                selected.events.forEach { event ->
                    val delta = event.atMillis - selected.startedAtMillis
                    append("+")
                    append(delta.toString().padStart(6, '0'))
                    append(" ms ")
                    append(formatClock(event.atMillis))
                    append(" [")
                    append(event.stage)
                    append("] ")
                    append(event.details)
                    if (event.repeatCount > 1) append("; repeticoes=${event.repeatCount}")
                    appendLine()
                }
            }
            appendLine()

            appendLine("--- ENDERECOS DETECTADOS NESTA SESSAO ---")
            if (selected.addresses.isEmpty()) {
                appendLine("nenhum")
            } else {
                selected.addresses.forEachIndexed { index, address -> appendLine("${index + 1}. $address") }
            }
            appendLine()

            appendLine("--- GEOCODIFICACAO ---")
            if (selected.geocodes.isEmpty()) appendLine("nenhuma tentativa registrada")
            selected.geocodes.forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()

            appendLine("--- ROTAS ---")
            if (selected.routes.isEmpty()) appendLine("nenhuma tentativa registrada")
            selected.routes.forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()

            appendLine("--- TEXTO DA ACESSIBILIDADE ---")
            appendLine(selected.accessibility?.text?.ifBlank { "vazio" } ?: "nao capturado")
            appendLine()
            appendLine("--- TEXTO DO OCR ---")
            appendLine(selected.ocr?.text?.ifBlank { "vazio" } ?: "nao capturado")
            appendLine()

            appendLine("--- SESSOES RECENTES RESUMIDAS ---")
            sessions.takeLast(6).forEach { session ->
                appendLine(
                    "#${session.id}; inicio=${formatDate(session.startedAtMillis)}; pacote=${session.packageName.ifBlank { "nao informado" }}; " +
                        "enderecos=${session.addresses.size}; destino=${session.destination ?: "nao identificado"}; " +
                        "cor=${session.finalColor ?: "sem cor"}; km=${session.finalDistanceKm ?: "sem km"}; " +
                        "falha=${inferFailure(session)}",
                )
            }
        }.trimEnd()
    }

    private fun sessionForReadLocked(
        packageName: String,
        text: String,
        addressSignature: String,
        screenHash: Int?,
        generation: Long?,
        nowMillis: Long,
    ): TraceSession {
        val current = currentSessionLocked()
        val textHash = text.hashCode()
        val shouldStart = when {
            current == null -> true
            current.endedAtMillis != null && text.isNotBlank() &&
                (addressSignature.isNotBlank() ||
                    (current.accessibility?.text.isNullOrBlank() && current.ocr?.text.isNullOrBlank())) -> true
            addressSignature.isNotBlank() && current.addressSignature.isNotBlank() && addressSignature != current.addressSignature -> true
            packageName.isNotBlank() && current.packageName.isNotBlank() && packageName != current.packageName &&
                text.isNotBlank() &&
                (addressSignature.isNotBlank() ||
                    (current.accessibility?.text.isNullOrBlank() && current.ocr?.text.isNullOrBlank())) -> true // session_start_guard_v2
            addressSignature.isBlank() && text.isNotBlank() &&
                current.lastReadTextHash != null && current.lastReadTextHash != textHash &&
                nowMillis - current.lastAtMillis >= EMPTY_READ_NEW_SESSION_GAP_MS -> true
            else -> false
        }
        val session = if (shouldStart) {
            newSessionLocked(packageName, screenHash, generation, nowMillis)
        } else {
            current ?: newSessionLocked(packageName, screenHash, generation, nowMillis)
        }
        session.lastReadTextHash = textHash
        return session
    }

    private fun newSessionLocked(
        packageName: String,
        screenHash: Int?,
        generation: Long?,
        nowMillis: Long,
    ): TraceSession {
        val session = TraceSession(
            id = nextSessionId++,
            startedAtMillis = nowMillis,
            lastAtMillis = nowMillis,
            packageName = packageName,
            screenHash = screenHash,
            generation = generation,
        )
        sessions += session
        currentSessionId = session.id
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(0)
        addEventLocked(
            session,
            "SESSION_START",
            "package=${packageName.ifBlank { "nao informado" }}; screen_hash=${screenHash ?: "nao informado"}; generation=${generation ?: "nao informada"}",
            nowMillis,
        )
        return session
    }

    private fun currentSessionLocked(): TraceSession? = currentSessionId?.let { id -> sessions.lastOrNull { it.id == id } }

    private fun selectRelevantSessionLocked(): TraceSession? =
        sessions.lastOrNull(::looksLikeRideAttempt) ?: sessions.lastOrNull { session ->
            session.events.any { event ->
                event.stage in setOf("SCREENSHOT_FAIL", "ERROR", "DISCARDED")
            } || !session.accessibility?.text.isNullOrBlank() || !session.ocr?.text.isNullOrBlank()
        } ?: sessions.lastOrNull() // session_selects_ride_attempt_v2

    private fun looksLikeRideAttempt(session: TraceSession): Boolean {
        if (session.addresses.isNotEmpty()) return true
        val combinedText = listOfNotNull(session.accessibility?.text, session.ocr?.text)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return listOf(
            "pedido de viagem",
            "solicitacao de viagem",
            "solicitação de viagem",
            "aceitar por",
            "ofereca sua tarifa",
            "ofereça sua tarifa",
            "preco justo",
            "preço justo",
            "corrida",
            "embarque",
            "destino",
        ).any(combinedText::contains)
    }

    private fun addEventLocked(session: TraceSession, stage: String, details: String, nowMillis: Long) {
        val cleanDetails = oneLine(details).take(MAX_DETAIL_LENGTH)
        val last = session.events.lastOrNull()
        if (
            last != null &&
            last.stage == stage &&
            last.details == cleanDetails &&
            nowMillis - last.atMillis <= EVENT_DEDUPE_WINDOW_MS
        ) {
            last.atMillis = nowMillis
            last.repeatCount += 1
            return
        }
        session.events += TraceEvent(nowMillis, stage, cleanDetails)
        while (session.events.size > MAX_EVENTS_PER_SESSION) session.events.removeAt(0)
    }

    private fun classifyTrace(message: String): String = when {
        message.startsWith("universal.event") -> "WINDOW_EVENT"
        message.startsWith("universal.trigger") -> "TRIGGER"
        message.startsWith("universal.screen.changed") -> "SCREEN_CHANGE"
        message.startsWith("universal.route.cache") -> "ROUTE_CACHE"
        message.startsWith("universal.result") -> "RESULT"
        message.startsWith("universal.clear") -> "CLEAR"
        message.contains("screenshot.request started") -> "SCREENSHOT_START"
        message.contains("screenshot.ocr success") -> "OCR_SUCCESS"
        message.contains("screenshot", ignoreCase = true) && message.contains("failed", ignoreCase = true) -> "SCREENSHOT_FAIL"
        message.contains("discarded", ignoreCase = true) -> "DISCARDED"
        message.contains("overlay", ignoreCase = true) -> "OVERLAY"
        message.contains("error", ignoreCase = true) -> "ERROR"
        else -> "TRACE"
    }

    private fun inferFailure(session: TraceSession): String = when {
        !session.finalColor.isNullOrBlank() &&
            (session.finalColor.equals("verde", true) || session.finalColor.equals("vermelho", true)) ->
            "nenhuma falha final registrada; a decisao foi aplicada"
        session.accessibility?.text.isNullOrBlank() && session.ocr?.text.isNullOrBlank() ->
            "nenhum texto foi capturado por acessibilidade nem OCR"
        session.addresses.size < 2 ->
            "houve texto, mas o parser encontrou somente ${session.addresses.size} endereco(s); verificar o texto bruto abaixo"
        session.destination.isNullOrBlank() ->
            "dois ou mais enderecos apareceram, mas nenhum destino final foi selecionado"
        session.geocodes["destination"]?.contains("nao localizado") == true ->
            "o destino foi identificado, mas a geocodificacao nao encontrou coordenada"
        session.routes.isEmpty() ->
            "o destino foi identificado, mas nenhuma tentativa de rota ficou registrada"
        session.routes.values.all { "nao calculada" in it } ->
            "a geocodificacao ocorreu, mas o Google Routes nao retornou distancia"
        !session.failureHint.isNullOrBlank() -> session.failureHint.orEmpty()
        else -> "a sessao nao chegou a aplicar verde/vermelho; conferir a ultima etapa da linha do tempo"
    }

    private fun oneLine(value: String): String = value
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun valueAfter(value: String, marker: String): String? =
        value.substringAfter(marker, "").takeIf { it.isNotBlank() }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun formatDate(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))

    private fun formatClock(value: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))

    private data class ReadSnapshot(
        val capturedAtMillis: Long,
        val hash: Int,
        val text: String,
    )

    private data class TraceEvent(
        var atMillis: Long,
        val stage: String,
        val details: String,
        var repeatCount: Int = 1,
    )

    private data class TraceSession(
        val id: Long,
        val startedAtMillis: Long,
        var lastAtMillis: Long,
        var packageName: String,
        var screenHash: Int?,
        var generation: Long?,
        var activeTrigger: Boolean = false,
        var addressSignature: String = "",
        var addresses: List<String> = emptyList(),
        var destination: String? = null,
        var accessibility: ReadSnapshot? = null,
        var ocr: ReadSnapshot? = null,
        var finalColor: String? = null,
        var finalDistanceKm: String? = null,
        var decisionReason: String? = null,
        var failureHint: String? = null,
        var endedAtMillis: Long? = null,
        var endReason: String? = null,
        var lastReadTextHash: Int? = null,
        var lastReadAddressSignature: String = "",
        var lastReadActive: Boolean? = null,
        var lastReadDestination: String? = null,
        val geocodes: LinkedHashMap<String, String> = linkedMapOf(),
        val routes: LinkedHashMap<String, String> = linkedMapOf(),
        val events: MutableList<TraceEvent> = mutableListOf(),
    )
}
