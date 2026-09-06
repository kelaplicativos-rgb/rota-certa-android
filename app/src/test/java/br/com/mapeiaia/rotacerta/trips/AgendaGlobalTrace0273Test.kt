package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaGlobalTrace0273Test {
    @Test
    fun everyCurrentAndFutureTripsActivityHasGlobalWindowTrace() {
        val trace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val actions = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()

        assertTrue(trace.contains("activity.javaClass.name.startsWith(AGENDA_PACKAGE_PREFIX)"))
        assertTrue(trace.contains("method.name == \"dispatchTouchEvent\""))
        assertTrue(trace.contains("MotionEvent.ACTION_UP"))
        assertTrue(trace.contains("UnifiedDebugEventStore.record"))
        assertTrue(trace.contains("AGENDA_INTERACTION"))
        assertTrue(trace.contains("AGENDA_SCREEN"))
        assertTrue(manifest.contains("android:name=\".trips.AgendaTraceProvider\""))
        assertTrue(manifest.contains("android:authorities=\"${'$'}{applicationId}.agenda.trace\""))
        assertTrue(actions.contains("val traceKey: String? = null"))
        assertTrue(actions.contains("AgendaTrace.action(context, action.traceKey, action.label)"))
    }

    @Test
    fun genericTraceIsPrivacySafeAndCannotBreakAgendaInput() {
        val trace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTrace.kt").readText()

        assertTrue(trace.contains("label_hash_"))
        assertTrue(trace.contains("runCatching"))
        assertTrue(trace.contains("method.invoke(current"))
        assertFalse(trace.contains("intent.data"))
        assertFalse(trace.contains("intent.extras"))
        assertFalse(trace.contains("CookieManager"))
        assertFalse(trace.contains("Authorization"))
        assertFalse(trace.contains("rawLabel.toString()"))
    }
}
