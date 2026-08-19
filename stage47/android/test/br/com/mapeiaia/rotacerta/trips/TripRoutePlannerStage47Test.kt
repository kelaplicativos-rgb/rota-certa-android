package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRoutePlannerStage47Test {
    @Test
    fun googleDurationStringsRoundUpSafelyToWholeSeconds() {
        assertEquals(4L, TripDurationParser.seconds("3.5s"))
        assertEquals(90L, TripDurationParser.seconds("90s"))
        assertNull(TripDurationParser.seconds("-1s"))
        assertNull(TripDurationParser.seconds("invalid"))
    }
}
