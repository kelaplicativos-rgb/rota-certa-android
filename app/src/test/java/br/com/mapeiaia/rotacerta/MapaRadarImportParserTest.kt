package br.com.mapeiaia.rotacerta

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapaRadarImportParserTest {
    @Test
    fun importsThreeColumnMapaRadarFileUsedInRealDevice() {
        val content = """
            -43.030287,-22.665031,Radar Fixo - 30 kmh@30
            -46.5077317,-23.5676948,Semaforo com Radar - 60 kmh@60
            -46.4754192,-23.6108139,Semaforo com Camera - 0 kmh@0
        """.trimIndent()

        val radars = parseMapaRadarCsv(content, importedAtMillis = 123L)

        assertEquals(3, radars.size)
        assertEquals(-22.665031, radars[0].coordinate.latitude, 0.000001)
        assertEquals(-43.030287, radars[0].coordinate.longitude, 0.000001)
        assertEquals(1, radars[0].type)
        assertEquals(30, radars[0].speedKmh)
        assertEquals(2, radars[1].type)
        assertEquals(60, radars[1].speedKmh)
        assertEquals(3, radars[2].type)
        assertNull(radars[2].speedKmh)
    }

    @Test
    fun keepsLegacyNumericSixColumnFormat() {
        val content = "-46.5000,-23.5000,1,50,2,180"
        val radar = parseMapaRadarCsv(content, 456L).single()

        assertEquals(1, radar.type)
        assertEquals(50, radar.speedKmh)
        assertEquals(2, radar.directionType)
        assertEquals(180, radar.direction)
    }

    @Test
    fun acceptsSemicolonAndDecimalCommaTxt() {
        val content = "-46,5000;-23,5000;Radar Movel - 80 kmh@80"
        val radar = parseMapaRadarCsv(content, 789L).single()

        assertEquals(-23.5, radar.coordinate.latitude, 0.000001)
        assertEquals(-46.5, radar.coordinate.longitude, 0.000001)
        assertEquals(4, radar.type)
        assertEquals(80, radar.speedKmh)
    }

    @Test
    fun respectsLatitudeLongitudeHeader() {
        val content = """
            latitude,longitude,tipo
            -23.5000,-46.5000,Radar Fixo - 60 kmh@60
        """.trimIndent()
        val radar = parseMapaRadarCsv(content, 999L).single()

        assertEquals(-23.5, radar.coordinate.latitude, 0.000001)
        assertEquals(-46.5, radar.coordinate.longitude, 0.000001)
    }

    @Test
    fun decodesWindows1252TxtAndRemovesDuplicates() {
        val line = "-46.5000,-23.5000,Semáforo com Câmera - 0 kmh@0"
        val bytes = "$line\r\n$line".toByteArray(Charset.forName("windows-1252"))
        val radars = parseMapaRadarFile(bytes, 1000L)

        assertEquals(1, radars.size)
        assertEquals(3, radars.single().type)
        assertNull(radars.single().speedKmh)
    }

    @Test
    fun ignoresInvalidRowsWithoutLosingValidRows() {
        val content = """
            X,Y,TYPE,SPEED
            texto,invalido,Radar Fixo@50
            -43.0,-22.0,Radar Fixo - 50 kmh@50
            999,-22.0,Radar Fixo - 50 kmh@50
        """.trimIndent()

        val radars = parseMapaRadarCsv(content, 1001L)
        assertEquals(1, radars.size)
        assertTrue(radars.single().id.startsWith("maparadar-"))
    }
}
