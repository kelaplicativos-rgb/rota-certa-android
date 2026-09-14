package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry

class AgendaTripScriptExecutorActivity0558 : Activity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var store: AgendaTripScriptStore0558
    private lateinit var publisherStore: AgendaBatchPublisherStore
    private lateinit var accounts: BlaBlaDynamicAccountRegistry
    private var executionLaunchedItemIndex: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AgendaTripScriptStore0558(this)
        publisherStore = AgendaBatchPublisherStore(this)
        accounts = BlaBlaDynamicAccountRegistry(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        body.addView(TextView(this).apply {
            text = "Criar viagens por script"
            textSize = 22f
        })
        body.addView(TextView(this).apply {
            text = "Contrato semântico da Agenda. O executor não executa JavaScript, shell ou código arbitrário. Publicação é sequencial e confirmação exige evidência externa/canônica."
        })

        input = EditText(this).apply {
            hint = "Cole aqui o JSON gerado pela Agenda"
            minLines = 10
            maxLines = 22
            setHorizontallyScrolling(false)
        }
        body.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        addButton(body, "Validar") { validateOnly() }
        addButton(body, "Pré-visualizar") { preview() }
        addButton(body, "Simular execução") { dryRun() }
        addButton(body, "Executar") { executeNew() }
        addButton(body, "Reconciliar / retomar") { resumeAndReconcile() }
        addButton(body, "Cancelar execução") { cancelExecution() }
        addButton(body, "Histórico") { showHistory() }
        addButton(body, "Limpar") { input.setText(""); output.text = "" }

        output = TextView(this).apply {
            setPadding(0, 18, 0, 40)
            textIsSelectable = true
        }
        body.addView(output)
        setContentView(root)

        store.active()?.let { active ->
            input.setText(active.rawScript)
            output.text = "Execução interrompida encontrada.\n${summary(active)}\n\nUse Reconciliar / retomar."
            AgendaTripScriptTrace0558.record(this, "EXECUTION_RECOVERED", active.executionId, active.scriptId, detail = "items=${active.items.size}")
        }
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun parseAndPlan(): AgendaTripScriptPlan0558? = try {
        AgendaTripScriptTrace0558.record(this, "SCRIPT_RECEIVED", detail = "bytes=${input.text.toString().toByteArray().size}")
        val script = AgendaTripScriptParser0558.parse(input.text.toString())
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
            append("Script ").append(if (plan.blockingIssues.isEmpty()) "válido ✅" else "bloqueado ❌").append('\n')
            append("ScriptId: ").append(plan.script.scriptId).append('\n')
            append("Hash: ").append(plan.script.scriptHash.take(24)).append("…\n")
            append("Viagens: ").append(plan.items.size).append('\n')
            append("Prontas para criar: ").append(plan.readyCount).append('\n')
            append("Já existentes/concluídas: ").append(plan.skippedCount).append('\n')
            append("Erros bloqueantes: ").append(plan.blockingIssues.size).append('\n')
            plan.issues.forEach { issue -> append(if (issue.blocking) "❌ " else "⚠️ ").append(issue.instructionIndex?.let { "Viagem $it — " }.orEmpty()).append(issue.message).append('\n') }
        }
    }

    private fun preview() {
        val plan = parseAndPlan() ?: return
        output.text = buildString {
            append("PRÉ-VISUALIZAÇÃO\n\n")
            plan.items.forEach { item ->
                val i = item.instruction
                append(i.index).append(". ").append(i.date).append(" • ").append(i.departureTime).append('\n')
                append(i.origin).append(" → ").append(i.destination).append('\n')
                append(i.seats).append(" lugares • perfil UUID …").append(i.profileUuid.takeLast(8)).append('\n')
                append("AÇÃO: ").append(when (item.action) {
                    AgendaTripScriptState0558.READY -> "CRIAR + PUBLICAR"
                    AgendaTripScriptState0558.SKIPPED_EXISTING -> "IGNORAR — viagem já existente"
                    AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED -> "IGNORAR — instrução já concluída"
                    else -> "BLOQUEADO — ${item.note}"
                }).append("\n\n")
            }
            if (plan.issues.isNotEmpty()) {
                append("Validação:\n")
                plan.issues.forEach { append(if (it.blocking) "❌ " else "⚠️ ").append(it.message).append('\n') }
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
        if (store.active() != null) {
            output.text = "Execução já em andamento. Use Reconciliar / retomar."
            return
        }
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
        val active = store.active()
        if (active == null) {
            output.text = "Nenhuma execução interrompida."
            return
        }
        AgendaTripScriptTrace0558.record(this, "EXECUTION_RESUMED", active.executionId, active.scriptId)
        var next = active
        var changed = false
        next.items.forEachIndexed { index, item ->
            if (item.state in setOf(AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS, AgendaTripScriptState0558.RECONCILING, AgendaTripScriptState0558.CONFIRMED, AgendaTripScriptState0558.CANONICALIZED)) {
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
                        "externalTripIdPresent=true canonical_sync_pending=true")
                    else -> AgendaTripScriptTrace0558.record(this, "RECONCILIATION_NOT_FOUND", next.executionId, next.scriptId, item.instruction.index,
                        "failClosed=true republish=false")
                }
            }
        }
        if (changed) store.save(next)
        startNext(next)
    }

    private fun startNext(execution: AgendaTripExecution0558) {
        if (execution.cancelled) {
            completeIfTerminal(execution)
            return
        }
        val blockingReconcile = execution.items.firstOrNull { it.state in setOf(AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS, AgendaTripScriptState0558.RECONCILING, AgendaTripScriptState0558.CONFIRMED, AgendaTripScriptState0558.CANONICALIZED) }
        if (blockingReconcile != null) {
            store.save(execution)
            output.text = "Execução parcialmente concluída ⚠️\n${summary(execution)}\n\nViagem ${blockingReconcile.instruction.index} aguarda evidência de reconciliação. Por segurança ela NÃO será republicada. Sincronize a Agenda/coletor e use Reconciliar / retomar."
            return
        }
        val pair = execution.items.withIndex().firstOrNull { it.value.state == AgendaTripScriptState0558.READY }
        if (pair == null) {
            completeIfTerminal(execution)
            return
        }
        val index = pair.index
        val item = pair.value
        val account = accounts.get(item.accountId)
        if (account == null || account.profileUuid?.equals(item.instruction.profileUuid, ignoreCase = true) != true) {
            val failed = item.copy(state = AgendaTripScriptState0558.FAILED_BLOCKING, lastError = "profile_identity_changed", updatedAtMillis = System.currentTimeMillis())
            val next = execution.copy(items = execution.items.mapIndexed { i, old -> if (i == index) failed else old })
            store.save(next)
            AgendaTripScriptTrace0558.record(this, "PROFILE_REJECTED", execution.executionId, execution.scriptId, item.instruction.index, "reason=profile_identity_changed")
            output.text = "Execução bloqueada: o perfil autenticado não corresponde mais ao profileUuid solicitado."
            return
        }
        AgendaTripScriptTrace0558.record(this, "PROFILE_CONFIRMED", execution.executionId, execution.scriptId, item.instruction.index, "profileUuidPresent=true")
        val creating = item.copy(state = AgendaTripScriptState0558.CREATING, attempts = item.attempts + 1, lastError = "", updatedAtMillis = System.currentTimeMillis())
        val next = execution.copy(items = execution.items.mapIndexed { i, old -> if (i == index) creating else old })
        store.save(next)
        publisherStore.replaceQueue(listOf(AgendaTripScriptPlanner0558.toPublisherBatch(creating, account.displayLabel)))
        executionLaunchedItemIndex = index
        AgendaTripScriptTrace0558.record(this, "CREATE_TRIP_OPENED", execution.executionId, execution.scriptId, item.instruction.index,
            "attempt=${creating.attempts} sequential=true")
        AgendaTripScriptTrace0558.record(this, "PUBLISH_SUBMITTED", execution.executionId, execution.scriptId, item.instruction.index,
            "publisher=AgendaBatchPublisherActivity evidencePending=true")
        output.text = "Executando script\nViagem ${item.instruction.index} de ${execution.items.size}\n${item.instruction.origin} → ${item.instruction.destination}\n${item.instruction.date} • ${item.instruction.departureTime}\n\nEstado: publicando…"
        startActivityForResult(Intent(this, AgendaBatchPublisherActivity::class.java), REQUEST_PUBLISH)
    }

    @Deprecated("Legacy Activity result is intentional: publisher is an existing Activity contract.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PUBLISH) return
        val active = store.active() ?: return
        val index = executionLaunchedItemIndex ?: active.items.indexOfFirst { it.state == AgendaTripScriptState0558.CREATING }
        executionLaunchedItemIndex = null
        if (index !in active.items.indices) return
        val current = active.items[index]
        // Existing publisher success is only UI evidence. Never mark CONFIRMED from resultCode.
        val ambiguous = current.copy(
            state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
            lastError = if (resultCode == RESULT_OK) "publisher_ui_success_requires_readback" else "publisher_interrupted_requires_reconciliation",
            updatedAtMillis = System.currentTimeMillis(),
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
            output.text = "Publicação enviada; confirmação ainda não é suficiente ⚠️\n${summary(next)}\n${message.take(240)}\n\nO executor entrou em reconciliação e NÃO republicará esta viagem. Sincronize a Agenda e use Reconciliar / retomar."
        }
    }

    private fun cancelExecution() {
        val active = store.active() ?: run { output.text = "Nenhuma execução em andamento."; return }
        val next = active.copy(
            cancelled = true,
            items = active.items.map { item -> if (item.state == AgendaTripScriptState0558.READY) item.copy(state = AgendaTripScriptState0558.CANCELLED_NOT_STARTED) else item },
        )
        store.save(next)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_CANCEL_REQUESTED", next.executionId, next.scriptId,
            detail = "currentExternalActionPreserved=true remainingCancelled=true")
        output.text = "Cancelamento registrado. Novas viagens não serão iniciadas; qualquer ação externa já iniciada continua exigindo reconciliação.\n${summary(next)}"
    }

    private fun completeIfTerminal(execution: AgendaTripExecution0558) {
        val nonTerminal = execution.items.any { it.state in setOf(AgendaTripScriptState0558.READY, AgendaTripScriptState0558.CREATING, AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS, AgendaTripScriptState0558.RECONCILING, AgendaTripScriptState0558.CONFIRMED, AgendaTripScriptState0558.CANONICALIZED) }
        if (nonTerminal) { store.save(execution); output.text = summary(execution); return }
        store.finish(execution)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_FINISHED", execution.executionId, execution.scriptId,
            detail = "synced=${execution.items.count { it.state == AgendaTripScriptState0558.SYNCED }} skipped=${execution.items.count { it.state.name.startsWith("SKIPPED") }} failures=${execution.items.count { it.state.name.startsWith("FAILED") }} cancelled=${execution.cancelled}")
        output.text = "Execução concluída${if (execution.items.any { it.state.name.startsWith("FAILED") }) " com falhas ⚠️" else " ✅"}\n${summary(execution)}\n\nAgenda/Timeline somente são consideradas sincronizadas nos itens com estado SYNCED."
    }

    private fun showHistory() {
        val history = store.history()
        output.text = if (history.isEmpty()) "Nenhuma execução concluída." else history.joinToString("\n\n") { e ->
            "SCRIPT ${e.scriptId}\nExecutionId: ${e.executionId}\nHash: ${e.scriptHash.take(24)}…\n${summary(e)}"
        }
    }

    private fun summary(execution: AgendaTripExecution0558): String = buildString {
        append(execution.items.size).append(" instruções\n")
        append("✅ sincronizadas: ").append(execution.items.count { it.state == AgendaTripScriptState0558.SYNCED }).append('\n')
        append("⏭️ já existentes/concluídas: ").append(execution.items.count { it.state in setOf(AgendaTripScriptState0558.SKIPPED_EXISTING, AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED) }).append('\n')
        append("⚠️ reconciliação pendente: ").append(execution.items.count { it.state in setOf(AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS, AgendaTripScriptState0558.RECONCILING, AgendaTripScriptState0558.CONFIRMED, AgendaTripScriptState0558.CANONICALIZED) }).append('\n')
        append("❌ falhas: ").append(execution.items.count { it.state in setOf(AgendaTripScriptState0558.FAILED_BLOCKING, AgendaTripScriptState0558.FAILED_RETRYABLE) }).append('\n')
        append("Estado por viagem:\n")
        execution.items.forEach { append(it.instruction.index).append(" — ").append(it.state.name).append(if (it.lastError.isBlank()) "" else " (${it.lastError})").append('\n') }
    }

    companion object {
        private const val REQUEST_PUBLISH = 5581
    }
}
