package br.com.mapeiaia.rotacerta

import android.graphics.Bitmap
import kotlin.math.roundToInt

enum class VisualReadPurpose0742 {
    FullText,
    Phone,
    Address,
}

data class VisualReadResult0742(
    val text: String,
    val phoneTarget: ScreenPhoneTarget?,
    val addressCandidates: List<String>,
    val blocks: List<OcrTextBlock0188>,
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

        if (purpose == VisualReadPurpose0742.Address) {
            val addresses0752 = ScreenAddressVisualAuthority0752.orderedAddresses(
                accessibilityText = accessibilityText,
                passes = passes,
            )
            if (addresses0752.size >= 2) {
                return result(
                    accessibilityText = accessibilityText,
                    passes = passes,
                    phoneTarget = null,
                    usedRecovery = false,
                )
            }
        }

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

            if (purpose == VisualReadPurpose0742.Address) {
                val addresses0752 = ScreenAddressVisualAuthority0752.orderedAddresses(
                    accessibilityText = accessibilityText,
                    passes = passes,
                )
                if (addresses0752.size >= 2) {
                    return result(
                        accessibilityText = accessibilityText,
                        passes = passes,
                        phoneTarget = null,
                        usedRecovery = true,
                    )
                }
            }

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
            val recognized = runCatching { ocrService.extractStructuredText(working) }.getOrNull()
                ?: return null
            return OcrStructuredText0188(
                text = recognized.text,
                blocks = recognized.blocks.mapIndexed { index, block ->
                    OcrTextBlock0188(
                        id = "visual-${spec.id}-$index",
                        text = block.text,
                        left = left + (block.left / factor).roundToInt(),
                        top = top + (block.top / factor).roundToInt(),
                        right = left + (block.right / factor).roundToInt(),
                        bottom = top + (block.bottom / factor).roundToInt(),
                    )
                },
            )
        } finally {
            if (!working.isRecycled) working.recycle()
        }
    }

    private fun result(
        accessibilityText: String,
        passes: List<OcrStructuredText0188>,
        phoneTarget: ScreenPhoneTarget?,
        usedRecovery: Boolean,
    ): VisualReadResult0742 {
        val text0742 = ScreenVisualFusion0742.mergeText(accessibilityText, passes)
        val blocks0742 = ScreenAddressVisualAuthority0752.deduplicatedBlocks(passes)
        return VisualReadResult0742(
            text = text0742,
            phoneTarget = phoneTarget,
            addressCandidates = ScreenAddressVisualAuthority0752.orderedAddresses(
                accessibilityText = accessibilityText,
                passes = passes,
            ),
            blocks = blocks0742,
            passCount = passes.size,
            blockCount = passes.sumOf { it.blocks.size },
            usedRecovery = usedRecovery,
        )
    }
}
