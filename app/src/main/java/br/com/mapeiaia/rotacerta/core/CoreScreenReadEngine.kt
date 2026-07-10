package br.com.mapeiaia.rotacerta.core

/**
 * Motor de leitura de tela do Rota Certa Core.
 * Ele nao acessa Android diretamente; recebe os textos brutos e prepara um snapshot estavel.
 */
object CoreScreenReadEngine {
    fun prepare(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): CoreScreenReadSnapshot {
        val snapshotText = if (allowPopupCandidate) {
            fallbackText.trimStable()
        } else {
            merge(accessibilityText, ocrText).ifBlank { fallbackText.trimStable() }
        }
        return CoreScreenReadSnapshot(
            text = snapshotText,
            hash = snapshotText.stableHash(),
            kind = if (snapshotText.isBlank()) CoreScreenReadKind.Empty else CoreScreenReadKind.Ready,
            sourceSummary = buildSourceSummary(accessibilityText, ocrText, fallbackText, allowPopupCandidate),
        )
    }

    fun merge(accessibilityText: String, ocrText: String): String {
        val lines = linkedSetOf<String>()
        listOf(accessibilityText, ocrText)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { lines += it }
        return lines.joinToString("\n")
    }

    private fun buildSourceSummary(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): String = "accessibility=${accessibilityText.length};ocr=${ocrText.length};fallback=${fallbackText.length};popup=$allowPopupCandidate"

    private fun String.trimStable(): String =
        lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

    private fun String.stableHash(): Int = hashCode()
}

enum class CoreScreenReadKind {
    Empty,
    Ready,
}

data class CoreScreenReadSnapshot(
    val text: String,
    val hash: Int,
    val kind: CoreScreenReadKind,
    val sourceSummary: String,
)
