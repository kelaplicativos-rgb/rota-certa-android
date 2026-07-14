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
    private val inDrivePerKmRegex = Regex(
        "(?i)^.*r\\$\\s*\\d+(?:[,.]\\d+)?\\s*/?\\s*km(?:\\s*[~≈-]\\s*(\\d+(?:[,.]\\d+)?\\s*(?:km|m)))?.*$",
    )

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
            .mapNotNull { line -> sanitizeLine(line, normalizedPackage) }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val key = canonicalKey(line)
                if (key.isNotBlank()) uniqueLines.putIfAbsent(key, line)
            }

        return uniqueLines.values.joinToString("\n")
    }

    private fun sanitizeLine(line: String, packageName: String?): String? {
        if (line.isBlank()) return null
        if (packageName != RideCardTemplateMatcher.INDRIVE_PACKAGE) return line

        val normalized = line.normalized()
        if (normalized == "agora mesmo") return null
        if (inDriveRatingRegex.matches(line) || inDriveCountRegex.matches(line)) return null

        // O inDrive combina custo por km e distancia ate o embarque na mesma linha,
        // por exemplo: "R$ 1,9/km ~4,0 km". O custo unitario e ruido para o farol,
        // mas a distancia depois de "~" e um sinal legitimo do card e precisa ficar.
        val perKmMatch = inDrivePerKmRegex.matchEntire(line)
        if (perKmMatch != null && "aceitar" !in normalized && "ofereca" !in normalized) {
            return perKmMatch.groupValues.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        return line
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
