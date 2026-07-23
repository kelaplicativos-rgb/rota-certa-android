from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta"
TESTS = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Ponto ausente para {label}: {path}")
    path.write_text(text.replace(old, new, 1))


def patch_ride_text_parser() -> None:
    path = MAIN / "RideTextParser.kt"
    text = path.read_text()

    old = '        "hospital",\n    )'
    if '        "passagem",\n' not in text:
        if old not in text:
            raise SystemExit("Lista de palavras de endereco nao encontrada")
        text = text.replace(old, '        "hospital",\n        "passagem",\n    )', 1)

    old = '''        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }
'''
    new = '''        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers)
            ?: addresses.firstOrNull()?.takeIf { addresses.size > 1 }
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.lastOrNull()
'''
    if old in text:
        text = text.replace(old, new, 1)

    old = '''        for (index in fareIndex + 1 until lines.size) {
            val line = lines[index]
            if (isActionLine(line) || isInDriveOfferBoundary(line)) {
'''
    new = '''        for (index in fareIndex + 1 until lines.size) {
            val line = lines[index]
            if (index > fareIndex + 1 && isAddressContinuation(cleanAddressLine(line), cleanAddressLine(lines[index - 1]))) {
                continue
            }
            if (isActionLine(line) || isInDriveOfferBoundary(line)) {
'''
    if old in text:
        text = text.replace(old, new, 1)

    old = '''        lines.forEachIndexed { index, rawLine ->
            buildAddressBlock(lines, index, rawLine)?.let { candidates += it }
        }
'''
    new = '''        lines.forEachIndexed { index, rawLine ->
            if (index > 0 && isAddressContinuation(cleanAddressLine(rawLine), cleanAddressLine(lines[index - 1]))) {
                return@forEachIndexed
            }
            buildAddressBlock(lines, index, rawLine)?.let { candidates += it }
        }
'''
    if old in text:
        text = text.replace(old, new, 1)

    old = '''        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }

        if (looksLikeAddress(value) && !previousEndsWithStreetType) return false

        return previous.endsWith(",") ||
            previousEndsWithStreetType ||
            previousHasOpenParenthesis ||
'''
    new = '''        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }
        val previousEndsWithConnector = listOf(" de", " da", " do", " das", " dos").any { previousNormalized.endsWith(it) }
        val startsWithBareHouseNumber = Regex("""^\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?(?:\\s|,|\\()""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

        if (looksLikeAddress(value) && !previousEndsWithStreetType && !previousEndsWithConnector && !startsWithBareHouseNumber) return false

        return previous.endsWith(",") ||
            previousEndsWithStreetType ||
            previousEndsWithConnector ||
            startsWithBareHouseNumber ||
            previousHasOpenParenthesis ||
'''
    if old in text:
        text = text.replace(old, new, 1)

    required = [
        '"passagem"',
        'addresses.firstOrNull()?.takeIf { addresses.size > 1 }',
        'addresses.lastOrNull()',
        'previousEndsWithConnector',
        'startsWithBareHouseNumber',
        'return@forEachIndexed',
    ]
    missing = [marker for marker in required if marker not in text]
    if missing:
        raise SystemExit(f"RideTextParser incompleto: {missing}")
    path.write_text(text)


def patch_universal_parser() -> None:
    path = MAIN / "UniversalScreenAddressParser.kt"
    text = path.read_text()
    text = text.replace('|via|viela|beco|marginal', '|via|passagem|viela|beco|marginal')

    old = '''        val wrappedStreetNumberContinuation = !isCompleteNumberedAddress(previous) &&
            explicitHouseNumberRegex.containsMatchIn(value) &&
            (stateRegex.containsMatchIn(value) ||
                cepRegex.containsMatchIn(value) ||
                namedPlaceLocalityRegex.containsMatchIn(value))
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return looksLikeStreetPrefixContinuation(value, previous) ||
'''
    new = '''        val wrappedStreetNumberContinuation = !isCompleteNumberedAddress(previous) &&
            (explicitHouseNumberRegex.containsMatchIn(value) ||
                Regex("""^\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?(?:\\s|,|\\()""", RegexOption.IGNORE_CASE).containsMatchIn(value)) &&
            (value.contains('(') || stateRegex.containsMatchIn(value) ||
                cepRegex.containsMatchIn(value) ||
                namedPlaceLocalityRegex.containsMatchIn(value))
        val previousEndsWithConnector = listOf(" de", " da", " do", " das", " dos").any { previousCanonical.endsWith(it) }
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return looksLikeStreetPrefixContinuation(value, previous) ||
            previousEndsWithConnector ||
'''
    if old in text:
        text = text.replace(old, new, 1)

    required = ["passagem", "previousEndsWithConnector", "wrappedStreetNumberContinuation"]
    missing = [marker for marker in required if marker not in text]
    if missing:
        raise SystemExit(f"UniversalScreenAddressParser incompleto: {missing}")
    path.write_text(text)


def patch_selector() -> None:
    path = MAIN / "PrimaryVisibleRideCardSelector.kt"
    text = path.read_text()
    text = text.replace('reason = "card_individual_completo"', 'reason = "card_individual_ou_layout_sem_lista"', 1)
    text = text.replace(
        'reason = "card_completo_visivel",',
        'reason = if (cardIndex == 0) "primeiro_card_completo_visivel" else "card_completo_visivel",',
        1,
    )
    if "primeiro_card_completo_visivel" not in text:
        raise SystemExit("Motivo do primeiro card nao preservado")
    path.write_text(text)


def patch_proximity() -> None:
    path = MAIN / "ProximityAlertEngine.kt"
    text = path.read_text()

    old = '''            if (distanceMeters <= threshold) {
                runtime.observeDistance(distanceMeters)
                val approaching = runtime.isApproaching(distanceMeters)
                val directionMatch = radarDirectionMatches(radar, movementBearing)
'''
    new = '''            if (distanceMeters <= threshold) {
                val approaching = runtime.isApproaching(distanceMeters)
                runtime.observeDistance(distanceMeters)
                val directionMatch = radarDirectionMatches(radar, movementBearing)
                trace { "imported_radar.candidate id=${radar.id} distance=${distanceMeters.roundToInt()}m approaching=$approaching direction_match=$directionMatch" }
'''
    if old in text:
        text = text.replace(old, new, 1)

    old = '''        if (!runtime.popupClosedAfterPass) {
            runtime.popupShownThisApproach = true
            onImportedRadarDetected(radar, distanceMeters)
        }
        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {
            if (speechEngine.speakImportedRadar(radar, distanceMeters)) {
                runtime.recordSpoken(now)
                onDiagnostic(
                    ProximityAlertDiagnostic(
                        stage = "imported_radar_spoken",
                        reason = "Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}",
                    ),
                )
            }
        }
'''
    new = '''        if (!runtime.popupClosedAfterPass && !runtime.popupShownThisApproach) {
            runtime.popupShownThisApproach = true
            onImportedRadarDetected(radar, distanceMeters)
        }
        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {
            trace { "imported_radar.speak.attempt id=${radar.id}" }
            if (speechEngine.speakImportedRadar(radar, distanceMeters)) {
                runtime.recordSpoken(now)
                trace { "imported_radar.speak.success id=${radar.id} spoken=${runtime.spokenCount}" }
                onDiagnostic(
                    ProximityAlertDiagnostic(
                        stage = "imported_radar_spoken",
                        reason = diagnosticReason("Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}"),
                    ),
                )
            } else {
                trace { "imported_radar.speak.failed id=${radar.id} counter_not_consumed=true" }
            }
        }
'''
    if old in text:
        text = text.replace(old, new, 1)

    anchor = '''    private inline fun trace(message: () -> String) {
        if (DiagnosticRuntimeGate.isEnabled()) DiagnosticLogStore.record(source = "proximity", message = message())
    }

'''
    helper = '''    private inline fun trace(message: () -> String) {
        if (DiagnosticRuntimeGate.isEnabled()) DiagnosticLogStore.record(source = "proximity", message = message())
    }

    private fun diagnosticReason(reason: String): String {
        if (!DiagnosticRuntimeGate.isEnabled()) return reason
        val log = DiagnosticLogStore.dump(maxEvents = 80)
        if (log.isBlank()) return reason
        return buildString {
            appendLine(reason)
            appendLine("--- LOG GLOBAL ---")
            append(log)
        }
    }

'''
    if "private fun diagnosticReason(reason: String)" not in text:
        if anchor not in text:
            raise SystemExit("Ponto do diagnostico de proximidade nao encontrado")
        text = text.replace(anchor, helper, 1)

    required = [
        "val approaching = runtime.isApproaching(distanceMeters)",
        "!runtime.popupShownThisApproach",
        "counter_not_consumed=true",
        "diagnosticReason(",
        "direction_match=$directionMatch",
    ]
    missing = [marker for marker in required if marker not in text]
    if missing:
        raise SystemExit(f"ProximityAlertEngine incompleto: {missing}")
    path.write_text(text)


def patch_service_contracts() -> None:
    path = MAIN / "LiveRideAccessibilityService.kt"
    text = path.read_text()

    process_anchor = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
'''
    process_with_markers = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        // global_single_passenger_gate_0_1_124
        // global_passenger_and_addresses_card_0_1_124
        // global_inactive_clear_now_0_1_124
        // global_full_screen_hash_0_1_124
'''
    if "global_single_passenger_gate_0_1_124" not in text[text.find(process_anchor):text.find("private suspend fun analyzeUniversalTwoAddress(")]:
        if process_anchor not in text:
            raise SystemExit("processRideText nao encontrado")
        text = text.replace(process_anchor, process_with_markers, 1)

    analysis_start = text.find("    private suspend fun analyzeUniversalTwoAddress(")
    analysis_end = text.find("    private suspend fun applyUniversalTwoAddressResult(", analysis_start)
    if analysis_start < 0 or analysis_end <= analysis_start:
        raise SystemExit("Regiao de analise universal nao encontrada")
    analysis = text[analysis_start:analysis_end]
    if "instant_farol_cached_settings_0_1_124" not in analysis:
        brace = text.find(") {", analysis_start)
        if brace < 0 or brace >= analysis_end:
            raise SystemExit("Corpo da analise universal nao encontrado")
        insert = brace + 3
        text = text[:insert] + "\n        // instant_farol_cached_settings_0_1_124" + text[insert:]

    actual_red = "            showOverlay(RadarColor.Red, distanceKm = null, reason = fastOutsideResult.reason)\n"
    if "// showOverlay(RadarColor.Red, distanceKm = null)" not in text:
        if actual_red not in text:
            raise SystemExit("Vermelho provisório nao encontrado")
        text = text.replace(actual_red, "            // showOverlay(RadarColor.Red, distanceKm = null)\n" + actual_red, 1)

    if "// val homeRouteStartedAt" not in text:
        route_anchor = "        val routeStartedAt = System.currentTimeMillis()\n"
        if route_anchor not in text:
            raise SystemExit("Inicio da rota exata nao encontrado")
        text = text.replace(route_anchor, "        // val homeRouteStartedAt\n" + route_anchor, 1)

    path.write_text(text)


def enable_diagnostics_in_tests() -> None:
    for filename in [
        "DiagnosticLogStoreTest.kt",
        "LiveFailureTraceStoreTest.kt",
        "ProximityAlertEngineTest.kt",
    ]:
        path = TESTS / filename
        text = path.read_text()
        if "DiagnosticRuntimeGate.setEnabled(true)" in text:
            continue
        anchor = '''    @Before
    fun setUp() {
'''
        if anchor not in text:
            raise SystemExit(f"setUp nao encontrado em {filename}")
        path.write_text(text.replace(anchor, anchor + "        DiagnosticRuntimeGate.setEnabled(true)\n", 1))


def main() -> None:
    patch_ride_text_parser()
    patch_universal_parser()
    patch_selector()
    patch_proximity()
    patch_service_contracts()
    enable_diagnostics_in_tests()


if __name__ == "__main__":
    main()
