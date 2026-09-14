package br.com.mapeiaia.rotacerta.trips

/**
 * Política de edição do Executor declarativo da Agenda.
 *
 * Uma execução persistida nunca deve preencher silenciosamente o editor.
 * O JSON em edição é sempre uma intenção nova e explícita do usuário.
 */
internal object AgendaScriptEditorPolicy0561 {
    fun initialEditorText(active: AgendaTripExecution0558?): String = ""

    fun recoveredExecutionMessage(active: AgendaTripExecution0558): String =
        "Existe uma execução pendente preservada, mas o editor foi aberto vazio para receber o JSON atual. " +
            "Use ‘Carregar execução pendente’ somente se quiser inspecionar o script anterior.\n" +
            "${active.items.size} instrução(ões) pendente(s)."

    fun executionGate(active: AgendaTripExecution0558?, candidateHash: String): AgendaScriptExecutionGate0561 = when {
        active == null -> AgendaScriptExecutionGate0561.ALLOW_NEW
        active.scriptHash == candidateHash -> AgendaScriptExecutionGate0561.RESUME_SAME
        else -> AgendaScriptExecutionGate0561.BLOCKED_BY_OTHER_ACTIVE
    }
}

internal enum class AgendaScriptExecutionGate0561 {
    ALLOW_NEW,
    RESUME_SAME,
    BLOCKED_BY_OTHER_ACTIVE,
}
