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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

internal enum class AgendaPublishDirection { IDA, VOLTA }

@Serializable
internal data class AgendaPublishTemplate(
    val originAddress: String = "",
    val destinationAddress: String = "",
    val departureTime: String = "10:30",
    val seats: Int = 4,
    val priceText: String = "",
    val automaticReservation: Boolean = false,
)

@Serializable
internal data class AgendaPublishProfileDraft(
    val accountId: String,
    val selected: Boolean = true,
    val outboundDays: String = "",
    val inboundDays: String = "",
    val comment: String = "",
)

@Serializable
internal data class AgendaPublisherDraft(
    val monthYear: String = "",
    val outbound: AgendaPublishTemplate = AgendaPublishTemplate(),
    val inbound: AgendaPublishTemplate = AgendaPublishTemplate(),
    val profiles: List<AgendaPublishProfileDraft> = emptyList(),
)

@Serializable
internal data class AgendaPublishBatch(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val profileUuid: String,
    val profileLabel: String,
    val direction: AgendaPublishDirection,
    val dates: List<String>,
    val originAddress: String,
    val destinationAddress: String,
    val departureTime: String,
    val seats: Int,
    val priceText: String = "",
    val automaticReservation: Boolean = false,
    val comment: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

internal data class AgendaPublishPlan(
    val batches: List<AgendaPublishBatch>,
    val errors: List<String>,
    val warnings: List<String>,
    val alreadyPublished: Int,
)

internal object AgendaBatchPublisherPlanner {
    fun parseMonth(raw: String): YearMonth? {
        val value = raw.trim()
        return runCatching {
            when {
                Regex("\\d{4}-\\d{2}").matches(value) -> YearMonth.parse(value)
                Regex("\\d{2}/\\d{4}").matches(value) -> {
                    val (month, year) = value.split('/')
                    YearMonth.of(year.toInt(), month.toInt())
                }
                else -> null
            }
        }.getOrNull()
    }

    fun parseDays(raw: String, month: YearMonth): List<LocalDate>? {
        if (raw.isBlank()) return emptyList()
        val days = raw.split(',', ';', ' ', '\n', '\t')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.toIntOrNull() ?: return null }
            .distinct()
        return days.map { day ->
            runCatching { month.atDay(day) }.getOrNull() ?: return null
        }.sorted()
    }

    fun plan(
        draft: AgendaPublisherDraft,
        accounts: List<BlaBlaDynamicAccount>,
        existing: List<BlaBlaCollectorTrip>,
    ): AgendaPublishPlan {
        val month = parseMonth(draft.monthYear)
            ?: return AgendaPublishPlan(emptyList(), listOf("Informe mês/ano como MM/AAAA ou AAAA-MM."), emptyList(), 0)
        val accountById = accounts.associateBy(BlaBlaDynamicAccount::id)
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val batches = mutableListOf<AgendaPublishBatch>()
        var already = 0

        fun templateErrors(label: String, template: AgendaPublishTemplate) {
            if (template.originAddress.isBlank()) errors += "$label: informe o endereço de origem."
            if (template.destinationAddress.isBlank()) errors += "$label: informe o endereço de destino."
            if (!Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(template.departureTime.trim())) {
                errors += "$label: horário inválido; use HH:mm."
            }
            if (template.seats !in 1..4) errors += "$label: vagas deve ficar entre 1 e 4."
        }
        templateErrors("IDA", draft.outbound)
        templateErrors("VOLTA", draft.inbound)

        draft.profiles.filter(AgendaPublishProfileDraft::selected).forEach { profileDraft ->
            val account = accountById[profileDraft.accountId]
            if (account == null) {
                errors += "Conta selecionada não encontrada."
                return@forEach
            }
            val uuid = account.profileUuid?.trim()?.takeIf(String::isNotEmpty)
            if (uuid == null) {
                errors += "${account.displayLabel}: faça login e confirme o UUID antes de publicar."
                return@forEach
            }
            val outboundDates = parseDays(profileDraft.outboundDays, month)
            val inboundDates = parseDays(profileDraft.inboundDays, month)
            if (outboundDates == null) errors += "${account.displayLabel}: dias de IDA inválidos."
            if (inboundDates == null) errors += "${account.displayLabel}: dias de VOLTA inválidos."
            if (outboundDates == null || inboundDates == null) return@forEach

            val chronology = (outboundDates.map { it to AgendaPublishDirection.IDA } + inboundDates.map { it to AgendaPublishDirection.VOLTA })
                .sortedBy { it.first }
            chronology.zipWithNext().forEach { (left, right) ->
                if (left.second == right.second) {
                    warnings += "${account.displayLabel}: ${left.first.dayOfMonth} e ${right.first.dayOfMonth} estão no mesmo sentido consecutivamente; confira a continuidade física."
                }
            }

            fun addDirection(direction: AgendaPublishDirection, dates: List<LocalDate>, template: AgendaPublishTemplate) {
                val remaining = dates.filterNot { date ->
                    val duplicate = existing.any { trip ->
                        trip.profile_uuid.equals(uuid, ignoreCase = true) &&
                            trip.date == date.toString() &&
                            trip.departure_time?.take(5) == template.departureTime.trim() &&
                            samePlaceForPublisher(trip.actual_departure ?: trip.search_from.orEmpty(), template.originAddress) &&
                            samePlaceForPublisher(trip.actual_arrival ?: trip.search_to.orEmpty(), template.destinationAddress)
                    }
                    if (duplicate) already++
                    duplicate
                }
                remaining.chunked(20).forEach { chunk ->
                    batches += AgendaPublishBatch(
                        accountId = account.id,
                        profileUuid = uuid,
                        profileLabel = account.displayLabel,
                        direction = direction,
                        dates = chunk.map(LocalDate::toString),
                        originAddress = template.originAddress.trim(),
                        destinationAddress = template.destinationAddress.trim(),
                        departureTime = template.departureTime.trim(),
                        seats = template.seats,
                        priceText = template.priceText.trim(),
                        automaticReservation = template.automaticReservation,
                        comment = profileDraft.comment.trim(),
                    )
                }
            }
            addDirection(AgendaPublishDirection.IDA, outboundDates, draft.outbound)
            addDirection(AgendaPublishDirection.VOLTA, inboundDates, draft.inbound)
        }

        if (draft.profiles.none(AgendaPublishProfileDraft::selected)) errors += "Selecione ao menos um perfil."
        return AgendaPublishPlan(batches, errors.distinct(), warnings.distinct(), already)
    }

    private fun samePlaceForPublisher(left: String, right: String): Boolean {
        fun key(raw: String): String = java.text.Normalizer.normalize(raw.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val a = key(left)
        val b = key(right)
        return a.isNotBlank() && b.isNotBlank() && (a == b || a.contains(b) || b.contains(a))
    }
}

internal class AgendaBatchPublisherStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun draft(accounts: List<BlaBlaDynamicAccount>): AgendaPublisherDraft {
        val saved = runCatching { json.decodeFromString<AgendaPublisherDraft>(prefs.getString(KEY_DRAFT, "") ?: "") }.getOrNull()
        val byId = saved?.profiles.orEmpty().associateBy(AgendaPublishProfileDraft::accountId)
        return (saved ?: AgendaPublisherDraft()).copy(
            profiles = accounts.map { account -> byId[account.id] ?: AgendaPublishProfileDraft(accountId = account.id) },
        )
    }

    fun saveDraft(value: AgendaPublisherDraft) {
        prefs.edit().putString(KEY_DRAFT, json.encodeToString(value)).apply()
    }

    fun queue(): List<AgendaPublishBatch> = runCatching {
        json.decodeFromString<List<AgendaPublishBatch>>(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun replaceQueue(value: List<AgendaPublishBatch>) {
        prefs.edit().putString(KEY_QUEUE, json.encodeToString(value)).apply()
    }

    fun removeBatch(id: String) = replaceQueue(queue().filterNot { it.id == id })

    companion object {
        private const val PREFS = "rota_certa_agenda_batch_publisher_v1"
        private const val KEY_DRAFT = "draft"
        private const val KEY_QUEUE = "queue"
    }
}

@Composable
internal fun AgendaBatchPublisherPanel(onChanged: (String) -> Unit) {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val store = remember(context) { AgendaBatchPublisherStore(context) }
    val accounts = registry.list()
    var draft by remember(accounts.map(BlaBlaDynamicAccount::id)) { mutableStateOf(store.draft(accounts)) }
    var status by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        status = result.data?.getStringExtra(AgendaBatchPublisherActivity.EXTRA_MESSAGE)
            ?: if (result.resultCode == Activity.RESULT_OK) "Publicação em lote concluída." else "Publicação interrompida; lotes pendentes foram preservados."
        onChanged(status)
    }

    fun updateTemplate(direction: AgendaPublishDirection, block: (AgendaPublishTemplate) -> AgendaPublishTemplate) {
        draft = if (direction == AgendaPublishDirection.IDA) draft.copy(outbound = block(draft.outbound))
        else draft.copy(inbound = block(draft.inbound))
    }

    fun updateProfile(accountId: String, block: (AgendaPublishProfileDraft) -> AgendaPublishProfileDraft) {
        draft = draft.copy(profiles = draft.profiles.map { if (it.accountId == accountId) block(it) else it })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Publicar agenda", style = MaterialTheme.typography.titleMedium)
            Text("Selecione perfis e informe apenas os dias. O Rota Certa agrupa configurações iguais em lotes nativos de até 20 datas.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = draft.monthYear,
                onValueChange = { draft = draft.copy(monthYear = it.take(7)) },
                label = { Text("Mês / ano (MM/AAAA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            PublisherTemplateEditor("IDA", draft.outbound) { updateTemplate(AgendaPublishDirection.IDA, it) }
            PublisherTemplateEditor("VOLTA", draft.inbound) { updateTemplate(AgendaPublishDirection.VOLTA, it) }

            Text("Perfis", style = MaterialTheme.typography.titleSmall)
            accounts.forEach { account ->
                val profile = draft.profiles.firstOrNull { it.accountId == account.id } ?: AgendaPublishProfileDraft(account.id)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(profile.selected, onCheckedChange = { checked -> updateProfile(account.id) { it.copy(selected = checked) } })
                            Column {
                                Text(account.displayLabel)
                                Text(if (account.profileUuid.isNullOrBlank()) "Login/UUID pendente" else "Conectado ✅", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(
                            value = profile.outboundDays,
                            onValueChange = { value -> updateProfile(account.id) { it.copy(outboundDays = value.take(120)) } },
                            label = { Text("Dias IDA: 4, 11, 18") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = profile.inboundDays,
                            onValueChange = { value -> updateProfile(account.id) { it.copy(inboundDays = value.take(120)) } },
                            label = { Text("Dias VOLTA: 5, 12, 19") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = profile.comment,
                            onValueChange = { value -> updateProfile(account.id) { it.copy(comment = value.take(300)) } },
                            label = { Text("Comentário opcional deste perfil") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        store.saveDraft(draft)
                        val plan = AgendaBatchPublisherPlanner.plan(draft, accounts, BlaBlaCollectorStateStore(context).lastResponse()?.trips.orEmpty())
                        status = when {
                            plan.errors.isNotEmpty() -> "Erros: ${plan.errors.joinToString(" | ")}"
                            else -> "${plan.batches.sumOf { it.dates.size }} carona(s) novas em ${plan.batches.size} lote(s) • ${plan.alreadyPublished} já existente(s)." +
                                plan.warnings.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = " Avisos: ", separator = " | ").orEmpty()
                        }
                        onChanged(status)
                    },
                ) { Text("Validar") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        store.saveDraft(draft)
                        val plan = AgendaBatchPublisherPlanner.plan(draft, accounts, BlaBlaCollectorStateStore(context).lastResponse()?.trips.orEmpty())
                        if (plan.errors.isNotEmpty()) {
                            status = plan.errors.joinToString(" | ")
                            onChanged(status)
                        } else if (plan.batches.isEmpty()) {
                            status = "Nenhuma carona nova para publicar."
                            onChanged(status)
                        } else {
                            store.replaceQueue(plan.batches)
                            UnifiedDebugEventStore.record(
                                "AGENDA_BATCH_PUBLISH_REQUESTED",
                                context.packageName,
                                "batches=${plan.batches.size} rides=${plan.batches.sumOf { it.dates.size }} profiles=${plan.batches.map { it.profileUuid }.distinct().size} alreadyPublished=${plan.alreadyPublished}",
                            )
                            launcher.launch(Intent(context, AgendaBatchPublisherActivity::class.java))
                        }
                    },
                ) { Text("Publicar todas") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PublisherTemplateEditor(
    label: String,
    template: AgendaPublishTemplate,
    update: ((AgendaPublishTemplate) -> AgendaPublishTemplate) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(template.originAddress, { v -> update { it.copy(originAddress = v.take(300)) } }, label = { Text("Endereço de origem") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(template.destinationAddress, { v -> update { it.copy(destinationAddress = v.take(300)) } }, label = { Text("Endereço de destino") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(template.departureTime, { v -> update { it.copy(departureTime = v.take(5)) } }, label = { Text("Horário") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(template.seats.toString(), { v -> update { it.copy(seats = v.filter(Char::isDigit).toIntOrNull() ?: 0) } }, label = { Text("Vagas") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(template.priceText, { v -> update { it.copy(priceText = v.take(20)) } }, label = { Text("Preço por lugar (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(template.automaticReservation, onCheckedChange = { checked -> update { it.copy(automaticReservation = checked) } })
                Text(if (template.automaticReservation) "Reserva automática" else "Analisar cada pedido")
            }
        }
    }
}

class AgendaBatchPublisherActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var store: AgendaBatchPublisherStore
    private lateinit var status: TextView
    private var webView: WebView? = null
    private var current: AgendaPublishBatch? = null
    private var batchStartedAt = 0L
    private var lastAction = ""
    private var stableTicks = 0

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
            finishWith(true, "Publicação em lote concluída. Sincronize a Agenda para confirmar as novas caronas.")
            return
        }
        val account = registry.get(batch.accountId)
        if (account == null || account.profileUuid?.equals(batch.profileUuid, ignoreCase = true) != true) {
            UnifiedDebugEventStore.record("AGENDA_BATCH_PUBLISH_FAILED", packageName, "batch=${batch.id} reason=account_identity_missing")
            finishWith(false, "Perfil ${batch.profileLabel} não está disponível; lote preservado.")
            return
        }
        current = batch
        batchStartedAt = System.currentTimeMillis()
        lastAction = ""
        stableTicks = 0
        status.text = "${batch.profileLabel} • ${batch.direction} • ${batch.dates.size} data(s)"
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
                view.postDelayed({ tick(view, root) }, 700)
            }
        }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webView = view
        UnifiedDebugEventStore.record(
            "AGENDA_BATCH_PUBLISH_START",
            packageName,
            "batch=${batch.id} profileUuidPresent=true direction=${batch.direction} dates=${batch.dates.size} originPresent=true destinationPresent=true commentPresent=${batch.comment.isNotBlank()}",
        )
        view.loadUrl(OFFER_URL)
    }

    private fun tick(view: WebView, root: LinearLayout) {
        val batch = current ?: return
        if (isFinishing || view !== webView) return
        if (System.currentTimeMillis() - batchStartedAt > BATCH_TIMEOUT_MS) {
            UnifiedDebugEventStore.record("AGENDA_BATCH_PUBLISH_FAILED", packageName, "batch=${batch.id} reason=timeout lastAction=$lastAction")
            finishWith(false, "Tempo excedido em ${batch.profileLabel} ${batch.direction}; lote preservado.")
            return
        }
        val script = automationScript(batch)
        view.evaluateJavascript(script) { encoded ->
            if (isFinishing || view !== webView) return@evaluateJavascript
            val action = runCatching { json.parseToJsonElement(encoded).toString().trim('"').replace("\\\"", "\"") }.getOrNull().orEmpty()
            if (action.isNotBlank() && action != "null") {
                if (action == lastAction) stableTicks++ else stableTicks = 0
                lastAction = action
                status.text = "${batch.profileLabel} • ${batch.direction} • $action"
                UnifiedDebugEventStore.record(
                    "AGENDA_BATCH_PUBLISH_STEP",
                    packageName,
                    "batch=${batch.id} action=${action.take(80)} stable=$stableTicks",
                )
                if (action.startsWith("SUCCESS")) {
                    store.removeBatch(batch.id)
                    UnifiedDebugEventStore.record(
                        "AGENDA_BATCH_PUBLISH_VERIFIED",
                        packageName,
                        "batch=${batch.id} dates=${batch.dates.size} profileUuidPresent=true direction=${batch.direction}",
                    )
                    startNext(root)
                    return@evaluateJavascript
                }
                if (action.startsWith("BLOCKED")) {
                    finishWith(false, "Publicação bloqueada em ${batch.profileLabel}: ${action.substringAfter(':', "etapa não reconhecida")}. Lote preservado.")
                    return@evaluateJavascript
                }
            }
            view.postDelayed({ tick(view, root) }, if (action.startsWith("WAIT")) 1100L else 750L)
        }
    }

    private fun automationScript(batch: AgendaPublishBatch): String {
        val payload = JSONObject.quote(json.encodeToString(batch))
        return """
            (function() {
              const b = JSON.parse($payload);
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const lower = (v) => clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
              const text = clean(document.body && document.body.innerText);
              const body = lower(text);
              const visible = (n) => !!n && (!n.getClientRects || n.getClientRects().length > 0);
              const controls = () => Array.from(document.querySelectorAll('button, a, [role="button"], [role="option"], label')).filter(visible);
              const clickText = (rx) => {
                const n = controls().find((x) => rx.test(lower(x.innerText || x.textContent || x.getAttribute('aria-label') || '')));
                if (n && typeof n.click === 'function') { n.click(); return true; }
                return false;
              };
              const setValue = (node, value) => {
                if (!node) return false;
                const proto = node.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(proto, 'value') && Object.getOwnPropertyDescriptor(proto, 'value').set;
                if (setter) setter.call(node, value); else node.value = value;
                node.dispatchEvent(new Event('input', { bubbles: true }));
                node.dispatchEvent(new Event('change', { bubbles: true }));
                node.focus();
                return true;
              };
              const next = () => clickText(/^(continuar|seguinte|proximo|avancar|ok|confirmar|\d+ datas? selecionadas?)$/i) ||
                (() => { const all = controls(); const n = all.find((x) => /→|arrow|next/i.test((x.innerText || '') + ' ' + (x.getAttribute('aria-label') || ''))); if (n) { n.click(); return true; } return false; })();
              const inputs = Array.from(document.querySelectorAll('input, textarea')).filter(visible);
              const selectSuggestion = (value) => {
                const key = lower(value);
                const options = Array.from(document.querySelectorAll('[role="option"], li, [data-testid*="suggest"], [data-testid*="option"]')).filter(visible);
                const exact = options.find((n) => lower(n.innerText || n.textContent).includes(key));
                const target = exact || options[0];
                if (target && typeof target.click === 'function') { target.click(); return true; }
                return false;
              };

              if (/como voce deseja se conectar|continuar com e-mail|entrar na sua conta/.test(body)) return 'BLOCKED:login necessario';
              if (/caronas? (foram )?(publicadas?|oferecidas?)|sua carona foi publicada|suas viagens/.test(body) && !/quando voce vai/.test(body)) return 'SUCCESS:confirmado';

              if (/de onde voce sai|saindo de/.test(body) && !/para onde voce vai/.test(body)) {
                const input = inputs.find((n) => /saindo|origem|partida|from/.test(lower((n.getAttribute('placeholder') || '') + ' ' + (n.getAttribute('aria-label') || '')))) || inputs[0];
                if (!input) return 'WAIT:origem_input';
                if (!lower(input.value).includes(lower(b.originAddress))) { setValue(input, b.originAddress); return 'ORIGEM:digitada'; }
                if (selectSuggestion(b.originAddress)) return 'ORIGEM:selecionada';
                return 'WAIT:origem_sugestao';
              }
              if (/para onde voce vai|indo para/.test(body)) {
                const input = inputs.find((n) => /indo|destino|chegada|to/.test(lower((n.getAttribute('placeholder') || '') + ' ' + (n.getAttribute('aria-label') || '')))) || inputs[0];
                if (!input) return 'WAIT:destino_input';
                if (!lower(input.value).includes(lower(b.destinationAddress))) { setValue(input, b.destinationAddress); return 'DESTINO:digitado'; }
                if (selectSuggestion(b.destinationAddress)) return 'DESTINO:selecionado';
                return 'WAIT:destino_sugestao';
              }
              if (/cidades? de passagem|de passagem para encontrar mais passageiros/.test(body)) {
                if (next()) return 'BOOST:sem_cidades_passagem';
                return 'WAIT:boost_continuar';
              }
              if (/quando voce vai/.test(body)) {
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
                if (changed) return 'DATAS:selecionando';
                const countLabel = controls().find((n) => new RegExp('^' + b.dates.length + ' datas? selecionadas?$','i').test(lower(n.innerText || n.textContent)));
                if (countLabel && typeof countLabel.click === 'function') { countLabel.click(); return 'DATAS:confirmadas'; }
                if (next()) return 'DATAS:continuar';
                return 'WAIT:datas';
              }
              if (/a que horas voce vai buscar seus passageiros|que horas/.test(body)) {
                const timeInput = inputs.find((n) => (n.getAttribute('type') || '').toLowerCase() === 'time');
                if (timeInput) { setValue(timeInput, b.departureTime); if (next()) return 'HORARIO:' + b.departureTime; return 'HORARIO:definido'; }
                const fields = Array.from(document.querySelectorAll('[role="spinbutton"], input')).filter(visible);
                if (fields.length >= 2) {
                  const parts=b.departureTime.split(':'); setValue(fields[0], parts[0]); setValue(fields[1], parts[1]); if (next()) return 'HORARIO:' + b.departureTime;
                }
                if (clickText(/^ok$/i)) return 'HORARIO:ok';
                return 'WAIT:horario';
              }
              if (/quantos passageiros.*voce podera levar|quantos passageiros/.test(body)) {
                const numbers = Array.from(document.querySelectorAll('body *')).filter((n) => visible(n) && /^\d+$/.test(clean(n.innerText)) && clean(n.innerText).length <= 2);
                const wanted = Number(b.seats);
                const currentNode = numbers.find((n) => Number(clean(n.innerText)) >= 1 && Number(clean(n.innerText)) <= 8);
                const current = currentNode ? Number(clean(currentNode.innerText)) : NaN;
                if (Number.isFinite(current) && current !== wanted) {
                  const rx = current < wanted ? /(adicionar|mais|plus|\+)/ : /(remover|menos|minus|−|-)/;
                  if (clickText(rx)) return 'VAGAS:' + current + '->' + wanted;
                }
                if (Number.isFinite(current) && current === wanted && next()) return 'VAGAS:' + wanted;
                return 'WAIT:vagas';
              }
              if (/reserva automatica|analisar cada pedido/.test(body)) {
                const desired = b.automaticReservation ? /ativar reserva automatica/ : /analisar cada pedido/;
                if (clickText(desired)) return 'RESERVA:' + (b.automaticReservation ? 'automatica' : 'manual');
                return 'WAIT:reserva';
              }
              if (/valor recomendado|preco|maximo permitido|quanto.*lugar/.test(body)) {
                if (b.priceText) {
                  const priceInput = inputs.find((n) => /preco|valor|price/.test(lower((n.getAttribute('aria-label') || '') + ' ' + (n.getAttribute('placeholder') || '')))) || inputs.find((n) => (n.getAttribute('inputmode') || '') === 'numeric');
                  if (priceInput) { setValue(priceInput, b.priceText.replace(/[^0-9,.]/g,'')); if (next()) return 'PRECO:definido'; return 'PRECO:digitado'; }
                }
                if (next()) return 'PRECO:padrao_plataforma';
                return 'WAIT:preco';
              }
              if (/comentario para seus passageiros|gostaria de incluir um comentario|comentario/.test(body)) {
                if (b.comment) {
                  const area = inputs.find((n) => n.tagName === 'TEXTAREA') || inputs[0];
                  if (area && clean(area.value) !== b.comment) { setValue(area, b.comment); return 'COMENTARIO:digitado'; }
                }
                if (next()) return b.comment ? 'COMENTARIO:continuar' : 'COMENTARIO:omitido';
                return 'WAIT:comentario';
              }
              const offer = controls().find((n) => /^(oferecer|publicar)(?:\s+\d+)?\s+caronas?|^oferecer carona$/i.test(lower(n.innerText || n.textContent)));
              if (offer && typeof offer.click === 'function') { offer.click(); return 'PUBLICAR:confirmado'; }
              return 'WAIT:tela_nao_classificada';
            })();
        """.trimIndent()
    }

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
        private const val BATCH_TIMEOUT_MS = 150_000L
    }
}
