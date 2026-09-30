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
import java.util.zip.ZipEntry
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
    val extractionVersion: Int = 2,
) {
    fun toModelDossier(): String = buildString {
        appendLine("contract=RIDE_APK_STATIC_DOSSIER_0704")
        appendLine("package=$packageName")
        appendLine("label=$appLabel")
        appendLine("versionName=$versionName")
        appendLine("versionCode=$versionCode")
        appendLine("apkSha256=$apkSha256")
        appendLine("fileSizeBytes=$fileSizeBytes")
        appendLine("extractionVersion=$extractionVersion")
        appendLine("evidenceOrdering=relevance_ranked_across_all_archives")
        appendLine("entries:")
        relevantEntries.take(700).forEach { appendLine("- ${it.take(240)}") }
        appendLine("semantic_strings:")
        relevantStrings.take(2_400).forEach { appendLine("- ${it.take(260)}") }
    }.take(MAX_DOSSIER_CHARS)

    companion object {
        const val MAX_DOSSIER_CHARS = 48_000
    }
}

object RideApkAnalyzer0700 {
    const val CONTRACT_MARKER = "RIDE_APK_STATIC_ANALYZER_0700"
    const val V2_MARKER = "RIDE_APK_RELEVANCE_RANKING_0704"
    const val SPLIT_APK_MARKER = "RIDE_APK_BASE_AND_SPLITS_0704"
    const val TEXT_ASSET_MARKER = "RIDE_APK_TEXT_ASSETS_0704"
    const val NO_EXECUTION_MARKER = "IMPORTED_APK_NEVER_EXECUTED_0700"
    const val DOSSIER_ONLY_MARKER = "ONLY_REDUCED_SEMANTIC_DOSSIER_SENT_0700"

    // Safety ceiling for a single imported archive. Unlike 0.1.703 this is not a
    // product-size gate: analysis is streaming and does not reject normal 250+ MB driver APKs.
    private const val MAX_SINGLE_ARCHIVE_BYTES = 1_024L * 1024L * 1024L
    private const val MAX_DEX_ENTRY_BYTES = 96L * 1024L * 1024L
    private const val MAX_TEXT_ASSET_BYTES = 6L * 1024L * 1024L
    private const val MAX_RANKED_ENTRIES = 900
    private const val MAX_RANKED_STRINGS = 2_800
    private const val PRUNE_EVIDENCE_AT = 14_000

    private val cueRegex = Regex(
        "(?iu)(corrid|viagem|ride|trip|driver|motorista|order|pedido|request|solicita|offer|oferta|" +
            "destin|destiny|destination|drop.?off|endere[cç]|address|origem|origin|pickup|pick_up|" +
            "fare|tarifa|valor|pre[cç]o|price|dist[aâ]nc|km|aceit|accept|recus|reject|" +
            "passage|passenger|embarque|desembarque|chegada|partida)",
    )
    private val destinationRegex = Regex(
        "(?iu)(destin|destination|drop.?off|desembarque|chegada|endere[cç]o.?dest|to.?address)",
    )
    private val pickupRegex = Regex(
        "(?iu)(origem|origin|pickup|pick_up|embarque|partida|from.?address)",
    )
    private val actionRegex = Regex(
        "(?iu)(aceit|accept|recus|reject|offer|oferta|request|solicita|order|pedido)",
    )
    private val fareRegex = Regex(
        "(?iu)(fare|tarifa|valor|pre[cç]o|price|R\\$)",
    )
    private val genericLibraryRegex = Regex(
        "(?iu)(^androidx\\.|^kotlin\\.|^kotlinx\\.|^java\\.|^javax\\.|^com\\.google\\.|" +
            "^okhttp|^retrofit|^org\\.jetbrains\\.|^io\\.grpc\\.|^com\\.squareup\\.)",
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
                    require(total <= MAX_SINGLE_ARCHIVE_BYTES) {
                        "Arquivo Android excede o teto técnico de segurança de 1 GB."
                    }
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
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        val archives = buildList {
            app.sourceDir?.let(::File)?.takeIf(File::isFile)?.let(::add)
            app.splitSourceDirs.orEmpty()
                .map(::File)
                .filter(File::isFile)
                .forEach(::add)
        }.distinctBy { it.absolutePath }
        require(archives.isNotEmpty()) { "Instalação Android sem APK base acessível." }
        return analyzeFiles(context, archives)
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

    internal fun analyzeFile(context: Context, apk: File): RideApkDossier0700 =
        analyzeFiles(context, listOf(apk))

    internal fun analyzeFiles(context: Context, archives: List<File>): RideApkDossier0700 {
        val files = archives.distinctBy { it.absolutePath }
        require(files.isNotEmpty()) { "APK ausente." }
        files.forEach { apk ->
            require(apk.isFile && apk.length() in 1..MAX_SINGLE_ARCHIVE_BYTES) {
                "APK inválido ou fora do teto técnico de segurança."
            }
        }

        val baseApk = files.first()
        val packageInfo = packageArchiveInfo(context.packageManager, baseApk)
            ?: error("O arquivo não possui um pacote Android válido.")
        val packageName = packageInfo.packageName?.trim().orEmpty()
        require(packageName.isNotBlank()) { "packageName ausente no APK." }

        val appLabel = packageInfo.applicationInfo?.let { info ->
            info.sourceDir = baseApk.absolutePath
            info.publicSourceDir = baseApk.absolutePath
            runCatching { context.packageManager.getApplicationLabel(info).toString() }.getOrNull()
        }.orEmpty().ifBlank { packageName }

        val rankedEntries = EvidenceRanker0704(MAX_RANKED_ENTRIES)
        val rankedStrings = EvidenceRanker0704(MAX_RANKED_STRINGS)

        files.forEachIndexed { archiveIndex, apk ->
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (isRelevantEntry(name)) {
                        rankedEntries.offer(
                            value = if (archiveIndex == 0) name else "split[$archiveIndex]/$name",
                            score = scoreEntry0704(name),
                        )
                    }
                    if (entry.isDirectory) continue

                    if (name.matches(Regex("classes(?:\\d+)?\\.dex")) && safeEntrySize(entry) <= MAX_DEX_ENTRY_BYTES) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        extractDexStrings(bytes).forEach { value ->
                            if (isRelevantSemanticEvidence(value)) {
                                rankedStrings.offer(value.take(260), scoreSemanticEvidence0704(value, packageName))
                            }
                        }
                    }

                    if (isTextEvidenceAsset0704(name, entry)) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        extractTextAssetStrings0704(bytes).forEach { value ->
                            if (isRelevantSemanticEvidence(value)) {
                                rankedStrings.offer(value.take(260), scoreSemanticEvidence0704(value, packageName) + 18)
                            }
                        }
                    }
                }
            }
        }

        return RideApkDossier0700(
            packageName = packageName,
            appLabel = appLabel.take(160),
            versionName = packageInfo.versionName.orEmpty().take(80),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            apkSha256 = sha256Files0704(files),
            fileSizeBytes = files.sumOf(File::length),
            relevantEntries = rankedEntries.values(),
            relevantStrings = rankedStrings.values(),
            extractionVersion = 2,
        )
    }

    internal fun isRelevantEntry(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return cueRegex.containsMatchIn(lower) ||
            (lower.startsWith("res/layout/") &&
                listOf("card", "popup", "offer", "request", "order", "driver", "ride").any(lower::contains)) ||
            (lower.startsWith("assets/") &&
                listOf("pt_br", "pt-br", "locale", "l10n", "i18n", "translation", "strings").any(lower::contains))
    }

    internal fun isRelevantSemanticEvidence(value: String): Boolean {
        val v = value.trim()
        if (v.length !in 3..260) return false
        if (!v.any(Char::isLetter)) return false
        return cueRegex.containsMatchIn(v)
    }

    internal fun scoreSemanticEvidence0704(value: String, packageName: String): Int {
        val v = value.trim()
        val lower = v.lowercase(Locale.ROOT)
        var score = 10
        if (destinationRegex.containsMatchIn(v)) score += 90
        if (pickupRegex.containsMatchIn(v)) score += 82
        if (fareRegex.containsMatchIn(v)) score += 58
        if (actionRegex.containsMatchIn(v)) score += 54
        if (Regex("(?iu)(corrid|viagem|ride|trip|driver|motorista)").containsMatchIn(v)) score += 35
        if (Regex("(?iu)(address|endere[cç]|km|dist[aâ]nc)").containsMatchIn(v)) score += 28
        if (v.length <= 80) score += 16
        if (v.length <= 40) score += 8

        val packageTokens = packageName.lowercase(Locale.ROOT)
            .split('.')
            .filter { it.length >= 4 && it !in setOf("com", "android", "driver", "startup") }
        if (packageTokens.any(lower::contains)) score += 72
        if (lower.contains("driver")) score += 24
        if (genericLibraryRegex.containsMatchIn(lower)) score -= 85
        if (lower.contains('/') || lower.contains('_')) score += 6
        return score
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
            val value = runCatching {
                String(bytes, start, end - start, Charsets.UTF_8)
            }.getOrNull()?.trim().orEmpty()
            if (value.isNotBlank()) yield(value)
        }
    }

    internal fun extractTextAssetStrings0704(bytes: ByteArray): Sequence<String> = sequence {
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
        if (text.isBlank()) return@sequence

        val quoted = Regex("""["']([^"'\\]{3,260})["']""")
        quoted.findAll(text).forEach { match ->
            val value = match.groupValues.getOrNull(1)
                ?.replace("\\n", " ")
                ?.replace("\\t", " ")
                ?.trim()
                .orEmpty()
            if (value.isNotBlank()) yield(value)
        }
        text.lineSequence()
            .map { it.trim().trim(',', '{', '}', '[', ']') }
            .filter { it.length in 3..260 }
            .take(2_000)
            .forEach { yield(it) }
    }

    private fun isTextEvidenceAsset0704(name: String, entry: ZipEntry): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        val size = safeEntrySize(entry)
        if (size !in 1..MAX_TEXT_ASSET_BYTES) return false
        if (!(lower.endsWith(".json") || lower.endsWith(".arb") || lower.endsWith(".xml") || lower.endsWith(".txt"))) {
            return false
        }
        return lower.startsWith("assets/") &&
            (
                listOf("pt_br", "pt-br", "/pt/", "locale", "l10n", "i18n", "translation", "strings")
                    .any(lower::contains) ||
                    cueRegex.containsMatchIn(lower)
                )
    }

    private fun scoreEntry0704(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        var score = 10
        if (lower.startsWith("res/layout/")) score += 100
        if (destinationRegex.containsMatchIn(lower)) score += 90
        if (pickupRegex.containsMatchIn(lower)) score += 82
        if (actionRegex.containsMatchIn(lower)) score += 52
        if (fareRegex.containsMatchIn(lower)) score += 45
        if (listOf("driver", "ride", "trip", "order", "card", "offer", "request").any(lower::contains)) score += 38
        if (lower.startsWith("assets/")) score += 22
        if (listOf("pt_br", "pt-br", "locale", "l10n", "i18n", "translation").any(lower::contains)) score += 35
        return score
    }

    private class EvidenceRanker0704(private val maxItems: Int) {
        private data class Candidate(val display: String, val score: Int)
        private val byCanonical = LinkedHashMap<String, Candidate>()

        fun offer(value: String, score: Int) {
            val display = value.trim().replace(Regex("\\s+"), " ")
            if (display.isBlank()) return
            val key = display.lowercase(Locale.ROOT)
            val previous = byCanonical[key]
            if (previous == null || score > previous.score) {
                byCanonical[key] = Candidate(display, score)
            }
            if (byCanonical.size > PRUNE_EVIDENCE_AT) prune()
        }

        fun values(): List<String> = byCanonical.values
            .sortedWith(
                compareByDescending<Candidate> { it.score }
                    .thenBy { it.display.length }
                    .thenBy { it.display.lowercase(Locale.ROOT) },
            )
            .take(maxItems)
            .map(Candidate::display)

        private fun prune() {
            val keep = byCanonical.values
                .sortedByDescending(Candidate::score)
                .take(maxItems * 3)
            byCanonical.clear()
            keep.forEach { candidate ->
                byCanonical[candidate.display.lowercase(Locale.ROOT)] = candidate
            }
        }
    }

    private fun safeEntrySize(entry: ZipEntry): Long =
        entry.size.takeIf { it >= 0L } ?: entry.compressedSize.takeIf { it >= 0L } ?: Long.MAX_VALUE

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

    private fun sha256Files0704(files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.sortedBy(File::name).forEach { file ->
            digest.update(file.name.toByteArray(Charsets.UTF_8))
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
