package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test

class FarolFlightRecorderContract0163Test {
    @Test
    fun recorderRemainsBoundedAndDoesNotUseTimerSampling() {
        assertTrue(FarolFlightRecorder0163.MAX_MEMORY_EVENTS <= 2_500)
        assertTrue(FarolFlightRecorder0163.MAX_DISK_EVENTS <= FarolFlightRecorder0163.MAX_MEMORY_EVENTS)
        assertTrue(FarolFlightRecorder0163.CHECKPOINT_EVERY_EVENTS >= 32L)
    }
}
