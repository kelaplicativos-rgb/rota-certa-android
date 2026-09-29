package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.serialization.Serializable

@Serializable
data class RideApkDossier0700(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val apkSha256: String,
    val fileSizeBytes: Long,
    val relevantEntries: List<String>,
    val relevantStrings: List<String>,
    val extractionVersion: Int = 1,
) {
    fun toModelDossier(): String = buildString {
        appendLine("contract=RIDE_APK_STATIC_DOSSIER_0700")
        appendLine("package=$packageName")
        appendLine("label=$appLabel")
        appendLine("versionName=$versionName")
        appendLine("versionCode=$versionCode")
        appendLine("apkSha256=$apkSha256")
        appendLine("fileSizeBytes=$fileSizeBytes")
        appendLine("entries:")
        relevantEntries.take(350).forEach { appendLine("- ${it.take(240)}") }
        appendLine("semantic_strings:")
        relevantStrings.take(500).forEach { appendLine("- ${it.take(260)}") }
    }.take(MAX_DOSSIER_CHARS)

    companion object {
        const val MAX_DOSSIER_CHARS = 38_000
    }
}

object RideApkAnalyzer0700 {
    const val CONTRACT_MARKER = "RIDE_APK_STATIC_ANALYZER_0700"
    const val NO_EXECUTION_MARKER = "IMPORTED_APK_NEVER_EXECUTED_0700"
    const val DOSSIER_ONLY_MARKER = "ONLY_REDUCED_SEMANTIC_DOSSIER_SENT_0700"
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val MAX_DEX_BYTES_TOTAL = 120L * 1024L * 1024L
    private const val MAX_RELEVANT_STRINGS = 1_600
    private val cueRegex = Regex(
        "(?iu)(corrid|viagem|ride|trip|destin|destiny|destination|origem|origin|pickup|pick_up|dropoff|drop_off|fare|tarifa|valor|pre[cç]o|dist[aâ]nc|km|aceit|accept|recus|reject|motorista|driver|taxi|moto|passage|passenger|request|offer|oferta|embarque|desembarque|chegada|partida)",
    )

    suspend fun analyzeUri(context: Context, uri: Uri): RideApkDossier0700 {
        val temp = File(context.cacheDir, "ride-learning-${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_APK_BYTES) { "APK excede o limite de 250 MB." }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Não foi possível abrir o APK.")
        return try {
            analyzeFile(context, temp)
        } finally {
            temp.delete()
        }
    }

    suspend fun analyzeInstalled(context: Context, packageName: String): RideApkDossier0700 {
        val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        return analyzeFile(context, File(app.sourceDir))
    }

    fun launchableApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.asSequence().mapNotNull { info ->
            val pkg = info.activityInfo?.packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (!DriverAppPackagePolicy0162.isEligible(pkg, context.packageName)) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            label to pkg
        }.distinctBy { it.second }.sortedBy { it.first.lowercase(Locale.ROOT) }.toList()
    }

    internal fun analyzeFile(context: Context, apk: File): RideApkDossier0700 {
        require(apk.isFile && apk.length() in 1..MAX_APK_BYTES) { "APK inválido ou fora do limite." }
        val packageInfo = packageArchiveInfo(context.packageManager, apk)
            ?: error("O arquivo não possui um pacote Android válido.")
        val packageName = packageInfo.packageName?.trim().orEmpty()
        require(packageName.isNotBlank()) { "packageName ausente no APK." }

        val appLabel = packageInfo.applicationInfo?.let { info ->
            info.sourceDir = apk.absolutePath
            info.publicSourceDir = apk.absolutePath
            runCatching { context.packageManager.getApplicationLabel(info).toString() }.getOrNull()
        }.orEmpty().ifBlank { packageName }

        val relevantEntries = LinkedHashSet<String>()
        val relevantStrings = LinkedHashSet<String>()
        var dexBytes = 0L
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (isRelevantEntry(name)) relevantEntries += name.take(240)
                if (!entry.isDirectory && name.matches(Regex("classes(?:\\d+)?\\.dex"))) {
                    if (entry.size <= 0L || dexBytes + entry.size > MAX_DEX_BYTES_TOTAL) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    dexBytes += bytes.size
                    extractDexStrings(bytes).forEach { value ->
                        if (relevantStrings.size < MAX_RELEVANT_STRINGS && isRelevantSemanticEvidence(value)) {
                            relevantStrings += value.take(260)
                        }
                    }
                }
            }
        }
        return RideApkDossier0700(
            packageName = packageName,
            appLabel = appLabel.take(160),
            versionName = packageInfo.versionName.orEmpty().take(80),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            apkSha256 = sha256(apk),
            fileSizeBytes = apk.length(),
            relevantEntries = relevantEntries.sorted().take(600),
            relevantStrings = relevantStrings
                .sortedWith(compareBy<String> { !cueRegex.containsMatchIn(it) }.thenBy { it.length })
                .take(MAX_RELEVANT_STRINGS),
        )
    }

    internal fun isRelevantEntry(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return cueRegex.containsMatchIn(lower) ||
            (lower.startsWith("res/layout/") && listOf("card", "popup", "offer", "request").any(lower::contains))
    }

    internal fun isRelevantSemanticEvidence(value: String): Boolean {
        val v = value.trim()
        if (v.length !in 3..260) return false
        if (!v.any(Char::isLetter)) return false
        return cueRegex.containsMatchIn(v)
    }

    internal fun extractDexStrings(bytes: ByteArray): Sequence<String> = sequence {
        val dexMagic = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte())
        if (bytes.size < 112 || !bytes.copyOfRange(0, 3).contentEquals(dexMagic)) return@sequence
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = runCatching { buffer.getInt(56) }.getOrDefault(0)
        val table = runCatching { buffer.getInt(60) }.getOrDefault(0)
        if (count !in 1..1_000_000 || table < 0 || table.toLong() + count.toLong() * 4L > bytes.size) return@sequence
        for (index in 0 until count) {
            val offset = runCatching { buffer.getInt(table + index * 4) }.getOrDefault(-1)
            if (offset !in 0 until bytes.size) continue
            val start = skipUleb128(bytes, offset)
            if (start !in 0 until bytes.size) continue
            var end = start
            while (end < bytes.size && bytes[end].toInt() != 0 && end - start <= 512) end += 1
            if (end <= start || end - start > 512) continue
            val value = runCatching { String(bytes, start, end - start, Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
            if (value.isNotBlank()) yield(value)
        }
    }

    private fun skipUleb128(bytes: ByteArray, offset: Int): Int {
        var pos = offset
        var count = 0
        while (pos < bytes.size && count < 5) {
            val b = bytes[pos].toInt() and 0xff
            pos += 1
            count += 1
            if ((b and 0x80) == 0) return pos
        }
        return -1
    }

    private fun packageArchiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
