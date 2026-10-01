package br.com.mapeiaia.rotacerta

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

object OfflineMapFilePolicy0708 {
    const val CONTRACT_MARKER = "OFFLINE_MAP_IMPORT_0708"
    const val STORAGE_MARKER = "OFFLINE_MAP_APP_PRIVATE_STORAGE_0708"
    const val NO_FAROL_AUTHORITY_MARKER = "OFFLINE_MAPS_DO_NOT_CHANGE_FAROL_AUTHORITY_0708"
    const val ORGANIC_MAPS_STORAGE_COMPAT_MARKER_0711 = "OFFLINE_MAPS_ORGANIC_STORAGE_COMPAT_0711"

    fun isSupportedName(name: String): Boolean =
        name.trim().lowercase(Locale.ROOT).endsWith(".mwm")

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace('/', '_')
            .replace(':', '_')
            .replace('*', '_')
            .replace('?', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('|', '_')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
        return cleaned.ifBlank { "mapa.mwm" }
    }
}

data class OfflineMapFile0708(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

data class OfflineMapImportResult0708(
    val accepted: Boolean,
    val file: OfflineMapFile0708? = null,
    val reason: String,
)

class OfflineMapStore0708(context: Context) {
    private val appContext = context.applicationContext
    private val legacyDirectory = File(appContext.filesDir, "offline_maps")
    private val storageRoot = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    private val directory = File(
        storageRoot,
        OrganicMapsEmbeddedRuntime0711.COMPATIBLE_DATA_VERSION_FOLDER,
    )

    init {
        migrateLegacyMaps0711()
    }

    fun listMaps(): List<OfflineMapFile0708> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && OfflineMapFilePolicy0708.isSupportedName(it.name) }
            .map { OfflineMapFile0708(it.name, it.length(), it.lastModified()) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun import(uri: Uri): OfflineMapImportResult0708 {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
            ?.trim()
            .orEmpty()
            .ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }

        if (!OfflineMapFilePolicy0708.isSupportedName(displayName)) {
            return OfflineMapImportResult0708(false, reason = "Arquivo ignorado: selecione um mapa regional com final .mwm.")
        }

        val safeName = OfflineMapFilePolicy0708.sanitizeFileName(displayName)
        directory.mkdirs()
        if (!directory.isDirectory) {
            return OfflineMapImportResult0708(false, reason = "Não foi possível preparar a pasta privada de mapas.")
        }

        val target = File(directory, safeName)
        val temporary = File(directory, "." + safeName + "." + System.nanoTime() + ".part")

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).buffered(1024 * 1024).use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                    output.flush()
                }
            } ?: error("Não foi possível abrir o arquivo selecionado.")
            check(temporary.length() > 0L) { "O arquivo selecionado está vazio." }
            moveReplacing(temporary, target)
            val imported = OfflineMapFile0708(target.name, target.length(), target.lastModified())
            OfflineMapImportResult0708(true, imported, "Mapa importado.")
        }.getOrElse { error ->
            temporary.delete()
            OfflineMapImportResult0708(false, reason = error.message ?: "Falha ao importar mapa.")
        }
    }

    fun remove(name: String): Boolean {
        val safe = OfflineMapFilePolicy0708.sanitizeFileName(name)
        if (safe != name || !OfflineMapFilePolicy0708.isSupportedName(safe)) return false
        val file = File(directory, safe)
        return !file.exists() || file.delete()
    }

    private fun migrateLegacyMaps0711() {
        if (!legacyDirectory.isDirectory) return
        directory.mkdirs()
        legacyDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && OfflineMapFilePolicy0708.isSupportedName(it.name) }
            .forEach { legacy ->
                val target = File(directory, OfflineMapFilePolicy0708.sanitizeFileName(legacy.name))
                if (target.exists()) return@forEach
                runCatching { moveReplacing(legacy, target) }
            }
        runCatching {
            if (legacyDirectory.listFiles().orEmpty().isEmpty()) legacyDirectory.delete()
        }
    }

    private fun moveReplacing(source: File, target: File) {
        val atomic = runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.getOrDefault(false)
        if (atomic) return

        if (target.exists() && !target.delete()) error("Não foi possível substituir o mapa existente.")
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                target.outputStream().buffered(1024 * 1024).use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }
            check(source.delete()) { "O arquivo temporário não pôde ser removido." }
        }
    }
}
