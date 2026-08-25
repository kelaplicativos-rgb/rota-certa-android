package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer

internal enum class BlaBlaDirectRosterState {
    UNKNOWN,
    COMPLETE_EMPTY,
    COMPLETE_WITH_PASSENGERS,
}

/** Passenger roster, names and monotonic merge decisions. */
internal object BlaBlaCollectorPassengerModule {
    fun normalizePhone(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val hasPlus = value.startsWith("+")
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 8..15) return null
        return if (hasPlus) "+$digits" else digits
    }

    fun rosterState(
        passengerCount: Int,
        rosterComplete: Boolean,
        explicitEmpty: Boolean,
    ): BlaBlaDirectRosterState = when {
        explicitEmpty && !rosterComplete -> BlaBlaDirectRosterState.UNKNOWN
        rosterComplete && passengerCount == 0 -> BlaBlaDirectRosterState.COMPLETE_EMPTY
        rosterComplete && passengerCount > 0 -> BlaBlaDirectRosterState.COMPLETE_WITH_PASSENGERS
        else -> BlaBlaDirectRosterState.UNKNOWN
    }

    /**
     * Accepts a rendered roster without depending on one brittle data-testid.
     * A positive roster needs two identical terminal observations; an unmarked
     * empty roster needs three. A visible expansion control keeps it open.
     */
    fun rosterCompleteAfterStableProbe(
        passengerCount: Int,
        structurallyComplete: Boolean,
        explicitEmpty: Boolean,
        hasMore: Boolean,
        terminalEvidence: Boolean,
        stablePasses: Int,
    ): Boolean = when {
        passengerCount < 0 || hasMore || !terminalEvidence -> false
        explicitEmpty && passengerCount == 0 -> stablePasses >= 3
        structurallyComplete && passengerCount > 0 -> true
        passengerCount > 0 -> stablePasses >= 2
        else -> stablePasses >= 3
    }

    fun shouldAwaitNetworkBeforeEmptyRoster(
        networkResolved: Boolean,
        passengerCount: Int,
        readAttempts: Int,
        maxReadAttempts: Int,
    ): Boolean = !networkResolved && passengerCount == 0 && readAttempts < maxReadAttempts

    /**
     * Collapses two DOM observations of the same visible booking without
     * inventing extra occupied seats. Distinct strong passenger URLs or phone
     * numbers remain separate even when the visible name is the same.
     */
    fun coalesceDuplicateEvidence(
        passengers: List<BlaBlaCollectorPassenger>,
    ): List<BlaBlaCollectorPassenger> {
        if (passengers.size < 2) return passengers
        val merged = mutableListOf<BlaBlaCollectorPassenger>()
        passengers.forEach { incoming ->
            val normalizedIncoming = incoming.copy(seats = incoming.seats.coerceAtLeast(1))
            val index = merged.indexOfFirst { existing -> duplicateEvidenceMatches(existing, normalizedIncoming) }
            if (index < 0) {
                merged += normalizedIncoming
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    name = normalizedIncoming.name.ifBlank { existing.name },
                    seats = maxOf(existing.seats, normalizedIncoming.seats),
                    boarding = normalizedIncoming.boarding?.takeIf(String::isNotBlank) ?: existing.boarding,
                    dropoff = normalizedIncoming.dropoff?.takeIf(String::isNotBlank) ?: existing.dropoff,
                    phone = normalizedIncoming.phone?.takeIf(String::isNotBlank) ?: existing.phone,
                    booking_href = normalizedIncoming.booking_href?.takeIf(String::isNotBlank) ?: existing.booking_href,
                )
            }
        }
        return merged
    }

    /** An incomplete read may enrich, but can never erase confirmed rows. */
    fun mergeMonotonic(
        previous: BlaBlaCollectorTrip?,
        current: BlaBlaCollectorTrip,
    ): BlaBlaCollectorTrip {
        if (previous == null || !current.passenger_roster_complete) {
            return BlaBlaPassengerRosterReconciler.reconcile(previous, current)
        }
        if (current.passengers.isEmpty()) {
            return current.copy(booked_seats = 0)
        }
        val enriched = current.passengers.map { incoming ->
            val confirmed = previous.passengers.firstOrNull { prior ->
                BlaBlaPassengerRosterReconciler.matches(prior, incoming)
            }
            if (confirmed == null) {
                incoming
            } else {
                incoming.copy(
                    name = incoming.name.ifBlank { confirmed.name },
                    boarding = incoming.boarding?.takeIf(String::isNotBlank) ?: confirmed.boarding,
                    dropoff = incoming.dropoff?.takeIf(String::isNotBlank) ?: confirmed.dropoff,
                    phone = incoming.phone?.takeIf(String::isNotBlank) ?: confirmed.phone,
                    booking_href = incoming.booking_href?.takeIf(String::isNotBlank) ?: confirmed.booking_href,
                )
            }
        }
        val occupied = enriched.sumOf { it.seats.coerceAtLeast(1) }
        return current.copy(
            passengers = enriched,
            booked_seats = maxOf(current.booked_seats, occupied),
        )
    }

    private fun duplicateEvidenceMatches(
        left: BlaBlaCollectorPassenger,
        right: BlaBlaCollectorPassenger,
    ): Boolean {
        val leftHref = left.booking_href?.trim().orEmpty()
        val rightHref = right.booking_href?.trim().orEmpty()
        val leftUuid = passengerUuid(leftHref)
        val rightUuid = passengerUuid(rightHref)
        if (leftUuid != null && rightUuid != null && leftUuid == rightUuid) return true
        if (leftHref.isNotBlank() && rightHref.isNotBlank()) {
            return BlaBlaCollectorUrlModule.samePassengerPage(leftHref, rightHref)
        }

        val leftPhone = normalizePhone(left.phone)
        val rightPhone = normalizePhone(right.phone)
        if (leftPhone != null && rightPhone != null) return leftPhone == rightPhone

        val leftName = normalizeEvidence(left.name)
        val rightName = normalizeEvidence(right.name)
        if (leftName.isBlank() || leftName != rightName) return false
        return compatiblePlace(left.boarding, right.boarding) &&
            compatiblePlace(left.dropoff, right.dropoff)
    }

    private fun passengerUuid(raw: String): String? {
        if (raw.isBlank()) return null
        val identity = BlaBlaCollectorUrlModule.passengerIdentityKey(raw).lowercase()
        return identity.takeIf(PASSENGER_UUID::matches)
    }

    private fun compatiblePlace(left: String?, right: String?): Boolean {
        val first = normalizeEvidence(left.orEmpty())
        val second = normalizeEvidence(right.orEmpty())
        return first.isBlank() || second.isBlank() || first == second
    }

    private fun normalizeEvidence(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private val PASSENGER_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}

internal fun blaBlaDirectRosterState(
    passengerCount: Int,
    rosterComplete: Boolean,
    explicitEmpty: Boolean,
): BlaBlaDirectRosterState = BlaBlaCollectorPassengerModule.rosterState(
    passengerCount,
    rosterComplete,
    explicitEmpty,
)

internal fun blaBlaDirectRosterCompleteAfterStableProbe(
    passengerCount: Int,
    structurallyComplete: Boolean,
    explicitEmpty: Boolean,
    hasMore: Boolean,
    terminalEvidence: Boolean,
    stablePasses: Int,
): Boolean = BlaBlaCollectorPassengerModule.rosterCompleteAfterStableProbe(
    passengerCount,
    structurallyComplete,
    explicitEmpty,
    hasMore,
    terminalEvidence,
    stablePasses,
)
