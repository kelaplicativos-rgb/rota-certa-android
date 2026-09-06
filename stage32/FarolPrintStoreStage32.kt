package br.com.mapeiaia.rotacerta

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FarolPrintStoreStage32 {
    const val CONTRACT_MARKER = "FAROL_REAL_PRINT_MEDIASTORE_STAGE32"
    data class SavedPrint(val displayName: String, val uri: Uri, val contentHash: Long)

    fun buildDisplayName(caseId: String?, ownerToken: String?, nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date(nowMillis))
        val parts = listOfNotNull("RotaCerta", caseId?.takeIf(String::isNotBlank), ownerToken?.takeIf(String::isNotBlank), stamp)
        return parts.joinToString("_") + ".png"
    }

    fun savePng(context: Context, bitmap: Bitmap, caseId: String?, ownerToken: String?): Result<SavedPrint> = runCatching {
        val app = context.applicationContext
        val name = buildDisplayName(caseId, ownerToken)
        val hash = sampleHash(bitmap)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Rota Certa")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val created = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Nao consegui criar a imagem na Galeria.")
            runCatching {
                resolver.openOutputStream(created, "w")?.use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Falha ao codificar PNG." }
                } ?: error("Nao consegui abrir a imagem para escrita.")
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(created, values, null, null)
            }.onFailure { resolver.delete(created, null, null) }.getOrThrow()
            created
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Rota Certa").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
            Uri.fromFile(file)
        }
        SavedPrint(name, uri, hash)
    }

    /** Cheap deterministic sample; forensic identity only, never cryptographic/security authority. */
    fun sampleHash(bitmap: Bitmap): Long {
        var h = -3750763034362895579L
        h = (h xor bitmap.width.toLong()) * 1099511628211L
        h = (h xor bitmap.height.toLong()) * 1099511628211L
        if (bitmap.width > 0 && bitmap.height > 0) {
            val xs = intArrayOf(0, bitmap.width / 2, bitmap.width - 1).distinct()
            val ys = intArrayOf(0, bitmap.height / 2, bitmap.height - 1).distinct()
            ys.forEach { y -> xs.forEach { x -> h = (h xor bitmap.getPixel(x, y).toLong()) * 1099511628211L } }
        }
        return h
    }
}
