package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityAlertsNoDirection0191ContractTest {
    private val policy = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt").readText()
    private val engine = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val legacyDirectional = File("src/test/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngineChecklist5Test.kt").readText()

    @Test
    fun `gps utilizavel nao exige heading para avisar proximidade`() {
        val isFixUsable = policy.substringAfter("fun isFixUsable(").substringBefore("fun isTargetAhead(")
        assertFalse(isFixUsable.contains("fix.headingDegrees != null"))
        assertTrue(isFixUsable.contains("fix.accuracyMeters"))
        assertTrue(isFixUsable.contains("fix.speedMetersPerSecond"))
    }

    @Test
    fun `radar e alerta nao usam gate de sentido na elegibilidade`() {
        assertFalse(engine.contains("DirectionalAlertPolicy.isTargetAhead("))
        assertFalse(engine.contains("DirectionalAlertPolicy.radarDirectionMatches("))
        assertFalse(engine.contains("targetAhead &&"))
        assertFalse(engine.contains("radarDirectionMatch &&"))
        assertTrue(engine.contains("runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES"))
    }

    @Test
    fun `passagem e detectada por afastamento depois do ponto sem heading`() {
        val hasPassedByDistance = policy.substringAfter("fun hasPassedByDistance(").substringBefore("fun isApproaching(")
        assertFalse(hasPassedByDistance.contains("headingDegrees"))
        assertFalse(hasPassedByDistance.contains("bearingToTargetDegrees"))
        assertTrue(hasPassedByDistance.contains("PASS_DISTANCE_INCREASE_METERS"))
        assertTrue(hasPassedByDistance.contains("REQUIRED_INCREASING_SAMPLES"))
        assertEquals(2, engine.split("runtime.hasPassed(distance)").size - 1)
        assertFalse(engine.contains("runtime.hasPassed(fix.headingDegrees"))
        assertTrue(engine.contains("DirectionalAlertPolicy.hasPassedByDistance("))
    }

    @Test
    fun `assinatura antiga de passagem permanece compativel sem decidir por sentido`() {
        val compatibility = policy.substringAfter("fun hasPassed(").substringBefore("fun hasPassedByDistance(")
        assertTrue(compatibility.contains("headingDegrees: Double?"))
        assertTrue(compatibility.contains("bearingToTargetDegrees: Double"))
        assertTrue(compatibility.contains("= hasPassedByDistance("))
        assertFalse(compatibility.contains("targetBehind"))
    }

    @Test
    fun `contratos direcionais legados seguem proximidade sem bloquear sentido oposto`() {
        assertFalse(legacyDirectional.contains("radar no sentido contrario nao aparece nem fala"))
        assertFalse(legacyDirectional.contains("mudanca para sentido contrario remove painel imediatamente"))
        assertFalse(legacyDirectional.contains("sentido confirmado"))
        assertTrue(legacyDirectional.contains("radar em sentido contrario tambem aparece e fala"))
        assertTrue(legacyDirectional.contains("mudanca de heading nao remove painel enquanto distancia aproxima"))
    }

    @Test
    fun `contrato visual de tres segundos e fechamento manual continua preservado`() {
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
        assertFalse(engine.contains("sentido confirmado"))
        assertFalse(engine.contains("direção confirmada"))
        assertTrue(engine.contains("status = \"Aproximando\""))
        assertTrue(engine.contains("mutedUntilExit"))
        assertTrue(engine.contains("resetAfterExit"))
    }
}
