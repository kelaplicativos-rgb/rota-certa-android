package br.com.mapeiaia.rotacerta

import android.graphics.Bitmap
import kotlin.math.roundToInt

enum class VisualReadPurpose0742 {
    FullText,
    Phone,
}

data class VisualReadResult0742(
    val text: String,
    val phoneTarget: ScreenPhoneTarget?,
    val passCount: Int,
    val blockCount: Int,
    val usedRecovery: Boolean,
)

class ScreenVisualReader0742(
    private val ocrService: OcrService,
) {
    suspend fun read(
        bitmap: Bitmap,
        accessibilityText: String,
        purpose: VisualReadPurpose0742,
    ): VisualReadResult0742 {
        val passes = mutableListOf<OcrStructuredText0188>()
        val primary = runCatching { ocrService.extractStructuredText(bitmap) }
            .getOrElse { OcrStructuredText0188(text = "", blocks = emptyList()) }
        passes += primary

        if (purpose == VisualReadPurpose0742.Phone) {
            ScreenVisualFusion0742.findPhone(accessibilityText, passes)?.let { target ->
                return result(accessibilityText, passes, target, usedRecovery = false)
            }
        }

        val crops = ScreenVisualFusion0742.recoveryCrops(
            width = bitmap.width,
            height = bitmap.height,
            blocks = primary.blocks,
            phoneMode = purpose == VisualReadPurpose0742.Phone,
        )
        var usedRecovery = false
        for (spec in crops) {
            val pass = recognizeCrop(bitmap, spec) ?: continue
            usedRecovery = true
            passes += pass

            if (purpose == VisualReadPurpose0742.Phone) {
                ScreenVisualFusion0742.findPhone(accessibilityText, passes)?.let { target ->
                    return result(accessibilityText, passes, target, usedRecovery = true)
                }
            }
        }

        return result(
            accessibilityText = accessibilityText,
            passes = passes,
            phoneTarget = ScreenVisualFusion0742.findPhone(accessibilityText, passes),
            usedRecovery = usedRecovery,
        )
    }

    private suspend fun recognizeCrop(
        source: Bitmap,
        spec: VisualCropSpec0742,
    ): OcrStructuredText0188? {
        val left = spec.left.coerceIn(0, source.width - 1)
        val top = spec.top.coerceIn(0, source.height - 1)
        val right = spec.right.coerceIn(left + 1, source.width)
        val bottom = spec.bottom.coerceIn(top + 1, source.height)
        val crop = runCatching {
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }.getOrNull() ?: return null

        var working = crop
        try {
            val maxDimension = 2_400f
            val factor = minOf(
                spec.scale,
                maxDimension / crop.width.toFloat(),
                maxDimension / crop.height.toFloat(),
            ).coerceAtLeast(1.0f)
            if (factor > 1.05f) {
                val scaled = Bitmap.createScaledBitmap(
                    crop,
                    (crop.width * factor).roundToInt().coerceAtLeast(1),
                    (crop.height * factor).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== crop) {
                    working = scaled
                    crop.recycle()
                }
            }
            return runCatching { ocrService.extractStructuredText(working) }.getOrNull()
        } finally {
            if (!working.isRecycled) working.recycle()
        }
    }

    private fun result(
        accessibilityText: String,
        passes: List<OcrStructuredText0188>,
        phoneTarget: ScreenPhoneTarget?,
        usedRecovery: Boolean,
    ): VisualReadResult0742 = VisualReadResult0742(
        text = ScreenVisualFusion0742.mergeText(accessibilityText, passes),
        phoneTarget = phoneTarget,
        passCount = passes.size,
        blockCount = passes.sumOf { it.blocks.size },
        usedRecovery = usedRecovery,
    )
}
