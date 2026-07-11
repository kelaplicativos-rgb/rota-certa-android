package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplate
import br.com.mapeiaia.rotacerta.RideFields

/**
 * Orquestrador do Core.
 * O fluxo profissional fica assim:
 * 1) identificar app/modulo;
 * 2) classificar tela: lista, parcial, card aberto ou fora;
 * 3) permitir match de assinatura e rota somente para card aberto.
 */
object RotaCertaCore {
    private val modules: List<RideAppCoreModule> = listOf(
        InDriveCoreModule,
        UberCoreModule,
        NinetyNineCoreModule,
    )

    fun classifyScreen(
        packageName: String?,
        text: String,
        fields: RideFields,
    ): RideScreenClassification {
        val normalizedPackage = packageName?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return RideScreenClassification(
                kind = RideScreenKind.NotRideApp,
                packageName = packageName,
                reason = "Pacote nao informado ao Rota Certa Core.",
                confidence = 0.0,
            )
        val snapshot = RideScreenSnapshot(normalizedPackage, text, fields)
        val module = modules.firstOrNull { it.supports(normalizedPackage) }
            ?: UniversalCoreModule
        return module.classify(snapshot)
    }

    fun matchRegisteredOpenCard(
        packageName: String?,
        text: String,
        fields: RideFields,
        templates: List<RideCardTemplate>,
    ): CoreCardMatchDecision {
        val classification = classifyScreen(packageName, text, fields)
        if (!classification.canAnalyzeRoute) {
            return CoreCardMatchDecision(
                classification = classification,
                matched = null,
                canAnalyzeRoute = false,
                reason = classification.reason,
            )
        }
        val matchResult = CoreCardMatchEngine.match(text, packageName, templates)
        val match = matchResult.match
        return CoreCardMatchDecision(
            classification = classification,
            matched = match?.template?.name,
            canAnalyzeRoute = matchResult.accepted && match != null,
            reason = if (matchResult.accepted && match != null) {
                matchResult.reason
            } else {
                matchResult.reason
            },
        )
    }
}

data class CoreCardMatchDecision(
    val classification: RideScreenClassification,
    val matched: String?,
    val canAnalyzeRoute: Boolean,
    val reason: String,
)
