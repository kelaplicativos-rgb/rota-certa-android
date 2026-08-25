from pathlib import Path

SOURCE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync.kt")
GRADLE = Path("app/build.gradle.kts")

text = SOURCE.read_text()

old_fields = """    private var verifyingCompensation = false
    private var beforeArchiveSaved = false
    private var beforeReadAttempts = 0
"""
new_fields = """    private var verifyingCompensation = false
    private var beforeArchiveSaved = false
    private var beforeReadAttempts = 0
    private var afterArchiveSaved = false
    private var verifyReadAttempts = 0
    private var verificationReloadScheduled = false
"""
if text.count(old_fields) != 1:
    raise SystemExit(f"expected one reliable state-field baseline, got {text.count(old_fields)}")
text = text.replace(old_fields, new_fields, 1)

old_page_finished = """            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!BlaBlaCollectorUrlModule.isAllowed(url) || busy) return
                view.postDelayed({ handlePage() }, 700L)
            }
"""
new_page_finished = """            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!BlaBlaCollectorUrlModule.isAllowed(url)) return
                if (phase == Phase.SAVING) {
                    scheduleVerificationReload("save_navigation")
                    return
                }
                if (phase == Phase.VERIFY && !BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, url)) return
                if (busy) return
                view.postDelayed({ handlePage() }, 700L)
            }
"""
if text.count(old_page_finished) != 1:
    raise SystemExit(f"expected one onPageFinished baseline, got {text.count(old_page_finished)}")
text = text.replace(old_page_finished, new_page_finished, 1)

old_verify = """            Phase.VERIFY -> archive.save(webView, account, \"reliable-options-after\", request.tripId) {
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    if (state != null && state.seats == expectedSeats && BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)) {
                        if (verifyingCompensation) {
                            completeCompensation(expectedSeats, wrote = true)
                        } else {
                            completeVerified(expectedSeats, alreadyApplied = false)
                        }
                    } else {
                        finishPending(\"Alteração não confirmada após releitura; a tentativa ficou preservada para conferência idempotente.\", rotate = true)
                    }
                }
            }
"""
new_verify = """            Phase.SAVING -> {
                busy = false
                scheduleVerificationReload("handle_page_fallback")
            }
            Phase.VERIFY -> {
                if (!afterArchiveSaved) {
                    archive.save(webView, account, \"reliable-options-after\", request.tripId) {
                        afterArchiveSaved = true
                        busy = false
                        handlePage()
                    }
                    return
                }
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    val exactPage = state != null && BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)
                    val verified = exactPage && state.seats == expectedSeats
                    if (verified) {
                        verifyReadAttempts = 0
                        if (verifyingCompensation) {
                            completeCompensation(expectedSeats, wrote = true)
                        } else {
                            completeVerified(expectedSeats, alreadyApplied = false)
                        }
                        return@evaluate
                    }
                    if (verifyReadAttempts < RELIABLE_OPTIONS_READ_MAX_RETRIES) {
                        verifyReadAttempts++
                        UnifiedDebugEventStore.record(
                            \"EXTERNAL_SEAT_SYNC_RELIABLE_VERIFY_RETRY\",
                            packageName,
                            \"request=${request.id} attempt=$verifyReadAttempts observed=${state?.seats ?: -1} expected=$expectedSeats exactPage=$exactPage savePresent=${state?.savePresent ?: false}\",
                        )
                        busy = false
                        webView.postDelayed({ handlePage() }, RELIABLE_OPTIONS_READ_RETRY_MS)
                        return@evaluate
                    }
                    finishPending(\"Alteração não confirmada após releituras; a tentativa ficou preservada para conferência idempotente.\", rotate = true)
                }
            }
"""
if text.count(old_verify) != 1:
    raise SystemExit(f"expected one VERIFY baseline, got {text.count(old_verify)}")
text = text.replace(old_verify, new_verify, 1)

old_apply = """    private fun applyTarget(before: Int, target: Int, compensation: Boolean) {
        expectedSeats = target
        verifyingCompensation = compensation
        statusView.text = \"${account.displayLabel} • $before → $target vagas • salvando…\"
        phase = Phase.VERIFY
        webView.evaluateJavascript(applyReliableSeatsJs(target), null)
        val distance = kotlin.math.abs(target - before).coerceAtMost(20)
        webView.postDelayed({
            busy = false
            webView.loadUrl(reliableOptionsUrl(request.tripId))
        }, 1_700L + distance * 320L)
    }
"""
new_apply = """    private fun applyTarget(before: Int, target: Int, compensation: Boolean) {
        expectedSeats = target
        verifyingCompensation = compensation
        afterArchiveSaved = false
        verifyReadAttempts = 0
        verificationReloadScheduled = false
        statusView.text = \"${account.displayLabel} • $before → $target vagas • salvando…\"
        phase = Phase.SAVING
        webView.evaluateJavascript(applyReliableSeatsJs(target), null)
        webView.postDelayed({
            if (phase == Phase.SAVING) scheduleVerificationReload(\"save_timeout\")
        }, RELIABLE_SAVE_COMPLETION_TIMEOUT_MS)
    }

    private fun scheduleVerificationReload(origin: String) {
        if (phase != Phase.SAVING || verificationReloadScheduled) return
        verificationReloadScheduled = true
        phase = Phase.VERIFY
        statusView.text = \"${account.displayLabel} • confirmando $expectedSeats vaga(s) publicadas…\"
        UnifiedDebugEventStore.record(
            \"EXTERNAL_SEAT_SYNC_RELIABLE_SAVE_COMPLETED\",
            packageName,
            \"request=${request.id} origin=$origin expected=$expectedSeats writeRepeated=false\",
        )
        webView.postDelayed({
            busy = false
            webView.loadUrl(reliableOptionsUrl(request.tripId))
        }, RELIABLE_SAVE_SETTLE_MS)
    }
"""
if text.count(old_apply) != 1:
    raise SystemExit(f"expected one applyTarget baseline, got {text.count(old_apply)}")
text = text.replace(old_apply, new_apply, 1)

old_phase = """    private enum class Phase { BEFORE, VERIFY }
"""
new_phase = """    private enum class Phase { BEFORE, SAVING, VERIFY }
"""
if text.count(old_phase) != 1:
    raise SystemExit(f"expected one Phase baseline, got {text.count(old_phase)}")
text = text.replace(old_phase, new_phase, 1)

old_constants = """private const val RELIABLE_OPTIONS_READ_MAX_RETRIES = 4
private const val RELIABLE_OPTIONS_READ_RETRY_MS = 650L
"""
new_constants = """private const val RELIABLE_OPTIONS_READ_MAX_RETRIES = 4
private const val RELIABLE_OPTIONS_READ_RETRY_MS = 650L
private const val RELIABLE_SAVE_COMPLETION_TIMEOUT_MS = 7_000L
private const val RELIABLE_SAVE_SETTLE_MS = 650L
"""
if text.count(old_constants) != 1:
    raise SystemExit(f"expected one reliable constants baseline, got {text.count(old_constants)}")
text = text.replace(old_constants, new_constants, 1)

if "1_700L + distance * 320L" in text:
    raise SystemExit("premature fixed reload is still present")
if "Phase.SAVING" not in text or "EXTERNAL_SEAT_SYNC_RELIABLE_SAVE_COMPLETED" not in text:
    raise SystemExit("save completion state was not materialized")
SOURCE.write_text(text)

gradle_text = GRADLE.read_text()
if gradle_text.count("versionCode = 5576") != 1:
    raise SystemExit("unexpected versionCode baseline")
if gradle_text.count('versionName = "0.1.283"') != 1:
    raise SystemExit("unexpected versionName baseline")
gradle_text = gradle_text.replace("versionCode = 5576", "versionCode = 5577", 1)
gradle_text = gradle_text.replace('versionName = "0.1.283"', 'versionName = "0.1.284"', 1)
GRADLE.write_text(gradle_text)
