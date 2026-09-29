package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptBackParity0690Test {
    @Test
    fun usesNativeMaterialBackDimensions() {
        assertEquals(48, ChatGptBackParity0690.TOUCH_TARGET_DP)
        assertEquals(24, ChatGptBackParity0690.ICON_SIZE_DP)
    }

    @Test
    fun onlyConsumesVisualBackWhenAPreviousStageExists() {
        assertTrue(ChatGptBackParity0690.shouldNavigate(true))
        assertFalse(ChatGptBackParity0690.shouldNavigate(false))
    }

    @Test
    fun contractMarkerIsStableForApkProof() {
        assertEquals("CHATGPT_NATIVE_BACK_PARITY_0690", ChatGptBackParity0690.CONTRACT_MARKER)
    }
}
