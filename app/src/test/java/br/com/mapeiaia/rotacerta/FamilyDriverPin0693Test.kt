package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyDriverPin0693Test {
    @Test
    fun driverDefinedPinIsServerConfirmedAndNeverAutoRegenerated() {
        assertTrue(FamilyDriverPin0693.DRIVER_DEFINED)
        assertTrue(FamilyDriverPin0693.SERVER_CONFIRMED)
        assertFalse(FamilyDriverPin0693.AUTO_REGENERATE)
        assertTrue(FamilyDriverPin0693.INVALIDATE_OLD_ACCESS)
    }
}
