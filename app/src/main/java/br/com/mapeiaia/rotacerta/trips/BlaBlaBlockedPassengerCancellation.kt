package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
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
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class BlockedPassengerCancellationRequest(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val profileUuid: String,
    val tripId: String,
    val externalPassengerId: String,
    val bookingHref: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

internal class BlockedPassengerCancellationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<BlockedPassengerCancellationRequest> = runCatching {
        json.decodeFromString<List<BlockedPassengerCancellationRequest>>(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun enqueue(request: BlockedPassengerCancellationRequest): Boolean {
        val current = list()
        val duplicate = current.any {
            it.profileUuid.equals(request.profileUuid, ignoreCase = true) &&
                it.tripId == request.tripId &&
                it.externalPassengerId == request.externalPassengerId
        }
        if (duplicate) return false
        save(current + request)
        return true
    }

    fun remove(id: String) = save(list().filterNot { it.id == id })

    private fun save(value: List<BlockedPassengerCancellationRequest>) {
        prefs.edit().putString(KEY_QUEUE, json.encodeToString(value)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blocked_passenger_cancel_v1"
        private const val KEY_QUEUE = "queue"
    }
}

internal object BlockedPassengerCancellationCoordinator {
    fun enqueueBlockedFromNetwork(
        context: Context,
        account: BlaBlaDynamicAccount,
        resolution: BlaBlaNetworkTripResolution,
    ): Int {
        val profileUuid = account.profileUuid?.trim()?.takeIf(String::isNotEmpty) ?: return 0
        val identityStore = PassengerIdentityStore(context)
        val queueStore = BlockedPassengerCancellationStore(context)
        var queued = 0
        resolution.bookings.forEach { booking ->
            val externalId = stableExternalPassengerId(booking.passengerId) ?: return@forEach
            val profile = identityStore.profileByExternalPassengerId(externalId) ?: return@forEach
            if (!profile.blocked) return@forEach
            val href = booking.passenger.booking_href?.takeIf(BlaBlaCollectorUrlModule::isPassenger) ?: return@forEach
            val request = BlockedPassengerCancellationRequest(
                accountId = account.id,
                profileUuid = profileUuid,
                tripId = resolution.tripId,
                externalPassengerId = externalId,
                bookingHref = href,
            )
            if (queueStore.enqueue(request)) queued++
            UnifiedDebugEventStore.record(
                "BLOCKED_PASSENGER_DETECTED",
                context.packageName,
                "profileUuidPresent=true tripIdPresent=true externalPassengerIdPresent=true queued=${queued > 0}",
            )
        }
        return queued
    }
}

internal object BlaBlaBlockedPassengerCancellationIntents {
    fun process(context: Context): Intent = Intent(context, BlaBlaBlockedPassengerCancellationActivity::class.java)
}

class BlaBlaBlockedPassengerCancellationActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var queue: BlockedPassengerCancellationStore
    private lateinit var status: TextView
    private var webView: WebView? = null
    private var request: BlockedPassengerCancellationRequest? = null
    private var phase = Phase.CANCEL
    private var startedAtMillis = 0L
    private var verificationMisses = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        queue = BlockedPassengerCancellationStore(this)
        status = TextView(this).apply { setPadding(22, 18, 22, 18) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            finishPending("WebView sem suporte aos perfis isolados.")
            return
        }
        processNext(root)
    }

    private fun processNext(root: LinearLayout) {
        webView?.let { old -> root.removeView(old); old.destroy() }
        val next = queue.list().firstOrNull()
        if (next == null) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_MESSAGE, "Passageiros bloqueados processados."))
            finish()
            return
        }
        val account = registry.get(next.accountId)
        if (account == null || account.profileUuid?.equals(next.profileUuid, ignoreCase = true) != true) {
            finishPending("Conta da reserva bloqueada não está disponível; retirada permanece pendente.")
            return
        }
        request = next
        phase = Phase.CANCEL
        startedAtMillis = System.currentTimeMillis()
        verificationMisses = 0
        status.text = "Passageiro bloqueado • abrindo reserva exata…"

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
                view.postDelayed({ tick(view, root) }, 650)
            }
        }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webView = view
        UnifiedDebugEventStore.record(
            "BLOCKED_PASSENGER_CANCEL_START",
            packageName,
            "request=${next.id} profileUuidPresent=true tripIdPresent=true externalPassengerIdPresent=true bookingHrefPresent=true",
        )
        view.loadUrl(next.bookingHref)
    }

    private fun tick(view: WebView, root: LinearLayout) {
        val current = request ?: return
        if (isFinishing || view !== webView) return
        if (System.currentTimeMillis() - startedAtMillis > TIMEOUT_MS) {
            finishPending("Retirada do passageiro bloqueado ficou pendente por tempo excedido.")
            return
        }
        when (phase) {
            Phase.CANCEL -> view.evaluateJavascript(cancelFlowScript()) { encoded ->
                val action = decodeJsString(encoded)
                UnifiedDebugEventStore.record(
                    "BLOCKED_PASSENGER_CANCEL_STEP",
                    packageName,
                    "request=${current.id} action=${action.take(80)}",
                )
                status.text = "Passageiro bloqueado • $action"
                when {
                    action.startsWith("BLOCKED:") -> finishPending("Retirada pendente: ${action.substringAfter(':')}.")
                    action == "FINAL_CANCEL_CLICKED" || action == "CANCELLED_HINT" -> {
                        phase = Phase.VERIFY
                        verificationMisses = 0
                        view.postDelayed({ view.loadUrl(tripVerificationUrl(current.tripId)) }, 1_400L)
                    }
                    else -> view.postDelayed({ tick(view, root) }, 750L)
                }
            }
            Phase.VERIFY -> view.evaluateJavascript(verificationScript(current.externalPassengerId)) { encoded ->
                val action = decodeJsString(encoded)
                if (action == "ABSENT") verificationMisses++ else verificationMisses = 0
                UnifiedDebugEventStore.record(
                    "BLOCKED_PASSENGER_CANCEL_VERIFY",
                    packageName,
                    "request=${current.id} passengerStillPresent=${action == "PRESENT"} stableAbsentPasses=$verificationMisses",
                )
                if (verificationMisses >= 2) {
                    queue.remove(current.id)
                    UnifiedDebugEventStore.record(
                        "BLOCKED_PASSENGER_CANCEL_VERIFIED",
                        packageName,
                        "request=${current.id} profileUuidPresent=true tripIdPresent=true externalPassengerIdPresent=true",
                    )
                    processNext(root)
                } else if (action == "LOGIN") {
                    finishPending("Sessão BlaBlaCar precisa de login; retirada permanece pendente.")
                } else {
                    view.postDelayed({
                        if (phase == Phase.VERIFY) view.reload()
                    }, 1_200L)
                }
            }
        }
    }

    private fun cancelFlowScript(): String = """
        (function() {
          const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
          const norm=(v)=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
          const body=norm(document.body&&document.body.innerText);
          if (/continuar com e-mail|como voce deseja se conectar|entrar na sua conta/.test(body)) return 'BLOCKED:login necessario';
          if (/reserva cancelada|cancelamento confirmado|foi cancelada/.test(body)) return 'CANCELLED_HINT';
          const visible=(n)=>!!n&&(!n.getClientRects||n.getClientRects().length>0);
          const nodes=Array.from(document.querySelectorAll('button,a,[role="button"],[role="option"],label')).filter(visible);
          const click=(rx)=>{const n=nodes.find(x=>rx.test(norm((x.innerText||x.textContent||'')+' '+(x.getAttribute('aria-label')||''))));if(n&&typeof n.click==='function'){n.click();return true;}return false;};
          if (/qual e o motivo/.test(body)) {
            if(click(/outro motivo que nao esta na lista/)) return 'REASON_OTHER_CLICKED';
            return 'WAIT:motivo';
          }
          if (/poderia contar um pouco mais|eu cancelei porque/.test(body)) {
            const field=Array.from(document.querySelectorAll('textarea,input')).find(visible);
            const reason='Não desejo realizar esta viagem com este passageiro. Solicito o cancelamento desta reserva.';
            if(field && clean(field.value)!==reason){
              const proto=field.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
              const setter=Object.getOwnPropertyDescriptor(proto,'value')&&Object.getOwnPropertyDescriptor(proto,'value').set;
              if(setter)setter.call(field,reason);else field.value=reason;
              field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));
              return 'REASON_TEXT_FILLED';
            }
            const cancelButtons=nodes.filter(x=>/^cancelar reserva$/i.test(norm(x.innerText||x.textContent)));
            if(cancelButtons.length){cancelButtons[cancelButtons.length-1].click();return 'FINAL_CANCEL_CLICKED';}
            return 'WAIT:cancelamento final';
          }
          if(click(/^cancelar reserva$/i)) return 'CANCEL_RESERVATION_CLICKED';
          return 'WAIT:tela de reserva';
        })();
    """.trimIndent()

    private fun verificationScript(externalPassengerId: String): String {
        val id = JSONObjectQuote.quote(externalPassengerId)
        return """
            (function() {
              const norm=(v)=>(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
              const body=norm(document.body&&document.body.innerText);
              if (/continuar com e-mail|como voce deseja se conectar|entrar na sua conta/.test(body)) return 'LOGIN';
              const wanted=$id;
              const links=Array.from(document.querySelectorAll('a[href]')).map(a=>a.href||'');
              const present=links.some(h=>h.includes('/passenger/'+wanted+'/')||h.includes('/booking/'+wanted)) || (document.documentElement.outerHTML||'').includes(wanted);
              return present ? 'PRESENT' : 'ABSENT';
            })();
        """.trimIndent()
    }

    private fun tripVerificationUrl(tripId: String): String =
        "${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer?id=$tripId"

    private fun decodeJsString(encoded: String?): String = encoded
        ?.trim()
        ?.removeSurrounding("\"")
        ?.replace("\\\"", "\"")
        ?.replace("\\n", " ")
        .orEmpty()

    private fun finishPending(message: String) {
        UnifiedDebugEventStore.record(
            "BLOCKED_PASSENGER_CANCEL_PENDING",
            packageName,
            "queue=${queue.list().size} reason=${message.take(120)}",
        )
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_MESSAGE, message))
        finish()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private enum class Phase { CANCEL, VERIFY }

    companion object {
        const val EXTRA_MESSAGE = "blocked_passenger_cancel_message"
        private const val TIMEOUT_MS = 120_000L
    }
}

/** Minimal local JSON-string quoting helper to avoid exposing request data to logs. */
private object JSONObjectQuote {
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
