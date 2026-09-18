package br.com.mapeiaia.rotacerta.monitoring

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import org.json.JSONArray
import org.json.JSONObject

internal object OperationalHealthEvidenceCapsuleStore0576 {
    private const val PREFS = "rota_certa_operational_health_evidence_0576"
    private const val KEY = "capsules"
    private const val MAX_INCIDENTS = 30
    private const val MAX_EVENTS_PER_INCIDENT = 96

    fun capture(
        context: Context,
        source: UnifiedDebugEventStore.Snapshot,
        health: OperationalHealthSnapshot,
    ) {
        if (health.incidents.isEmpty() || source.events.isEmpty()) return
        val root = loadRoot(context)
        val now = System.currentTimeMillis()

        health.incidents.take(MAX_INCIDENTS).forEach { incident ->
            val hasMatch = source.events.any {
                OperationalHealthEngine.fingerprintForEvidence0575(it) == incident.fingerprint
            }
            if (!hasMatch) return@forEach

            val selected = OperationalHealthTechnicalPackage0575.selectEvidenceEvents0575(
                events = source.events,
                incidents = listOf(incident),
                contextRadius = 12,
                maxEvents = MAX_EVENTS_PER_INCIDENT,
            )
            val eventsJson = JSONArray()
            selected.forEach { event ->
                eventsJson.put(eventJson0576(event))
            }
            root.put(
                incident.id,
                JSONObject()
                    .put("incidentId", incident.id)
                    .put("fingerprint", incident.fingerprint)
                    .put("capturedAtMillis", now)
                    .put("firstSeenMillis", incident.firstSeenMillis)
                    .put("lastSeenMillis", incident.lastSeenMillis)
                    .put("errorCode", safe0576(incident.errorCode, 160))
                    .put("eventCount", selected.size)
                    .put("events", eventsJson),
            )
        }

        val compact = JSONObject()
        val orderedKeys = mutableListOf<Pair<String, Long>>()
        val names = root.keys()
        while (names.hasNext()) {
            val key = names.next()
            val at = root.optJSONObject(key)?.optLong("capturedAtMillis") ?: 0L
            orderedKeys += key to at
        }
        orderedKeys
            .sortedByDescending { it.second }
            .take(MAX_INCIDENTS)
            .forEach { (key, _) ->
                root.optJSONObject(key)?.let { compact.put(key, it) }
            }

        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, compact.toString())
            .apply()
    }

    fun export(context: Context, incidentId: String? = null): String {
        val root = loadRoot(context)
        if (incidentId.isNullOrBlank()) return root.toString(2)
        val selected = JSONObject()
        root.optJSONObject(incidentId)?.let { selected.put(incidentId, it) }
        return selected.toString(2)
    }

    internal fun eventJson0576(event: UnifiedDebugEventStore.SnapshotEvent): JSONObject {
        val json = JSONObject()
            .put("atMillis", event.atMillis)
            .put("monotonicNs", event.monotonicNs)
            .put("stage", safe0576(event.stage, 160))
            .put("packageName", safe0576(event.packageName, 160))
            .put("threadName", safe0576(event.threadName, 120))
            .put("details", safe0576(event.details, 1_600))

        event.diagnosticContext?.let { diagnostic ->
            json.put(
                "diagnostic",
                JSONObject()
                    .put("parentModule", diagnostic.parentModule.name)
                    .put("originModule", diagnostic.originModule.name)
                    .put("executorModule", diagnostic.executorModule.name)
                    .put("operation", safe0576(diagnostic.operation, 160))
                    .put("correlationId", safe0576(diagnostic.correlationId, 180))
                    .put("traceId", safe0576(diagnostic.traceId, 180))
                    .put("operationId", safe0576(diagnostic.operationId, 180))
                    .put("parentOperationId", safe0576(diagnostic.parentOperationId, 180))
                    .put("result", safe0576(diagnostic.result, 100))
                    .put("errorCode", safe0576(diagnostic.errorCode, 160))
                    .put("reason", safe0576(diagnostic.reason, 420)),
            )
        }
        return json
    }

    private fun loadRoot(context: Context): JSONObject {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            .orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun safe0576(value: String, max: Int): String =
        UnifiedDebugEventStore.sanitizeForExport(value).take(max)
}
