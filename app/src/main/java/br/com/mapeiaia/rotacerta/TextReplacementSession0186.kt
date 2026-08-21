package br.com.mapeiaia.rotacerta

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID


internal object TextReplacementLengthPolicy0186 {
    fun allowedFinalLength(
        originalLength: Int,
        selectionLength: Int,
        replacementLength: Int,
        maxLength: Int,
    ): Int? {
        if (originalLength < 0 || selectionLength < 0 || replacementLength < 0 || maxLength < 0) return null
        if (selectionLength > originalLength) return null
        val projected = originalLength.toLong() - selectionLength.toLong() + replacementLength.toLong()
        return projected.takeIf { it <= maxLength.toLong() }?.toInt()
    }
}

/**
 * Sessão somente em memória para substituição explícita e fail-closed.
 * Nenhum texto é gravado em preferências, arquivo ou log.
 */
data class TextReplacementTicket0186(
    val token: String,
    val capturedText: String,
)

object TextReplacementSession0186 {
    const val CONTRACT_MARKER = "SAFE_TEXT_REPLACEMENT_0186"
    private const val MAX_AGE_MILLIS = 5 * 60 * 1_000L
    private const val MAX_TEXT_LENGTH = 12_000

    private data class Session(
        val token: String,
        val node: AccessibilityNodeInfo,
        val originalFullText: String,
        val capturedText: String,
        val packageName: String,
        val className: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val createdAtMillis: Long,
    )

    private var current: Session? = null

    @Synchronized
    fun create(node: AccessibilityNodeInfo?, nowMillis: Long = System.currentTimeMillis()): TextReplacementTicket0186? {
        clearLocked()
        if (node == null) return null
        val editable = runCatching { node.isEditable }.getOrDefault(false)
        val password = runCatching { node.isPassword }.getOrDefault(true)
        val fullText = runCatching { node.text?.toString().orEmpty() }.getOrDefault("").take(MAX_TEXT_LENGTH)
        if (!editable || password || fullText.isBlank()) return null
        val packageName = runCatching { node.packageName?.toString().orEmpty() }.getOrDefault("")
        val className = runCatching { node.className?.toString().orEmpty() }.getOrDefault("")
        if (packageName.isBlank() || className.isBlank()) return null
        val startRaw = runCatching { node.textSelectionStart }.getOrDefault(-1)
        val endRaw = runCatching { node.textSelectionEnd }.getOrDefault(-1)
        val hasSelection = startRaw >= 0 && endRaw > startRaw && endRaw <= fullText.length
        val start = if (hasSelection) startRaw else 0
        val end = if (hasSelection) endRaw else fullText.length
        val captured = fullText.substring(start, end).take(MAX_TEXT_LENGTH)
        if (captured.isBlank()) return null
        @Suppress("DEPRECATION")
        val copy = runCatching { AccessibilityNodeInfo.obtain(node) }.getOrNull() ?: return null
        val token = UUID.randomUUID().toString()
        current = Session(
            token = token,
            node = copy,
            originalFullText = fullText,
            capturedText = captured,
            packageName = packageName,
            className = className,
            selectionStart = start,
            selectionEnd = end,
            createdAtMillis = nowMillis,
        )
        return TextReplacementTicket0186(token, captured)
    }

    @Synchronized
    fun canReplace(token: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val session = current ?: return false
        if (!isCurrent(session, token, nowMillis)) return false
        return validate(session)
    }

    @Synchronized
    fun replace(token: String?, correctedText: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val session = current ?: return false
        if (!isCurrent(session, token, nowMillis) || !validate(session) || correctedText.isBlank()) {
            clearLocked()
            return false
        }
        val replacement = correctedText.take(MAX_TEXT_LENGTH)
        val finalLength = TextReplacementLengthPolicy0186.allowedFinalLength(
            originalLength = session.originalFullText.length,
            selectionLength = session.selectionEnd - session.selectionStart,
            replacementLength = replacement.length,
            maxLength = MAX_TEXT_LENGTH,
        ) ?: run {
            clearLocked()
            return false
        }
        val finalText = buildString(finalLength) {
            append(session.originalFullText, 0, session.selectionStart)
            append(replacement)
            append(session.originalFullText, session.selectionEnd, session.originalFullText.length)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }
        val success = runCatching {
            session.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
        clearLocked()
        return success
    }

    @Synchronized
    fun clear(token: String? = null) {
        if (token == null || current?.token == token) clearLocked()
    }

    private fun isCurrent(session: Session, token: String?, nowMillis: Long): Boolean {
        if (token == null || token != session.token || nowMillis - session.createdAtMillis > MAX_AGE_MILLIS) {
            clearLocked()
            return false
        }
        return true
    }

    private fun validate(session: Session): Boolean {
        val refreshed = runCatching { session.node.refresh() }.getOrDefault(false)
        if (!refreshed) return false
        if (!runCatching { session.node.isEditable }.getOrDefault(false)) return false
        if (runCatching { session.node.isPassword }.getOrDefault(true)) return false
        if (runCatching { session.node.packageName?.toString() }.getOrNull() != session.packageName) return false
        if (runCatching { session.node.className?.toString() }.getOrNull() != session.className) return false
        if (runCatching { session.node.text?.toString().orEmpty() }.getOrDefault("") != session.originalFullText) return false
        val start = runCatching { session.node.textSelectionStart }.getOrDefault(-1)
        val end = runCatching { session.node.textSelectionEnd }.getOrDefault(-1)
        val expectedWholeField = session.selectionStart == 0 && session.selectionEnd == session.originalFullText.length
        return expectedWholeField || (start == session.selectionStart && end == session.selectionEnd)
    }

    private fun clearLocked() {
        @Suppress("DEPRECATION")
        current?.let { runCatching { it.node.recycle() } }
        current = null
    }
}
