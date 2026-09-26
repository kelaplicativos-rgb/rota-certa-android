from pathlib import Path

SOURCE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt")
GRADLE = Path("app/build.gradle.kts")

text = SOURCE.read_text()

old_fields = """    private var expectedSeats = -1
    private var verifyingCompensation = false
"""
new_fields = """    private var expectedSeats = -1
    private var verifyingCompensation = false
    private var beforeArchiveSaved = false
    private var beforeReadAttempts = 0
"""
if text.count(old_fields) != 1:
    raise SystemExit(f"expected one state-field baseline, got {text.count(old_fields)}")
text = text.replace(old_fields, new_fields, 1)

old_before = """            Phase.BEFORE -> archive.save(webView, account, \"reliable-options-before\", request.tripId) {
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    if (state == null || state.seats < 0 || !state.savePresent) {
                        finishPending(\"O editor de vagas não está disponível ou não pôde ser lido.\", rotate = true)
                        return@evaluate
                    }
"""
new_before = """            Phase.BEFORE -> {
                if (!beforeArchiveSaved) {
                    archive.save(webView, account, \"reliable-options-before\", request.tripId) {
                        beforeArchiveSaved = true
                        busy = false
                        handlePage()
                    }
                    return
                }
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    if (state == null || state.seats < 0) {
                        if (beforeReadAttempts < RELIABLE_OPTIONS_READ_MAX_RETRIES) {
                            beforeReadAttempts++
                            UnifiedDebugEventStore.record(
                                \"EXTERNAL_SEAT_SYNC_RELIABLE_READ_RETRY\",
                                packageName,
                                \"request=${request.id} attempt=$beforeReadAttempts seats=${state?.seats ?: -1} savePresent=${state?.savePresent ?: false}\",
                            )
                            busy = false
                            webView.postDelayed({ handlePage() }, RELIABLE_OPTIONS_READ_RETRY_MS)
                            return@evaluate
                        }
                        finishPending(\"O editor de vagas não está disponível ou não pôde ser lido após novas leituras.\", rotate = true)
                        return@evaluate
                    }
                    beforeReadAttempts = 0
"""
if text.count(old_before) != 1:
    raise SystemExit(f"expected one reliable BEFORE baseline, got {text.count(old_before)}")
text = text.replace(old_before, new_before, 1)

marker = """private fun reliableOptionsUrl(tripId: String): String =
    \"${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/edit/${tripId.trim()}/options\"
"""
replacement = """private const val RELIABLE_OPTIONS_READ_MAX_RETRIES = 4
private const val RELIABLE_OPTIONS_READ_RETRY_MS = 650L

private fun reliableOptionsUrl(tripId: String): String =
    \"${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/edit/${tripId.trim()}/options\"
"""
if text.count(marker) != 1:
    raise SystemExit(f"expected one reliable options URL marker, got {text.count(marker)}")
text = text.replace(marker, replacement, 1)
SOURCE.write_text(text)

gradle_text = GRADLE.read_text()
if gradle_text.count("versionCode = 5575") != 1:
    raise SystemExit("unexpected versionCode baseline")
if gradle_text.count('versionName = "0.1.282"') != 1:
    raise SystemExit("unexpected versionName baseline")
gradle_text = gradle_text.replace("versionCode = 5575", "versionCode = 5576", 1)
gradle_text = gradle_text.replace('versionName = "0.1.282"', 'versionName = "0.1.283"', 1)
GRADLE.write_text(gradle_text)
