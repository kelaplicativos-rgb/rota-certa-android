package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyLiveFirst0692Test {
    @Test
    fun familyTrackingOpensLiveFirstAndKeepsHistoryLazy() {
        assertEquals("FAMILY_LIVE_FIRST_0692", FamilyLiveFirst0692.CONTRACT)
        assertEquals("LIVE_FIRST", FamilyLiveFirst0692.DEFAULT_MODE)
        assertFalse(FamilyLiveFirst0692.TRACE_ON_START)
        assertTrue(FamilyLiveFirst0692.SMART_FOLLOW)
        assertTrue(FamilyLiveFirst0692.HISTORY_LAZY_LOAD)
        assertTrue(FamilyLiveFirst0692.RETURN_TO_LIVE)
    }
}
