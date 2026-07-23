package br.com.mapeiaia.rotacerta

enum class UniversalLiveReadSource {
    Accessibility,
    Ocr,
}

enum class UniversalLiveReadAction {
    Analyze,
    Ignore,
    Clear,
}

/**
 * Impede que OCR e acessibilidade disputem a mesma bolinha.
 *
 * A acessibilidade tem prioridade quando encontrou enderecos. O OCR continua
 * como fallback para telas que nao expoem texto. Uma leitura vazia isolada nao
 * encerra um card: o fim precisa ser confirmado pela mesma fonte ativa.
 */
class UniversalLiveReadGate(
    private val accessibilityPriorityMillis: Long = 1_000L,
    private val ocrGraceMillis: Long = 750L,
    private val inactiveConfirmationsRequired: Int = 2,
) {
    private var activeSource: UniversalLiveReadSource? = null
    private var lastAccessibilityActiveAtMillis: Long = 0L
    private var lastOcrActiveAtMillis: Long = 0L
    private var lastInactiveSource: UniversalLiveReadSource? = null
    private var consecutiveInactiveReads: Int = 0

    fun submit(
        source: UniversalLiveReadSource,
        active: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): UniversalLiveReadAction {
        if (active) {
            resetInactiveConfirmation()
            return when (source) {
                UniversalLiveReadSource.Accessibility -> {
                    activeSource = source
                    lastAccessibilityActiveAtMillis = nowMillis
                    UniversalLiveReadAction.Analyze
                }

                UniversalLiveReadSource.Ocr -> {
                    lastOcrActiveAtMillis = nowMillis
                    if (
                        activeSource == UniversalLiveReadSource.Accessibility &&
                        isFresh(lastAccessibilityActiveAtMillis, nowMillis, accessibilityPriorityMillis)
                    ) {
                        UniversalLiveReadAction.Ignore
                    } else {
                        activeSource = source
                        UniversalLiveReadAction.Analyze
                    }
                }
            }
        }

        return when (source) {
            UniversalLiveReadSource.Ocr -> {
                when {
                    activeSource == UniversalLiveReadSource.Accessibility -> UniversalLiveReadAction.Ignore
                    activeSource == UniversalLiveReadSource.Ocr -> confirmInactive(source)
                    else -> UniversalLiveReadAction.Ignore
                }
            }

            UniversalLiveReadSource.Accessibility -> {
                when {
                    activeSource == UniversalLiveReadSource.Ocr &&
                        isFresh(lastOcrActiveAtMillis, nowMillis, ocrGraceMillis) ->
                        UniversalLiveReadAction.Ignore
                    activeSource == UniversalLiveReadSource.Accessibility -> confirmInactive(source)
                    else -> UniversalLiveReadAction.Ignore
                }
            }
        }
    }

    fun reset() {
        activeSource = null
        lastAccessibilityActiveAtMillis = 0L
        lastOcrActiveAtMillis = 0L
        resetInactiveConfirmation()
    }

    private fun confirmInactive(source: UniversalLiveReadSource): UniversalLiveReadAction {
        if (lastInactiveSource != source) {
            lastInactiveSource = source
            consecutiveInactiveReads = 1
        } else {
            consecutiveInactiveReads += 1
        }
        if (consecutiveInactiveReads < inactiveConfirmationsRequired.coerceAtLeast(1)) {
            return UniversalLiveReadAction.Ignore
        }
        activeSource = null
        resetInactiveConfirmation()
        return UniversalLiveReadAction.Clear
    }

    private fun resetInactiveConfirmation() {
        lastInactiveSource = null
        consecutiveInactiveReads = 0
    }

    private fun isFresh(timestamp: Long, nowMillis: Long, windowMillis: Long): Boolean =
        timestamp > 0L && nowMillis >= timestamp && nowMillis - timestamp <= windowMillis
}

/**
 * Regras pequenas e testaveis para o caminho rapido da leitura ao vivo.
 *
 * Em alguns aparelhos, TYPE_ACCESSIBILITY_OVERLAY vira temporariamente a raiz
 * de acessibilidade enquanto o app de corrida continua em primeiro plano. A
 * arvore da propria bolinha nao contem o card e produz texto vazio. Esse vazio
 * nao representa saida do card e nao pode cancelar geocodificacao ou rota.
 */
object UniversalFastReadPolicy {
    private val passivePackages = setOf(
        "com.android.systemui",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.android.settings",
    )

    fun shouldIgnoreTransientEmptyAccessibilityRead(
        text: String,
        rootPackageName: String?,
        effectivePackageName: String?,
        ownPackageName: String,
    ): Boolean {
        if (text.isNotBlank()) return false
        val own = normalize(ownPackageName) ?: return false
        val root = normalize(rootPackageName)
        val effective = normalize(effectivePackageName)
        return root == own && effective != null && effective != own
    }

    fun shouldScanLivePackage(
        packageName: String?,
        ownPackageName: String,
    ): Boolean {
        val normalized = normalize(packageName) ?: return false
        val own = normalize(ownPackageName)
        if (normalized == own || normalized in passivePackages) return false
        if (normalized.contains("launcher")) return false
        if (normalized.contains("inputmethod") || normalized.contains("keyboard")) return false
        return true
    }

    fun shouldRequestOcr(
        accessibilityOwnsCard: Boolean,
        hasActiveAddressSignature: Boolean,
    ): Boolean = !(accessibilityOwnsCard && hasActiveAddressSignature)

    fun minimumOcrIntervalMillis(hasActiveAddressSignature: Boolean): Long =
        if (hasActiveAddressSignature) 650L else 300L

    fun minimumAccessibilityScanIntervalMillis(
        accessibilityOwnsCard: Boolean,
        hasActiveAddressSignature: Boolean,
    ): Long = if (hasActiveAddressSignature && !accessibilityOwnsCard) {
        650L // universal_accessibility_scan_watchdog_0_1_110
    } else {
        120L
    }

    data class OcrRequestToken(
        val observedPackageName: String,
        val screenGeneration: Long,
        val windowGeneration: Long,
    )

    fun createOcrRequestToken(
        observedPackageName: String?,
        resolvedPackageName: String?,
        ownPackageName: String,
        screenGeneration: Long,
        windowGeneration: Long,
    ): OcrRequestToken? {
        val observed = normalize(observedPackageName) ?: return null
        val resolved = normalize(resolvedPackageName) ?: return null
        if (!shouldScanLivePackage(observed, ownPackageName)) return null
        if (observed != resolved) return null
        return OcrRequestToken(
            observedPackageName = observed,
            screenGeneration = screenGeneration,
            windowGeneration = windowGeneration,
        ) // universal_ocr_freshness_policy_0_1_120
    }

    fun isOcrRequestFresh(
        token: OcrRequestToken,
        observedPackageName: String?,
        resolvedPackageName: String?,
        ownPackageName: String,
        screenGeneration: Long,
        windowGeneration: Long,
    ): Boolean {
        val observed = normalize(observedPackageName) ?: return false
        val resolved = normalize(resolvedPackageName) ?: return false
        return shouldScanLivePackage(observed, ownPackageName) &&
            token.observedPackageName == observed &&
            token.observedPackageName == resolved &&
            token.screenGeneration == screenGeneration &&
            token.windowGeneration == windowGeneration
    }

    const val ROUTE_INFLIGHT_GRACE_MILLIS = 2_500L

    fun shouldProtectRouteFromForeignEvent(
        hasActiveAddressSignature: Boolean,
        routeInFlight: Boolean,
        lastActiveReadAtMillis: Long,
        nowMillis: Long,
        activeRidePackageName: String?,
        incomingPackageName: String?,
    ): Boolean {
        if (!hasActiveAddressSignature || !routeInFlight) return false
        val active = normalize(activeRidePackageName) ?: return false
        val incoming = normalize(incomingPackageName) ?: return false
        if (active == incoming) return false
        return isInsideInflightGrace(lastActiveReadAtMillis, nowMillis)
    } // universal_route_inflight_policy_0_1_120

    fun shouldIgnoreTransientInactiveRead(
        hasActiveAddressSignature: Boolean,
        routeInFlight: Boolean,
        lastActiveReadAtMillis: Long,
        nowMillis: Long,
    ): Boolean = hasActiveAddressSignature &&
        routeInFlight &&
        isInsideInflightGrace(lastActiveReadAtMillis, nowMillis)

    private fun isInsideInflightGrace(lastActiveReadAtMillis: Long, nowMillis: Long): Boolean =
        lastActiveReadAtMillis > 0L &&
            nowMillis >= lastActiveReadAtMillis &&
            nowMillis - lastActiveReadAtMillis <= ROUTE_INFLIGHT_GRACE_MILLIS

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}

data class UniversalRideCardEvidenceDecision(
    val accepted: Boolean,
    val score: Int,
    val reason: String,
)

/**
 * Impede que listas de enderecos, mapas, documentos e fotos de produtos sejam
 * tratadas como ofertas de corrida. A verificacao acontece antes de qualquer
 * geocodificacao ou chamada de rota.
 */
object UniversalRideCardEvidencePolicy {
    private val knownRidePackages = setOf(
        "com.app99.driver",
        "com.ubercab.driver",
        "sinet.startup.indriver",
    )
    private val timeTokenRegex = Regex(
        "\\b\\d{1,3}\\s*(?:min|minutos?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val tripDistanceRegex = Regex(
        "\\b\\d+(?:[,.]\\d+)?\\s*(?:km|m)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val moneyRegex = Regex(
        "R\\$\\s*\\d+(?:[,.]\\d{1,2})?",
        RegexOption.IGNORE_CASE,
    )
    private val perKmRegex = Regex(
        "R\\$\\s*\\d+(?:[,.]\\d{1,2})?\\s*/\\s*km",
        RegexOption.IGNORE_CASE,
    )
    private val rideMarkerRegex = Regex(
        "\\b(?:corridas?|perfil\\s+(?:essencial|premium)|[aá]rea\\s+de\\s+risco|tarifa(?:\\s+base)?|aceitar|ofere[cç]a|pedido\\s+de\\s+viagem|uberx|comfort|99pop|din[aâ]mic[ao])\\b",
        RegexOption.IGNORE_CASE,
    )
    private val malformedStreetRegex = Regex(
        "\\b(?:rua|avenida)[.:;,]+\\s*\\p{L}",
        RegexOption.IGNORE_CASE,
    )

    fun evaluate(
        text: String,
        addresses: List<String>,
        destination: String?,
        packageName: String?,
    ): UniversalRideCardEvidenceDecision {
        val normalizedAddresses = addresses
            .map { address -> address.trim() }
            .filter { address -> address.isNotBlank() }
        if (normalizedAddresses.size < 2 || destination.isNullOrBlank()) {
            return UniversalRideCardEvidenceDecision(false, 0, "menos_de_dois_enderecos")
        }
        if (normalizedAddresses.any(malformedStreetRegex::containsMatchIn) ||
            malformedStreetRegex.containsMatchIn(destination)
        ) {
            return UniversalRideCardEvidenceDecision(false, 0, "logradouro_deformado")
        }

        val hasTime = timeTokenRegex.containsMatchIn(text)
        val hasTripDistance = tripDistanceRegex.containsMatchIn(text)
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasPerKm = perKmRegex.containsMatchIn(text)
        val markerCount = rideMarkerRegex.findAll(text).map { it.value.lowercase() }.distinct().count()
        val hasTripMetrics = hasTime && hasTripDistance
        val normalizedPackage = packageName?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val knownRideApp = normalizedPackage != null && normalizedPackage in knownRidePackages

        val score = (if (hasTripMetrics) 2 else 0) +
            (if (hasMoney) 1 else 0) +
            (if (hasPerKm) 1 else 0) +
            (if (markerCount > 0) 1 else 0)

        val acceptedInRideApp = knownRideApp &&
            (hasTripMetrics || hasMoney || hasPerKm || markerCount > 0)
        val acceptedInExternalViewer =
            (hasTripMetrics && (hasMoney || markerCount > 0)) ||
                (hasPerKm && (hasTime || markerCount > 0)) ||
                (hasMoney && markerCount >= 2)
        val accepted = acceptedInRideApp || acceptedInExternalViewer
        return UniversalRideCardEvidenceDecision(
            accepted = accepted,
            score = score,
            reason = if (accepted) "card_de_corrida_confirmado" else "sem_evidencia_de_corrida",
        )
    }
} // universal_ride_card_evidence_0_1_112

/** Mantem o historico com uma entrada util por decisao, sem dezenas de copias. */
class UniversalAnalysisDeduper(
    private val duplicateWindowMillis: Long = 60_000L,
) {
    private var lastSignature: String? = null
    private var lastPersistedAtMillis: Long = 0L

    @Synchronized
    fun shouldPersist(
        signature: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val duplicate = signature == lastSignature &&
            nowMillis >= lastPersistedAtMillis &&
            nowMillis - lastPersistedAtMillis < duplicateWindowMillis
        if (duplicate) return false

        lastSignature = signature
        lastPersistedAtMillis = nowMillis
        return true
    }
}

data class UniversalWindowResolution(
    val effectivePackageName: String?,
    val lastExternalPackageName: String?,
)

/**
 * Separa a janela real do aplicativo em primeiro plano da TYPE_ACCESSIBILITY_OVERLAY.
 *
 * A bolinha e o menu pertencem ao pacote do Rota Certa. Em alguns aparelhos o
 * Android os entrega como rootInActiveWindow, mesmo com o card de corrida ainda
 * visivel por baixo. Nessa situacao deve ser preservado o ultimo pacote externo.
 * O pacote proprio so assume o primeiro plano quando existe um evento real da
 * MainActivity.
 */
object UniversalWindowPackageResolver {
    fun resolve(
        rootPackageName: String?,
        activePackageName: String?,
        lastExternalPackageName: String?,
        ownPackageName: String,
    ): UniversalWindowResolution {
        val own = normalize(ownPackageName)
        val root = normalize(rootPackageName)
        val active = normalize(activePackageName)
        val previousExternal = normalize(lastExternalPackageName)?.takeUnless { it == own }

        val updatedExternal = when {
            root != null && root != own -> root
            active != null && active != own -> active
            else -> previousExternal
        }

        val effective = when {
            root != null && root != own -> root
            root == own && active == own -> own
            root == own -> updatedExternal
            active != null -> active
            else -> updatedExternal ?: root
        }

        return UniversalWindowResolution(
            effectivePackageName = effective,
            lastExternalPackageName = updatedExternal,
        )
    }

    fun isOwnMainActivityEvent(
        eventPackageName: String?,
        eventClassName: String?,
        eventType: Int,
        ownPackageName: String,
        mainActivityClassName: String,
        windowStateChangedType: Int,
    ): Boolean {
        if (eventType != windowStateChangedType) return false
        val own = normalize(ownPackageName)
        if (normalize(eventPackageName) != own) return false
        val eventClass = normalize(eventClassName) ?: return false
        val mainClass = normalize(mainActivityClassName) ?: return false
        return eventClass == mainClass || eventClass.endsWith(".mainactivity")
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}
