package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaRidesForensicEvidence0528Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"
    private val otherProfileUuid = "175a7068-50d8-40c3-a27a-214b9c6e0461"
    private val tripA = "01a0359e-de23-78ab-ab26-6cc973c5c3d1"
    private val tripB = "01a058be-73c8-7845-9ad2-076aaef9883c"

    @Test
    fun identityExpectedEqualsAuthenticatedCanCompleteWhenEvidenceExists() {
        assertNull(forensicCompletionError0528(validProfile(), validChecks()))
    }

    @Test
    fun identityMismatchFailsClosed() {
        assertEquals(
            "PROFILE_UUID_MISMATCH",
            forensicCompletionError0528(
                validProfile().copy(authenticatedProfileUuid = otherProfileUuid),
                validChecks(),
            ),
        )
    }

    @Test
    fun authenticatedUuidMissingFailsClosed() {
        assertEquals(
            "AUTHENTICATED_PROFILE_UUID_MISSING",
            forensicCompletionError0528(
                validProfile().copy(authenticatedProfileUuid = ""),
                validChecks(),
            ),
        )
    }

    @Test
    fun missingIdentityEvidenceNeverCompletes() {
        assertEquals(
            "IDENTITY_EVIDENCE_MISSING",
            forensicCompletionError0528(
                validProfile().copy(identityEvidence = BlaBlaRidesIdentityEvidence0528()),
                validChecks(),
            ),
        )
    }

    @Test
    fun inventoryCountsDuplicatesAndAllowsExplicitEmptyEvidence() {
        val duplicate = buildTripInventory0528(listOf(tripA, tripA, tripB), explicitEmptyList = false)
        assertEquals(3, duplicate.count)
        assertEquals(2, duplicate.uniqueCount)
        assertEquals(1, duplicate.duplicateCount)

        val empty = buildTripInventory0528(emptyList(), explicitEmptyList = true)
        assertEquals(0, empty.count)
        assertEquals(0, empty.uniqueCount)
        assertEquals(0, empty.duplicateCount)
        assertTrue(empty.explicitEmptyList)
        assertTrue(empty.tripIdsSha256.isNotBlank())
    }

    @Test
    fun fingerprintIsOrderIndependentButMembershipSensitive() {
        val first = tripSetSha2560528(listOf(tripA, tripB))
        val reordered = tripSetSha2560528(listOf(tripB, tripA, tripA))
        val changed = tripSetSha2560528(listOf(tripA, "third-trip-00000001"))

        assertEquals(first, reordered)
        assertNotEquals(first, changed)
    }

    @Test
    fun equalCardCountWithDifferentIdsDoesNotStabilize() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(
            startedAtMillis = 0L,
            maxNoProgressCycles = 20,
        )
        val firstSet = tripSetSha2560528(listOf(tripA, tripB))
        val secondSet = tripSetSha2560528(listOf(tripA, "third-trip-00000001"))

        val first = stabilizer.observe(stableObs(firstSet), nowMillis = 2_000L)
        val changed = stabilizer.observe(stableObs(secondSet), nowMillis = 4_000L)
        val sameOnce = stabilizer.observe(stableObs(secondSet), nowMillis = 6_000L)

        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, first.action)
        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, changed.action)
        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, sameOnce.action)
        assertEquals(2, sameOnce.stablePasses)
    }

    @Test
    fun identicalIdsForRequiredCyclesConfirmStabilization() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(
            startedAtMillis = 0L,
            maxNoProgressCycles = 20,
        )
        val set = tripSetSha2560528(listOf(tripA, tripB))

        assertEquals(
            BlaBlaRidesSnapshotAction0526.WAIT,
            stabilizer.observe(stableObs(set), nowMillis = 2_000L).action,
        )
        assertEquals(
            BlaBlaRidesSnapshotAction0526.WAIT,
            stabilizer.observe(stableObs(set), nowMillis = 4_000L).action,
        )
        val complete = stabilizer.observe(stableObs(set), nowMillis = 6_000L)

        assertEquals(BlaBlaRidesSnapshotAction0526.CAPTURE, complete.action)
        assertEquals(stabilizer.requiredStableIterations, complete.stablePasses)
        assertEquals(set, stabilizer.finalTripSetSha256)
    }

    @Test
    fun finalizationGateAllowsOnlyOneTerminalCaptureUntilReset() {
        val gate = BlaBlaRidesSnapshotFinalizationGate0529()
        assertFalse(gate.inFlight)
        assertTrue(gate.tryAcquire())
        assertTrue(gate.inFlight)
        assertFalse(gate.tryAcquire())
        gate.reset()
        assertFalse(gate.inFlight)
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun stabilizationProofIsIndependentFromMhtmlOutcome() {
        val inventory = buildTripInventory0528(listOf(tripA, tripB), explicitEmptyList = false)
        val proven = BlaBlaRidesStabilizationEvidence0528(
            requiredStableIterations = 3,
            observedStableIterations = 4,
            tripSetSha256 = inventory.tripIdsSha256,
            completionReason = "TRIP_ID_SET_UNCHANGED_AT_BOTTOM",
        )
        assertTrue(ridesStabilizationProven0529(proven))
        assertFalse(ridesStabilizationProven0529(proven.copy(observedStableIterations = 2)))
    }

    @Test
    fun htmlAndQuotedPrintableMhtmlWithSameTripsAreConsistent() {
        val html = """
            <a href="https://www.blablacar.com.br/rides/offer?id=$tripA">A</a>
            <a href="/rides/offer?id=$tripB">B</a>
        """.trimIndent()
        val mhtml = """
            Content-Type: text/html
            Content-Transfer-Encoding: quoted-printable

            <a href=3D"https://www.blablacar.com.br/rides/offer?id=3D$tripB">B</a>
            <a href=3D"https://www.blablacar.com.br/rides/offer?id=3D$tripA">A</a>
        """.trimIndent()

        val result = compareHtmlMhtmlTripSets0528(html, mhtml)
        assertTrue(result.sameTripSet)
        assertEquals(2, result.htmlTripCount)
        assertEquals(2, result.mhtmlTripCount)
        assertTrue(result.htmlOnly.isEmpty())
        assertTrue(result.mhtmlOnly.isEmpty())
    }

    @Test
    fun htmlOnlyTripMakesCrossFormatInconsistent() {
        val html = """
            <a href="/rides/offer?id=$tripA">A</a>
            <a href="/rides/offer?id=$tripB">B</a>
        """.trimIndent()
        val mhtml = "<a href=3D\"/rides/offer?id=3D$tripA\">A</a>"

        val result = compareHtmlMhtmlTripSets0528(html, mhtml)
        assertFalse(result.sameTripSet)
        assertEquals(listOf(tripB), result.htmlOnly)
        assertTrue(result.mhtmlOnly.isEmpty())
    }

    @Test
    fun mhtmlOnlyTripMakesCrossFormatInconsistent() {
        val html = "<a href=\"/rides/offer?id=$tripA\">A</a>"
        val mhtml = """
            <a href=3D"/rides/offer?id=3D$tripA">A</a>
            <a href=3D"/rides/offer?id=3D$tripB">B</a>
        """.trimIndent()

        val result = compareHtmlMhtmlTripSets0528(html, mhtml)
        assertFalse(result.sameTripSet)
        assertEquals(listOf(tripB), result.mhtmlOnly)
        assertTrue(result.htmlOnly.isEmpty())
    }

    @Test
    fun base64MhtmlHtmlPartIsDecodedForIndependentTripSetVerification() {
        val html = "<a href=\"/rides/offer?id=$tripA\">A</a>"
        val encoded = java.util.Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
        val mhtml = """
            MIME-Version: 1.0
            Content-Type: multipart/related; boundary=abc

            --abc
            Content-Type: text/html
            Content-Transfer-Encoding: base64

            $encoded
            --abc--
        """.trimIndent()

        val result = compareHtmlMhtmlTripSets0528(html, mhtml)
        assertTrue(result.sameTripSet)
        assertEquals(1, result.htmlTripCount)
        assertEquals(1, result.mhtmlTripCount)
    }

    @Test
    fun reusableSecretMarkersAreRejectedWithoutLoggingSecretValues() {
        assertEquals(
            "AUTHORIZATION_BEARER",
            sensitiveArtifactMarker0528("Authorization: Bearer abcdefghijklmnopqrstuvwxyz"),
        )
        assertEquals(
            "SET_COOKIE_HEADER",
            sensitiveArtifactMarker0528("Set-Cookie: session=secret-value; Secure"),
        )
        assertEquals(
            "REFRESH_TOKEN",
            sensitiveArtifactMarker0528("""{"refresh_token":"reusable-secret-value"}"""),
        )
        assertNull(sensitiveArtifactMarker0528("cookie preferences and authorization policy text"))
    }

    @Test
    fun hashMismatchAndMissingFileAreRejected() {
        val file = File.createTempFile("rides-0528", ".txt")
        try {
            file.writeText("evidence")
            val good = BlaBlaRidesSnapshotStore0526.sha256(file)
            assertTrue(verifySnapshotArtifact0528(file, file.length(), good))
            assertFalse(verifySnapshotArtifact0528(file, file.length(), "0".repeat(64)))
            assertFalse(verifySnapshotArtifact0528(null, file.length(), good))
        } finally {
            file.delete()
        }
    }

    @Test
    fun manifestGateRejectsUnprovenEndStabilityAndCrossFormat() {
        assertEquals(
            "END_NOT_PROVEN",
            forensicCompletionError0528(
                validProfile().copy(reachedEnd = false),
                validChecks(),
            ),
        )
        assertEquals(
            "STABILIZATION_NOT_PROVEN",
            forensicCompletionError0528(
                validProfile().copy(stabilized = false),
                validChecks(),
            ),
        )
        assertEquals(
            "HTML_MHTML_TRIP_SET_MISMATCH",
            forensicCompletionError0528(
                validProfile().copy(
                    crossFormatConsistency = validProfile().crossFormatConsistency.copy(
                        sameTripSet = false,
                        htmlOnly = listOf(tripB),
                    ),
                ),
                validChecks(),
            ),
        )
    }

    @Test
    fun dateRangeRecordsObservedExtremesWithoutFiltering() {
        val range = buildRideDateRange0528(
            listOf(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2027, 2, 3),
                LocalDate.of(2026, 8, 1),
            ),
        )
        assertEquals("2026-08-01", range.earliest)
        assertEquals("2027-02-03", range.latest)
    }

    @Test
    fun reusableSecretMaterialAndMissingSecurityProofRejectComplete() {
        assertEquals(
            "REUSABLE_AUTH_MATERIAL_DETECTED",
            forensicCompletionError0528(
                validProfile(),
                validChecks().copy(securityArtifactsValid = false),
            ),
        )
        assertEquals(
            "SECURITY_EVIDENCE_MISSING",
            forensicCompletionError0528(
                validProfile().copy(securityEvidence = BlaBlaRidesArtifactSecurityEvidence0528()),
                validChecks(),
            ),
        )
        assertEquals(
            "AUTHORIZATION_BEARER",
            sensitiveArtifactMarker0528("Authorization: Bearer abcdefghijklmnop"),
        )
        assertEquals(
            "REFRESH_TOKEN",
            sensitiveArtifactMarker0528("{\"refresh_token\":\"secretsecret\"}"),
        )
        assertNull(sensitiveArtifactMarker0528("<a href=\"/rides/offer?id=$tripA\">ride</a>"))
    }

    @Test
    fun forensicIdentityArtifactContainsNoReusableSessionFieldsBySchema() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesForensicEvidence0528.kt",
        ).readText()
        val identityStart = source.indexOf("data class BlaBlaRidesIdentityJson0528")
        val identityEnd = source.indexOf("data class BlaBlaRidesIndexJson0528", identityStart)
        val identitySchema = source.substring(identityStart, identityEnd).lowercase()

        for (forbidden in listOf(
            "cookie",
            "authorization",
            "access_token",
            "refreshtoken",
            "refresh_token",
            "password",
            "credential",
        )) {
            assertFalse(identitySchema.contains(forbidden), forbidden)
        }
    }

    private fun stableObs(setSha256: String) = BlaBlaRidesSnapshotObservation0526(
        cardCount = 2,
        scrollY = 1_000,
        scrollHeight = 1_500,
        viewportHeight = 600,
        atBottom = true,
        loadingActive = false,
        lastMutationAgeMs = 2_000,
        explicitEmptyList = false,
        tripSetSha256 = setSha256,
    )

    private fun validProfile(): BlaBlaRidesSnapshotProfile0526 {
        val inventory = buildTripInventory0528(listOf(tripA, tripB), explicitEmptyList = false)
        val locator = BlaBlaRidesIdentityLocator0528(
            host = "www.blablacar.com.br",
            path = "/member/$profileUuid",
            extractedProfileUuid = profileUuid,
            locatorSha256 = "a".repeat(64),
        )
        val identitySha = "b".repeat(64)
        return BlaBlaRidesSnapshotProfile0526(
            accountKey = "account-key-0528",
            expectedProfileUuid = profileUuid,
            authenticatedProfileUuid = profileUuid,
            identityConfirmed = true,
            identityEvidence = BlaBlaRidesIdentityEvidence0528(
                source = "SESSION_IDENTITY",
                capturedAt = "2026-09-10T00:00:00Z",
                evidenceType = "AUTHENTICATED_PROFILE_UUID_LINK",
                evidenceHashSha256 = identitySha,
                accountKey = "account-key-0528",
                locators = listOf(locator),
            ),
            identityFile = "$profileUuid/identity.json",
            identityBytes = 100,
            identitySha256 = identitySha,
            finalUrl = "https://www.blablacar.com.br/rides",
            cardCountFinal = 2,
            reachedEnd = true,
            endEvidence = BlaBlaRidesEndEvidence0528(
                reason = "BOTTOM_POSITION_TRIP_SET_AND_SCROLL_HEIGHT_STABLE",
                lastVisibleTripId = tripB,
                finalScrollY = 1_000,
                finalScrollHeight = 1_500,
                viewportHeight = 600,
                atBottom = true,
                loadingActive = false,
                mutationQuietMillis = 2_000,
                noNewTripIterations = 2,
                observedAt = "2026-09-10T00:01:00Z",
            ),
            stabilized = true,
            stabilizationEvidence = BlaBlaRidesStabilizationEvidence0528(
                requiredStableIterations = 3,
                observedStableIterations = 3,
                cardCounts = listOf(2, 2, 2),
                tripSetFingerprintsSha256 = List(3) { inventory.tripIdsSha256 },
                tripSetSha256 = inventory.tripIdsSha256,
                completionReason = "TRIP_ID_SET_UNCHANGED_AT_BOTTOM",
            ),
            tripInventory = inventory,
            ridesIndexFile = "$profileUuid/rides-index.json",
            ridesIndexBytes = 100,
            ridesIndexSha256 = "c".repeat(64),
            securityEvidence = BlaBlaRidesArtifactSecurityEvidence0528(
                scannedAt = "2026-09-10T00:02:00Z",
                htmlScanned = true,
                mhtmlScanned = true,
                identitySchemaMinimal = true,
                ridesIndexSchemaMinimal = true,
                reusableSecretMarkersDetected = 0,
                result = "PASS",
            ),
            crossFormatConsistency = BlaBlaRidesCrossFormatConsistency0528(
                htmlTripCount = 2,
                mhtmlTripCount = 2,
                htmlTripIdsSha256 = inventory.tripIdsSha256,
                mhtmlTripIdsSha256 = inventory.tripIdsSha256,
                sameTripSet = true,
            ),
            htmlCaptured = true,
            mhtmlSupported = true,
            mhtmlCaptured = true,
            htmlFile = "$profileUuid/suas-viagens.html",
            mhtmlFile = "$profileUuid/suas-viagens.mhtml",
            htmlBytes = 100,
            mhtmlBytes = 100,
            htmlSha256 = "d".repeat(64),
            mhtmlSha256 = "e".repeat(64),
        )
    }

    private fun validChecks() = BlaBlaRidesArtifactChecks0528(
        identityFileValid = true,
        identityPayloadValid = true,
        ridesIndexFileValid = true,
        ridesIndexPayloadValid = true,
        htmlFileValid = true,
        mhtmlFileValid = true,
        crossFormatPayloadValid = true,
        securityArtifactsValid = true,
    )
}
