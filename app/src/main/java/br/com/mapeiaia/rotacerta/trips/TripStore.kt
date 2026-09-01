package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.TenantStorageScope
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TripStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tripsKey = tenantScope.key(KEY_TRIPS)
    private val bookingsKey = tenantScope.key(KEY_BOOKINGS)
    private val onlineKey = tenantScope.key(KEY_ONLINE)
    private val publicExternalBindingsKey = tenantScope.key(KEY_PUBLIC_EXTERNAL_BINDINGS)
    private val secretStore = TripSecretStore(appContext, tenantScope)
    private val publicAgendaLinkStore = PublicAgendaLinkStore(appContext, tenantScope)
    private val passengerIdentityStore = PassengerIdentityStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    internal fun bookingReconcileScopeKey(): String = tenantScope.tenantId

    fun trips(): List<Trip> = decode<List<Trip>>(prefs.getString(tripsKey, null)).orEmpty()
        .sortedByDescending(Trip::departureAtMillis)

    fun bookings(): List<Booking> = decode<List<Booking>>(prefs.getString(bookingsKey, null)).orEmpty()

    fun bookingsFor(tripId: String): List<Booking> = bookings().filter { it.tripId == tripId }

    fun getTrip(id: String): Trip? = trips().firstOrNull { it.id == id }

    fun publicExternalBindings(): List<PublicExternalTripBinding> =
        decode<List<PublicExternalTripBinding>>(prefs.getString(publicExternalBindingsKey, null)).orEmpty()
            .sortedByDescending(PublicExternalTripBinding::departureAtMillis)

    fun publicExternalBinding(remoteTripId: String): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.remoteTripId == remoteTripId }

    fun publicExternalBindingForStrongIdentity(profileUuid: String, blablaTripId: String): PublicExternalTripBinding? {
        val profile = profileUuid.trim()
        val tripId = blablaTripId.trim()
        if (profile.isBlank() || tripId.isBlank()) return null
        return publicExternalBindings().firstOrNull {
            it.profileUuid.trim().equals(profile, ignoreCase = true) && it.blablaTripId.trim() == tripId
        }
    }

    fun publicExternalBindingFor(entry: TripTimelineEntry): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.matches(entry) }

    fun savePublicExternalBinding(binding: PublicExternalTripBinding): PublicExternalTripBinding = synchronized(CANONICAL_LOCK) {
        val normalized = binding.copy(updatedAtMillis = System.currentTimeMillis())
        val current = publicExternalBindings().filterNot {
            it.remoteTripId == normalized.remoteTripId ||
                (normalized.bookingTripId.isNotBlank() && it.bookingTripId == normalized.bookingTripId) ||
                (normalized.profileUuid.isNotBlank() && normalized.blablaTripId.isNotBlank() &&
                    it.profileUuid.equals(normalized.profileUuid, ignoreCase = true) && it.blablaTripId == normalized.blablaTripId)
        }
        require(prefs.edit().putString(publicExternalBindingsKey, json.encodeToString(listOf(normalized) + current)).commit()) {
            "Falha ao persistir vínculo externo canônico."
        }
        normalized
    }

    fun saveTrip(trip: Trip): Trip = synchronized(CANONICAL_LOCK) {
        val incoming = trip.normalizedRecordOrigin()
        val existing = trips().firstOrNull { it.id == incoming.id }
        if (existing != null && existing.canonicalRevision > 0L && incoming.canonicalRevision < existing.canonicalRevision) {
            UnifiedDebugEventStore.record(
                "TRIP_CANONICAL_WRITE",
                appContext.packageName,
                "tenantId=" + tenantScope.tenantId +
                    " internalTripId=" + incoming.id +
                    " source=TripStore oldRevision=" + existing.canonicalRevision +
                    " newRevision=" + incoming.canonicalRevision +
                    " changedFields=unknown publicationTarget=LOCAL result=SKIP_STALE_REVISION" +
                    " reason=older_local_snapshot configVersion=" + existing.seatAllocationVersionUsed,
            )
            return@synchronized existing
        }
        val semanticChanged = existing == null || canonicalTripComparable0395(existing) != canonicalTripComparable0395(incoming)
        if (existing != null && !semanticChanged && incoming.canonicalRevision <= existing.canonicalRevision) {
            return@synchronized existing
        }
        val nextRevision = if (existing == null) {
            maxOf(1L, incoming.canonicalRevision)
        } else {
            nextCanonicalTripRevision0395(existing.canonicalRevision, incoming.canonicalRevision, semanticChanged)
        }
        val normalized = incoming.copy(
            canonicalRevision = nextRevision,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val current = trips().filterNot { it.id == normalized.id }
        require(prefs.edit().putString(tripsKey, json.encodeToString(listOf(normalized) + current)).commit()) {
            "Falha ao persistir estado canônico da viagem."
        }
        UnifiedDebugEventStore.record(
            "TRIP_CANONICAL_WRITE",
            appContext.packageName,
            "tenantId=" + tenantScope.tenantId +
                " internalTripId=" + normalized.id +
                " source=TripStore oldRevision=" + (existing?.canonicalRevision ?: 0L) +
                " newRevision=" + normalized.canonicalRevision +
                " changedFields=trip publicationTarget=LOCAL result=UPDATE" +
                " reason=canonical_mutation configVersion=" + normalized.seatAllocationVersionUsed,
        )
        normalized
    }

    private fun canonicalTripComparable0395(trip: Trip): Trip = trip.copy(
        canonicalRevision = 0L,
        updatedAtMillis = 0L,
    )

    /**
     * Reconciles active/future trips to the channel-derived inventory. The old
     * vehicle_capacity preference is intentionally absent from this calculation.
     */
    fun reconcileOperationalInventory(
        rotaCertaSeatAllocation: Int,
        nowMillis: Long = System.currentTimeMillis(),
        seatAllocationVersion: Long = 0L,
    ): Pair<Int, Int> = reconcileOperationalInventoryTripIds(
        rotaCertaSeatAllocation = rotaCertaSeatAllocation,
        seatAllocationVersion = seatAllocationVersion,
        nowMillis = nowMillis,
    ).size to 0

    internal fun reconcileOperationalInventoryTripIds(
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<String> = synchronized(CANONICAL_LOCK) {
        require(rotaCertaSeatAllocation in 0..999) { "Vagas do Rota Certa inválidas." }
        require(seatAllocationVersion >= 0L) { "Versão de vagas do Rota Certa inválida." }
        val activeStatuses = setOf(
            TripStatus.DRAFT,
            TripStatus.PUBLISHED,
            TripStatus.FULL,
            TripStatus.STARTING,
            TripStatus.ACTIVE,
        )
        val allBookings = bookings()
        val changedTripIds = linkedSetOf<String>()
        val currentTrips = trips()
        val reconciledTrips = currentTrips.map { trip ->
            val shouldApply = trip.status in activeStatuses &&
                (trip.departureAtMillis >= nowMillis || trip.status in setOf(TripStatus.STARTING, TripStatus.ACTIVE))
            if (!shouldApply) {
                trip
            } else {
                val withAllocation = trip.copy(
                    rotaCertaSeatAllocation = rotaCertaSeatAllocation,
                    seatAllocationVersionUsed = maxOf(trip.seatAllocationVersionUsed, seatAllocationVersion),
                )
                val derivedCapacity = operationalInventoryCapacity(withAllocation, allBookings)
                val changed = trip.capacity != derivedCapacity ||
                    trip.rotaCertaSeatAllocation != rotaCertaSeatAllocation ||
                    trip.seatAllocationVersionUsed < seatAllocationVersion
                if (changed) {
                    changedTripIds += trip.id
                    withAllocation.copy(
                        capacity = derivedCapacity,
                        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                        updatedAtMillis = nowMillis,
                    )
                } else trip
            }
        }
        if (changedTripIds.isNotEmpty()) {
            require(prefs.edit().putString(tripsKey, json.encodeToString(reconciledTrips)).commit()) {
                "Falha ao persistir fan-out canônico de vagas."
            }
        }
        changedTripIds
    }

    @Deprecated("Legacy compatibility only; vehicle capacity no longer drives trip inventory.")
    fun reconcilePhysicalPassengerCapacity(
        @Suppress("UNUSED_PARAMETER") capacity: Int,
        rotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<Int, Int> = reconcileOperationalInventory(rotaCertaSeatAllocation, nowMillis)

    fun deleteTrip(id: String) {
        prefs.edit()
            .putString(tripsKey, json.encodeToString(trips().filterNot { it.id == id }))
            .putString(bookingsKey, json.encodeToString(bookings().filterNot { it.tripId == id }))
            .apply()
    }

    /**
     * Historical compatibility shim. Timeline cleanup must never delete canonical local trips,
     * bookings or passenger history. Visual cleanup is handled by TripTimelineArchiveStore.
     */
    fun clearTimelineLocalData(): Pair<Int, Int> = 0 to 0

    fun saveBooking(booking: Booking): Booking =
        saveBookingsBatch(listOf(booking), preserveSourceUpdatedAt = false).single()

    /**
     * Persists a reconcile diff as one coherent booking snapshot. Remote imports keep
     * the server updatedAt value so an unchanged reservation compares equal on the
     * next pull instead of being imported again only because the local clock changed.
     */
    internal fun saveBookingsBatch(
        bookingsToSave: List<Booking>,
        preserveSourceUpdatedAt: Boolean,
    ): List<Booking> = synchronized(CANONICAL_LOCK) {
        if (bookingsToSave.isEmpty()) return emptyList()

        val existingAll = bookings()
        val existingById = existingAll.associateBy(Booking::id)
        val distinctIncoming = LinkedHashMap<String, Booking>().apply {
            bookingsToSave.forEach { put(it.id, it) }
        }.values.toList()

        val prepared = distinctIncoming.map { booking ->
            prepareBookingForPersistence(booking, existingById[booking.id])
        }
        val passengerIds = passengerIdentityStore.ensureLocalBookingProfilesBatch(prepared)
        val now = System.currentTimeMillis()
        val normalized = prepared.map { invariantState ->
            val passengerId = passengerIds[invariantState.id] ?: invariantState.passengerId
            invariantState.copy(
                passengerId = passengerId,
                updatedAtMillis = if (preserveSourceUpdatedAt) {
                    invariantState.updatedAtMillis.takeIf { it > 0L } ?: now
                } else {
                    now
                },
            )
        }

        val changedTripIds = normalized.asSequence()
            .filter { incoming ->
                val existing = existingById[incoming.id]
                existing == null || existing.copy(updatedAtMillis = 0L) != incoming.copy(updatedAtMillis = 0L)
            }
            .map(Booking::tripId)
            .toSet()
        if (changedTripIds.isEmpty()) return@synchronized normalized

        val next = mergeBookingBatch0380(existingAll, normalized)
        require(prefs.edit().putString(bookingsKey, json.encodeToString(next)).commit()) {
            "Falha ao persistir reservas do estado canônico."
        }
        refreshCanonicalTripStateBatch0395(
            tripIds = changedTripIds,
            bookingSnapshot = next,
            nowMillis = now,
        )
        normalized
    }

    internal fun reconcileBookingDerivedInventory(tripIds: Set<String>): Int = synchronized(CANONICAL_LOCK) {
        if (tripIds.isEmpty()) return@synchronized 0
        val allBookings = bookings()
        val bookingsByTrip = allBookings.groupBy(Booking::tripId)
        val currentTrips = trips()
        val now = System.currentTimeMillis()
        var changed = 0
        val nextTrips = currentTrips.map { trip ->
            if (trip.id !in tripIds) {
                trip
            } else {
                val derived = operationalInventoryCapacity(trip, bookingsByTrip[trip.id].orEmpty())
                if (trip.capacity == derived) {
                    trip
                } else {
                    changed++
                    trip.copy(
                        capacity = derived,
                        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                        updatedAtMillis = now,
                    )
                }
            }
        }
        if (changed > 0) {
            require(prefs.edit().putString(tripsKey, json.encodeToString(nextTrips)).commit()) {
                "Falha ao reconciliar inventário canônico."
            }
        }
        changed
    }

    private fun prepareBookingForPersistence(booking: Booking, existing: Booking?): Booking {
        val withPreservedLocalMetadata = if (
            existing?.localMetadataTouched == true && !booking.localMetadataTouched
        ) {
            booking.copy(
                passengerId = existing.passengerId,
                fareMinorUnits = existing.fareMinorUnits,
                fareCurrencyCode = existing.fareCurrencyCode,
                boardingAddress = existing.boardingAddress,
                dropoffAddress = existing.dropoffAddress,
                localMetadataTouched = true,
            )
        } else {
            booking
        }
        val withPreservedOperationalState = existing?.let { current ->
            val explicitCancellationTombstone =
                current.status == BookingStatus.CANCELLED && current.lastDriverSelection == "CANCELLED"
            withPreservedLocalMetadata.copy(
                status = if (explicitCancellationTombstone) BookingStatus.CANCELLED else withPreservedLocalMetadata.status,
                operationalStatus = when {
                    explicitCancellationTombstone -> PassengerOperationalStatus.CANCELLED
                    withPreservedLocalMetadata.status == BookingStatus.CANCELLED -> PassengerOperationalStatus.CANCELLED
                    withPreservedLocalMetadata.lastDriverSelection.isBlank() &&
                        withPreservedLocalMetadata.operationalStatus == PassengerOperationalStatus.CONFIRMED &&
                        current.operationalStatus != PassengerOperationalStatus.CONFIRMED -> current.operationalStatus
                    else -> withPreservedLocalMetadata.operationalStatus
                },
                paymentStatus = if (
                    withPreservedLocalMetadata.lastDriverSelection.isBlank() &&
                    current.paymentStatus == PassengerPaymentStatus.PAID &&
                    withPreservedLocalMetadata.paymentStatus == PassengerPaymentStatus.UNPAID
                ) PassengerPaymentStatus.PAID else withPreservedLocalMetadata.paymentStatus,
                lastDriverSelection = when {
                    explicitCancellationTombstone -> "CANCELLED"
                    withPreservedLocalMetadata.status == BookingStatus.CANCELLED -> "CANCELLED"
                    else -> withPreservedLocalMetadata.lastDriverSelection.ifBlank { current.lastDriverSelection }
                },
            )
        } ?: withPreservedLocalMetadata
        return if (withPreservedOperationalState.status == BookingStatus.CANCELLED) {
            withPreservedOperationalState.copy(
                operationalStatus = PassengerOperationalStatus.CANCELLED,
                lastDriverSelection = "CANCELLED",
            )
        } else {
            withPreservedOperationalState
        }
    }

    private fun refreshCanonicalTripStateBatch0395(
        tripIds: Set<String>,
        bookingSnapshot: List<Booking>,
        nowMillis: Long,
    ) {
        if (tripIds.isEmpty()) return
        val bookingsByTrip = bookingSnapshot.groupBy(Booking::tripId)
        val currentTrips = trips()
        var changed = false
        val nextTrips = currentTrips.map { trip ->
            if (trip.id !in tripIds) {
                trip
            } else {
                val tripBookings = bookingsByTrip[trip.id].orEmpty()
                val status = SeatAvailabilityEngine.suggestedStatus(trip, tripBookings)
                val capacity = operationalInventoryCapacity(trip, tripBookings)
                changed = true
                trip.copy(
                    status = status,
                    capacity = capacity,
                    canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                    updatedAtMillis = nowMillis,
                )
            }
        }
        if (changed) {
            prefs.edit().putString(tripsKey, json.encodeToString(nextTrips)).apply()
        }
    }

    fun deleteBooking(id: String) {
        val current = bookings()
        val booking = current.firstOrNull { it.id == id }
        val next = current.filterNot { it.id == id }
        prefs.edit().putString(bookingsKey, json.encodeToString(next)).apply()
        booking?.let {
            refreshCanonicalTripStateBatch0395(
                tripIds = setOf(it.tripId),
                bookingSnapshot = next,
                nowMillis = System.currentTimeMillis(),
            )
        }
    }

    fun onlineSettings(): TripOnlineSettings {
        val publicSettings = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(publicSettings.publicCalendarToken)
        return publicSettings.copy(
            driverToken = secretStore.driverToken(),
            publicCalendarToken = stablePublicAgendaToken,
        )
    }

    fun saveOnlineSettings(settings: TripOnlineSettings) {
        secretStore.saveDriverToken(settings.driverToken)
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(settings.publicCalendarToken)
        val withoutAdministrativeSecret = settings.copy(
            driverToken = "",
            publicCalendarToken = stablePublicAgendaToken,
        )
        prefs.edit().putString(onlineKey, json.encodeToString(withoutAdministrativeSecret)).apply()
    }

    fun replacePublicAgendaLinkAfterConfirmedRotation(
        expectedCurrent: String,
        replacement: String,
    ): Boolean = publicAgendaLinkStore.replaceAfterConfirmedRotation(expectedCurrent, replacement)

    fun clearOnlineCredentials() {
        secretStore.clear()
        val current = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        prefs.edit().putString(onlineKey, json.encodeToString(current.copy(driverToken = ""))).apply()
    }

    fun nextPublishedTrip(nowMillis: Long = System.currentTimeMillis()): Trip? = trips()
        .asSequence()
        .filter { it.departureAtMillis >= nowMillis }
        .filter { it.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL, TripStatus.STARTING) }
        .minByOrNull(Trip::departureAtMillis)

    private inline fun <reified T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) null else json.decodeFromString<T>(value)
    }.getOrNull()

    companion object {
        private val CANONICAL_LOCK = Any()
        private const val PREFS = "rota_certa_trips_stage47"
        private const val KEY_TRIPS = "trips"
        private const val KEY_BOOKINGS = "bookings"
        private const val KEY_ONLINE = "online_settings"
        private const val KEY_PUBLIC_EXTERNAL_BINDINGS = "public_external_bindings_v1"
    }
}

@kotlinx.serialization.Serializable
data class PublicExternalTripBinding(
    val remoteTripId: String,
    val publicToken: String,
    val bookingTripId: String,
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
    val blablaPublicHref: String = "",
    val title: String,
    val departureAtMillis: Long,
    val capacity: Int,
    val stops: List<TripStop>,
    /** Stable tenant-scoped internal identity used by Timeline/Agenda reconciliation. */
    val canonicalRevision: Long = 0L,
    val seatAllocationVersionUsed: Long = 0L,
    val externalFingerprint: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun matches(entry: TripTimelineEntry): Boolean {
        val entryProfile = entry.blablaProfileUuid.orEmpty().trim().lowercase()
        val entryTripId = entry.blablaTripId.orEmpty().trim()
        if (profileUuid.isNotBlank() && blablaTripId.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && entryTripId == blablaTripId.trim()
        ) return true

        val leftHref = blablaTripHref.substringBefore("&search_uuid=").trim()
        val rightHref = entry.blablaTripHref.orEmpty().substringBefore("&search_uuid=").trim()
        if (profileUuid.isNotBlank() && leftHref.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && leftHref == rightHref
        ) return true

        return kotlin.math.abs(entry.departureAtMillis - departureAtMillis) <= 45L * 60L * 1000L &&
            normalizeBindingPlace(entry.origin) == normalizeBindingPlace(stops.minByOrNull(TripStop::order)?.name.orEmpty()) &&
            normalizeBindingPlace(entry.destination) == normalizeBindingPlace(stops.maxByOrNull(TripStop::order)?.name.orEmpty())
    }

    fun asTrip(): Trip = Trip(
        id = bookingTripId,
        title = title,
        departureAtMillis = departureAtMillis,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publicToken = publicToken,
        remoteId = remoteTripId,
        blablaProfileUuid = profileUuid.takeIf(String::isNotBlank),
        blablaTripId = blablaTripId.takeIf(String::isNotBlank),
        blablaManageUrl = blablaTripHref.takeIf(String::isNotBlank),
        blablaPublicUrl = blablaPublicHref.takeIf(String::isNotBlank),
        publicBookingEnabled = true,
        canonicalRevision = canonicalRevision,
        seatAllocationVersionUsed = seatAllocationVersionUsed,
    )
}

private fun normalizeBindingPlace(value: String): String = java.text.Normalizer
    .normalize(value.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

@kotlinx.serialization.Serializable
data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
    val driverDisplayName: String = "",
    val driverUsername: String = "",
    val driverWhatsapp: String = "",
    val driverPhotoUrl: String = "",
    val driverPublicAbout: String = "",
    val driverPublicRating: String = "",
    val driverPublicReviewCount: Int = 0,
    val driverPublicBadge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val vehicleAmenities: String = "",
    val driverPreferences: String = "",
    val paymentInstructions: String = "",
    val googleCalendarPublicUrl: String = "",
    val publicProfileMode: PublicDriverProfileMode = PublicDriverProfileMode.MANUAL,
    val selectedPublicProfileAccountId: String = "",
    val publicProfileOverrideFields: Set<String> = emptySet(),
) {
    val configured: Boolean
        get() = apiBaseUrl.startsWith("https://") && driverToken.isNotBlank()

    val publicAgendaUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            val username = driverUsername.takeIf(DriverIdentityRules::isValidPublicUsername) ?: return@let null
            publicCalendarToken.takeIf { it.length >= 16 }?.let { "$base/$username" }
        }

    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token ->
                if (DriverIdentityRules.isValidUsername(driverUsername)) "$base/calendar/$driverUsername/$token.ics" else "$base/calendar/$token.ics"
            }
        }

    val googleCalendarMirrorUrl: String?
        get() = googleCalendarPublicUrl.trim().takeIf { it.startsWith("https://") }
}

internal class PublicAgendaLinkStore(
    context: Context,
    tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val valueKey = tenantScope.key(KEY_VALUE)
    private val generationKey = tenantScope.key(KEY_GENERATION)

    @Synchronized
    fun currentOrMigrate(legacyValue: String = ""): String {
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current.isNotBlank()) return current
        val candidate = normalize(legacyValue)
        if (candidate.length < MIN_LENGTH) return ""
        prefs.edit().putString(valueKey, candidate).apply()
        return candidate
    }

    @Synchronized
    fun replaceAfterConfirmedRotation(expectedCurrentRaw: String, replacementRaw: String): Boolean {
        val expected = normalize(expectedCurrentRaw)
        val replacement = normalize(replacementRaw)
        if (expected.length < MIN_LENGTH || replacement.length < MIN_LENGTH || replacement == expected) return false
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current != expected) return false
        return prefs.edit()
            .putString(valueKey, replacement)
            .putLong(generationKey, generation() + 1L)
            .commit()
    }

    fun generation(): Long = prefs.getLong(generationKey, 1L).coerceAtLeast(1L)

    private fun normalize(value: String): String =
        value.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(120)

    companion object {
        private const val PREFS = "rota_certa_public_agenda_link_v1"
        private const val KEY_VALUE = "public_agenda_identifier"
        private const val KEY_GENERATION = "generation"
        private const val MIN_LENGTH = 16
    }
}

private class TripSecretStore(
    context: Context,
    private val tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ciphertextKey = tenantScope.key(KEY_CIPHERTEXT)
    private val ivKey = tenantScope.key(KEY_IV)
    private val keyAlias = tenantScope.keyAlias(KEY_ALIAS_BASE)

    fun saveDriverToken(token: String) {
        val value = token.trim()
        if (value.isBlank()) {
            prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ciphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun driverToken(): String {
        val ciphertext = prefs.getString(ciphertextKey, null)?.let(::decode) ?: return ""
        val iv = prefs.getString(ivKey, null)?.let(::decode) ?: return ""
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return@runCatching ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun clear() {
        prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun decode(value: String): ByteArray? = runCatching {
        Base64.decode(value, Base64.NO_WRAP)
    }.getOrNull()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_BASE = "rota_certa_stage47_driver_token_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "rota_certa_trip_secrets_stage47"
        private const val KEY_CIPHERTEXT = "driver_token_ciphertext"
        private const val KEY_IV = "driver_token_iv"
    }
}


internal fun mergeBookingBatch0380(
    existing: List<Booking>,
    updated: List<Booking>,
): List<Booking> {
    if (updated.isEmpty()) return existing
    val distinctUpdated = LinkedHashMap<String, Booking>().apply {
        updated.forEach { put(it.id, it) }
    }.values.toList()
    val updatedIds = distinctUpdated.map(Booking::id).toSet()
    return distinctUpdated.asReversed() + existing.filterNot { it.id in updatedIds }
}
