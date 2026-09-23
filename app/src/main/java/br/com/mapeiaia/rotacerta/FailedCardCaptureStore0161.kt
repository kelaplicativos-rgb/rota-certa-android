package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class FailedCardLayoutModelStore0161(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modelsFor(packageName: String): List<FailedCardLayoutModel0161> = prefs
        .getStringSet(KEY_MODELS, emptySet())
        .orEmpty()
        .mapNotNull(::decode)
        .filter { it.packageName == packageName }
        .sortedByDescending { it.confidence }
        .take(MAX_MODELS_PER_PACKAGE)

    fun saveCandidate(model: FailedCardLayoutModel0161) {
        if (model.confidence < 90 || model.packageName.isBlank() || model.structureKey.isBlank()) return
        val existing = prefs.getStringSet(KEY_MODELS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .filterNot {
                it.packageName == model.packageName &&
                    it.structureKey == model.structureKey &&
                    it.originMarker == model.originMarker &&
                    it.destinationMarker == model.destinationMarker
            }
            .toMutableList()
        existing.add(0, model)
        val bounded = existing
            .groupBy { it.packageName }
            .flatMap { (_, models) -> models.take(MAX_MODELS_PER_PACKAGE) }
            .take(MAX_TOTAL_MODELS)
            .map(::encode)
            .toSet()
        prefs.edit().putStringSet(KEY_MODELS, bounded).apply()
    }

    private fun encode(model: FailedCardLayoutModel0161): String = listOf(
        model.packageName,
        model.originMarker,
        model.destinationMarker,
        model.originOffset.toString(),
        model.destinationOffset.toString(),
        model.structureKey,
        model.confidence.toString(),
    ).joinToString("|") { part -> URLEncoder.encode(part, StandardCharsets.UTF_8.name()) }

    private fun decode(value: String): FailedCardLayoutModel0161? = runCatching {
        val parts = value.split('|').map { part -> URLDecoder.decode(part, StandardCharsets.UTF_8.name()) }
        if (parts.size != 7) return@runCatching null
        FailedCardLayoutModel0161(
            packageName = parts[0],
            originMarker = parts[1],
            destinationMarker = parts[2],
            originOffset = parts[3].toInt(),
            destinationOffset = parts[4].toInt(),
            structureKey = parts[5],
            confidence = parts[6].toInt(),
        )
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "failed_card_layout_models_0161"
        private const val KEY_MODELS = "models"
        private const val MAX_MODELS_PER_PACKAGE = 4
        private const val MAX_TOTAL_MODELS = 12
    }
}

data class FailedCardTechnicalSnapshot0161(
    val signature: String,
    val packageName: String,
    val windowId: Int,
    val createdAtMillis: Long,
    val accessibilityText: String,
    val ocrText: String,
    val nodes: List<FailedCardNodeLine0161>,
    val recovered: Boolean,
    val recoveryStrategy: String?,
    val cardLeaseId: Long? = null,
    val destination: String? = null,
    val confidence: Int? = null,
    val distanceKm: Double? = null,
    val result: String? = null,
)

data class FarolCaptureRecord0161(
    val id: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val packageName: String,
    val cardLeaseId: Long?,
    val destination: String?,
    val confidence: Int?,
    val distanceKm: Double?,
    val result: String?,
    val recovered: Boolean,
    val imagePath: String?,
    val textPath: String,
)

/** Private, bounded diagnostic storage. No permission, server, database or background worker. */
object FailedCardTechnicalCaptureStore0161 {
    private const val DIRECTORY = "failed-card-captures-0161"
    private const val MAX_CAPTURES = 80
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L

    fun save(
        context: Context,
        snapshot: FailedCardTechnicalSnapshot0161,
        bitmap: Bitmap?,
    ) {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return
        trim(directory, snapshot.createdAtMillis)
        val baseName = "${snapshot.createdAtMillis}-${snapshot.signature.take(24)}"
        val textFile = File(directory, "$baseName.txt")
        val tempText = File(directory, "$baseName.txt.tmp")
        tempText.writeText(buildText(snapshot), Charsets.UTF_8)
        if (!tempText.renameTo(textFile)) {
            textFile.writeText(tempText.readText(Charsets.UTF_8), Charsets.UTF_8)
            tempText.delete()
        }
        if (bitmap != null && !bitmap.isRecycled) {
            val imageFile = File(directory, "$baseName.jpg")
            runCatching {
                imageFile.outputStream().buffered().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 68, output)
                }
            }.onFailure { imageFile.delete() }
        }
        trim(directory, snapshot.createdAtMillis)
    }

    private fun buildText(snapshot: FailedCardTechnicalSnapshot0161): String = buildString {
        appendLine("ROTA CERTA FAILED CARD CAPTURE 0.1.161")
        appendLine("signature=${snapshot.signature}")
        appendLine("package=${snapshot.packageName}")
        appendLine("window=${snapshot.windowId}")
        appendLine("createdAt=${snapshot.createdAtMillis}")
        appendLine("recovered=${snapshot.recovered}")
        appendLine("strategy=${snapshot.recoveryStrategy.orEmpty()}")
        appendLine("cardLeaseId=${snapshot.cardLeaseId ?: -1L}")
        appendLine("destination=${sanitize(snapshot.destination.orEmpty())}")
        appendLine("confidence=${snapshot.confidence ?: -1}")
        appendLine("distanceKm=${snapshot.distanceKm ?: -1.0}")
        appendLine("result=${sanitize(snapshot.result.orEmpty())}")
        appendLine("expiresAt=${snapshot.createdAtMillis + MAX_AGE_MILLIS}")
        appendLine("--- ACCESSIBILITY ---")
        appendLine(redactPhone(snapshot.accessibilityText).take(12_000))
        appendLine("--- OCR ---")
        appendLine(redactPhone(snapshot.ocrText).take(12_000))
        appendLine("--- NODES ---")
        snapshot.nodes.take(160).forEach { node ->
            appendLine(
                listOf(
                    node.top,
                    node.left,
                    node.bottom,
                    node.right,
                    sanitize(node.className),
                    sanitize(node.viewId),
                    redactPhone(node.text),
                ).joinToString("\t"),
            )
        }
    }

    fun list(context: Context, nowMillis: Long = System.currentTimeMillis()): List<FarolCaptureRecord0161> {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.exists()) return emptyList()
        trim(directory, nowMillis)
        return directory.listFiles().orEmpty()
            .filter { it.extension == "txt" }
            .mapNotNull { textFile ->
                runCatching {
                    val header = textFile.useLines { lines ->
                        lines.takeWhile { !it.startsWith("--- ") }
                            .mapNotNull { line ->
                                val idx = line.indexOf('=')
                                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
                            }.toMap()
                    }
                    val created = header["createdAt"]?.toLongOrNull() ?: textFile.lastModified()
                    val base = textFile.nameWithoutExtension
                    FarolCaptureRecord0161(
                        id = base,
                        createdAtMillis = created,
                        expiresAtMillis = header["expiresAt"]?.toLongOrNull() ?: (created + MAX_AGE_MILLIS),
                        packageName = header["package"].orEmpty(),
                        cardLeaseId = header["cardLeaseId"]?.toLongOrNull()?.takeIf { it >= 0L },
                        destination = header["destination"]?.takeIf(String::isNotBlank),
                        confidence = header["confidence"]?.toIntOrNull()?.takeIf { it >= 0 },
                        distanceKm = header["distanceKm"]?.toDoubleOrNull()?.takeIf { it >= 0.0 },
                        result = header["result"]?.takeIf(String::isNotBlank),
                        recovered = header["recovered"].toBoolean(),
                        imagePath = File(directory, "${base}.jpg").takeIf(File::isFile)?.absolutePath,
                        textPath = textFile.absolutePath,
                    )
                }.getOrNull()
            }
            .sortedByDescending(FarolCaptureRecord0161::createdAtMillis)
            .take(MAX_CAPTURES)
    }

    fun remove(context: Context, id: String) {
        val safe = id.substringAfterLast('/').substringAfterLast('\\')
        val directory = File(context.filesDir, DIRECTORY)
        listOf("txt", "jpg").forEach { ext -> runCatching { File(directory, "${safe}.${ext}").delete() } }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIRECTORY).listFiles().orEmpty().forEach { runCatching { it.delete() } }
    }

    fun cleanup(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val directory = File(context.filesDir, DIRECTORY)
        if (directory.exists()) trim(directory, nowMillis)
    }

    private fun sanitize(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .trim()
        .take(240)

    private fun redactPhone(value: String): String = PHONE_REGEX.replace(value) { "[telefone mascarado]" }

    private fun trim(directory: File, nowMillis: Long) {
        val files = directory.listFiles().orEmpty().toList()
        files.filter { file ->
            nowMillis >= file.lastModified() && nowMillis - file.lastModified() > MAX_AGE_MILLIS
        }.forEach(File::delete)

        val captureGroups = directory.listFiles().orEmpty()
            .groupBy { file -> file.name.substringBeforeLast('.') }
            .entries
            .sortedByDescending { (_, groupFiles) -> groupFiles.maxOfOrNull(File::lastModified) ?: 0L }
        captureGroups.drop(MAX_CAPTURES).flatMap { it.value }.forEach(File::delete)
    }

    private val PHONE_REGEX = Regex(
        "(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?(?:9\\s*)?\\d{4}[\\s-]?\\d{4}(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
}
