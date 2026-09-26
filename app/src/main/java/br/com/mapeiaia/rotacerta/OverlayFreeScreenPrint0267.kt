package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class OverlayFreePrintWindow0267(
    val id: Int,
    val layer: Int,
    val active: Boolean,
    val focused: Boolean,
    val packageName: String?,
    val applicationWindow: Boolean,
)

/** Chooses the visible application surface and never an accessibility overlay. */
internal object OverlayFreePrintWindowPolicy0267 {
    fun select(windows: List<OverlayFreePrintWindow0267>): OverlayFreePrintWindow0267? = windows
        .asSequence()
        .filter { it.applicationWindow && it.id >= 0 }
        .sortedWith(
            compareByDescending<OverlayFreePrintWindow0267> { it.active }
                .thenByDescending { it.focused }
                .thenByDescending { it.layer }
                .thenByDescending { it.id },
        )
        .firstOrNull()
}

/**
 * User-requested gallery print for Android 14+.
 *
 * Capturing the concrete application window keeps the shortcut panel and the
 * main Rota Certa bubble out of the PNG without changing FAROL reading,
 * detection, color, distance, or decision behavior.
 */
internal object OverlayFreeScreenPrint0267 {
    const val CONTRACT_MARKER = "OVERLAY_FREE_APPLICATION_WINDOW_PRINT_0267"

    private val captureInProgress = AtomicBoolean(false)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun captureIfSupported(
        context: Context,
        trace: (String) -> Unit = {},
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val service = context as? AccessibilityService ?: return false
        captureWindow(service, trace)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun captureWindow(
        service: AccessibilityService,
        trace: (String) -> Unit,
    ) {
        if (!captureInProgress.compareAndSet(false, true)) {
            toast(service, "Um Print já está em andamento.")
            return
        }

        val target = OverlayFreePrintWindowPolicy0267.select(
            runCatching { service.windows }.getOrDefault(emptyList()).map { window ->
                val root = runCatching { window.root }.getOrNull()
                OverlayFreePrintWindow0267(
                    id = runCatching { window.id }.getOrDefault(-1),
                    layer = runCatching { window.layer }.getOrDefault(Int.MIN_VALUE),
                    active = runCatching { window.isActive }.getOrDefault(false),
                    focused = runCatching { window.isFocused }.getOrDefault(false),
                    packageName = runCatching { root?.packageName?.toString() }.getOrNull(),
                    applicationWindow = runCatching { window.type }.getOrDefault(-1) ==
                        AccessibilityWindowInfo.TYPE_APPLICATION,
                )
            },
        )
        if (target == null) {
            captureInProgress.set(false)
            recordFailure(null, "application_window_not_found")
            toast(service, "Não encontrei a janela da tela para salvar o Print.")
            return
        }

        val caseId = FarolForensicCardBlackBoxStage32.currentCaseId()
        val owner = FarolForensicCardBlackBoxStage32.currentOwnerToken()
        trace("bubble.print.overlay_free.requested window=${target.id} package=${target.packageName.orEmpty()}")
        FarolFlightRecorder0163.record(
            "PRINT_WINDOW_REQUESTED_0267",
            target.packageName,
            "window=${target.id}; case=${caseId ?: "none"}; owner=${owner ?: "UNKNOWN"}; overlaysExcluded=true",
        )

        runCatching {
            service.takeScreenshotOfWindow(
                target.id,
                service.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        ioScope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = screenshot.toSoftwareBitmap0267()
                                    ?: error("bitmap indisponível")
                                val saved = FarolPrintStoreStage32.savePng(
                                    service.applicationContext,
                                    bitmap,
                                    caseId,
                                    owner,
                                ).getOrThrow()
                                FarolFlightRecorder0163.record(
                                    "PRINT_WINDOW_SUCCESS_0267",
                                    target.packageName,
                                    "window=${target.id}; name=${saved.displayName}; uri=${saved.uri}; hash=${saved.contentHash}; overlaysExcluded=true",
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    toast(service, "Print limpo salvo na Galeria / Pictures/Rota Certa")
                                }
                            } catch (error: Throwable) {
                                recordFailure(target.packageName, error::class.java.simpleName)
                                withContext(Dispatchers.Main.immediate) {
                                    toast(service, "Não foi possível salvar o Print.")
                                }
                            } finally {
                                bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                                captureInProgress.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        captureInProgress.set(false)
                        recordFailure(target.packageName, "platform_error_$errorCode")
                        val message = if (
                            errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                        ) {
                            "Outra captura acabou de ocorrer. Toque em Print novamente."
                        } else {
                            "Não foi possível tirar o Print (código $errorCode)."
                        }
                        toast(service, message)
                    }
                },
            )
        }.onFailure { error ->
            captureInProgress.set(false)
            recordFailure(target.packageName, error::class.java.simpleName)
            toast(service, "Não foi possível tirar o Print.")
        }
    }

    private fun recordFailure(packageName: String?, reason: String) {
        FarolFlightRecorder0163.record(
            "PRINT_WINDOW_FAILED_0267",
            packageName,
            "reason=$reason; overlaysExcluded=true",
        )
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap0267(): Bitmap? {
        val buffer = hardwareBuffer
        return try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
            hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
    }
}
