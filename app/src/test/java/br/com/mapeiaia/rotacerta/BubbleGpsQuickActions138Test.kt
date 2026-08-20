package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGpsQuickActions138Test {
    @Test fun `radar manual evita duplicacao e confirma salvamento`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("GeoDistance.meters(radar.coordinate, coordinate) < 8.0"))
        assertTrue(source.contains("Radar salvo"))
    }

    @Test fun `alerta local e destino usam fluxos distintos`() {
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertTrue(service.contains("EXTRA_CREATE_SAVED_PLACE_TYPE_138"))
        assertTrue(service.contains("EXTRA_CONFIRM_DESTINATION_GPS_138"))
        assertTrue(main.contains("Digite um nome ou salve vazio para usar Alerta"))
        assertTrue(main.contains("Definir este local como destino?"))
    }
}
