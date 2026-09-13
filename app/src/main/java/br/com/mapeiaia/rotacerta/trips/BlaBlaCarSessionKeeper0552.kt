package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Central session-health facade for BlaBlaCar accounts.
 *
 * It deliberately does not own cookies, credentials or WebView storage. Those remain in the
 * persistent Android WebView profile identified by [BlaBlaDynamicAccount.webProfileName].
 * Canonical account identity remains the confirmed external UUID. This class only projects and
 * records the health of that existing session and delegates persistence of observed navigation
 * to [BlaBlaDynamicSessionStore].
 */
internal enum class BlaBlaSessionState0552 {
    VALID,
    SUSPECTED,
    REVALIDATING,
    EXPIRED,
    LOGIN_REQUIRED,
    PROFILE_MISMATCH,
    NETWORK_ERROR,
}

internal enum class BlaBlaSessionLossCause0552 {
    NONE,
    SERVER_SESSION_EXPIRED,
    LOCAL_STORAGE_LOST,
    COOKIE_LOST,
    PROFILE_RECREATED,
    PROFILE_MISMATCH,
    APP_UPDATE_MIGRATION,
    MANUAL_LOGOUT,
    UNKNOWN,
}

internal data class BlaBlaSessionHealth0552(
    val state: BlaBlaSessionState0552,
    val expectedProfileUuid: String = "",
    val actualProfileUuid: String = "",
    val lastValidatedAtMillis: Long = 0L,
    val lossCause: BlaBlaSessionLossCause0552 = BlaBlaSessionLossCause0552.NONE,
    val explanation: String = "",
) {
    val blocksAuthenticatedOperation: Boolean
        get() = state in setOf(
            BlaBlaSessionState0552.EXPIRED,
            BlaBlaSessionState0552.LOGIN_REQUIRED,
            BlaBlaSessionState0552.PROFILE_MISMATCH,
        )
}

internal object BlaBlaCarSessionKeeper0552 {
    private data class RuntimeObservation(
        val state: BlaBlaSessionState0552,
        val actualProfileUuid: String = "",
        val atMillis: Long = System.currentTimeMillis(),
        val cause: BlaBlaSessionLossCause0552 = BlaBlaSessionLossCause0552.NONE,
        val explanation: String = "",
    )

    private val runtimeByAccount = ConcurrentHashMap<String, RuntimeObservation>()

    fun health(
        account: BlaBlaDynamicAccount,
        snapshot: BlaBlaDynamicSessionSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
    ): BlaBlaSessionHealth0552 {
        val expected = normalizeUuid(account.profileUuid)
        val snapshotUuid = normalizeUuid(snapshot?.profileUuid)
        val runtime = runtimeByAccount[account.id]
            ?.takeIf { nowMillis >= it.atMillis && nowMillis - it.atMillis <= RUNTIME_OBSERVATION_TTL_MILLIS }

        if (expected.isNotBlank() && snapshotUuid.isNotBlank() && expected != snapshotUuid) {
            return BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.PROFILE_MISMATCH,
                expectedProfileUuid = expected,
                actualProfileUuid = snapshotUuid,
                lastValidatedAtMillis = snapshot?.lastValidSyncAtMillis0426 ?: 0L,
                lossCause = BlaBlaSessionLossCause0552.PROFILE_MISMATCH,
                explanation = "UUID da sessão não corresponde à conta solicitada.",
            )
        }

        if (runtime != null) {
            return BlaBlaSessionHealth0552(
                state = runtime.state,
                expectedProfileUuid = expected,
                actualProfileUuid = runtime.actualProfileUuid,
                lastValidatedAtMillis = if (runtime.state == BlaBlaSessionState0552.VALID) runtime.atMillis else snapshot?.lastValidSyncAtMillis0426 ?: 0L,
                lossCause = runtime.cause,
                explanation = runtime.explanation,
            )
        }

        if (snapshot == null) {
            return BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.LOGIN_REQUIRED,
                expectedProfileUuid = expected,
                lossCause = BlaBlaSessionLossCause0552.UNKNOWN,
                explanation = "Esta conta ainda não possui sessão validada neste aparelho.",
            )
        }

        if (isExplicitLoginUrl(snapshot.lastUrl)) {
            return BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.LOGIN_REQUIRED,
                expectedProfileUuid = expected,
                actualProfileUuid = snapshotUuid,
                lastValidatedAtMillis = snapshot.lastValidSyncAtMillis0426,
                lossCause = BlaBlaSessionLossCause0552.SERVER_SESSION_EXPIRED,
                explanation = "A navegação atual exige autenticação novamente.",
            )
        }

        if (snapshot.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED) {
            return BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.SUSPECTED,
                expectedProfileUuid = expected,
                actualProfileUuid = snapshotUuid,
                lastValidatedAtMillis = snapshot.lastValidSyncAtMillis0426,
                explanation = "Sessão preservada; a origem está temporariamente indisponível e não será tratada como logout.",
            )
        }

        val exactIdentity = expected.isNotBlank() && snapshotUuid == expected && snapshot.identityVerified
        val validatedAt = snapshot.lastValidSyncAtMillis0426
        val recentlyValidated = validatedAt > 0L && nowMillis >= validatedAt && nowMillis - validatedAt <= VALID_SESSION_DISPLAY_WINDOW_MILLIS
        return if (exactIdentity && recentlyValidated) {
            BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.VALID,
                expectedProfileUuid = expected,
                actualProfileUuid = snapshotUuid,
                lastValidatedAtMillis = validatedAt,
                explanation = "UUID e sessão foram validados recentemente.",
            )
        } else {
            BlaBlaSessionHealth0552(
                state = BlaBlaSessionState0552.SUSPECTED,
                expectedProfileUuid = expected,
                actualProfileUuid = snapshotUuid,
                lastValidatedAtMillis = validatedAt,
                explanation = if (exactIdentity) {
                    "Sessão persistente preservada; revalidação silenciosa necessária antes de afirmar que está conectada."
                } else {
                    "Sessão persistente existe, mas a identidade ainda precisa ser validada."
                },
            )
        }
    }

    fun observeAcquire(context: Context, account: BlaBlaDynamicAccount, operation: String) {
        runtimeByAccount[account.id] = RuntimeObservation(
            state = BlaBlaSessionState0552.REVALIDATING,
            explanation = "Validando a sessão antes de $operation.",
        )
        record(context, "SESSION_ACQUIRE", account, "operation=${safe(operation)}")
    }

    fun observeNavigation(context: Context, account: BlaBlaDynamicAccount, rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        if (url.isBlank()) return
        BlaBlaDynamicSessionStore(context).markSeen(account, url)
        if (isExplicitLoginUrl(url)) {
            runtimeByAccount[account.id] = RuntimeObservation(
                state = BlaBlaSessionState0552.LOGIN_REQUIRED,
                cause = BlaBlaSessionLossCause0552.SERVER_SESSION_EXPIRED,
                explanation = "BlaBlaCar apresentou uma tela explícita de autenticação.",
            )
            record(context, "SESSION_LOGIN_REQUIRED", account, "cause=SERVER_SESSION_EXPIRED")
        } else {
            runtimeByAccount[account.id] = RuntimeObservation(
                state = BlaBlaSessionState0552.REVALIDATING,
                explanation = "Navegação preservada; aguardando confirmação do UUID.",
            )
            record(context, "SESSION_REVALIDATE", account, "loginPage=false")
        }
    }

    fun observeIdentity(
        context: Context,
        account: BlaBlaDynamicAccount,
        actualProfileUuid: String?,
        operation: String,
    ): BlaBlaSessionHealth0552 {
        val expected = normalizeUuid(account.profileUuid)
        val actual = normalizeUuid(actualProfileUuid)
        val mismatch = expected.isNotBlank() && actual.isNotBlank() && expected != actual
        val observation = when {
            mismatch -> RuntimeObservation(
                state = BlaBlaSessionState0552.PROFILE_MISMATCH,
                actualProfileUuid = actual,
                cause = BlaBlaSessionLossCause0552.PROFILE_MISMATCH,
                explanation = "UUID encontrado pertence a outra sessão; operação bloqueada.",
            )
            actual.isBlank() -> RuntimeObservation(
                state = BlaBlaSessionState0552.SUSPECTED,
                explanation = "A página não comprovou identidade suficiente.",
            )
            else -> RuntimeObservation(
                state = BlaBlaSessionState0552.VALID,
                actualProfileUuid = actual,
                explanation = "UUID da sessão confirmado.",
            )
        }
        runtimeByAccount[account.id] = observation
        record(
            context,
            if (mismatch) "SESSION_PROFILE_MISMATCH" else if (actual.isNotBlank()) "SESSION_VALID" else "SESSION_SUSPECTED",
            account,
            "operation=${safe(operation)} actualPresent=${actual.isNotBlank()} match=${!mismatch}",
        )
        return health(account, BlaBlaDynamicSessionStore(context).read(account))
    }

    fun observeNetworkError(context: Context, account: BlaBlaDynamicAccount, operation: String) {
        runtimeByAccount[account.id] = RuntimeObservation(
            state = BlaBlaSessionState0552.NETWORK_ERROR,
            explanation = "Falha de rede; cookies e armazenamento da sessão foram preservados.",
        )
        record(context, "SESSION_SUSPECTED", account, "operation=${safe(operation)} cause=NETWORK_ERROR destructiveRecovery=false")
    }

    fun clearRuntime(accountId: String) {
        runtimeByAccount.remove(accountId)
    }

    internal fun isExplicitLoginUrl(raw: String?): Boolean {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        if (host != "blablacar.com.br" && !host.endsWith(".blablacar.com.br") && host != "blablacar.com" && !host.endsWith(".blablacar.com")) {
            return false
        }
        val path = uri.path.orEmpty().lowercase().trimEnd('/')
        return path == "/login" ||
            path.endsWith("/login") ||
            path.endsWith("/signin") ||
            path.endsWith("/sign-in") ||
            path.contains("/auth/login") ||
            path.contains("/authentication/login")
    }

    private fun normalizeUuid(value: String?): String = value?.trim()?.lowercase().orEmpty()

    private fun record(context: Context, event: String, account: BlaBlaDynamicAccount, details: String) {
        UnifiedDebugEventStore.record(
            event,
            context.packageName,
            "accountKey=${seatSyncDiagnosticKey(account.profileUuid ?: account.id)} $details piiLogged=false credentialsLogged=false",
        )
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_.:-]+"), "_").take(80)

    private const val VALID_SESSION_DISPLAY_WINDOW_MILLIS = 30L * 60L * 1000L
    private const val RUNTIME_OBSERVATION_TTL_MILLIS = 2L * 60L * 60L * 1000L
}
