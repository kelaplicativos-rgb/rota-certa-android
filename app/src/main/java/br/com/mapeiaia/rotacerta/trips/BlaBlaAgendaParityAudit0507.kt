package br.com.mapeiaia.rotacerta.trips

data class BlaBlaAgendaParityItem0507(
    val canonicalTripId: String,
    val reconciliationState: String,
    val visibilityState: String,
    val reasonCode: String,
)

data class BlaBlaAgendaParitySummary0507(
    val confirmedExternalPublications: Int,
    val canonicalExternalTrips: Int,
    val expectedVisibleConfirmedTrips: Int,
    val actualVisibleAgendaTrips: Int,
    val missingFromAgenda: Int,
    val unexpectedAgendaTrips: Int,
    val duplicates: Int,
    val identityPending: Int,
    val identityConflicts: Int,
    val coverageComplete: Boolean,
)

data class BlaBlaAgendaParityResult0507(
    val items: List<BlaBlaAgendaParityItem0507>,
    val summary: BlaBlaAgendaParitySummary0507,
)

object BlaBlaAgendaParityAudit0507 {
    fun audit(
        collection: BlaBlaAuditableCollectionSnapshot,
        visibleCanonicalTripIds: List<String>,
        expectedAbsenceReasons: Map<String, String> = emptyMap(),
    ): BlaBlaAgendaParityResult0507 {
        val confirmedPublications = collection.publicCards.count {
            it.ownership.ownership == "CONFIRMED" && it.profileUuid != null
        }
        val canonicalExternal = collection.reconciledTrips.filter {
            it.recordOrigin == TripRecordOrigin.EXTERNAL_BACKING.name
        }
        val expectedVisible = canonicalExternal.filter { trip ->
            trip.reconciliation.state == "CONFIRMED_STRONG_IDENTITY" &&
                trip.status in setOf(TripStatus.PUBLISHED.name, TripStatus.FULL.name) &&
                trip.stops.size >= 2 &&
                expectedAbsenceReasons[trip.internalTripId].isNullOrBlank()
        }
        val visibleCounts = visibleCanonicalTripIds.filter(String::isNotBlank).groupingBy { it }.eachCount()
        val visible = visibleCounts.keys
        val expectedIds = expectedVisible.map(BlaBlaAuditReconciledTrip::internalTripId).toSet()
        val missing = expectedIds - visible
        val unexpected = visible - expectedIds

        val items = canonicalExternal.map { trip ->
            val expectedAbsence = expectedAbsenceReasons[trip.internalTripId]
            val state = when {
                !expectedAbsence.isNullOrBlank() -> "EXPECTED_ABSENCE"
                trip.internalTripId in visible -> "VISIBLE"
                trip.internalTripId in expectedIds -> "MISSING"
                else -> "NOT_EXPECTED_VISIBLE"
            }
            val reason = when {
                !expectedAbsence.isNullOrBlank() -> expectedAbsence
                state == "VISIBLE" -> "PARITY_OK"
                state == "MISSING" -> "PUBLIC_AGENDA_PROJECTION_NOT_VISIBLE"
                trip.reconciliation.state == "PENDING_IDENTITY_ENRICHMENT" -> "PUBLIC_AGENDA_IDENTITY_PENDING"
                trip.reconciliation.state == "IDENTITY_CONFLICT" -> "PUBLIC_AGENDA_IDENTITY_CONFLICT"
                trip.status == TripStatus.CANCELLED.name -> "PUBLIC_AGENDA_CANCELLED"
                trip.status == TripStatus.COMPLETED.name -> "PUBLIC_AGENDA_DEPARTED"
                else -> "PUBLIC_AGENDA_NOT_ELIGIBLE"
            }
            BlaBlaAgendaParityItem0507(
                canonicalTripId = trip.internalTripId,
                reconciliationState = trip.reconciliation.state,
                visibilityState = state,
                reasonCode = reason,
            )
        }.sortedBy(BlaBlaAgendaParityItem0507::canonicalTripId)

        return BlaBlaAgendaParityResult0507(
            items = items,
            summary = BlaBlaAgendaParitySummary0507(
                confirmedExternalPublications = confirmedPublications,
                canonicalExternalTrips = canonicalExternal.size,
                expectedVisibleConfirmedTrips = expectedIds.size,
                actualVisibleAgendaTrips = visible.size,
                missingFromAgenda = missing.size,
                unexpectedAgendaTrips = unexpected.size,
                duplicates = visibleCounts.values.count { it > 1 },
                identityPending = canonicalExternal.count { it.reconciliation.state == "PENDING_IDENTITY_ENRICHMENT" },
                identityConflicts = canonicalExternal.count { it.reconciliation.state == "IDENTITY_CONFLICT" },
                coverageComplete = collection.summary.coverageComplete,
            ),
        )
    }
}
