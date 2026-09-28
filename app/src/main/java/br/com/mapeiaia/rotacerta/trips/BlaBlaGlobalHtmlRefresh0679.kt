package br.com.mapeiaia.rotacerta.trips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BlaBlaGlobalHtmlRefreshStatus0679 {
    IDLE,
    ENQUEUED,
    RUNNING,
    COMPLETE,
    INCOMPLETE,
}

internal data class BlaBlaGlobalHtmlRefreshState0679(
    val status: BlaBlaGlobalHtmlRefreshStatus0679 = BlaBlaGlobalHtmlRefreshStatus0679.IDLE,
    val captureId: String = "",
    val progress: String = "",
    val expectedAccounts: Int = 0,
    val observedAccounts: Int = 0,
    val completeAccounts: Int = 0,
    val expectedTrips: Int = 0,
    val completeTrips: Int = 0,
    val canonicalCommitted: Boolean = false,
    val errorCode: String = "",
    val updatedAtMillis: Long = 0L,
) {
    val running: Boolean
        get() = status == BlaBlaGlobalHtmlRefreshStatus0679.ENQUEUED ||
            status == BlaBlaGlobalHtmlRefreshStatus0679.RUNNING

    val needsAttention: Boolean
        get() = status == BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE

    val summary: String
        get() = when (status) {
            BlaBlaGlobalHtmlRefreshStatus0679.IDLE -> "Pronto para atualizar BlaBlaCar"
            BlaBlaGlobalHtmlRefreshStatus0679.ENQUEUED -> "Atualização BlaBlaCar na fila"
            BlaBlaGlobalHtmlRefreshStatus0679.RUNNING -> progress.ifBlank { "Atualizando BlaBlaCar em segundo plano" }
            BlaBlaGlobalHtmlRefreshStatus0679.COMPLETE ->
                "Atualização completa • contas $completeAccounts/$expectedAccounts • cards $completeTrips/$expectedTrips"
            BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE ->
                "Atualização incompleta • contas $completeAccounts/$expectedAccounts • cards $completeTrips/$expectedTrips"
        }
}

internal data class BlaBlaGlobalHtmlRefreshCompletion0679(
    val expectedAccounts: Int,
    val observedAccounts: Int,
    val completeAccounts: Int,
    val expectedTrips: Int,
    val completeTrips: Int,
    val canonicalCommitted: Boolean,
    val complete: Boolean,
)

internal fun evaluateGlobalHtmlRefresh0679(
    manifest: BlaBlaRidesSnapshotManifest0526,
    expectedAccounts: Int,
    canonicalCommitted: Boolean,
): BlaBlaGlobalHtmlRefreshCompletion0679 {
    val expected = expectedAccounts.coerceAtLeast(0)
    val profiles = manifest.profiles
    val trips = profiles.flatMap(BlaBlaRidesSnapshotProfile0526::tripCaptures0605)
    val completeProfiles = profiles.count { it.status == BlaBlaRidesSnapshotStatus0526.COMPLETE }
    val completeTrips = trips.count { it.status == BlaBlaRidesSnapshotStatus0526.COMPLETE }
    val complete =
        expected > 0 &&
            manifest.result == "COMPLETE" &&
            profiles.size == expected &&
            completeProfiles == expected &&
            trips.all { it.status == BlaBlaRidesSnapshotStatus0526.COMPLETE } &&
            canonicalCommitted
    return BlaBlaGlobalHtmlRefreshCompletion0679(
        expectedAccounts = expected,
        observedAccounts = profiles.size,
        completeAccounts = completeProfiles,
        expectedTrips = trips.size,
        completeTrips = completeTrips,
        canonicalCommitted = canonicalCommitted,
        complete = complete,
    )
}

/**
 * Single entry point for the user-requested global BlaBlaCar HTML refresh.
 *
 * The worker does not implement acquisition. It only keeps the already-authoritative
 * BlaBlaRidesSnapshotCoordinator0526 alive outside Compose/Activity lifecycle and publishes
 * a persistent status for the header and the existing BlaBlaCar screen.
 */
internal object BlaBlaGlobalHtmlRefresh0679 {
    private const val PREFS = "blablacar_global_html_refresh_0679"
    private const val UNIQUE_WORK = "blablacar-global-html-refresh-0679"
    private const val INPUT_SOURCE = "source"
    private val loaded = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(BlaBlaGlobalHtmlRefreshState0679())

    fun state(context: Context): StateFlow<BlaBlaGlobalHtmlRefreshState0679> {
        ensureLoaded(context.applicationContext)
        return mutableState.asStateFlow()
    }

    @Synchronized
    fun enqueue(context: Context, source: String): Boolean {
        val app = context.applicationContext
        ensureLoaded(app)
        if (mutableState.value.running) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_REFRESH_DUPLICATE_BLOCKED_0679",
                app.packageName,
                "source=${source.take(80)} singleFlight=true",
            )
            return false
        }
        val accountCount = BlaBlaDynamicAccountRegistry(app).list().size
        if (accountCount <= 0) {
            publish(
                app,
                BlaBlaGlobalHtmlRefreshState0679(
                    status = BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE,
                    progress = "Nenhuma conta BlaBlaCar conectada.",
                    expectedAccounts = 0,
                    errorCode = "NO_BLABLACAR_ACCOUNTS",
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            return false
        }

        publish(
            app,
            BlaBlaGlobalHtmlRefreshState0679(
                status = BlaBlaGlobalHtmlRefreshStatus0679.ENQUEUED,
                progress = "Preparando atualização de $accountCount conta(s)…",
                expectedAccounts = accountCount,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        val request = OneTimeWorkRequestBuilder<BlaBlaGlobalHtmlRefreshWorker0679>()
            .setInputData(workDataOf(INPUT_SOURCE to source.take(80)))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_GLOBAL_HTML_REFRESH_ENQUEUED_0679",
            app.packageName,
            "source=${source.take(80)} accounts=$accountCount worker=WorkManager foregroundDataSync=true",
            diagnosticContext = DiagnosticEventContext0507(
                parentModule = DiagnosticModule0507.BLABLACAR,
                operation = "GLOBAL_HTML_REFRESH",
                result = "ENQUEUED",
            ),
        )
        return true
    }

    internal fun publishProgress(
        context: Context,
        progress: String,
        captureId: String = mutableState.value.captureId,
    ) {
        val current = mutableState.value
        publish(
            context.applicationContext,
            current.copy(
                status = BlaBlaGlobalHtmlRefreshStatus0679.RUNNING,
                captureId = captureId.ifBlank { current.captureId },
                progress = progress.take(240),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    internal fun publish(context: Context, state: BlaBlaGlobalHtmlRefreshState0679) {
        val app = context.applicationContext
        ensureLoaded(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("status", state.status.name)
            .putString("captureId", state.captureId)
            .putString("progress", state.progress)
            .putInt("expectedAccounts", state.expectedAccounts)
            .putInt("observedAccounts", state.observedAccounts)
            .putInt("completeAccounts", state.completeAccounts)
            .putInt("expectedTrips", state.expectedTrips)
            .putInt("completeTrips", state.completeTrips)
            .putBoolean("canonicalCommitted", state.canonicalCommitted)
            .putString("errorCode", state.errorCode)
            .putLong("updatedAtMillis", state.updatedAtMillis)
            .apply()
        mutableState.value = state
    }

    private fun ensureLoaded(context: Context) {
        if (!loaded.compareAndSet(false, true)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = runCatching {
            BlaBlaGlobalHtmlRefreshStatus0679.valueOf(
                prefs.getString("status", BlaBlaGlobalHtmlRefreshStatus0679.IDLE.name)
                    ?: BlaBlaGlobalHtmlRefreshStatus0679.IDLE.name,
            )
        }.getOrDefault(BlaBlaGlobalHtmlRefreshStatus0679.IDLE)
        mutableState.value = BlaBlaGlobalHtmlRefreshState0679(
            status = status,
            captureId = prefs.getString("captureId", "").orEmpty(),
            progress = prefs.getString("progress", "").orEmpty(),
            expectedAccounts = prefs.getInt("expectedAccounts", 0),
            observedAccounts = prefs.getInt("observedAccounts", 0),
            completeAccounts = prefs.getInt("completeAccounts", 0),
            expectedTrips = prefs.getInt("expectedTrips", 0),
            completeTrips = prefs.getInt("completeTrips", 0),
            canonicalCommitted = prefs.getBoolean("canonicalCommitted", false),
            errorCode = prefs.getString("errorCode", "").orEmpty(),
            updatedAtMillis = prefs.getLong("updatedAtMillis", 0L),
        )
    }

    internal fun inputSource(parameters: WorkerParameters): String =
        parameters.inputData.getString(INPUT_SOURCE).orEmpty()
}

class BlaBlaGlobalHtmlRefreshWorker0679(
    appContext: Context,
    private val parameters0679: WorkerParameters,
) : CoroutineWorker(appContext, parameters0679) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val source = BlaBlaGlobalHtmlRefresh0679.inputSource(parameters0679)
        val expectedAccounts = BlaBlaDynamicAccountRegistry(app).list().size
        if (expectedAccounts <= 0) {
            BlaBlaGlobalHtmlRefresh0679.publish(
                app,
                BlaBlaGlobalHtmlRefreshState0679(
                    status = BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE,
                    progress = "Nenhuma conta BlaBlaCar conectada.",
                    errorCode = "NO_BLABLACAR_ACCOUNTS",
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            return Result.failure()
        }

        setForeground(foregroundInfo0679("Atualizando $expectedAccounts conta(s) BlaBlaCar"))
        BlaBlaGlobalHtmlRefresh0679.publish(
            app,
            BlaBlaGlobalHtmlRefreshState0679(
                status = BlaBlaGlobalHtmlRefreshStatus0679.RUNNING,
                progress = "Preparando captura HTML de todas as contas…",
                expectedAccounts = expectedAccounts,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )

        return try {
            var canonicalCommitted = false
            val manifest = BlaBlaRidesSnapshotCoordinator0526.captureAll(
                context = app,
                onProgress = { progress ->
                    BlaBlaGlobalHtmlRefresh0679.publishProgress(app, progress)
                },
                onGlobalCommitResult0679 = { committed ->
                    canonicalCommitted = committed
                },
            )
            val completion = evaluateGlobalHtmlRefresh0679(
                manifest = manifest,
                expectedAccounts = expectedAccounts,
                canonicalCommitted = canonicalCommitted,
            )
            val firstError = manifest.profiles
                .firstOrNull { it.status != BlaBlaRidesSnapshotStatus0526.COMPLETE }
                ?.errorCode
                .orEmpty()
            val errorCode = when {
                completion.complete -> ""
                !canonicalCommitted -> "CANONICAL_FINAL_COMMIT_NOT_CONFIRMED"
                firstError.isNotBlank() -> firstError.take(120)
                else -> "GLOBAL_HTML_NOT_100_PERCENT_COMPLETE"
            }
            val terminal = BlaBlaGlobalHtmlRefreshState0679(
                status = if (completion.complete) {
                    BlaBlaGlobalHtmlRefreshStatus0679.COMPLETE
                } else {
                    BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE
                },
                captureId = manifest.captureId,
                progress = if (completion.complete) {
                    "100% concluído • todas as contas e todos os cards HTML foram validados"
                } else {
                    "Atualização incompleta • toque no alerta para tentar novamente"
                },
                expectedAccounts = completion.expectedAccounts,
                observedAccounts = completion.observedAccounts,
                completeAccounts = completion.completeAccounts,
                expectedTrips = completion.expectedTrips,
                completeTrips = completion.completeTrips,
                canonicalCommitted = completion.canonicalCommitted,
                errorCode = errorCode,
                updatedAtMillis = System.currentTimeMillis(),
            )
            BlaBlaGlobalHtmlRefresh0679.publish(app, terminal)
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_REFRESH_FINISHED_0679",
                app.packageName,
                "source=${source.take(80)} captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                    "status=${terminal.status.name} accounts=${completion.completeAccounts}/${completion.expectedAccounts} " +
                    "cards=${completion.completeTrips}/${completion.expectedTrips} canonicalCommitted=$canonicalCommitted " +
                    "error=${errorCode.ifBlank { "NONE" }}",
                diagnosticContext = DiagnosticEventContext0507(
                    parentModule = DiagnosticModule0507.BLABLACAR,
                    operation = "GLOBAL_HTML_REFRESH",
                    result = terminal.status.name,
                    severity = if (completion.complete) DiagnosticSeverity0507.INFO else DiagnosticSeverity0507.WARNING,
                    errorCode = errorCode,
                ),
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_REFRESH_CANCELLED_0679",
                app.packageName,
                "source=${source.take(80)} workManagerCancellation=true transactionFinallyExpected=true",
            )
            throw cancelled
        } catch (error: Throwable) {
            BlaBlaGlobalHtmlRefresh0679.publish(
                app,
                BlaBlaGlobalHtmlRefreshState0679(
                    status = BlaBlaGlobalHtmlRefreshStatus0679.INCOMPLETE,
                    progress = "Atualização interrompida • toque no alerta para tentar novamente",
                    expectedAccounts = expectedAccounts,
                    errorCode = error.javaClass.simpleName.take(120).ifBlank { "GLOBAL_HTML_REFRESH_FAILED" },
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_REFRESH_FAILED_0679",
                app.packageName,
                "source=${source.take(80)} error=${error.javaClass.simpleName.take(80)} failClosed=true",
                diagnosticContext = DiagnosticEventContext0507(
                    parentModule = DiagnosticModule0507.BLABLACAR,
                    operation = "GLOBAL_HTML_REFRESH",
                    result = "FAILED",
                    severity = DiagnosticSeverity0507.ERROR,
                    errorCode = error.javaClass.simpleName.take(80),
                ),
            )
            Result.failure()
        }
    }

    private fun foregroundInfo0679(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Atualização BlaBlaCar",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantém a captura HTML global em execução até terminar."
                },
            )
        }
        val openIntent = Intent(applicationContext, TripsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Rota Certa • Atualizando BlaBlaCar")
            .setContentText(text.take(120))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "blablacar_global_html_refresh_0679"
        const val NOTIFICATION_ID = 6791
    }
}
