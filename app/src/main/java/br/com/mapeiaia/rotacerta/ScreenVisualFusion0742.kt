package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class VisualCropSpec0742(
    val id: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val scale: Float,
)

object ScreenVisualFusion0742 {
    const val MARKER = "ROTA_VISUAL_INTELLIGENCE_0742"

    private val phoneAnchors = listOf("whatsapp", "whats app", "telefone", "celular", "contato", "fone", "phone")

    fun mergeText(
        accessibilityText: String,
        passes: List<OcrStructuredText0188>,
    ): String {
        val seen = linkedSetOf<String>()
        val output = mutableListOf<String>()

        fun addSource(source: String) {
            source.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { line ->
                    val key = normalizeKey(line)
                    if (key.isNotBlank() && seen.add(key)) output += line
                }
        }

        passes.forEach { addSource(it.text) }
        addSource(accessibilityText)
        return output.joinToString("\n")
    }

    fun findPhone(
        accessibilityText: String,
        passes: List<OcrStructuredText0188>,
    ): ScreenPhoneTarget? {
        val candidates = mutableListOf<ScreenPhoneTarget>()

        fun inspect(text: String) {
            ScreenPhoneLink.findBest(text)?.let(candidates::add)
        }

        inspect(accessibilityText)
        passes.forEach { pass ->
            inspect(pass.text)
            blockNeighborhoods(pass.blocks).forEach(::inspect)
        }

        return candidates.maxWithOrNull(
            compareBy<ScreenPhoneTarget> { it.score }
                .thenBy { it.nationalDigits.length }
                .thenByDescending { it.nationalDigits },
        )
    }

    fun recoveryCrops(
        width: Int,
        height: Int,
        blocks: List<OcrTextBlock0188>,
        phoneMode: Boolean,
    ): List<VisualCropSpec0742> {
        if (width <= 32 || height <= 32) return emptyList()
        val specs = mutableListOf<VisualCropSpec0742>()

        if (phoneMode) {
            blocks.asSequence()
                .filter { block -> phoneAnchors.any { anchor -> normalizeKey(block.text).contains(anchor) } }
                .sortedBy { it.top }
                .take(2)
                .forEachIndexed { index, block ->
                    val blockHeight = max(32, block.bottom - block.top)
                    val desiredHeight = max((height * 0.34f).toInt(), blockHeight * 12)
                    val centerY = (block.top + block.bottom) / 2
                    var top = centerY - desiredHeight / 3
                    var bottom = top + desiredHeight
                    if (top < 0) {
                        bottom -= top
                        top = 0
                    }
                    if (bottom > height) {
                        top -= bottom - height
                        bottom = height
                    }
                    specs += VisualCropSpec0742(
                        id = "anchor-$index",
                        left = 0,
                        top = top.coerceAtLeast(0),
                        right = width,
                        bottom = bottom.coerceAtMost(height),
                        scale = 2.0f,
                    )
                }
        }

        val bandHeight = max(64, (height * 0.46f).toInt())
        val starts = listOf(
            0,
            ((height - bandHeight) / 2).coerceAtLeast(0),
            (height - bandHeight).coerceAtLeast(0),
        )
        starts.forEachIndexed { index, top ->
            specs += VisualCropSpec0742(
                id = "band-$index",
                left = 0,
                top = top,
                right = width,
                bottom = min(height, top + bandHeight),
                scale = if (phoneMode) 2.0f else 1.75f,
            )
        }

        return specs
            .filter { it.right - it.left >= 32 && it.bottom - it.top >= 32 }
            .distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
            .take(if (phoneMode) 5 else 3)
    }

    private fun blockNeighborhoods(blocks: List<OcrTextBlock0188>): List<String> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedWith(compareBy<OcrTextBlock0188> { it.top }.thenBy { it.left })
        val output = mutableListOf<String>()
        sorted.forEach { anchor ->
            val key = normalizeKey(anchor.text)
            if (phoneAnchors.none(key::contains)) return@forEach
            val height = max(24, anchor.bottom - anchor.top)
            val center = (anchor.top + anchor.bottom) / 2
            val radius = max(260, height * 10)
            val nearby = sorted
                .filter { block ->
                    val blockCenter = (block.top + block.bottom) / 2
                    kotlin.math.abs(blockCenter - center) <= radius
                }
                .joinToString("\n") { it.text }
            if (nearby.isNotBlank()) output += nearby
        }
        return output.distinct()
    }

    private fun normalizeKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
}
