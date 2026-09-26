package br.com.mapeiaia.rotacerta

import java.util.Locale

object ShortcutInPlacePolicy0181 {
    val overlayFirstIds = setOf(
        "route",
        "destination",
        "alerts",
        "saved_places",
        "radars",
        "appearance",
        "backup",
        "finance",
        "diagnostic",
        "quick_replies",
        "manual_capture",
        "stop_app",
        "links",
    )

    fun description(shortcutId: String): String = when (shortcutId) {
        "route" -> "Mostra o estado atual do farol sem abrir a Home."
        "destination" -> "Mostra a acao rapida de destino sem trocar de aplicativo."
        "alerts" -> "Crie um alerta no ponto atual em um pop-up sobre esta tela."
        "saved_places" -> "Salve o local atual com endereco completo sem sair desta tela."
        "radars" -> "Mostra quantos radares estao carregados sem abrir outro modulo."
        "appearance" -> "Consulta rapida da bolinha; ajustes completos continuam protegidos na Home."
        "backup" -> "Acesso seguro ao modulo sem troca automatica de tela."
        "finance" -> "Acesso seguro ao modulo sem troca automatica de tela."
        "diagnostic" -> "Acesso seguro ao diagnostico sem troca automatica de tela."
        "quick_replies" -> "Acesso seguro as respostas sem troca automatica de tela."
        "manual_capture" -> "Acesso seguro a captura sem troca automatica de tela."
        "stop_app" -> "Acesso seguro ao encerramento sem executar por engano."
        "links" -> "Acesso seguro aos links sem abrir outro aplicativo automaticamente."
        else -> "Acao da grade executada sobre a tela atual."
    }

    fun statusLine(
        shortcutId: String,
        radarColor: String,
        distanceKm: Double?,
        radarCount: Int,
    ): String = when (shortcutId) {
        "route" -> buildString {
            append("Farol: ")
            append(radarColor)
            distanceKm?.let {
                append(" • ")
                append(String.format(Locale("pt", "BR"), "%.1f km", it))
            }
        }
        "radars" -> "Radares carregados: $radarCount"
        else -> "Voce continua no aplicativo e na tela que estava usando."
    }
}
