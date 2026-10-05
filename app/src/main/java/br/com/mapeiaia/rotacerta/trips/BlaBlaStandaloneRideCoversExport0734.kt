package br.com.mapeiaia.rotacerta.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ContextThemeWrapper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.AppBuildInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Download-only BlaBlaCar "Suas viagens" cover collector.
 *
 * Isolation contract:
 * - reads only the authenticated /rides list of each already-configured isolated WebView profile;
 * - never opens trip details, passengers, seat controls, public searches or publication flows;
 * - never writes TripStore, Agenda, Timeline, canonical/public projections, capacity or "today" state;
 * - never calls BlaBlaCollectorStateStore.saveResponse or any synchronization callback;
 * - the only durable output is the user-requested JSON file in Downloads/Rota Certa.
 *
 * A transient per-WebView-profile lease is used only to prevent simultaneous browser access to
 * the same authenticated profile. It is released before this operation returns and is not a
 * business rule or a source of Agenda/Timeline state.
 */
@Serializable
internal data class BlaBlaStandaloneRideCoversIsolation0734(
    val downloadOnly: Boolean = true,
    val writesTimeline: Boolean = false,
    val writesAgenda: Boolean = false,
    val writesCanonicalTrips: Boolean = false,
    val writesAvailability: Boolean = false,
    val writesCapacity: Boolean = false,
    val writesTodayState: Boolean = false,
    val readsPassengers: Boolean = false,
    val opensTripDetails: Boolean = false,
)

@Serializable
internal data class BlaBlaStandaloneRideCover0734(
    val tripId: String = "",
    val administrativeHref: String = "",
    val dateIso: String = "",
    val dateText: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
)

@Serializable
internal data class BlaBlaStandaloneRideCoversProfile0734(
    val displayName: String,
    val profileUuid: String,
    val identityConfirmed: Boolean,
    val status: String,
    val observedCardCount: Int,
    val exportedCardCount: Int,
    val reachedEnd: Boolean,
    val stabilized: Boolean,
    val errorCode: String = "",
    val cards: List<BlaBlaStandaloneRideCover0734> = emptyList(),
)

@Serializable
internal data class BlaBlaStandaloneRideCoversPayload0734(
    val schemaVersion: String = SCHEMA_VERSION_0734,
    val kind: String = KIND_0734,
    val capturedAt: String,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val result: String,
    val totalProfiles: Int,
    val totalCards: Int,
    val isolation: BlaBlaStandaloneRideCoversIsolation0734 = BlaBlaStandaloneRideCoversIsolation0734(),
    val profiles: List<BlaBlaStandaloneRideCoversProfile0734>,
)

internal data class BlaBlaStandaloneRideCoversDownloadResult0734(
    val displayName: String,
    val relativeLocation: String,
    val bytes: Long,
    val result: String,
    val totalProfiles: Int,
    val totalCards: Int,
)

@Serializable
private data class StandaloneRideCoverRaw0734(
    val href: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val dateText: String = "",
)

@Serializable
private data class StandaloneRideListEnvelope0734(
    val covers: List<StandaloneRideCoverRaw0734> = emptyList(),
    val observedTripHrefs: List<String> = emptyList(),
    val observedCardCount: Int = 0,
    val explicitEmptyList: Boolean = false,
    val documentReady: Boolean = false,
    val loadingActive: Boolean = false,
    val endSentinelVisible: Boolean = false,
    val lastMutationAgeMs: Long = 0L,
    val scrollY: Int = 0,
    val scrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
)

@Serializable
private data class StandaloneProfileIdentityEnvelope0734(
    val documentReady: Boolean = false,
    val currentUrl: String = "",
    val profileUuids: List<String> = emptyList(),
)

private data class StandaloneProfileIdentityResult0734(
    val confirmed: Boolean = false,
    val errorCode: String = "",
)

private data class StandalonePageResult0734(
    val finalUrl: String = "",
    val sample: StandaloneRideListEnvelope0734? = null,
    val errorCode: String = "",
)

internal const val SCHEMA_VERSION_0734 = "rota-certa-blablacar-ride-covers-v1"
internal const val KIND_0734 = "BLABLACAR_RIDE_COVERS_STANDALONE"
internal const val RESULT_COMPLETE_0734 = "COMPLETE"
internal const val RESULT_PARTIAL_0734 = "PARTIAL"
private const val PROFILE_COMPLETE_0734 = "COMPLETE"
private const val PROFILE_PARTIAL_0734 = "PARTIAL"
private const val RIDES_URL_0734 = "https://www.blablacar.com.br/rides"
private const val PROFILE_URL_0734 = "https://www.blablacar.com.br/dashboard/profile/menu"
private const val IDENTITY_TIMEOUT_MS_0734 = 20_000L
private const val IDENTITY_MAX_PASSES_0734 = 24
private const val MAX_EXPORT_BYTES_0734 = 2 * 1024 * 1024
private const val PAGE_TIMEOUT_MS_0734 = 60_000L
private const val RETRY_MS_0734 = 500L
private const val INITIAL_SETTLE_MS_0734 = 650L
private const val REQUIRED_STABLE_PASSES_0734 = 4
private const val QUIET_MS_0734 = 750L
private const val MAX_EVALUATION_PASSES_0734 = 120

internal fun validateStandaloneRideCoversPayload0734(
    value: BlaBlaStandaloneRideCoversPayload0734,
): BlaBlaStandaloneRideCoversPayload0734 {
    require(value.schemaVersion == SCHEMA_VERSION_0734) { "Schema avulso não suportado" }
    require(value.kind == KIND_0734) { "Tipo de arquivo avulso não suportado" }
    require(value.capturedAt.isNotBlank()) { "Timestamp da coleta ausente" }
    require(value.sourceAppVersion.isNotBlank() && value.sourceVersionCode > 0) {
        "Identidade da build ausente"
    }
    require(value.totalProfiles == value.profiles.size) { "Quantidade de perfis inconsistente" }
    require(value.totalCards == value.profiles.sumOf { it.exportedCardCount }) {
        "Quantidade de capas inconsistente"
    }
    require(value.isolation.downloadOnly)
    require(!value.isolation.writesTimeline)
    require(!value.isolation.writesAgenda)
    require(!value.isolation.writesCanonicalTrips)
    require(!value.isolation.writesAvailability)
    require(!value.isolation.writesCapacity)
    require(!value.isolation.writesTodayState)
    require(!value.isolation.readsPassengers)
    require(!value.isolation.opensTripDetails)

    value.profiles.forEach { profile ->
        require(profile.exportedCardCount == profile.cards.size) { "Contagem de capas do perfil inconsistente" }
        if (profile.identityConfirmed) {
            require(normalizeStandaloneProfileUuid0734(profile.profileUuid) == profile.profileUuid) {
                "UUID do perfil não está canônico"
            }
        }
        if (profile.status == PROFILE_COMPLETE_0734) {
            require(profile.identityConfirmed) { "Perfil COMPLETE sem identidade confirmada" }
            require(profile.reachedEnd && profile.stabilized) { "Perfil COMPLETE sem prova de fim/estabilidade" }
            require(profile.observedCardCount == profile.exportedCardCount) {
                "Perfil COMPLETE não exportou todas as capas observadas"
            }
            require(profile.cards.all { it.tripId.isNotBlank() && it.dateIso.isNotBlank() }) {
                "Perfil COMPLETE contém capa sem tripId/data"
            }
        }
    }

    val expectedResult = if (value.profiles.isNotEmpty() &&
        value.profiles.all { it.status == PROFILE_COMPLETE_0734 }
    ) {
        RESULT_COMPLETE_0734
    } else {
        RESULT_PARTIAL_0734
    }
    require(value.result == expectedResult) { "Resultado global não corresponde aos perfis" }
    return value
}

internal fun encodeStandaloneRideCoversPayload0734(
    value: BlaBlaStandaloneRideCoversPayload0734,
): String {
    val verified = validateStandaloneRideCoversPayload0734(value)
    val raw = standaloneJson0734.encodeToString(BlaBlaStandaloneRideCoversPayload0734.serializer(), verified)
    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES_0734) {
        "Arquivo avulso excede o limite permitido"
    }
    return raw
}

internal fun decodeStandaloneRideCoversPayload0734(raw: String): BlaBlaStandaloneRideCoversPayload0734 {
    val bytes = raw.toByteArray(Charsets.UTF_8)
    require(bytes.isNotEmpty() && bytes.size <= MAX_EXPORT_BYTES_0734) {
        "Arquivo avulso vazio ou acima do limite permitido"
    }
    return validateStandaloneRideCoversPayload0734(
        standaloneJson0734.decodeFromString(BlaBlaStandaloneRideCoversPayload0734.serializer(), raw),
    )
}

private val standaloneJson0734 = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    prettyPrint = true
}

private val standaloneWireJson0734 = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun normalizeStandaloneProfileUuid0734(raw: String?): String? {
    val candidate = raw?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { UUID.fromString(candidate).toString() }
        .getOrNull()
        ?.takeIf { it == candidate }
}

internal object BlaBlaStandaloneRideCoversExport0734 {
    suspend fun download(
        context: Context,
        accounts: List<BlaBlaDynamicAccount>,
        onProgress: (String) -> Unit = {},
    ): BlaBlaStandaloneRideCoversDownloadResult0734 {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Download avulso requer Android 10 ou superior"
        }
        require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            "Perfis isolados do WebView não estão disponíveis neste aparelho"
        }
        require(accounts.isNotEmpty()) { "Nenhuma conta BlaBlaCar configurada" }

        val app = context.applicationContext
        val scripts = withContext(Dispatchers.IO) {
            val identity = app.assets.open("blablacar/scripts/standalone_profile_identity.js")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val covers = app.assets.open("blablacar/scripts/ride_covers_standalone.js")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            identity to covers
        }
        val identityScript = scripts.first
        val coverScript = scripts.second
        require(identityScript.isNotBlank()) { "Prova avulsa de identidade não está disponível" }
        require(coverScript.isNotBlank()) { "Leitor avulso de capas não está disponível" }

        val profiles = mutableListOf<BlaBlaStandaloneRideCoversProfile0734>()
        accounts.forEachIndexed { index, account ->
            onProgress(
                "Capas avulsas • perfil " + (index + 1) + "/" + accounts.size +
                    " • " + account.displayLabel,
            )
            profiles += collectProfile(
                context = app,
                account = account,
                identityScript = identityScript,
                coverScript = coverScript,
                onProgress = onProgress,
            )
        }

        val result = if (profiles.all { it.status == PROFILE_COMPLETE_0734 }) {
            RESULT_COMPLETE_0734
        } else {
            RESULT_PARTIAL_0734
        }
        val payload = validateStandaloneRideCoversPayload0734(
            BlaBlaStandaloneRideCoversPayload0734(
                capturedAt = Instant.now().toString(),
                sourceAppVersion = AppBuildInfo.versionName,
                sourceVersionCode = AppBuildInfo.versionCode,
                sourceCommitSha = AppBuildInfo.commit,
                sourceBranch = AppBuildInfo.branch,
                result = result,
                totalProfiles = profiles.size,
                totalCards = profiles.sumOf { it.exportedCardCount },
                profiles = profiles,
            ),
        )
        val raw = encodeStandaloneRideCoversPayload0734(payload)
        require(decodeStandaloneRideCoversPayload0734(raw) == payload) {
            "O Rota Certa não conseguiu reler o arquivo avulso gerado"
        }

        return writeDownload(app, raw, payload)
    }

    private suspend fun collectProfile(
        context: Context,
        account: BlaBlaDynamicAccount,
        identityScript: String,
        coverScript: String,
        onProgress: (String) -> Unit,
    ): BlaBlaStandaloneRideCoversProfile0734 {
        val definition = account.verifiedDefinition()
        val profileUuid = normalizeStandaloneProfileUuid0734(definition?.uuid)
        if (definition == null || profileUuid == null) {
            return failedProfile(account, profileUuid.orEmpty(), "PROFILE_UUID_NOT_CONFIRMED")
        }

        val sessionStore = BlaBlaDynamicSessionStore(context)
        val lease = acquire(sessionStore, account)
            ?: return failedProfile(account, profileUuid, "PROFILE_BROWSER_BUSY")

        val page = try {
            withContext(Dispatchers.Main.immediate) {
                val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
                val webView = WebView(themed)
                try {
                    configure(webView, account)
                    onProgress(account.displayLabel + " • confirmando UUID do perfil")
                    val identity0734 = verifyProfileIdentity(
                        webView = webView,
                        expectedProfileUuid = profileUuid,
                        script = identityScript,
                    )
                    if (!identity0734.confirmed) {
                        StandalonePageResult0734(errorCode = identity0734.errorCode)
                    } else {
                        loadStable(
                            webView = webView,
                            script = coverScript,
                            onProgress = { count ->
                                onProgress(account.displayLabel + " • " + count + " capa(s) encontradas")
                            },
                        )
                    }
                } finally {
                    runCatching { webView.stopLoading() }
                    runCatching { webView.webViewClient = WebViewClient() }
                    runCatching { webView.loadUrl("about:blank") }
                    runCatching { webView.clearHistory() }
                    runCatching { webView.removeAllViews() }
                    runCatching { webView.destroy() }
                }
            }
        } finally {
            sessionStore.releaseExternalFlight0426(lease)
        }

        val sample = page.sample
        if (page.errorCode.isNotBlank() || sample == null) {
            return failedProfile(
                account = account,
                profileUuid = profileUuid,
                errorCode = page.errorCode.ifBlank { "RIDES_COVERS_NOT_AVAILABLE" },
            )
        }
        if (!BlaBlaCollectorUrlModule.ridesPageMatches(page.finalUrl)) {
            return failedProfile(account, profileUuid, "SESSION_NOT_ON_RIDES_PAGE")
        }

        val cards = sample.covers.map { raw ->
            val tripId = BlaBlaCollectorUrlModule.tripId(raw.href).orEmpty().trim()
            val href = if (tripId.isNotBlank()) {
                BlaBlaCollectorUrlModule.canonical(raw.href).substringBefore('?').substringBefore('#').take(800)
            } else {
                ""
            }
            val dateIso = BlaBlaDomNormalizer.parseDate(raw.dateText)?.toString().orEmpty()
            BlaBlaStandaloneRideCover0734(
                tripId = tripId,
                administrativeHref = href,
                dateIso = dateIso,
                dateText = raw.dateText.trim().take(600),
                departureTime = raw.departureTime.trim().take(40),
                arrivalTime = raw.arrivalTime.trim().take(40),
                origin = raw.origin.trim().take(500),
                destination = raw.destination.trim().take(500),
                price = raw.price.trim().take(120),
            )
        }.distinctBy { cover ->
            cover.tripId.ifBlank { cover.administrativeHref + "|" + cover.dateText + "|" + cover.departureTime }
        }

        val identityComplete = cards.all { it.tripId.isNotBlank() && it.dateIso.isNotBlank() }
        val inventoryComplete =
            sample.observedCardCount == cards.size &&
                (sample.explicitEmptyList || sample.endSentinelVisible || sample.atBottom)
        val complete = identityComplete && inventoryComplete
        return BlaBlaStandaloneRideCoversProfile0734(
            displayName = account.displayLabel,
            profileUuid = profileUuid,
            identityConfirmed = true,
            status = if (complete) PROFILE_COMPLETE_0734 else PROFILE_PARTIAL_0734,
            observedCardCount = sample.observedCardCount,
            exportedCardCount = cards.size,
            reachedEnd = sample.explicitEmptyList || sample.endSentinelVisible || sample.atBottom,
            stabilized = true,
            errorCode = when {
                complete -> ""
                !inventoryComplete -> "COVER_INVENTORY_INCOMPLETE"
                !identityComplete -> "COVER_IDENTITY_OR_DATE_INCOMPLETE"
                else -> "COVER_EXPORT_PARTIAL"
            },
            cards = cards,
        )
    }

    private fun failedProfile(
        account: BlaBlaDynamicAccount,
        profileUuid: String,
        errorCode: String,
    ): BlaBlaStandaloneRideCoversProfile0734 =
        BlaBlaStandaloneRideCoversProfile0734(
            displayName = account.displayLabel,
            profileUuid = profileUuid,
            identityConfirmed = profileUuid.isNotBlank(),
            status = PROFILE_PARTIAL_0734,
            observedCardCount = 0,
            exportedCardCount = 0,
            reachedEnd = false,
            stabilized = false,
            errorCode = errorCode.take(120),
        )

    private fun configure(webView: WebView, account: BlaBlaDynamicAccount) {
        WebViewCompat.setProfile(webView, account.webProfileName)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.loadsImagesAutomatically = false
        webView.settings.blockNetworkImage = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    private suspend fun acquire(
        store: BlaBlaDynamicSessionStore,
        account: BlaBlaDynamicAccount,
    ): BlaBlaExternalFlightLease0426? {
        repeat(12) { attempt ->
            store.tryAcquireExternalFlight0426(
                account,
                "standalone-ride-covers-0734-" + account.id.take(18),
            )?.let { return it }
            if (attempt < 11) delay(200L)
        }
        return null
    }

    private suspend fun verifyProfileIdentity(
        webView: WebView,
        expectedProfileUuid: String,
        script: String,
    ): StandaloneProfileIdentityResult0734 =
        withTimeoutOrNull(IDENTITY_TIMEOUT_MS_0734) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var done = false
                var passes = 0

                fun finish(value: StandaloneProfileIdentityResult0734) {
                    if (done) return
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    if (continuation.isActive) continuation.resume(value)
                }

                fun evaluate() {
                    if (done) return
                    val currentUrl = webView.url.orEmpty()
                    if (
                        currentUrl.contains("/login", ignoreCase = true) ||
                        currentUrl.contains("/connect", ignoreCase = true)
                    ) {
                        finish(
                            StandaloneProfileIdentityResult0734(
                                errorCode = "PROFILE_SESSION_AUTH_REQUIRED",
                            ),
                        )
                        return
                    }
                    webView.evaluateJavascript(script) { raw ->
                        if (done) return@evaluateJavascript
                        val sample = decodeIdentityEnvelope(raw)
                        if (sample != null && sample.documentReady) {
                            val observed = sample.profileUuids
                                .mapNotNull(::normalizeStandaloneProfileUuid0734)
                                .toSet()
                            when {
                                expectedProfileUuid in observed -> {
                                    finish(StandaloneProfileIdentityResult0734(confirmed = true))
                                    return@evaluateJavascript
                                }
                                observed.isNotEmpty() -> {
                                    finish(
                                        StandaloneProfileIdentityResult0734(
                                            errorCode = "PROFILE_UUID_MISMATCH",
                                        ),
                                    )
                                    return@evaluateJavascript
                                }
                            }
                        }
                        passes++
                        if (passes >= IDENTITY_MAX_PASSES_0734) {
                            finish(
                                StandaloneProfileIdentityResult0734(
                                    errorCode = "PROFILE_UUID_NOT_OBSERVABLE",
                                ),
                            )
                        } else {
                            handler.postDelayed(::evaluate, RETRY_MS_0734)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            finish(
                                StandaloneProfileIdentityResult0734(
                                    errorCode = "PROFILE_NETWORK_ERROR_" + error.errorCode,
                                ),
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (done) return
                        if (
                            url.contains("/login", ignoreCase = true) ||
                            url.contains("/connect", ignoreCase = true)
                        ) {
                            finish(
                                StandaloneProfileIdentityResult0734(
                                    errorCode = "PROFILE_SESSION_AUTH_REQUIRED",
                                ),
                            )
                            return
                        }
                        handler.postDelayed(::evaluate, INITIAL_SETTLE_MS_0734)
                    }
                }

                continuation.invokeOnCancellation {
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { webView.stopLoading() }
                }
                webView.loadUrl(PROFILE_URL_0734)
            }
        } ?: StandaloneProfileIdentityResult0734(errorCode = "PROFILE_IDENTITY_TIMEOUT")

    private fun decodeIdentityEnvelope(raw: String?): StandaloneProfileIdentityEnvelope0734? {
        val value = raw?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
            ?: return null
        val payload = runCatching {
            if (value.firstOrNull() == '"') {
                standaloneWireJson0734.decodeFromString<JsonPrimitive>(value).content
            } else {
                value
            }
        }.getOrElse { return null }
        return runCatching {
            standaloneWireJson0734.decodeFromString<StandaloneProfileIdentityEnvelope0734>(payload)
        }.getOrNull()
    }

    private suspend fun loadStable(
        webView: WebView,
        script: String,
        onProgress: (Int) -> Unit,
    ): StandalonePageResult0734 =
        withTimeoutOrNull(PAGE_TIMEOUT_MS_0734) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var done = false
                var passes = 0
                var stablePasses = 0
                var lastFingerprint = ""

                fun finish(value: StandalonePageResult0734) {
                    if (done) return
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    if (continuation.isActive) continuation.resume(value)
                }

                fun evaluate() {
                    if (done) return
                    val finalUrl = webView.url.orEmpty()
                    if (!BlaBlaCollectorUrlModule.ridesPageMatches(finalUrl)) {
                        finish(
                            StandalonePageResult0734(
                                finalUrl = finalUrl,
                                errorCode = "SESSION_NOT_ON_RIDES_PAGE",
                            ),
                        )
                        return
                    }
                    webView.evaluateJavascript(script) { raw ->
                        if (done) return@evaluateJavascript
                        val sample = decodeEnvelope(raw)
                        if (sample == null) {
                            passes++
                            if (passes >= MAX_EVALUATION_PASSES_0734) {
                                finish(
                                    StandalonePageResult0734(
                                        finalUrl = finalUrl,
                                        errorCode = "COVER_SCRIPT_NOT_READABLE",
                                    ),
                                )
                            } else {
                                handler.postDelayed(::evaluate, RETRY_MS_0734)
                            }
                            return@evaluateJavascript
                        }

                        onProgress(sample.observedCardCount)
                        val terminalEvidence =
                            sample.explicitEmptyList || sample.endSentinelVisible || sample.atBottom
                        val materialized =
                            sample.documentReady &&
                                !sample.loadingActive &&
                                sample.lastMutationAgeMs >= QUIET_MS_0734 &&
                                sample.covers.size == sample.observedCardCount
                        val fingerprint = sample.observedTripHrefs.sorted().joinToString("\n")
                        stablePasses = if (
                            terminalEvidence &&
                            materialized &&
                            fingerprint == lastFingerprint
                        ) {
                            stablePasses + 1
                        } else if (terminalEvidence && materialized) {
                            1
                        } else {
                            0
                        }
                        lastFingerprint = fingerprint

                        if (stablePasses >= REQUIRED_STABLE_PASSES_0734) {
                            finish(
                                StandalonePageResult0734(
                                    finalUrl = finalUrl,
                                    sample = sample,
                                ),
                            )
                            return@evaluateJavascript
                        }

                        if (sample.endSentinelVisible || sample.explicitEmptyList) {
                            passes++
                            if (passes >= MAX_EVALUATION_PASSES_0734) {
                                finish(
                                    StandalonePageResult0734(
                                        finalUrl = finalUrl,
                                        errorCode = "COVER_LIST_NOT_STABLE",
                                    ),
                                )
                            } else {
                                handler.postDelayed(::evaluate, RETRY_MS_0734)
                            }
                            return@evaluateJavascript
                        }

                        passes++
                        if (passes >= MAX_EVALUATION_PASSES_0734) {
                            finish(
                                StandalonePageResult0734(
                                    finalUrl = finalUrl,
                                    errorCode = "COVER_LIST_NOT_STABLE",
                                ),
                            )
                            return@evaluateJavascript
                        }

                        val scrollScript = if (
                            sample.atBottom &&
                            !sample.explicitEmptyList &&
                            !sample.endSentinelVisible
                        ) {
                            "(function(){try{" +
                                "var root=window.__rotaCertaStandaloneRideCoversScrollRoot0734||document.scrollingElement||document.documentElement||document.body;" +
                                "var doc=document.scrollingElement||document.documentElement||document.body;" +
                                "var isDoc=!root||root===doc||root===document.documentElement||root===document.body;" +
                                "var h=isDoc?Math.max(document.documentElement.clientHeight||0,window.innerHeight||0,600):Math.max(root.clientHeight||0,600);" +
                                "var dy=-Math.max(240,Math.floor(h*0.65));" +
                                "if(isDoc){window.scrollBy(0,dy);}else if(root.scrollBy){root.scrollBy(0,dy);}else{root.scrollTop=Math.max(0,(root.scrollTop||0)+dy);}" +
                                "setTimeout(function(){if(isDoc){window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}else if(root.scrollTo){root.scrollTo(0,root.scrollHeight||0);}else{root.scrollTop=root.scrollHeight||0;}},160);" +
                                "}catch(_){ } return true;})();"
                        } else {
                            "(function(){try{" +
                                "var root=window.__rotaCertaStandaloneRideCoversScrollRoot0734||document.scrollingElement||document.documentElement||document.body;" +
                                "var doc=document.scrollingElement||document.documentElement||document.body;" +
                                "var isDoc=!root||root===doc||root===document.documentElement||root===document.body;" +
                                "if(isDoc){window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}" +
                                "else if(root.scrollTo){root.scrollTo(0,root.scrollHeight||0);}else{root.scrollTop=root.scrollHeight||0;}" +
                                "}catch(_){ } return true;})();"
                        }
                        webView.evaluateJavascript(scrollScript) {
                            handler.postDelayed(::evaluate, RETRY_MS_0734)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            finish(
                                StandalonePageResult0734(
                                    finalUrl = view.url.orEmpty(),
                                    errorCode = "NETWORK_ERROR_" + error.errorCode,
                                ),
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (done) return
                        if (!BlaBlaCollectorUrlModule.ridesPageMatches(url)) {
                            finish(
                                StandalonePageResult0734(
                                    finalUrl = url,
                                    errorCode = "SESSION_NOT_ON_RIDES_PAGE",
                                ),
                            )
                            return
                        }
                        handler.postDelayed(::evaluate, INITIAL_SETTLE_MS_0734)
                    }
                }

                continuation.invokeOnCancellation {
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { webView.stopLoading() }
                }
                webView.loadUrl(RIDES_URL_0734)
            }
        } ?: StandalonePageResult0734(errorCode = "COVER_COLLECTION_TIMEOUT")

    private fun decodeEnvelope(raw: String?): StandaloneRideListEnvelope0734? {
        val value = raw?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
            ?: return null
        val payload = runCatching {
            if (value.firstOrNull() == '"') {
                standaloneWireJson0734.decodeFromString<JsonPrimitive>(value).content
            } else {
                value
            }
        }.getOrElse { return null }
        return runCatching {
            standaloneWireJson0734.decodeFromString<StandaloneRideListEnvelope0734>(payload)
        }.getOrNull()
    }

    private suspend fun writeDownload(
        context: Context,
        raw: String,
        payload: BlaBlaStandaloneRideCoversPayload0734,
    ): BlaBlaStandaloneRideCoversDownloadResult0734 = withContext(Dispatchers.IO) {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val displayName = "rota-certa-capas-avulsas-" + stamp + ".json"
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Rota Certa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android não disponibilizou um destino em Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Não foi possível abrir o arquivo avulso em Downloads")

            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }.also { ready -> resolver.update(uri, ready, null, null) }

            BlaBlaStandaloneRideCoversDownloadResult0734(
                displayName = displayName,
                relativeLocation = relativePath + "/" + displayName,
                bytes = bytes.size.toLong(),
                result = payload.result,
                totalProfiles = payload.totalProfiles,
                totalCards = payload.totalCards,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
