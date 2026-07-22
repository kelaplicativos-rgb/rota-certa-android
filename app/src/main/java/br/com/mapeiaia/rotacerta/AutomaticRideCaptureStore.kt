package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class AutomaticRideCapture(
    val id: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val packageName: String,
    val imageFileName: String,
    val textHash: Int,
    val textPreview: String,
    val pickup: String? = null,
    val destination: String? = null,
    val fare: String? = null,
)

object AutomaticRideCapturePolicy {
    const val RETENTION_DAYS = 14
    const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1_000L
    const val MAX_CAPTURES = 30

    fun normalizedTextHash(text: String): Int = text
        .lines()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .hashCode()

    fun isExpired(capture: AutomaticRideCapture, nowMillis: Long): Boolean =
        capture.expiresAtMillis <= nowMillis || nowMillis < capture.createdAtMillis

    fun isDuplicate(
        existing: AutomaticRideCapture,
        packageName: String,
        textHash: Int,
    ): Boolean = existing.packageName.equals(packageName, ignoreCase = true) &&
        existing.textHash == textHash
}

/**
 * Armazena somente capturas de cards já confirmados por modelo manual do mesmo app.
 * As imagens ficam em filesDir, invisíveis à galeria e a outros aplicativos.
 */
class AutomaticRideCaptureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val directory = File(appContext.filesDir, CAPTURE_DIRECTORY).apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
            .apply()
    }

    fun capturesFlow(): Flow<List<AutomaticRideCapture>> = callbackFlow {
        trySend(list())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_METADATA || key == KEY_CHANGED_AT || key == KEY_ENABLED) {
                trySend(list())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun list(nowMillis: Long = System.currentTimeMillis()): List<AutomaticRideCapture> = synchronized(lock) {
        pruneLocked(nowMillis)
    }

    fun imageFile(capture: AutomaticRideCapture): File = File(directory, capture.imageFileName)

    suspend fun saveConfirmedCard(
        bitmap: Bitmap,
        packageName: String,
        text: String,
        fields: RideFields,
        nowMillis: Long = System.currentTimeMillis(),
    ): AutomaticRideCapture? = withContext(Dispatchers.IO) {
        if (!isEnabled() || text.isBlank() || packageName.isBlank()) return@withContext null
        val normalizedPackage = packageName.trim().lowercase(Locale.ROOT)
        val textHash = AutomaticRideCapturePolicy.normalizedTextHash(text)
        synchronized(lock) {
            val active = pruneLocked(nowMillis)
            active.firstOrNull { AutomaticRideCapturePolicy.isDuplicate(it, normalizedPackage, textHash) }
        }?.let { return@withContext it }

        val id = "auto-${nowMillis}-${abs(textHash)}"
        val fileName = "$id.jpg"
        val imageFile = File(directory, fileName)
        val scaled = scaleForStorage(bitmap)
        val written = runCatching {
            FileOutputStream(imageFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        }.getOrDefault(false)
        if (scaled !== bitmap) scaled.recycle()
        if (!written || !imageFile.exists() || imageFile.length() <= 0L) {
            imageFile.delete()
            return@withContext null
        }

        val capture = AutomaticRideCapture(
            id = id,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + AutomaticRideCapturePolicy.RETENTION_MILLIS,
            packageName = normalizedPackage,
            imageFileName = fileName,
            textHash = textHash,
            textPreview = text.trim().take(MAX_TEXT_PREVIEW),
            pickup = fields.pickup?.trim()?.takeIf { it.isNotBlank() },
            destination = fields.destination?.trim()?.takeIf { it.isNotBlank() },
            fare = fields.fare?.trim()?.takeIf { it.isNotBlank() },
        )

        synchronized(lock) {
            val active = pruneLocked(nowMillis)
            val duplicate = active.firstOrNull {
                AutomaticRideCapturePolicy.isDuplicate(it, normalizedPackage, textHash)
            }
            if (duplicate != null) {
                imageFile.delete()
                return@synchronized duplicate
            }
            val updated = (listOf(capture) + active)
                .distinctBy { it.id }
                .take(AutomaticRideCapturePolicy.MAX_CAPTURES)
            persistLocked(updated)
            removeUnreferencedImagesLocked(updated)
            capture
        }
    }

    fun delete(captureId: String): Boolean = synchronized(lock) {
        val current = decodeMetadata()
        val removed = current.firstOrNull { it.id == captureId } ?: return@synchronized false
        val updated = current.filterNot { it.id == captureId }
        imageFile(removed).delete()
        persistLocked(updated)
        true
    }

    fun clearAll() = synchronized(lock) {
        directory.listFiles()?.forEach(File::delete)
        persistLocked(emptyList())
    }

    fun cleanupExpired(nowMillis: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val before = decodeMetadata().size
        val after = pruneLocked(nowMillis).size
        before - after
    }

    private fun pruneLocked(nowMillis: Long): List<AutomaticRideCapture> {
        val current = decodeMetadata()
        val active = current.filter { capture ->
            !AutomaticRideCapturePolicy.isExpired(capture, nowMillis) && imageFile(capture).isFile
        }
        if (active.size != current.size) persistLocked(active)
        removeUnreferencedImagesLocked(active)
        return active.sortedByDescending { it.createdAtMillis }
    }

    private fun decodeMetadata(): List<AutomaticRideCapture> = runCatching {
        json.decodeFromString<List<AutomaticRideCapture>>(prefs.getString(KEY_METADATA, "").orEmpty())
    }.getOrDefault(emptyList())

    private fun persistLocked(captures: List<AutomaticRideCapture>) {
        prefs.edit()
            .putString(KEY_METADATA, json.encodeToString(captures))
            .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun removeUnreferencedImagesLocked(captures: List<AutomaticRideCapture>) {
        val referenced = captures.mapTo(hashSetOf()) { it.imageFileName }
        directory.listFiles()?.forEach { file ->
            if (file.name !in referenced) file.delete()
        }
    }

    private fun scaleForStorage(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toDouble() / largest.toDouble()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val PREFS_NAME = "automatic_ride_capture_v129"
        const val KEY_ENABLED = "enabled"
        const val KEY_METADATA = "metadata"
        const val KEY_CHANGED_AT = "changed_at"
        const val CAPTURE_DIRECTORY = "automatic_ride_card_captures"
        const val JPEG_QUALITY = 82
        const val MAX_IMAGE_DIMENSION = 1280
        const val MAX_TEXT_PREVIEW = 5000
    }
}
