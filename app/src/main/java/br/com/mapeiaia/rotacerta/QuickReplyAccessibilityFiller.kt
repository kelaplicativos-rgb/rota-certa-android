package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.delay

internal object QuickReplyAccessibilityFiller {
    suspend fun apply(
        service: AccessibilityService,
        replyText: String,
        expectedPackageName: String? = null,
    ) {
        val normalizedText = replyText.trim()
        if (normalizedText.isBlank()) return

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MILLIS)
            val root = service.rootInActiveWindow ?: return@repeat
            val rootPackageName = root.packageName?.toString()
            if (!QuickReplyTargetPolicy.canFill(
                    currentPackageName = rootPackageName,
                    expectedPackageName = expectedPackageName,
                    ownPackageName = service.packageName,
                )
            ) return@repeat

            val target = findEditableNode(root) ?: return@repeat
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    normalizedText,
                )
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Toast.makeText(service, "Resposta inserida.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        copyFallback(service, normalizedText)
    }

    private fun copyFallback(service: AccessibilityService, text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resposta rápida", text))
        Toast.makeText(
            service,
            "Não consegui preencher automaticamente. A resposta foi copiada.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) return node
        for (index in 0 until node.childCount) {
            findEditableNode(runCatching { node.getChild(index) }.getOrNull())?.let { return it }
        }
        return node.takeIf { it.isEditable }
    }

    private const val MAX_ATTEMPTS = 14
    private const val RETRY_DELAY_MILLIS = 100L
}
