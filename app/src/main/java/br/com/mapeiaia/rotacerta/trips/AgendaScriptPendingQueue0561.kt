package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.SharedPreferences
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import org.json.JSONArray
import org.json.JSONObject

internal enum class AgendaScriptCancelDisposition0561 {
    KEEP_ACTIVE_EXTERNAL_IN_FLIGHT,
    DEFER_RECONCILIATION,
    ARCHIVE_TERMINAL,
}

internal object AgendaScriptPendingPolicy0561 {
    private val reconcileStates = setOf(
        AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
        AgendaTripScriptState0558.RECONCILING,
        AgendaTripScriptState0558.CONFIRMED,
        AgendaTripScriptState0558.CANONICALIZED,
    )

    fun cancellationDisposition(execution: AgendaTripExecution0558): AgendaScriptCancelDisposition0561 {
        if (execution.items.any { it.state == AgendaTripScriptState0558.CREATING }) {
            return AgendaScriptCancelDisposition0561.KEEP_ACTIVE_EXTERNAL_IN_FLIGHT
        }
        if (execution.items.any { it.state in reconcileStates }) {
            return AgendaScriptCancelDisposition0561.DEFER_RECONCILIATION
        }
        return AgendaScriptCancelDisposition0561.ARCHIVE_TERMINAL
    }

    fun isReconcileState(state: AgendaTripScriptState0558): Boolean = state in reconcileStates
}

internal data class AgendaScriptPendingRelease0561(
    val released: Boolean,
    val deferredForReconciliation: Boolean,
    val pendingCount: Int,
    val message: String,
)

/**
 * Keeps ambiguous external publications durable without monopolizing the single active execution slot.
 *
 * A cancelled execution that may already have created something externally is moved to a dedicated
 * reconciliation queue. It is never republished from that queue. New unrelated scripts may execute,
 * while the exact same script/fingerprint remains blocked until reconciliation resolves the ambiguity.
 */
internal object AgendaScriptPendingQueue0561 {
    private const val PREFS = "rota_certa_agenda_trip_script_executor_0558"
    private const val KEY_ACTIVE = "active"
    private const val KEY_HISTORY = "history"
    private const val KEY_PENDING = "pending_reconciliation_0561"
    private const val MAX_PENDING = 30
    private const val MAX_HISTORY = 20
    private val lock = Any()

    fun migrateCancelledActiveIfSafe(context: Context): AgendaScriptPendingRelease0561? = synchronized(lock) {
        val storage = storage(context)
        val raw = storage.prefs.getString(storage.activeKey, null) ?: return@synchronized null
        val execution = decodeExecution(raw) ?: return@synchronized null
        if (!execution.cancelled) return@synchronized null
        releaseRaw(storage, raw, execution)
    }

    fun releaseCancelledActive(context: Context): AgendaScriptPendingRelease0561 = synchronized(lock) {
        val storage = storage(context)
        val raw = storage.prefs.getString(storage.activeKey, null)
            ?: return@synchronized AgendaScriptPendingRelease0561(false, false, pendingCount(storage), "Nenhuma execução ativa para liberar.")
        val execution = decodeExecution(raw)
            ?: return@synchronized AgendaScriptPendingRelease0561(false, false, pendingCount(storage), "A execução ativa não pôde ser lida; o estado foi preservado.")
        if (!execution.cancelled) {
            return@synchronized AgendaScriptPendingRelease0561(false, false, pendingCount(storage), "A execução ainda não está cancelada; o estado foi preservado.")
        }
        releaseRaw(storage, raw, execution)
    }

    fun pendingCount(context: Context): Int = synchronized(lock) { pendingCount(storage(context)) }

    fun containsScriptHash(context: Context, scriptHash: String): Boolean = synchronized(lock) {
        pendingObjects(storage(context)).any { it.optString("scriptHash") == scriptHash }
    }

    fun containsAnyFingerprint(context: Context, fingerprints: Collection<String>): Boolean = synchronized(lock) {
        if (fingerprints.isEmpty()) return@synchronized false
        val wanted = fingerprints.toHashSet()
        pendingObjects(storage(context)).any pendingExecution@{ execution ->
            val items = execution.optJSONArray("items") ?: return@pendingExecution false
            (0 until items.length()).any pendingItem@{ index ->
                val item = items.optJSONObject(index) ?: return@pendingItem false
                val state = runCatching { AgendaTripScriptState0558.valueOf(item.optString("state")) }.getOrNull()
                state != null && AgendaScriptPendingPolicy0561.isReconcileState(state) && item.optString("fingerprint") in wanted
            }
        }
    }

    fun reconcilePending(context: Context): String = synchronized(lock) {
        val storage = storage(context)
        val objects = pendingObjects(storage)
        if (objects.isEmpty()) return@synchronized "Não existe publicação ambígua aguardando reconciliação."

        val store = AgendaTripScriptStore0558(context)
        val stillPending = mutableListOf<JSONObject>()
        val resolvedForHistory = mutableListOf<JSONObject>()
        var resolvedExecutions = 0
        var syncedItems = 0
        var unresolvedItems = 0

        objects.forEach { rawObject ->
            val execution = decodeExecution(rawObject.toString())
            if (execution == null) {
                stillPending += rawObject
                unresolvedItems += 1
                return@forEach
            }
            val reconciledItems = execution.items.map { item ->
                if (!AgendaScriptPendingPolicy0561.isReconcileState(item.state)) return@map item
                val reconciled = AgendaTripScriptPlanner0558.reconcile(context, item.copy(state = AgendaTripScriptState0558.RECONCILING))
                if (reconciled.state == AgendaTripScriptState0558.SYNCED) {
                    store.markCompleted(reconciled.fingerprint)
                    syncedItems += 1
                } else {
                    unresolvedItems += 1
                }
                reconciled
            }
            val next = execution.copy(updatedAtMillis = System.currentTimeMillis(), items = reconciledItems)
            if (next.items.any { AgendaScriptPendingPolicy0561.isReconcileState(it.state) }) {
                stillPending += encodeExecution(next)
            } else {
                resolvedExecutions += 1
                resolvedForHistory += encodeExecution(next)
            }
        }

        val existingHistory = historyObjects(storage)
        val nextHistory = (resolvedForHistory + existingHistory).take(MAX_HISTORY)
        val editor = storage.prefs.edit()
            .putString(storage.pendingKey, toArray(stillPending).toString())
            .putString(storage.historyKey, toArray(nextHistory).toString())
        require(editor.commit()) { "Falha ao persistir reconciliação pendente." }

        val remaining = stillPending.size
        buildString {
            append("Reconciliação concluída. ")
            append("Execuções resolvidas: $resolvedExecutions. ")
            append("Itens sincronizados: $syncedItems. ")
            append("Pendências restantes: $remaining")
            if (unresolvedItems > 0) append(" — nenhuma pendência restante será republicada automaticamente.")
        }
    }

    private fun releaseRaw(storage: Storage, raw: String, execution: AgendaTripExecution0558): AgendaScriptPendingRelease0561 {
        return when (AgendaScriptPendingPolicy0561.cancellationDisposition(execution)) {
            AgendaScriptCancelDisposition0561.KEEP_ACTIVE_EXTERNAL_IN_FLIGHT -> {
                AgendaScriptPendingRelease0561(
                    released = false,
                    deferredForReconciliation = false,
                    pendingCount = pendingCount(storage),
                    message = "Há uma ação externa ainda em andamento. O cancelamento impede novas instruções, mas o slot ativo só será liberado após o retorno dessa ação.",
                )
            }
            AgendaScriptCancelDisposition0561.DEFER_RECONCILIATION -> {
                val objectRaw = JSONObject(raw)
                val pending = pendingObjects(storage).toMutableList()
                val executionId = objectRaw.optString("executionId")
                pending.removeAll { it.optString("executionId") == executionId }
                pending.add(0, objectRaw)
                val nextPending = pending.take(MAX_PENDING)
                require(
                    storage.prefs.edit()
                        .putString(storage.pendingKey, toArray(nextPending).toString())
                        .remove(storage.activeKey)
                        .commit(),
                ) { "Falha ao mover execução para reconciliação pendente." }
                AgendaScriptPendingRelease0561(
                    released = true,
                    deferredForReconciliation = true,
                    pendingCount = nextPending.size,
                    message = "Execução cancelada liberou o Executor. A publicação ambígua foi preservada separadamente para reconciliação e não será republicada.",
                )
            }
            AgendaScriptCancelDisposition0561.ARCHIVE_TERMINAL -> {
                val nextHistory = (listOf(JSONObject(raw)) + historyObjects(storage)).take(MAX_HISTORY)
                require(
                    storage.prefs.edit()
                        .putString(storage.historyKey, toArray(nextHistory).toString())
                        .remove(storage.activeKey)
                        .commit(),
                ) { "Falha ao arquivar execução cancelada." }
                AgendaScriptPendingRelease0561(
                    released = true,
                    deferredForReconciliation = false,
                    pendingCount = pendingCount(storage),
                    message = "Execução cancelada e arquivada. O Executor está livre para um novo JSON.",
                )
            }
        }
    }

    private data class Storage(
        val prefs: SharedPreferences,
        val activeKey: String,
        val historyKey: String,
        val pendingKey: String,
    )

    private fun storage(context: Context): Storage {
        val app = context.applicationContext
        val scope = RotaCertaTenantRegistry(app).activeScope()
        return Storage(
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            activeKey = scope.key(KEY_ACTIVE),
            historyKey = scope.key(KEY_HISTORY),
            pendingKey = scope.key(KEY_PENDING),
        )
    }

    private fun pendingCount(storage: Storage): Int = pendingObjects(storage).size

    private fun pendingObjects(storage: Storage): List<JSONObject> = runCatching {
        val array = JSONArray(storage.prefs.getString(storage.pendingKey, "[]"))
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun historyObjects(storage: Storage): List<JSONObject> = runCatching {
        val array = JSONArray(storage.prefs.getString(storage.historyKey, "[]"))
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun toArray(objects: List<JSONObject>): JSONArray = JSONArray().also { array -> objects.forEach(array::put) }

    private fun encodeInstruction(v: AgendaTripInstruction0558) = JSONObject()
        .put("index", v.index).put("profileUuid", v.profileUuid).put("date", v.date).put("departureTime", v.departureTime)
        .put("origin", v.origin).put("destination", v.destination).put("seats", v.seats).put("publish", v.publish)
        .put("priceText", v.priceText).put("automaticReservation", v.automaticReservation).put("comment", v.comment).put("direction", v.direction.name)

    private fun decodeInstruction(o: JSONObject) = AgendaTripInstruction0558(
        o.optInt("index"), o.optString("profileUuid"), o.optString("date"), o.optString("departureTime"), o.optString("origin"), o.optString("destination"),
        o.optInt("seats"), o.optBoolean("publish", true), o.optString("priceText"), o.optBoolean("automaticReservation"), o.optString("comment"),
        runCatching { AgendaTripScriptDirection0558.valueOf(o.optString("direction")) }.getOrDefault(AgendaTripScriptDirection0558.IDA),
    )

    private fun encodeExecution(e: AgendaTripExecution0558): JSONObject {
        val items = JSONArray()
        e.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("instruction", encodeInstruction(item.instruction))
                    .put("fingerprint", item.fingerprint)
                    .put("state", item.state.name)
                    .put("accountId", item.accountId)
                    .put("attempts", item.attempts)
                    .put("externalTripId", item.externalTripId)
                    .put("canonicalTripId", item.canonicalTripId)
                    .put("lastError", item.lastError)
                    .put("updatedAtMillis", item.updatedAtMillis),
            )
        }
        return JSONObject()
            .put("executionId", e.executionId)
            .put("scriptId", e.scriptId)
            .put("scriptHash", e.scriptHash)
            .put("startedAtMillis", e.startedAtMillis)
            .put("updatedAtMillis", e.updatedAtMillis)
            .put("cancelled", e.cancelled)
            .put("rawScript", e.rawScript)
            .put("items", items)
    }

    private fun decodeExecution(raw: String): AgendaTripExecution0558? = runCatching {
        val o = JSONObject(raw)
        val a = o.getJSONArray("items")
        val items = (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            AgendaTripExecutionItem0558(
                instruction = decodeInstruction(x.getJSONObject("instruction")),
                fingerprint = x.getString("fingerprint"),
                state = AgendaTripScriptState0558.valueOf(x.getString("state")),
                accountId = x.optString("accountId"),
                attempts = x.optInt("attempts"),
                externalTripId = x.optString("externalTripId"),
                canonicalTripId = x.optString("canonicalTripId"),
                lastError = x.optString("lastError"),
                updatedAtMillis = x.optLong("updatedAtMillis"),
            )
        }
        AgendaTripExecution0558(
            executionId = o.getString("executionId"),
            scriptId = o.getString("scriptId"),
            scriptHash = o.getString("scriptHash"),
            startedAtMillis = o.optLong("startedAtMillis"),
            updatedAtMillis = o.optLong("updatedAtMillis"),
            cancelled = o.optBoolean("cancelled"),
            rawScript = o.optString("rawScript"),
            items = items,
        )
    }.getOrNull()
}
