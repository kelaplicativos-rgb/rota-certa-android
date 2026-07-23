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
    suspend fun apply(service: AccessibilityService, replyText: String) {
        val normalized = replyText.trim()
        if (normalized.isBlank()) return
        repeat(8) { attempt ->
            if (attempt > 0) delay(120L)
            val root = service.rootInActiveWindow ?: return@repeat
            val target = findEditableNode(root) ?: return@repeat
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, normalized)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Toast.makeText(service, "Resposta inserida.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resposta rapida", normalized))
        Toast.makeText(
            service,
            "Nao consegui preencher automaticamente. A resposta foi copiada.",
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
} // quick_reply_effective_filler_0_1_128
