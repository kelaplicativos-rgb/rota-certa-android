package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.net.URI
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object BlaBlaRidesEnrichmentStatus0533 {
    const val COMPLETE = "COMPLETE"
    const val PARTIAL = "PARTIAL"
    const val FAILED = "FAILED"
    const val PENDING_UNKNOWN = "PENDING_UNKNOWN"
}

@Serializable
internal data class BlaBlaRidesEnrichedRide0533(
    val captureId: String,
    val authenticatedProfileUuid: String,
    val tripId: String,
    val inventoryHref: String = "",
    val status: String = BlaBlaRidesEnrichmentStatus0533.PENDING_UNKNOWN,
    val startedAt: String = "",
    val completedAt: String = "",
    val attemptCount: Int = 0,
    val lastError: String = "NOT_ATTEMPTED",
    val trip: BlaBlaCollectorTrip? = null,
    val provenance: List<String> = listOf("inventory:RIDE_LIST"),
    val inventoryHrefSha256: String = "",
    val tripPayloadSha256: String = "",
    val evidenceFile: String = "",
    val evidenceBytes: Long = 0L,
    val evidenceSha256: String = "",
    val sessionUpdatedAtMillis: Long = 0L,
    val sourceAccessStatus: String = "UNKNOWN",
)

@Serializable
internal data class BlaBlaRidesEnrichedProfile0533(
    val accountKey: String,
    val expectedProfileUuid: String,
    val authenticatedProfileUuid: String,
    val inventoryStatus: String,
    val expectedTripCount: Int,
    val identifiedTripCount: Int,
    val unresolvedInventoryTripCount: Int,
    val rides: List<BlaBlaRidesEnrichedRide0533>,
)

@Serializable
internal data class BlaBlaRidesEnrichmentManifest0533(
    val schemaVersion: String = SCHEMA_VERSION_0533,
    val kind: String = KIND_0533,
    val captureId: String,
    val inventorySchemaVersion: String,
    val inventoryResult: String,
    val inventoryCompletedAt: String,
    val inventoryComplete: Boolean,
    val startedAt: String,
    val completedAt: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val commitSha: String = BuildConfig.BUILD_GIT_SHA,
    val branch: String = BuildConfig.BUILD_GIT_BRANCH,
    val expectedTripCount: Int,
    val identifiedTripCount: Int,
    val unresolvedInventoryTripCount: Int,
    val terminalTripCount: Int = 0,
    val completeCount: Int = 0,
    val partialCount: Int = 0,
    val failedCount: Int = 0,
    val pendingUnknownCount: Int = 0,
    val enrichmentComplete: Boolean = false,
    val profiles: List<BlaBlaRidesEnrichedProfile0533>,
)

@Serializable
private data class BlaBlaRidesTripCheckpoint0533(
    val schemaVersion: String = "rota-certa-blablacar-trip-enrichment-v1",
    val captureId: String,
    val authenticatedProfileUuid: String,
    val tripId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String,
    val attemptCount: Int,
    val lastError: String,
    val inventoryHref: String,
    val trip: BlaBlaCollectorTrip? = null,
    val provenance: List<String>,
    val sessionUpdatedAtMillis: Long,
    val sourceAccessStatus: String,
)

internal data class BlaBlaRidesExpectedTrip0533(
    val accountKey: String,
    val profileUuid: String,
    val inventoryStatus: String,
    val expectedProfileTripCount: Int,
    val tripId: String,
    val tripHref: String,
)

internal data class BlaBlaRidesEnrichmentPlan0533(
    val inventoryComplete: Boolean,
    val expectedTripCount: Int,
    val identifiedTripCount: Int,
    val unresolvedInventoryTripCount: Int,
    val trips: List<BlaBlaRidesExpectedTrip0533>,
)

internal data class BlaBlaRidesEnrichmentDecision0533(
    val status: String,
    val error: String = "",
)

internal const val SCHEMA_VERSION_0533 = "rota-certa-blablacar-rides-v2"
internal const val KIND_0533 = "BLABLACAR_SUAS_VIAGENS_ENRICHED"

private val ENRICHMENT_JSON_0533 = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

internal fun extractInventoryTripTargets0533(
    htmlRaw: String,
    inventoryPageUrl: String,
): Map<String, String> {
    if (htmlRaw.isBlank()) return emptyMap()
    val origin = BlaBlaCollectorUrlModule.origin(inventoryPageUrl).orEmpty()
    val pageUri = runCatching { URI(inventoryPageUrl) }.getOrNull()
    val found = linkedMapOf<String, MutableSet<String>>()
    val hrefRegex = Regex("""(?is)\\bhref\\s*=\\s*[\"']([^\"']+)[\"']""")
    hrefRegex.findAll(htmlRaw).forEach { match ->
        val raw = match.groupValues[1]
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&#38;", "&", ignoreCase = true)
            .trim()
        val absolute = when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith('/') && origin.isNotBlank() -> origin + raw
            raw.startsWith("https://", ignoreCase = true) -> raw
            pageUri != null -> runCatching { pageUri.resolve(raw).toString() }.getOrDefault("")
            else -> ""
        }
        val canonical = BlaBlaCollectorUrlModule.canonical(absolute)
        if (canonical.isBlank() || !BlaBlaCollectorUrlModule.isSpecificTrip(canonical)) return@forEach
        val path = runCatching { URI(canonical).path.orEmpty().lowercase() }.getOrDefault("")
        if (path.trimEnd('/') != "/rides/offer") return@forEach
        val tripId = BlaBlaCollectorUrlModule.tripId(canonical)?.trim().orEmpty()
        if (tripId.isBlank()) return@forEach
        found.getOrPut(tripId) { linkedSetOf() }.add(canonical)
    }
    return found.mapNotNull { (tripId, hrefs) ->
        hrefs.singleOrNull()?.let { tripId to it }
    }.toMap()
}

internal fun classifyEnrichmentResult0533(
    runSucceeded: Boolean,
    runFailure: String,
    expectedProfileUuid: String,
    expectedTripId: String,
    attemptStartedAtMillis: Long,
    session: BlaBlaDynamicSessionSnapshot?,
    trip: BlaBlaCollectorTrip?,
): BlaBlaRidesEnrichmentDecision0533 {
    if (!runSucceeded) {
        return BlaBlaRidesEnrichmentDecision0533(
            BlaBlaRidesEnrichmentStatus0533.FAILED,
            runFailure.ifBlank { "EXACT_SYNC_FAILED" },
        )
    }
    if (session == null) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.PARTIAL, "SESSION_SNAPSHOT_MISSING")
    }
    if (session.updatedAtMillis < attemptStartedAtMillis) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.PARTIAL, "SESSION_SNAPSHOT_STALE")
    }
    val expectedUuid = BlaBlaRidesSnapshotStore0526.strongUuid(expectedProfileUuid)
    val observedUuid = BlaBlaRidesSnapshotStore0526.strongUuid(session.profileUuid)
    if (expectedUuid == null || observedUuid != expectedUuid) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.FAILED, "PROFILE_UUID_MISMATCH")
    }
    if (!session.identityVerified) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.PARTIAL, "PROFILE_IDENTITY_NOT_VERIFIED")
    }
    if (session.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.FAILED, "SOURCE_TEMPORARILY_RESTRICTED")
    }
    if (trip == null) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.PARTIAL, "TARGET_TRIP_MISSING_AFTER_SYNC")
    }
    if (!trip.profile_uuid.trim().equals(expectedUuid, ignoreCase = true)) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.FAILED, "TRIP_PROFILE_UUID_MISMATCH")
    }
    if (trip.trip_id?.trim() != expectedTripId || BlaBlaCollectorUrlModule.tripId(trip.trip_href) != expectedTripId) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.FAILED, "TRIP_IDENTITY_MISMATCH")
    }
    if (trip.identity_conflict) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.FAILED, "TRIP_IDENTITY_CONFLICT")
    }
    if (!trip.passenger_roster_complete) {
        return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.PARTIAL, "PASSENGER_ROSTER_NOT_TERMINAL")
    }
    return BlaBlaRidesEnrichmentDecision0533(BlaBlaRidesEnrichmentStatus0533.COMPLETE)
}

internal fun finalizeEnrichmentCoverage0533(
    value: BlaBlaRidesEnrichmentManifest0533,
    completedAt: String = value.completedAt,
): BlaBlaRidesEnrichmentManifest0533 {
    val rides = value.profiles.flatMap { it.rides }
    val complete = rides.count { it.status == BlaBlaRidesEnrichmentStatus0533.COMPLETE }
    val partial = rides.count { it.status == BlaBlaRidesEnrichmentStatus0533.PARTIAL }
    val failed = rides.count { it.status == BlaBlaRidesEnrichmentStatus0533.FAILED }
    val pendingKnown = rides.count { it.status == BlaBlaRidesEnrichmentStatus0533.PENDING_UNKNOWN }
    val pending = pendingKnown + value.unresolvedInventoryTripCount
    val terminal = complete + partial + failed + pending
    val coverageComplete = value.inventoryComplete &&
        value.unresolvedInventoryTripCount == 0 &&
        value.identifiedTripCount == value.expectedTripCount &&
        rides.size == value.expectedTripCount &&
        complete == value.expectedTripCount && partial == 0 && failed == 0 && pending == 0
    return value.copy(
        completedAt = completedAt,
        terminalTripCount = terminal,
        completeCount = complete,
        partialCount = partial,
        failedCount = failed,
        pendingUnknownCount = pending,
        enrichmentComplete = coverageComplete,
    )
}

internal fun shouldRetryEnrichment0533(status: String, error: String): Boolean {
    if (status == BlaBlaRidesEnrichmentStatus0533.COMPLETE || status == BlaBlaRidesEnrichmentStatus0533.PENDING_UNKNOWN) return false
    if (error in setOf("AUTH_REQUIRED", "BROKEN_FOR_VERSION", "PROFILE_UUID_MISMATCH", "TRIP_PROFILE_UUID_MISMATCH", "TRIP_IDENTITY_MISMATCH", "TRIP_IDENTITY_CONFLICT", "SOURCE_TEMPORARILY_RESTRICTED", "TEMPORARILY_RESTRICTED")) return false
    return true
}

internal object BlaBlaRidesEnrichmentPlanner0533 {
    fun build(context: Context, inventory: BlaBlaRidesSnapshotManifest0526): BlaBlaRidesEnrichmentPlan0533 {
        val app = context.applicationContext
        val store = BlaBlaRidesSnapshotStore0526(app)
        val expectedTotal = inventory.profiles.sumOf { it.tripInventory.uniqueCount }
        val inventoryComplete = inventory.result == BlaBlaRidesSnapshotStatus0526.COMPLETE &&
            inventory.profiles.all { profile ->
                profile.status == BlaBlaRidesSnapshotStatus0526.COMPLETE &&
                    profile.identityConfirmed && profile.reachedEnd && profile.stabilized &&
                    profile.tripInventory.duplicateCount == 0
            }
        val planned = mutableListOf<BlaBlaRidesExpectedTrip0533>()

        inventory.profiles.forEach { profile ->
            val profileUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profile.authenticatedProfileUuid) ?: return@forEach
            val indexFile = store.resolveArtifact0528(inventory.captureId, profile.ridesIndexFile)
            if (!verifySnapshotArtifact0528(indexFile, profile.ridesIndexBytes, profile.ridesIndexSha256)) return@forEach
            val index = runCatching {
                ENRICHMENT_JSON_0533.decodeFromString(BlaBlaRidesIndexJson0528.serializer(), indexFile!!.readText(Charsets.UTF_8))
            }.getOrNull() ?: return@forEach
            val ids = canonicalTripIds0528(index.tripIds)
            if (ids != index.tripIds || index.duplicateCount != 0 || tripSetSha2560528(ids) != index.tripIdsSha256) return@forEach
            if (index.tripIdsSha256 != profile.tripInventory.tripIdsSha256 || ids.size != profile.tripInventory.uniqueCount) return@forEach

            val htmlFile = store.resolveArtifact0528(inventory.captureId, profile.htmlFile)
            val htmlValid = verifySnapshotArtifact0528(htmlFile, profile.htmlBytes, profile.htmlSha256)
            val hrefs = if (htmlValid) {
                extractInventoryTripTargets0533(htmlFile!!.readText(Charsets.UTF_8), profile.finalUrl)
            } else {
                emptyMap()
            }
            ids.forEach { tripId ->
                planned += BlaBlaRidesExpectedTrip0533(
                    accountKey = profile.accountKey,
                    profileUuid = profileUuid,
                    inventoryStatus = profile.status,
                    expectedProfileTripCount = ids.size,
                    tripId = tripId,
                    tripHref = hrefs[tripId].orEmpty(),
                )
            }
        }

        val distinct = planned.distinctBy { it.profileUuid to it.tripId }
        return BlaBlaRidesEnrichmentPlan0533(
            inventoryComplete = inventoryComplete,
            expectedTripCount = expectedTotal,
            identifiedTripCount = distinct.size,
            unresolvedInventoryTripCount = (expectedTotal - distinct.size).coerceAtLeast(0),
            trips = distinct.sortedWith(compareBy({ it.profileUuid }, { it.tripId })),
        )
    }
}

internal class BlaBlaRidesEnrichmentStore0533(context: Context) {
    private val app = context.applicationContext
    private val snapshotStore = BlaBlaRidesSnapshotStore0526(app)

    fun startOrResume(
        inventory: BlaBlaRidesSnapshotManifest0526,
        plan: BlaBlaRidesEnrichmentPlan0533,
    ): BlaBlaRidesEnrichmentManifest0533 = synchronized(lock) {
        val existing = readUnlocked(inventory.captureId)
        val oldByIdentity = existing?.profiles.orEmpty().flatMap { it.rides }
            .associateBy { it.authenticatedProfileUuid to it.tripId }
        val grouped = plan.trips.groupBy { it.profileUuid }
        val profiles = inventory.profiles.mapNotNull { sourceProfile ->
            val uuid = BlaBlaRidesSnapshotStore0526.strongUuid(sourceProfile.authenticatedProfileUuid) ?: return@mapNotNull null
            val expected = grouped[uuid].orEmpty()
            BlaBlaRidesEnrichedProfile0533(
                accountKey = sourceProfile.accountKey,
                expectedProfileUuid = sourceProfile.expectedProfileUuid,
                authenticatedProfileUuid = uuid,
                inventoryStatus = sourceProfile.status,
                expectedTripCount = sourceProfile.tripInventory.uniqueCount,
                identifiedTripCount = expected.size,
                unresolvedInventoryTripCount = (sourceProfile.tripInventory.uniqueCount - expected.size).coerceAtLeast(0),
                rides = expected.map { item ->
                    val old = oldByIdentity[uuid to item.tripId]
                    if (old != null && old.inventoryHref == item.tripHref) old else BlaBlaRidesEnrichedRide0533(
                        captureId = inventory.captureId,
                        authenticatedProfileUuid = uuid,
                        tripId = item.tripId,
                        inventoryHref = item.tripHref,
                        inventoryHrefSha256 = item.tripHref.takeIf(String::isNotBlank)?.let(::sha256).orEmpty(),
                        lastError = if (item.tripHref.isBlank()) "INVENTORY_ADMIN_HREF_NOT_PROVEN" else "NOT_ATTEMPTED",
                    )
                },
            )
        }
        val initial = BlaBlaRidesEnrichmentManifest0533(
            captureId = inventory.captureId,
            inventorySchemaVersion = inventory.schemaVersion,
            inventoryResult = inventory.result,
            inventoryCompletedAt = inventory.completedAt,
            inventoryComplete = plan.inventoryComplete,
            startedAt = existing?.startedAt?.takeIf(String::isNotBlank) ?: Instant.now().toString(),
            expectedTripCount = plan.expectedTripCount,
            identifiedTripCount = plan.identifiedTripCount,
            unresolvedInventoryTripCount = plan.unresolvedInventoryTripCount,
            profiles = profiles,
        )
        writeManifest(finalizeEnrichmentCoverage0533(initial))
    }

    fun read(captureId: String): BlaBlaRidesEnrichmentManifest0533? = synchronized(lock) { readUnlocked(captureId) }

    fun updateRide(
        captureId: String,
        profileUuid: String,
        tripId: String,
        transform: (BlaBlaRidesEnrichedRide0533) -> BlaBlaRidesEnrichedRide0533,
    ): BlaBlaRidesEnrichmentManifest0533? = synchronized(lock) {
        val current = readUnlocked(captureId) ?: return@synchronized null
        var changed: BlaBlaRidesEnrichedRide0533? = null
        val profiles = current.profiles.map { profile ->
            if (profile.authenticatedProfileUuid != profileUuid) return@map profile
            profile.copy(rides = profile.rides.map { ride ->
                if (ride.tripId != tripId) ride else transform(ride).also { changed = it }
            })
        }
        val checkpointed = changed?.let { writeCheckpoint(it) }
        val finalProfiles = if (checkpointed == null) profiles else profiles.map { profile ->
            if (profile.authenticatedProfileUuid != profileUuid) profile else profile.copy(
                rides = profile.rides.map { ride -> if (ride.tripId == tripId) checkpointed else ride },
            )
        }
        val replacement = finalizeEnrichmentCoverage0533(current.copy(profiles = finalProfiles))
        writeManifest(replacement)
        replacement
    }

    fun finish(captureId: String): BlaBlaRidesEnrichmentManifest0533? = synchronized(lock) {
        val current = readUnlocked(captureId) ?: return@synchronized null
        val replacement = finalizeEnrichmentCoverage0533(current, Instant.now().toString())
        writeManifest(replacement)
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_RIDES_ENRICHMENT_COMPLETED_0533",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} expected=${replacement.expectedTripCount} terminal=${replacement.terminalTripCount} complete=${replacement.completeCount} partial=${replacement.partialCount} failed=${replacement.failedCount} pendingUnknown=${replacement.pendingUnknownCount} inventoryComplete=${replacement.inventoryComplete} enrichmentComplete=${replacement.enrichmentComplete}",
        )
        replacement
    }

    fun manifestPath(captureId: String): String = File(captureRoot(captureId), "enrichment-v2.json").absolutePath

    private fun writeCheckpoint(ride: BlaBlaRidesEnrichedRide0533): BlaBlaRidesEnrichedRide0533 {
        val dir = File(captureRoot(ride.captureId), "enrichment-v2/${ride.authenticatedProfileUuid}").canonicalFile
        val root = captureRoot(ride.captureId)
        require(dir.path.startsWith(root.path + File.separator)) { "Enrichment evidence escaped capture directory" }
        dir.mkdirs()
        require(canonicalTripIds0528(listOf(ride.tripId)) == listOf(ride.tripId)) { "Invalid tripId for checkpoint" }
        val target = File(dir, "${ride.tripId}.json")
        val payload = BlaBlaRidesTripCheckpoint0533(
            captureId = ride.captureId,
            authenticatedProfileUuid = ride.authenticatedProfileUuid,
            tripId = ride.tripId,
            status = ride.status,
            startedAt = ride.startedAt,
            completedAt = ride.completedAt,
            attemptCount = ride.attemptCount,
            lastError = ride.lastError,
            inventoryHref = ride.inventoryHref,
            trip = ride.trip,
            provenance = ride.provenance,
            sessionUpdatedAtMillis = ride.sessionUpdatedAtMillis,
            sourceAccessStatus = ride.sourceAccessStatus,
        )
        atomicWrite(target, ENRICHMENT_JSON_0533.encodeToString(BlaBlaRidesTripCheckpoint0533.serializer(), payload))
        val relative = target.canonicalFile.relativeTo(root).invariantSeparatorsPath
        return ride.copy(
            evidenceFile = relative,
            evidenceBytes = target.length(),
            evidenceSha256 = BlaBlaRidesSnapshotStore0526.sha256(target),
        )
    }

    private fun writeManifest(value: BlaBlaRidesEnrichmentManifest0533): BlaBlaRidesEnrichmentManifest0533 {
        val target = File(captureRoot(value.captureId), "enrichment-v2.json")
        atomicWrite(target, ENRICHMENT_JSON_0533.encodeToString(BlaBlaRidesEnrichmentManifest0533.serializer(), value))
        return value
    }

    private fun readUnlocked(captureId: String): BlaBlaRidesEnrichmentManifest0533? {
        val file = File(captureRoot(captureId), "enrichment-v2.json")
        if (!file.isFile) return null
        return runCatching {
            ENRICHMENT_JSON_0533.decodeFromString(BlaBlaRidesEnrichmentManifest0533.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun captureRoot(captureId: String): File =
        File(snapshotStore.manifestPath(captureId)).canonicalFile.parentFile
            ?: error("Capture root unavailable")

    private fun atomicWrite(target: File, raw: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(raw, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun sha256(value: String): String =
        BlaBlaRidesSnapshotStore0526.sha256(value.toByteArray(Charsets.UTF_8))

    companion object { private val lock = Any() }
}

internal object BlaBlaRidesEnrichmentCoordinator0533 {
    suspend fun enrichAll(
        context: Context,
        inventory: BlaBlaRidesSnapshotManifest0526,
        onProgress: (String) -> Unit = {},
    ): BlaBlaRidesEnrichmentManifest0533 {
        val app = context.applicationContext
        val plan = BlaBlaRidesEnrichmentPlanner0533.build(app, inventory)
        val store = BlaBlaRidesEnrichmentStore0533(app)
        val sessionStore = BlaBlaDynamicSessionStore(app)
        val snapshotStore = BlaBlaRidesSnapshotStore0526(app)
        val registry = BlaBlaDynamicAccountRegistry(app)
        var manifest = store.startOrResume(inventory, plan)
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_RIDES_ENRICHMENT_STARTED_0533",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(inventory.captureId)} inventoryComplete=${plan.inventoryComplete} expected=${plan.expectedTripCount} identified=${plan.identifiedTripCount} unresolved=${plan.unresolvedInventoryTripCount} exactTripId=true sequential=true",
        )

        var ordinal = 0
        val total = manifest.profiles.sumOf { it.rides.size }
        manifest.profiles.forEach { profile ->
            val account = registry.list().filter { account ->
                BlaBlaRidesSnapshotStore0526.strongUuid(account.profileUuid) == profile.authenticatedProfileUuid &&
                    snapshotStore.accountKey(account.id) == profile.accountKey
            }.singleOrNull()
            profile.rides.forEach { initialRide ->
                ordinal++
                var ride = store.read(inventory.captureId)
                    ?.profiles?.firstOrNull { it.authenticatedProfileUuid == profile.authenticatedProfileUuid }
                    ?.rides?.firstOrNull { it.tripId == initialRide.tripId }
                    ?: initialRide
                if (ride.status == BlaBlaRidesEnrichmentStatus0533.COMPLETE) {
                    onProgress("Enriquecimento $ordinal/$total • ${ride.tripId.take(12)} • já comprovado")
                    return@forEach
                }
                if (ride.inventoryHref.isBlank()) {
                    store.updateRide(inventory.captureId, profile.authenticatedProfileUuid, ride.tripId) {
                        it.copy(
                            status = BlaBlaRidesEnrichmentStatus0533.PENDING_UNKNOWN,
                            completedAt = Instant.now().toString(),
                            lastError = "INVENTORY_ADMIN_HREF_NOT_PROVEN",
                        )
                    }
                    onProgress("Enriquecimento $ordinal/$total • vínculo administrativo não comprovado")
                    return@forEach
                }
                if (account == null) {
                    store.updateRide(inventory.captureId, profile.authenticatedProfileUuid, ride.tripId) {
                        it.copy(
                            status = BlaBlaRidesEnrichmentStatus0533.FAILED,
                            completedAt = Instant.now().toString(),
                            lastError = "PROFILE_ACCOUNT_BINDING_FAILED",
                        )
                    }
                    return@forEach
                }

                var attemptsThisRun = 0
                while (attemptsThisRun < 2 && ride.status != BlaBlaRidesEnrichmentStatus0533.COMPLETE) {
                    attemptsThisRun++
                    val startedMillis = System.currentTimeMillis()
                    val startedAt = Instant.now().toString()
                    ride = store.updateRide(inventory.captureId, profile.authenticatedProfileUuid, ride.tripId) { previous ->
                        previous.copy(
                            status = BlaBlaRidesEnrichmentStatus0533.PENDING_UNKNOWN,
                            startedAt = startedAt,
                            completedAt = "",
                            attemptCount = previous.attemptCount + 1,
                            lastError = "ATTEMPT_IN_PROGRESS",
                        )
                    }?.profiles?.first { it.authenticatedProfileUuid == profile.authenticatedProfileUuid }
                        ?.rides?.first { it.tripId == ride.tripId } ?: ride
                    onProgress("Enriquecendo $ordinal/$total • tentativa ${ride.attemptCount} • ${ride.tripId.take(12)}")
                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_RIDE_ENRICHMENT_ATTEMPT_0533",
                        app.packageName,
                        "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(inventory.captureId)} profileUuid=${profile.authenticatedProfileUuid} tripId=${ride.tripId} attempt=${ride.attemptCount}",
                    )

                    val run = withTimeoutOrNull(TRIP_TIMEOUT_MS_0533) {
                        runExact(app, account, ride.tripId, ride.inventoryHref)
                    } ?: ExactRun0533(Activity.RESULT_CANCELED, Intent().putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "TIMEOUT"))
                    val failure = run.data.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407).orEmpty()
                    val session = sessionStore.read(account)
                    val exact = session?.trips.orEmpty().filter { trip ->
                        trip.profile_uuid.trim().equals(profile.authenticatedProfileUuid, ignoreCase = true) &&
                            trip.trip_id?.trim() == ride.tripId &&
                            BlaBlaCollectorUrlModule.tripId(trip.trip_href) == ride.tripId
                    }.singleOrNull()
                    val decision = classifyEnrichmentResult0533(
                        runSucceeded = run.resultCode == Activity.RESULT_OK,
                        runFailure = failure,
                        expectedProfileUuid = profile.authenticatedProfileUuid,
                        expectedTripId = ride.tripId,
                        attemptStartedAtMillis = startedMillis,
                        session = session,
                        trip = exact,
                    )
                    val tripHash = exact?.let { trip ->
                        BlaBlaRidesSnapshotStore0526.sha256(
                            ENRICHMENT_JSON_0533.encodeToString(BlaBlaCollectorTrip.serializer(), trip).toByteArray(Charsets.UTF_8),
                        )
                    }.orEmpty()
                    val provenance = buildList {
                        add("inventory:RIDE_LIST")
                        if (exact != null) add("detail:TRIP_DETAIL")
                        if (exact?.passengers?.isNotEmpty() == true || exact?.passenger_roster_complete == true) add("passenger:PASSENGER_OPEN")
                        if (exact?.passengers?.any { !it.phone.isNullOrBlank() } == true) add("passenger_contact:PASSENGER_CONTACT")
                        if (exact?.public_trip_href?.isNotBlank() == true) add("public_link:TRIP_PUBLIC_SHARE_OR_AUTHORITATIVE_SOURCE")
                    }
                    ride = store.updateRide(inventory.captureId, profile.authenticatedProfileUuid, ride.tripId) { previous ->
                        previous.copy(
                            status = decision.status,
                            completedAt = Instant.now().toString(),
                            lastError = decision.error,
                            trip = exact,
                            provenance = provenance,
                            tripPayloadSha256 = tripHash,
                            sessionUpdatedAtMillis = session?.updatedAtMillis ?: 0L,
                            sourceAccessStatus = session?.sourceAccessStatus0426?.name ?: "UNKNOWN",
                        )
                    }?.profiles?.first { it.authenticatedProfileUuid == profile.authenticatedProfileUuid }
                        ?.rides?.first { it.tripId == ride.tripId } ?: ride

                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_RIDE_ENRICHMENT_CHECKPOINT_0533",
                        app.packageName,
                        "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(inventory.captureId)} profileUuid=${profile.authenticatedProfileUuid} tripId=${ride.tripId} status=${ride.status} attempt=${ride.attemptCount} rosterComplete=${ride.trip?.passenger_roster_complete == true} evidenceSha256=${ride.evidenceSha256}",
                    )
                    if (!shouldRetryEnrichment0533(ride.status, ride.lastError)) break
                    if (attemptsThisRun < 2) delay(750L)
                }
                manifest = store.read(inventory.captureId) ?: manifest
            }
        }
        return store.finish(inventory.captureId) ?: manifest
    }

    private suspend fun runExact(
        context: Context,
        account: BlaBlaDynamicAccount,
        tripId: String,
        tripHref: String,
    ): ExactRun0533 = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            var controller: BlaBlaDynamicAccountSessionController0401? = null
            val payload = BlaBlaDynamicSessionIntents.syncExact(context, account, tripId, tripHref)
            controller = BlaBlaDynamicAccountSessionController0401(
                baseContext = context,
                launchIntent = payload,
                visualHost = null,
                finishHost = { resultCode, data ->
                    controller?.destroy("rides_enrichment_trip_terminal_0533")
                    if (continuation.isActive) continuation.resume(ExactRun0533(resultCode, data))
                },
            )
            continuation.invokeOnCancellation {
                controller?.destroy("rides_enrichment_trip_cancelled_0533")
            }
            controller?.start()
        }
    }

    private data class ExactRun0533(val resultCode: Int, val data: Intent)
    private const val TRIP_TIMEOUT_MS_0533 = 120_000L
}

internal data class BlaBlaRidesEnrichmentDownloadResult0533(
    val displayName: String,
    val relativeLocation: String,
    val bytes: Long,
)

internal object BlaBlaRidesEnrichmentDownload0533 {
    suspend fun download(
        context: Context,
        manifest: BlaBlaRidesEnrichmentManifest0533,
    ): BlaBlaRidesEnrichmentDownloadResult0533 = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Download automático requer Android 10 ou superior" }
        val verified = finalizeEnrichmentCoverage0533(manifest, manifest.completedAt)
        require(verified.completedAt.isNotBlank()) { "Enriquecimento ainda não terminou" }
        require(verified.terminalTripCount == verified.expectedTripCount) {
            "Nem todas as viagens possuem disposição terminal"
        }
        val raw = ENRICHMENT_JSON_0533.encodeToString(BlaBlaRidesEnrichmentManifest0533.serializer(), verified)
        require(raw.length <= 8 * 1024 * 1024) { "JSON enriquecido excede o limite permitido" }
        require(!raw.contains("authorization: bearer", ignoreCase = true) && !raw.contains("set-cookie", ignoreCase = true)) {
            "JSON enriquecido contém material de autenticação reutilizável"
        }
        val bytes = raw.toByteArray(Charsets.UTF_8)
        val displayName = "rota-certa-suas-viagens-enriquecidas-${BlaBlaRidesSnapshotStore0526.safeCaptureId(verified.captureId)}.json"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Rota Certa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android não disponibilizou destino em Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes); it.flush() }
                ?: error("Não foi possível gravar o JSON enriquecido")
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                .also { resolver.update(uri, it, null, null) }
            BlaBlaRidesEnrichmentDownloadResult0533(displayName, "$relativePath/$displayName", bytes.size.toLong())
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
