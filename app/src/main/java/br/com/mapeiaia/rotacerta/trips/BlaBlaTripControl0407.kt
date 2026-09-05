package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Typed trip-operation contract layered on the existing BlaBlaBrowserOrchestrator.
 * It does not own transport, session, collection or canonical state.
 */
internal enum class BlaBlaTransport0407 {
    NETWORK,
    BROWSER,
    DOM,
    HYBRID,
}

internal enum class BlaBlaCapabilityEvidence0407 {
    DISCOVERED_STATIC,
    NETWORK_OBSERVED,
    NAVIGATION_CONFIRMED,
    WRITE_OBSERVED,
    WRITE_VERIFIED,
    UNSUPPORTED,
    NOT_ELIGIBLE,
    AUTH_REQUIRED,
    BROKEN_FOR_VERSION,
}

internal enum class BlaBlaTripCapability0407 {
    REVERIFY_TRIP,
    SET_TRIP_SEATS,
    SET_TRIP_BOOST,
    SET_SMART_STOPOVERS,
    SET_INSTANT_BOOKING,
    SET_TWO_MAX_IN_BACK,
    SET_WOMEN_ONLY,
    SET_TRIP_VEHICLE,
    SET_TRIP_COMMENT,
    BOOKING_REQUESTS_READ,
    ACCEPT_BOOKING_REQUEST,
    DECLINE_BOOKING_REQUEST,
    SET_TRIP_DATE,
    SET_DEPARTURE_TIME,
    SET_TRIP_ROUTE,
    SET_TRIP_PRICE,
    DUPLICATE_TRIP,
    CREATE_RETURN_TRIP,
    CANCEL_TRIP,
    TRIP_PUBLISH,
}

internal data class BlaBlaTripTarget0407(
    val tenantId: String,
    val accountId: String,
    val profileUuid: String,
    val tripId: String,
    val tripHref: String,
) {
    val strongIdentityKey: String
        get() = listOf(tenantId, accountId, profileUuid.lowercase(), tripId).joinToString("|")
}

internal data class BlaBlaCapabilityState0407(
    val capability: BlaBlaTripCapability0407,
    val evidence: BlaBlaCapabilityEvidence0407,
    val readable: Boolean = false,
    val writable: Boolean = false,
    val verified: Boolean = false,
    val preferredTransport: BlaBlaTransport0407? = null,
    val fallbackTransport: BlaBlaTransport0407? = null,
    val currentState: String = "",
    val failure: String = "",
)

internal data class BlaBlaCapabilityManifest0407(
    val states: Map<BlaBlaTripCapability0407, BlaBlaCapabilityState0407>,
    val runtimeVersion: String = "",
    val runtimeFingerprint: String = "",
)

internal data class BlaBlaTripCapabilitySnapshot0407(
    val target: BlaBlaTripTarget0407?,
    val states: Map<BlaBlaTripCapability0407, BlaBlaCapabilityState0407>,
    val lastVerifiedAtMillis: Long = 0L,
) {
    fun state(capability: BlaBlaTripCapability0407): BlaBlaCapabilityState0407? = states[capability]

    fun canShow(capability: BlaBlaTripCapability0407): Boolean =
        states[capability]?.let { state ->
            state.evidence !in setOf(
                BlaBlaCapabilityEvidence0407.DISCOVERED_STATIC,
                BlaBlaCapabilityEvidence0407.UNSUPPORTED,
                BlaBlaCapabilityEvidence0407.NOT_ELIGIBLE,
                BlaBlaCapabilityEvidence0407.BROKEN_FOR_VERSION,
            ) && (state.readable || state.writable)
        } == true
}

internal enum class BlaBlaCommandMode0407 {
    EXECUTE,
    DRY_RUN,
}

internal enum class BlaBlaCommandOrigin0407 {
    CARD,
    SCRIPT,
    PLANNER,
    SYSTEM_RECONCILIATION,
}

internal data class BlaBlaCommand0407(
    val commandId: String,
    val tenantId: String,
    val accountId: String,
    val profileUuid: String,
    val tripId: String,
    val passengerKey: String = "",
    val operation: BlaBlaTripCapability0407,
    val desiredState: String = "",
    val origin: BlaBlaCommandOrigin0407,
    val idempotencyKey: String,
    val preconditions: List<String> = emptyList(),
    val expectedRevision: String = "",
    val requestedAtMillis: Long = System.currentTimeMillis(),
    val mode: BlaBlaCommandMode0407 = BlaBlaCommandMode0407.EXECUTE,
) {
    companion object {
        fun forTarget(
            target: BlaBlaTripTarget0407,
            operation: BlaBlaTripCapability0407,
            origin: BlaBlaCommandOrigin0407,
            desiredState: String = "",
            passengerKey: String = "",
            preconditions: List<String> = emptyList(),
            mode: BlaBlaCommandMode0407 = BlaBlaCommandMode0407.EXECUTE,
            expectedRevision: String = "",
        ): BlaBlaCommand0407 {
            val commandId = UUID.randomUUID().toString()
            return BlaBlaCommand0407(
                commandId = commandId,
                tenantId = target.tenantId,
                accountId = target.accountId,
                profileUuid = target.profileUuid,
                tripId = target.tripId,
                passengerKey = passengerKey.take(200),
                operation = operation,
                desiredState = desiredState,
                origin = origin,
                idempotencyKey = sha256TripPublication0387(
                    listOf(
                        target.strongIdentityKey,
                        passengerKey.take(200),
                        operation.name,
                        desiredState,
                        commandId,
                    ).joinToString("|"),
                ),
                preconditions = preconditions.map(String::trim).filter(String::isNotBlank).distinct().take(32),
                expectedRevision = expectedRevision,
                mode = mode,
            )
        }

        fun forTarget(
            target: BlaBlaTripTarget0407,
            operation: BlaBlaTripCapability0407,
            origin: String,
            desiredState: String = "",
            passengerKey: String = "",
            preconditions: List<String> = emptyList(),
            mode: BlaBlaCommandMode0407 = BlaBlaCommandMode0407.EXECUTE,
            expectedRevision: String = "",
        ): BlaBlaCommand0407 {
            val typedOrigin = runCatching {
                BlaBlaCommandOrigin0407.valueOf(origin.trim().uppercase())
            }.getOrElse {
                throw IllegalArgumentException("Origem de comando BlaBlaCar não suportada.")
            }
            return forTarget(
                target = target,
                operation = operation,
                origin = typedOrigin,
                desiredState = desiredState,
                passengerKey = passengerKey,
                preconditions = preconditions,
                mode = mode,
                expectedRevision = expectedRevision,
            )
        }
    }
}

internal enum class BlaBlaCommandStatus0407 {
    QUEUED,
    NO_OP_ALREADY_MATCHED,
    VERIFIED_SUCCESS,
    PUBLISHED_URL_PENDING,
    NOT_AVAILABLE,
    NOT_ELIGIBLE,
    TRIP_NOT_FOUND,
    AUTH_REQUIRED,
    TEMPORARILY_RESTRICTED,
    ACCOUNT_NOT_AVAILABLE,
    UNVERIFIED_TARGET,
    CAPABILITY_NOT_VERIFIED,
    STALE_STATE,
    BROKEN_FOR_VERSION,
    FAILED,
    UNVERIFIED,
}

internal data class BlaBlaCommandResult0407(
    val commandId: String,
    val target: BlaBlaTripTarget0407?,
    val capability: BlaBlaTripCapability0407,
    val transportUsed: BlaBlaTransport0407? = null,
    val before: String = "",
    val desired: String = "",
    val after: String = "",
    val writeAttempted: Boolean = false,
    val verification: String = "",
    val status: BlaBlaCommandStatus0407,
    val errorCode: String = "",
    val exceptionMessage: String = "",
    val rootCause: String = "",
    val externalRevisionBefore: String = "",
    val externalRevisionAfter: String = "",
    val startedAtMillis: Long = System.currentTimeMillis(),
    val finishedAtMillis: Long = System.currentTimeMillis(),
)


internal data class BlaBlaCommandAuditSnapshot0407(
    val commandId: String,
    val status: BlaBlaCommandStatus0407,
    val requestedAtMillis: Long,
    val finishedAtMillis: Long,
    val errorCode: String = "",
    val queuedVersionCode: Long = 0L,
    val pending: Boolean = status == BlaBlaCommandStatus0407.QUEUED,
)

internal const val BLA_BLA_COMMAND_PENDING_LEASE_MILLIS_0463 = 30L * 60L * 1000L

internal fun commandPendingLeaseCurrent0463(
    status: BlaBlaCommandStatus0407,
    requestedAtMillis: Long,
    queuedVersionCode: Long,
    currentVersionCode: Long,
    nowMillis: Long,
): Boolean =
    status == BlaBlaCommandStatus0407.QUEUED &&
        requestedAtMillis > 0L &&
        queuedVersionCode > 0L &&
        queuedVersionCode == currentVersionCode &&
        nowMillis >= requestedAtMillis &&
        nowMillis - requestedAtMillis < BLA_BLA_COMMAND_PENDING_LEASE_MILLIS_0463

/**
 * Process-local invalidation only. Durable command status lives in SharedPreferences;
 * canonical trip state continues to live exclusively in TripStore.
 */
internal object BlaBlaTripControlEvents0407 {
    private val counter = AtomicLong(0L)
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun notifyChanged() {
        mutableRevision.value = counter.incrementAndGet()
    }
}

/**
 * Durable control/audit status for the last command targeting one strong external trip.
 * This is not a trip-state store and never supplies Timeline/Agenda business data.
 */
internal class BlaBlaTripCommandStatusStore0407(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(
        target: BlaBlaTripTarget0407,
        nowMillis: Long = System.currentTimeMillis(),
    ): BlaBlaCommandAuditSnapshot0407? {
        val prefix = prefix(target)
        val rawStatus = prefs.getString(prefix + "_status", null) ?: return null
        val status = runCatching { BlaBlaCommandStatus0407.valueOf(rawStatus) }.getOrNull() ?: return null
        val requestedAtMillis = prefs.getLong(prefix + "_requested", 0L)
        val queuedVersionCode = prefs.getLong(prefix + "_version_code", 0L)
        val pending = commandPendingLeaseCurrent0463(
            status = status,
            requestedAtMillis = requestedAtMillis,
            queuedVersionCode = queuedVersionCode,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            nowMillis = nowMillis,
        )
        return BlaBlaCommandAuditSnapshot0407(
            commandId = prefs.getString(prefix + "_command", "").orEmpty(),
            status = status,
            requestedAtMillis = requestedAtMillis,
            finishedAtMillis = prefs.getLong(prefix + "_finished", 0L),
            errorCode = prefs.getString(prefix + "_error", "").orEmpty(),
            queuedVersionCode = queuedVersionCode,
            pending = pending,
        )
    }

    /**
     * Returns false when a still-live command already owns this exact target/capability.
     * A stale marker is recoverable so process death cannot permanently block the card.
     */
    fun tryMarkQueued(
        target: BlaBlaTripTarget0407,
        commandId: String,
        requestedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val current = get(target, nowMillis)
        if (current?.pending == true) return false

        val prefix = prefix(target)
        val saved = prefs.edit()
            .putString(prefix + "_command", commandId.take(160))
            .putString(prefix + "_status", BlaBlaCommandStatus0407.QUEUED.name)
            .putLong(prefix + "_requested", requestedAtMillis.coerceAtLeast(1L))
            .putLong(prefix + "_finished", 0L)
            .putLong(prefix + "_version_code", BuildConfig.VERSION_CODE.toLong())
            .putString(prefix + "_error", "")
            .commit()
        if (saved) BlaBlaTripControlEvents0407.notifyChanged()
        return saved
    }

    fun recordResult(result: BlaBlaCommandResult0407) {
        val target = result.target ?: return
        val prefix = prefix(target)
        val saved = prefs.edit()
            .putString(prefix + "_command", result.commandId.take(160))
            .putString(prefix + "_status", result.status.name)
            .putLong(prefix + "_requested", result.startedAtMillis.coerceAtLeast(1L))
            .putLong(prefix + "_finished", result.finishedAtMillis.coerceAtLeast(result.startedAtMillis))
            .putLong(prefix + "_version_code", BuildConfig.VERSION_CODE.toLong())
            .putString(prefix + "_error", result.errorCode.take(120))
            .commit()
        if (saved) BlaBlaTripControlEvents0407.notifyChanged()
    }

    private fun prefix(target: BlaBlaTripTarget0407): String =
        "trip_" + sha256TripPublication0387(target.strongIdentityKey).take(32)

    companion object {
        private const val PREFS = "rota_certa_blablacar_trip_control_0407"
    }
}

internal fun blaBlaVerificationLabel0407(
    audit: BlaBlaCommandAuditSnapshot0407?,
    lastObservedAtMillis: Long,
    strongTargetAvailable: Boolean,
): String = when {
    audit?.pending == true -> "⟳ Atualizando"
    audit?.status == BlaBlaCommandStatus0407.QUEUED -> "⚠ Verificação anterior interrompida"
    audit?.status == BlaBlaCommandStatus0407.AUTH_REQUIRED -> "⚠ Sessão necessária"
    audit?.status == BlaBlaCommandStatus0407.TEMPORARILY_RESTRICTED -> "⚠ BlaBlaCar temporariamente indisponível"
    audit?.status == BlaBlaCommandStatus0407.ACCOUNT_NOT_AVAILABLE -> "⚠ Conta indisponível"
    audit?.status == BlaBlaCommandStatus0407.UNVERIFIED_TARGET -> "⚠ Identidade externa não confirmada"
    audit?.status == BlaBlaCommandStatus0407.BROKEN_FOR_VERSION ||
        audit?.errorCode == "BROKEN_FOR_VERSION" -> "⚠ Recurso incompatível"
    audit?.status in setOf(
        BlaBlaCommandStatus0407.NOT_AVAILABLE,
        BlaBlaCommandStatus0407.NOT_ELIGIBLE,
        BlaBlaCommandStatus0407.TRIP_NOT_FOUND,
    ) -> "⚠ Recurso indisponível"
    audit?.status in setOf(BlaBlaCommandStatus0407.FAILED, BlaBlaCommandStatus0407.UNVERIFIED) -> "⚠ Falha na verificação"
    audit?.status == BlaBlaCommandStatus0407.PUBLISHED_URL_PENDING -> "✓ Publicado • URL pendente"
    audit?.status == BlaBlaCommandStatus0407.VERIFIED_SUCCESS -> "✓ Verificado agora"
    lastObservedAtMillis > 0L -> "✓ Verificado"
    strongTargetAvailable -> "Dados desatualizados"
    else -> "⚠ Identidade externa incompleta"
}

internal enum class BlaBlaTripAction0407 {
    REVERIFY,
    SEAT_DETAILS,
    OPEN_PUBLICATION,
}

internal data class BlaBlaTripActionPalette0407(
    val primary: List<BlaBlaTripAction0407>,
    val overflow: List<BlaBlaTripAction0407>,
)

internal fun buildBlaBlaTripActionPalette0407(
    snapshot: BlaBlaTripCapabilitySnapshot0407,
    hasPublicationHref: Boolean,
): BlaBlaTripActionPalette0407 {
    val primary = buildList {
        if (snapshot.canShow(BlaBlaTripCapability0407.REVERIFY_TRIP)) add(BlaBlaTripAction0407.REVERIFY)
        if (snapshot.canShow(BlaBlaTripCapability0407.SET_TRIP_SEATS)) add(BlaBlaTripAction0407.SEAT_DETAILS)
    }
    val overflow = buildList {
        if (snapshot.canShow(BlaBlaTripCapability0407.REVERIFY_TRIP)) add(BlaBlaTripAction0407.REVERIFY)
        if (snapshot.canShow(BlaBlaTripCapability0407.SET_TRIP_SEATS)) add(BlaBlaTripAction0407.SEAT_DETAILS)
        if (hasPublicationHref && snapshot.target != null) add(BlaBlaTripAction0407.OPEN_PUBLICATION)
    }.distinct()
    return BlaBlaTripActionPalette0407(primary = primary.take(3), overflow = overflow)
}
internal object BlaBlaCapabilityRegistry0407 {
    private val staticOnlyCandidates = setOf(
        BlaBlaTripCapability0407.SET_TRIP_BOOST,
        BlaBlaTripCapability0407.SET_SMART_STOPOVERS,
        BlaBlaTripCapability0407.SET_INSTANT_BOOKING,
        BlaBlaTripCapability0407.SET_TWO_MAX_IN_BACK,
        BlaBlaTripCapability0407.SET_WOMEN_ONLY,
        BlaBlaTripCapability0407.SET_TRIP_VEHICLE,
        BlaBlaTripCapability0407.SET_TRIP_COMMENT,
        BlaBlaTripCapability0407.BOOKING_REQUESTS_READ,
        BlaBlaTripCapability0407.ACCEPT_BOOKING_REQUEST,
        BlaBlaTripCapability0407.DECLINE_BOOKING_REQUEST,
        BlaBlaTripCapability0407.SET_TRIP_DATE,
        BlaBlaTripCapability0407.SET_DEPARTURE_TIME,
        BlaBlaTripCapability0407.SET_TRIP_ROUTE,
        BlaBlaTripCapability0407.SET_TRIP_PRICE,
        BlaBlaTripCapability0407.DUPLICATE_TRIP,
        BlaBlaTripCapability0407.CREATE_RETURN_TRIP,
        BlaBlaTripCapability0407.CANCEL_TRIP,
        BlaBlaTripCapability0407.TRIP_PUBLISH,
    )

    fun snapshot(
        target: BlaBlaTripTarget0407?,
        seatSyncState: BlaBlaPublicationSeatSyncState? = null,
        lastVerifiedAtMillis: Long = 0L,
    ): BlaBlaTripCapabilitySnapshot0407 {
        val states = linkedMapOf<BlaBlaTripCapability0407, BlaBlaCapabilityState0407>()
        staticOnlyCandidates.forEach { capability ->
            states[capability] = BlaBlaCapabilityState0407(
                capability = capability,
                evidence = BlaBlaCapabilityEvidence0407.DISCOVERED_STATIC,
                readable = false,
                writable = false,
                verified = false,
                failure = "write_not_verified_for_current_runtime",
            )
        }
        states[BlaBlaTripCapability0407.REVERIFY_TRIP] = if (target != null) {
            BlaBlaCapabilityState0407(
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                evidence = BlaBlaCapabilityEvidence0407.NAVIGATION_CONFIRMED,
                readable = true,
                writable = false,
                verified = true,
                preferredTransport = BlaBlaTransport0407.HYBRID,
                fallbackTransport = BlaBlaTransport0407.BROWSER,
            )
        } else {
            BlaBlaCapabilityState0407(
                capability = BlaBlaTripCapability0407.REVERIFY_TRIP,
                evidence = BlaBlaCapabilityEvidence0407.UNSUPPORTED,
                failure = "strong_target_unavailable",
            )
        }

        val seatVerified = seatSyncState?.state == BlaBlaPublicationSeatSyncVisualState.SYNCED &&
            seatSyncState.lastObservedPublishedSeats != null
        states[BlaBlaTripCapability0407.SET_TRIP_SEATS] = when {
            target == null -> BlaBlaCapabilityState0407(
                capability = BlaBlaTripCapability0407.SET_TRIP_SEATS,
                evidence = BlaBlaCapabilityEvidence0407.UNSUPPORTED,
                failure = "strong_target_unavailable",
            )
            seatVerified -> BlaBlaCapabilityState0407(
                capability = BlaBlaTripCapability0407.SET_TRIP_SEATS,
                evidence = BlaBlaCapabilityEvidence0407.WRITE_VERIFIED,
                readable = true,
                writable = true,
                verified = true,
                preferredTransport = BlaBlaTransport0407.BROWSER,
                fallbackTransport = BlaBlaTransport0407.DOM,
                currentState = seatSyncState.lastObservedPublishedSeats.toString(),
            )
            else -> BlaBlaCapabilityState0407(
                capability = BlaBlaTripCapability0407.SET_TRIP_SEATS,
                evidence = BlaBlaCapabilityEvidence0407.NAVIGATION_CONFIRMED,
                readable = true,
                writable = false,
                verified = false,
                preferredTransport = BlaBlaTransport0407.BROWSER,
                fallbackTransport = BlaBlaTransport0407.DOM,
                currentState = seatSyncState?.lastObservedPublishedSeats?.toString().orEmpty(),
                failure = "write_requires_verified_readback_for_this_trip",
            )
        }
        return BlaBlaTripCapabilitySnapshot0407(
            target = target,
            states = states,
            lastVerifiedAtMillis = lastVerifiedAtMillis,
        )
    }
}

internal fun resolveBlaBlaTripTarget0407(
    context: Context,
    entry: TripTimelineEntry,
): BlaBlaTripTarget0407? = resolveBlaBlaTripTarget0407(
    context = context,
    entry = entry,
    accounts = BlaBlaDynamicAccountRegistry(context.applicationContext).list(),
)

/**
 * Composition-safe overload. The caller may preload the dynamic-account registry once
 * for the whole Timeline instead of decoding it again for every LazyColumn card.
 */
internal fun resolveBlaBlaTripTarget0407(
    context: Context,
    entry: TripTimelineEntry,
    accounts: List<BlaBlaDynamicAccount>,
): BlaBlaTripTarget0407? {
    val tenantId = RotaCertaTenantRegistry(context.applicationContext).activeScope().tenantId.trim()
    val profileUuid = canonicalTimelineProfileUuid(entry)?.trim()?.lowercase().orEmpty()
    val tripId = entry.blablaTripId?.trim().orEmpty()
    val tripHref = entry.blablaTripHref?.trim().orEmpty()
    if (tenantId.isBlank() || profileUuid.isBlank() || tripId.isBlank() || tripHref.isBlank()) return null
    if (BlaBlaCollectorUrlModule.tripId(tripHref) != tripId) return null
    val matchingAccounts = accounts.filter { account ->
        account.profileUuid?.trim()?.lowercase() == profileUuid
    }
    if (matchingAccounts.size != 1) return null
    return BlaBlaTripTarget0407(
        tenantId = tenantId,
        accountId = matchingAccounts.single().id,
        profileUuid = profileUuid,
        tripId = tripId,
        tripHref = tripHref,
    )
}
