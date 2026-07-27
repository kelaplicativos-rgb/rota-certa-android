package br.com.mapeiaia.rotacerta.core

import java.text.Normalizer
import java.util.Locale

object CoreRideTextSanitizer {
    fun sanitize(text: String, packageName: String?): String {
        @Suppress("UNUSED_VARIABLE") val ignoredPackage = packageName
        if (text.isBlank()) return ""
        val unique = linkedMapOf<String, String>()
        text.lines()
            .map { it.replace('\u00A0', ' ').replace('\u202F', ' ').trim().replace(Regex("""\s+"""), " ") }
            .filter(String::isNotBlank)
            .forEach { line -> unique.putIfAbsent(canonical(line), line) }
        return unique.values.joinToString("\n")
    }

    private fun canonical(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), "")
}
