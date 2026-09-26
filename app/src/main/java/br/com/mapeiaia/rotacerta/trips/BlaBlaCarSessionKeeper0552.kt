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
 *
 * 0.1.553 hardening rule: a positively validated UUID match authorizes an authenticated
 * operation. A login-required/profile-mismatch observation is sticky and cannot be cleared by
 * merely navigating away from a login URL. Network failures preserve storage, but never become
 * implicit proof of authentication.
 *
 * 0.1.555 correction: a page that simply does not expose any usable UUID is NOT evidence of
 * logout or profile mismatch. In that NOT_OBSERVABLE case only, a recent persisted proof for the
 * same authoritative UUID may be reused for the current isolated WebView session. Explicit login,
 * positive UUID conflict, stale proof, temporary restriction and missing proof remain fail-closed.
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

internal enum class BlaBlaIdentityEvidenceState0555 {
    MATCH,
    CONFLICT,
    NOT_OBSERVABLE,
}

internal data class BlaBlaSessionHealth0552(
    val state: BlaBlaSessionState0552,
    val expectedProfileUuid: String = "",
    val actualProfileUuid: String = "",
    val lastValidatedAtMillis: Long = 0L,
    val lossCause: BlaBlaSessionLossCause0552 = BlaBlaSessionLossCause0552.NONE,
    val explanation: String = "",
) {
    /**
     * Fail closed for any operation that requires a currently authenticated external session.
     * Read-only canonical projections may still render their last complete local snapshot while
     * the session is SUSPECTED/REVALIDATING/NETWORK_ERROR; that is a separate presentation rule.
     */
    val blocksAuthenticatedOperation: Boolean
        get() = state != BlaBlaSessionState0552.VALID
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
        val current = runtimeByAccount[account.id]
        if (current != null && current.state.isStickyFailure()) {
            record(
                context,
                "SESSION_ACQUIRE_BLOCKED",
                account,
                "operation=${safe(operation)} state=${current.state.name} awaitingPositiveIdentity=true",
            )
            return
        }
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
            return
        }

        val current = runtimeByAccount[account.id]
        if (current != null && current.state.isStickyFailure()) {
            record(
                context,
                "SESSION_REVALIDATE",
                account,
                "loginPage=false stickyState=${current.state.name} awaitingPositiveIdentity=true",
            )
            return
        }

        runtimeByAccount[account.id] = RuntimeObservation(
            state = BlaBlaSessionState0552.REVALIDATING,
            explanation = "Navegação preservada; aguardando confirmação do UUID.",
        )
        record(context, "SESSION_REVALIDATE", account, "loginPage=false awaitingPositiveIdentity=true")
    }

    fun observeIdentity(
        context: Context,
        account: BlaBlaDynamicAccount,
        actualProfileUuid: String?,
        operation: String,
    ): BlaBlaSessionHealth0552 {
        val expected = normalizeUuid(account.profileUuid)
        val actual = normalizeUuid(actualProfileUuid)
        val evidenceState = classifyIdentityEvidence0555(expected, actual)
        val current = runtimeByAccount[account.id]
        val sessionStore = BlaBlaDynamicSessionStore(context)
        val snapshot = sessionStore.read(account)
        val reusableNotObservableProof =
            evidenceState == BlaBlaIdentityEvidenceState0555.NOT_OBSERVABLE &&
                current?.state.isStickyFailure() != true &&
                canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                    account = account,
                    snapshot = snapshot,
                )

        val observation = when {
            evidenceState == BlaBlaIdentityEvidenceState0555.CONFLICT -> RuntimeObservation(
                state = BlaBlaSessionState0552.PROFILE_MISMATCH,
                actualProfileUuid = actual,
                cause = BlaBlaSessionLossCause0552.PROFILE_MISMATCH,
                explanation = "UUID encontrado pertence a outra sessão; operação bloqueada.",
            )
            evidenceState == BlaBlaIdentityEvidenceState0555.MATCH -> RuntimeObservation(
                state = BlaBlaSessionState0552.VALID,
                actualProfileUuid = actual,
                explanation = "UUID da sessão confirmado.",
            )
            current != null && current.state.isStickyFailure() -> current.copy(
                atMillis = System.currentTimeMillis(),
                explanation = "A sessão continua bloqueada até uma confirmação positiva do UUID esperado.",
            )
            reusableNotObservableProof -> RuntimeObservation(
                state = BlaBlaSessionState0552.VALID,
                actualProfileUuid = expected,
                explanation = "UUID não observável nesta página; prova recente do mesmo perfil isolado foi preservada.",
            )
            else -> RuntimeObservation(
                state = BlaBlaSessionState0552.SUSPECTED,
                actualProfileUuid = actual,
                explanation = if (expected.isBlank()) {
                    "A conta ainda não possui UUID autoritativo confirmado."
                } else {
                    "A página não comprovou identidade suficiente."
                },
            )
        }
        runtimeByAccount[account.id] = observation
        val event = when {
            evidenceState == BlaBlaIdentityEvidenceState0555.CONFLICT -> "SESSION_PROFILE_MISMATCH"
            evidenceState == BlaBlaIdentityEvidenceState0555.MATCH -> "SESSION_VALID"
            reusableNotObservableProof -> "SESSION_IDENTITY_NOT_OBSERVABLE_REUSED_0555"
            else -> "SESSION_SUSPECTED"
        }
        record(
            context,
            event,
            account,
            "operation=${safe(operation)} actualPresent=${actual.isNotBlank()} expectedPresent=${expected.isNotBlank()} evidence=${evidenceState.name} reusedRecentProof=$reusableNotObservableProof state=${observation.state.name}",
        )
        return health(account, snapshot)
    }

    fun observeNetworkError(context: Context, account: BlaBlaDynamicAccount, operation: String) {
        val current = runtimeByAccount[account.id]
        if (current != null && current.state.isStickyFailure()) {
            runtimeByAccount[account.id] = current.copy(atMillis = System.currentTimeMillis())
            record(
                context,
                "SESSION_SUSPECTED",
                account,
                "operation=${safe(operation)} cause=NETWORK_ERROR preservedState=${current.state.name} destructiveRecovery=false",
            )
            return
        }
        runtimeByAccount[account.id] = RuntimeObservation(
            state = BlaBlaSessionState0552.NETWORK_ERROR,
            explanation = "Falha de rede; cookies e armazenamento da sessão foram preservados.",
        )
        record(context, "SESSION_SUSPECTED", account, "operation=${safe(operation)} cause=NETWORK_ERROR destructiveRecovery=false")
    }

    fun requireValidated(
        context: Context,
        account: BlaBlaDynamicAccount,
        operation: String,
    ): BlaBlaSessionHealth0552 {
        val result = health(account, BlaBlaDynamicSessionStore(context).read(account))
        if (result.blocksAuthenticatedOperation) {
            record(
                context,
                "SESSION_OPERATION_BLOCKED",
                account,
                "operation=${safe(operation)} state=${result.state.name} cause=${result.lossCause.name}",
            )
        } else {
            record(context, "SESSION_REUSE", account, "operation=${safe(operation)} state=VALID")
        }
        return result
    }

    fun clearRuntime(accountId: String) {
        runtimeByAccount.remove(accountId)
    }

    internal fun classifyIdentityEvidence0555(
        expectedProfileUuid: String?,
        actualProfileUuid: String?,
    ): BlaBlaIdentityEvidenceState0555 {
        val expected = normalizeUuid(expectedProfileUuid)
        val actual = normalizeUuid(actualProfileUuid)
        return when {
            expected.isBlank() || actual.isBlank() -> BlaBlaIdentityEvidenceState0555.NOT_OBSERVABLE
            expected == actual -> BlaBlaIdentityEvidenceState0555.MATCH
            else -> BlaBlaIdentityEvidenceState0555.CONFLICT
        }
    }

    internal fun canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
        account: BlaBlaDynamicAccount,
        snapshot: BlaBlaDynamicSessionSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val expected = normalizeUuid(account.profileUuid)
        val snapshotUuid = normalizeUuid(snapshot?.profileUuid)
        val validatedAt = snapshot?.lastValidSyncAtMillis0426 ?: 0L
        val recentlyValidated =
            validatedAt > 0L &&
                nowMillis >= validatedAt &&
                nowMillis - validatedAt <= VALID_SESSION_DISPLAY_WINDOW_MILLIS
        return expected.isNotBlank() &&
            snapshot != null &&
            snapshot.identityVerified &&
            snapshotUuid == expected &&
            recentlyValidated &&
            !isExplicitLoginUrl(snapshot.lastUrl) &&
            snapshot.sourceAccessStatus0426 != BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED
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

    private fun BlaBlaSessionState0552?.isStickyFailure(): Boolean = this in setOf(
        BlaBlaSessionState0552.EXPIRED,
        BlaBlaSessionState0552.LOGIN_REQUIRED,
        BlaBlaSessionState0552.PROFILE_MISMATCH,
    )

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
