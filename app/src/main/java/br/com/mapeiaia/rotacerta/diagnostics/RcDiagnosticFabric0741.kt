package br.com.mapeiaia.rotacerta.diagnostics

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class RcDiagnosticSeverity { DEBUG, INFO, WARN, ERROR, CRITICAL }

data class RcDiagnosticEvent(
    val wallMs: Long,
    val monotonicNs: Long,
    val module: String,
    val action: String,
    val traceId: String,
    val severity: RcDiagnosticSeverity,
    val details: Map<String, String>,
)

data class RcIncident(
    val id: String,
    val createdAtMs: Long,
    val module: String,
    val invariant: String,
    val traceId: String,
    val evidence: List<RcDiagnosticEvent>,
)

object RcPrivacyRedactor {
    private val forbidden = Regex("(?i)(authorization|cookie|password|senha|pin|token|secret|session|telefone|phone|whatsapp)")
    fun sanitize(input: Map<String, String>): Map<String, String> =
        input.entries.associate { (key, value) ->
            key.take(64) to if (forbidden.containsMatchIn(key)) "[REDACTED]" else value.take(512)
        }
}

object RcDiagnosticFabric0741 {
    const val MARKER = "RC_DIAGNOSTIC_FABRIC_0741"
    private const val MAX_EVENTS = 4096
    private const val INCIDENT_WINDOW = 160
    private val lock = Any()
    private val events = ArrayDeque<RcDiagnosticEvent>(MAX_EVENTS)
    private val incidents = ArrayDeque<RcIncident>(128)
    private val modules = ConcurrentHashMap.newKeySet<String>()
    private val installed = AtomicBoolean(false)

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return
        registerModule("APP")
        registerModule("AGENDA")
        registerModule("TIMELINE")
        registerModule("FAROL")
        registerModule("BLABLACAR")
        registerModule("PASSENGERS")
        registerModule("TRACKING")
        registerModule("VIP")
        registerModule("HEALTH")
        registerModule("NETWORK")
        registerModule("DATABASE")
        registerModule("BACKGROUND")
        registerModule("BUILD")
        event("HEALTH", "FABRIC_READY", details = mapOf("marker" to MARKER))
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) = lifecycle(a, "CREATED")
            override fun onActivityStarted(a: Activity) = lifecycle(a, "STARTED")
            override fun onActivityResumed(a: Activity) = lifecycle(a, "RESUMED")
            override fun onActivityPaused(a: Activity) = lifecycle(a, "PAUSED")
            override fun onActivityStopped(a: Activity) = lifecycle(a, "STOPPED")
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = lifecycle(a, "STATE_SAVED")
            override fun onActivityDestroyed(a: Activity) = lifecycle(a, "DESTROYED")
        })
    }

    fun registerModule(name: String) { modules += safe(name) }

    fun newTrace(module: String, action: String): String {
        val id = "rc-" + UUID.randomUUID().toString().replace("-", "").take(20)
        event(module, action + "_START", id)
        return id
    }

    fun event(
        module: String,
        action: String,
        traceId: String = "trace-none",
        severity: RcDiagnosticSeverity = RcDiagnosticSeverity.INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        val e = RcDiagnosticEvent(
            System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), safe(module), safe(action),
            safe(traceId), severity, RcPrivacyRedactor.sanitize(details)
        )
        modules += e.module
        synchronized(lock) {
            while (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(e)
        }
    }

    fun invariant(
        module: String,
        name: String,
        ok: Boolean,
        traceId: String,
        expected: String,
        actual: String,
    ): RcIncident? {
        if (ok) return null
        event(module, "INVARIANT_VIOLATION", traceId, RcDiagnosticSeverity.ERROR,
            mapOf("invariant" to name, "expected" to expected, "actual" to actual))
        val evidence = synchronized(lock) { events.takeLast(INCIDENT_WINDOW) }
        val incident = RcIncident(
            "INC-" + System.currentTimeMillis().toString(36).uppercase(),
            System.currentTimeMillis(), safe(module), safe(name), safe(traceId), evidence
        )
        synchronized(lock) {
            while (incidents.size >= 128) incidents.removeFirst()
            incidents.addLast(incident)
        }
        return incident
    }

    fun snapshot(module: String? = null, limit: Int = 250): List<RcDiagnosticEvent> = synchronized(lock) {
        events.asSequence().filter { module == null || it.module == safe(module) }.takeLast(limit.coerceIn(1, 1000)).toList()
    }

    fun incidentSnapshot(): List<RcIncident> = synchronized(lock) { incidents.toList() }
    fun moduleSnapshot(): Set<String> = modules.toSortedSet()

    fun diagnosticDigest(): String {
        val payload = synchronized(lock) {
            events.takeLast(256).joinToString("\n") { e ->
                "${e.wallMs}|${e.module}|${e.action}|${e.traceId}|${e.severity}|${e.details}"
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun lifecycle(a: Activity, state: String) {
        event("APP", "ACTIVITY_$state", details = mapOf("activity" to a.javaClass.simpleName))
    }

    private fun safe(v: String): String =
        v.uppercase().replace(Regex("[^A-Z0-9_.:-]"), "_").take(96).ifBlank { "UNKNOWN" }
}

class RcDiagnosticFabricInitializer0741 : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        RcDiagnosticFabric0741.install(app)
        return true
    }
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
