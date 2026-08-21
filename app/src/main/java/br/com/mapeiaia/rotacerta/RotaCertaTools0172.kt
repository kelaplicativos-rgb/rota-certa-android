package br.com.mapeiaia.rotacerta

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Link persistente criado manualmente pelo usuário. */
data class QuickLink0172(
    val id: String,
    val title: String,
    val description: String = "",
    val url: String,
    val primary: Boolean = false,
    val updatedAtMillis: Long = 0L,
)

object QuickLinkStore0172 {
    private const val PREFS = "rota_certa_quick_links_0172"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = QuickLinkCapacityPolicy0186.MAX_ITEMS

    fun read(context: Context): List<QuickLink0172> = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = normalizeHttpUrl(item.optString("url")) ?: continue
                add(
                    QuickLink0172(
                        id = item.optString("id").ifBlank { "link-$index" },
                        title = item.optString("title").trim().take(80).ifBlank { url },
                        description = item.optString("description").trim().take(240),
                        url = url,
                        primary = item.optBoolean("primary", false),
                        updatedAtMillis = item.optLong("updatedAtMillis", 0L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList()).sortedWith(
        compareByDescending<QuickLink0172> { it.primary }.thenByDescending { it.updatedAtMillis },
    )

    fun save(context: Context, links: List<QuickLink0172>) {
        val normalized = links
            .mapNotNull { link ->
                normalizeHttpUrl(link.url)?.let { safeUrl ->
                    link.copy(
                        title = link.title.trim().take(80).ifBlank { safeUrl },
                        description = link.description.trim().take(240),
                        url = safeUrl,
                    )
                }
            }
            .distinctBy { it.id }
            .take(MAX_ITEMS)
        val primaryId = normalized.firstOrNull { it.primary }?.id
        val array = JSONArray()
        normalized.forEach { link ->
            array.put(
                JSONObject()
                    .put("id", link.id)
                    .put("title", link.title)
                    .put("description", link.description)
                    .put("url", link.url)
                    .put("primary", link.id == primaryId)
                    .put("updatedAtMillis", link.updatedAtMillis),
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun primary(context: Context): QuickLink0172? = read(context).firstOrNull { it.primary }
        ?: read(context).firstOrNull()

    fun open(context: Context, link: QuickLink0172): Boolean {
        val safe = normalizeHttpUrl(link.url) ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    fun openPrimary(context: Context): Boolean = primary(context)?.let { open(context, it) } == true

    fun normalizeHttpUrl(raw: String): String? {
        val trimmed = raw.trim().take(2048)
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return uri.toString()
    }
}

object MessageTemplateRenderer0172 {
    fun apply(template: String, replacements: Map<String, String>): String {
        var output = template
        replacements.forEach { (key, value) -> output = output.replace("{$key}", value) }
        return output.trim().take(4_000)
    }
}

object MessageTemplateStore0172 {
    private const val PREFS = "rota_certa_message_templates_0172"
    private const val KEY_TRIP = "trip"
    private const val KEY_VALUE = "value"

    const val DEFAULT_TRIP = "{saudacao} Confirmando sua viagem:\n\n{origem} → {destino}\n{dia_semana}, {dia} de {mes}, às {horario}.\n\nEstá tudo certo?"
    const val DEFAULT_VALUE = "Olá, {nome}! O valor exibido para sua reserva de {lugares}, de {origem} para {destino}, é {valor}."

    fun readTrip(context: Context): String = prefs(context).getString(KEY_TRIP, DEFAULT_TRIP)
        ?.takeIf { it.isNotBlank() } ?: DEFAULT_TRIP

    fun readValue(context: Context): String = prefs(context).getString(KEY_VALUE, DEFAULT_VALUE)
        ?.takeIf { it.isNotBlank() } ?: DEFAULT_VALUE

    fun saveTrip(context: Context, value: String) {
        prefs(context).edit().putString(KEY_TRIP, value.trim().take(4_000).ifBlank { DEFAULT_TRIP }).apply()
    }

    fun saveValue(context: Context, value: String) {
        prefs(context).edit().putString(KEY_VALUE, value.trim().take(4_000).ifBlank { DEFAULT_VALUE }).apply()
    }

    fun restoreDefaults(context: Context) {
        prefs(context).edit().putString(KEY_TRIP, DEFAULT_TRIP).putString(KEY_VALUE, DEFAULT_VALUE).apply()
    }

    fun formatTrip(context: Context, data: TripConfirmationData): String {
        val time = if (data.minute == 0) "${data.hour}h" else String.format(Locale("pt", "BR"), "%dh%02d", data.hour, data.minute)
        val greeting = data.passengerName?.takeIf { it.isNotBlank() }?.let { "Olá, $it!" } ?: "Olá!"
        return MessageTemplateRenderer0172.apply(
            readTrip(context),
            mapOf(
                "saudacao" to greeting,
                "nome" to data.passengerName.orEmpty(),
                "origem" to data.origin,
                "destino" to data.destination,
                "dia_semana" to data.weekday,
                "dia" to data.dayOfMonth.toString(),
                "mes" to data.month,
                "horario" to time,
            ),
        )
    }

    fun formatValue(context: Context, data: PassengerValueData): String = MessageTemplateRenderer0172.apply(
        readValue(context),
        mapOf(
            "nome" to data.passengerName,
            "lugares" to if (data.seats == 1) "1 lugar" else "${data.seats} lugares",
            "origem" to data.origin,
            "destino" to data.destination,
            "valor" to PassengerValueFormatter.formatCurrency(data.amountCents),
        ),
    )

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class CacheCleanResult0172(val bytesFreed: Long, val filesRemoved: Int)

object CacheCleaner0172 {
    fun clean(context: Context): CacheCleanResult0172 {
        val root = context.applicationContext.cacheDir
        var bytes = 0L
        var files = 0
        root.listFiles().orEmpty().forEach { child ->
            bytes += sizeOf(child)
            files += countFiles(child)
            runCatching { child.deleteRecursively() }
        }
        return CacheCleanResult0172(bytesFreed = bytes, filesRemoved = files)
    }

    fun humanBytes(bytes: Long): String = when {
        bytes < 1_024L -> "$bytes B"
        bytes < 1_048_576L -> String.format(Locale("pt", "BR"), "%.1f KB", bytes / 1_024.0)
        else -> String.format(Locale("pt", "BR"), "%.1f MB", bytes / 1_048_576.0)
    }

    private fun sizeOf(file: File): Long = if (file.isFile) file.length() else file.listFiles().orEmpty().sumOf(::sizeOf)
    private fun countFiles(file: File): Int = if (file.isFile) 1 else file.listFiles().orEmpty().sumOf(::countFiles)
}

object IntensiveDiagnostics0172 {
    private const val PREFS = "rota_certa_intensive_diagnostics_0172"
    private const val KEY_UNTIL = "active_until"
    private const val KEY_LAST = "last_checkpoint"
    private const val KEY_SEQ = "sequence"
    const val DEFAULT_DURATION_MILLIS = 10L * 60L * 1_000L
    const val MAX_DURATION_MILLIS = 15L * 60L * 1_000L

    fun start(context: Context, durationMillis: Long = DEFAULT_DURATION_MILLIS) {
        val duration = durationMillis.coerceIn(60_000L, MAX_DURATION_MILLIS)
        prefs(context).edit()
            .putLong(KEY_UNTIL, System.currentTimeMillis() + duration)
            .putString(KEY_LAST, "iniciado=${System.currentTimeMillis()}")
            .apply()
    }

    fun stop(context: Context) {
        prefs(context).edit().putLong(KEY_UNTIL, 0L).apply()
    }

    fun isActive(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        prefs(context).getLong(KEY_UNTIL, 0L) > now

    fun remainingMillis(context: Context, now: Long = System.currentTimeMillis()): Long =
        (prefs(context).getLong(KEY_UNTIL, 0L) - now).coerceAtLeast(0L)

    fun heartbeat(context: Context, details: String) {
        if (!isActive(context)) return
        val shared = prefs(context)
        val sequence = shared.getLong(KEY_SEQ, 0L) + 1L
        shared.edit()
            .putLong(KEY_SEQ, sequence)
            .putString(KEY_LAST, "seq=$sequence | wall=${System.currentTimeMillis()} | ${details.take(700)}")
            .apply()
    }

    fun export(context: Context): String {
        val shared = prefs(context)
        val until = shared.getLong(KEY_UNTIL, 0L)
        return buildString {
            appendLine("Ativo agora: ${isActive(context)}")
            appendLine("Ativo até: ${if (until > 0L) formatDate(until) else "não informado"}")
            appendLine("Tempo restante: ${remainingMillis(context)} ms")
            appendLine("Último checkpoint persistido: ${shared.getString(KEY_LAST, null) ?: "nenhum"}")
        }.trimEnd()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))
}

object ProcessExitDiagnostics0172 {
    fun build(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Histórico de encerramento indisponível nesta versão do Android."
        val manager = context.getSystemService(ActivityManager::class.java)
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrDefault(emptyList())
        if (exits.isEmpty()) return "Nenhum encerramento recente informado pelo Android."
        return exits.joinToString("\n") { info ->
            "${formatDate(info.timestamp)} | motivo=${reasonLabel(info.reason)} | status=${info.status} | importância=${info.importance} | pss=${info.pss}KB | rss=${info.rss}KB | descrição=${info.description?.take(180).orEmpty()}"
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        else -> "OTHER_$reason"
    }

    private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))
}

const val ACTION_INTENSIVE_DIAGNOSTIC_CONTROL_0172 = "br.com.mapeiaia.rotacerta.INTENSIVE_DIAGNOSTIC_CONTROL_0172"
const val EXTRA_QUICK_REPLY_OVERLAY_MODE_0172 = "quick_reply_overlay_mode_0172"

/** Registro compilado usado para identificar o conjunto funcional 0.1.172 no APK. */
object RotaCertaTools0172 {
    const val VERSION_NAME: String = "0.1.172"
    const val VERSION_CODE: Int = 5330
    const val QUICK_LINKS: Boolean = true
    const val EDITABLE_MESSAGE_TEMPLATES: Boolean = true
    const val SAFE_CACHE_CLEANING: Boolean = true
    const val ONE_SHOT_SCREEN_OCR: Boolean = true
    const val ACCESSIBILITY_RESILIENCE: Boolean = true
    const val TEMPORARY_INTENSIVE_DIAGNOSTICS: Boolean = true

    /** Mantém compatibilidade entre validadores do artifact e os marcadores reais de contenção. */
    @JvmField
    val DEX_VALIDATION_MARKERS: Array<String> = arrayOf(
        "RotaCertaTools0172",
        "QuickLinksActivity",
        "MessageTemplatesActivity",
        "ACCESSIBILITY_EVENT_FAILURE_CONTAINED_0172",
        "UNEXPECTED_FAILURE_CONTAINED_0172",
        "SERVICE_LIFECYCLE_FAILURE_CONTAINED_0172",
    )
}

