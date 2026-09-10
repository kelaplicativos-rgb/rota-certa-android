package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.net.URI
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Verifiable evidence layer for the authenticated multi-profile "Suas viagens" capture.
 *
 * It does not navigate, authenticate, synchronize TripStore or publish Agenda data. It only
 * canonicalizes the evidence already produced by the existing SESSION_IDENTITY/RIDE_LIST flow.
 */
@Serializable
internal data class BlaBlaRidesIdentityLocator0528(
    val host: String = "",
    val path: String = "",
    val extractedProfileUuid: String = "",
    val locatorSha256: String = "",
)

@Serializable
internal data class BlaBlaRidesIdentityEvidence0528(
    val source: String = "",
    val capturedAt: String = "",
    val evidenceType: String = "",
    val evidenceHashSha256: String = "",
    val accountKey: String = "",
    val locators: List<BlaBlaRidesIdentityLocator0528> = emptyList(),
)

@Serializable
internal data class BlaBlaRidesEndEvidence0528(
    val reason: String = "",
    val sentinel: String = "",
    val lastVisibleTripId: String = "",
    val finalScrollY: Int = 0,
    val finalScrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
    val loadingActive: Boolean = false,
    val mutationQuietMillis: Long = 0L,
    val noNewTripIterations: Int = 0,
    val observedAt: String = "",
)

@Serializable
internal data class BlaBlaRidesStabilizationEvidence0528(
    val requiredStableIterations: Int = 0,
    val observedStableIterations: Int = 0,
    val cardCounts: List<Int> = emptyList(),
    val tripSetFingerprintsSha256: List<String> = emptyList(),
    val tripSetSha256: String = "",
    val completionReason: String = "",
)

@Serializable
internal data class BlaBlaRidesTripInventory0528(
    val count: Int = 0,
    val uniqueCount: Int = 0,
    val duplicateCount: Int = 0,
    val explicitEmptyList: Boolean = false,
    val tripIdsSha256: String = "",
    val firstTripId: String = "",
    val lastTripId: String = "",
)

@Serializable
internal data class BlaBlaRidesCrossFormatConsistency0528(
    val htmlTripCount: Int = 0,
    val mhtmlTripCount: Int = 0,
    val htmlTripIdsSha256: String = "",
    val mhtmlTripIdsSha256: String = "",
    val sameTripSet: Boolean = false,
    val htmlOnly: List<String> = emptyList(),
    val mhtmlOnly: List<String> = emptyList(),
)

@Serializable
internal data class BlaBlaRidesRideDateRange0528(
    val earliest: String = "",
    val latest: String = "",
)

@Serializable
internal data class BlaBlaRidesIdentityJson0528(
    val schemaVersion: String = "blablacar-rides-identity-v1",
    val accountKey: String,
    val expectedProfileUuid: String,
    val authenticatedProfileUuid: String,
    val confirmedAt: String,
    val source: String = "SESSION_IDENTITY",
    val evidenceType: String = "AUTHENTICATED_PROFILE_UUID_LINK",
    val locators: List<BlaBlaRidesIdentityLocator0528> = emptyList(),
)

@Serializable
internal data class BlaBlaRidesIndexJson0528(
    val schemaVersion: String = "blablacar-rides-index-v1",
    val profileUuid: String,
    val capturedAt: String,
    val tripIds: List<String>,
    val tripIdsSha256: String,
    val duplicateCount: Int,
    val rideDateRange: BlaBlaRidesRideDateRange0528 = BlaBlaRidesRideDateRange0528(),
)

internal data class BlaBlaRidesArtifactChecks0528(
    val identityFileValid: Boolean = false,
    val identityPayloadValid: Boolean = false,
    val ridesIndexFileValid: Boolean = false,
    val ridesIndexPayloadValid: Boolean = false,
    val htmlFileValid: Boolean = false,
    val mhtmlFileValid: Boolean = false,
    val crossFormatPayloadValid: Boolean = false,
)

private val STABLE_RIDE_ID_0528 = Regex("^[A-Za-z0-9_-]{8,160}$")
private val ADMIN_RIDE_LINK_0528 = Regex(
    """(?i)/rides/offer/?\?(?:[^"'<>\s]{0,500}?&)?id=([A-Za-z0-9_-]{8,160})""",
)

internal fun canonicalTripIds0528(rawIds: Iterable<String>): List<String> =
    rawIds
        .map { it.trim() }
        .filter { STABLE_RIDE_ID_0528.matches(it) }
        .distinct()
        .sorted()

internal fun tripSetSha2560528(rawIds: Iterable<String>): String {
    val canonical = canonicalTripIds0528(rawIds)
    val payload = buildString {
        append("blablacar-rides-trip-set-v1\n")
        canonical.forEach { append(it).append('\n') }
    }
    return BlaBlaRidesSnapshotStore0526.sha256(payload.toByteArray(Charsets.UTF_8))
}

internal fun buildTripInventory0528(
    rawIdsInUiOrder: List<String>,
    explicitEmptyList: Boolean,
): BlaBlaRidesTripInventory0528 {
    val accepted = rawIdsInUiOrder.map { it.trim() }.filter { STABLE_RIDE_ID_0528.matches(it) }
    val canonical = canonicalTripIds0528(accepted)
    return BlaBlaRidesTripInventory0528(
        count = accepted.size,
        uniqueCount = canonical.size,
        duplicateCount = (accepted.size - canonical.size).coerceAtLeast(0),
        explicitEmptyList = explicitEmptyList,
        tripIdsSha256 = tripSetSha2560528(canonical),
        firstTripId = canonical.firstOrNull().orEmpty(),
        lastTripId = canonical.lastOrNull().orEmpty(),
    )
}

internal fun extractAdministrativeTripIds0528(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val normalized = raw
        .replace("=\r\n", "")
        .replace("=\n", "")
        .replace(Regex("=3D", RegexOption.IGNORE_CASE), "=")
        .replace(Regex("%3D", RegexOption.IGNORE_CASE), "=")
        .replace(Regex("\\\\u003[dD]"), "=")
        .replace(Regex("\\\\u0026", RegexOption.IGNORE_CASE), "&")
        .replace("&amp;", "&")
    return canonicalTripIds0528(
        ADMIN_RIDE_LINK_0528.findAll(normalized).map { it.groupValues[1] }.toList(),
    )
}

internal fun compareHtmlMhtmlTripSets0528(
    htmlRaw: String,
    mhtmlRaw: String,
): BlaBlaRidesCrossFormatConsistency0528 {
    val htmlIds = extractAdministrativeTripIds0528(htmlRaw)
    val mhtmlIds = extractAdministrativeTripIds0528(mhtmlRaw)
    val htmlSet = htmlIds.toSet()
    val mhtmlSet = mhtmlIds.toSet()
    return BlaBlaRidesCrossFormatConsistency0528(
        htmlTripCount = htmlIds.size,
        mhtmlTripCount = mhtmlIds.size,
        htmlTripIdsSha256 = tripSetSha2560528(htmlIds),
        mhtmlTripIdsSha256 = tripSetSha2560528(mhtmlIds),
        sameTripSet = htmlSet == mhtmlSet,
        htmlOnly = (htmlSet - mhtmlSet).sorted(),
        mhtmlOnly = (mhtmlSet - htmlSet).sorted(),
    )
}

internal fun buildRideDateRange0528(dates: Collection<LocalDate>): BlaBlaRidesRideDateRange0528 {
    val ordered = dates.distinct().sorted()
    return BlaBlaRidesRideDateRange0528(
        earliest = ordered.firstOrNull()?.toString().orEmpty(),
        latest = ordered.lastOrNull()?.toString().orEmpty(),
    )
}

internal fun sanitizedIdentityLocators0528(
    profileLinks: List<String>,
    authenticatedProfileUuid: String,
): List<BlaBlaRidesIdentityLocator0528> {
    val expected = BlaBlaRidesSnapshotStore0526.strongUuid(authenticatedProfileUuid) ?: return emptyList()
    return profileLinks.mapNotNull { raw ->
        val absolute = BlaBlaCollectorUrlModule.absolute(raw)
        if (!BlaBlaCollectorUrlModule.isAllowed(absolute)) return@mapNotNull null
        if (!absolute.contains(expected, ignoreCase = true)) return@mapNotNull null
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return@mapNotNull null
        val path = uri.rawPath.orEmpty().ifBlank { "/" }.take(500)
        val identityShape = Regex("(profile|user|member)", RegexOption.IGNORE_CASE)
        if (!identityShape.containsMatchIn(path) && !identityShape.containsMatchIn(absolute)) return@mapNotNull null
        BlaBlaRidesIdentityLocator0528(
            host = uri.host.orEmpty().lowercase().take(120),
            path = path,
            extractedProfileUuid = expected,
            locatorSha256 = BlaBlaRidesSnapshotStore0526.sha256(absolute.toByteArray(Charsets.UTF_8)),
        )
    }.distinctBy { "${it.host}|${it.path}|${it.extractedProfileUuid}|${it.locatorSha256}" }
        .sortedWith(compareBy({ it.host }, { it.path }, { it.locatorSha256 }))
}

private val FORENSIC_JSON_0528 = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

private fun BlaBlaRidesSnapshotStore0526.captureRoot0528(captureId: String): File =
    File(manifestPath(captureId)).canonicalFile.parentFile
        ?: error("Capture root unavailable")

private fun BlaBlaRidesSnapshotStore0526.profileEvidenceDir0528(
    captureId: String,
    profileUuid: String,
): File {
    val uuid = BlaBlaRidesSnapshotStore0526.strongUuid(profileUuid)
        ?: error("Strong profile UUID required")
    val root = captureRoot0528(captureId)
    val dir = File(root, uuid).canonicalFile
    require(dir.path.startsWith(root.path + File.separator)) { "Evidence escaped capture directory" }
    dir.mkdirs()
    return dir
}

internal fun BlaBlaRidesSnapshotStore0526.writeIdentityJson0528(
    captureId: String,
    profileUuid: String,
    payload: BlaBlaRidesIdentityJson0528,
): BlaBlaRidesSnapshotFile0526 {
    val target = File(profileEvidenceDir0528(captureId, profileUuid), "identity.json")
    val temp = File(target.parentFile, "identity.json.tmp")
    temp.writeText(FORENSIC_JSON_0528.encodeToString(BlaBlaRidesIdentityJson0528.serializer(), payload), Charsets.UTF_8)
    if (!temp.renameTo(target)) {
        target.writeBytes(temp.readBytes())
        temp.delete()
    }
    return evidence(captureId, target)
}

internal fun BlaBlaRidesSnapshotStore0526.writeRidesIndexJson0528(
    captureId: String,
    profileUuid: String,
    payload: BlaBlaRidesIndexJson0528,
): BlaBlaRidesSnapshotFile0526 {
    val target = File(profileEvidenceDir0528(captureId, profileUuid), "rides-index.json")
    val temp = File(target.parentFile, "rides-index.json.tmp")
    temp.writeText(FORENSIC_JSON_0528.encodeToString(BlaBlaRidesIndexJson0528.serializer(), payload), Charsets.UTF_8)
    if (!temp.renameTo(target)) {
        target.writeBytes(temp.readBytes())
        temp.delete()
    }
    return evidence(captureId, target)
}

internal fun BlaBlaRidesSnapshotStore0526.resolveArtifact0528(
    captureId: String,
    relativePath: String,
): File? {
    if (relativePath.isBlank()) return null
    val root = captureRoot0528(captureId)
    val candidate = File(root, relativePath).canonicalFile
    if (!candidate.path.startsWith(root.path + File.separator) || !candidate.isFile) return null
    return candidate
}

internal fun verifySnapshotArtifact0528(
    file: File?,
    expectedBytes: Long,
    expectedSha256: String,
): Boolean {
    if (file == null || !file.isFile || expectedBytes <= 0L || expectedSha256.isBlank()) return false
    if (file.length() != expectedBytes) return false
    return runCatching {
        BlaBlaRidesSnapshotStore0526.sha256(file).equals(expectedSha256, ignoreCase = true)
    }.getOrDefault(false)
}

internal fun forensicCompletionError0528(
    profile: BlaBlaRidesSnapshotProfile0526,
    checks: BlaBlaRidesArtifactChecks0528,
): String? {
    val expected = BlaBlaRidesSnapshotStore0526.strongUuid(profile.expectedProfileUuid)
        ?: return "EXPECTED_PROFILE_UUID_MISSING"
    val authenticated = BlaBlaRidesSnapshotStore0526.strongUuid(profile.authenticatedProfileUuid)
        ?: return "AUTHENTICATED_PROFILE_UUID_MISSING"
    if (expected != authenticated) return "PROFILE_UUID_MISMATCH"
    if (!profile.identityConfirmed) return "IDENTITY_NOT_CONFIRMED"
    if (profile.identityEvidence.source != "SESSION_IDENTITY" ||
        profile.identityEvidence.capturedAt.isBlank() ||
        profile.identityEvidence.evidenceType.isBlank() ||
        profile.identityEvidence.evidenceHashSha256.isBlank() ||
        profile.identityEvidence.accountKey != profile.accountKey ||
        profile.identityEvidence.locators.isEmpty()
    ) return "IDENTITY_EVIDENCE_MISSING"
    if (!checks.identityFileValid) return "IDENTITY_ARTIFACT_INVALID"
    if (!checks.identityPayloadValid) return "IDENTITY_PAYLOAD_INVALID"
    if (!BlaBlaCollectorUrlModule.ridesPageMatches(profile.finalUrl)) return "NOT_ON_RIDES_PAGE"
    if (!profile.reachedEnd || !profile.endEvidence.atBottom ||
        profile.endEvidence.reason.isBlank() || profile.endEvidence.observedAt.isBlank()
    ) return "END_NOT_PROVEN"
    if (!profile.stabilized ||
        profile.stabilizationEvidence.requiredStableIterations < 2 ||
        profile.stabilizationEvidence.observedStableIterations <
            profile.stabilizationEvidence.requiredStableIterations ||
        profile.stabilizationEvidence.tripSetSha256.isBlank() ||
        profile.stabilizationEvidence.completionReason.isBlank()
    ) return "STABILIZATION_NOT_PROVEN"
    if (profile.tripInventory.duplicateCount != 0) return "DUPLICATE_TRIP_IDS"
    if (profile.tripInventory.uniqueCount == 0 && !profile.tripInventory.explicitEmptyList) {
        return "TRIP_IDS_MISSING"
    }
    if (profile.cardCountFinal != profile.tripInventory.uniqueCount) return "CARD_INVENTORY_COUNT_MISMATCH"
    if (profile.tripInventory.tripIdsSha256 != profile.stabilizationEvidence.tripSetSha256) {
        return "STABILIZATION_INVENTORY_HASH_MISMATCH"
    }
    if (!checks.ridesIndexFileValid) return "RIDES_INDEX_ARTIFACT_INVALID"
    if (!checks.ridesIndexPayloadValid) return "RIDES_INDEX_PAYLOAD_INVALID"
    if (!profile.htmlCaptured || profile.htmlFile.isBlank() || profile.htmlSha256.isBlank()) {
        return "HTML_EVIDENCE_MISSING"
    }
    if (!checks.htmlFileValid) return "HTML_ARTIFACT_INVALID"
    if (profile.mhtmlSupported && (!profile.mhtmlCaptured || profile.mhtmlFile.isBlank() || profile.mhtmlSha256.isBlank())) {
        return "MHTML_EVIDENCE_MISSING"
    }
    if (profile.mhtmlSupported && !checks.mhtmlFileValid) return "MHTML_ARTIFACT_INVALID"
    if (profile.mhtmlSupported) {
        val cross = profile.crossFormatConsistency
        if (!cross.sameTripSet || cross.htmlOnly.isNotEmpty() || cross.mhtmlOnly.isNotEmpty()) {
            return "HTML_MHTML_TRIP_SET_MISMATCH"
        }
        if (cross.htmlTripCount != profile.tripInventory.uniqueCount ||
            cross.mhtmlTripCount != profile.tripInventory.uniqueCount ||
            cross.htmlTripIdsSha256 != profile.tripInventory.tripIdsSha256 ||
            cross.mhtmlTripIdsSha256 != profile.tripInventory.tripIdsSha256
        ) return "HTML_MHTML_INVENTORY_MISMATCH"
        if (!checks.crossFormatPayloadValid) return "HTML_MHTML_COMPARISON_INVALID"
    }
    return null
}

internal fun BlaBlaRidesSnapshotStore0526.validateProfileForComplete0528(
    captureId: String,
    profile: BlaBlaRidesSnapshotProfile0526,
): String? {
    val identityFile = resolveArtifact0528(captureId, profile.identityFile)
    val ridesIndexFile = resolveArtifact0528(captureId, profile.ridesIndexFile)
    val htmlFile = resolveArtifact0528(captureId, profile.htmlFile)
    val mhtmlFile = resolveArtifact0528(captureId, profile.mhtmlFile)

    val identityFileValid = verifySnapshotArtifact0528(
        identityFile,
        profile.identityBytes,
        profile.identitySha256,
    )
    val ridesIndexFileValid = verifySnapshotArtifact0528(
        ridesIndexFile,
        profile.ridesIndexBytes,
        profile.ridesIndexSha256,
    )
    val htmlFileValid = verifySnapshotArtifact0528(htmlFile, profile.htmlBytes, profile.htmlSha256)
    val mhtmlFileValid = if (profile.mhtmlSupported) {
        verifySnapshotArtifact0528(mhtmlFile, profile.mhtmlBytes, profile.mhtmlSha256)
    } else {
        true
    }

    val identityPayloadValid = if (identityFileValid) {
        runCatching {
            val payload = FORENSIC_JSON_0528.decodeFromString(
                BlaBlaRidesIdentityJson0528.serializer(),
                identityFile!!.readText(Charsets.UTF_8),
            )
            payload.accountKey == profile.accountKey &&
                payload.expectedProfileUuid == profile.expectedProfileUuid &&
                payload.authenticatedProfileUuid == profile.authenticatedProfileUuid &&
                payload.confirmedAt == profile.identityEvidence.capturedAt &&
                payload.source == profile.identityEvidence.source &&
                payload.evidenceType == profile.identityEvidence.evidenceType &&
                payload.locators == profile.identityEvidence.locators
        }.getOrDefault(false)
    } else false

    val indexPayload = if (ridesIndexFileValid) {
        runCatching {
            FORENSIC_JSON_0528.decodeFromString(
                BlaBlaRidesIndexJson0528.serializer(),
                ridesIndexFile!!.readText(Charsets.UTF_8),
            )
        }.getOrNull()
    } else null
    val indexCanonicalIds = canonicalTripIds0528(indexPayload?.tripIds.orEmpty())
    val ridesIndexPayloadValid = indexPayload != null &&
        indexPayload.profileUuid == profile.authenticatedProfileUuid &&
        indexPayload.tripIds == indexCanonicalIds &&
        indexPayload.tripIdsSha256 == tripSetSha2560528(indexCanonicalIds) &&
        indexPayload.tripIdsSha256 == profile.tripInventory.tripIdsSha256 &&
        indexPayload.tripIds.size == profile.tripInventory.uniqueCount &&
        indexPayload.duplicateCount == profile.tripInventory.duplicateCount &&
        indexPayload.rideDateRange == profile.rideDateRange

    var actualCross: BlaBlaRidesCrossFormatConsistency0528? = null
    if (htmlFileValid && mhtmlFileValid && htmlFile != null && mhtmlFile != null) {
        actualCross = runCatching {
            compareHtmlMhtmlTripSets0528(
                htmlRaw = htmlFile.readText(Charsets.UTF_8),
                mhtmlRaw = mhtmlFile.readBytes().toString(Charsets.ISO_8859_1),
            )
        }.getOrNull()
    }
    val crossFormatPayloadValid = if (profile.mhtmlSupported) {
        actualCross != null &&
            actualCross == profile.crossFormatConsistency &&
            actualCross.htmlTripIdsSha256 == profile.tripInventory.tripIdsSha256 &&
            actualCross.mhtmlTripIdsSha256 == profile.tripInventory.tripIdsSha256 &&
            indexCanonicalIds == extractAdministrativeTripIds0528(htmlFile?.readText(Charsets.UTF_8).orEmpty())
    } else true

    return forensicCompletionError0528(
        profile = profile,
        checks = BlaBlaRidesArtifactChecks0528(
            identityFileValid = identityFileValid,
            identityPayloadValid = identityPayloadValid,
            ridesIndexFileValid = ridesIndexFileValid,
            ridesIndexPayloadValid = ridesIndexPayloadValid,
            htmlFileValid = htmlFileValid,
            mhtmlFileValid = mhtmlFileValid,
            crossFormatPayloadValid = crossFormatPayloadValid,
        ),
    )
}
