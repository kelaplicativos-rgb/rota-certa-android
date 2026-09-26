from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Version: one build after the proven 0.1.544 / 5836 baseline.
replace_once("app/build.gradle.kts", "versionCode = 5836", "versionCode = 5837")
replace_once("app/build.gradle.kts", 'versionName = "0.1.544"', 'versionName = "0.1.545"')

# Stage43 already persists only liveReadingEnabled. The real coupling is that the service fed that
# value into the master runtime transition. Route the manual toggle into a reading-only transition.
service_path = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service_path,
    "        applyWorkModeRuntime0162(enabledStage43, force0162 = true)\n",
    "        applyManualReadingRuntimeStage43(enabledStage43)\n",
)

manual_runtime = r'''
    private fun applyManualReadingRuntimeStage43(enabledStage43: Boolean) {
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.setManualAuthority(enabledStage43)
        stage26ReadingActivation.setManualAuthority(enabledStage43)
        farolRealtimeEventGate0167.reset()
        if (enabledStage43) {
            lastRejectedForegroundPackage0162 = null
            UnifiedDebugEventStore.record(
                "MANUAL_READING_RUNTIME_STAGE43",
                packageName,
                "enabled=true; proximity_runtime_preserved=true",
            )
            showOverlay(RadarColor.Idle, null)
            scheduleVisibleTextAnalysis(delayMs = 0L)
            return
        }

        driverCardSessionGate0162.invalidate()
        clearStage16VisualProof()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        cancelNotificationWakeup0169()
        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null
        liveAnalysisJob?.cancel()
        liveAnalysisJob = null
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalActiveRidePackageName = null
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.markExplicitOff("manual_reading_disabled_stage43")
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        shortcutOverlayController.hideAll()

        // Reading OFF is intentionally NOT master work-mode OFF. In particular, do not stop
        // preciseNavigationTrackerChecklist5, do not hide directionalAlertOverlayChecklist5 and
        // do not change workModeRuntimeActive0162. Radar/user proximity keep their existing owner.
        val offRenderBeforeStage43 = stage43OffRenderAppliedSerial
        showOverlay(RadarColor.Idle, null, forcePhysicalCommitStage43 = true)
        val offRenderAppliedStage43 = stage43OffRenderAppliedSerial > offRenderBeforeStage43 &&
            currentRadarColor == RadarColor.Idle && currentDistanceKm == null
        FarolManualOffVisualCommitStage43.recordAttempt(offRenderAppliedStage43)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_COMMIT", universalResolvedForegroundPackage(),
            details = "applied=$offRenderAppliedStage43; proximityPreserved=true; beforeSerial=$offRenderBeforeStage43; afterSerial=$stage43OffRenderAppliedSerial; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}",
        )
        if (!offRenderAppliedStage43) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_ANOMALY", universalResolvedForegroundPackage(),
                details = "logicalOff=true; expected=Idle/no-km/renderApplied; proximityPreserved=true; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}; serviceReady=$serviceReady; overlayPresent=${overlayView != null}",
            )
        }
        UnifiedDebugEventStore.record(
            "MANUAL_READING_RUNTIME_STAGE43",
            packageName,
            "enabled=false; proximity_runtime_preserved=true",
        )
    }

'''
replace_once(
    service_path,
    "    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {\n",
    manual_runtime + "    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {\n",
)

# Stage44 already defines the final Green/Red lease. A transient OCR frame with no candidate may
# not revoke it while the exact captured target surface is still active. Hard visual boundaries and
# stale-work checks remain authoritative and still clear old-card results.
old_no_candidate = '''                                } else {
                                    FarolForensicCardBlackBoxStage32.markOcrNoCandidate(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())
                                    FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "NO_CANDIDATE", cycleIdStage20)
                                    hardClearUniversalTwoAddress(
                                        reason = "Snapshot visual atual sem dois endereços semanticamente completos Stage23.",
                                        keepWaitingYellow = true,
                                    )
                                }
'''
new_no_candidate = '''                                } else {
                                    FarolForensicCardBlackBoxStage32.markOcrNoCandidate(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())
                                    FarolForensicCaseStoreStage32.persistIfIntensive(applicationContext)
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "NO_CANDIDATE", cycleIdStage20)
                                    val transientLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(
                                        currentRadarColor.name,
                                        currentDistanceKm,
                                        universalActiveAddressSignature,
                                    )
                                    val transientPresenceStage44 = observeTargetSurfaceStage46R3(surfaceTokenStage46.packageName)
                                    if (transientLeaseStage44.activeFinal && transientPresenceStage44.active) {
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_TRANSIENT_NO_CANDIDATE_FINAL_PRESERVED", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "color=${transientLeaseStage44.color}; distance=${transientLeaseStage44.distanceKm ?: -1.0}; signature=${transientLeaseStage44.addressSignature.orEmpty()}; surface=${surfaceTokenStage46.packageName.orEmpty()}; active=${transientPresenceStage44.active}",
                                        )
                                    } else {
                                        hardClearUniversalTwoAddress(
                                            reason = "Snapshot visual atual sem dois endereços semanticamente completos Stage23 e sem lease Stage44 ativa.",
                                            keepWaitingYellow = true,
                                        )
                                    }
                                }
'''
replace_once(service_path, old_no_candidate, new_no_candidate)

# Expose the actual Route Matrix HTTP error and recover only transport/request failures through the
# already existing Geocoding -> ComputeRoutes path. A successful matrix with null route elements is
# left untouched, so 'no route' is not misreported as an HTTP failure.
maps_path = "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
old_fetch = '''        val fetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
            requestAddressRouteMatrix(body, apiKey, missingDestinations.size)
        }

        FarolFlightRecorder0163.record(
'''
new_fetch = '''        val matrixFetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
            requestAddressRouteMatrix(body, apiKey, missingDestinations.size)
        }
        val fetched = matrixFetched ?: requestAddressRouteFallback(
            originAddress = originAddress,
            destinations = missingDestinations,
            apiKey = apiKey,
        )

        FarolFlightRecorder0163.record(
'''
replace_once(maps_path, old_fetch, new_fetch)

old_http_error = '''            if (responseCode0163 !in 200..299) {
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
'''
new_http_error = '''            if (responseCode0163 !in 200..299) {
                val errorBody0163 = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                FarolFlightRecorder0163.record(
                    stage = "ROUTE_MATRIX_HTTP_ERROR",
                    packageName = null,
                    details = "code=$responseCode0163; destinations=$destinationCount; body=${sanitizeMapsErrorBody(errorBody0163)}",
                )
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
'''
replace_once(maps_path, old_http_error, new_http_error)

fallback_helper = r'''
    private fun requestAddressRouteFallback(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty() || apiKey.isBlank()) return null
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_STARTED",
            packageName = null,
            details = "destinations=${destinations.size}",
        )
        val origin = requestWithRetry(GEOCODE_REQUEST_ATTEMPTS) {
            requestGeocode(originAddress, apiKey)
        } ?: run {
            FarolFlightRecorder0163.record(
                stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_FAILED",
                packageName = null,
                details = "originResolved=false; destinations=${destinations.size}",
            )
            return null
        }
        val values = destinations.map { destination ->
            requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestDrivingDistance(coordinateRouteBody(origin, destination), apiKey)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_RESOLVED",
            packageName = null,
            details = "returned=${values.count { it != null }}; destinations=${destinations.size}",
        )
        return values
    }

    private fun sanitizeMapsErrorBody(raw: String): String = raw
        .replace(Regex("AIza[0-9A-Za-z_-]+"), "<redacted-api-key>")
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer <redacted>")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(600)

'''
replace_once(
    maps_path,
    "    /**\n     * Monta consultas sem inventar uma cidade fixa.\n",
    fallback_helper + "    /**\n     * Monta consultas sem inventar uma cidade fixa.\n",
)

# Bring Stage43 tests in line with the already-intended persistence contract and prove the new
# runtime boundary does not stop proximity-owned components.
test43_path = "app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage43ManualToggleRuntimeSyncTest.kt"
replace_once(test43_path, "        assertFalse(off.appEnabled)\n", "        assertTrue(off.appEnabled)\n")
replace_once(
    test43_path,
    '        val runtime = block.indexOf("applyWorkModeRuntime0162(enabledStage43, force0162 = true)")\n',
    '        val runtime = block.indexOf("applyManualReadingRuntimeStage43(enabledStage43)")\n',
)
replace_once(
    test43_path,
    '        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")\n        val b = s.indexOf("    private fun ensureDriverCardSession0162(", a)\n',
    '        val a = s.indexOf("    private fun applyManualReadingRuntimeStage43(")\n        val b = s.indexOf("    private fun applyWorkModeRuntime0162(", a)\n',
)
replace_once(
    test43_path,
    '        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")\n        val b = s.indexOf("        driverCardSessionGate0162.invalidate()", a)\n',
    '        val a = s.indexOf("    private fun applyManualReadingRuntimeStage43(")\n        val b = s.indexOf("        driverCardSessionGate0162.invalidate()", a)\n',
)

contract_test = r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProximity0545ContractTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun manualReadingRuntimeDoesNotOwnProximityLifecycle() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyManualReadingRuntimeStage43(")
        val b = s.indexOf("    private fun applyWorkModeRuntime0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("MANUAL_READING_RUNTIME_STAGE43"))
        assertFalse(block.contains("preciseNavigationTrackerChecklist5.stop()"))
        assertFalse(block.contains("directionalAlertOverlayChecklist5.hide()"))
        assertFalse(block.contains("workModeRuntimeActive0162 ="))
    }

    @Test fun transientNoCandidateUsesExistingStage44LeaseBeforeClearing() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S44_TRANSIENT_NO_CANDIDATE_FINAL_PRESERVED"))
        assertTrue(s.contains("transientLeaseStage44.activeFinal && transientPresenceStage44.active"))
        assertTrue(s.contains("sem lease Stage44 ativa"))
    }

    @Test fun routeMatrixHttpFailureIsObservableAndUsesExistingRouteFallback() {
        val s = source("GoogleMapsService.kt")
        assertTrue(s.contains("ROUTE_MATRIX_HTTP_ERROR"))
        assertTrue(s.contains("sanitizeMapsErrorBody"))
        assertTrue(s.contains("ROUTE_MATRIX_FALLBACK_GEOCODE_STARTED"))
        assertTrue(s.contains("requestDrivingDistance(coordinateRouteBody(origin, destination), apiKey)"))
    }
}
'''
Path("app/src/test/java/br/com/mapeiaia/rotacerta/ReadingProximity0545ContractTest.kt").write_text(contract_test, encoding="utf-8")

print("Materialized Rota Certa 0.1.545 reading/proximity surgical fix")
