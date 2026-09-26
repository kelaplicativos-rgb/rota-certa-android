package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedRadarEditPolicy0178Test {
    private val radar = ImportedRadar(
        id = "radar-1",
        coordinate = Coordinate(-23.0, -46.0),
        type = 1,
        speedKmh = 60,
    )

    @Test
    fun appliesTrimmedNameAndValidSpeed() {
        val updated = ImportedRadarEditPolicy0178.apply(radar, "  Radar   80 por hora  ", "80")
        assertEquals("Radar 80 por hora", updated?.name)
        assertEquals(80, updated?.speedKmh)
    }

    @Test
    fun blankSpeedRemovesLimitWithoutRejectingEdit() {
        val updated = ImportedRadarEditPolicy0178.apply(radar, "Radar", "")
        assertEquals("Radar", updated?.name)
        assertNull(updated?.speedKmh)
    }

    @Test
    fun invalidSpeedFailsClosed() {
        assertNull(ImportedRadarEditPolicy0178.apply(radar, "Radar", "999"))
        assertNull(ImportedRadarEditPolicy0178.apply(radar, "Radar", "oitenta"))
    }
}
