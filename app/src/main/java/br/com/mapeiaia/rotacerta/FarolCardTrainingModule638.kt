package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest

class FarolCardTrainingModule638(
    private val context: Context,
    private val store: FarolCardSignatureStore638,
) {
    data class Result(
        val model: FarolCardSignatureModel638,
        val evidencePath: String?,
    )

    fun train(
        packageName: String,
        text: String,
        nodes: List<FailedCardNodeLine0161>,
        bitmap: Bitmap?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result {
        val visualHash = bitmap?.let(FarolCardVisualHash638::averageHash)
        val model = FarolCardSignatureCompiler638.newModel(
            packageName = packageName,
            text = text,
            nodes = nodes,
            visualHash = visualHash,
            nowMillis = nowMillis,
        )
        val evidencePath = bitmap?.let { saveEvidence(model.packageName, model.id, it) }
        val saved = store.saveOrMerge(model)
        return Result(saved, evidencePath)
    }

    private fun saveEvidence(packageName: String, id: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "farol-card-signatures-0638/$packageName").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        file.outputStream().buffered().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
        file.absolutePath
    }.getOrNull()
}

object FarolCardVisualHash638 {
    fun averageHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        return try {
            val gray = IntArray(64)
            var sum = 0L
            var index = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val pixel = scaled.getPixel(x, y)
                    val value = (
                        android.graphics.Color.red(pixel) * 299 +
                            android.graphics.Color.green(pixel) * 587 +
                            android.graphics.Color.blue(pixel) * 114
                        ) / 1000
                    gray[index++] = value
                    sum += value
                }
            }
            val average = sum / 64L
            var bits = 0L
            gray.forEachIndexed { bit, value ->
                if (value >= average) bits = bits or (1L shl bit)
            }
            java.lang.Long.toUnsignedString(bits, 16).padStart(16, '0')
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
