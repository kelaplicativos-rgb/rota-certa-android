package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/** Decide se o mesmo bitmap usado pelo OCR deve ser preservado como tela completa. */
object FullScreenRideCapturePolicy {
    private val strongRideMarkers = listOf(
        "pedido de viagem",
        "pedidos de viagem",
        "aceitar por",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "preco justo",
        "preço justo",
        "uberx",
        "negocia",
    )

    fun shouldSaveCandidate(
        packageSelected: Boolean,
        automaticCaptureEnabled: Boolean,
        text: String,
        fields: RideFields,
    ): Boolean {
        if (!packageSelected || !automaticCaptureEnabled) return false
        if (fields.destination.isNullOrBlank()) return false
        val normalized = normalize(text)
        return strongRideMarkers.any(normalized::contains)
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
