package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputMode0186Test {
    @Test
    fun storedModesAreStableAndMutedSuppressesOnlyAudio() {
        assertEquals(SpeechOutputMode0186.Alarm, SpeechOutputMode0186.fromStored("alarm"))
        assertEquals(SpeechOutputMode0186.Media, SpeechOutputMode0186.fromStored("unknown"))
        assertFalse(SpeechOutputPolicy0186.shouldProduceAudio(SpeechOutputMode0186.Muted))
        assertTrue(SpeechOutputPolicy0186.shouldProduceAudio(SpeechOutputMode0186.Media))
    }
}
