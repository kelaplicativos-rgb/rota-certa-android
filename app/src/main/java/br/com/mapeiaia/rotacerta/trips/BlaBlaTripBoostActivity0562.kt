package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Physical browser execution host for SET_TRIP_BOOST.
 *
 * It always re-resolves canonical identity, reuses the exact isolated WebView profile,
 * pre-reads state, writes only on an explicit EXECUTE launch, and requires a fresh
 * post-save readback before WRITE_VERIFIED.
 */
class BlaBlaTripBoostActivity0562 : Activity() {
    private lateinit var ledger: BlaBlaPublicationBoostSyncStateStore0562
    private lateinit var accounts: BlaBlaDynamicAccountRegistry
    private lateinit var status: TextView
    private var operation: BlaBlaPublicationBoostOperation0562? = null
    private var webView: WebView? = null
    private var browser: BlaBlaTripBoostBrowserController0562? = null
    private var mode = Mode0562.EXECUTE
    private var phase = Phase0562.PRE_READ
    private var busy = false
    private var navigationCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ledger = BlaBlaPublicationBoostSyncStateStore0562(this)
        accounts = BlaBlaDynamicAccountRegistry(this)
        mode = Mode0562.parse(intent.getStringExtra(EXTRA_MODE)) ?: run {
            finishResult(false, "Modo de Boost inválido.")
            return
        }
        val operationId = intent.getStringExtra(EXTRA_OPERATION_ID).orEmpty()
        val op = ledger.get(operationId) ?: run {
            finishResult(false, "Operação de Boost não encontrada.")
            return
        }
        operation = op
        status = TextView(this).apply { setPadding(24, 20, 24, 20) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        val stale = validateTarget(op)
        if (stale != null) {
            ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.STALE_PLAN, error = stale)
            BlaBlaTripBoostTrace0562.record(this, "BOOST_FAILED_BLOCKING", op, "reason=stale_plan")
            finishResult(false, "STALE_PLAN: $stale")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            failBlocking("WebView sem suporte a sessão isolada multi-profile.")
            return
        }
        val account = accounts.get(op.accountId)
        if (account == null || account.profileUuid?.equals(op.profileUuid, ignoreCase = true) != true) {
            failBlocking("A sessão persistida não corresponde ao profileUuid da viagem.")
            return
        }
        // Refuse zero or duplicate strong-identity sessions even if accountId still exists.
        val matching = accounts.list().filter { it.profileUuid?.equals(op.profileUuid, ignoreCase = true) == true }
        if (matching.size != 1 || matching.single().id != op.accountId) {
            failBlocking("A identidade da sessão está ausente ou ambígua.")
            return
        }

        status.text = "${op.profileLabel} • Boost ${op.desiredState.name}\nConfirmando sessão e publicação exatas…"
        val view = WebView(this)
        WebViewCompat.setProfile(view, account.webProfileName)
        WebViewCompat.getProfile(view).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(view, true)
        }
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                navigationCount += 1
                if (navigationCount > MAX_NAVIGATIONS) {
                    if (phase == Phase0562.READBACK) ambiguous("navigation_limit_during_readback") else failBlocking("Limite de navegação atingido antes de uma tela Boost segura.")
                    return
                }
                view.postDelayed({ drive() }, PAGE_SETTLE_MS)
            }
        }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webView = view
        browser = BlaBlaTripBoostBrowserController0562(this, view, op.accountId, op.profileUuid, op.tripId, op.correlationId)
        BlaBlaTripBoostTrace0562.record(this, "BOOST_TARGET_RESOLVED", op, "mode=${mode.name} strongIdentity=true")
        view.loadUrl(op.manageUrl)
    }

    private fun validateTarget(op: BlaBlaPublicationBoostOperation0562): String? {
        val trip = TripStore(this).trips().firstOrNull { it.id == op.localTripId } ?: return "viagem_canônica_ausente"
        if (trip.deleted || trip.status == TripStatus.CANCELLED) return "viagem_cancelada_ou_removida"
        if (!trip.blablaTripId.orEmpty().equals(op.tripId, ignoreCase = true)) return "tripId_alterado"
        if (!trip.blablaProfileUuid.orEmpty().equals(op.profileUuid, ignoreCase = true)) return "profileUuid_alterado"
        if (trip.canonicalRevision != op.observedCanonicalRevision) return "canonical_revision_alterada"
        val manage = trip.blablaManageUrl.orEmpty()
        if (manage != op.manageUrl || !BlaBlaTripBoostTargetResolver0562.isStrongManageUrl(manage, op.tripId)) return "url_administrativa_alterada"
        return null
    }

    private fun drive() {
        val op = operation ?: return
        val controller = browser ?: return
        if (busy || isFinishing) return
        busy = true
        controller.verifySession { sessionVerified ->
            if (!sessionVerified) {
                busy = false
                failBlocking("Sessão não autenticada para o profileUuid esperado.")
                return@verifySession
            }
            controller.read { state ->
                busy = false
                if (state == null) {
                    if (phase == Phase0562.READBACK || mode == Mode0562.RECONCILE) ambiguous("boost_state_read_failed") else failBlocking("Não foi possível ler o estado atual do Boost.")
                    return@read
                }
                handleState(op, state)
            }
        }
    }

    private fun handleState(op: BlaBlaPublicationBoostOperation0562, state: BlaBlaTripBoostBrowserState0562) {
        if (state.authRequired) { failBlocking("Sessão expirada ou autenticação adicional requerida."); return }
        if (state.error) {
            if (phase == Phase0562.READBACK || mode == Mode0562.RECONCILE) ambiguous("external_error_during_readback") else failBlocking("A BlaBlaCar retornou uma tela de erro.")
            return
        }
        if (!state.tripIdBound) { failBlocking("A tela atual não está vinculada ao tripId esperado."); return }
        when (state.screen) {
            "RIDE_DETAIL" -> navigateEdit()
            "RIDE_EDIT" -> navigateBoostSection()
            "BOOST_EDIT" -> handleBoostEdit(op, state)
            "AUTH_REQUIRED" -> failBlocking("Autenticação adicional requerida.")
            "ERROR" -> if (phase == Phase0562.READBACK) ambiguous("external_error") else failBlocking("Tela externa de erro.")
            else -> failBlocking("Tela externa desconhecida; nenhuma ação será executada.")
        }
    }

    private fun navigateEdit() {
        val controller = browser ?: return
        if (busy) return
        busy = true
        controller.openEdit { result ->
            busy = false
            if (result?.clicked != true) failBlocking("Não foi possível abrir semanticamente a edição da publicação (${result?.reason ?: "sem_evidência"}).")
        }
    }

    private fun navigateBoostSection() {
        val controller = browser ?: return
        if (busy) return
        busy = true
        controller.openBoostSection { result ->
            busy = false
            if (result?.clicked != true) failBlocking("Seção Boost não encontrada de forma inequívoca (${result?.reason ?: "sem_evidência"}).")
        }
    }

    private fun handleBoostEdit(op: BlaBlaPublicationBoostOperation0562, state: BlaBlaTripBoostBrowserState0562) {
        val observed = state.observed()
        val observedLedgerState = when (observed) {
            BlaBlaTripBoostObservedState0562.ENABLED -> BlaBlaTripBoostSyncState0562.READ_CONFIRMED_ENABLED
            BlaBlaTripBoostObservedState0562.DISABLED -> BlaBlaTripBoostSyncState0562.READ_CONFIRMED_DISABLED
            BlaBlaTripBoostObservedState0562.UNKNOWN -> BlaBlaTripBoostSyncState0562.UNKNOWN
        }
        ledger.update(op.operationId, observedLedgerState, observed, source = "BOOST_STATE_external_read")
        BlaBlaTripBoostTrace0562.record(this, "BOOST_PRE_READ", op, "observed=${observed.name} phase=${phase.name} mode=${mode.name}")

        if (!state.controlFound || state.controlAmbiguous || observed == BlaBlaTripBoostObservedState0562.UNKNOWN) {
            if (phase == Phase0562.READBACK || mode == Mode0562.RECONCILE) ambiguous("boost_control_or_state_unknown")
            else failBlocking("Controle Boost ou estado atual não pôde ser identificado de forma inequívoca.")
            return
        }

        if (phase == Phase0562.READBACK) {
            val finalState = BlaBlaTripBoostPolicy0562.afterWriteReadback(observed, op.desiredState)
            if (finalState == BlaBlaTripBoostSyncState0562.WRITE_VERIFIED) {
                ledger.update(op.operationId, finalState, observed, source = "external_post_save_readback")
                BlaBlaTripBoostTrace0562.record(this, "BOOST_READBACK_CONFIRMED", op, "observed=${observed.name}")
                finishResult(true, "WRITE_VERIFIED — Boost confirmado externamente como ${observed.name}.")
            } else ambiguous("post_save_state_opposite")
            return
        }

        if (mode == Mode0562.RECONCILE) {
            val finalState = BlaBlaTripBoostPolicy0562.afterWriteReadback(observed, op.desiredState)
            if (finalState == BlaBlaTripBoostSyncState0562.WRITE_VERIFIED) {
                ledger.update(op.operationId, finalState, observed, source = "reconciliation_external_readback")
                BlaBlaTripBoostTrace0562.record(this, "BOOST_READBACK_CONFIRMED", op, "source=reconcile observed=${observed.name}")
                finishResult(true, "Reconciliação confirmou o estado desejado; nenhuma escrita foi realizada.")
            } else {
                ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE, observed, source = "reconciliation_opposite_state", error = "explicit_execute_required_for_retry")
                BlaBlaTripBoostTrace0562.record(this, "BOOST_FAILED_RETRYABLE", op, "oppositeStateProven=true autoRetry=false")
                finishResult(false, "Reconciliação comprovou estado oposto. Nenhuma escrita automática foi feita; EXECUTAR explicitamente para tentar novamente.")
            }
            return
        }

        if (mode == Mode0562.SIMULATE) {
            ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.SIMULATED, observed, source = "simulation_external_read")
            val desiredObserved = if (op.desiredState == BlaBlaTripBoostDesiredState0562.ENABLED) BlaBlaTripBoostObservedState0562.ENABLED else BlaBlaTripBoostObservedState0562.DISABLED
            finishResult(true, "SIMULAÇÃO — alvo e sessão confirmados; estado atual ${observed.name}; ${if (observed == desiredObserved) "nenhuma alteração seria necessária" else "seria executado ${observed.name} → ${op.desiredState.name}"}. Nenhuma escrita realizada.")
            return
        }

        val decision = BlaBlaTripBoostPolicy0562.beforeWrite(observed, op.desiredState, identityVerified = true, screenVerified = state.screen == "BOOST_EDIT")
        if (!decision.shouldWrite) {
            val terminal = decision.terminalState ?: BlaBlaTripBoostSyncState0562.FAILED_BLOCKING
            ledger.update(op.operationId, terminal, observed, source = "external_pre_read")
            if (terminal == BlaBlaTripBoostSyncState0562.SKIPPED_ALREADY_IN_DESIRED_STATE) {
                BlaBlaTripBoostTrace0562.record(this, "BOOST_ALREADY_DESIRED", op, "observed=${observed.name} writes=0")
                finishResult(true, decision.message)
            } else failBlocking(decision.message)
            return
        }
        if (!state.savePresent) { failBlocking("Salvar não está disponível na tela Boost confirmada."); return }
        writeDesired(op)
    }

    private fun writeDesired(op: BlaBlaPublicationBoostOperation0562) {
        val controller = browser ?: return
        if (busy) return
        busy = true
        ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.WRITE_PENDING, incrementAttempt = true, source = "write_started")
        BlaBlaTripBoostTrace0562.record(this, "BOOST_WRITE_STARTED", op, "desired=${op.desiredState.name}")
        controller.setDesired(op.desiredState) { action ->
            if (action?.clicked != true) {
                busy = false
                ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE, source = "boost_select", error = action?.reason.orEmpty())
                finishResult(false, "A seleção do Boost não foi executada com segurança (${action?.reason ?: "sem_evidência"}). Salvar NÃO foi acionado.")
                return@setDesired
            }
            controller.read { selected ->
                if (selected == null || selected.screen != "BOOST_EDIT" || !selected.tripIdBound || selected.observed() == BlaBlaTripBoostObservedState0562.UNKNOWN) {
                    busy = false
                    ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE, source = "pre_save_read", error = "selection_readback_failed")
                    finishResult(false, "A seleção foi tocada, mas não pôde ser confirmada antes de Salvar. Salvar NÃO foi acionado.")
                    return@read
                }
                val expected = if (op.desiredState == BlaBlaTripBoostDesiredState0562.ENABLED) BlaBlaTripBoostObservedState0562.ENABLED else BlaBlaTripBoostObservedState0562.DISABLED
                if (selected.observed() != expected || !selected.savePresent) {
                    busy = false
                    ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE, selected.observed(), source = "pre_save_read", error = "desired_not_selected_or_save_missing")
                    finishResult(false, "Estado desejado não foi confirmado antes de Salvar. Nenhum Save foi enviado.")
                    return@read
                }
                controller.save(op.desiredState) { saved ->
                    busy = false
                    if (saved?.clicked != true) {
                        ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE, expected, source = "save_not_sent", error = saved?.reason.orEmpty())
                        finishResult(false, "Salvar não foi enviado (${saved?.reason ?: "sem_evidência"}).")
                        return@save
                    }
                    ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.WRITE_PENDING, expected, source = "save_sent")
                    BlaBlaTripBoostTrace0562.record(this, "BOOST_SAVE_SENT", op, "successAssumed=false readbackRequired=true")
                    phase = Phase0562.READBACK
                    status.text = "Salvar enviado. Reabrindo a publicação exata para readback obrigatório…"
                    webView?.postDelayed({
                        if (!isFinishing) webView?.loadUrl(op.manageUrl)
                    }, SAVE_SETTLE_MS)
                }
            }
        }
    }

    private fun ambiguous(reason: String) {
        val op = operation ?: return
        ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS, source = "external_readback", error = reason)
        BlaBlaTripBoostTrace0562.record(this, "BOOST_WRITE_AMBIGUOUS", op, "reason=$reason autoRetry=false")
        finishResult(false, "WRITE_AMBIGUOUS — Save pode ter ocorrido, mas o estado externo não foi confirmado. Use Reconciliar pendências; não haverá toggle automático.")
    }

    private fun failBlocking(message: String) {
        val op = operation
        if (op != null) {
            ledger.update(op.operationId, BlaBlaTripBoostSyncState0562.FAILED_BLOCKING, source = "fail_closed", error = message)
            BlaBlaTripBoostTrace0562.record(this, "BOOST_FAILED_BLOCKING", op, "reason=${message.take(100)}")
        }
        finishResult(false, message)
    }

    private fun finishResult(ok: Boolean, message: String) {
        if (isFinishing) return
        setResult(if (ok) RESULT_OK else RESULT_CANCELED, Intent().putExtra(EXTRA_MESSAGE, message).putExtra(EXTRA_OPERATION_ID, operation?.operationId.orEmpty()))
        finish()
    }

    override fun onDestroy() {
        browser?.cancel()
        webView?.destroy()
        super.onDestroy()
    }

    private enum class Mode0562 {
        SIMULATE, EXECUTE, RECONCILE;
        companion object { fun parse(raw: String?): Mode0562? = values().firstOrNull { it.name == raw?.trim()?.uppercase() } }
    }
    private enum class Phase0562 { PRE_READ, READBACK }

    companion object {
        const val EXTRA_OPERATION_ID = "boost_operation_id_0562"
        const val EXTRA_MODE = "boost_mode_0562"
        const val EXTRA_MESSAGE = "boost_message_0562"
        const val MODE_SIMULATE = "SIMULATE"
        const val MODE_EXECUTE = "EXECUTE"
        const val MODE_RECONCILE = "RECONCILE"
        private const val PAGE_SETTLE_MS = 650L
        private const val SAVE_SETTLE_MS = 1_400L
        private const val MAX_NAVIGATIONS = 16
    }
}
