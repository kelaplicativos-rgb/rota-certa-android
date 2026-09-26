package br.com.mapeiaia.rotacerta

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ManualTechnicalReportExporter {
    data class SavedReport(
        val displayName: String,
        val uri: Uri,
    )

    fun saveToDownloads(context: Context, report: String): Result<SavedReport> = runCatching {
        val appContext = context.applicationContext
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val displayName = "rota-certa-relatorio-$stamp.txt"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + DOWNLOAD_SUBDIRECTORY,
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val created = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Nao consegui criar o arquivo em Downloads.")
            runCatching {
                resolver.openOutputStream(created, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(report)
                } ?: error("Nao consegui escrever o relatorio em Downloads.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(created, values, null, null)
            }.onFailure {
                resolver.delete(created, null, null)
            }.getOrThrow()
            created
        } else {
            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_SUBDIRECTORY,
            ).apply { mkdirs() }
            val file = File(directory, displayName)
            file.writeText(report, Charsets.UTF_8)
            Uri.fromFile(file)
        }

        SavedReport(displayName = displayName, uri = uri)
    }

    private const val DOWNLOAD_SUBDIRECTORY = "Rota Certa"
}
