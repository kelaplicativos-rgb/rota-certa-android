package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val REMOTE_SUPPORT_ATTENTION_MARKER_0743 = "REMOTE_SUPPORT_ATTENTION_0743"

// Push notifications are optional for the remote collector: authenticated device
// polling is the primary transport. Still report a genuine unavailable listener
// when the driver explicitly enabled remote access.
internal fun shouldEscalateRemotePushFailure0769(
    remoteAccessEnabled: Boolean,
    pollingListenerRunning: Boolean,
): Boolean = remoteAccessEnabled && !pollingListenerRunning

// A successful authenticated poll may clear *only* transport fallback warnings.
// Never dismiss consent requests, authorization errors or collection failures.
internal fun shouldRecoverRemoteTransportAttention0769(
    status: String,
    reasonCode: String,
    remoteAccessEnabled: Boolean,
): Boolean = remoteAccessEnabled &&
    status == "ATTENTION" &&
    reasonCode in setOf(
        "FCM_TOKEN_FAILED",
        "FCM_TOKEN_UNAVAILABLE",
        "PUSH_REGISTER_FAILED",
        "PUSH_REGISTER_REJECTED",
        "PUSH_REGISTRATION_PENDING",
        "REMOTE_POLL_LISTENER_INACTIVE",
    )

internal data class RemoteSupportAttentionState0743(
    val tenantId: String = "",
    val status: String = "UNKNOWN",
    val reasonCode: String = "NOT_VERIFIED",
    val message: String = "Conexão remota ainda não confirmada.",
    val updatedAtMillis: Long = 0L,
    val needsAttention: Boolean = true,
) {
    val ready: Boolean get() = status == "READY" && !needsAttention
    val checking: Boolean get() = status == "CHECKING"
}

internal object RemoteSupportAttention0743 {
    private const val PREFS = "rota_certa_remote_support_attention_0743"
    private const val KEY_STATUS = "status"
    private const val KEY_REASON = "reason"
    private const val KEY_MESSAGE = "message"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_ATTENTION = "needs_attention"

    private val mutable = MutableStateFlow(RemoteSupportAttentionState0743())

    @Synchronized
    fun state(context: Context): StateFlow<RemoteSupportAttentionState0743> {
        val disk = read(context.applicationContext)
        if (mutable.value.tenantId != disk.tenantId || mutable.value.updatedAtMillis < disk.updatedAtMillis) {
            mutable.value = disk
        } else if (mutable.value.tenantId.isBlank()) {
            mutable.value = disk
        }
        return mutable.asStateFlow()
    }

    fun current(context: Context): RemoteSupportAttentionState0743 {
        state(context)
        return mutable.value
    }

    fun markChecking(
        context: Context,
        message: String = "Verificando registro do aparelho para consultas remotas…",
    ) {
        val previous = current(context)
        update(
            context = context,
            status = "CHECKING",
            reasonCode = previous.reasonCode,
            message = message,
            needsAttention = previous.needsAttention,
        )
    }

    fun markPending(
        context: Context,
        reasonCode: String,
        message: String,
    ) {
        update(
            context = context,
            status = "ATTENTION",
            reasonCode = reasonCode,
            message = message,
            needsAttention = true,
        )
    }

    fun markRequestPending(
        context: Context,
        message: String = "Solicitação remota recebida. Toque na notificação para abrir o suporte remoto.",
    ) {
        update(
            context = context,
            status = "REQUESTED",
            reasonCode = "REMOTE_REQUEST_RECEIVED",
            message = message,
            needsAttention = true,
        )
    }

    fun markReady(
        context: Context,
        message: String = "Conexão remota pronta. O aparelho pode receber consultas.",
    ) {
        update(
            context = context,
            status = "READY",
            reasonCode = "",
            message = message,
            needsAttention = false,
        )
    }

    @Synchronized
    private fun update(
        context: Context,
        status: String,
        reasonCode: String,
        message: String,
        needsAttention: Boolean,
    ) {
        val app = context.applicationContext
        val scope = RotaCertaTenantRegistry(app).activeScope()
        val now = System.currentTimeMillis()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(scope.key(KEY_STATUS), status.take(32))
            .putString(scope.key(KEY_REASON), reasonCode.take(80))
            .putString(scope.key(KEY_MESSAGE), message.take(320))
            .putLong(scope.key(KEY_UPDATED), now)
            .putBoolean(scope.key(KEY_ATTENTION), needsAttention)
            .apply()
        mutable.value = RemoteSupportAttentionState0743(
            tenantId = scope.tenantId,
            status = status.take(32),
            reasonCode = reasonCode.take(80),
            message = message.take(320),
            updatedAtMillis = now,
            needsAttention = needsAttention,
        )
    }

    private fun read(context: Context): RemoteSupportAttentionState0743 {
        val scope = RotaCertaTenantRegistry(context).activeScope()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = prefs.getString(scope.key(KEY_STATUS), "UNKNOWN").orEmpty()
        val hasPersisted = prefs.contains(scope.key(KEY_STATUS))
        return RemoteSupportAttentionState0743(
            tenantId = scope.tenantId,
            status = status.ifBlank { "UNKNOWN" },
            reasonCode = prefs.getString(scope.key(KEY_REASON), "NOT_VERIFIED").orEmpty(),
            message = prefs.getString(
                scope.key(KEY_MESSAGE),
                "Conexão remota ainda não confirmada.",
            ).orEmpty(),
            updatedAtMillis = prefs.getLong(scope.key(KEY_UPDATED), 0L),
            needsAttention = if (hasPersisted) {
                prefs.getBoolean(scope.key(KEY_ATTENTION), status != "READY")
            } else {
                true
            },
        )
    }
}
