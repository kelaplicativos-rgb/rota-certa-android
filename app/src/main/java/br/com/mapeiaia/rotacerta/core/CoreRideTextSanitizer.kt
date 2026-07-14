package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.text.Normalizer
import java.util.Locale

/**
 * Sanitizacao leve e deterministica antes do match de card.
 *
 * Inspiracao arquitetural: leitores maduros tratam cada aplicativo separadamente
 * e removem ruido de OCR antes de classificar a tela. Esta implementacao e propria
 * do Rota Certa e nao depende de codigo externo.
 */
object CoreRideTextSanitizer {
    private val inDriveRatingRegex = Regex("^\\s*\\d(?:[,.]\\d)?\\s*\\(\\d{1,5}\\)\\s*$")
    private val inDriveCountRegex = Regex("^\\s*\\(\\d{1,5}\\)\\s*$")
    private val moneyAndDistanceRegex = Regex("(?i).*r\\$.*\\b(?:km|m)\\b.*")

    fun sanitize(text: String, packageName: String?): String {
        if (text.isBlank()) return ""
        val normalizedPackage = CorePackageMonitor.normalize(packageName)
        val uniqueLines = linkedMapOf<String, String>()

        text.lines()
            .map { line ->
                line.replace('\u00A0', ' ')
                    .replace('\u202F', ' ')
                    .trim()
                    .replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotBlank() }
            .filterNot { line -> shouldIgnoreLine(line, normalizedPackage) }
            .forEach { line ->
                val key = canonicalKey(line)
                if (key.isNotBlank()) uniqueLines.putIfAbsent(key, line)
            }

        return uniqueLines.values.joinToString("\n")
    }

    private fun shouldIgnoreLine(line: String, packageName: String?): Boolean {
        if (packageName != RideCardTemplateMatcher.INDRIVE_PACKAGE) return false
        val normalized = line.normalized()
        if (normalized == "agora mesmo") return true
        if (inDriveRatingRegex.matches(line) || inDriveCountRegex.matches(line)) return true

        // Linhas compactadas como "R$2,03/km" sao custo por km/ruido visual,
        // nao o valor da corrida nem uma distancia de rota independente.
        if (moneyAndDistanceRegex.matches(line) &&
            "aceitar" !in normalized &&
            "ofereca" !in normalized
        ) {
            return true
        }
        return false
    }

    private fun canonicalKey(text: String): String =
        text.normalized()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun String.normalized(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
