package br.com.mapeiaia.rotacerta.trips

import android.content.ClipData
import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.monitoring.OperationalHealthCoordinator
import br.com.mapeiaia.rotacerta.monitoring.OperationalHealthSavedPackage0575
import br.com.mapeiaia.rotacerta.monitoring.OperationalHealthTechnicalPackage0575
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RemoteSupportDiagnostics0743 {
    suspend fun generateAndShare(context: Context): Result<String> {
        val generated = withContext(Dispatchers.IO) {
            runCatching {
                val health = OperationalHealthCoordinator.scan(context.applicationContext)
                OperationalHealthTechnicalPackage0575.generateAndSave(
                    context = context.applicationContext,
                    health = health,
                    source = UnifiedDebugEventStore.snapshot(),
                ).getOrThrow()
            }
        }
        val saved = generated.getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.Main.immediate) {
            shareZip(context, saved).map { saved.displayName }
        }
    }

    private fun shareZip(
        context: Context,
        saved: OperationalHealthSavedPackage0575,
    ): Result<Unit> = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, "Rota Certa — diagnóstico sanitizado")
            putExtra(Intent.EXTRA_STREAM, saved.uri)
            clipData = ClipData.newUri(
                context.contentResolver,
                "Rota Certa — diagnóstico sanitizado",
                saved.uri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar diagnóstico"))
    }
}
