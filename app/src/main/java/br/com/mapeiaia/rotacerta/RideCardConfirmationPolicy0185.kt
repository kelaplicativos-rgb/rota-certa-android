package br.com.mapeiaia.rotacerta

/**
 * Confirma que a evidência pertence a um card individual antes de liberar o parser genérico.
 *
 * O inDrive expõe simultaneamente vários pedidos na lista. Nessa tela, contar endereços e usar
 * o último como destino mistura cards diferentes. O modal individual possui um marcador singular,
 * uma ação de aceite e um fechamento explícito; somente esse recorte pode chegar ao farol.
 */
data class RideCardEvidence0185(
    val analysisText: String,
    val confirmedIndividualCard: Boolean,
    val rejectedFeed: Boolean,
    val reason: String,
)

object RideCardConfirmationPolicy0185 {
    const val CONTRACT_MARKER = "CONFIRMED_INDIVIDUAL_CARD_0185"
    private const val INDRIVE_PACKAGE = "sinet.startup.indriver"

    private val individualStart = Regex(
        pattern = "(?i)(?<![\\p{L}])pedido\\s+de\\s+viagem(?![\\p{L}])",
    )
    private val closeAction = Regex(
        pattern = "(?i)(?<![\\p{L}])fechar(?![\\p{L}])",
    )
    private val acceptAction = Regex(
        pattern = "(?i)(?<![\\p{L}])(?:aceitar(?:\\s+por)?|ofere(?:ç|c)a\\s+sua\\s+tarifa)(?![\\p{L}])",
    )

    fun prepare(packageName: String?, rawText: String): RideCardEvidence0185 {
        val normalizedPackage = packageName?.trim()?.lowercase().orEmpty()
        if (normalizedPackage != INDRIVE_PACKAGE || rawText.isBlank()) {
            return RideCardEvidence0185(
                analysisText = rawText,
                confirmedIndividualCard = normalizedPackage != INDRIVE_PACKAGE,
                rejectedFeed = false,
                reason = "Leitura mantida para o parser do aplicativo selecionado.",
            )
        }

        val start = individualStart.find(rawText)
        if (start == null) {
            return rejected(rawText, "Lista/feed do inDrive sem card individual aberto.")
        }
        val close = closeAction.find(rawText, start.range.last + 1)
        if (close == null) {
            return rejected(rawText, "Card do inDrive sem fechamento individual confirmado.")
        }
        val accept = acceptAction.find(rawText, start.range.last + 1)
        if (accept == null || accept.range.first >= close.range.first) {
            return rejected(rawText, "Card do inDrive sem ação individual de aceite confirmada.")
        }

        val isolated = rawText.substring(start.range.first, close.range.last + 1).trim()
        if (isolated.isBlank()) {
            return rejected(rawText, "Recorte individual do inDrive ficou vazio.")
        }
        return RideCardEvidence0185(
            analysisText = isolated,
            confirmedIndividualCard = true,
            rejectedFeed = false,
            reason = "Card individual do inDrive confirmado e isolado.",
        )
    }

    private fun rejected(rawText: String, reason: String): RideCardEvidence0185 =
        RideCardEvidence0185(
            analysisText = rawText,
            confirmedIndividualCard = false,
            rejectedFeed = true,
            reason = reason,
        )
}
