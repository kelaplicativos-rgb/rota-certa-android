package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaDirectAccountCapture0608Test {
    @Test
    fun initialTenCardBottomCannotCompleteBeforeLazyListFinishes0613() {
        val stabilizer = directRidesStabilizer0613(startedAtMillis = 0L)

        repeat(5) { index ->
            val decision = stabilizer.observe(
                observation(
                    cards = 10,
                    height = 1800,
                    fingerprint = "first-ten",
                    mutationAge = 5_000L,
                ),
                nowMillis = 1_000L + index * 650L,
            )
            assertFalse(decision.action == BlaBlaRidesSnapshotAction0526.CAPTURE)
        }

        val grew = stabilizer.observe(
            observation(
                cards = 30,
                height = 5200,
                fingerprint = "all-thirty",
                mutationAge = 100L,
            ),
            nowMillis = 5_000L,
        )
        assertFalse(grew.action == BlaBlaRidesSnapshotAction0526.CAPTURE)

        var finalDecision = grew
        repeat(7) { index ->
            finalDecision = stabilizer.observe(
                observation(
                    cards = 30,
                    height = 5200,
                    fingerprint = "all-thirty",
                    mutationAge = 5_000L,
                ),
                nowMillis = 7_000L + index * 650L,
            )
        }
        assertTrue(finalDecision.action == BlaBlaRidesSnapshotAction0526.CAPTURE)
    }

    @Test
    fun accumulatedObservedInventoryMustExactlyMatchSnapshot0613() {
        val ids = (1..30).map { index ->
            "01a0${index.toString().padStart(4, '0')}-0000-7000-8000-${index.toString().padStart(12, '0')}"
        }
        val hrefs = ids.map { id -> "https://www.blablacar.com.br/rides/offer?id=$id" }

        assertTrue(
            directObservedInventoryMatches0613(
                snapshotTripIds = ids,
                observedTripHrefs = hrefs,
                observedCardCount = 30,
            ),
        )

        assertFalse(
            directObservedInventoryMatches0613(
                snapshotTripIds = ids.take(19),
                observedTripHrefs = hrefs,
                observedCardCount = 30,
            ),
        )
    }

    @Test
    fun sameDateDoesNotCollapseDistinctStrongTripIds0613() {
        val outbound = "01a0886e-44ee-79c2-95e6-6fb74d595289"
        val inbound = "01a0c657-4609-7704-9b20-b915ca4ec37e"
        val hrefs = listOf(outbound, inbound).map { id ->
            "https://www.blablacar.com.br/rides/offer?id=$id"
        }

        assertTrue(
            directObservedInventoryMatches0613(
                snapshotTripIds = listOf(outbound, inbound),
                observedTripHrefs = hrefs,
                observedCardCount = 2,
            ),
        )
    }

    private fun observation(
        cards: Int,
        height: Int,
        fingerprint: String,
        mutationAge: Long,
    ) = BlaBlaRidesSnapshotObservation0526(
        cardCount = cards,
        scrollY = height - 600,
        scrollHeight = height,
        viewportHeight = 600,
        atBottom = true,
        loadingActive = false,
        lastMutationAgeMs = mutationAge,
        explicitEmptyList = false,
        tripSetSha256 = fingerprint,
        htmlTruncated = false,
        htmlMaterializedComplete = true,
    )
}
