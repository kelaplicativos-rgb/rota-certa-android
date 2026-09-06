package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationCrashContainmentContract0170Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun notificationEntryIsFailClosedAndDoesNotEscapeToAndroid() {
        val branch = service.substringAfter("AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED").substringBefore("val rootPackage")
        assertTrue(branch.contains("notificationFailureCircuit0170.canAttempt"))
        assertTrue(branch.contains("try {"))
        assertTrue(branch.contains("catch (error0170: Exception)"))
        assertTrue(branch.contains("containNotificationWakeupFailure0170"))
    }

    @Test
    fun asynchronousWakeJobAlsoContainsUnexpectedExceptions() {
        val job = service.substringAfter("notificationWakeJob0169 = scope.launch").substringBefore("private suspend fun captureNotificationOverlay0169")
        assertTrue(job.contains("catch (cancelled0170: kotlinx.coroutines.CancellationException)"))
        assertTrue(job.contains("catch (error0170: Exception)"))
        assertTrue(job.contains("containNotificationWakeupFailure0170"))
    }

    @Test
    fun containmentClearsOnlyTransientWakeState() {
        val containment = service.substringAfter("private fun containNotificationWakeupFailure0170").substringBefore("private fun cancelNotificationWakeup0169")
        assertTrue(containment.contains("notificationWakeGate0169.invalidate"))
        assertTrue(containment.contains("screenshotInProgress.set(false)"))
        assertTrue(containment.contains("recordDiagnostic"))
        assertTrue(containment.contains("keepWaitingYellow = false"))
    }
}
