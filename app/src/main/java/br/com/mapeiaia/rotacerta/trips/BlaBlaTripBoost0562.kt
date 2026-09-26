package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Desired Boost state. There is deliberately no TOGGLE state. */
@Serializable
internal enum class BlaBlaTripBoostDesiredState0562 {
    ENABLED,
    DISABLED;

    companion object {
        fun parse(raw: String): BlaBlaTripBoostDesiredState0562? = when (raw.trim().uppercase(Locale.ROOT)) {
            "ON", "TRUE", "ENABLE", "ENABLED" -> ENABLED
            "OFF", "FALSE", "DISABLE", "DISABLED" -> DISABLED
            else -> null
        }
    }
}

@Serializable
internal enum class BlaBlaTripBoostObservedState0562 { ENABLED, DISABLED, UNKNOWN }

@Serializable
internal enum class BlaBlaTripBoostSyncState0562 {
    UNKNOWN,
    READ_CONFIRMED_ENABLED,
    READ_CONFIRMED_DISABLED,
    SKIPPED_ALREADY_IN_DESIRED_STATE,
    WRITE_PENDING,
    WRITE_VERIFIED,
    WRITE_AMBIGUOUS,
    FAILED_RETRYABLE,
    FAILED_BLOCKING,
    STALE_PLAN,
    SIMULATED,
}

@Serializable
internal data class BlaBlaTripBoostCommand0562(
    val schemaVersion: String,
    val action: String,
    val mode: String,
    val tripReference: String,
    val desiredState: BlaBlaTripBoostDesiredState0562,
    val explicitDate: String = "",
)

internal object BlaBlaTripBoostCommandParser0562 {
    private val rootKeys = setOf("schemaVersion", "action", "mode", "tripReference", "freeTextValue", "temporal")
    private val temporalKeys = setOf("explicitDate")

    fun isBoostCommand(raw: String): Boolean = runCatching {
        JSONObject(stripFence(raw)).optString("action").trim().equals("SET_TRIP_BOOST", ignoreCase = true)
    }.getOrDefault(false)

    fun parse(raw: String): BlaBlaTripBoostCommand0562 {
        require(raw.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "Script excede o limite permitido." }
        val root = JSONObject(stripFence(raw))
        val unknown = root.keys().asSequence().filterNot(rootKeys::contains).toList()
        require(unknown.isEmpty()) { "Campo não permitido em SET_TRIP_BOOST: ${unknown.joinToString()}" }
        val schema = root.optString("schemaVersion").trim()
        require(schema == "1.0") { "schemaVersion não suportada para SET_TRIP_BOOST." }
        val action = root.optString("action").trim().uppercase(Locale.ROOT)
        require(action == "SET_TRIP_BOOST") { "Action não permitida neste executor." }
        val mode = root.optString("mode", "EXECUTE").trim().uppercase(Locale.ROOT)
        require(mode in setOf("EXECUTE", "SIMULATE", "SIMULATION")) { "mode inválido para SET_TRIP_BOOST." }
        val reference = root.optString("tripReference").trim()
        require(reference.isNotBlank()) { "tripReference é obrigatório." }
        val desired = BlaBlaTripBoostDesiredState0562.parse(root.optString("freeTextValue"))
            ?: throw IllegalArgumentException("freeTextValue deve indicar ON ou OFF; toggle/valor ambíguo é proibido.")
        val temporal = root.optJSONObject("temporal")
        val explicitDate = if (temporal != null) {
            val temporalUnknown = temporal.keys().asSequence().filterNot(temporalKeys::contains).toList()
            require(temporalUnknown.isEmpty()) { "Campo temporal não permitido em SET_TRIP_BOOST: ${temporalUnknown.joinToString()}" }
            temporal.optString("explicitDate").trim().also { value ->
                if (value.isNotBlank()) runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("temporal.explicitDate inválida; use AAAA-MM-DD.") }
            }
        } else ""
        return BlaBlaTripBoostCommand0562(schema, action, mode, reference, desired, explicitDate)
    }

    private fun stripFence(value: String): String {
        val text = value.trim()
        if (!text.startsWith("```")) return text
        return text.removePrefix("```").removePrefix("json").substringBeforeLast("```").trim()
    }
}

internal enum class BlaBlaTripBoostTargetCode0562 {
    OK,
    TRIP_NOT_FOUND,
    AMBIGUOUS_TRIP,
    TRIP_CANCELLED_OR_DELETED,
    MISSING_TRIP_ID,
    MISSING_PROFILE_UUID,
    MISSING_STRONG_MANAGE_URL,
    DATE_MISMATCH,
    SESSION_NOT_FOUND,
    SESSION_AMBIGUOUS,
}

internal data class BlaBlaTripBoostTarget0562(
    val code: BlaBlaTripBoostTargetCode0562,
    val message: String,
    val trip: Trip? = null,
    val account: BlaBlaDynamicAccount? = null,
    val desiredState: BlaBlaTripBoostDesiredState0562? = null,
)

internal object BlaBlaTripBoostTargetResolver0562 {
    fun resolve(
        command: BlaBlaTripBoostCommand0562,
        trips: List<Trip>,
        accounts: List<BlaBlaDynamicAccount>,
        zoneId: ZoneId,
    ): BlaBlaTripBoostTarget0562 {
        val ref = command.tripReference.trim()
        val candidates = trips.filter { trip ->
            trip.id.equals(ref, ignoreCase = true) || trip.blablaTripId?.equals(ref, ignoreCase = true) == true
        }
        if (candidates.isEmpty()) return fail(BlaBlaTripBoostTargetCode0562.TRIP_NOT_FOUND, "Nenhuma viagem canônica corresponde exatamente a tripReference.")
        if (candidates.size != 1) return fail(BlaBlaTripBoostTargetCode0562.AMBIGUOUS_TRIP, "tripReference corresponde a mais de uma viagem canônica.")
        val trip = candidates.single()
        if (trip.deleted || trip.status == TripStatus.CANCELLED) return fail(BlaBlaTripBoostTargetCode0562.TRIP_CANCELLED_OR_DELETED, "A viagem está cancelada ou removida.", trip)
        val tripId = trip.blablaTripId?.trim().orEmpty()
        if (tripId.isBlank()) return fail(BlaBlaTripBoostTargetCode0562.MISSING_TRIP_ID, "A viagem não possui blablaTripId confirmado.", trip)
        val profileUuid = trip.blablaProfileUuid?.trim().orEmpty()
        if (profileUuid.isBlank()) return fail(BlaBlaTripBoostTargetCode0562.MISSING_PROFILE_UUID, "A viagem não possui blablaProfileUuid confirmado.", trip)
        if (command.explicitDate.isNotBlank()) {
            val observedDate = Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate()
            if (observedDate != LocalDate.parse(command.explicitDate)) {
                return fail(BlaBlaTripBoostTargetCode0562.DATE_MISMATCH, "A data informada não corresponde à viagem canônica.", trip)
            }
        }
        val manageUrl = trip.blablaManageUrl?.trim().orEmpty()
        if (!isStrongManageUrl(manageUrl, tripId)) {
            return fail(BlaBlaTripBoostTargetCode0562.MISSING_STRONG_MANAGE_URL, "A viagem não possui URL administrativa fortemente vinculada ao tripId.", trip)
        }
        val matching = accounts.filter { it.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true }
        if (matching.isEmpty()) return fail(BlaBlaTripBoostTargetCode0562.SESSION_NOT_FOUND, "Nenhuma sessão externa cadastrada corresponde ao profileUuid da viagem.", trip)
        if (matching.size != 1) return fail(BlaBlaTripBoostTargetCode0562.SESSION_AMBIGUOUS, "Mais de uma sessão externa corresponde ao profileUuid da viagem.", trip)
        return BlaBlaTripBoostTarget0562(BlaBlaTripBoostTargetCode0562.OK, "Alvo forte resolvido.", trip, matching.single(), command.desiredState)
    }

    internal fun isStrongManageUrl(raw: String, tripId: String): Boolean {
        if (raw.isBlank() || tripId.isBlank()) return false
        return runCatching {
            val uri = URI(raw)
            uri.scheme.equals("https", true) && uri.host?.isNotBlank() == true &&
                (uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty()).contains(tripId, ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun fail(code: BlaBlaTripBoostTargetCode0562, message: String, trip: Trip? = null) =
        BlaBlaTripBoostTarget0562(code, message, trip)
}

internal data class BlaBlaTripBoostPolicyDecision0562(
    val shouldWrite: Boolean,
    val terminalState: BlaBlaTripBoostSyncState0562? = null,
    val message: String,
)

internal object BlaBlaTripBoostPolicy0562 {
    fun beforeWrite(
        observed: BlaBlaTripBoostObservedState0562,
        desired: BlaBlaTripBoostDesiredState0562,
        identityVerified: Boolean,
        screenVerified: Boolean,
    ): BlaBlaTripBoostPolicyDecision0562 {
        if (!identityVerified || !screenVerified) return BlaBlaTripBoostPolicyDecision0562(false, BlaBlaTripBoostSyncState0562.FAILED_BLOCKING, "Identidade ou tela não confirmada.")
        if (observed == BlaBlaTripBoostObservedState0562.UNKNOWN) return BlaBlaTripBoostPolicyDecision0562(false, BlaBlaTripBoostSyncState0562.FAILED_BLOCKING, "Estado atual do Boost desconhecido.")
        val alreadyDesired = (observed == BlaBlaTripBoostObservedState0562.ENABLED) == (desired == BlaBlaTripBoostDesiredState0562.ENABLED)
        return if (alreadyDesired) {
            BlaBlaTripBoostPolicyDecision0562(false, BlaBlaTripBoostSyncState0562.SKIPPED_ALREADY_IN_DESIRED_STATE, "Boost já está no estado solicitado; nenhuma escrita necessária.")
        } else {
            BlaBlaTripBoostPolicyDecision0562(true, null, "Alteração mínima necessária.")
        }
    }

    fun afterWriteReadback(
        observed: BlaBlaTripBoostObservedState0562,
        desired: BlaBlaTripBoostDesiredState0562,
    ): BlaBlaTripBoostSyncState0562 {
        if (observed == BlaBlaTripBoostObservedState0562.UNKNOWN) return BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS
        val matches = (observed == BlaBlaTripBoostObservedState0562.ENABLED) == (desired == BlaBlaTripBoostDesiredState0562.ENABLED)
        return if (matches) BlaBlaTripBoostSyncState0562.WRITE_VERIFIED else BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS
    }
}

@Serializable
internal data class BlaBlaPublicationBoostOperation0562(
    val operationId: String,
    val correlationId: String,
    val idempotencyKey: String,
    val tenantId: String,
    val localTripId: String,
    val tripId: String,
    val profileUuid: String,
    val accountId: String,
    val profileLabel: String,
    val manageUrl: String,
    val desiredState: BlaBlaTripBoostDesiredState0562,
    val expectedDate: String = "",
    val observedCanonicalRevision: Long,
    val syncState: BlaBlaTripBoostSyncState0562 = BlaBlaTripBoostSyncState0562.UNKNOWN,
    val lastObservedState: BlaBlaTripBoostObservedState0562 = BlaBlaTripBoostObservedState0562.UNKNOWN,
    val attempts: Int = 0,
    val lastConfirmationSource: String = "",
    val lastError: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

internal class BlaBlaPublicationBoostSyncStateStore0562(context: Context) {
    private val app = context.applicationContext
    private val tenantId = TripStore(app).bookingReconcileScopeKey()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = "${tenantId}_operations"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun prepare(command: BlaBlaTripBoostCommand0562, target: BlaBlaTripBoostTarget0562): BlaBlaPublicationBoostOperation0562 {
        require(target.code == BlaBlaTripBoostTargetCode0562.OK)
        val trip = requireNotNull(target.trip)
        val account = requireNotNull(target.account)
        val desired = requireNotNull(target.desiredState)
        val tripId = requireNotNull(trip.blablaTripId).trim()
        val profileUuid = requireNotNull(trip.blablaProfileUuid).trim().lowercase(Locale.ROOT)
        val idempotency = sha256TripPublication0387(listOf(tenantId, "SET_TRIP_BOOST", profileUuid, tripId, desired.name).joinToString("|"))
        val existing = list().firstOrNull { it.idempotencyKey == idempotency && it.syncState in setOf(BlaBlaTripBoostSyncState0562.WRITE_VERIFIED, BlaBlaTripBoostSyncState0562.SKIPPED_ALREADY_IN_DESIRED_STATE) }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val op = BlaBlaPublicationBoostOperation0562(
            operationId = UUID.randomUUID().toString(),
            correlationId = "boost:${UUID.randomUUID()}",
            idempotencyKey = idempotency,
            tenantId = tenantId,
            localTripId = trip.id,
            tripId = tripId,
            profileUuid = profileUuid,
            accountId = account.id,
            profileLabel = account.displayLabel,
            manageUrl = requireNotNull(trip.blablaManageUrl).trim(),
            desiredState = desired,
            expectedDate = command.explicitDate,
            observedCanonicalRevision = trip.canonicalRevision,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        save(op)
        return op
    }

    fun get(operationId: String): BlaBlaPublicationBoostOperation0562? = list().firstOrNull { it.operationId == operationId }
    fun list(): List<BlaBlaPublicationBoostOperation0562> = runCatching {
        json.decodeFromString<List<BlaBlaPublicationBoostOperation0562>>(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAtMillis }

    fun pending(): List<BlaBlaPublicationBoostOperation0562> = list().filter { it.syncState in setOf(BlaBlaTripBoostSyncState0562.WRITE_PENDING, BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS, BlaBlaTripBoostSyncState0562.FAILED_RETRYABLE) }

    fun save(operation: BlaBlaPublicationBoostOperation0562): BlaBlaPublicationBoostOperation0562 {
        val current = list().filterNot { it.operationId == operation.operationId }
        val next = (listOf(operation) + current).take(100)
        check(prefs.edit().putString(key, json.encodeToString(next)).commit()) { "Falha ao persistir ledger de Boost." }
        return operation
    }

    fun update(
        operationId: String,
        state: BlaBlaTripBoostSyncState0562,
        observed: BlaBlaTripBoostObservedState0562? = null,
        source: String = "",
        error: String = "",
        incrementAttempt: Boolean = false,
    ): BlaBlaPublicationBoostOperation0562? {
        val current = get(operationId) ?: return null
        return save(current.copy(
            syncState = state,
            lastObservedState = observed ?: current.lastObservedState,
            lastConfirmationSource = source.take(120),
            lastError = error.take(180),
            attempts = current.attempts + if (incrementAttempt) 1 else 0,
            updatedAtMillis = System.currentTimeMillis(),
        ))
    }

    companion object { private const val PREFS = "rota_certa_trip_boost_sync_0562" }
}

internal object BlaBlaTripBoostTrace0562 {
    fun record(context: Context, event: String, operation: BlaBlaPublicationBoostOperation0562? = null, detail: String = "") {
        val safe = detail.replace(Regex("(?i)(cookie|token|password|authorization)=[^\\s]+"), "$1=[REDACTED]").take(300)
        UnifiedDebugEventStore.recordAlways(
            event,
            context.packageName,
            "trace=${operation?.correlationId.orEmpty()} tripIdPresent=${operation?.tripId?.isNotBlank() == true} profileUuidPresent=${operation?.profileUuid?.isNotBlank() == true} $safe",
        )
    }
}
