package br.com.mapeiaia.rotacerta.versioncenter

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseHistoryDocument(
    val schemaVersion: Int = 1,
    val releases: List<ReleaseRecord> = emptyList(),
    val regressions: List<RegressionRecord> = emptyList(),
)

@Serializable
data class ReleaseRecord(
    val version: String,
    val build: Int,
    val commit: String? = null,
    val branch: String? = null,
    val generatedAt: String? = null,
    val status: String? = null,
    val implemented: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val regressions: List<String> = emptyList(),
    val resolvedRegressions: List<String> = emptyList(),
    val modulesAffected: List<String> = emptyList(),
    val detailsComplete: Boolean = true,
)

@Serializable
data class RegressionRecord(
    val regressionId: String,
    val title: String,
    val description: String,
    val introducedIn: String,
    val fixedIn: String? = null,
    val validatedIn: String? = null,
    val affectedModules: List<String> = emptyList(),
    val status: String,
)

object ReleaseHistoryStore {
    private const val ASSET_PATH = "release_history.json"
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Volatile
    private var cached: ReleaseHistoryDocument? = null

    fun load(context: Context): ReleaseHistoryDocument {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                json.decodeFromString<ReleaseHistoryDocument>(reader.readText())
            }.also { loaded ->
                cached = loaded
            }
        }
    }
}

object VersionHistoryLogic {
    fun sortedReleases(releases: List<ReleaseRecord>): List<ReleaseRecord> =
        releases.sortedWith { left, right ->
            val versionOrder = compareVersions(right.version, left.version)
            if (versionOrder != 0) versionOrder else right.build.compareTo(left.build)
        }

    fun availableModules(document: ReleaseHistoryDocument): List<String> =
        (document.releases.flatMap { it.modulesAffected } + document.regressions.flatMap { it.affectedModules })
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun filterByModule(releases: List<ReleaseRecord>, module: String?): List<ReleaseRecord> {
        if (module.isNullOrBlank()) return sortedReleases(releases)
        return sortedReleases(
            releases.filter { release ->
                release.modulesAffected.any { it.equals(module, ignoreCase = true) }
            },
        )
    }

    fun activeRegressions(document: ReleaseHistoryDocument, installedVersion: String): List<RegressionRecord> =
        document.regressions.filter { regression ->
            compareVersions(regression.introducedIn, installedVersion) <= 0 &&
                (regression.validatedIn == null || compareVersions(regression.validatedIn, installedVersion) > 0)
        }

    fun lifecycle(regression: RegressionRecord): String = buildString {
        append("Introduzida em ${regression.introducedIn}")
        regression.fixedIn?.let { append(" → corrigida em $it") }
        regression.validatedIn?.let { append(" → validada em $it") }
    }

    fun compareVersions(left: String, right: String): Int {
        val a = left.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val result = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }
}

object VersionReportBuilder {
    private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val phonePattern = Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?[\\s.-]*)?9?\\d{4}[\\s.-]*\\d{4}(?!\\d)")
    private val sensitivePattern = Regex("(?i)(token|cookie|authorization|session|password|senha)\\s*[:=]\\s*\\S+")

    fun build(
        release: ReleaseRecord,
        regressions: List<RegressionRecord>,
    ): String = sanitize(
        buildString {
            appendLine("ROTA CERTA")
            appendLine()
            appendLine("Versão: ${release.version}")
            appendLine("Build: ${release.build}")
            appendLine("Commit: ${release.commit.orUnavailable()}")
            appendLine("Branch: ${release.branch.orUnavailable()}")
            appendLine("Gerada em: ${release.generatedAt.orUnavailable()}")
            appendLine()
            appendLine("STATUS")
            appendLine(statusLabel(release.status))
            appendSection("IMPLEMENTADO", release.implemented)
            appendSection("CORRIGIDO", release.fixed)
            appendSection("MELHORADO / INOVADO", release.improved)

            val known = regressions.filter { it.regressionId in release.regressions }
            if (known.isNotEmpty()) {
                appendLine()
                appendLine("REGRESSÕES CONHECIDAS")
                known.forEach { appendLine("- ${it.title} — ${VersionHistoryLogic.lifecycle(it)}") }
            }

            val resolved = regressions.filter { it.regressionId in release.resolvedRegressions }
            if (resolved.isNotEmpty()) {
                appendLine()
                appendLine("REGRESSÕES RESOLVIDAS")
                resolved.forEach { appendLine("- ${it.title} — ${VersionHistoryLogic.lifecycle(it)}") }
            }

            appendSection("MÓDULOS AFETADOS", release.modulesAffected)
        }.trimEnd(),
    )

    fun sanitize(value: String): String =
        value
            .replace(emailPattern, "[email mascarado]")
            .replace(phonePattern, "[telefone mascarado]")
            .replace(sensitivePattern) { match -> "${match.groupValues[1]}=[dado sensível mascarado]" }

    fun statusLabel(status: String?): String = when (status) {
        "ESTAVEL" -> "✅ Estável"
        "EM_VALIDACAO" -> "🧪 Em validação"
        "REGRESSAO_CONHECIDA" -> "⚠️ Possui regressão conhecida"
        "CORRECAO_EM_ANDAMENTO" -> "🛠 Correção em andamento"
        else -> "Não registrado"
    }

    private fun StringBuilder.appendSection(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine()
        appendLine(title)
        items.forEach { appendLine("- $it") }
    }

    private fun String?.orUnavailable(): String = this?.takeIf(String::isNotBlank) ?: "indisponível"
}
