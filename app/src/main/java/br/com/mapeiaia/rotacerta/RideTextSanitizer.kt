package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object RideTextSanitizer {
    fun stripRotaCertaOverlay(text: String): String {
        if (text.isBlank()) return ""
        val lines = text.lines().map { it.replace('\u00A0', ' ').replace('\u202F', ' ').trim() }
        val filtered = lines.filterNot(::isRotaCertaOverlayLine)
        return filtered.joinToString("\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    fun containsRotaCertaOverlay(text: String): Boolean =
        text.lines().count(::isRotaCertaOverlayLine) >= 2

    private fun isRotaCertaOverlayLine(line: String): Boolean {
        val normalized = line.normalizedForOverlayMatch()
        if (normalized.isBlank()) return false
        return overlayMenuLines.any { normalized == it || normalized.endsWith(" $it") }
    }

    private fun String.normalizedForOverlayMatch(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""^[^\p{L}\p{N}]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val overlayMenuLines = setOf(
        "abrir rota certa",
        "salvar card de corrida",
        "salvar card desta corrida",
        "salvar este local",
        "minha regiao de corridas",
        "limpar area de transferencia",
        "criar alerta de proximidade",
        "fechar",
    )
}
