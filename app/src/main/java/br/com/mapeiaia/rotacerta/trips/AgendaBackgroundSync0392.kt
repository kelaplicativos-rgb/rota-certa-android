package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AgendaBackgroundSyncRun0392(
    val bookingImports: Int = 0,
    val publicLocalPublished: Int = 0,
    val publicExternalPublished: Int = 0,
    val failures: Int = 0,
)

internal fun agendaBackgroundSyncIntervalMinutes0392(): Long = 15L

internal fun agendaBackgroundSyncShowsUiStatus0392(): Boolean = false

internal object AgendaBackgroundSync0392 {
    private const val PERIODIC_WORK = "agenda-background-sync-0392-periodic"
    private const val IMMEDIATE_WORK = "agenda-background-sync-0392-immediate"
    private const val INPUT_REASON = "reason"
    private val processMutex = Mutex()

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val constraints = networkConstraints()
        val periodic = PeriodicWorkRequestBuilder<AgendaBackgroundSyncWorker0392>(
            agendaBackgroundSyncIntervalMinutes0392(),
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_SCHEDULED_0392",
            appContext.packageName,
            "periodMinutes=${agendaBackgroundSyncIntervalMinutes0392()} silentUi=true durable=true",
        )
    }

    fun enqueueImmediate(context: Context, reason: String) {
        val appContext = context.applicationContext
        ensureScheduled(appContext)
        val request = OneTimeWorkRequestBuilder<AgendaBackgroundSyncWorker0392>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(INPUT_REASON to reason.take(80)))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        UnifiedDebugEventStore.record(
            "AGENDA_BACKGROUND_SYNC_ENQUEUED_0392",
            appContext.packageName,
            "reason=${reason.take(80)} silentUi=true",
        )
    }

    internal suspend fun runCycle(context: Context, reason: String): AgendaBackgroundSyncRun0392 =
        processMutex.withLock {
            val appContext = context.applicationContext
            val store = TripStore(appContext)
            var failures = 0
            var bookingImports = 0
            var publicLocalPublished = 0
            var publicExternalPublished = 0

            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_START_0392",
                appContext.packageName,
                "reason=${reason.take(80)} silentUi=true",
            )

            try {
                val booking = PublicBookingRemoteSync0296.pullAndReconcile(appContext, store)
                bookingImports = booking.importedCount
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "AGENDA_BACKGROUND_SYNC_BOOKINGS_FAILED_0392",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_BOOKING_RECONCILE",
                        component = "AgendaBackgroundSync0392",
                        method = "runCycle",
                    ),
                )
            }

            try {
                TripMutationCoordinator0387(appContext, store).drainPending()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "AGENDA_BACKGROUND_SYNC_OUTBOX_FAILED_0392",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_OUTBOX_DRAIN",
                        component = "AgendaBackgroundSync0392",
                        method = "runCycle",
                    ),
                )
            }

            try {
                val allocation = SettingsRepository(appContext).settings.first().rotaCertaSeatAllocation
                val publicResult = PublicAgendaAutoSync0300.sync(
                    context = appContext,
                    store = store,
                    configuredVehicleCapacity = 0,
                    configuredRotaCertaSeatAllocation = allocation,
                )
                publicLocalPublished = publicResult.localPublished
                publicExternalPublished = publicResult.externalPublished
                failures += publicResult.failures
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "AGENDA_BACKGROUND_SYNC_PUBLIC_FAILED_0392",
                    appContext.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BACKGROUND_PUBLIC_AGENDA_SYNC",
                        component = "AgendaBackgroundSync0392",
                        method = "runCycle",
                    ),
                )
            }

            runCatching {
                BookingPushRegistration0304.ensureRegistered(appContext, store)
            }

            BookingRealtimeEvents0356.notifyChanged()
            TripWidgetProvider.updateAll(appContext)

            UnifiedDebugEventStore.record(
                "AGENDA_BACKGROUND_SYNC_END_0392",
                appContext.packageName,
                "reason=${reason.take(80)} bookingImports=$bookingImports localPublished=$publicLocalPublished externalPublished=$publicExternalPublished failures=$failures silentUi=true",
            )

            AgendaBackgroundSyncRun0392(
                bookingImports = bookingImports,
                publicLocalPublished = publicLocalPublished,
                publicExternalPublished = publicExternalPublished,
                failures = failures,
            )
        }

    internal fun reason(workerParameters: WorkerParameters): String =
        workerParameters.inputData.getString(INPUT_REASON)?.takeIf(String::isNotBlank) ?: "periodic"

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

class AgendaBackgroundSyncWorker0392(
    appContext: Context,
    private val parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val result = AgendaBackgroundSync0392.runCycle(
            context = applicationContext,
            reason = AgendaBackgroundSync0392.reason(parameters),
        )
        return if (result.failures > 0 && runAttemptCount < 5) Result.retry() else Result.success()
    }
}
