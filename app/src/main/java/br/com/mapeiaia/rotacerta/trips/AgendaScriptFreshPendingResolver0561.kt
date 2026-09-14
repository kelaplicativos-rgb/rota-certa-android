package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import org.json.JSONArray
import org.json.JSONObject

internal enum class AgendaScriptPublisherReturn0561 {
    FAILED_BEFORE_SUBMIT,
    NEEDS_RECONCILIATION,
}

internal object AgendaScriptPublisherReturnPolicy0561 {
    fun classify(resultOk: Boolean, submitAttempted: Boolean): AgendaScriptPublisherReturn0561 =
        if (!resultOk && !submitAttempted) AgendaScriptPublisherReturn0561.FAILED_BEFORE_SUBMIT
        else AgendaScriptPublisherReturn0561.NEEDS_RECONCILIATION
}

internal object AgendaScriptFreshMissingPolicy0561 {
    fun canRetryAfterFreshMissing(
        state: AgendaTripScriptState0558,
        profileUuid: String,
        completeProfileUuids: Set<String>,
        freshGeneration: Long,
    ): Boolean =
        freshGeneration > 0L &&
            state == AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS &&
            profileUuid.trim().lowercase() in completeProfileUuids.map(String::lowercase).toSet()
}

internal data class AgendaScriptFreshReconcileResult0561(
    val resolvedExecutions: Int,
    val syncedItems: Int,
    val retryableItems: Int,
    val remainingExecutions: Int,
) {
    val message: String
        get() = "Reconciliação fresca concluída. Execuções resolvidas: $resolvedExecutions. Itens sincronizados: $syncedItems. " +
            "Itens liberados para nova tentativa após ausência externa comprovada: $retryableItems. Pendências restantes: $remainingExecutions."
}

/**
 * Applies a fresh, completed collector generation to the detached ambiguous-publication queue.
 * Missing external evidence only becomes retryable when the collector proves complete coverage for
 * the exact profile. Stale or partial collector state never authorizes a republish.
 */
internal object AgendaScriptFreshPendingResolver0561 {
    private const val PREFS = "rota_certa_agenda_trip_script_executor_0558"
    private const val KEY_HISTORY = "history"
    private const val KEY_PENDING = "pending_reconciliation_0561"
    private const val MAX_HISTORY = 20
    private val lock = Any()

    fun reconcile(
        context: Context,
        collectorState: AgendaAutomaticCollectorState0400,
    ): AgendaScriptFreshReconcileResult0561 = synchronized(lock) {
        val app = context.applicationContext
        val scope = RotaCertaTenantRegistry(app).activeScope()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendingKey = scope.key(KEY_PENDING)
        val historyKey = scope.key(KEY_HISTORY)
        val pendingArray = runCatching { JSONArray(prefs.getString(pendingKey, "[]")) }.getOrDefault(JSONArray())
        val completeProfiles = completeCollectorProfileUuids0408(app, collectorState)
        val store = AgendaTripScriptStore0558(app)

        val stillPending = mutableListOf<JSONObject>()
        val resolved = mutableListOf<JSONObject>()
        var syncedItems = 0
        var retryableItems = 0

        for (index in 0 until pendingArray.length()) {
            val raw = pendingArray.optJSONObject(index) ?: continue
            val execution = decodeExecution(raw.toString())
            if (execution == null) {
                stillPending += raw
                continue
            }
            val nextItems = execution.items.map { item ->
                if (!AgendaScriptPendingPolicy0561.isReconcileState(item.state)) return@map item
                val reconciled = AgendaTripScriptPlanner0558.reconcile(app, item.copy(state = AgendaTripScriptState0558.RECONCILING))
                when {
                    reconciled.state == AgendaTripScriptState0558.SYNCED -> {
                        store.markCompleted(reconciled.fingerprint)
                        syncedItems++
                        reconciled
                    }
                    AgendaScriptFreshMissingPolicy0561.canRetryAfterFreshMissing(
                        state = reconciled.state,
                        profileUuid = reconciled.instruction.profileUuid,
                        completeProfileUuids = completeProfiles,
                        freshGeneration = collectorState.completedGeneration,
                    ) -> {
                        retryableItems++
                        reconciled.copy(
                            state = AgendaTripScriptState0558.FAILED_RETRYABLE,
                            lastError = "fresh_external_not_found_generation_${collectorState.completedGeneration}",
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    else -> reconciled
                }
            }
            val next = execution.copy(updatedAtMillis = System.currentTimeMillis(), items = nextItems)
            if (next.items.any { AgendaScriptPendingPolicy0561.isReconcileState(it.state) }) {
                stillPending += encodeExecution(next)
            } else {
                resolved += encodeExecution(next)
            }
        }

        val historyArray = runCatching { JSONArray(prefs.getString(historyKey, "[]")) }.getOrDefault(JSONArray())
        val history = mutableListOf<JSONObject>()
        resolved.forEach(history::add)
        for (index in 0 until historyArray.length()) historyArray.optJSONObject(index)?.let(history::add)
        val nextHistory = history.take(MAX_HISTORY)
        require(
            prefs.edit()
                .putString(pendingKey, JSONArray().also { a -> stillPending.forEach(a::put) }.toString())
                .putString(historyKey, JSONArray().also { a -> nextHistory.forEach(a::put) }.toString())
                .commit(),
        ) { "Falha ao persistir reconciliação fresca do Executor." }

        AgendaScriptFreshReconcileResult0561(
            resolvedExecutions = resolved.size,
            syncedItems = syncedItems,
            retryableItems = retryableItems,
            remainingExecutions = stillPending.size,
        )
    }

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
