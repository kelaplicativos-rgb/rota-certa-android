package br.com.mapeiaia.rotacerta

import android.content.Context

/** Persists only compact CASE snapshots on significant events while intensive diagnostics is active. */
object FarolForensicCaseStoreStage32 {
    private const val PREFS = "farol_stage32_case_store"
    private const val KEY_REPORT = "report"
    private const val KEY_WALL = "wall"
    private const val MAX_CHARS = 60_000

    fun persistIfIntensive(context: Context) {
        if (!IntensiveDiagnostics0172.isActive(context)) return
        persist(context)
    }

    fun persist(context: Context) {
        val report = FarolForensicCardBlackBoxStage32.exportReport().takeLast(MAX_CHARS)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_WALL, System.currentTimeMillis()).putString(KEY_REPORT, report).apply()
    }

    fun export(context: Context): String {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return buildString {
            appendLine("ROTA CERTA — STAGE32 PERSISTED FORENSIC CASES")
            appendLine("persistedWall=${p.getLong(KEY_WALL, 0L)}")
            appendLine(p.getString(KEY_REPORT, null) ?: "(nenhum CASE persistido)")
        }.trimEnd()
    }
}
