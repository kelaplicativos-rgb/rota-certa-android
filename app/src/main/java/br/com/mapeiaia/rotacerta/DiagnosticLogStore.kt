package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.SharedPreferences

object DiagnosticLogStore {
    private const val MaxEvents = 1_500
    private const val PersistedEvents = 900
    private const val MaxSourceLength = 48
    private const val MaxMessageLength = 700
    private const val PreferencesName = "rota_certa_live_diagnostic_trace"
    private const val EventsKey = "events"

    private val lock = Any()
    private val events = mutableListOf<String>()
    private var preferences: SharedPreferences? = null
    private var loadedFromDisk = false

    fun attach(context: Context) {
        synchronized(lock) {
            if (preferences == null) {
                preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            }
            if (!loadedFromDisk) {
                loadedFromDisk = true
                val persisted = preferences
                    ?.getString(EventsKey, "")
                    .orEmpty()
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .takeLast(PersistedEvents)
                if (persisted.isNotEmpty() && events.isEmpty()) {
                    events += persisted
                }
            }
        }
    }

    fun record(source: String, message: String, nowMillis: Long = System.currentTimeMillis()) {
        val cleanSource = source
            .trim()
            .ifBlank { "unknown" }
            .replace(Regex("\\s+"), "_")
            .take(MaxSourceLength)
        val cleanMessage = message
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .ifBlank { "empty" }
            .take(MaxMessageLength)
        synchronized(lock) {
            events += "$nowMillis $cleanSource $cleanMessage"
            while (events.size > MaxEvents) events.removeAt(0)
            persistLocked()
        }
    }

    fun dump(maxEvents: Int = MaxEvents): String = synchronized(lock) {
        if (!loadedFromDisk) {
            val persisted = preferences
                ?.getString(EventsKey, "")
                .orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeLast(PersistedEvents)
            if (persisted.isNotEmpty() && events.isEmpty()) events += persisted
            loadedFromDisk = true
        }
        events
            .takeLast(maxEvents.coerceIn(1, MaxEvents))
            .joinToString("\n")
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
            preferences?.edit()?.remove(EventsKey)?.apply()
        }
    }

    private fun persistLocked() {
        preferences
            ?.edit()
            ?.putString(EventsKey, events.takeLast(PersistedEvents).joinToString("\n"))
            ?.apply()
    }
}
