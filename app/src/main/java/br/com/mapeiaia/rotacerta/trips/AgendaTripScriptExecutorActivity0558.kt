package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private val handler = Handler(Looper.getMainLooper())
    private var launchedIndex: Int? = null
    private var freshReconcileRunning = false

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
            text = "Cole o JSON atual que deseja executar. Uma execução anterior nunca preenche este editor automaticamente. Publicações de resultado ambíguo são preservadas separadamente para reconciliação e não bloqueiam para sempre o editor."
        })
        input = EditText(this).apply {
            hint = "Cole aqui o JSON atual gerado pela Agenda"; minLines = 10; maxLines = 22; setHorizontallyScrolling(false)
        }
        body.addView(input)
        button(body, "Novo JSON", ::newJson)
        button(body, "Carregar execução ativa", ::loadPendingScript)
        button(body, "Validar", ::validateOnly)
        button(body, "Pré-visualizar", ::preview)
        button(body, "Simular execução", ::dryRun)
        button(body, "Executar", ::executeNew)
        button(body, "Reconciliar pendências / retomar ativa", ::resumeAndReconcile)
        button(body, "Cancelar execução ativa", ::cancelExecution)
        button(body, "Histórico", ::showHistory)
        button(body, "Limpar editor") { newJson() }
        output = TextView(this).apply { setPadding(0, 18, 0, 40); setTextIsSelectable(true) }
        body.addView(output)
        setContentView(root)

        recoverInterruptedCreating()
        val migrated = AgendaScriptPendingQueue0561.migrateCancelledActiveIfSafe(this)
        val active = store.active()
        input.setText(AgendaScriptEditorPolicy0561.initialEditorText(active))
        val pendingCount = AgendaScriptPendingQueue0561.pendingCount(this)
        when {
            migrated?.released == true -> {
                output.text = migrated.message + "\nPendências separadas: ${migrated.pendingCount}.\nEditor pronto para receber o JSON atual."
                AgendaTripScriptTrace0558.record(this, "CANCELLED_ACTIVE_RELEASED", detail = "pending=${migrated.pendingCount}")
            }
            active != null -> {
                output.text = AgendaScriptEditorPolicy0561.recoveredExecutionMessage(active) + "\n\n" + summary(active)
                AgendaTripScriptTrace0558.record(
                    this,
                    "EXECUTION_RECOVERED",
                    active.executionId,
                    active.scriptId,
                    detail = "items=${active.items.size} editorPrefilled=false",
                )
            }
            pendingCount > 0 -> output.text = "Editor pronto para um novo JSON. Existem $pendingCount publicação(ões) ambígua(s) preservadas separadamente para reconciliação."
            else -> output.text = "Editor pronto para receber um novo JSON."
        }
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }

    private fun recoverInterruptedCreating() {
        val active = store.active() ?: return
        if (active.items.none { it.state == AgendaTripScriptState0558.CREATING }) return
        val recovered = active.copy(
            updatedAtMillis = System.currentTimeMillis(),
            items = active.items.map { item ->
                if (item.state == AgendaTripScriptState0558.CREATING) {
                    item.copy(
                        state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
                        lastError = "process_recovery_after_external_create_opened",
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                } else item
            },
        )
        store.save(recovered)
        AgendaTripScriptTrace0558.record(
            this,
            "CREATING_RECOVERED_AS_AMBIGUOUS",
            recovered.executionId,
            recovered.scriptId,
            detail = "republish=false",
        )
    }

    private fun newJson() {
        input.setText("")
        input.requestFocus()
        val active = store.active()
        val pending = AgendaScriptPendingQueue0561.pendingCount(this)
        output.text = when {
            active != null -> "Editor limpo para um novo JSON. A execução ativa continua preservada e não foi alterada."
            pending > 0 -> "Editor limpo. Existem $pending publicação(ões) ambígua(s) preservadas separadamente; elas não serão republicadas sem reconciliação."
            else -> "Editor limpo. Cole o JSON atual."
        }
        AgendaTripScriptTrace0558.record(
            this,
            "EDITOR_NEW_JSON",
            executionId = active?.executionId.orEmpty(),
            scriptId = active?.scriptId.orEmpty(),
            detail = "activePreserved=${active != null} pending=$pending editorPrefilled=false",
        )
    }

    private fun loadPendingScript() {
        val active = store.active() ?: run {
            output.text = "Não existe execução ativa para carregar. Publicações ambíguas arquivadas para reconciliação não invadem o editor."
            return
        }
        input.setText(active.rawScript)
        input.setSelection(input.text.length)
        input.requestFocus()
        output.text = "Script ativo carregado manualmente.\n${summary(active)}"
        AgendaTripScriptTrace0558.record(
            this,
            "PENDING_SCRIPT_LOADED_MANUALLY",
            active.executionId,
            active.scriptId,
            detail = "bytes=${active.rawScript.toByteArray().size}",
        )
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
            store.active()?.let { append("\n⚠️ Existe uma execução ativa. Este JSON pode ser validado/simulado, mas outra publicação real não começa em paralelo.\n") }
            val pending = AgendaScriptPendingQueue0561.pendingCount(this@AgendaTripScriptExecutorActivity0558)
            if (pending > 0) append("\n⚠️ $pending publicação(ões) ambígua(s) aguardam reconciliação segura.\n")
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
        executeFromEditor(allowFreshPendingCheck = true)
    }

    private fun executeFromEditor(allowFreshPendingCheck: Boolean) {
        AgendaScriptPendingQueue0561.migrateCancelledActiveIfSafe(this)
        val plan = parseAndPlan() ?: return
        val active = store.active()
        when (AgendaScriptEditorPolicy0561.executionGate(active, plan.script.scriptHash)) {
            AgendaScriptExecutionGate0561.RESUME_SAME -> {
                output.text = "Este JSON corresponde à execução ativa já persistida. Ele não será iniciado outra vez. Use Reconciliar pendências / retomar ativa."
                return
            }
            AgendaScriptExecutionGate0561.BLOCKED_BY_OTHER_ACTIVE -> {
                output.text = "O JSON atual foi validado, mas outra execução está ativa. Cancele-a com segurança ou aguarde seu retorno antes de iniciar uma nova publicação."
                return
            }
            AgendaScriptExecutionGate0561.ALLOW_NEW -> Unit
        }
        if (plan.blockingIssues.isNotEmpty()) {
            output.text = "Execução bloqueada ❌\n" + plan.blockingIssues.joinToString("\n") { "Viagem ${it.instructionIndex ?: "?"}: ${it.message}" }
            return
        }

        val fingerprints = plan.items.map { it.fingerprint }
        val pendingConflict = AgendaScriptPendingQueue0561.containsScriptHash(this, plan.script.scriptHash) ||
            AgendaScriptPendingQueue0561.containsAnyFingerprint(this, fingerprints)
        if (pendingConflict && allowFreshPendingCheck) {
            AgendaTripScriptTrace0558.record(this, "PENDING_CONFLICT_REQUIRES_FRESH_RECONCILE", scriptId = plan.script.scriptId,
                detail = "pending=${AgendaScriptPendingQueue0561.pendingCount(this)} republish=false")
            startFreshPendingReconciliation(afterFresh = { executeFromEditor(allowFreshPendingCheck = false) })
            return
        }
        if (pendingConflict && !allowFreshPendingCheck && plan.readyCount > 0) {
            output.text = "Ainda existe uma publicação ambígua desta mesma viagem. A coleta fresca não comprovou nem a existência nem a ausência externa com cobertura completa. Por segurança, o Rota Certa não vai republicar e criar duplicidade. Use Reconciliar novamente quando a conta estiver acessível."
            return
        }

        val execution = AgendaTripScriptTrace0558.newExecution(plan, input.text.toString())
        store.save(execution)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_STARTED", execution.executionId, execution.scriptId,
            detail = "instructions=${execution.items.size} ready=${execution.items.count { it.state == AgendaTripScriptState0558.READY }} pendingConflict=$pendingConflict")
        startNext(execution)
    }

    private fun resumeAndReconcile() {
        AgendaScriptPendingQueue0561.migrateCancelledActiveIfSafe(this)
        val active = store.active()
        if (active == null) {
            if (AgendaScriptPendingQueue0561.pendingCount(this) > 0) {
                startFreshPendingReconciliation()
            } else {
                output.text = "Nenhuma execução ativa nem publicação ambígua aguardando reconciliação."
            }
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
        if (next.cancelled) {
            val release = AgendaScriptPendingQueue0561.releaseCancelledActive(this)
            output.text = release.message
            return
        }
        startNext(next)
    }

    private fun startFreshPendingReconciliation(afterFresh: (() -> Unit)? = null) {
        if (freshReconcileRunning) {
            output.text = "Reconciliação fresca já está em andamento. Aguarde a conclusão antes de iniciar outra."
            return
        }
        val count = AgendaScriptPendingQueue0561.pendingCount(this)
        if (count == 0) {
            afterFresh?.invoke() ?: run { output.text = "Nenhuma publicação ambígua aguardando reconciliação." }
            return
        }
        val before = AgendaBackgroundSyncConfig0392.collectorState0400(this)
        val baselineGeneration = before.completedGeneration
        freshReconcileRunning = true
        AgendaTripScriptTrace0558.record(this, "FRESH_RECONCILE_REQUESTED", detail = "pending=$count baselineGeneration=$baselineGeneration")
        AgendaBackgroundSync0392.enqueueImmediate(this, "admin_update_now:script_executor_reconcile_0561")
        output.text = "Reconciliando $count publicação(ões) ambígua(s) com uma coleta nova da BlaBlaCar. Nenhuma viagem será republicada durante esta verificação."
        pollFreshReconciliation(
            baselineGeneration = baselineGeneration,
            startedElapsed = SystemClock.elapsedRealtime(),
            afterFresh = afterFresh,
        )
    }

    private fun pollFreshReconciliation(
        baselineGeneration: Long,
        startedElapsed: Long,
        afterFresh: (() -> Unit)?,
    ) {
        handler.postDelayed({
            if (isFinishing || isDestroyed) {
                freshReconcileRunning = false
                return@postDelayed
            }
            val state = AgendaBackgroundSyncConfig0392.collectorState0400(this)
            val completedFreshGeneration = state.completedGeneration > baselineGeneration && !state.pending
            if (completedFreshGeneration) {
                freshReconcileRunning = false
                val result = AgendaScriptFreshPendingResolver0561.reconcile(this, state)
                AgendaTripScriptTrace0558.record(
                    this,
                    "FRESH_RECONCILE_FINISHED",
                    detail = "generation=${state.completedGeneration} completedAccounts=${state.completedAccountIds.size} failedAccounts=${state.failedAccountIds.size} remaining=${result.remainingExecutions} retryable=${result.retryableItems}",
                )
                output.text = result.message
                afterFresh?.invoke()
                return@postDelayed
            }
            if (SystemClock.elapsedRealtime() - startedElapsed >= FRESH_RECONCILE_TIMEOUT_MS) {
                freshReconcileRunning = false
                val fallback = AgendaScriptPendingQueue0561.reconcilePending(this)
                AgendaTripScriptTrace0558.record(this, "FRESH_RECONCILE_TIMEOUT", detail = "baselineGeneration=$baselineGeneration republish=false")
                output.text = "A coleta fresca ainda não terminou. $fallback\nA pendência foi preservada sem republicação."
                return@postDelayed
            }
            pollFreshReconciliation(baselineGeneration, startedElapsed, afterFresh)
        }, FRESH_RECONCILE_POLL_MS)
    }

    private fun startNext(execution: AgendaTripExecution0558) {
        if (execution.cancelled) {
            val release = AgendaScriptPendingQueue0561.releaseCancelledActive(this)
            output.text = release.message
            return
        }
        val barrier = execution.items.firstOrNull { it.state in RECONCILE_STATES }
        if (barrier != null) {
            store.save(execution)
            output.text = "Execução parcialmente concluída ⚠️\n${summary(execution)}\nViagem ${barrier.instruction.index} aguarda reconciliação. Por segurança ela NÃO será republicada. Use Reconciliar pendências / retomar ativa."
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
            "publisher=AgendaBatchPublisherActivity0559 evidencePending=true")
        output.text = "Executando script\nViagem ${item.instruction.index} de ${execution.items.size}\n${item.instruction.origin} → ${item.instruction.destination}\n${item.instruction.date} • ${item.instruction.departureTime}\nEstado: publicando…"
        startActivityForResult(Intent(this, AgendaBatchPublisherActivity0559::class.java), REQUEST_PUBLISH)
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
            "resultOk=${resultCode == RESULT_OK} confirmed=false cancelled=${next.cancelled}")
        val reconciled = AgendaTripScriptPlanner0558.reconcile(this, ambiguous.copy(state = AgendaTripScriptState0558.RECONCILING))
        next = next.copy(items = next.items.mapIndexed { i, old -> if (i == index) reconciled else old })
        store.save(next)
        if (reconciled.state == AgendaTripScriptState0558.SYNCED) {
            store.markCompleted(reconciled.fingerprint)
            AgendaTripScriptTrace0558.record(this, "PUBLISH_SUCCESS", next.executionId, next.scriptId, current.instruction.index,
                "externalEvidence=true canonicalEvidence=true")
            AgendaTripScriptTrace0558.record(this, "CANONICAL_TRIP_PERSISTED", next.executionId, next.scriptId, current.instruction.index,
                "canonicalTripIdPresent=true")
            if (next.cancelled) completeIfTerminal(next) else startNext(next)
        } else if (next.cancelled) {
            val release = AgendaScriptPendingQueue0561.releaseCancelledActive(this)
            AgendaTripScriptTrace0558.record(this, "AMBIGUOUS_CANCELLED_DEFERRED", next.executionId, next.scriptId, current.instruction.index,
                "released=${release.released} pending=${release.pendingCount} republish=false")
            output.text = release.message
        } else {
            val message = data?.getStringExtra(AgendaBatchPublisherActivity0559.EXTRA_MESSAGE).orEmpty()
            output.text = "Publicação enviada; confirmação ainda insuficiente ⚠️\n${summary(next)}\n${message.take(240)}\nO executor NÃO republicará esta viagem. Use Reconciliar pendências / retomar ativa."
        }
    }

    private fun cancelExecution() {
        val active = store.active() ?: run {
            val pending = AgendaScriptPendingQueue0561.pendingCount(this)
            output.text = if (pending > 0) "Não existe execução ativa. Há $pending publicação(ões) ambígua(s) preservadas para reconciliação; elas não bloqueiam novos JSONs diferentes." else "Nenhuma execução em andamento."
            return
        }
        val next = active.copy(
            cancelled = true,
            updatedAtMillis = System.currentTimeMillis(),
            items = active.items.map { item ->
                if (item.state == AgendaTripScriptState0558.READY) item.copy(state = AgendaTripScriptState0558.CANCELLED_NOT_STARTED, updatedAtMillis = System.currentTimeMillis()) else item
            },
        )
        store.save(next)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_CANCEL_REQUESTED", next.executionId, next.scriptId,
            detail = "currentExternalActionPreserved=true remainingCancelled=true")
        val release = AgendaScriptPendingQueue0561.releaseCancelledActive(this)
        AgendaTripScriptTrace0558.record(this, "EXECUTION_CANCEL_RELEASE_RESULT", next.executionId, next.scriptId,
            detail = "released=${release.released} deferred=${release.deferredForReconciliation} pending=${release.pendingCount}")
        output.text = release.message
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
        val pending = AgendaScriptPendingQueue0561.pendingCount(this)
        output.text = buildString {
            append("Publicações ambíguas aguardando reconciliação: $pending\n\n")
            if (history.isEmpty()) append("Nenhuma execução concluída.") else history.forEachIndexed { index, e ->
                if (index > 0) append("\n\n")
                append("SCRIPT ${e.scriptId}\nExecutionId: ${e.executionId}\nHash: ${e.scriptHash.take(24)}…\n${summary(e)}")
            }
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PUBLISH = 5581
        private const val FRESH_RECONCILE_POLL_MS = 900L
        private const val FRESH_RECONCILE_TIMEOUT_MS = 180_000L
        private val RECONCILE_STATES = setOf(
            AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
            AgendaTripScriptState0558.RECONCILING,
            AgendaTripScriptState0558.CONFIRMED,
            AgendaTripScriptState0558.CANONICALIZED,
        )
    }
}
