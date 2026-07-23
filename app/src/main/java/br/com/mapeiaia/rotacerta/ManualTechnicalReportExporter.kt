package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ManualTechnicalReportExporter {
    fun createAndShare(context: Context, report: String): Result<File> = runCatching {
        val appContext = context.applicationContext
        val directory = File(appContext.cacheDir, "technical_reports").apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > RETENTION_MILLIS }
            ?.forEach(File::delete)

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "rota-certa-relatorio-$stamp.txt")
        file.writeText(report, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatorio tecnico Rota Certa")
            putExtra(Intent.EXTRA_TEXT, "Relatorio tecnico gerado manualmente pelo Rota Certa.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, "Compartilhar relatorio tecnico")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        file
    }

    private const val RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
}
