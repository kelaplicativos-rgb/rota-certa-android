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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 0.1.559 publisher hardened against the real BlaBlaCar flow observed on SM-S911B.
 *
 * Rules:
 * - fail closed on unknown screens;
 * - never treats "Suas viagens" as publication proof;
 * - never clicks a generic publish button from an unclassified screen;
 * - CPF / birth-date verification is always manual and no field value is read or logged;
 * - RESULT_OK means only explicit UI publication evidence; canonical/external confirmation
 *   remains the responsibility of AgendaTripScriptExecutor reconciliation.
 */
class AgendaBatchPublisherActivity0559 : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var store: AgendaBatchPublisherStore
    private lateinit var status: TextView
    private var webView: WebView? = null
    private var current: AgendaPublishBatch? = null
    private var batchStartedAt = 0L
    private var lastScreen = AgendaPublisherScreen0559.UNKNOWN
    private var screenStableTicks = 0
    private var waitingForUserVerification = false
    private var unknownEvidenceRecorded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        store = AgendaBatchPublisherStore(this)
        status = TextView(this).apply { setPadding(22, 18, 22, 18) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            finishWith(false, "WebView sem suporte a perfis isolados.")
            return
        }
        startNext(root)
    }

    private fun startNext(root: LinearLayout) {
        webView?.let { old -> root.removeView(old); old.destroy() }
        val batch = store.queue().firstOrNull()
        if (batch == null) {
            finishWith(true, "Ação de publicação terminou. A confirmação externa/canônica ainda será reconciliada pelo Executor.")
            return
        }
        val account = registry.get(batch.accountId)
        if (account == null || account.profileUuid?.equals(batch.profileUuid, ignoreCase = true) != true) {
            UnifiedDebugEventStore.record("AGENDA_BATCH_PUBLISH_FAILED_0559", packageName, "batch=${batch.id} reason=account_identity_missing")
            finishWith(false, "Perfil ${batch.profileLabel} não está disponível; lote preservado.")
            return
        }

        current = batch
        batchStartedAt = System.currentTimeMillis()
        lastScreen = AgendaPublisherScreen0559.UNKNOWN
        screenStableTicks = 0
        waitingForUserVerification = false
        unknownEvidenceRecorded = false
        status.text = "${batch.profileLabel} • ${batch.direction} • iniciando publicação segura…"

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
                view.postDelayed({ tick(view, root) }, 350L)
            }
        }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webView = view
        UnifiedDebugEventStore.record(
            "AGENDA_BATCH_PUBLISH_START_0559",
            packageName,
            "batch=${batch.id} profileUuidPresent=true direction=${batch.direction} dates=${batch.dates.size}",
        )
        view.loadUrl(OFFER_URL)
    }

    private fun tick(view: WebView, root: LinearLayout) {
        val batch = current ?: return
        if (isFinishing || view !== webView) return
        if (!waitingForUserVerification && System.currentTimeMillis() - batchStartedAt > BATCH_TIMEOUT_MS) {
            UnifiedDebugEventStore.record(
                "AGENDA_BATCH_PUBLISH_FAILED_0559",
                packageName,
                "batch=${batch.id} reason=timeout screen=${lastScreen.name}",
            )
            finishWith(false, "Tempo excedido em ${batch.profileLabel} ${batch.direction}; lote preservado. Última etapa: ${lastScreen.name}.")
            return
        }
        view.evaluateJavascript(snapshotScript()) { encoded ->
            if (isFinishing || view !== webView) return@evaluateJavascript
            val body = decodeSnapshotBody(encoded)
            val screen = AgendaPublisherScreenClassifier0559.classify(body)
            handleScreen(view, root, batch, screen, body.length)
        }
    }

    private fun handleScreen(
        view: WebView,
        root: LinearLayout,
        batch: AgendaPublishBatch,
        screen: AgendaPublisherScreen0559,
        bodyLength: Int,
    ) {
        if (screen == lastScreen) screenStableTicks++ else {
            lastScreen = screen
            screenStableTicks = 0
            unknownEvidenceRecorded = false
            UnifiedDebugEventStore.record(
                "AGENDA_BATCH_PUBLISH_SCREEN_0559",
                packageName,
                "batch=${batch.id} screen=${screen.name} bodyLength=$bodyLength",
            )
        }

        if (screen == AgendaPublisherScreen0559.USER_VERIFICATION) {
            if (!waitingForUserVerification) {
                waitingForUserVerification = true
                UnifiedDebugEventStore.record(
                    "AGENDA_BATCH_PUBLISH_USER_VERIFICATION_REQUIRED_0559",
                    packageName,
                    "batch=${batch.id} kind=CPF_OR_BIRTHDATE valuesCaptured=false valuesLogged=false",
                )
            }
            status.text = "${batch.profileLabel} • verificação do BlaBlaCar necessária\nPreencha diretamente na página. O Rota Certa não lê, armazena nem registra CPF/data de nascimento. Depois da mudança de tela, a execução continuará automaticamente."
            view.postDelayed({ tick(view, root) }, HUMAN_WAIT_POLL_MS)
            return
        }

        if (waitingForUserVerification) {
            waitingForUserVerification = false
            batchStartedAt = System.currentTimeMillis()
            UnifiedDebugEventStore.record(
                "AGENDA_BATCH_PUBLISH_USER_VERIFICATION_RESUMED_0559",
                packageName,
                "batch=${batch.id} nextScreen=${screen.name}",
            )
        }

        when (screen) {
            AgendaPublisherScreen0559.LOGIN -> {
                UnifiedDebugEventStore.record("AGENDA_BATCH_PUBLISH_BLOCKED_0559", packageName, "batch=${batch.id} reason=login_required")
                finishWith(false, "Sessão do perfil expirou. Faça login nessa conta e retome; o lote foi preservado.")
            }
            AgendaPublisherScreen0559.CONFIRMATION -> {
                store.removeBatch(batch.id)
                UnifiedDebugEventStore.record(
                    "AGENDA_BATCH_PUBLISH_UI_CONFIRMED_0559",
                    packageName,
                    "batch=${batch.id} canonicalConfirmed=false externalReadbackPending=true",
                )
                startNext(root)
            }
            AgendaPublisherScreen0559.UNKNOWN -> {
                status.text = "${batch.profileLabel} • tela ainda não reconhecida\nNenhuma ação automática será executada nesta tela."
                if (screenStableTicks >= UNKNOWN_EVIDENCE_TICKS && !unknownEvidenceRecorded) {
                    unknownEvidenceRecorded = true
                    UnifiedDebugEventStore.record(
                        "AGENDA_BATCH_PUBLISH_UNKNOWN_SCREEN_0559",
                        packageName,
                        "batch=${batch.id} stableTicks=$screenStableTicks bodyLength=$bodyLength action=NONE",
                    )
                }
                view.postDelayed({ tick(view, root) }, UNKNOWN_POLL_MS)
            }
            AgendaPublisherScreen0559.USER_VERIFICATION -> Unit
            else -> runAction(view, root, batch, screen)
        }
    }

    private fun runAction(view: WebView, root: LinearLayout, batch: AgendaPublishBatch, screen: AgendaPublisherScreen0559) {
        status.text = "${batch.profileLabel} • ${batch.direction} • ${screen.name}"
        view.evaluateJavascript(actionScript(screen, batch)) { encoded ->
            if (isFinishing || view !== webView) return@evaluateJavascript
            val action = decodeJavascriptString(encoded)
            UnifiedDebugEventStore.record(
                "AGENDA_BATCH_PUBLISH_ACTION_0559",
                packageName,
                "batch=${batch.id} screen=${screen.name} action=${action.take(80)}",
            )
            when {
                action.startsWith("BLOCKED:") -> finishWith(
                    false,
                    "Publicação bloqueada em ${batch.profileLabel}: ${action.substringAfter(':')}. Lote preservado.",
                )
                action.startsWith("PUBLISH_SUBMITTED:") -> {
                    status.text = "${batch.profileLabel} • publicação enviada; aguardando evidência explícita da página…"
                    view.postDelayed({ tick(view, root) }, AFTER_ACTION_POLL_MS)
                }
                else -> view.postDelayed({ tick(view, root) }, AFTER_ACTION_POLL_MS)
            }
        }
    }

    private fun snapshotScript(): String = """
        (function() {
          const text = (document.body && document.body.innerText) ? document.body.innerText : '';
          return JSON.stringify({ body: text });
        })();
    """.trimIndent()

    private fun actionScript(screen: AgendaPublisherScreen0559, batch: AgendaPublishBatch): String {
        val payload = JSONObject.quote(json.encodeToString(batch))
        val screenName = screen.name
        return """
            (function() {
              const b = JSON.parse($payload);
              const screen = '$screenName';
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const lower = (v) => clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
              const body = lower(document.body && document.body.innerText);
              const visible = (n) => !!n && (!n.getClientRects || n.getClientRects().length > 0) && !n.disabled && n.getAttribute('aria-disabled') !== 'true';
              const controls = () => Array.from(document.querySelectorAll('button, a, [role="button"], [role="option"], [role="radio"], label')).filter(visible);
              const inputs = () => Array.from(document.querySelectorAll('input, textarea')).filter(visible);
              const textOf = (n) => lower((n && (n.innerText || n.textContent || n.getAttribute('aria-label'))) || '');
              const clickExact = (rx) => {
                const n = controls().find((x) => rx.test(textOf(x)));
                if (n && typeof n.click === 'function') { n.click(); return true; }
                return false;
              };
              const next = () => clickExact(/^(continuar|seguinte|proximo|avancar|ok|confirmar|confirmar precos|selecionar ate \d+ datas?|\d+ datas? selecionadas?)$/i) || (() => {
                const n = controls().find((x) => /^(→|›|>)$/.test(clean(x.innerText || x.textContent || '')) || /arrow|next|continuar|avancar/.test(lower(x.getAttribute('aria-label') || '')));
                if (n && typeof n.click === 'function') { n.click(); return true; }
                return false;
              })();
              const setValue = (node, value) => {
                if (!node) return false;
                const proto = node.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                const descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                if (descriptor && descriptor.set) descriptor.set.call(node, value); else node.value = value;
                node.dispatchEvent(new Event('input', { bubbles: true }));
                node.dispatchEvent(new Event('change', { bubbles: true }));
                node.focus();
                return true;
              };
              const selectSuggestion = (value) => {
                const key = lower(value);
                const options = Array.from(document.querySelectorAll('[role="option"], li, [data-testid*="suggest"], [data-testid*="option"]')).filter(visible);
                const target = options.find((n) => lower(n.innerText || n.textContent).includes(key)) || options[0];
                if (target && typeof target.click === 'function') { target.click(); return true; }
                return false;
              };

              if (screen === 'ORIGIN') {
                const fields = inputs();
                const input = fields.find((n) => /sair|saindo|origem|partida|from/.test(lower((n.getAttribute('placeholder') || '') + ' ' + (n.getAttribute('aria-label') || '')))) || fields[0];
                if (!input) return 'WAIT:origin_input';
                if (!lower(input.value).includes(lower(b.originAddress))) { setValue(input, b.originAddress); return 'ORIGIN:TYPED'; }
                if (selectSuggestion(b.originAddress)) return 'ORIGIN:SUGGESTION_SELECTED';
                return 'WAIT:origin_suggestion';
              }

              if (screen === 'DESTINATION') {
                const fields = inputs();
                const input = fields.find((n) => /destino|chegada|para onde|indo|to/.test(lower((n.getAttribute('placeholder') || '') + ' ' + (n.getAttribute('aria-label') || '')))) || fields[0];
                if (!input) return 'WAIT:destination_input';
                if (!lower(input.value).includes(lower(b.destinationAddress))) { setValue(input, b.destinationAddress); return 'DESTINATION:TYPED'; }
                if (selectSuggestion(b.destinationAddress)) return 'DESTINATION:SUGGESTION_SELECTED';
                return 'WAIT:destination_suggestion';
              }

              if (screen === 'ROUTE') {
                const radios = Array.from(document.querySelectorAll('input[type="radio"], [role="radio"]')).filter(visible);
                const selected = radios.find((n) => n.checked || n.getAttribute('aria-checked') === 'true');
                if (!selected && radios[0] && typeof radios[0].click === 'function') { radios[0].click(); return 'ROUTE:DEFAULT_SELECTED'; }
                if (next()) return 'ROUTE:CONTINUED';
                return 'WAIT:route_continue';
              }

              if (screen === 'EXTRA_STOPS') {
                if (next()) return 'EXTRA_STOPS:NONE_DECLARED_CONTINUE';
                return 'WAIT:extra_stops_continue';
              }

              if (screen === 'STOP_CONFIRMATION') {
                if (clickExact(/^(continuar|sim|estao bons|confirmar)$/i) || next()) return 'STOP_CONFIRMATION:ACCEPTED';
                return 'WAIT:stop_confirmation';
              }

              if (screen === 'DATE') {
                const monthNames = ['janeiro','fevereiro','marco','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro'];
                const targetDates = b.dates.map((x) => { const p=x.split('-').map(Number); return {day:p[2], month:p[1], year:p[0]}; });
                let changed = false;
                for (const d of targetDates) {
                  const monthName = monthNames[d.month - 1];
                  const dayButtons = controls().filter((n) => clean(n.innerText || n.textContent) === String(d.day));
                  const candidate = dayButtons.find((n) => {
                    const aria = lower(n.getAttribute('aria-label') || '');
                    if (aria && aria.includes(monthName) && (aria.includes(String(d.year)) || !/20\d{2}/.test(aria))) return true;
                    let p=n.parentElement, depth=0;
                    while (p && depth++ < 6) { const t=lower(p.innerText || ''); if (t.includes(monthName) && (t.includes(String(d.year)) || !/20\d{2}/.test(t))) return true; p=p.parentElement; }
                    return false;
                  });
                  if (candidate) {
                    const selected = candidate.getAttribute('aria-pressed') === 'true' || candidate.getAttribute('aria-selected') === 'true' || /selected|active|checked/i.test(candidate.className || '');
                    if (!selected && typeof candidate.click === 'function') { candidate.click(); changed = true; }
                  }
                }
                if (changed) return 'DATE:SELECTING';
                const countLabel = controls().find((n) => new RegExp('^' + b.dates.length + ' datas? selecionadas?$','i').test(textOf(n)));
                if (countLabel && typeof countLabel.click === 'function') { countLabel.click(); return 'DATE:CONFIRMED'; }
                if (next()) return 'DATE:CONTINUED';
                return 'WAIT:date';
              }

              if (screen === 'TIME') {
                const wanted = b.departureTime;
                if (body.includes(lower(wanted)) && clickExact(/^continuar$/i)) return 'TIME:CONFIRMED_' + wanted;
                const timeInput = inputs().find((n) => (n.getAttribute('type') || '').toLowerCase() === 'time');
                if (timeInput) { setValue(timeInput, wanted); return 'TIME:INPUT_SET_' + wanted; }
                const exact = controls().find((n) => clean(n.innerText || n.textContent) === wanted);
                if (exact && typeof exact.click === 'function') { exact.click(); return 'TIME:OPTION_SELECTED_' + wanted; }
                const trigger = controls().find((n) => /^\d{2}:\d{2}$/.test(clean(n.innerText || n.textContent)));
                if (trigger && typeof trigger.click === 'function') { trigger.click(); return 'TIME:PICKER_OPENED'; }
                return 'WAIT:time';
              }

              if (screen === 'SEATS') {
                const numberNodes = Array.from(document.querySelectorAll('body *')).filter((n) => visible(n) && /^\d+$/.test(clean(n.innerText || n.textContent)) && clean(n.innerText || n.textContent).length <= 2);
                const wanted = Number(b.seats);
                const node = numberNodes.find((n) => Number(clean(n.innerText || n.textContent)) >= 1 && Number(clean(n.innerText || n.textContent)) <= 8);
                const current = node ? Number(clean(node.innerText || node.textContent)) : NaN;
                if (!Number.isFinite(current)) return 'WAIT:seats_value';
                if (current < wanted && clickExact(/adicionar|mais|plus|\+/i)) return 'SEATS:' + current + '->' + wanted;
                if (current > wanted && clickExact(/remover|menos|minus|−|-/i)) return 'SEATS:' + current + '->' + wanted;
                if (current === wanted && next()) return 'SEATS:CONFIRMED_' + wanted;
                return current === wanted ? 'WAIT:seats_continue' : 'BLOCKED:seats_control_unavailable';
              }

              if (screen === 'RESERVATION') {
                const desired = b.automaticReservation ? /ativar reserva automatica/i : /analisar cada pedido|analisar pedidos/i;
                if (clickExact(desired)) return 'RESERVATION:' + (b.automaticReservation ? 'AUTOMATIC' : 'MANUAL');
                return 'WAIT:reservation_choice';
              }

              if (screen === 'PRICE') {
                const confirmPrices = () => clickExact(/^confirmar precos$/i) || clickExact(/^confirmar$/i);
                const requestedDigits = clean(b.priceText || '').replace(/[^0-9]/g, '');
                if (!requestedDigits) {
                  if (confirmPrices()) return 'PRICE:PLATFORM_VALUE_CONFIRMED';
                  return 'WAIT:price_confirm';
                }
                const wanted = Number(requestedDigits);
                const match = (document.body && document.body.innerText || '').match(/R\$\s*([0-9]+)/i);
                const current = match ? Number(match[1]) : NaN;
                if (!Number.isFinite(current)) return 'BLOCKED:explicit_price_readback_unavailable';
                if (current === wanted && confirmPrices()) return 'PRICE:EXPLICIT_CONFIRMED_' + wanted;
                if (current < wanted && clickExact(/adicionar|mais|plus|\+/i)) return 'PRICE:' + current + '->' + wanted;
                if (current > wanted && clickExact(/remover|menos|minus|−|-/i)) return 'PRICE:' + current + '->' + wanted;
                return 'BLOCKED:explicit_price_control_unavailable';
              }

              if (screen === 'RETURN_OFFER') {
                if (clickExact(/^nao,? obrigado$/i) || clickExact(/^agora nao$/i) || clickExact(/^nao$/i)) return 'RETURN_OFFER:DECLINED';
                return 'WAIT:return_offer_decline';
              }

              if (screen === 'COMMENT') {
                const fields = inputs();
                if (b.comment) {
                  const area = fields.find((n) => n.tagName === 'TEXTAREA') || fields[0];
                  if (!area) return 'BLOCKED:comment_field_missing';
                  if (clean(area.value) !== b.comment) { setValue(area, b.comment); return 'COMMENT:TYPED'; }
                }
                if (next()) return b.comment ? 'COMMENT:CONTINUED' : 'COMMENT:OMITTED';
                return 'WAIT:comment_continue';
              }

              if (screen === 'FINAL_PUBLISH') {
                const publish = controls().find((n) => /^(publicar carona|oferecer carona|oferecer \d+ caronas?)$/i.test(textOf(n)));
                if (publish && typeof publish.click === 'function') { publish.click(); return 'PUBLISH_SUBMITTED:EXPLICIT_FINAL_SCREEN'; }
                return 'BLOCKED:final_publish_control_missing';
              }

              return 'WAIT:no_action_for_' + screen;
            })();
        """.trimIndent()
    }

    private fun decodeSnapshotBody(encoded: String): String {
        val inner = decodeJavascriptString(encoded)
        return runCatching { JSONObject(inner).optString("body") }.getOrDefault("")
    }

    private fun decodeJavascriptString(encoded: String): String = runCatching {
        (JSONTokener(encoded).nextValue() as? String).orEmpty()
    }.getOrDefault("")

    private fun finishWith(ok: Boolean, message: String) {
        setResult(if (ok) RESULT_OK else RESULT_CANCELED, Intent().putExtra(EXTRA_MESSAGE, message))
        finish()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MESSAGE = "agenda_batch_publisher_message"
        private const val OFFER_URL = "https://www.blablacar.com.br/offer-seats"
        private const val BATCH_TIMEOUT_MS = 180_000L
        private const val AFTER_ACTION_POLL_MS = 550L
        private const val UNKNOWN_POLL_MS = 900L
        private const val HUMAN_WAIT_POLL_MS = 1_000L
        private const val UNKNOWN_EVIDENCE_TICKS = 8
    }
}
