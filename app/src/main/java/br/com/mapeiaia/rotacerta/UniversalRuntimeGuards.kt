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

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}

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
