package br.com.mapeiaia.rotacerta

import android.media.AudioManager
import android.media.ToneGenerator

/** Toque curto e leve para chamar a atencao antes da voz do radar. */
class RadarDetectionCue {
    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME_PERCENT)
    }.getOrNull()

    fun play() {
        runCatching {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, DURATION_MS)
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
    }

    private companion object {
        const val VOLUME_PERCENT = 72
        const val DURATION_MS = 120
    }
}
