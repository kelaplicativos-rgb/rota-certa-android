package br.com.mapeiaia.rotacerta.trips

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class StandaloneRemoteConnection0739(
    val access: StandaloneCoversRemoteAccess0736,
    val pushRegistered: Boolean,
) {
    val message: String
        get() = if (pushRegistered) {
            "Acesso privado disponível e escuta remota ligada, sem FCM. " +
                "O pop-up só é dispensado com autorização automática ativada no aparelho."
        } else {
            "Acesso privado disponível. Ative a escuta remota abaixo para receber pedidos sem token FCM."
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
    return "Rota Certa — coleta remota avulsa\n" +
        "Solicitar capas: ${access.refreshUrl}\n" +
        "Consultar capas: ${access.latestUrl}\n" +
        "Consulta HTML por viagem: ${access.tripQueryBaseUrl}\\n" +
        "Solicitar detalhe: ${access.tripQueryBaseUrl}/trip/{profileUuid}/{tripId}/refresh\\n" +
        "Consultar detalhe: ${access.tripQueryBaseUrl}/trip/{profileUuid}/{tripId}/latest\\n" +
        "Leia primeiro as capas COMPLETE para confirmar perfil, tripId, rota e data. " +
        "No detalhe, só informe valor individual se vier de HTML confirmado; jamais divida o preço da capa."
}
