package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AgendaTripScriptExecutorActivity0558 : Activity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var store: AgendaTripScriptStore0558
    private lateinit var publisherStore: AgendaBatchPublisherStore
    private lateinit var accounts: BlaBlaDynamicAccountRegistry
    private var launchedIndex: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AgendaTripScriptStore0558(this)
        publisherStore = AgendaBatchPublisherStore(this)
        accounts = BlaBlaDynamicAccountRegistry(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(TextView(this).apply { text = "Criar viagens por script"; textSize = 22f })
        body.addView(TextView(this).apply {
            text = "Contrato semântico da Agenda. Não executa JavaScript, shell ou código arbitrário. A publicação é sequencial e só é concluída com evidência externa + canônica."
        })
        input = EditText(this).apply {
            hint = "Cole aqui o JSON gerado pela Agenda"; minLines = 10; maxLines = 22; setHorizontallyScrolling(false)
        }
        body.addView(input)
        button(body, "Validar", ::validateOnly)
        button(body, "Pré-visualizar", ::preview)
        button(body, "Simular execução", ::dryRun)
        button(body, "Executar", ::executeNew)
        button(body, "Reconciliar / retomar", ::resumeAndReconcile)
        button(body, "Cancelar execução", ::cancelExecution)
        button(body, "Histórico", ::showHistory)
        button(body, "Limpar") { input.setText(""); output.text = "" }
        output = TextView(this).apply { setPadding(0, 18, 0, 40); setTextIsSelectable(true) }
        body.addView(output)
        setContentView(root)

        store.active()?.let { active ->
            input.setText(active.rawScript)
            output.text = "Execução interrompida encontrada.\n${summary(active)}\nUse Reconciliar / retomar."
            AgendaTripScriptTrace0558.record(this, "EXECUTION_RECOVERED", active.executionId, active.scriptId, detail = "items=${active.items.size}")
        }
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }

    private fun parseAndPlan(): AgendaTripScriptPlan0558? = try {
        val raw = input.text.toString()
        AgendaTripScriptTrace0558.record(this, "SCRIPT_RECEIVED", detail = "bytes=${raw.toByteArray().size}")
        val script = AgendaTripScriptParser0558.parse(raw)
        val plan = AgendaTripScriptPlanner0558.plan(this, script)
        AgendaTripScriptTrace0558.record(this, "SCRIPT_VALIDATED", scriptId = script.scriptId,
            detail = "hash=${script.scriptHash.take(20)} instructions=${script.instructions.size} blocking=${plan.blockingIssues.size}")
        plan
    } catch (error: Throwable) {
        AgendaTripScriptTrace0558.record(this, "SCRIPT_REJECTED", detail = "reason=${error.message.orEmpty().take(180)}")
        output.text = "Script inválido ❌\n${error.message ?: "Erro desconhecido"}"
        null
    }

    private fun validateOnly() {
        val plan = parseAndPlan() ?: return
        output.text = buildString {
            append(if (plan.blockingIssues.isEmpty()) "Script válido ✅" else "Script bloqueado ❌").append('\n')
            append("ScriptId: ${plan.script.scriptId}\nHash: ${plan.script.scriptHash.take(24)}…\n")
            append("Viagens: ${plan.items.size}\nProntas para criar: ${plan.readyCount}\n")
            append("Já existentes/concluídas: ${plan.skippedCount}\nErros bloqueantes: ${plan.blockingIssues.size}\n")
            plan.issues.forEach { append(if (it.blocking) "❌ " else "⚠️ ").append(it.instructionIndex?.let { n -> "Viagem $n — " }.orEmpty()).append(it.message).append('\n') }
        }
    }

    private fun preview() {
        val plan = parseAndPlan() ?: return
        output.text = buildString {
            append("PRÉ-VISUALIZAÇÃO\n\n")
            plan.items.forEach { item ->
                val i = item.instruction
                append("${i.index}. ${i.date} • ${i.departureTime}\n${i.origin} → ${i.destination}\n")
                append("${i.seats} lugares • perfil UUID …${i.profileUuid.takeLast(8)}\nAÇÃO: ")
                append(when (item.action) {
                    AgendaTripScriptState0558.READY -> "CRIAR + PUBLICAR"
                    AgendaTripScriptState0558.SKIPPED_EXISTING -> "IGNORAR — viagem já existente"
                    AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED -> "IGNORAR — instrução já concluída"
                    else -> "BLOQUEADO — ${item.note}"
                }).append("\n\n")
            }
        }
    }

    private fun dryRun() {
        val plan = parseAndPlan() ?: return
        AgendaTripScriptTrace0558.record(this, "DRY_RUN", scriptId = plan.script.scriptId,
            detail = "ready=${plan.readyCount} skipped=${plan.skippedCount} blocking=${plan.blockingIssues.size}")
        output.text = "Simulação concluída\n${plan.items.size} instruções recebidas\n${plan.readyCount} seriam criadas\n${plan.skippedCount} já existem/já foram concluídas\n${plan.blockingIssues.size} conflitos bloqueantes\nNenhuma publicação foi iniciada."
    }

    private fun executeNew() {
        if (store.active() != null) { output.text = "Execução já em andamento. Use Reconciliar / retomar."; return }
        val plan = parseAndPlan() ?: return
        if (plan.blockingIssues.isNotEmpty()) {
            output.text = "Execução bloqueada ❌\n" + plan.blockingIssues.joinToString("\n") { "Viagem ${it.instructionIndex ?: "?"}: ${it.message}" }
            return
        }
        val execution = AgendaTripScriptTrace0558.newExecution(plan, input.text.toString())
        store.save(execution)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_STARTED", execution.executionId, execution.scriptId,
            detail = "instructions=${execution.items.size} ready=${execution.items.count { it.state == AgendaTripScriptState0558.READY }}")
        startNext(execution)
    }

    private fun resumeAndReconcile() {
        val active: AgendaTripExecution0558 = store.active() ?: run {
            output.text = "Nenhuma execução interrompida."
            return
        }
        AgendaTripScriptTrace0558.record(this, "EXECUTION_RESUMED", active.executionId, active.scriptId)
        var next: AgendaTripExecution0558 = active
        var changed = false
        active.items.forEachIndexed { index, item ->
            if (item.state in RECONCILE_STATES) {
                AgendaTripScriptTrace0558.record(this, "RECONCILIATION_STARTED", next.executionId, next.scriptId, item.instruction.index)
                val reconciled = AgendaTripScriptPlanner0558.reconcile(this, item.copy(state = AgendaTripScriptState0558.RECONCILING))
                next = next.copy(items = next.items.mapIndexed { i, old -> if (i == index) reconciled else old })
                changed = true
                when (reconciled.state) {
                    AgendaTripScriptState0558.SYNCED -> {
                        store.markCompleted(reconciled.fingerprint)
                        AgendaTripScriptTrace0558.record(this, "RECONCILIATION_FOUND", next.executionId, next.scriptId, item.instruction.index,
                            "externalTripIdPresent=true canonicalTripIdPresent=true")
                    }
                    AgendaTripScriptState0558.CONFIRMED -> AgendaTripScriptTrace0558.record(this, "EXTERNAL_TRIP_ID_CAPTURED", next.executionId, next.scriptId, item.instruction.index,
                        "externalTripIdPresent=true canonicalSyncPending=true")
                    else -> AgendaTripScriptTrace0558.record(this, "RECONCILIATION_NOT_FOUND", next.executionId, next.scriptId, item.instruction.index,
                        "failClosed=true republish=false")
                }
            }
        }
        if (changed) store.save(next)
        startNext(next)
    }

    private fun startNext(execution: AgendaTripExecution0558) {
        if (execution.cancelled) { completeIfTerminal(execution); return }
        val barrier = execution.items.firstOrNull { it.state in RECONCILE_STATES }
        if (barrier != null) {
            store.save(execution)
            output.text = "Execução parcialmente concluída ⚠️\n${summary(execution)}\nViagem ${barrier.instruction.index} aguarda reconciliação. Por segurança ela NÃO será republicada. Sincronize a Agenda/coletor e use Reconciliar / retomar."
            return
        }
        val pair = execution.items.withIndex().firstOrNull { it.value.state == AgendaTripScriptState0558.READY }
        if (pair == null) { completeIfTerminal(execution); return }
        val index = pair.index
        val item = pair.value
        val account = accounts.get(item.accountId)
        if (account == null || account.profileUuid?.equals(item.instruction.profileUuid, ignoreCase = true) != true) {
            val failed = item.copy(state = AgendaTripScriptState0558.FAILED_BLOCKING, lastError = "profile_identity_changed")
            val next = execution.copy(items = execution.items.mapIndexed { i, old -> if (i == index) failed else old })
            store.save(next)
            AgendaTripScriptTrace0558.record(this, "PROFILE_REJECTED", execution.executionId, execution.scriptId, item.instruction.index, "reason=profile_identity_changed")
            output.text = "Execução bloqueada: o perfil autenticado não corresponde ao profileUuid solicitado."
            return
        }
        AgendaTripScriptTrace0558.record(this, "PROFILE_CONFIRMED", execution.executionId, execution.scriptId, item.instruction.index, "profileUuidPresent=true")
        val creating = item.copy(state = AgendaTripScriptState0558.CREATING, attempts = item.attempts + 1, lastError = "")
        val next = execution.copy(items = execution.items.mapIndexed { i, old -> if (i == index) creating else old })
        store.save(next)
        publisherStore.replaceQueue(listOf(AgendaTripScriptPlanner0558.toPublisherBatch(creating, account.displayLabel)))
        launchedIndex = index
        AgendaTripScriptTrace0558.record(this, "CREATE_TRIP_OPENED", execution.executionId, execution.scriptId, item.instruction.index,
            "attempt=${creating.attempts} sequential=true")
        AgendaTripScriptTrace0558.record(this, "PUBLISH_SUBMITTED", execution.executionId, execution.scriptId, item.instruction.index,
            "publisher=AgendaBatchPublisherActivity evidencePending=true")
        output.text = "Executando script\nViagem ${item.instruction.index} de ${execution.items.size}\n${item.instruction.origin} → ${item.instruction.destination}\n${item.instruction.date} • ${item.instruction.departureTime}\nEstado: publicando…"
        startActivityForResult(Intent(this, AgendaBatchPublisherActivity::class.java), REQUEST_PUBLISH)
    }

    @Deprecated("Existing publisher Activity contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PUBLISH) return
        val active = store.active() ?: return
        val index = launchedIndex ?: active.items.indexOfFirst { it.state == AgendaTripScriptState0558.CREATING }
        launchedIndex = null
        if (index !in active.items.indices) return
        val current = active.items[index]
        val ambiguous = current.copy(
            state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
            lastError = if (resultCode == RESULT_OK) "publisher_ui_success_requires_readback" else "publisher_interrupted_requires_reconciliation",
        )
        var next = active.copy(items = active.items.mapIndexed { i, old -> if (i == index) ambiguous else old })
        store.save(next)
        AgendaTripScriptTrace0558.record(this, "PUBLISH_ACTION_RETURNED", next.executionId, next.scriptId, current.instruction.index,
            "resultOk=${resultCode == RESULT_OK} confirmed=false")
        val reconciled = AgendaTripScriptPlanner0558.reconcile(this, ambiguous.copy(state = AgendaTripScriptState0558.RECONCILING))
        next = next.copy(items = next.items.mapIndexed { i, old -> if (i == index) reconciled else old })
        store.save(next)
        if (reconciled.state == AgendaTripScriptState0558.SYNCED) {
            store.markCompleted(reconciled.fingerprint)
            AgendaTripScriptTrace0558.record(this, "PUBLISH_SUCCESS", next.executionId, next.scriptId, current.instruction.index,
                "externalEvidence=true canonicalEvidence=true")
            AgendaTripScriptTrace0558.record(this, "CANONICAL_TRIP_PERSISTED", next.executionId, next.scriptId, current.instruction.index,
                "canonicalTripIdPresent=true")
            startNext(next)
        } else {
            val message = data?.getStringExtra(AgendaBatchPublisherActivity.EXTRA_MESSAGE).orEmpty()
            output.text = "Publicação enviada; confirmação ainda insuficiente ⚠️\n${summary(next)}\n${message.take(240)}\nO executor NÃO republicará esta viagem. Sincronize a Agenda e use Reconciliar / retomar."
        }
    }

    private fun cancelExecution() {
        val active = store.active() ?: run { output.text = "Nenhuma execução em andamento."; return }
        val next = active.copy(cancelled = true, items = active.items.map { item ->
            if (item.state == AgendaTripScriptState0558.READY) item.copy(state = AgendaTripScriptState0558.CANCELLED_NOT_STARTED) else item
        })
        store.save(next)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_CANCEL_REQUESTED", next.executionId, next.scriptId,
            detail = "currentExternalActionPreserved=true remainingCancelled=true")
        output.text = "Cancelamento registrado. Novas viagens não serão iniciadas; ações externas já iniciadas continuam exigindo reconciliação.\n${summary(next)}"
    }

    private fun completeIfTerminal(execution: AgendaTripExecution0558) {
        val nonTerminal = execution.items.any { it.state == AgendaTripScriptState0558.READY || it.state == AgendaTripScriptState0558.CREATING || it.state in RECONCILE_STATES }
        if (nonTerminal) { store.save(execution); output.text = summary(execution); return }
        store.finish(execution)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_FINISHED", execution.executionId, execution.scriptId,
            detail = "synced=${execution.items.count { it.state == AgendaTripScriptState0558.SYNCED }} skipped=${execution.items.count { it.state.name.startsWith("SKIPPED") }} failures=${execution.items.count { it.state.name.startsWith("FAILED") }} cancelled=${execution.cancelled}")
        output.text = "Execução concluída${if (execution.items.any { it.state.name.startsWith("FAILED") }) " com falhas ⚠️" else " ✅"}\n${summary(execution)}\nAgenda/Timeline somente são consideradas sincronizadas nos itens SYNCED."
    }

    private fun showHistory() {
        val history = store.history()
        output.text = if (history.isEmpty()) "Nenhuma execução concluída." else history.joinToString("\n\n") { e ->
            "SCRIPT ${e.scriptId}\nExecutionId: ${e.executionId}\nHash: ${e.scriptHash.take(24)}…\n${summary(e)}"
        }
    }

    private fun summary(execution: AgendaTripExecution0558): String = buildString {
        append("${execution.items.size} instruções\n")
        append("✅ sincronizadas: ${execution.items.count { it.state == AgendaTripScriptState0558.SYNCED }}\n")
        append("⏭️ já existentes/concluídas: ${execution.items.count { it.state in setOf(AgendaTripScriptState0558.SKIPPED_EXISTING, AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED) }}\n")
        append("⚠️ reconciliação pendente: ${execution.items.count { it.state in RECONCILE_STATES }}\n")
        append("❌ falhas: ${execution.items.count { it.state in setOf(AgendaTripScriptState0558.FAILED_BLOCKING, AgendaTripScriptState0558.FAILED_RETRYABLE) }}\n")
        execution.items.forEach { append("${it.instruction.index} — ${it.state.name}${if (it.lastError.isBlank()) "" else " (${it.lastError})"}\n") }
    }

    companion object {
        private const val REQUEST_PUBLISH = 5581
        private val RECONCILE_STATES = setOf(
            AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
            AgendaTripScriptState0558.RECONCILING,
            AgendaTripScriptState0558.CONFIRMED,
            AgendaTripScriptState0558.CANONICALIZED,
        )
    }
}
