package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionalAlertPolicyChecklist5Test {
    @Test
    fun `rejeita GPS antigo ou impreciso`() {
        val now = 100_000L
        assertFalse(DirectionalAlertPolicy.isFixUsable(fix(accuracy = 45.0, time = now), 200, now))
        assertFalse(DirectionalAlertPolicy.isFixUsable(fix(accuracy = 8.0, time = now - 5_000L), 200, now))
        assertTrue(DirectionalAlertPolicy.isFixUsable(fix(accuracy = 8.0, time = now), 200, now))
    }

    @Test
    fun `alvo atras do veiculo nao e elegivel`() {
        assertTrue(DirectionalAlertPolicy.isTargetAhead(90.0, 100.0))
        assertFalse(DirectionalAlertPolicy.isTargetAhead(90.0, 270.0))
    }

    @Test
    fun `radar de sentido unico rejeita direcao contraria`() {
        val radar = ImportedRadar(
            id = "radar",
            coordinate = Coordinate(0.0, 0.0),
            type = 1,
            directionType = 1,
            direction = 90,
        )
        assertTrue(DirectionalAlertPolicy.radarDirectionMatches(radar, 92.0))
        assertFalse(DirectionalAlertPolicy.radarDirectionMatches(radar, 270.0))
    }

    @Test
    fun `radar de duas direcoes aceita rumo inverso`() {
        val radar = ImportedRadar(
            id = "radar",
            coordinate = Coordinate(0.0, 0.0),
            type = 1,
            directionType = 2,
            direction = 90,
        )
        assertTrue(DirectionalAlertPolicy.radarDirectionMatches(radar, 270.0))
    }

    @Test
    fun `passagem exige alvo atras distancia crescente e duas confirmacoes`() {
        assertFalse(
            DirectionalAlertPolicy.hasPassed(
                headingDegrees = 90.0,
                bearingToTargetDegrees = 270.0,
                minimumDistanceMeters = 10.0,
                currentDistanceMeters = 40.0,
                increasingSamples = 1,
            ),
        )
        assertTrue(
            DirectionalAlertPolicy.hasPassed(
                headingDegrees = 90.0,
                bearingToTargetDegrees = 270.0,
                minimumDistanceMeters = 10.0,
                currentDistanceMeters = 40.0,
                increasingSamples = 2,
            ),
        )
    }

    private fun fix(
        accuracy: Double,
        time: Long,
    ): PreciseNavigationFix = PreciseNavigationFix(
        coordinate = Coordinate(0.0, 0.0),
        accuracyMeters = accuracy,
        speedMetersPerSecond = 10.0,
        headingDegrees = 90.0,
        headingSource = NavigationHeadingSource.GpsAndCompass,
        timestampMillis = time,
        provider = "gps",
    )
}
