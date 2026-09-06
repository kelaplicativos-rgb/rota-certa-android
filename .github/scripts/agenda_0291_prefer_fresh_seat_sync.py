from pathlib import Path

root = Path(__file__).resolve().parents[2]

reliable = root / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt"
ui = root / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt"
test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync0271Test.kt"
build = root / "app/build.gradle.kts"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one occurrence in {path}: {old[:120]!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


# 0.1.290 physical evidence: a retained compensation attempt can sit at queue head
# and starve a newly queued booking change. Keep the retained request, but prefer a
# request that has never started whenever one exists.
replace_once(
    reliable,
    "internal data class BlaBlaReliableSeatSyncDecision(\n    val action: BlaBlaReliableSeatSyncAction,\n    val targetSeats: Int? = null,\n)\n\n/** Pure retry/idempotency policy used by the Activity and unit tests. */",
    "internal data class BlaBlaReliableSeatSyncDecision(\n    val action: BlaBlaReliableSeatSyncAction,\n    val targetSeats: Int? = null,\n)\n\ninternal object BlaBlaReliableSeatQueuePolicy {\n    fun select(\n        queue: List<BlaBlaManualSeatSyncRequest>,\n        hasPersistedAttempt: (String) -> Boolean,\n    ): BlaBlaManualSeatSyncRequest? =\n        queue.firstOrNull { !hasPersistedAttempt(it.id) } ?: queue.firstOrNull()\n}\n\n/** Pure retry/idempotency policy used by the Activity and unit tests. */",
)

replace_once(
    ui,
    "    val manualSeatStore = remember(context) { BlaBlaManualSeatSyncRequestStore(context) }\n",
    "    val manualSeatStore = remember(context) { BlaBlaManualSeatSyncRequestStore(context) }\n    val manualSeatAttemptStore = remember(context) { BlaBlaManualSeatSyncAttemptStore(context) }\n",
)

replace_once(
    ui,
    "    fun pendingSeatTarget(): Pair<BlaBlaManualSeatSyncRequest, BlaBlaDynamicAccount>? {\n        val pending = manualSeatStore.peek() ?: return null\n",
    "    fun pendingSeatRequest(): BlaBlaManualSeatSyncRequest? =\n        BlaBlaReliableSeatQueuePolicy.select(manualSeatStore.list()) { requestId ->\n            manualSeatAttemptStore.get(requestId) != null\n        }\n\n    fun pendingSeatTarget(): Pair<BlaBlaManualSeatSyncRequest, BlaBlaDynamicAccount>? {\n        val pending = pendingSeatRequest() ?: return null\n",
)

replace_once(
    ui,
    "        val pendingManualSeat = manualSeatStore.peek()\n",
    "        val pendingManualSeat = pendingSeatRequest()\n",
)

insert_before = "    @Test\n    fun unavailableEditorKeepsOperationPending() {"
queue_tests = '''    @Test
    fun freshRequestIsPreferredOverRetainedAttempt() {
        val retained = request(id = "retained", bookingId = "booking-old", delta = -3, createdAt = 1L)
        val fresh = request(id = "fresh", bookingId = "booking-new", delta = -1, createdAt = 2L)

        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained, fresh)) { requestId ->
            requestId == retained.id
        }

        assertEquals(fresh.id, selected?.id)
    }

    @Test
    fun retainedAttemptStillRunsWhenItIsTheOnlyPendingRequest() {
        val retained = request(id = "retained", bookingId = "booking-old", delta = -3, createdAt = 1L)

        val selected = BlaBlaReliableSeatQueuePolicy.select(listOf(retained)) { true }

        assertEquals(retained.id, selected?.id)
    }

'''
replace_once(test, insert_before, queue_tests + insert_before)

helper_anchor = "    private fun attempt(\n"
request_helper = '''    private fun request(
        id: String,
        bookingId: String,
        delta: Int,
        createdAt: Long,
    ) = BlaBlaManualSeatSyncRequest(
        id = id,
        profileUuid = "7371f028-9c55-4903-8444-308015823efd",
        tripId = "trip-1",
        seatDelta = delta,
        localTripId = "local-trip-1",
        localBookingId = bookingId,
        source = BookingSource.PRIVATE.name,
        createdAtMillis = createdAt,
    )

'''
replace_once(test, helper_anchor, request_helper + helper_anchor)

replace_once(build, '        versionCode = 5583\n        versionName = "0.1.290"', '        versionCode = 5584\n        versionName = "0.1.291"')
