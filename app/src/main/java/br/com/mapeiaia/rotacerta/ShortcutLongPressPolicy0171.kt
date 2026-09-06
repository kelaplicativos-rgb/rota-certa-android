package br.com.mapeiaia.rotacerta

import android.content.Context

/**
 * Contrato restaurado da grade flutuante.
 *
 * Toque simples continua executando a ação principal.
 * Toque longo executa a ação secundária original quando ela existe; caso contrário,
 * repete a ação principal, exatamente como antes da personalização 0.1.171.
 */
enum class ShortcutLongPressResolved0173 {
    Primary,
    Secondary,
}

object ShortcutGridPolicy0173 {
    private const val LEGACY_PREFS = "rota_certa_shortcut_long_press_0171"
    private const val MIGRATION_PREFS = "rota_certa_shortcut_grid_0173"
    private const val KEY_LEGACY_CLEARED = "legacy_customization_cleared"

    fun resolve(spec: BubbleShortcutSpec): ShortcutLongPressResolved0173 =
        if (spec.doubleTapAction != null) {
            ShortcutLongPressResolved0173.Secondary
        } else {
            ShortcutLongPressResolved0173.Primary
        }

    /** Mantém a única confirmação que já existia no comportamento legado. */
    fun requiresConfirmation(
        spec: BubbleShortcutSpec,
        resolved: ShortcutLongPressResolved0173,
    ): Boolean = spec.id == "clear_clipboard" && resolved == ShortcutLongPressResolved0173.Secondary

    fun resolvedLabel(
        spec: BubbleShortcutSpec,
        resolved: ShortcutLongPressResolved0173,
    ): String = when (resolved) {
        ShortcutLongPressResolved0173.Primary -> "executar ${spec.displayLabel}"
        ShortcutLongPressResolved0173.Secondary -> "executar a ação rápida original de ${spec.displayLabel}"
    }

    fun fixedBehaviorLabel(spec: BubbleShortcutSpec): String = when (resolve(spec)) {
        ShortcutLongPressResolved0173.Primary -> "repete a ação principal"
        ShortcutLongPressResolved0173.Secondary -> "executa a ação rápida original"
    }

    /**
     * Migração única: escolhas gravadas pela 0.1.171/0.1.172 não podem mais alterar a grade.
     * Nenhuma leitura dessas preferências participa da execução dos atalhos.
     */
    fun clearLegacyPreferences(context: Context) {
        val applicationContext = context.applicationContext
        val migration = applicationContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (migration.getBoolean(KEY_LEGACY_CLEARED, false)) return

        applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        migration.edit().putBoolean(KEY_LEGACY_CLEARED, true).apply()
    }

    fun description(spec: BubbleShortcutSpec): String = when (spec.id) {
        "route" -> "Configura a rota e acompanha o funcionamento geral do Rota Certa."
        "reading" -> "Liga ou desliga manualmente a leitura universal de qualquer tela, janela ou pop-up."
        "destination" -> "Define Casa ou ponto principal e o raio usado na decisão."
        "alerts" -> "Cria e gerencia alertas de proximidade."
        "saved_places" -> "Cria e gerencia locais salvos sem alerta automático."
        "radars" -> "Importa, consulta e remove radares armazenados."
        "appearance" -> "Ajusta tamanho, transparência, contraste e pop-up da bolinha."
        "backup" -> "Cria e restaura cópia local das configurações e dados."
        "whatsapp" -> "Na grade, captura o telefone visível e abre o WhatsApp."
        "copy_trip_confirmation" -> "Copia a confirmação formatada e permite editar a frase predefinida."
        "passenger_value" -> "Captura o valor, registra no Financeiro e permite editar a frase."
        "finance" -> "Abre o controle financeiro de receitas e despesas."
        "clear_clipboard" -> "Apaga a área de transferência; no toque longo limpa somente o cache temporário, com confirmação."
        "diagnostic" -> "Abre relatórios e gera o arquivo de depuração manual."
        "quick_replies" -> "Abre e gerencia respostas rápidas."
        "quick_links" -> "Pesquisa, abre, copia, edita e exclui links salvos localmente."
        "text_correction" -> "Sugere correções básicas e offline, com revisão antes de copiar ou substituir."
        "manual_capture" -> "Gerencia referências de aplicativos/cards e captura manual; não liga nem bloqueia a leitura do Farol."
        "stop_app" -> "Abre o controle para desligar a Leitura do Farol com segurança."
        else -> "Módulo do Rota Certa."
    }
}

// deterministic_shortcut_grid_restored_0_1_173
