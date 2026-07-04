package br.com.mapeiaia.rotacerta

import android.speech.tts.TextToSpeech

interface ProximitySpeech {
    fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean
    fun speakProximityAlert(place: SavedPlace): Boolean
    fun proximityAlertSpeech(place: SavedPlace): String
}

class LiveSpeechEngine(
    private val textToSpeechProvider: () -> TextToSpeech?,
    private val isReady: () -> Boolean,
    private val trace: (String) -> Unit,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ProximitySpeech {
    override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean {
        return speak(
            text = importedRadarSpeech(radar, distanceMeters),
            utteranceId = "imported-radar-${radar.id}-${nowProvider()}",
            notReadyTrace = "tts.not_ready imported_radar=${radar.id}",
        )
    }

    override fun speakProximityAlert(place: SavedPlace): Boolean {
        return speak(
            text = proximityAlertSpeech(place),
            utteranceId = "proximity-alert-${place.id}-${nowProvider()}",
            notReadyTrace = "tts.not_ready proximity_alert=${place.id}",
        )
    }

    override fun proximityAlertSpeech(place: SavedPlace): String {
        val name = place.name.trim().ifBlank { "Alerta de proximidade" }
        return "$name se aproximando"
    }

    private fun speak(text: String, utteranceId: String, notReadyTrace: String): Boolean {
        if (!isReady()) {
            trace(notReadyTrace)
            return false
        }
        val result = textToSpeechProvider()?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        ) ?: TextToSpeech.ERROR
        return result == TextToSpeech.SUCCESS
    }
}
