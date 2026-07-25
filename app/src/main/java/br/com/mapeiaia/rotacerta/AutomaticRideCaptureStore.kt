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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Serializable
enum class AutomaticRideCaptureKind {
    /** Oferta reconhecida, mas ainda sem modelo manual correspondente. */
    Candidate,

    /** Card que já corresponde a um modelo manual do mesmo aplicativo. */
    Matched,
}

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
    // O padrão Matched preserva corretamente metadados gravados pela 0.1.129.
    val kind: AutomaticRideCaptureKind = AutomaticRideCaptureKind.Matched,
    val semanticHash: Int? = null,
    val matchedTemplateId: String? = null,
    val matchedTemplateName: String? = null,
)

object AutomaticRideCapturePolicy {
    const val RETENTION_DAYS = 14
    const val CANDIDATE_RETENTION_DAYS = 7
    const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1_000L
    const val CANDIDATE_RETENTION_MILLIS = CANDIDATE_RETENTION_DAYS * 24L * 60L * 60L * 1_000L
    const val MAX_CAPTURES = 30
    const val MAX_CANDIDATES = 20
    const val MAX_MATCHED = 10

    fun normalizedTextHash(text: String): Int = normalize(text).hashCode()

    /**
     * Duplicação sem preço, horário ou pequenas mudanças visuais. Quando existem
     * embarque e destino, a identidade passa a ser somente app + endereços.
     */
    fun semanticHash(packageName: String, text: String, fields: RideFields): Int {
        val normalizedPackage = normalize(packageName)
        val pickup = normalize(fields.pickup.orEmpty())
        val destination = normalize(fields.destination.orEmpty())
        val semantic = if (destination.isNotBlank()) {
            listOf(normalizedPackage, pickup, destination).joinToString("|")
        } else {
            val stableText = normalize(text)
                .replace(Regex("(?:r\\$|brl)\\s*\\d+[.,]?\\d*"), "<valor>")
                .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), "<hora>")
                .replace(Regex("\\b\\d+[.,]?\\d*\\s*(?:km|min|mins|minutos?)\\b"), "<medida>")
            "$normalizedPackage|$stableText"
        }
        return semantic.hashCode()
    }

    fun expiresAt(kind: AutomaticRideCaptureKind, createdAtMillis: Long): Long =
        createdAtMillis + if (kind == AutomaticRideCaptureKind.Candidate) {
            CANDIDATE_RETENTION_MILLIS
        } else {
            RETENTION_MILLIS
        }

    fun isExpired(capture: AutomaticRideCapture, nowMillis: Long): Boolean =
        capture.expiresAtMillis <= nowMillis || nowMillis < capture.createdAtMillis

    fun isDuplicate(
        existing: AutomaticRideCapture,
        packageName: String,
        textHash: Int,
    ): Boolean = existing.packageName.equals(packageName, ignoreCase = true) &&
        (existing.semanticHash ?: existing.textHash) == textHash

    fun isUseful(fields: RideFields, bitmapWidth: Int, bitmapHeight: Int): Boolean =
        !fields.destination.isNullOrBlank() &&
            bitmapWidth >= MIN_IMAGE_EDGE &&
            bitmapHeight >= MIN_IMAGE_EDGE

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MIN_IMAGE_EDGE = 180
}

/**
 * Armazena capturas privadas fora do caminho crítico do farol.
 *
 * Candidatas podem virar modelos manuais. Capturas reconhecidas servem somente
 * como histórico visual temporário e nunca exibem uma ação de criar outro modelo.
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

    fun candidates(nowMillis: Long = System.currentTimeMillis()): List<AutomaticRideCapture> =
        list(nowMillis).filter { it.kind == AutomaticRideCaptureKind.Candidate }

    fun matched(nowMillis: Long = System.currentTimeMillis()): List<AutomaticRideCapture> =
        list(nowMillis).filter { it.kind == AutomaticRideCaptureKind.Matched }

    fun imageFile(capture: AutomaticRideCapture): File = File(directory, capture.imageFileName)

    suspend fun saveCard(
        bitmap: Bitmap,
        packageName: String,
        text: String,
        fields: RideFields,
        kind: AutomaticRideCaptureKind,
        matchedTemplateId: String? = null,
        matchedTemplateName: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): AutomaticRideCapture? = withContext(Dispatchers.IO) {
        if (!isEnabled() || bitmap.isRecycled || text.isBlank() || packageName.isBlank()) return@withContext null
        if (!AutomaticRideCapturePolicy.isUseful(fields, bitmap.width, bitmap.height)) return@withContext null

        val normalizedPackage = packageName.trim().lowercase(Locale.ROOT)
        val semanticHash = AutomaticRideCapturePolicy.semanticHash(normalizedPackage, text, fields)
        val textHash = AutomaticRideCapturePolicy.normalizedTextHash(text)

        synchronized(lock) {
            val active = pruneLocked(nowMillis)
            val duplicate = active.firstOrNull {
                AutomaticRideCapturePolicy.isDuplicate(it, normalizedPackage, semanticHash)
            }
            if (duplicate != null) {
                if (duplicate.kind == AutomaticRideCaptureKind.Candidate && kind == AutomaticRideCaptureKind.Matched) {
                    val upgraded = duplicate.copy(
                        kind = AutomaticRideCaptureKind.Matched,
                        expiresAtMillis = AutomaticRideCapturePolicy.expiresAt(AutomaticRideCaptureKind.Matched, nowMillis),
                        matchedTemplateId = matchedTemplateId,
                        matchedTemplateName = matchedTemplateName,
                    )
                    persistLocked(active.map { if (it.id == duplicate.id) upgraded else it })
                    return@synchronized upgraded
                }
                return@synchronized duplicate
            }
            null
        }?.let { return@withContext it }

        val id = "auto-${nowMillis}-${abs(semanticHash)}"
        val fileName = "$id.jpg"
        val imageFile = File(directory, fileName)
        val temporaryFile = File(directory, "$fileName.tmp")
        val scaled = scaleForStorage(bitmap)
        val written = runCatching {
            FileOutputStream(temporaryFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.fd.sync()
            }
            temporaryFile.length() > 0L && temporaryFile.renameTo(imageFile)
        }.getOrDefault(false)
        if (scaled !== bitmap) scaled.recycle()
        temporaryFile.delete()
        if (!written || !imageFile.isFile || imageFile.length() <= 0L) {
            imageFile.delete()
            return@withContext null
        }

        val capture = AutomaticRideCapture(
            id = id,
            createdAtMillis = nowMillis,
            expiresAtMillis = AutomaticRideCapturePolicy.expiresAt(kind, nowMillis),
            packageName = normalizedPackage,
            imageFileName = fileName,
            textHash = textHash,
            semanticHash = semanticHash,
            textPreview = text.trim().take(MAX_TEXT_PREVIEW),
            pickup = fields.pickup?.trim()?.takeIf { it.isNotBlank() },
            destination = fields.destination?.trim()?.takeIf { it.isNotBlank() },
            fare = fields.fare?.trim()?.takeIf { it.isNotBlank() },
            kind = kind,
            matchedTemplateId = matchedTemplateId,
            matchedTemplateName = matchedTemplateName,
        )

        synchronized(lock) {
            val active = pruneLocked(nowMillis)
            val duplicate = active.firstOrNull {
                AutomaticRideCapturePolicy.isDuplicate(it, normalizedPackage, semanticHash)
            }
            if (duplicate != null) {
                imageFile.delete()
                return@synchronized duplicate
            }
            val updated = enforceLimits(listOf(capture) + active)
            persistLocked(updated)
            removeUnreferencedImagesLocked(updated)
            capture
        }
    }

    /** Compatibilidade com a 0.1.129: chamada agora classificada como reconhecida. */
    suspend fun saveConfirmedCard(
        bitmap: Bitmap,
        packageName: String,
        text: String,
        fields: RideFields,
        nowMillis: Long = System.currentTimeMillis(),
    ): AutomaticRideCapture? = saveCard(
        bitmap = bitmap,
        packageName = packageName,
        text = text,
        fields = fields,
        kind = AutomaticRideCaptureKind.Matched,
        nowMillis = nowMillis,
    )

    /** Após criar o modelo manual, a captura temporária deixa de ocupar espaço. */
    fun consumePromotedCandidate(captureId: String): Boolean = delete(captureId)

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
        val active = enforceLimits(
            current.filter { capture ->
                !AutomaticRideCapturePolicy.isExpired(capture, nowMillis) && imageFile(capture).isFile
            },
        )
        if (active.size != current.size || active != current) persistLocked(active)
        removeUnreferencedImagesLocked(active)
        return active.sortedByDescending { it.createdAtMillis }
    }

    private fun enforceLimits(captures: List<AutomaticRideCapture>): List<AutomaticRideCapture> {
        val ordered = captures.distinctBy { it.id }.sortedByDescending { it.createdAtMillis }
        val candidates = ordered.filter { it.kind == AutomaticRideCaptureKind.Candidate }
            .take(AutomaticRideCapturePolicy.MAX_CANDIDATES)
        val matched = ordered.filter { it.kind == AutomaticRideCaptureKind.Matched }
            .take(AutomaticRideCapturePolicy.MAX_MATCHED)
        val allowedIds = (candidates + matched)
            .sortedByDescending { it.createdAtMillis }
            .take(AutomaticRideCapturePolicy.MAX_CAPTURES)
            .mapTo(hashSetOf()) { it.id }
        return ordered.filter { it.id in allowedIds }
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
        const val JPEG_QUALITY = 80
        const val MAX_IMAGE_DIMENSION = 1080
        const val MAX_TEXT_PREVIEW = 5000
    }
}
