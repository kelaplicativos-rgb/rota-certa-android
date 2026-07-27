package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Última captura feita explicitamente pelo usuário. Nunca participa da decisão do farol. */
data class ManualAppScreenCapture(
    val packageName: String,
    val textPreview: String,
    val imagePath: String?,
    val createdAtMillis: Long,
)

object ManualAppScreenCaptureStore {
    private const val PREFS = "manual_app_screen_capture_138"
    private const val KEY_PACKAGE = "package"
    private const val KEY_TEXT = "text"
    private const val KEY_IMAGE = "image"
    private const val KEY_CREATED = "created"

    fun save(context: Context, packageName: String, text: String, bitmap: Bitmap?): ManualAppScreenCapture {
        val directory = File(context.filesDir, "manual-captures").apply { mkdirs() }
        val image = bitmap?.let {
            File(directory, "latest-screen.png").also { output ->
                output.outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            }
        }
        val created = System.currentTimeMillis()
        val preview = text.trim().take(4_000)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PACKAGE, packageName)
            .putString(KEY_TEXT, preview)
            .putString(KEY_IMAGE, image?.absolutePath)
            .putLong(KEY_CREATED, created)
            .apply()
        return ManualAppScreenCapture(packageName, preview, image?.absolutePath, created)
    }

    fun read(context: Context): ManualAppScreenCapture? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packageName = prefs.getString(KEY_PACKAGE, null)?.takeIf { it.isNotBlank() } ?: return null
        return ManualAppScreenCapture(
            packageName = packageName,
            textPreview = prefs.getString(KEY_TEXT, "").orEmpty(),
            imagePath = prefs.getString(KEY_IMAGE, null),
            createdAtMillis = prefs.getLong(KEY_CREATED, 0L),
        )
    }

    fun clear(context: Context) {
        read(context)?.imagePath?.let { runCatching { File(it).delete() } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
