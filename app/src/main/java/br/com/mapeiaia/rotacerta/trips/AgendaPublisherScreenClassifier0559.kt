package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer
import java.util.Locale

internal enum class AgendaPublisherScreen0559 {
    LOGIN,
    USER_VERIFICATION,
    ORIGIN,
    DESTINATION,
    ROUTE,
    EXTRA_STOPS,
    STOP_CONFIRMATION,
    DATE,
    TIME,
    SEATS,
    RESERVATION,
    PRICE,
    RETURN_OFFER,
    COMMENT,
    FINAL_PUBLISH,
    CONFIRMATION,
    UNKNOWN,
}

internal object AgendaPublisherScreenClassifier0559 {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    fun classify(rawBody: String): AgendaPublisherScreen0559 {
        val body = normalize(rawBody)
        return when {
            body.isBlank() -> AgendaPublisherScreen0559.UNKNOWN
            containsAny(body, "como voce deseja se conectar", "continuar com e-mail", "entrar na sua conta") -> AgendaPublisherScreen0559.LOGIN
            containsAny(body, "qual e o seu cpf", "data de nascimento") -> AgendaPublisherScreen0559.USER_VERIFICATION
            containsAny(body, "sua carona foi publicada", "carona publicada com sucesso", "caronas foram publicadas", "caronas publicadas com sucesso") -> AgendaPublisherScreen0559.CONFIRMATION
            containsAny(body, "de onde voce vai sair", "de onde voce sai", "saindo de") && !body.contains("para onde voce vai") -> AgendaPublisherScreen0559.ORIGIN
            containsAny(body, "para onde voce vai", "indo para") -> AgendaPublisherScreen0559.DESTINATION
            containsAny(body, "qual e o seu trajeto") -> AgendaPublisherScreen0559.ROUTE
            containsAny(body, "encontrar mais passageiros", "cidades de passagem", "de passagem para encontrar mais passageiros") -> AgendaPublisherScreen0559.EXTRA_STOPS
            containsAny(body, "estes sao os melhores pontos de parada", "estao bons") -> AgendaPublisherScreen0559.STOP_CONFIRMATION
            containsAny(body, "quando voce vai") -> AgendaPublisherScreen0559.DATE
            containsAny(body, "a que horas voce vai buscar seus passageiros", "que horas voce vai buscar") -> AgendaPublisherScreen0559.TIME
            containsAny(body, "quantos passageiros", "podera levar no carro") -> AgendaPublisherScreen0559.SEATS
            containsAny(body, "ative a reserva automatica", "analisar cada pedido", "reserva automatica") -> AgendaPublisherScreen0559.RESERVATION
            containsAny(body, "defina o valor por lugar", "valor recomendado", "preco maximo permitido", "quanto por lugar") -> AgendaPublisherScreen0559.PRICE
            containsAny(body, "vai e volta", "ofereca ja a sua carona para o retorno", "ofereca sua carona para o retorno") -> AgendaPublisherScreen0559.RETURN_OFFER
            containsAny(body, "comentario para seus passageiros", "gostaria de incluir um comentario") -> AgendaPublisherScreen0559.COMMENT
            isFinalPublish(body) -> AgendaPublisherScreen0559.FINAL_PUBLISH
            else -> AgendaPublisherScreen0559.UNKNOWN
        }
    }

    private fun isFinalPublish(body: String): Boolean {
        if (body.contains("suas viagens")) return false
        val hasPublishIntent = containsAny(body, "publicar carona", "oferecer carona", "oferecer 1 carona", "oferecer 2 caronas", "oferecer 3 caronas", "oferecer 4 caronas")
        val hasReviewContext = containsAny(body, "resumo", "revise", "confirme", "preco", "valor por lugar", "passageiros")
        return hasPublishIntent && hasReviewContext
    }

    private fun containsAny(body: String, vararg needles: String): Boolean = needles.any(body::contains)
}
