package br.com.mapeiaia.rotacerta

import android.content.Context
import android.media.AudioAttributes

enum class SpeechOutputMode0186(val storedValue: String, val displayLabel: String) {
    Muted("muted", "Sem som"),
    Alarm("alarm", "Canal de alarme"),
    Media("media", "Canal de mídia/alto-falante"),
    ;

    companion object {
        fun fromStored(value: String?): SpeechOutputMode0186 =
            values().firstOrNull { it.storedValue == value } ?: Media
    }
}

class SpeechOutputPreferenceStore0186(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): SpeechOutputMode0186 = SpeechOutputMode0186.fromStored(prefs.getString(KEY_MODE, null))

    fun write(mode: SpeechOutputMode0186) {
        prefs.edit().putString(KEY_MODE, mode.storedValue).apply()
    }

    private companion object {
        const val PREFS = "rota_certa_speech_output_0186"
        const val KEY_MODE = "mode"
    }
}

object SpeechOutputPolicy0186 {
    const val CONTRACT_MARKER = "CONFIGURABLE_SPEECH_OUTPUT_0186"

    fun shouldProduceAudio(mode: SpeechOutputMode0186): Boolean = mode != SpeechOutputMode0186.Muted

    fun audioAttributes(mode: SpeechOutputMode0186): AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(
            if (mode == SpeechOutputMode0186.Alarm) {
                AudioAttributes.USAGE_ALARM
            } else {
                AudioAttributes.USAGE_MEDIA
            },
        )
        .build()
}
