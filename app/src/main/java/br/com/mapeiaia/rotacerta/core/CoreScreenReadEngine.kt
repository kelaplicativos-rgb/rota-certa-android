package br.com.mapeiaia.rotacerta.core

/**
 * Motor de leitura de tela do Rota Certa Core.
 * Ele nao acessa Android diretamente; recebe os textos brutos e prepara um snapshot estavel.
 *
 * Contrato profissional deste modulo:
 * - popup usa somente o texto atual recebido;
 * - leitura ao vivo combina Acessibilidade + OCR sem duplicar linhas;
 * - fallback so entra quando a combinacao esta vazia;
 * - o hash usado pela bolinha nasce aqui, junto com o snapshot;
 * - normalizacao e hash sao API publica do Core e possuem teste unitario.
 */
object CoreScreenReadEngine {
    fun prepare(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): CoreScreenReadSnapshot {
        val snapshotText = if (allowPopupCandidate) {
            normalizeText(fallbackText)
        } else {
            merge(accessibilityText, ocrText).ifBlank { normalizeText(fallbackText) }
        }
        return CoreScreenReadSnapshot(
            text = snapshotText,
            hash = stableHash(snapshotText),
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

    fun normalizeText(text: String): String =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    fun stableHash(text: String): Int = normalizeText(text).hashCode()

    private fun buildSourceSummary(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): String = "accessibility=${accessibilityText.length};ocr=${ocrText.length};fallback=${fallbackText.length};popup=$allowPopupCandidate"
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
