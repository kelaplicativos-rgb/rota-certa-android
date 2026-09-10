package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaRidesSnapshot0526Test {
    private val ezequiel = "7371f028-9c55-4903-8444-308015823efd"
    private val barbosa = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    @Test
    fun expectedStrongProfileUuidIsRequiredAndConfirmed() {
        val result = BlaBlaRidesSnapshotIdentityPolicy0526.resolve(
            expectedProfileUuid = ezequiel,
            profileLinks = listOf(
                "https://www.blablacar.com.br/member/$ezequiel",
                "https://www.blablacar.com.br/profile/$ezequiel/reviews",
            ),
            observedUuids = listOf(ezequiel),
        )

        assertTrue(result.confirmed)
        assertEquals(ezequiel, result.authenticatedProfileUuid)
        assertEquals("", result.errorCode)
    }

    @Test
    fun crossSessionMismatchNeverConfirmsRequestedBarbosaAsEzequielSession() {
        val result = BlaBlaRidesSnapshotIdentityPolicy0526.resolve(
            expectedProfileUuid = barbosa,
            profileLinks = listOf("https://www.blablacar.com.br/member/$ezequiel"),
            observedUuids = listOf(ezequiel),
        )

        assertFalse(result.confirmed)
        assertEquals(ezequiel, result.authenticatedProfileUuid)
        assertEquals("PROFILE_UUID_MISMATCH", result.errorCode)
    }

    @Test
    fun multipleStrongProfileUuidsAreAmbiguousEvenWhenExpectedIsAmongThem() {
        val result = BlaBlaRidesSnapshotIdentityPolicy0526.resolve(
            expectedProfileUuid = ezequiel,
            profileLinks = listOf(
                "https://www.blablacar.com.br/member/$ezequiel",
                "https://www.blablacar.com.br/member/$barbosa",
            ),
            observedUuids = listOf(ezequiel, barbosa),
        )

        assertFalse(result.confirmed)
        assertEquals("", result.authenticatedProfileUuid)
        assertEquals("PROFILE_IDENTITY_AMBIGUOUS", result.errorCode)
    }

    @Test
    fun missingExpectedUuidFailsClosed() {
        val result = BlaBlaRidesSnapshotIdentityPolicy0526.resolve(
            expectedProfileUuid = null,
            profileLinks = listOf("https://www.blablacar.com.br/member/$ezequiel"),
            observedUuids = listOf(ezequiel),
        )

        assertFalse(result.confirmed)
        assertEquals("EXPECTED_PROFILE_UUID_MISSING", result.errorCode)
    }

    @Test
    fun lazyLoadingResetsStabilityAndCapturesOnlyAfterFinalCountAndHeightStayQuiet() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(startedAtMillis = 0L)

        assertEquals(
            BlaBlaRidesSnapshotAction0526.SCROLL,
            stabilizer.observe(
                obs(cards = 3, y = 0, height = 1000, bottom = false, mutationAge = 50),
                nowMillis = 100,
            ).action,
        )
        assertEquals(
            BlaBlaRidesSnapshotAction0526.SCROLL,
            stabilizer.observe(
                obs(cards = 8, y = 700, height = 2200, bottom = false, mutationAge = 100),
                nowMillis = 1000,
            ).action,
        )
        assertEquals(
            BlaBlaRidesSnapshotAction0526.WAIT,
            stabilizer.observe(
                obs(cards = 8, y = 1600, height = 2200, bottom = true, mutationAge = 1400),
                nowMillis = 2500,
            ).action,
        )
        assertEquals(
            BlaBlaRidesSnapshotAction0526.CAPTURE,
            stabilizer.observe(
                obs(cards = 8, y = 1600, height = 2200, bottom = true, mutationAge = 2600),
                nowMillis = 3700,
            ).action,
        )
        assertEquals(3, stabilizer.initialCardCount)
        assertEquals(8, stabilizer.finalCardCount)
        assertEquals(2, stabilizer.scrollIterations)
    }

    @Test
    fun emptyAuthoritativeRideListCanCompleteWithZeroCards() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(startedAtMillis = 0L)

        val first = stabilizer.observe(
            obs(cards = 0, y = 0, height = 600, bottom = true, mutationAge = 1500, empty = true),
            nowMillis = 1600,
        )
        val second = stabilizer.observe(
            obs(cards = 0, y = 0, height = 600, bottom = true, mutationAge = 2800, empty = true),
            nowMillis = 2900,
        )
        val third = stabilizer.observe(
            obs(cards = 0, y = 0, height = 600, bottom = true, mutationAge = 4100, empty = true),
            nowMillis = 4200,
        )

        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, first.action)
        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, second.action)
        assertEquals(BlaBlaRidesSnapshotAction0526.CAPTURE, third.action)
        assertEquals(0, stabilizer.finalCardCount)
    }

    @Test
    fun slowInternetSpinnerCannotBeMistakenForStableBottom() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(
            startedAtMillis = 0L,
            maxNoProgressCycles = 20,
        )

        repeat(4) { index ->
            val decision = stabilizer.observe(
                obs(
                    cards = 4,
                    y = 900,
                    height = 1500,
                    bottom = true,
                    loading = true,
                    mutationAge = 5000,
                ),
                nowMillis = 1000L + index * 1200L,
            )
            assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, decision.action)
        }

        val justChanged = stabilizer.observe(
            obs(cards = 5, y = 900, height = 1700, bottom = true, loading = false, mutationAge = 100),
            nowMillis = 7000,
        )
        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, justChanged.action)

        val quiet1 = stabilizer.observe(
            obs(cards = 5, y = 900, height = 1700, bottom = true, loading = false, mutationAge = 1500),
            nowMillis = 8500,
        )
        val quiet2 = stabilizer.observe(
            obs(cards = 5, y = 900, height = 1700, bottom = true, loading = false, mutationAge = 2800),
            nowMillis = 9800,
        )
        assertEquals(BlaBlaRidesSnapshotAction0526.WAIT, quiet1.action)
        assertEquals(BlaBlaRidesSnapshotAction0526.CAPTURE, quiet2.action)
    }

    @Test
    fun noProgressLimitProducesIncompleteInsteadOfInfiniteLoop() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(
            startedAtMillis = 0L,
            maxCycles = 20,
            maxTotalMillis = 60_000L,
            maxNoProgressCycles = 3,
        )
        var decision = stabilizer.observe(
            obs(cards = 2, y = 0, height = 1200, bottom = true, loading = true, mutationAge = 5000),
            nowMillis = 100,
        )
        repeat(3) { index ->
            decision = stabilizer.observe(
                obs(cards = 2, y = 0, height = 1200, bottom = true, loading = true, mutationAge = 5000),
                nowMillis = 1000L + index * 1000L,
            )
        }

        assertEquals(BlaBlaRidesSnapshotAction0526.INCOMPLETE, decision.action)
        assertEquals("NO_PROGRESS", decision.reason)
    }

    @Test
    fun virtualizedDomCannotClaimCompleteWhenObservedCardsWereRemoved() {
        val stabilizer = BlaBlaRidesSnapshotStabilizer0526(startedAtMillis = 0L)
        stabilizer.observe(
            obs(cards = 9, y = 1400, height = 2000, bottom = true, mutationAge = 1500, materializedComplete = false),
            nowMillis = 1600,
        )
        stabilizer.observe(
            obs(cards = 9, y = 1400, height = 2000, bottom = true, mutationAge = 2800, materializedComplete = false),
            nowMillis = 2900,
        )
        val decision = stabilizer.observe(
            obs(cards = 9, y = 1400, height = 2000, bottom = true, mutationAge = 4100, materializedComplete = false),
            nowMillis = 4200,
        )

        assertEquals(BlaBlaRidesSnapshotAction0526.INCOMPLETE, decision.action)
        assertEquals("HTML_NOT_FULLY_MATERIALIZED", decision.reason)
    }

    @Test
    fun partialProfileFailureKeepsSuccessfulProfileAndGlobalResultIsPartialSuccess() {
        assertEquals(
            "PARTIAL_SUCCESS",
            ridesSnapshotGlobalResult0526(
                listOf(
                    BlaBlaRidesSnapshotStatus0526.COMPLETE,
                    BlaBlaRidesSnapshotStatus0526.FAILED_IDENTITY,
                ),
            ),
        )
        assertEquals(
            "COMPLETE",
            ridesSnapshotGlobalResult0526(
                listOf(
                    BlaBlaRidesSnapshotStatus0526.COMPLETE,
                    BlaBlaRidesSnapshotStatus0526.COMPLETE,
                ),
            ),
        )
    }

    @Test
    fun snapshotReusesOfficialBrowserAndRideListAndDownloadsZipWithoutCanonicalPublication() {
        val snapshot = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaRidesSnapshot0526.kt").readText()
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val script = File("src/main/assets/blablacar/scripts/ride_list.js").readText()

        assertFalse(snapshot.contains("WebView("))
        assertFalse(snapshot.contains("TripStore("))
        assertFalse(snapshot.contains("saveSync("))
        assertFalse(snapshot.contains("publishCurrentSessions("))
        assertTrue(snapshot.contains("BlaBlaDynamicAccountSessionController0401"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.SESSION_IDENTITY"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.RIDE_LIST"))
        assertTrue(dynamic.contains("webView.saveWebArchive"))
        assertFalse(snapshot.contains("Intent.ACTION_SEND_MULTIPLE"))
        assertFalse(snapshot.contains("FileProvider"))
        assertFalse(snapshot.contains("copyForShare"))
        assertTrue(snapshot.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(snapshot.contains("Environment.DIRECTORY_DOWNLOADS"))
        assertTrue(snapshot.contains("ZipOutputStream"))
        assertTrue(snapshot.contains("downloadEntries0527"))
        assertTrue(snapshot.contains("application/zip"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("observedCardCount"))
        assertTrue(script.contains("loadingActive"))
        assertTrue(script.contains("snapshotHtml"))
    }

    private fun obs(
        cards: Int,
        y: Int,
        height: Int,
        bottom: Boolean,
        loading: Boolean = false,
        mutationAge: Long,
        empty: Boolean = false,
        materializedComplete: Boolean = true,
    ) = BlaBlaRidesSnapshotObservation0526(
        cardCount = cards,
        scrollY = y,
        scrollHeight = height,
        viewportHeight = 600,
        atBottom = bottom,
        loadingActive = loading,
        lastMutationAgeMs = mutationAge,
        explicitEmptyList = empty,
        htmlMaterializedComplete = materializedComplete,
    )
}
