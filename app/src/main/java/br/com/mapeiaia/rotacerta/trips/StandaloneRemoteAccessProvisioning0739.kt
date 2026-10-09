package br.com.mapeiaia.rotacerta.trips

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class StandaloneRemoteConnection0739(
    val access: StandaloneCoversRemoteAccess0736,
    val pushRegistered: Boolean,
) {
    val message: String
        get() = if (pushRegistered) {
            "Acesso privado preparado. Use o toggle abaixo para permitir ou bloquear consultas remotas."
        } else {
            "Acesso privado preparado. O toggle abaixo controla as consultas, mesmo sem token FCM."
        }
}

// Creating the private access must not depend on obtaining an FCM token.
internal suspend fun provisionStandaloneRemoteAccess0739(
    provisionAccess: suspend () -> StandaloneCoversRemoteAccess0736,
    registerPush: suspend () -> Boolean,
): StandaloneRemoteConnection0739 {
    val access = provisionAccess()
    check(access.configured && access.tripQueryBaseUrl.startsWith("https://")) {
        "O servidor não forneceu um acesso remoto completo e válido. Tente novamente."
    }
    val registered = try {
        withTimeoutOrNull(20_000L) { registerPush() } == true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    return StandaloneRemoteConnection0739(access, registered)
}

internal fun standaloneRemoteClipboardText0739(access: StandaloneCoversRemoteAccess0736): String {
    check(access.configured && access.tripQueryBaseUrl.startsWith("https://")) {
        "O acesso remoto precisa ser atualizado antes de copiar."
    }
    return listOf(
        "Rota Certa — coleta remota avulsa",
        "Solicitar capas: ${access.refreshUrl}",
        "Consultar capas: ${access.latestUrl}",
        "Consulta HTML por viagem: ${access.tripQueryBaseUrl}",
        "Solicitar detalhe: ${access.tripQueryBaseUrl}/trip/{profileUuid}/{tripId}/refresh",
        "Consultar detalhe: ${access.tripQueryBaseUrl}/trip/{profileUuid}/{tripId}/latest",
        "Leia primeiro as capas COMPLETE para confirmar perfil, tripId, rota e data. " +
            "No detalhe, só informe valor individual se vier de HTML confirmado; jamais divida o preço da capa.",
    ).joinToString("\n")
}
