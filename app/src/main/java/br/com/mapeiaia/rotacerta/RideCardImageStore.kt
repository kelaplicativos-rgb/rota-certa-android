package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

class RideCardImageStore(private val context: Context) {
    private val directory = File(context.filesDir, "ride-card-models")

    suspend fun save(
        bitmap: Bitmap,
        requestedBounds: Rect?,
        packageName: String?,
        signature: String,
    ): StoredRideCardImage? = withContext(Dispatchers.IO) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        directory.mkdirs()
        val bounds = sanitizeBounds(requestedBounds, bitmap.width, bitmap.height)
        val cropped = runCatching {
            Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
        }.getOrNull() ?: return@withContext null
        val safePackage = packageName.orEmpty().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "unknown" }
        val safeSignature = canonical(signature).hashCode().toUInt().toString(16)
        val file = File(directory, "${System.currentTimeMillis()}-${safePackage}-${safeSignature}.jpg")
        val saved = runCatching {
            file.outputStream().buffered().use { output -> cropped.compress(Bitmap.CompressFormat.JPEG, 88, output) }
        }.getOrDefault(false)
        if (cropped !== bitmap) cropped.recycle()
        if (!saved) {
            file.delete()
            return@withContext null
        }
        prune()
        StoredRideCardImage(file.absolutePath, bounds)
    }

    private fun sanitizeBounds(requested: Rect?, width: Int, height: Int): Rect {
        val fallback = Rect(
            (width * 0.03f).toInt(),
            (height * 0.10f).toInt(),
            (width * 0.97f).toInt(),
            (height * 0.93f).toInt(),
        )
        val source = requested?.takeIf { it.width() >= width / 5 && it.height() >= height / 12 } ?: fallback
        return Rect(
            source.left.coerceIn(0, width - 2),
            source.top.coerceIn(0, height - 2),
            source.right.coerceIn(source.left + 1, width),
            source.bottom.coerceIn(source.top + 1, height),
        )
    }

    private fun prune() {
        directory.listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_STORED_IMAGES)
            ?.forEach(File::delete)
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private companion object {
        const val MAX_STORED_IMAGES = 40
    }
}

data class StoredRideCardImage(val path: String, val bounds: Rect)
