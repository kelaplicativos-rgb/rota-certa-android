package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict data contract for Agenda-driven trip creation.
 *
 * It deliberately does NOT execute JavaScript, shell, intents, files or arbitrary commands.
 * The existing RotaCertaStructuredCommand0410 CREATE_TRIPS JSON remains the V1 semantic
 * contract. This executor adds a backwards-compatible batch envelope (`commands`) and
 * durable orchestration/idempotency around the already existing AgendaBatchPublisher.
 */
internal const val AGENDA_TRIP_SCRIPT_SCHEMA_0558 = "1.0"
internal const val AGENDA_TRIP_SCRIPT_MAX_CHARS_0558 = 256_000

internal enum class AgendaTripScriptState0558 {
    PENDING,
    VALIDATED,
    READY,
    CREATING,
    PUBLISHED_AMBIGUOUS,
    RECONCILING,
    CONFIRMED,
    CANONICALIZED,
    SYNCED,
    SKIPPED_EXISTING,
    SKIPPED_ALREADY_COMPLETED,
    FAILED_RETRYABLE,
    FAILED_BLOCKING,
    CANCELLED_NOT_STARTED,
}

internal enum class AgendaTripScriptDirection0558 { IDA, VOLTA }

internal data class AgendaTripInstruction0558(
    val index: Int,
    val profileUuid: String,
    val date: String,
    val departureTime: String,
    val origin: String,
    val destination: String,
    val seats: Int,
    val publish: Boolean,
    val priceText: String = "",
    val automaticReservation: Boolean = false,
    val comment: String = "",
    val direction: AgendaTripScriptDirection0558 = AgendaTripScriptDirection0558.IDA,
)

internal data class AgendaTripScript0558(
    val schemaVersion: String,
    val scriptId: String,
    val scriptHash: String,
    val createdAt: String,
    val normalized: String,
    val instructions: List<AgendaTripInstruction0558>,
)

internal data class AgendaTripScriptIssue0558(
    val blocking: Boolean,
    val instructionIndex: Int?,
    val message: String,
)

internal data class AgendaTripScriptPlanItem0558(
    val instruction: AgendaTripInstruction0558,
    val fingerprint: String,
    val action: AgendaTripScriptState0558,
    val accountId: String = "",
    val accountLabel: String = "",
    val note: String = "",
)

internal data class AgendaTripScriptPlan0558(
    val script: AgendaTripScript0558,
    val items: List<AgendaTripScriptPlanItem0558>,
    val issues: List<AgendaTripScriptIssue0558>,
) {
    val blockingIssues: List<AgendaTripScriptIssue0558> get() = issues.filter { it.blocking }
    val readyCount: Int get() = items.count { it.action == AgendaTripScriptState0558.READY }
    val skippedCount: Int get() = items.count {
        it.action == AgendaTripScriptState0558.SKIPPED_EXISTING ||
            it.action == AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED
    }
}

internal class AgendaTripScriptContractException0558(message: String) : IllegalArgumentException(message)

internal object AgendaTripScriptParser0558 {
    private val envelopeKeys = setOf("schemaVersion", "scriptId", "createdAt", "commands")
    private val commandKeys = setOf(
        "schemaVersion", "action", "mode", "validation", "onInvalidDate",
        "profileUuid", "profileSelection", "date", "dates", "dateTokens", "temporal",
        "origin", "destination", "departureTime", "outbound", "return", "inbound",
        "returnDepartureTime", "roundTrip", "seats", "publish", "priceText",
        "automaticReservation", "comment", "preExecution",
    )
    private val legKeys = setOf("origin", "destination", "departureTime", "time")
    private val temporalKeys = setOf("explicitDate", "time", "raw", "relative", "weekday", "dayOfMonth", "month", "year")
    private val profileKeys = setOf("profileUuid", "strategy", "checkAllDriverProfiles")
    private val validationKeys = setOf("validateCalendarDate", "failClosed")
    private val preExecutionKeys = setOf("runPublicCollector", "checkPhysicalContinuity", "checkScheduleConflicts")

    fun parse(raw: String): AgendaTripScript0558 {
        requireContract(raw.isNotBlank(), "O script está vazio.")
        requireContract(raw.length <= AGENDA_TRIP_SCRIPT_MAX_CHARS_0558, "O script excede 256 KB.")
        val root = try { JSONObject(stripFence(raw)) } catch (_: Throwable) {
            throw AgendaTripScriptContractException0558("JSON inválido.")
        }
        val normalized = canonicalJson(root)
        val hash = sha256("agenda-script-v1|$normalized")
        val isEnvelope = root.has("commands")
        val commands: List<JSONObject>
        val scriptId: String
        val createdAt: String
        if (isEnvelope) {
            rejectUnknown(root, envelopeKeys, "raiz")
            requireSchema(root.opt("schemaVersion"))
            val array = root.optJSONArray("commands")
                ?: throw AgendaTripScriptContractException0558("commands precisa ser uma lista.")
            requireContract(array.length() in 1..100, "O script deve conter entre 1 e 100 comandos.")
            commands = (0 until array.length()).map { i ->
                array.optJSONObject(i) ?: throw AgendaTripScriptContractException0558("Comando ${i + 1} não é um objeto JSON.")
            }
            scriptId = root.optString("scriptId").trim().ifBlank { "script-${hash.take(24)}" }
            createdAt = root.optString("createdAt").trim()
        } else {
            requireSchema(root.opt("schemaVersion"))
            commands = listOf(root)
            scriptId = "script-${hash.take(24)}"
            createdAt = ""
        }

        val instructions = mutableListOf<AgendaTripInstruction0558>()
        commands.forEachIndexed { commandIndex, command ->
            parseCommand(command, commandIndex, instructions)
        }
        requireContract(instructions.isNotEmpty(), "Nenhuma viagem executável foi declarada.")
        return AgendaTripScript0558(
            schemaVersion = AGENDA_TRIP_SCRIPT_SCHEMA_0558,
            scriptId = scriptId.take(120),
            scriptHash = hash,
            createdAt = createdAt.take(80),
            normalized = normalized,
            instructions = instructions.mapIndexed { i, v -> v.copy(index = i + 1) },
        )
    }

    private fun parseCommand(command: JSONObject, commandIndex: Int, sink: MutableList<AgendaTripInstruction0558>) {
        rejectUnknown(command, commandKeys, "comando ${commandIndex + 1}")
        requireSchema(command.opt("schemaVersion"))
        val action = command.optString("action").trim().uppercase(Locale.ROOT)
        requireContract(action in setOf("CREATE_TRIPS", "CREATE_TRIP", "CREATE_ROUND_TRIP", "CREATE_ROUND_TRIPS"),
            "Comando ${commandIndex + 1}: action precisa ser CREATE_TRIPS.")
        val mode = command.optString("mode", "EXECUTE").trim().uppercase(Locale.ROOT)
        requireContract(mode in setOf("EXECUTE", "EXECUTION", "LIVE", "SIMULATION", "SIMULATE"),
            "Comando ${commandIndex + 1}: mode não suportado: $mode.")

        command.optJSONObject("validation")?.let { validation ->
            rejectUnknown(validation, validationKeys, "validation")
            if (validation.has("validateCalendarDate")) requireContract(validation.optBoolean("validateCalendarDate"), "validateCalendarDate não pode ser desativado.")
            if (validation.has("failClosed")) requireContract(validation.optBoolean("failClosed"), "failClosed não pode ser desativado.")
        }
        command.optJSONObject("preExecution")?.let { rejectUnknown(it, preExecutionKeys, "preExecution") }
        val onInvalid = command.optString("onInvalidDate").trim()
        requireContract(onInvalid.isBlank() || onInvalid == "REJECT_AND_DO_NOT_PUBLISH", "onInvalidDate precisa ser REJECT_AND_DO_NOT_PUBLISH.")

        val profile = command.optString("profileUuid").trim().ifBlank {
            command.optJSONObject("profileSelection")?.also { rejectUnknown(it, profileKeys, "profileSelection") }
                ?.optString("profileUuid")?.trim().orEmpty()
        }
        requireContract(profile.isNotBlank(), "Comando ${commandIndex + 1}: profileUuid é obrigatório para publicação determinística.")

        val temporal = command.optJSONObject("temporal")?.also { rejectUnknown(it, temporalKeys, "temporal") }
        val dates = when {
            command.has("dates") -> stringArray(command, "dates")
            command.has("dateTokens") -> stringArray(command, "dateTokens")
            command.optString("date").isNotBlank() -> listOf(command.optString("date").trim())
            temporal?.optString("explicitDate")?.isNotBlank() == true -> listOf(temporal.optString("explicitDate").trim())
            else -> emptyList()
        }
        requireContract(dates.isNotEmpty(), "Comando ${commandIndex + 1}: informe date/dates.")
        requireContract(dates.size <= 62, "Comando ${commandIndex + 1}: máximo de 62 datas.")
        dates.forEach { requireContract(validDate(it), "Comando ${commandIndex + 1}: data inválida: $it.") }

        val outbound = command.optJSONObject("outbound")?.also { rejectUnknown(it, legKeys, "outbound") }
        val origin = command.optString("origin").trim().ifBlank { outbound?.optString("origin")?.trim().orEmpty() }
        val destination = command.optString("destination").trim().ifBlank { outbound?.optString("destination")?.trim().orEmpty() }
        val time = temporal?.optString("time")?.trim().orEmpty().ifBlank {
            command.optString("departureTime").trim().ifBlank {
                outbound?.optString("departureTime")?.trim().orEmpty().ifBlank { outbound?.optString("time")?.trim().orEmpty() }
            }
        }
        requireContract(origin.isNotBlank(), "Comando ${commandIndex + 1}: origem obrigatória.")
        requireContract(destination.isNotBlank(), "Comando ${commandIndex + 1}: destino obrigatório.")
        requireContract(!samePlace(origin, destination), "Comando ${commandIndex + 1}: origem e destino não podem ser iguais.")
        requireContract(validTime(time), "Comando ${commandIndex + 1}: horário inválido: $time.")
        val seats = if (command.has("seats")) command.optInt("seats", -1) else -1
        requireContract(seats in 1..4, "Comando ${commandIndex + 1}: seats precisa ficar entre 1 e 4 para o publicador atual.")
        val publish = if (command.has("publish")) command.optBoolean("publish") else true
        val price = command.optString("priceText").trim()
        val automatic = command.optBoolean("automaticReservation", false)
        val comment = command.optString("comment").trim()

        dates.distinct().sorted().forEach { date ->
            sink += AgendaTripInstruction0558(0, profile, date, time, origin, destination, seats, publish, price, automatic, comment)
        }

        val inbound = (command.optJSONObject("return") ?: command.optJSONObject("inbound"))?.also { rejectUnknown(it, legKeys, "return") }
        val roundTrip = action.startsWith("CREATE_ROUND") || command.optBoolean("roundTrip", false) || inbound != null
        if (roundTrip) {
            val returnOrigin = inbound?.optString("origin")?.trim().orEmpty().ifBlank { destination }
            val returnDestination = inbound?.optString("destination")?.trim().orEmpty().ifBlank { origin }
            requireContract(samePlace(returnOrigin, destination) && samePlace(returnDestination, origin),
                "Comando ${commandIndex + 1}: a volta declarada não é o inverso da ida.")
            val returnTime = command.optString("returnDepartureTime").trim().ifBlank {
                inbound?.optString("departureTime")?.trim().orEmpty().ifBlank { inbound?.optString("time")?.trim().orEmpty() }
            }
            requireContract(validTime(returnTime), "Comando ${commandIndex + 1}: returnDepartureTime obrigatório/válido para ida e volta.")
            dates.distinct().sorted().forEach { date ->
                sink += AgendaTripInstruction0558(0, profile, date, returnTime, returnOrigin, returnDestination, seats, publish, price, automatic, comment, AgendaTripScriptDirection0558.VOLTA)
            }
        }
    }

    internal fun fingerprint(tenantId: String, item: AgendaTripInstruction0558): String {
        val semantic = listOf(
            tenantId.trim().lowercase(Locale.ROOT), item.profileUuid.trim().lowercase(Locale.ROOT), item.date,
            item.departureTime, normalizePlace(item.origin), normalizePlace(item.destination), item.seats.toString(),
            item.publish.toString(), item.priceText.trim(), item.automaticReservation.toString(), item.comment.trim(), item.direction.name,
        ).joinToString("|")
        return "trip-instruction-v1:" + sha256(semantic)
    }

    internal fun samePlace(left: String, right: String): Boolean {
        val a = normalizePlace(left); val b = normalizePlace(right)
        return a.isNotBlank() && b.isNotBlank() && (a == b || a.contains(b) || b.contains(a))
    }

    private fun normalizePlace(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value); true }.getOrDefault(false)
    private fun validTime(value: String): Boolean = runCatching { LocalTime.parse(value); Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(value) }.getOrDefault(false)
    private fun stringArray(root: JSONObject, key: String): List<String> {
        val array = root.optJSONArray(key) ?: throw AgendaTripScriptContractException0558("$key precisa ser uma lista.")
        return (0 until array.length()).map { i -> array.optString(i).trim() }.filter(String::isNotBlank)
    }
    private fun requireSchema(value: Any?) {
        val normalized = when (value) { null, JSONObject.NULL -> AGENDA_TRIP_SCRIPT_SCHEMA_0558; is Number -> if (value.toInt() == 1) "1.0" else value.toString(); else -> value.toString().trim() }
        requireContract(normalized == AGENDA_TRIP_SCRIPT_SCHEMA_0558, "schemaVersion não suportada: $normalized.")
    }
    private fun rejectUnknown(root: JSONObject, allowed: Set<String>, where: String) {
        val unknown = root.keys().asSequence().filterNot(allowed::contains).toList()
        requireContract(unknown.isEmpty(), "Campo não suportado nesta versão em $where: ${unknown.joinToString()}.")
    }
    private fun requireContract(condition: Boolean, message: String) { if (!condition) throw AgendaTripScriptContractException0558(message) }
    private fun stripFence(raw: String): String {
        val t = raw.trim().removePrefix("\uFEFF").trim(); if (!t.startsWith("```")) return t
        val first = t.indexOf('\n'); val last = t.lastIndexOf("```")
        if (first < 0 || last <= first) throw AgendaTripScriptContractException0558("Bloco JSON incompleto.")
        return t.substring(first + 1, last).trim()
    }
    private fun canonicalJson(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { k -> JSONObject.quote(k) + ":" + canonicalJson(value.get(k)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.get(it)) }
        JSONObject.NULL, null -> "null"
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal data class AgendaTripExecutionItem0558(
    val instruction: AgendaTripInstruction0558,
    val fingerprint: String,
    val state: AgendaTripScriptState0558,
    val accountId: String,
    val attempts: Int = 0,
    val externalTripId: String = "",
    val canonicalTripId: String = "",
    val lastError: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

internal data class AgendaTripExecution0558(
    val executionId: String,
    val scriptId: String,
    val scriptHash: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val cancelled: Boolean,
    val rawScript: String,
    val items: List<AgendaTripExecutionItem0558>,
)

internal class AgendaTripScriptStore0558(context: Context) {
    private val app = context.applicationContext
    private val scope = RotaCertaTenantRegistry(app).activeScope()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(name: String) = scope.key(name)

    fun completedFingerprints(): Set<String> = prefs.getStringSet(key(KEY_COMPLETED), emptySet()).orEmpty().toSet()
    fun markCompleted(fingerprint: String) {
        synchronized(LOCK) {
            val next = completedFingerprints().toMutableSet().apply { add(fingerprint) }
            require(prefs.edit().putStringSet(key(KEY_COMPLETED), next.takeLast(2000).toSet()).commit()) { "Falha ao persistir idempotência." }
        }
    }

    fun active(): AgendaTripExecution0558? = prefs.getString(key(KEY_ACTIVE), null)?.let(::decodeExecution)
    fun save(execution: AgendaTripExecution0558) {
        val normalized = execution.copy(updatedAtMillis = System.currentTimeMillis())
        require(prefs.edit().putString(key(KEY_ACTIVE), encodeExecution(normalized).toString()).commit()) { "Falha ao persistir execução do script." }
    }
    fun finish(execution: AgendaTripExecution0558) {
        synchronized(LOCK) {
            val history = history().toMutableList().apply { add(0, execution.copy(updatedAtMillis = System.currentTimeMillis())) }.take(20)
            val array = JSONArray(); history.forEach { array.put(encodeExecution(it)) }
            require(prefs.edit().putString(key(KEY_HISTORY), array.toString()).remove(key(KEY_ACTIVE)).commit()) { "Falha ao concluir histórico do script." }
        }
    }
    fun history(): List<AgendaTripExecution0558> = runCatching {
        val array = JSONArray(prefs.getString(key(KEY_HISTORY), "[]")); (0 until array.length()).mapNotNull { decodeExecution(array.optJSONObject(it)?.toString().orEmpty()) }
    }.getOrDefault(emptyList())

    private fun encodeInstruction(v: AgendaTripInstruction0558) = JSONObject()
        .put("index", v.index).put("profileUuid", v.profileUuid).put("date", v.date).put("departureTime", v.departureTime)
        .put("origin", v.origin).put("destination", v.destination).put("seats", v.seats).put("publish", v.publish)
        .put("priceText", v.priceText).put("automaticReservation", v.automaticReservation).put("comment", v.comment).put("direction", v.direction.name)
    private fun decodeInstruction(o: JSONObject) = AgendaTripInstruction0558(
        o.optInt("index"), o.optString("profileUuid"), o.optString("date"), o.optString("departureTime"), o.optString("origin"), o.optString("destination"),
        o.optInt("seats"), o.optBoolean("publish", true), o.optString("priceText"), o.optBoolean("automaticReservation"), o.optString("comment"),
        runCatching { AgendaTripScriptDirection0558.valueOf(o.optString("direction")) }.getOrDefault(AgendaTripScriptDirection0558.IDA),
    )
    private fun encodeExecution(e: AgendaTripExecution0558): JSONObject {
        val items = JSONArray(); e.items.forEach { item -> items.put(JSONObject().put("instruction", encodeInstruction(item.instruction)).put("fingerprint", item.fingerprint)
            .put("state", item.state.name).put("accountId", item.accountId).put("attempts", item.attempts).put("externalTripId", item.externalTripId)
            .put("canonicalTripId", item.canonicalTripId).put("lastError", item.lastError).put("updatedAtMillis", item.updatedAtMillis)) }
        return JSONObject().put("executionId", e.executionId).put("scriptId", e.scriptId).put("scriptHash", e.scriptHash).put("startedAtMillis", e.startedAtMillis)
            .put("updatedAtMillis", e.updatedAtMillis).put("cancelled", e.cancelled).put("rawScript", e.rawScript).put("items", items)
    }
    private fun decodeExecution(raw: String): AgendaTripExecution0558? = runCatching {
        val o = JSONObject(raw); val a = o.getJSONArray("items"); val items = (0 until a.length()).map { i -> val x = a.getJSONObject(i); AgendaTripExecutionItem0558(
            instruction = decodeInstruction(x.getJSONObject("instruction")), fingerprint = x.getString("fingerprint"),
            state = AgendaTripScriptState0558.valueOf(x.getString("state")), accountId = x.optString("accountId"), attempts = x.optInt("attempts"),
            externalTripId = x.optString("externalTripId"), canonicalTripId = x.optString("canonicalTripId"), lastError = x.optString("lastError"), updatedAtMillis = x.optLong("updatedAtMillis")) }
        AgendaTripExecution0558(o.getString("executionId"), o.getString("scriptId"), o.getString("scriptHash"), o.optLong("startedAtMillis"), o.optLong("updatedAtMillis"), o.optBoolean("cancelled"), o.optString("rawScript"), items)
    }.getOrNull()

    companion object { private const val PREFS = "rota_certa_agenda_trip_script_executor_0558"; private const val KEY_ACTIVE = "active"; private const val KEY_HISTORY = "history"; private const val KEY_COMPLETED = "completed_fingerprints"; private val LOCK = Any() }
}

internal object AgendaTripScriptPlanner0558 {
    fun plan(context: Context, script: AgendaTripScript0558): AgendaTripScriptPlan0558 {
        val app = context.applicationContext
        val tenantId = RotaCertaTenantRegistry(app).activeScope().tenantId
        val accounts = BlaBlaDynamicAccountRegistry(app).list()
        val completed = AgendaTripScriptStore0558(app).completedFingerprints()
        val external = BlaBlaCollectorStateStore(app).lastResponse()?.trips.orEmpty()
        val issues = mutableListOf<AgendaTripScriptIssue0558>()
        val items = script.instructions.map { instruction ->
            val fp = AgendaTripScriptParser0558.fingerprint(tenantId, instruction)
            val matchingAccounts = accounts.filter { it.profileUuid?.trim()?.equals(instruction.profileUuid.trim(), ignoreCase = true) == true }
            when {
                matchingAccounts.size != 1 -> {
                    issues += AgendaTripScriptIssue0558(true, instruction.index, "Perfil solicitado não corresponde exatamente a uma sessão externa confirmada.")
                    AgendaTripScriptPlanItem0558(instruction, fp, AgendaTripScriptState0558.FAILED_BLOCKING, note = "profile_identity_mismatch")
                }
                fp in completed -> AgendaTripScriptPlanItem0558(instruction, fp, AgendaTripScriptState0558.SKIPPED_ALREADY_COMPLETED, matchingAccounts.single().id, matchingAccounts.single().displayLabel, "fingerprint concluído")
                findExternal(instruction, external) != null -> AgendaTripScriptPlanItem0558(instruction, fp, AgendaTripScriptState0558.SKIPPED_EXISTING, matchingAccounts.single().id, matchingAccounts.single().displayLabel, "viagem externa já existente")
                !instruction.publish -> AgendaTripScriptPlanItem0558(instruction, fp, AgendaTripScriptState0558.FAILED_BLOCKING, matchingAccounts.single().id, matchingAccounts.single().displayLabel, "publish=false não cria estado paralelo; somente simulação")
                else -> AgendaTripScriptPlanItem0558(instruction, fp, AgendaTripScriptState0558.READY, matchingAccounts.single().id, matchingAccounts.single().displayLabel)
            }
        }
        return AgendaTripScriptPlan0558(script, items, issues)
    }

    fun findExternal(instruction: AgendaTripInstruction0558, trips: List<BlaBlaCollectorTrip>): BlaBlaCollectorTrip? = trips.firstOrNull { trip ->
        trip.profile_uuid.trim().equals(instruction.profileUuid.trim(), ignoreCase = true) && trip.date == instruction.date &&
            trip.departure_time?.take(5) == instruction.departureTime &&
            AgendaTripScriptParser0558.samePlace(trip.actual_departure ?: trip.search_from.orEmpty(), instruction.origin) &&
            AgendaTripScriptParser0558.samePlace(trip.actual_arrival ?: trip.search_to.orEmpty(), instruction.destination) &&
            !trip.trip_id.isNullOrBlank()
    }

    fun reconcile(context: Context, item: AgendaTripExecutionItem0558): AgendaTripExecutionItem0558 {
        val app = context.applicationContext
        val external = findExternal(item.instruction, BlaBlaCollectorStateStore(app).lastResponse()?.trips.orEmpty())
            ?: return item.copy(state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS, lastError = "external_evidence_not_found_yet", updatedAtMillis = System.currentTimeMillis())
        val externalId = external.trip_id?.trim().orEmpty()
        val canonical = TripStore(app).trips().firstOrNull { trip ->
            trip.blablaProfileUuid?.trim()?.equals(item.instruction.profileUuid.trim(), ignoreCase = true) == true && trip.blablaTripId?.trim() == externalId
        }
        return if (canonical == null) {
            item.copy(state = AgendaTripScriptState0558.CONFIRMED, externalTripId = externalId, lastError = "canonical_sync_pending", updatedAtMillis = System.currentTimeMillis())
        } else {
            item.copy(state = AgendaTripScriptState0558.SYNCED, externalTripId = externalId,
                canonicalTripId = canonical.tripKey.ifBlank { canonical.id }, lastError = "", updatedAtMillis = System.currentTimeMillis())
        }
    }

    fun toPublisherBatch(item: AgendaTripExecutionItem0558, accountLabel: String): AgendaPublishBatch = AgendaPublishBatch(
        accountId = item.accountId, profileUuid = item.instruction.profileUuid, profileLabel = accountLabel,
        direction = if (item.instruction.direction == AgendaTripScriptDirection0558.IDA) AgendaPublishDirection.IDA else AgendaPublishDirection.VOLTA,
        dates = listOf(item.instruction.date), originAddress = item.instruction.origin, destinationAddress = item.instruction.destination,
        departureTime = item.instruction.departureTime, seats = item.instruction.seats, priceText = item.instruction.priceText,
        automaticReservation = item.instruction.automaticReservation, comment = item.instruction.comment,
    )
}

internal object AgendaTripScriptTrace0558 {
    fun record(context: Context, event: String, executionId: String = "", scriptId: String = "", index: Int? = null, detail: String = "") {
        val safe = buildString {
            if (executionId.isNotBlank()) append("executionId=").append(executionId.take(80)).append(' ')
            if (scriptId.isNotBlank()) append("scriptId=").append(scriptId.take(80)).append(' ')
            if (index != null) append("tripIndex=").append(index).append(' ')
            append(detail.replace(Regex("(?i)(token|cookie|password|authorization)=[^ ]+"), "$1=[redacted]").take(400))
        }.trim()
        UnifiedDebugEventStore.record("AGENDA_SCRIPT_$event", context.applicationContext.packageName, safe)
    }

    fun newExecution(plan: AgendaTripScriptPlan0558, raw: String): AgendaTripExecution0558 {
        val now = System.currentTimeMillis()
        return AgendaTripExecution0558(
            executionId = UUID.randomUUID().toString(), scriptId = plan.script.scriptId, scriptHash = plan.script.scriptHash,
            startedAtMillis = now, updatedAtMillis = now, cancelled = false, rawScript = raw.take(AGENDA_TRIP_SCRIPT_MAX_CHARS_0558),
            items = plan.items.map { p -> AgendaTripExecutionItem0558(p.instruction, p.fingerprint, p.action, p.accountId) },
        )
    }
}
