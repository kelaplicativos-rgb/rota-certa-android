package br.com.mapeiaia.rotacerta.core

import java.text.Normalizer
import java.util.Locale

/**
 * Motor de leitura de tela do Rota Certa Core.
 * Ele nao acessa Android diretamente; recebe o texto bruto da leitura atual e prepara um snapshot estavel.
 *
 * Contrato deste modulo:
 * - cada callback usa somente o texto que acabou de ler;
 * - texto de Acessibilidade ou OCR guardado de um callback anterior nunca entra no card atual;
 * - popup e leitura ao vivo seguem a mesma regra de isolamento;
 * - o hash usado pela bolinha nasce aqui, junto com o snapshot;
 * - normalizacao e hash sao API publica do Core e possuem teste unitario.
 *
 * Os parametros accessibilityText e ocrText continuam na assinatura para compatibilidade com o
 * servico existente, mas servem apenas ao resumo diagnostico. O fallbackText representa a captura
 * corrente e e a unica fonte autorizada a decidir cor e quilometragem.
 */
object CoreScreenReadEngine {
    fun prepare(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): CoreScreenReadSnapshot {
        val snapshotText = normalizeText(fallbackText)
        return CoreScreenReadSnapshot(
            text = snapshotText,
            hash = stableHash(snapshotText),
            kind = if (snapshotText.isBlank()) CoreScreenReadKind.Empty else CoreScreenReadKind.Ready,
            sourceSummary = buildSourceSummary(accessibilityText, ocrText, fallbackText, allowPopupCandidate),
        )
    }

    /**
     * Mantido para usos explicitos fora da leitura ao vivo. A bolinha nao usa este metodo para
     * combinar callbacks, porque isso poderia unir o card atual ao OCR do card anterior.
     */
    fun merge(accessibilityText: String, ocrText: String): String {
        val linesByCanonicalKey = linkedMapOf<String, String>()
        listOf(accessibilityText, ocrText)
            .flatMap { it.lines() }
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val canonicalKey = canonicalLineKey(line)
                if (canonicalKey.isNotBlank()) linesByCanonicalKey.putIfAbsent(canonicalKey, line)
            }
        return linesByCanonicalKey.values.joinToString("\n")
    }

    fun normalizeText(text: String): String =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /**
     * Hash semantico: ignora acentos, caixa, espacos e pontuacao que variam entre
     * Acessibilidade e OCR. Mantem a ordem das linhas para ainda detectar troca real
     * de card, mas evita piscar por R$ 10/R$10 ou Sao/São.
     */
    fun stableHash(text: String): Int =
        text.lines()
            .map(::canonicalLineKey)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .hashCode()

    /**
     * Chave exclusiva para deduplicacao entre Acessibilidade e OCR.
     * Espacos e pontuacao variam muito entre as duas fontes: R$ 10, R$10,
     * Sao/São e 2,3 KM/2,3 km precisam representar a mesma linha.
     */
    private fun canonicalLineKey(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
            .trim()

    private fun buildSourceSummary(
        accessibilityText: String,
        ocrText: String,
        fallbackText: String,
        allowPopupCandidate: Boolean,
    ): String = "accessibility=${accessibilityText.length};ocr=${ocrText.length};fallback=${fallbackText.length};popup=$allowPopupCandidate;isolated=true"
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
