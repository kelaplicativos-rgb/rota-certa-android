package br.com.mapeiaia.rotacerta

import java.util.Locale

/** Correção conservadora, offline e revisável para português do Brasil. */
data class TextCorrectionResult0186(
    val original: String,
    val corrected: String,
    val changeCount: Int,
)

object PortugueseTextCorrectionEngine0186 {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    const val CONTRACT_MARKER = "OFFLINE_TEXT_CORRECTION_0186"

    private val protectedSpanRegex = (
        """(?i)(?:https?://|www\.)[^\s<>]+|""" +
            """[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.[\p{L}]{2,}"""
        ).toRegex()

    private val commonWords = mapOf(
        "nao" to "não",
        "voce" to "você",
        "voces" to "vocês",
        "tambem" to "também",
        "ja" to "já",
        "apos" to "após",
        "possivel" to "possível",
        "facil" to "fácil",
        "dificil" to "difícil",
        "necessario" to "necessário",
        "necessaria" to "necessária",
        "estao" to "estão",
        "numero" to "número",
        "endereco" to "endereço",
        "enderecos" to "endereços",
        "informacao" to "informação",
        "informacoes" to "informações",
        "usuario" to "usuário",
        "usuarios" to "usuários",
    )

    fun correct(raw: String): TextCorrectionResult0186 {
        val original = raw.take(MAX_TEXT_LENGTH)
        if (original.isBlank()) return TextCorrectionResult0186(original, "", 0)

        val protectedSpans = mutableListOf<String>()
        var corrected = original.replace("\r\n", "\n").replace('\r', '\n').trim()
        corrected = protectSpans(corrected, protectedSpans)
        corrected = corrected
            .replace("[ \\t]+".toRegex(), " ")
            .replace(" +\n".toRegex(), "\n")
            .replace("\n{3,}".toRegex(), "\n\n")
            .replace("\\s+([,;:!?])".toRegex(), "$1")
            .replace("([,;:!?])(?=\\p{L})".toRegex(), "$1 ")

        corrected = replaceCommonWords(corrected)
        corrected = capitalizeSentences(corrected)
        corrected = restoreSpans(corrected, protectedSpans)

        val changes = if (corrected == original) 0 else estimateChanges(original, corrected)
        return TextCorrectionResult0186(original, corrected, changes)
    }

    private fun protectSpans(text: String, protectedSpans: MutableList<String>): String =
        protectedSpanRegex.replace(text) { match ->
            val index = protectedSpans.size
            protectedSpans += match.value
            protectedPlaceholder(index)
        }

    private fun restoreSpans(text: String, protectedSpans: List<String>): String {
        var restored = text
        protectedSpans.forEachIndexed { index, value ->
            restored = restored.replace(protectedPlaceholder(index), value)
        }
        return restored
    }

    private fun protectedPlaceholder(index: Int): String = "\uE000${index}\uE001"

    private fun replaceCommonWords(text: String): String {
        val wordRegex = "(?U)\\b[\\p{L}]+\\b".toRegex()
        return wordRegex.replace(text) { match ->
            val value = match.value
            val replacement = commonWords[value.lowercase(PT_BR)] ?: return@replace value
            preserveCase(value, replacement)
        }
    }

    private fun preserveCase(source: String, replacement: String): String = when {
        source.all(Char::isUpperCase) -> replacement.uppercase(PT_BR)
        source.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.titlecase(PT_BR) }
        else -> replacement
    }

    private fun capitalizeSentences(text: String): String {
        val chars = text.toCharArray()
        var capitalizeNext = true
        for (index in chars.indices) {
            val char = chars[index]
            if (capitalizeNext && char.isLetter()) {
                chars[index] = char.titlecaseChar()
                capitalizeNext = false
            }
            val next = chars.getOrNull(index + 1)
            if (char == '\n' || ((char == '.' || char == '!' || char == '?') && (next == null || next.isWhitespace()))) {
                capitalizeNext = true
            }
        }
        return String(chars)
    }

    private fun estimateChanges(original: String, corrected: String): Int {
        val originalTokens = original.split("\\s+".toRegex())
        val correctedTokens = corrected.split("\\s+".toRegex())
        val paired = minOf(originalTokens.size, correctedTokens.size)
        var count = kotlin.math.abs(originalTokens.size - correctedTokens.size)
        for (index in 0 until paired) if (originalTokens[index] != correctedTokens[index]) count += 1
        return count.coerceAtLeast(1)
    }

    private const val MAX_TEXT_LENGTH = 12_000
}
