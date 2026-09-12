from pathlib import Path

UI = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")


def replace_once(old: str, new: str) -> None:
    text = UI.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"TripTimelineUi.kt: expected exactly one match, got {count}")
    UI.write_text(text.replace(old, new, 1))


replace_once(
    """    var referenceOrigin by remember { mutableStateOf<TripReferenceOrigin?>(null) }
    var currentCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
""",
    """    var referenceOrigin by remember { mutableStateOf<TripReferenceOrigin?>(null) }
    var currentCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    var timelineNowMillis0548 by remember { mutableStateOf(System.currentTimeMillis()) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
""",
)

replace_once(
    """        while (true) {
            currentCoordinate = runCatching { locationService.currentCoordinate() }.getOrNull()
            delay(30_000L)
        }
""",
    """        while (true) {
            timelineNowMillis0548 = System.currentTimeMillis()
            currentCoordinate = runCatching { locationService.currentCoordinate() }.getOrNull()
            delay(30_000L)
        }
""",
)

replace_once(
    """    val entries = remember(canonicalProjection0494.entries, archiveRevision, showArchived) {
        val operation = AgendaTrace.operationStart(context, \"TIMELINE_CANONICAL_SORT_0494\", \"TripTimelineScreen\", traceId)
        try {
            canonicalProjection0494.entries
                .filter { archiveStore.isArchived(it) == showArchived }
                .sortedBy(TripTimelineEntry::departureAtMillis)
""",
    """    val entries = remember(
        canonicalProjection0494.entries,
        archiveRevision,
        showArchived,
        timelineNowMillis0548,
    ) {
        val operation = AgendaTrace.operationStart(context, \"TIMELINE_CANONICAL_SORT_0494\", \"TripTimelineScreen\", traceId)
        try {
            canonicalProjection0494.entries
                .filter { archiveStore.isArchived(it) == showArchived }
                .filter { entry ->
                    showArchived || isPassengerTimelineCurrentOrUpcoming0548(
                        departureAtMillis = entry.departureAtMillis,
                        arrivalAtMillis = entry.arrivalAtMillis,
                        nowMillis = timelineNowMillis0548,
                    )
                }
                .sortedBy(TripTimelineEntry::departureAtMillis)
""",
)
