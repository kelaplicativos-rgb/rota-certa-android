package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID

@Serializable
data class AutomaticRideCapture(
    val id: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val packageName: String,
    val textHash: Int,
    val text: String,
    val imageFileName: String,
    val pickup: String? = null,
    val destination: String? = null,
    val fare: String? = null,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}

/**
 * Armazena apenas capturas de ofertas detectadas em aplicativos escolhidos pelo usuario.
 *
 * As imagens ficam no armazenamento privado do aplicativo, nao entram na galeria e sao
 * eliminadas automaticamente. A gravacao deve ser chamada em Dispatchers.IO para nunca
 * disputar tempo com leitura, match, geocodificacao ou pintura da bolinha.
 */
class AutomaticRideCaptureStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val captureDirectory = File(appContext.filesDir, DIRECTORY_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    fun list(nowMillis: Long = System.currentTimeMillis()): List<AutomaticRideCapture> {
        val current = readUnsafe()
        val retained = purgeExpiredUnsafe(current, nowMillis)
        if (retained.size != current.size) writeUnsafe(retained)
        return retained.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun save(
        bitmap: Bitmap,
        packageName: String,
        text: String,
        pickup: String?,
        destination: String?,
        fare: String?,
        nowMillis: Long = System.currentTimeMillis(),
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
    ): AutomaticRideCapture? {
        val normalizedPackage = packageName.trim().lowercase(Locale.ROOT)
        val normalizedText = text.normalizedCaptureText()
        if (normalizedPackage.isBlank() || normalizedText.length < MIN_TEXT_LENGTH) return null

        captureDirectory.mkdirs()
        val textHash = normalizedText.hashCode()
        val current = purgeExpiredUnsafe(readUnsafe(), nowMillis).toMutableList()
        val duplicate = current.firstOrNull { capture ->
            capture.packageName == normalizedPackage &&
                capture.textHash == textHash &&
                nowMillis >= capture.createdAtMillis &&
                nowMillis - capture.createdAtMillis <= DUPLICATE_WINDOW_MILLIS
        }
        if (duplicate != null) {
            writeUnsafe(current)
            return duplicate
        }

        val id = "auto-${nowMillis}-${UUID.randomUUID()}"
        val fileName = "$id.jpg"
        val imageFile = File(captureDirectory, fileName)
        val wroteImage = runCatching {
            imageFile.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        }.getOrDefault(false)
        if (!wroteImage || !imageFile.exists() || imageFile.length() <= 0L) {
            imageFile.delete()
            return null
        }

        val safeRetentionDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        val capture = AutomaticRideCapture(
            id = id,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + safeRetentionDays * DAY_MILLIS,
            packageName = normalizedPackage,
            textHash = textHash,
            text = text.trim().take(MAX_TEXT_LENGTH),
            imageFileName = fileName,
            pickup = pickup?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_FIELD_LENGTH),
            destination = destination?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_FIELD_LENGTH),
            fare = fare?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_FIELD_LENGTH),
        )
        current += capture

        val retained = current
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_CAPTURE_COUNT)
        val retainedIds = retained.mapTo(hashSetOf()) { it.id }
        current.filterNot { it.id in retainedIds }.forEach(::deleteImageUnsafe)
        writeUnsafe(retained)
        return capture
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = readUnsafe()
        val removed = current.firstOrNull { it.id == id } ?: return false
        deleteImageUnsafe(removed)
        writeUnsafe(current.filterNot { it.id == id })
        return true
    }

    @Synchronized
    fun purgeExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        val current = readUnsafe()
        val retained = purgeExpiredUnsafe(current, nowMillis)
        val removedCount = current.size - retained.size
        if (removedCount > 0) writeUnsafe(retained)
        return removedCount
    }

    fun imageFile(capture: AutomaticRideCapture): File = File(captureDirectory, capture.imageFileName)

    private fun purgeExpiredUnsafe(
        captures: List<AutomaticRideCapture>,
        nowMillis: Long,
    ): List<AutomaticRideCapture> {
        val expired = captures.filter { it.isExpired(nowMillis) || !imageFile(it).exists() }
        expired.forEach(::deleteImageUnsafe)
        return captures.filterNot { it in expired }
    }

    private fun deleteImageUnsafe(capture: AutomaticRideCapture) {
        runCatching { imageFile(capture).delete() }
    }

    private fun readUnsafe(): List<AutomaticRideCapture> {
        val payload = preferences.getString(KEY_CAPTURES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AutomaticRideCapture>>(payload) }
            .getOrDefault(emptyList())
    }

    private fun writeUnsafe(captures: List<AutomaticRideCapture>) {
        preferences.edit().putString(KEY_CAPTURES, json.encodeToString(captures)).commit()
    }

    private fun String.normalizedCaptureText(): String =
        lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .lowercase(Locale.ROOT)

    companion object {
        const val DEFAULT_RETENTION_DAYS = 14
        const val MAX_CAPTURE_COUNT = 80
        private const val MIN_RETENTION_DAYS = 1
        private const val MAX_RETENTION_DAYS = 30
        private const val MIN_TEXT_LENGTH = 24
        private const val MAX_TEXT_LENGTH = 12_000
        private const val MAX_FIELD_LENGTH = 500
        private const val JPEG_QUALITY = 84
        private const val DUPLICATE_WINDOW_MILLIS = 90_000L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        private const val DIRECTORY_NAME = "automatic_ride_cards"
        private const val PREFS_NAME = "automatic_ride_capture_store_v1"
        private const val KEY_CAPTURES = "captures_json"
    }
}
