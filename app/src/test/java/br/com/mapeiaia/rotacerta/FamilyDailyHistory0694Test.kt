package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyDailyHistory0694Test {
    @Test
    fun familyHistoryIsDailySeparateAndNeverExposedToPassengers() {
        assertEquals("FAMILY_DAILY_HISTORY_0694", FamilyDailyHistory0694.CONTRACT)
        assertEquals("America/Sao_Paulo", FamilyDailyHistory0694.TIMEZONE)
        assertTrue(FamilyDailyHistory0694.LIVE_AND_HISTORY_SEPARATE)
        assertFalse(FamilyDailyHistory0694.PASSENGER_HISTORY_ALLOWED)
        assertTrue(FamilyDailyHistory0694.DATE_SCOPED_QUERY)
        assertTrue(FamilyDailyHistory0694.SEGMENT_GAPS)
    }
}
