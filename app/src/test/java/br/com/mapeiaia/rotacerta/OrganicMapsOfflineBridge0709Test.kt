package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrganicMapsOfflineBridge0709Test {
    @Test
    fun parsesStandardLatitudeLongitudePair() {
        val coordinate = OrganicMapsOfflineBridge0709.parseCoordinate("-23.550520,-46.633308")
        requireNotNull(coordinate)
        assertEquals(-23.550520, coordinate.latitude)
        assertEquals(-46.633308, coordinate.longitude)
    }

    @Test
    fun parsesSemicolonPairWithDecimalComma() {
        val coordinate = OrganicMapsOfflineBridge0709.parseCoordinate("-23,550520;-46,633308")
        requireNotNull(coordinate)
        assertEquals(-23.550520, coordinate.latitude)
        assertEquals(-46.633308, coordinate.longitude)
    }

    @Test
    fun rejectsAddressAndOutOfRangeCoordinates() {
        assertNull(OrganicMapsOfflineBridge0709.parseCoordinate("Rua Vicente Lopes, 8"))
        assertNull(OrganicMapsOfflineBridge0709.parseCoordinate("-123.0,-46.0"))
        assertNull(OrganicMapsOfflineBridge0709.parseCoordinate("-23.0,-246.0"))
    }

    @Test
    fun wireFormatUsesDotAndStablePrecision() {
        assertEquals(
            "-23.5505200,-46.6333080",
            OfflineCoordinate0709(-23.55052, -46.633308).wireValue(),
        )
    }

    @Test
    fun contractKeepsFarolAuthorityIsolated() {
        assertEquals(
            "ORGANIC_MAPS_BRIDGE_DOES_NOT_CHANGE_FAROL_AUTHORITY_0709",
            OrganicMapsOfflineBridge0709.FAROL_ISOLATION_MARKER,
        )
        assertTrue(OrganicMapsOfflineBridge0709.PICK_POINT_MARKER.contains("ROUNDTRIP"))
    }
}
