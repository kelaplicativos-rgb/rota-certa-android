package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Captura complementar vinculada a um pacote autorizado. Nunca participa da decisão do farol. */
data class ManualAppScreenCapture(
    val id: String,
    val packageName: String,
    val textPreview: String,
    val imagePath: String?,
    val createdAtMillis: Long,
)

object ManualAppScreenCaptureStore {
    private const val PREFS = "manual_app_screen_capture_146"
    private const val KEY_ITEMS = "items"
    private const val LEGACY_PREFS = "manual_app_screen_capture_138"

    fun save(context: Context, packageName: String, text: String, bitmap: Bitmap?): ManualAppScreenCapture {
        val normalizedPackage = SelectedRideAppStore.normalize(packageName)
            ?: error("Pacote inválido para captura")
        val directory = File(context.filesDir, "manual-captures/$normalizedPackage").apply { mkdirs() }
        val created = System.currentTimeMillis()
        val id = "$created-${UUID.randomUUID()}"
        val image = bitmap?.let {
            File(directory, "$id.png").also { output ->
                output.outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            }
        }
        val capture = ManualAppScreenCapture(
            id = id,
            packageName = normalizedPackage,
            textPreview = text.trim().take(4_000),
            imagePath = image?.absolutePath,
            createdAtMillis = created,
        )
        writeAll(context, readAll(context) + capture)
        return capture
    }

    fun read(context: Context): ManualAppScreenCapture? = readAll(context).maxByOrNull { it.createdAtMillis }

    fun readAll(context: Context): List<ManualAppScreenCapture> {
        migrateLegacy(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ManualAppScreenCapture(
                            id = item.getString("id"),
                            packageName = item.getString("packageName"),
                            textPreview = item.optString("textPreview"),
                            imagePath = item.optString("imagePath").takeIf { it.isNotBlank() },
                            createdAtMillis = item.optLong("createdAtMillis"),
                        ),
                    )
                }
            }.sortedByDescending { it.createdAtMillis }
        }.getOrDefault(emptyList())
    }

    fun readForPackage(context: Context, packageName: String): List<ManualAppScreenCapture> {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return emptyList()
        return readAll(context).filter { it.packageName == normalized }
    }

    fun remove(context: Context, id: String) {
        val current = readAll(context)
        current.firstOrNull { it.id == id }?.imagePath?.let { runCatching { File(it).delete() } }
        writeAll(context, current.filterNot { it.id == id })
    }

    fun removePackage(context: Context, packageName: String) {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        val current = readAll(context)
        current.filter { it.packageName == normalized }.forEach { capture ->
            capture.imagePath?.let { runCatching { File(it).delete() } }
        }
        writeAll(context, current.filterNot { it.packageName == normalized })
    }

    fun clear(context: Context) {
        readAll(context).forEach { it.imagePath?.let { path -> runCatching { File(path).delete() } } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun writeAll(context: Context, captures: List<ManualAppScreenCapture>) {
        val array = JSONArray()
        captures.distinctBy { it.id }.sortedByDescending { it.createdAtMillis }.forEach { capture ->
            array.put(JSONObject().apply {
                put("id", capture.id)
                put("packageName", capture.packageName)
                put("textPreview", capture.textPreview)
                put("imagePath", capture.imagePath.orEmpty())
                put("createdAtMillis", capture.createdAtMillis)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun migrateLegacy(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val packageName = legacy.getString("package", null)?.takeIf { it.isNotBlank() } ?: return
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        val existingRaw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
        if (existingRaw == null) {
            val capture = ManualAppScreenCapture(
                id = "legacy-${legacy.getLong("created", System.currentTimeMillis())}",
                packageName = normalized,
                textPreview = legacy.getString("text", "").orEmpty(),
                imagePath = legacy.getString("image", null),
                createdAtMillis = legacy.getLong("created", System.currentTimeMillis()),
            )
            writeAll(context, listOf(capture))
        }
        legacy.edit().clear().apply()
    }
}
