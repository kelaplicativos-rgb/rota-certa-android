package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object ManualRideCardCapturePolicy {
    data class Evaluation(
        val canStoreImage: Boolean,
        val canCreateTemplate: Boolean,
        val reason: String,
    )

    private val strongMarkers = listOf(
        "pedido de viagem",
        "pedidos de viagem",
        "aceitar por",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "preco justo",
        "preço justo",
        "uberx",
        "negocia",
        "perfil premium",
        "perfil essencial",
    )

    fun evaluate(
        packageSelected: Boolean,
        text: String,
        bitmapWidth: Int,
        bitmapHeight: Int,
        looksLikeRideCard: Boolean,
    ): Evaluation {
        if (!packageSelected) {
            return Evaluation(false, false, "O aplicativo atual não está selecionado no Rota Certa.")
        }
        if (bitmapWidth < MIN_IMAGE_EDGE || bitmapHeight < MIN_IMAGE_EDGE) {
            return Evaluation(false, false, "A imagem capturada ficou pequena demais.")
        }
        val normalized = normalize(text)
        val strongMarker = strongMarkers.any(normalized::contains)
        val enoughText = normalized.length >= MIN_TEXT_LENGTH
        val canStore = enoughText && (strongMarker || looksLikeRideCard)
        if (!canStore) {
            return Evaluation(false, false, "Não encontrei um card de corrida legível nesta tela.")
        }
        return Evaluation(
            canStoreImage = true,
            canCreateTemplate = looksLikeRideCard,
            reason = if (looksLikeRideCard) {
                "Print completo e modelo podem ser salvos."
            } else {
                "Print completo pode ser salvo para conferência, mas o modelo ainda precisa de um card mais nítido."
            },
        )
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MIN_TEXT_LENGTH = 24
    private const val MIN_IMAGE_EDGE = 180
}
