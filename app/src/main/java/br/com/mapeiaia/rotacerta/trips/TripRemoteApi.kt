package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PublishedTripResponse(
    val tripId: String,
    val publicToken: String,
    val publicUrl: String,
    val entityRevision: Long = 0L,
    val stale: Boolean = false,
)

@Serializable
data class DriverRegistrationRequest(val displayName: String, val username: String)

@Serializable
data class DriverRegistrationResponse(
    val displayName: String,
    val username: String,
    val driverToken: String,
    val publicAgendaToken: String,
    val publicAgendaUrl: String,
    val calendarUrl: String,
)

@Serializable
data class DriverUsernameChangeRequest(
    val username: String,
    val currentPublicAgendaToken: String,
    val requestId: String,
)

@Serializable
data class DriverUsernameChangeResponse(
    val username: String,
    val publicAgendaToken: String,
    val publicAgendaUrl: String,
    val calendarUrl: String,
    val changed: Boolean = false,
)

@Serializable
data class DriverPushTokenRequest(
    val token: String,
    val appVersion: String = "",
    val deviceLabel: String = "",
)

@Serializable
data class DriverPushTokenResponse(
    val registered: Boolean = false,
)

@Serializable
data class DriverPublicReviewPayload(
    val author: String = "",
    val rating: String = "",
    val dateLabel: String = "",
    val text: String = "",
)

@Serializable
data class DriverAgendaEnsureRequest(
    val publicAgendaToken: String = "",
    val publicProfileMode: String = PublicDriverProfileMode.MANUAL.name,
    val selectedPublicProfileUuid: String = "",
    val publicProfileLastSyncedAtMillis: Long? = null,
    val publicProfileOverrideFields: List<String> = emptyList(),
    val driverDisplayName: String = "",
    val driverWhatsapp: String = "",
    val driverPhotoUrl: String = "",
    val driverPublicAbout: String = "",
    val driverPublicRating: String = "",
    val driverPublicReviewCount: Int = 0,
    val driverPublicReviews: List<DriverPublicReviewPayload> = emptyList(),
    val driverPublicBadge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val vehicleAmenities: String = "",
    val driverPreferences: String = "",
    val paymentInstructions: String = "",
)

@Serializable
data class DriverAgendaRegenerateRequest(
    val confirmation: String = "REGENERATE_PUBLIC_AGENDA_LINK",
    val currentPublicAgendaToken: String,
    val rotationId: String,
)

@Serializable
data class DriverAgendaEnsureResponse(
    val displayName: String,
    val username: String,
    val publicAgendaToken: String,
    val publicAgendaUrl: String,
    val calendarUrl: String,
    val repaired: Boolean = false,
)

@Serializable
data class DriverTesterLinkResponse(
    val active: Boolean = false,
    val generation: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val revokedAtMillis: Long = 0L,
    val testUrl: String = "",
)

@Serializable
data class RemoteBookingResponse(
    val bookingId: String,
    val cancellationToken: String? = null,
    val availableSeats: Int? = null,
    val status: String = BookingStatus.REQUESTED.name,
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.PENDING,
)

@Serializable
data class RemoteBooking(
    val id: String,
    val tripId: String = "",
    val passengerId: String = "",
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String = "CONFIRMED",
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    val paymentStatus: PassengerPaymentStatus = PassengerPaymentStatus.UNPAID,
    val lastDriverSelection: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
    val holdExpiresAtMillis: Long? = null,
)

@Serializable
data class DriverBookingsResponse(
    val bookings: List<RemoteBooking> = emptyList(),
    val entityRevision: Long = 0L,
)

@Serializable
data class DriverPassengerAccess(
    val id: String = "",
    val passengerId: String = "",
    val passengerContact: String = "",
    val displayName: String = "",
    val status: String = "PENDING",
    val accountActivated: Boolean = false,
    val agendaAdmin: Boolean = false,
    val referredByContact: String = "",
    val referralRewardGrantedAtMillis: Long = 0L,
    val creditBalanceCents: Long = 0L,
    val creditEarnedCents: Long = 0L,
    val creditSpentCents: Long = 0L,
)

@Serializable
data class DriverPassengersResponse(
    val passengers: List<DriverPassengerAccess> = emptyList(),
    val referralCreditCents: Long = 0L,
)

@Serializable
data class DriverPassengerInviteRequest(
    val displayName: String,
    val passengerContact: String,
    val passengerId: String = "",
    val referredByContact: String = "",
)

@Serializable
data class DriverPassengerDirectoryItem(
    val passengerId: String,
    val displayName: String,
    val passengerContact: String,
    val blocked: Boolean = false,
)

@Serializable
data class DriverPassengerDirectoryRequest(
    val passengers: List<DriverPassengerDirectoryItem>,
)

@Serializable
data class DriverPassengerDirectoryResponse(
    val synced: Int = 0,
)

@Serializable
data class DriverPassengerInviteResponse(
    val passenger: DriverPassengerAccess = DriverPassengerAccess(),
    val temporaryPassword: String = "",
)

@Serializable
data class DriverPassengerBlockRequest(
    val passengerContact: String,
    val passengerId: String = "",
    val blocked: Boolean = false,
    val status: String = "",
)

@Serializable
data class DriverPassengerWhatsappUpdateRequest(
    val passengerId: String,
    val currentPassengerContact: String = "",
    val newPassengerContact: String,
    val displayName: String = "",
)

@Serializable
data class DriverPassengerAgendaAdminRequest0418(
    val passengerId: String = "",
    val passengerContact: String = "",
    val agendaAdmin: Boolean,
)

@Serializable
data class DriverPassengerBlockResponse(
    val passenger: DriverPassengerAccess = DriverPassengerAccess(),
    val cancelledBookings: Int = 0,
    val affectedTrips: Int = 0,
)

@Serializable
data class DriverPassengerResetPasswordRequest(
    val passengerContact: String,
    val passengerId: String = "",
)

@Serializable
data class DriverPassengerResetPasswordResponse(
    val temporaryPassword: String = "",
)

@Serializable
data class DriverReferralSettingsRequest(
    val referralCreditCents: Long,
)

@Serializable
data class DriverReferralSettingsResponse(
    val referralCreditCents: Long = 0L,
)

@Serializable
data class RemotePublicDebugEvent(
    val id: String,
    val event: String,
    val source: String = "server",
    val sessionId: String = "",
    val targetType: String = "",
    val targetRefHash: String = "",
    val screen: String = "",
    val reason: String = "",
    val statusCode: Int = 0,
    val seats: Int = 0,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val replayed: Boolean = false,
    val createdAtMillis: Long = 0L,
)

@Serializable
data class DriverPublicDebugEventsResponse(
    val events: List<RemotePublicDebugEvent> = emptyList(),
)

@Serializable
data class DriverNotificationItem(
    val id: String = "",
    val notificationId: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val tripId: String = "",
    val bookingId: String = "",
    val passengerId: String = "",
    val boardingStopId: String = "",
    val dropoffStopId: String = "",
    val seats: Int = 0,
    val driverUsername: String = "",
    val createdAtMillis: Long = 0L,
    val read: Boolean = false,
    val readAtMillis: Long? = null,
    val eventId: String = "",
)

@Serializable
data class DriverNotificationsResponse(
    val notifications: List<DriverNotificationItem> = emptyList(),
    val unreadCount: Int = 0,
)

@Serializable
data class NotificationReadResponse(
    val changed: Int = 0,
)

@Serializable
data class PublicBookingRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    /** Stable per user intent so retries/double taps converge to one backend Booking. */
    val idempotencyKey: String = "",
)

@Serializable
data class DriverBookingUpsertRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val passengerId: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String = BookingStatus.CONFIRMED.name,
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    val paymentStatus: PassengerPaymentStatus = PassengerPaymentStatus.UNPAID,
    val lastDriverSelection: String = "",
    val holdExpiresAtMillis: Long? = null,
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
)

@Serializable
data class DriverBookingUpsertResponse(
    val booking: RemoteBooking,
    val segmentLoads: List<Int> = emptyList(),
    val availableSeatsMinimum: Int = 0,
    val availableSeatsMaximum: Int = 0,
    val changed: Boolean = false,
    val passengerNotified: Boolean = false,
    val entityRevision: Long = 0L,
)

@Serializable
data class DriverCapacitySnapshotClaim(
    val id: String,
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String = BookingStatus.CONFIRMED.name,
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
    val holdExpiresAtMillis: Long? = null,
)

@Serializable
data class DriverProtectedBookingSnapshot(
    val id: String,
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String,
    val operationalStatus: PassengerOperationalStatus,
    val paymentStatus: PassengerPaymentStatus,
    val lastDriverSelection: String = "",
    val holdExpiresAtMillis: Long? = null,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
)

@Serializable
data class DriverPublicAttestationRequest0417(
    val state: String,
    val canonicalRevision: Long,
    val publicationRevision: Long,
    val canonicalStateHash: String,
    val expectedHash: String,
    val readbackHash: String,
    val mismatchFields: List<String> = emptyList(),
    val reason: String = "",
    val correlationId: String = "",
)

@Serializable
data class DriverPublicAttestationResponse0417(
    val state: String = "",
    val verified: Boolean = false,
    val publicationRevision: Long = 0L,
    val canonicalRevision: Long = 0L,
)

@Serializable
data class DriverPrivateMirrorWriteRequest0434(
    val canonicalTripId: String,
    val canonicalRevision: Long,
    val privateStateHash: String,
    val canonicalJson: String,
    val correlationId: String = "",
    val syncOperationId: String = "",
    val idempotencyKey: String = "",
)

@Serializable
data class DriverPrivateMirrorWriteResponse0434(
    val canonicalTripId: String = "",
    val canonicalRevision: Long = 0L,
    val mirrorRevision: Long = 0L,
    val privateStateHash: String = "",
    val changed: Boolean = false,
    val replayed: Boolean = false,
)

@Serializable
data class DriverPrivateMirrorReadRequest0434(
    val canonicalTripId: String,
)

@Serializable
data class DriverPrivateMirrorReadback0434(
    val canonicalTripId: String = "",
    val canonicalRevision: Long = 0L,
    val mirrorRevision: Long = 0L,
    val privateStateHash: String = "",
    val canonicalJson: String = "",
    val persistedAtMillis: Long = 0L,
)

@Serializable
data class DriverAdminSyncPolicy0417(
    val automatic: Boolean = true,
    val intervalMinutes: Long = 15L,
)

@Serializable
data class DriverAdminPublicUrlAssignment0465(
    val remoteTripId: String = "",
    val canonicalTripId: String = "",
    val blablaProfileUuid: String = "",
    val blablaTripId: String = "",
    val blablaPublicUrl: String = "",
    val requestRevision: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class DriverAdminIdentityAssignment0472(
    val remoteTripId: String = "",
    val canonicalTripId: String = "",
    val expectedProfileUuid: String = "",
    val candidateTripId: String = "",
    val blablaManageUrl: String = "",
    val requestRevision: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class DriverAdminIdentityConfirmRequest0472(
    val remoteTripId: String,
    val blablaProfileUuid: String,
    val blablaTripId: String,
    val blablaManageUrl: String,
    val requestRevision: Long,
    val localApplied: Boolean = false,
)

@Serializable
data class DriverAdminIdentityConfirmResponse0472(
    val accepted: Boolean = false,
    val changed: Boolean = false,
    val localApplied: Boolean = false,
    val remoteTripId: String = "",
    val canonicalTripId: String = "",
    val canonicalRevision: Long = 0L,
    val blablaProfileUuid: String = "",
    val blablaTripId: String = "",
    val blablaManageUrl: String = "",
    val requestRevision: Long = 0L,
)

@Serializable
data class DriverAdminSyncPolicyResponse0417(
    val syncPolicy: DriverAdminSyncPolicy0417 = DriverAdminSyncPolicy0417(),
    val manualPublicUrlAssignments0465: List<DriverAdminPublicUrlAssignment0465> = emptyList(),
    val manualIdentityAssignments0472: List<DriverAdminIdentityAssignment0472> = emptyList(),
)

@Serializable
data class DriverAdminSyncHealthRequest0417(
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long = 0L,
    val result: String = "",
    val trigger: String = "",
    val correlationId: String = "",
    val failures: Int = 0,
    val changed: Int = 0,
    val skipped: Int = 0,
    val pending: Int = 0,
    val divergent: Int = 0,
    val readbackFailures: Int = 0,
    val appVersion: String = "",
)

@Serializable
data class DriverAdminSyncHealthResponse0417(val recorded: Boolean = false)

@Serializable
data class DriverCapacitySnapshotRequest(
    val trip: Trip,
    val claims: List<DriverCapacitySnapshotClaim> = emptyList(),
    val protectedBookings: List<DriverProtectedBookingSnapshot> = emptyList(),
    val claimNamespace: String,
    val snapshotRevision: String,
    val sourceComplete: Boolean = true,
    val preserveManagedClaims0436: Boolean = false,
    val entityRevision: Long = 0L,
    val canonicalTripId: String = "",
    val outboxEventId: String = "",
    val mutationId0421: String = "",
    val idempotencyKey0421: String = "",
    val expectedPublicProjectionHash0425: String = "",
    val expectedPublicProjectionJson0434: String = "",
    val serverCanonicalAuthority0468: Boolean = false,
)

@Serializable
data class DriverAgendaSeatAllocationReconcileResponse(
    val processed: Int = 0,
    val updated: Int = 0,
    val failClosed: Int = 0,
)

@Serializable
data class DriverAgendaSeatAllocationReconcileRequest(
    val rotaCertaSeatAllocation: Int,
    val configVersion: Long = 0L,
)

@Serializable
data class DriverTripSyncState0402(
    val remoteTripId: String,
    val status: String = "",
    val departureAtMillis: Long = 0L,
    val arrivalAtMillis: Long = 0L,
    val stops: List<TripStop> = emptyList(),
    val capacityReliable: Boolean = false,
    val capacitySnapshotRevision: String = "",
    /** Transport/entity sequence. Not the logical canonical state revision. */
    val publicationRevision: Long = 0L,
    /** Logical canonical snapshot revision persisted in the backend canonical projection. */
    val canonicalRevision: Long = 0L,
    val canonicalTripId: String = "",
    val canonicalStateHash: String = "",
    val publicProjectionHash: String = "",
    val bookingsCount: Int = 0,
    val tripKey: String = "",
    val blablaProfileUuid: String = "",
    val blablaTripId: String = "",
    val blablaPublicUrl: String = "",
    val driverDisplayName: String = "",
    val title: String = "",
    val publicUrl: String = "",
    val publicBookingEnabled: Boolean = false,
    val itineraryAuthoritative: Boolean = true,
    val capacity: Int = 0,
    val publishedSeats: Int? = null,
    val rotaCertaSeatAllocation: Int? = null,
    val operationalAvailableSeats: Int? = null,
    val availableSeatsMinimum: Int? = null,
    val availableSeatsMaximum: Int? = null,
    val minimumOccupiedSeats: Int = 0,
    val maximumOccupiedSeats: Int = 0,
    val operationalBlockedSeats: Int = 0,
    val operationalOverbookingSeats: Int = 0,
    val segmentLoads: List<Int> = emptyList(),
    val segmentPassengerLoads: List<Int> = emptyList(),
    val segmentBlockedLoads: List<Int> = emptyList(),
    val segmentAvailableSeats: List<Int> = emptyList(),
    val sourceSeatCounts: Map<String, Int> = emptyMap(),
    val canonicalIssues: List<String> = emptyList(),
    /** Exact canonical bookings for the Timeline projection. No collector snapshot is embedded. */
    val bookings: List<RemoteBooking> = emptyList(),
    val occupancyRevision: Long? = null,
    val updatedAtMillis: Long = 0L,
    val publicAgendaOnline0471: Boolean = true,
    val publicAgendaVisibilityRevision0471: Long = 0L,
)

@Serializable
data class DriverTripSyncStateResponse0402(
    val trips: List<DriverTripSyncState0402> = emptyList(),
    val source: String = "CANONICAL_BACKEND",
    val snapshotAtMillis: Long = 0L,
)

@Serializable
data class DriverTripPublicVisibilityRequest0491(
    val online: Boolean,
    val expectedVisibilityRevision0471: Long? = null,
)

@Serializable
data class DriverTripPublicVisibilityResponse0491(
    val accepted: Boolean = false,
    val changed: Boolean = false,
    val remoteTripId: String = "",
    val online: Boolean = true,
    val visibilityRevision0471: Long = 0L,
)

@Serializable
data class DriverCapacitySnapshotResponse(
    val tripId: String,
    val publicToken: String,
    val availableSeatsMinimum: Int = 0,
    val availableSeatsMaximum: Int = 0,
    val occupancyRevision: Long = 0L,
    val changed: Boolean = false,
    val entityRevision: Long = 0L,
    val stale: Boolean = false,
    val logicalReplay: Boolean = false,
    val stopShapeMigrationCount0439: Int = 0,
    val canonicalTripId: String = "",
    val canonicalRevision: Long = 0L,
    val canonicalStateHash: String = "",
    val publicProjectionHash: String = "",
    val createdCanonical: Boolean = false,
    val serverCanonicalAuthority0468: Boolean = false,
)

internal data class RemotePublicationEvidenceContext0421(
    val evidenceId: String,
    val traceId: String,
    val canonicalTripId: String,
    val logicalRevision: Long,
    val transportRevision: Long,
    val mutationId: String,
    val idempotencyKey: String,
)
internal class TripRemoteApiException(
    val httpMethod: String,
    val endpoint: String,
    val httpStatus: Int,
    val backendErrorCode: String,
    val sanitizedResponse: String,
    val requestId: String,
    val correlationId: String,
    val networkCallId: String = "",
    val transportPhase: String = "",
    val requestBytes: Int = 0,
    val responseBytes: Int = 0,
    val requestSha256: String = "",
    val responseSha256: String = "",
    val sanitizedRequest: String = "",
    val responseContentType: String = "",
    val elapsedMs: Long = 0L,
    cause: Throwable? = null,
) : IllegalStateException(
    buildString {
        if (httpStatus > 0) {
            append("Servidor respondeu HTTP ").append(httpStatus)
        } else {
            append("Falha de transporte remoto")
        }
        if (transportPhase.isNotBlank()) append(" phase=").append(transportPhase)
        if (backendErrorCode.isNotBlank()) append(" code=").append(backendErrorCode)
        if (sanitizedResponse.isNotBlank()) append(": ").append(sanitizedResponse.take(240))
        if (sanitizedResponse.isBlank() && cause?.message?.isNotBlank() == true) {
            append(": ").append(UnifiedDebugEventStore.sanitizeForExport(cause.message.orEmpty()).take(240))
        }
    },
    cause,
)

@Serializable
data class DriverOperationalStatusRequest(
    val selection: String,
)

@Serializable
data class DriverBookingDecisionRequest(
    val action: String,
    val reason: String = "",
)

class TripRemoteApi(
    private val settings: TripOnlineSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun registerDriver(displayName: String, username: String): DriverRegistrationResponse = request(
        method = "POST",
        path = "/v1/drivers/register",
        body = json.encodeToString(DriverRegistrationRequest(displayName.trim(), username.trim())),
        requireDriverToken = false,
    )

    suspend fun changeDriverUsername(
        username: String,
        currentPublicAgendaToken: String,
        requestId: String,
    ): DriverUsernameChangeResponse = request(
        method = "POST",
        path = "/v1/driver/username",
        body = json.encodeToString(
            DriverUsernameChangeRequest(
                username = username.trim(),
                currentPublicAgendaToken = currentPublicAgendaToken.trim(),
                requestId = requestId.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun registerPushToken(
        token: String,
        appVersion: String,
        deviceLabel: String,
    ): DriverPushTokenResponse = request(
        method = "POST",
        path = "/v1/driver/push-tokens",
        body = json.encodeToString(DriverPushTokenRequest(token, appVersion, deviceLabel)),
        requireDriverToken = true,
    )

    suspend fun ensurePublicAgenda(publicAgendaToken: String): DriverAgendaEnsureResponse =
        ensurePublicAgenda(publicAgendaToken, ResolvedPublicDriverProfile.manual(settings))

    suspend fun ensurePublicAgenda(
        publicAgendaToken: String,
        publicProfile: ResolvedPublicDriverProfile,
    ): DriverAgendaEnsureResponse = request(
        method = "POST",
        path = "/v1/driver/agenda/ensure",
        body = json.encodeToString(
            DriverAgendaEnsureRequest(
                publicAgendaToken = publicAgendaToken.trim(),
                publicProfileMode = publicProfile.sourceMode.name,
                selectedPublicProfileUuid = publicProfile.selectedProfileUuid.trim(),
                publicProfileLastSyncedAtMillis = publicProfile.automaticProfileLastSyncedAtMillis,
                publicProfileOverrideFields = publicProfile.overrideFields.sorted(),
                driverDisplayName = publicProfile.displayName.trim(),
                driverWhatsapp = publicProfile.whatsapp.trim(),
                driverPhotoUrl = publicProfile.photoUrl.trim(),
                driverPublicAbout = publicProfile.about.trim(),
                driverPublicRating = publicProfile.rating.trim(),
                driverPublicReviewCount = publicProfile.reviewCount?.coerceAtLeast(0) ?: 0,
                driverPublicReviews = publicProfile.reviews.map { review ->
                    DriverPublicReviewPayload(
                        author = review.author.trim(),
                        rating = review.rating.trim(),
                        dateLabel = review.dateLabel.trim(),
                        text = review.text.trim(),
                    )
                },
                driverPublicBadge = publicProfile.badge.trim(),
                vehicleMakeModel = publicProfile.vehicleMakeModel.trim(),
                vehicleColor = publicProfile.vehicleColor.trim(),
                vehicleAmenities = publicProfile.amenities.trim(),
                driverPreferences = publicProfile.preferences.trim(),
                paymentInstructions = publicProfile.paymentInstructions.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun regeneratePublicAgenda(
        currentPublicAgendaToken: String,
        rotationId: String,
    ): DriverAgendaEnsureResponse = request(
        method = "POST",
        path = "/v1/driver/agenda/regenerate",
        body = json.encodeToString(
            DriverAgendaRegenerateRequest(
                currentPublicAgendaToken = currentPublicAgendaToken.trim(),
                rotationId = rotationId.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun testerLinkStatus(): DriverTesterLinkResponse = request(
        method = "GET",
        path = "/v1/driver/test-link",
        requireDriverToken = true,
    )

    suspend fun generateTesterLink(): DriverTesterLinkResponse = request(
        method = "POST",
        path = "/v1/driver/test-link/generate",
        body = "{}",
        requireDriverToken = true,
    )

    suspend fun revokeTesterLink(): DriverTesterLinkResponse = request(
        method = "POST",
        path = "/v1/driver/test-link/revoke",
        body = "{}",
        requireDriverToken = true,
    )

    suspend fun listDriverTripSyncStates0402(
        includePastForVerification0429: Boolean = false,
        timelineProjection0494: Boolean = false,
    ): DriverTripSyncStateResponse0402 = request(
        method = "GET",
        path = buildString {
            append("/v1/driver/trips/sync-state")
            val query = buildList {
                if (includePastForVerification0429) add("includePastForVerification=1")
                if (timelineProjection0494) add("timelineProjection=1")
            }
            if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
        },
        requireDriverToken = true,
        successNextStage0421 = if (timelineProjection0494) "TIMELINE_CANONICAL_PROJECTION" else "PUBLIC_IDENTITY_RESOLUTION",
    )

    /**
     * Reads the Timeline exclusively from the authenticated canonical backend.
     * The extra booking reads keep this client compatible with a backend revision
     * that predates the embedded-bookings optimization; they still read the same
     * canonical trip documents and never consult the collector.
     */
    suspend fun loadCanonicalTimelineState0494(
        includePastForVerification0429: Boolean = true,
    ): DriverTripSyncStateResponse0402 = coroutineScope {
        val response = listDriverTripSyncStates0402(
            includePastForVerification0429 = includePastForVerification0429,
            timelineProjection0494 = true,
        )
        require(response.source.isBlank() || response.source == "CANONICAL_BACKEND") {
            "Fonte inesperada para a Timeline: ${response.source}"
        }
        val semaphore = Semaphore(4)
        val enriched = response.trips.map { state ->
            async {
                if (state.bookings.isNotEmpty() || state.bookingsCount <= 0) {
                    state
                } else {
                    semaphore.withPermit {
                        val remote = listBookings(state.remoteTripId)
                        state.copy(
                            bookings = remote.bookings,
                            publicationRevision = maxOf(state.publicationRevision, remote.entityRevision),
                        )
                    }
                }
            }
        }.awaitAll()
        response.copy(
            trips = enriched,
            source = "CANONICAL_BACKEND",
            snapshotAtMillis = response.snapshotAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    suspend fun updateDriverTripPublicVisibility0491(
        remoteTripId: String,
        online: Boolean,
        expectedVisibilityRevision0471: Long? = null,
    ): DriverTripPublicVisibilityResponse0491 = request(
        method = "PUT",
        path = "/v1/driver/trips/${remoteTripId.trim()}/public-visibility",
        body = json.encodeToString(
            DriverTripPublicVisibilityRequest0491(
                online = online,
                expectedVisibilityRevision0471 = expectedVisibilityRevision0471,
            ),
        ),
        requireDriverToken = true,
    )

    internal suspend fun readPublicTripProjection0411(
        remoteTripId: String,
        evidence0421: RemotePublicationEvidenceContext0421? = null,
    ): DriverPublicTripReadback0411 = request(
        method = "GET",
        path = "/v1/driver/trips/${remoteTripId.trim()}/public-readback",
        requireDriverToken = true,
        evidence0421 = evidence0421,
        successNextStage0421 = "PUBLIC_READBACK_RESPONSE",
    )

    internal suspend fun writePrivateAgendaMirror0434(
        mirror: DriverPrivateMirrorWriteRequest0434,
        evidence0421: RemotePublicationEvidenceContext0421? = null,
    ): DriverPrivateMirrorWriteResponse0434 {
        val body = serializeRequestForEvidence0458(evidence0421, "PRIVATE_MIRROR_WRITE") {
            json.encodeToString(mirror)
        }
        return request(
            method = "PUT",
            path = "/v1/driver/private-mirror",
            body = body,
            requireDriverToken = true,
            evidence0421 = evidence0421,
        )
    }

    internal suspend fun readPrivateAgendaMirror0434(
        canonicalTripId: String,
        evidence0421: RemotePublicationEvidenceContext0421? = null,
    ): DriverPrivateMirrorReadback0434 {
        val body = serializeRequestForEvidence0458(evidence0421, "PRIVATE_MIRROR_READBACK") {
            json.encodeToString(DriverPrivateMirrorReadRequest0434(canonicalTripId.trim()))
        }
        return request(
            method = "POST",
            path = "/v1/driver/private-mirror/readback",
            body = body,
            requireDriverToken = true,
            evidence0421 = evidence0421,
        )
    }

    suspend fun reportPublicTripAttestation0417(
        remoteTripId: String,
        request: DriverPublicAttestationRequest0417,
    ): DriverPublicAttestationResponse0417 = request(
        method = "POST",
        path = "/v1/driver/trips/${remoteTripId.trim()}/public-attestation",
        body = json.encodeToString(request),
        requireDriverToken = true,
    )

    suspend fun adminSyncPolicy0417(): DriverAdminSyncPolicyResponse0417 = request(
        method = "GET",
        path = "/v1/driver/admin/sync-policy",
        requireDriverToken = true,
    )

    suspend fun confirmAdminBlaBlaIdentity0472(
        request0472: DriverAdminIdentityConfirmRequest0472,
    ): DriverAdminIdentityConfirmResponse0472 = request(
        method = "POST",
        path = "/v1/driver/admin/identity-recovery",
        body = json.encodeToString(request0472),
        requireDriverToken = true,
    )

    suspend fun reportAdminSyncHealth0417(
        health: DriverAdminSyncHealthRequest0417,
    ): DriverAdminSyncHealthResponse0417 = request(
        method = "POST",
        path = "/v1/driver/admin/sync-health",
        body = json.encodeToString(health),
        requireDriverToken = true,
    )

    suspend fun publish(trip: Trip): PublishedTripResponse = request(
        method = "POST",
        path = "/v1/driver/trips",
        body = json.encodeToString(trip),
        requireDriverToken = true,
    )

    suspend fun update(trip: Trip): PublishedTripResponse = request(
        method = "PUT",
        path = "/v1/driver/trips/${trip.remoteId ?: trip.id}",
        body = json.encodeToString(trip),
        requireDriverToken = true,
    )

    suspend fun reconcileAgendaSeatAllocation(
        rotaCertaSeatAllocation: Int,
        configVersion: Long,
    ): DriverAgendaSeatAllocationReconcileResponse = request(
        method = "PUT",
        path = "/v1/driver/agenda/seat-allocation",
        body = json.encodeToString(
            DriverAgendaSeatAllocationReconcileRequest(
                rotaCertaSeatAllocation = rotaCertaSeatAllocation.coerceIn(0, 999),
                configVersion = configVersion.coerceAtLeast(0L),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun reconcileCapacitySnapshot(
        remoteTripId: String,
        trip: Trip,
        claims: List<Booking>,
        protectedBookings: List<Booking> = emptyList(),
        claimNamespace: String,
        snapshotRevision: String,
        entityRevision: Long = 0L,
        canonicalTripId: String = "",
        outboxEventId: String = "",
        mutationId0421: String = "",
        idempotencyKey0421: String = "",
        expectedPublicProjectionHash0425: String = "",
        expectedPublicProjectionJson0434: String = "",
        sourceComplete: Boolean = true,
        preserveManagedClaims0436: Boolean = false,
        serverCanonicalAuthority0468: Boolean = false,
    ): DriverCapacitySnapshotResponse {
        val evidence0421 = outboxEventId.takeIf(String::isNotBlank)?.let { traceId ->
            RemotePublicationEvidenceContext0421(
                evidenceId = publicationEvidenceId0421(traceId, trip.canonicalRevision),
                traceId = traceId,
                canonicalTripId = canonicalTripId.ifBlank { trip.id },
                logicalRevision = trip.canonicalRevision,
                transportRevision = entityRevision,
                mutationId = mutationId0421,
                idempotencyKey = idempotencyKey0421,
            )
        }
        val body = serializeRequestForEvidence0458(evidence0421, "CAPACITY_SNAPSHOT") {
            json.encodeToString(
                DriverCapacitySnapshotRequest(
                    trip = trip.copy(
                        remoteId = remoteTripId,
                        capacityReliable = if (sourceComplete) true else trip.capacityReliable,
                    ),
                    claims = claims.map { booking ->
                        DriverCapacitySnapshotClaim(
                            id = booking.id,
                            passengerName = booking.passengerName,
                            passengerContact = booking.passengerContact,
                            boardingStopId = booking.boardingStopId,
                            dropoffStopId = booking.dropoffStopId,
                            seats = booking.seats,
                            status = booking.status.name,
                            source = booking.source,
                            capacityClaimType = booking.capacityClaimType,
                            sourceReference = booking.sourceReference,
                            occupancyGroupId = booking.occupancyGroupId,
                            holdExpiresAtMillis = booking.holdExpiresAtMillis,
                        )
                    },
                    protectedBookings = protectedBookings.map { booking ->
                        DriverProtectedBookingSnapshot(
                            id = booking.id,
                            passengerName = booking.passengerName,
                            passengerContact = booking.passengerContact,
                            boardingStopId = booking.boardingStopId,
                            dropoffStopId = booking.dropoffStopId,
                            seats = booking.seats,
                            status = booking.status.name,
                            operationalStatus = booking.operationalStatus,
                            paymentStatus = booking.paymentStatus,
                            lastDriverSelection = booking.lastDriverSelection,
                            holdExpiresAtMillis = booking.holdExpiresAtMillis,
                            sourceReference = booking.sourceReference,
                            occupancyGroupId = booking.occupancyGroupId,
                        )
                    },
                    claimNamespace = claimNamespace,
                    snapshotRevision = snapshotRevision,
                    sourceComplete = sourceComplete,
                    preserveManagedClaims0436 = preserveManagedClaims0436,
                    entityRevision = entityRevision,
                    canonicalTripId = canonicalTripId,
                    outboxEventId = outboxEventId,
                    mutationId0421 = mutationId0421,
                    idempotencyKey0421 = idempotencyKey0421,
                    expectedPublicProjectionHash0425 = expectedPublicProjectionHash0425,
                    expectedPublicProjectionJson0434 = expectedPublicProjectionJson0434,
                    serverCanonicalAuthority0468 = serverCanonicalAuthority0468,
                ),
            )
        }
        return request(
            method = "PUT",
            path = "/v1/driver/trips/$remoteTripId/capacity-snapshot",
            body = body,
            requireDriverToken = true,
            evidence0421 = evidence0421,
        )
    }

    suspend fun listBookings(remoteTripId: String): DriverBookingsResponse = request(
        method = "GET",
        path = "/v1/driver/trips/$remoteTripId/bookings",
        requireDriverToken = true,
    )

    suspend fun listDriverPassengers(): DriverPassengersResponse = request(
        method = "GET",
        path = "/v1/driver/passengers",
        requireDriverToken = true,
    )

    suspend fun invitePassenger(
        displayName: String,
        passengerContact: String,
        passengerId: String = "",
        referredByContact: String = "",
    ): DriverPassengerInviteResponse = request(
        method = "POST",
        path = "/v1/driver/passengers/invite",
        body = json.encodeToString(
            DriverPassengerInviteRequest(
                displayName = displayName.trim(),
                passengerContact = passengerContact.trim(),
                passengerId = passengerId.trim(),
                referredByContact = referredByContact.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun syncPassengerDirectory(
        profiles: List<PassengerProfile>,
    ): DriverPassengerDirectoryResponse = request(
        method = "POST",
        path = "/v1/driver/passengers/sync",
        body = json.encodeToString(
            DriverPassengerDirectoryRequest(
                profiles
                    .filter { it.id.isNotBlank() && passengerContactKey(it.agendaAccessContact()).isNotBlank() }
                    .distinctBy { passengerContactKey(it.agendaAccessContact()) }
                    .map {
                        DriverPassengerDirectoryItem(
                            passengerId = it.id,
                            displayName = it.displayName,
                            passengerContact = it.agendaAccessContact(),
                            blocked = it.blocked,
                        )
                    },
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun updatePassengerAccessWhatsapp(
        passengerId: String,
        currentPassengerContact: String,
        newPassengerContact: String,
        displayName: String,
    ): DriverPassengerBlockResponse = request(
        method = "PUT",
        path = "/v1/driver/passengers/whatsapp",
        body = json.encodeToString(
            DriverPassengerWhatsappUpdateRequest(
                passengerId = passengerId.trim(),
                currentPassengerContact = currentPassengerContact.trim(),
                newPassengerContact = newPassengerContact.trim(),
                displayName = displayName.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun setPassengerAccessStatus(
        passengerContact: String,
        status: String,
        passengerId: String = "",
    ): DriverPassengerBlockResponse = request(
        method = "POST",
        path = "/v1/driver/passengers/block",
        body = json.encodeToString(
            DriverPassengerBlockRequest(
                passengerContact = passengerContact.trim(),
                passengerId = passengerId.trim(),
                status = status.trim().uppercase(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun setPassengerAccessBlocked(
        passengerContact: String,
        blocked: Boolean,
        passengerId: String = "",
    ): DriverPassengerBlockResponse = setPassengerAccessStatus(
        passengerContact = passengerContact,
        status = if (blocked) "BLOCKED" else "AUTHORIZED",
        passengerId = passengerId,
    )

    suspend fun setPassengerAgendaAdmin0418(
        passengerContact: String,
        passengerId: String,
        agendaAdmin: Boolean,
    ): DriverPassengerBlockResponse = request(
        method = "PUT",
        path = "/v1/driver/passengers/admin",
        body = json.encodeToString(
            DriverPassengerAgendaAdminRequest0418(
                passengerContact = passengerContact.trim(),
                passengerId = passengerId.trim(),
                agendaAdmin = agendaAdmin,
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun resetPassengerPassword(
        passengerContact: String,
        passengerId: String = "",
    ): DriverPassengerResetPasswordResponse = request(
        method = "POST",
        path = "/v1/driver/passengers/reset-password",
        body = json.encodeToString(
            DriverPassengerResetPasswordRequest(
                passengerContact = passengerContact.trim(),
                passengerId = passengerId.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun updateReferralCredit(referralCreditCents: Long): DriverReferralSettingsResponse = request(
        method = "PUT",
        path = "/v1/driver/referral-settings",
        body = json.encodeToString(DriverReferralSettingsRequest(referralCreditCents.coerceAtLeast(0L))),
        requireDriverToken = true,
    )
    suspend fun listPublicDebugEvents(
        afterMillis: Long = 0L,
        limit: Int = 100,
    ): DriverPublicDebugEventsResponse = request(
        method = "GET",
        path = "/v1/driver/public-debug?afterMillis=${afterMillis.coerceAtLeast(0L)}&limit=${limit.coerceIn(1, 250)}",
        requireDriverToken = true,
    )

    suspend fun listDriverNotifications(): DriverNotificationsResponse = request(
        method = "GET",
        path = "/v1/driver/notifications",
        requireDriverToken = true,
    )

    suspend fun markDriverNotificationRead(notificationId: String): NotificationReadResponse = request(
        method = "POST",
        path = "/v1/driver/notifications/" + notificationId.trim() + "/read",
        body = "{}",
        requireDriverToken = true,
    )

    suspend fun markAllDriverNotificationsRead(): NotificationReadResponse = request(
        method = "POST",
        path = "/v1/driver/notifications/read-all",
        body = "{}",
        requireDriverToken = true,
    )

    suspend fun upsertDriverBooking(remoteTripId: String, booking: Booking): DriverBookingUpsertResponse = request(
        method = "PUT",
        path = "/v1/driver/trips/$remoteTripId/bookings/${booking.id}",
        body = json.encodeToString(
            DriverBookingUpsertRequest(
                passengerName = booking.passengerName,
                passengerContact = booking.passengerContact,
                passengerId = booking.passengerId,
                boardingStopId = booking.boardingStopId,
                dropoffStopId = booking.dropoffStopId,
                seats = booking.seats,
                status = booking.status.name,
                operationalStatus = booking.operationalStatus,
                paymentStatus = booking.paymentStatus,
                lastDriverSelection = booking.lastDriverSelection,
                holdExpiresAtMillis = booking.holdExpiresAtMillis,
                source = booking.source,
                capacityClaimType = booking.capacityClaimType,
                sourceReference = booking.sourceReference,
                occupancyGroupId = booking.occupancyGroupId,
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun updateProtectedDriverBooking(
        remoteTripId: String,
        booking: Booking,
    ): DriverBookingUpsertResponse = request(
        method = "PUT",
        path = "/v1/driver/trips/$remoteTripId/bookings/${booking.id}/admin",
        body = json.encodeToString(
            DriverBookingUpsertRequest(
                passengerName = booking.passengerName,
                passengerContact = booking.passengerContact,
                passengerId = booking.passengerId,
                boardingStopId = booking.boardingStopId,
                dropoffStopId = booking.dropoffStopId,
                seats = booking.seats,
                status = booking.status.name,
                operationalStatus = booking.operationalStatus,
                paymentStatus = booking.paymentStatus,
                lastDriverSelection = booking.lastDriverSelection,
                holdExpiresAtMillis = booking.holdExpiresAtMillis,
                source = booking.source,
                capacityClaimType = booking.capacityClaimType,
                sourceReference = booking.sourceReference,
                occupancyGroupId = booking.occupancyGroupId,
            ),
        ),
        requireDriverToken = true,
    )

    suspend fun cancelProtectedDriverBooking(
        remoteTripId: String,
        bookingId: String,
    ): DriverBookingUpsertResponse = request(
        method = "POST",
        path = "/v1/driver/trips/$remoteTripId/bookings/$bookingId/admin/cancel",
        body = "{}",
        requireDriverToken = true,
    )

    suspend fun updateDriverPassengerOperationalStatus(
        remoteTripId: String,
        bookingId: String,
        selection: String,
    ): DriverBookingUpsertResponse = request(
        method = "POST",
        path = "/v1/driver/trips/$remoteTripId/bookings/$bookingId/operational",
        body = json.encodeToString(DriverOperationalStatusRequest(selection.trim().uppercase())),
        requireDriverToken = true,
    )

    suspend fun decideDriverBooking(
        remoteTripId: String,
        bookingId: String,
        action: String,
        reason: String = "",
    ): DriverBookingUpsertResponse = request(
        method = "POST",
        path = "/v1/driver/trips/$remoteTripId/bookings/$bookingId/decision",
        body = json.encodeToString(
            DriverBookingDecisionRequest(
                action = action.trim().uppercase(),
                reason = reason.trim(),
            ),
        ),
        requireDriverToken = true,
    )

    internal suspend fun interpretAssistant0410(
        request: RotaCertaAssistantInterpretRequest0410,
    ): RotaCertaAssistantInterpretResponse0410 = request(
        method = "POST",
        path = "/v1/assistant/interpret",
        body = json.encodeToString(request),
        requireDriverToken = true,
    )

    suspend fun createPublicBooking(
        publicToken: String,
        request: PublicBookingRequest,
    ): RemoteBookingResponse = request(
        method = "POST",
        path = "/v1/public/trips/$publicToken/bookings",
        body = json.encodeToString(request),
        requireDriverToken = false,
    )

    private inline fun serializeRequestForEvidence0458(
        evidence0421: RemotePublicationEvidenceContext0421?,
        requestKind: String,
        block: () -> String,
    ): String {
        val startedNs = System.nanoTime()
        return try {
            val body = block()
            val bytes = body.toByteArray(Charsets.UTF_8)
            val byteEvidence = AgendaFailureEvidence.byteSanitizationEvidence0458(bytes)
            recordRemotePublicationEvidence0421(
                evidence0421,
                stage = "REQUEST_SERIALIZATION",
                status = "OK",
                reason = "JSON_SERIALIZED",
                extra = "requestKind=$requestKind charset=UTF-8 " +
                    byteEvidence.compactDetails0458() +
                    " durationMs=" + ((System.nanoTime() - startedNs).coerceAtLeast(0L) / 1_000_000L) +
                    " previousStage=CANONICAL_OPERATIONAL_BUILD nextStage=REQUEST_SANITIZATION",
            )
            body
        } catch (error: Throwable) {
            val resolution = AgendaFailureEvidence.resolveCauseChain(error)
            val root = resolution.chain.lastOrNull() ?: error
            val message = runCatching { UnifiedDebugEventStore.sanitizeForExport(error.message.orEmpty()) }
                .getOrDefault("<sanitization-failed>")
                .replace('"', '\'')
                .take(240)
            val rootMessage = runCatching { UnifiedDebugEventStore.sanitizeForExport(root.message.orEmpty()) }
                .getOrDefault("<sanitization-failed>")
                .replace('"', '\'')
                .take(240)
            val source = error.stackTrace.firstOrNull { it.className.startsWith("br.com.mapeiaia.rotacerta") }
            recordRemotePublicationEvidence0421(
                evidence0421,
                stage = "REQUEST_SERIALIZATION",
                status = "FAILED",
                reason = "REQUEST_SERIALIZATION_EXCEPTION",
                extra = buildString {
                    append("requestKind=").append(requestKind)
                    append(" exceptionClass=").append(error.javaClass.name)
                    append(" exceptionMessage=\"").append(message).append('\"')
                    append(" rootCauseClass=").append(root.javaClass.name)
                    append(" rootCauseMessage=\"").append(rootMessage).append('\"')
                    if (source != null) {
                        append(" exceptionSource=")
                            .append(source.fileName ?: source.className.substringAfterLast('.'))
                            .append(':').append(source.methodName).append(':').append(source.lineNumber)
                    }
                    append(" durationMs=").append(((System.nanoTime() - startedNs).coerceAtLeast(0L)) / 1_000_000L)
                    append(" previousStage=CANONICAL_OPERATIONAL_BUILD nextStage=STOP")
                },
            )
            throw error
        }
    }

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        body: String? = null,
        requireDriverToken: Boolean,
        evidence0421: RemotePublicationEvidenceContext0421? = null,
        successNextStage0421: String = "PUBLIC_READBACK_REQUEST",
    ): T = withContext(Dispatchers.IO) {
        val requestPayload = body.orEmpty()
        val requestPayloadBytes = if (body == null) ByteArray(0) else requestPayload.toByteArray(Charsets.UTF_8)
        val callStartedNs = System.nanoTime()
        val networkCallId = buildString {
            append("net-")
            append(callStartedNs.toString(36))
            append('-')
            append(sha256Hex("$method|$path".toByteArray(Charsets.UTF_8)).take(10))
        }
        val sanitizationEvidence0458 = AgendaFailureEvidence.byteSanitizationEvidence0458(requestPayloadBytes)
        recordRemotePublicationEvidence0421(
            evidence0421,
            stage = "REQUEST_SANITIZATION",
            status = if (sanitizationEvidence0458.sanitizerSucceeded) "OK" else "DEGRADED",
            reason = if (sanitizationEvidence0458.sanitizerSucceeded) {
                if (sanitizationEvidence0458.sanitizationChanged) "EVIDENCE_REDACTION_APPLIED" else "EVIDENCE_REDACTION_NOT_REQUIRED"
            } else {
                "EVIDENCE_SANITIZER_FAILED"
            },
            extra = "networkCallId=$networkCallId " + sanitizationEvidence0458.compactDetails0458() +
                " charset=UTF-8 previousStage=REQUEST_SERIALIZATION nextStage=REQUEST_BUILD",
        )
        recordRemotePublicationEvidence0421(
            evidence0421,
            stage = "REQUEST_BUILD",
            status = "OK",
            reason = "REQUEST_SERIALIZED",
            extra = "networkCallId=$networkCallId method=$method endpoint=${seatSyncDiagnosticKey(path)} requestBytes=${requestPayloadBytes.size} requestSha256=${sha256Hex(requestPayloadBytes)} charset=UTF-8 contentType=application/json previousStage=REQUEST_SANITIZATION nextStage=HTTP_SEND",
        )
        var phase = "validate_configuration"
        var connection: HttpURLConnection? = null
        var status = 0
        var responsePayloadBytes = ByteArray(0)
        var responseText = ""
        var responseContentType = ""
        var requestId = ""
        var correlationId = ""

        try {
            check(settings.apiBaseUrl.startsWith("https://")) { "Servidor HTTPS não configurado" }
            if (requireDriverToken) check(settings.driverToken.isNotBlank()) { "Chave do motorista não configurada" }

            val base = settings.apiBaseUrl.trimEnd('/')
            phase = "open_connection"
            val opened = URL(base + path).openConnection() as HttpURLConnection
            connection = opened
            opened.requestMethod = method
            opened.connectTimeout = 12_000
            opened.readTimeout = 12_000
            opened.setRequestProperty("Accept", "application/json")
            opened.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (settings.publicBaseUrl.startsWith("https://")) {
                opened.setRequestProperty("X-Rota-Certa-Public-Base-Url", settings.publicBaseUrl)
            }
            if (requireDriverToken && settings.driverUsername.isNotBlank()) {
                opened.setRequestProperty("X-Rota-Certa-Driver-Username", settings.driverUsername)
            }
            if (requireDriverToken) {
                opened.setRequestProperty("X-Rota-Certa-Driver-Token", settings.driverToken)
            }
            if (body != null) {
                phase = "request_body_write"
                opened.doOutput = true
                opened.outputStream.use { output -> output.write(requestPayloadBytes) }
            }
            recordRemotePublicationEvidence0421(
                evidence0421,
                stage = "HTTP_SEND",
                status = "OK",
                reason = "REQUEST_BYTES_SENT",
                extra = "networkCallId=$networkCallId method=$method endpoint=${seatSyncDiagnosticKey(path)} requestBytes=${requestPayloadBytes.size} requestSha256=${sha256Hex(requestPayloadBytes)} previousStage=REQUEST_BUILD nextStage=HTTP_RESPONSE",
            )

            phase = "response_status"
            status = opened.responseCode
            responseContentType = opened.contentType?.trim().orEmpty()
            requestId = responseHeader(opened, "X-Request-Id", "Request-Id")
            correlationId = responseHeader(opened, "X-Correlation-Id", "Correlation-Id")

            phase = "response_body_read"
            responsePayloadBytes = (if (status in 200..299) opened.inputStream else opened.errorStream)
                ?.use { input -> input.readBytes() }
                ?: ByteArray(0)
            responseText = responsePayloadBytes.toString(Charsets.UTF_8)
            recordRemotePublicationEvidence0421(
                evidence0421,
                stage = "HTTP_RESPONSE",
                status = if (status in 200..299) "OK" else "FAILED",
                reason = if (status in 200..299) "HTTP_2XX" else backendErrorCode(responseText).ifBlank { "HTTP_$status" },
                extra = "networkCallId=$networkCallId httpStatus=$status responseBytes=${responsePayloadBytes.size} responseSha256=${sha256Hex(responsePayloadBytes)} responseContentType=${UnifiedDebugEventStore.sanitizeForExport(responseContentType).take(80)} requestId=$requestId correlationId=$correlationId durationMs=${((System.nanoTime() - callStartedNs).coerceAtLeast(0L)) / 1_000_000L} previousStage=HTTP_SEND nextStage=SERVER_ACK",
            )

            if (status !in 200..299) {
                recordRemotePublicationEvidence0421(
                    evidence0421,
                    stage = "SERVER_ACK",
                    status = "DENIED",
                    reason = backendErrorCode(responseText).ifBlank { "HTTP_$status" },
                    extra = "networkCallId=$networkCallId httpStatus=$status previousStage=HTTP_RESPONSE nextStage=$successNextStage0421",
                )
                remoteException(
                    method = method,
                    path = path,
                    status = status,
                    backendErrorCode = backendErrorCode(responseText),
                    responseText = responseText,
                    requestText = requestPayload,
                    requestId = requestId,
                    correlationId = correlationId,
                    networkCallId = networkCallId,
                    phase = "http_status",
                    requestBytes = requestPayloadBytes,
                    responseBytes = responsePayloadBytes,
                    responseContentType = responseContentType,
                    startedNs = callStartedNs,
                )
            }

            phase = "decode_json"
            try {
                val decoded = json.decodeFromString<T>(responseText)
                recordRemotePublicationEvidence0421(
                    evidence0421,
                    stage = "SERVER_ACK",
                    status = "OK",
                    reason = "WRITE_ACCEPTED",
                    extra = "networkCallId=$networkCallId httpStatus=$status responseSha256=${sha256Hex(responsePayloadBytes)} durationMs=${((System.nanoTime() - callStartedNs).coerceAtLeast(0L)) / 1_000_000L} previousStage=HTTP_RESPONSE nextStage=$successNextStage0421",
                )
                decoded
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                recordRemotePublicationEvidence0421(
                    evidence0421,
                    stage = "SERVER_ACK",
                    status = "FAILED",
                    reason = "RESPONSE_DECODE_FAILED",
                    extra = "networkCallId=$networkCallId httpStatus=$status responseBytes=${responsePayloadBytes.size} responseSha256=${sha256Hex(responsePayloadBytes)} previousStage=HTTP_RESPONSE nextStage=$successNextStage0421",
                )
                remoteException(
                    method = method,
                    path = path,
                    status = status,
                    backendErrorCode = backendErrorCode(responseText),
                    responseText = responseText,
                    requestText = requestPayload,
                    requestId = requestId,
                    correlationId = correlationId,
                    networkCallId = networkCallId,
                    phase = phase,
                    requestBytes = requestPayloadBytes,
                    responseBytes = responsePayloadBytes,
                    responseContentType = responseContentType,
                    startedNs = callStartedNs,
                    cause = error,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (remote: TripRemoteApiException) {
            throw remote
        } catch (error: Throwable) {
            if (status <= 0) {
                recordRemotePublicationEvidence0421(
                    evidence0421,
                    stage = "HTTP_RESPONSE",
                    status = "FAILED",
                    reason = "TRANSPORT_" + phase.uppercase(),
                    extra = "networkCallId=$networkCallId httpStatus=0 responseBytes=${responsePayloadBytes.size} responseSha256=${sha256Hex(responsePayloadBytes)} durationMs=${((System.nanoTime() - callStartedNs).coerceAtLeast(0L)) / 1_000_000L} previousStage=HTTP_SEND nextStage=SERVER_ACK",
                )
            }
            remoteException(
                method = method,
                path = path,
                status = status,
                backendErrorCode = backendErrorCode(responseText),
                responseText = responseText,
                requestText = requestPayload,
                requestId = requestId,
                correlationId = correlationId,
                networkCallId = networkCallId,
                phase = phase,
                requestBytes = requestPayloadBytes,
                responseBytes = responsePayloadBytes,
                responseContentType = responseContentType,
                startedNs = callStartedNs,
                cause = error,
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun remoteException(
        method: String,
        path: String,
        status: Int,
        backendErrorCode: String,
        responseText: String,
        requestText: String,
        requestId: String,
        correlationId: String,
        networkCallId: String,
        phase: String,
        requestBytes: ByteArray,
        responseBytes: ByteArray,
        responseContentType: String,
        startedNs: Long,
        cause: Throwable? = null,
    ): Nothing {
        val elapsedMs = ((System.nanoTime() - startedNs).coerceAtLeast(0L)) / 1_000_000L
        throw TripRemoteApiException(
            httpMethod = method,
            endpoint = path.take(220),
            httpStatus = status,
            backendErrorCode = backendErrorCode,
            sanitizedResponse = UnifiedDebugEventStore.sanitizeForExport(responseText).take(600),
            requestId = requestId,
            correlationId = correlationId,
            networkCallId = networkCallId,
            transportPhase = phase,
            requestBytes = requestBytes.size,
            responseBytes = responseBytes.size,
            requestSha256 = sha256Hex(requestBytes),
            responseSha256 = sha256Hex(responseBytes),
            sanitizedRequest = UnifiedDebugEventStore.sanitizeForExport(requestText).take(600),
            responseContentType = UnifiedDebugEventStore.sanitizeForExport(responseContentType).take(120),
            elapsedMs = elapsedMs,
            cause = cause,
        )
    }

    private fun recordRemotePublicationEvidence0421(
        evidence: RemotePublicationEvidenceContext0421?,
        stage: String,
        status: String,
        reason: String,
        extra: String = "",
    ) {
        evidence ?: return
        // Evidence must never become a transport/business failure source.
        runCatching {
            UnifiedDebugEventStore.recordAlways(
                "PUBLIC_EVIDENCE_0421",
                "br.com.mapeiaia.rotacerta.trips",
                buildString {
                    append("evidenceId=").append(evidence.evidenceId)
                    append(" traceId=").append(evidence.traceId)
                    append(" correlationId=").append(evidence.traceId)
                    append(" stage=").append(stage)
                    append(" status=").append(status)
                    append(" reasonCode=").append(reason)
                    append(" canonicalTripId=").append(seatSyncDiagnosticKey(evidence.canonicalTripId))
                    append(" logicalRevision=").append(evidence.logicalRevision)
                    append(" transportRevision=").append(evidence.transportRevision)
                    append(" mutationId=").append(evidence.mutationId)
                    append(" idempotencyKey=").append(evidence.idempotencyKey)
                    if (extra.isNotBlank()) append(' ').append(extra)
                },
            )
        }
    }
    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun responseHeader(connection: HttpURLConnection, vararg names: String): String =
        names.asSequence()
            .mapNotNull { name -> connection.getHeaderField(name)?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?.let { UnifiedDebugEventStore.sanitizeForExport(it) }
            ?.take(120)
            .orEmpty()

    private fun backendErrorCode(responseText: String): String {
        val patterns = listOf(
            Regex("(?i)\\\"(?:errorCode|error_code|code)\\\"\\s*:\\s*\\\"([^\\\"]{1,96})\\\""),
            Regex("(?i)\\b(?:errorCode|error_code|code)\\s*[:=]\\s*([A-Z0-9_.-]{2,96})"),
        )
        return patterns.asSequence()
            .mapNotNull { it.find(responseText)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?.let { UnifiedDebugEventStore.sanitizeForExport(it) }
            ?.take(96)
            .orEmpty()
    }
}

/**
 * Server contract carries operational/payment state; passengerId/fare/exact-address remain local-preserved metadata.
 * Preserve those local-only values when a remote refresh updates the same Booking.
 */
fun RemoteBooking.toLocalBooking(localTripId: String, existingLocal: Booking? = null): Booking = Booking(
    id = id,
    tripId = localTripId,
    passengerName = passengerName,
    passengerContact = passengerContact,
    boardingStopId = boardingStopId,
    dropoffStopId = dropoffStopId,
    seats = seats,
    status = runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.CONFIRMED),
    operationalStatus = operationalStatus,
    paymentStatus = paymentStatus,
    lastDriverSelection = lastDriverSelection,
    holdExpiresAtMillis = holdExpiresAtMillis,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    source = source,
    capacityClaimType = capacityClaimType,
    sourceReference = sourceReference,
    occupancyGroupId = occupancyGroupId,
    passengerId = existingLocal?.passengerId?.takeIf(String::isNotBlank) ?: passengerId,
    fareMinorUnits = existingLocal?.fareMinorUnits,
    fareCurrencyCode = existingLocal?.fareCurrencyCode.orEmpty(),
    boardingAddress = existingLocal?.boardingAddress.orEmpty(),
    dropoffAddress = existingLocal?.dropoffAddress.orEmpty(),
    cancellationToken = existingLocal?.cancellationToken,
    localMetadataTouched = existingLocal?.localMetadataTouched == true,
)
