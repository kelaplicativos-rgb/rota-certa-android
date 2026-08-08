#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
gradle = root / 'app/build.gradle.kts'
policy = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt'
engine = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/ProximityAlertsNoDirection0191ContractTest.kt'

for path in (gradle, policy, engine):
    if not path.is_file():
        raise SystemExit(f'Arquivo obrigatório ausente: {path.relative_to(root)}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado exatamente 1 ocorrência, encontrado {count}: {old!r}')
    return text.replace(old, new, 1)


def replace_once_between(
    text: str,
    start_anchor: str,
    end_anchor: str,
    old: str,
    new: str,
    label: str,
) -> str:
    start_count = text.count(start_anchor)
    end_count = text.count(end_anchor)
    if start_count != 1:
        raise SystemExit(
            f'{label}: âncora inicial esperada exatamente 1 vez, encontrada {start_count}: {start_anchor!r}'
        )
    if end_count != 1:
        raise SystemExit(
            f'{label}: âncora final esperada exatamente 1 vez, encontrada {end_count}: {end_anchor!r}'
        )
    start = text.index(start_anchor)
    end = text.index(end_anchor, start + len(start_anchor))
    segment = text[start:end]
    count = segment.count(old)
    if count != 1:
        raise SystemExit(
            f'{label}: esperado exatamente 1 ocorrência no bloco semântico, encontrado {count}: {old!r}'
        )
    segment = segment.replace(old, new, 1)
    return text[:start] + segment + text[end:]


gradle_text = gradle.read_text(encoding='utf-8')
gradle_text = replace_once(gradle_text, 'versionCode = 5474', 'versionCode = 5475', 'versionCode')
gradle_text = replace_once(gradle_text, 'versionName = "0.1.190"', 'versionName = "0.1.191"', 'versionName')
gradle.write_text(gradle_text, encoding='utf-8')

policy_text = policy.read_text(encoding='utf-8')
policy_text = replace_once(
    policy_text,
    '''    ): Boolean =\n        nowMillis - fix.timestampMillis in 0L..MAX_FIX_AGE_MILLIS &&\n            fix.accuracyMeters in 0.1..maxAcceptedAccuracyMeters(thresholdMeters) &&\n            fix.speedMetersPerSecond >= MIN_MOVING_SPEED_MPS &&\n            fix.headingDegrees != null\n''',
    '''    ): Boolean =\n        nowMillis - fix.timestampMillis in 0L..MAX_FIX_AGE_MILLIS &&\n            fix.accuracyMeters in 0.1..maxAcceptedAccuracyMeters(thresholdMeters) &&\n            fix.speedMetersPerSecond >= MIN_MOVING_SPEED_MPS\n''',
    'GPS utilizável sem heading',
)
policy_text = replace_once(
    policy_text,
    '''    fun hasPassed(\n        headingDegrees: Double?,\n        bearingToTargetDegrees: Double,\n        minimumDistanceMeters: Double,\n        currentDistanceMeters: Double,\n        increasingSamples: Int,\n    ): Boolean {\n        val heading = headingDegrees ?: return false\n        val targetBehind = GeoDistance.angleDifferenceDegrees(heading, bearingToTargetDegrees) >= TARGET_BEHIND_DEGREES\n        val movedAway = currentDistanceMeters >= minimumDistanceMeters + PASS_DISTANCE_INCREASE_METERS\n        return targetBehind && movedAway && increasingSamples >= REQUIRED_INCREASING_SAMPLES\n    }\n''',
    '''    fun hasPassed(\n        headingDegrees: Double?,\n        bearingToTargetDegrees: Double,\n        minimumDistanceMeters: Double,\n        currentDistanceMeters: Double,\n        increasingSamples: Int,\n    ): Boolean = hasPassedByDistance(\n        minimumDistanceMeters = minimumDistanceMeters,\n        currentDistanceMeters = currentDistanceMeters,\n        increasingSamples = increasingSamples,\n    )\n\n    fun hasPassedByDistance(\n        minimumDistanceMeters: Double,\n        currentDistanceMeters: Double,\n        increasingSamples: Int,\n    ): Boolean {\n        val movedAway = currentDistanceMeters >= minimumDistanceMeters + PASS_DISTANCE_INCREASE_METERS\n        return movedAway && increasingSamples >= REQUIRED_INCREASING_SAMPLES\n    }\n''',
    'ultrapassagem por tendência de distância',
)
policy.write_text(policy_text, encoding='utf-8')

engine_text = engine.read_text(encoding='utf-8')
engine_text = replace_once(
    engine_text,
    ''' * Só considera um alvo quando o GPS é recente e preciso, existe rumo confiável,\n * o alvo está à frente e a distância confirma aproximação. Radares importados\n * também precisam respeitar a direção indicada no arquivo do MapaRadar.\n''',
    ''' * Considera um alvo quando o GPS é recente e preciso e a distância confirma\n * aproximação. O aviso não depende de heading, azimute nem da direção cadastrada\n * no radar; a tendência de distância é a autoridade para aproximação e passagem.\n''',
    'documentação do motor',
)
engine_text = replace_once(engine_text, 'status = "Confirmando GPS e direção",', 'status = "Confirmando GPS",', 'status GPS')
engine_text = replace_once(
    engine_text,
    'reason = "${visual.title} a ${selected.distanceMeters.roundToInt()} metros; direção confirmada.",',
    'reason = "${visual.title} a ${selected.distanceMeters.roundToInt()} metros; aproximação confirmada.",',
    'diagnóstico sem direção',
)

old_target_ahead = '        val targetAhead = DirectionalAlertPolicy.isTargetAhead(fix.headingDegrees, bearingToTarget)\n'
old_has_passed = '        if (runtime.hasPassed(fix.headingDegrees, bearingToTarget, distance)) {\n'
new_has_passed = '        if (runtime.hasPassed(distance)) {\n'

saved_bearing = '        val bearingToTarget = GeoDistance.bearingDegrees(fix.coordinate, alert.coordinate)\n'
saved_eligible = '''        val eligible = distance <= threshold &&\n            targetAhead &&\n            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&\n            !runtime.passed\n'''
saved_eligible_without_direction = '''        val eligible = distance <= threshold &&\n            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&\n            !runtime.passed\n'''
engine_text = replace_once_between(
    engine_text,
    saved_bearing,
    saved_eligible,
    old_target_ahead,
    '',
    'gate alvo à frente do alerta salvo no bloco semântico correto',
)
engine_text = replace_once_between(
    engine_text,
    saved_bearing,
    saved_eligible,
    old_has_passed,
    new_has_passed,
    'passagem do alerta salvo no bloco semântico correto',
)
engine_text = replace_once(engine_text, saved_bearing, '', 'bearing alerta salvo')
engine_text = replace_once(
    engine_text,
    saved_eligible,
    saved_eligible_without_direction,
    'elegibilidade alerta salvo',
)

radar_bearing = '        val bearingToTarget = GeoDistance.bearingDegrees(fix.coordinate, radar.coordinate)\n'
radar_direction_match = '        val radarDirectionMatch = DirectionalAlertPolicy.radarDirectionMatches(radar, fix.headingDegrees)\n'
radar_eligible = '''        val eligible = distance <= threshold &&\n            targetAhead &&\n            radarDirectionMatch &&\n            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&\n            !runtime.passed\n'''
radar_eligible_without_direction = '''        val eligible = distance <= threshold &&\n            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&\n            !runtime.passed\n'''
engine_text = replace_once_between(
    engine_text,
    radar_bearing,
    radar_eligible,
    old_target_ahead,
    '',
    'gate alvo à frente do radar no bloco semântico correto',
)
engine_text = replace_once_between(
    engine_text,
    radar_bearing,
    radar_eligible,
    radar_direction_match,
    '',
    'gate de direção cadastrada do radar no bloco semântico correto',
)
engine_text = replace_once_between(
    engine_text,
    radar_bearing,
    radar_eligible,
    old_has_passed,
    new_has_passed,
    'passagem do radar no bloco semântico correto',
)
engine_text = replace_once(engine_text, radar_bearing, '', 'bearing radar')
engine_text = replace_once(
    engine_text,
    radar_eligible,
    radar_eligible_without_direction,
    'elegibilidade radar',
)
engine_text = replace_once(engine_text, 'status = "Aproximando — sentido confirmado",', 'status = "Aproximando",', 'status aproximação')
engine_text = replace_once(
    engine_text,
    '''        fun hasPassed(headingDegrees: Double?, bearingToTargetDegrees: Double, distanceMeters: Double): Boolean =\n            insideZone && DirectionalAlertPolicy.hasPassed(\n                headingDegrees = headingDegrees,\n                bearingToTargetDegrees = bearingToTargetDegrees,\n                minimumDistanceMeters = minimumDistanceMeters,\n                currentDistanceMeters = distanceMeters,\n                increasingSamples = increasingSamples,\n            )\n''',
    '''        fun hasPassed(distanceMeters: Double): Boolean =\n            insideZone && DirectionalAlertPolicy.hasPassedByDistance(\n                minimumDistanceMeters = minimumDistanceMeters,\n                currentDistanceMeters = distanceMeters,\n                increasingSamples = increasingSamples,\n            )\n''',
    'runtime passagem sem heading',
)

if engine_text.count(new_has_passed) != 2:
    raise SystemExit(
        f'passagem por distância: esperado exatamente 2 fluxos materializados, encontrado {engine_text.count(new_has_passed)}'
    )
if old_has_passed in engine_text:
    raise SystemExit('passagem por distância: chamada antiga com heading permaneceu no motor')
if 'DirectionalAlertPolicy.isTargetAhead(' in engine_text:
    raise SystemExit('gate alvo à frente permaneceu no motor após materialização')
if 'DirectionalAlertPolicy.radarDirectionMatches(' in engine_text:
    raise SystemExit('gate de direção cadastrada do radar permaneceu no motor após materialização')

engine.write_text(engine_text, encoding='utf-8')

# Regressão estrutural: protege exatamente a retirada do filtro de sentido sem
# acoplar os testes JVM ao GPS/WindowManager do Android.
test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityAlertsNoDirection0191ContractTest {
    private val policy = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt").readText()
    private val engine = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()

    @Test
    fun `gps utilizavel nao exige heading para avisar proximidade`() {
        val isFixUsable = policy.substringAfter("fun isFixUsable(").substringBefore("fun isTargetAhead(")
        assertFalse(isFixUsable.contains("fix.headingDegrees != null"))
        assertTrue(isFixUsable.contains("fix.accuracyMeters"))
        assertTrue(isFixUsable.contains("fix.speedMetersPerSecond"))
    }

    @Test
    fun `radar e alerta nao usam gate de sentido na elegibilidade`() {
        assertFalse(engine.contains("DirectionalAlertPolicy.isTargetAhead("))
        assertFalse(engine.contains("DirectionalAlertPolicy.radarDirectionMatches("))
        assertFalse(engine.contains("targetAhead &&"))
        assertFalse(engine.contains("radarDirectionMatch &&"))
        assertTrue(engine.contains("runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES"))
    }

    @Test
    fun `passagem e detectada por afastamento depois do ponto sem heading`() {
        val hasPassedByDistance = policy.substringAfter("fun hasPassedByDistance(").substringBefore("fun isApproaching(")
        assertFalse(hasPassedByDistance.contains("headingDegrees"))
        assertFalse(hasPassedByDistance.contains("bearingToTargetDegrees"))
        assertTrue(hasPassedByDistance.contains("PASS_DISTANCE_INCREASE_METERS"))
        assertTrue(hasPassedByDistance.contains("REQUIRED_INCREASING_SAMPLES"))
        assertEquals(2, engine.split("runtime.hasPassed(distance)").size - 1)
        assertFalse(engine.contains("runtime.hasPassed(fix.headingDegrees"))
        assertTrue(engine.contains("DirectionalAlertPolicy.hasPassedByDistance("))
    }

    @Test
    fun `assinatura antiga de passagem permanece compativel sem decidir por sentido`() {
        val compatibility = policy.substringAfter("fun hasPassed(").substringBefore("fun hasPassedByDistance(")
        assertTrue(compatibility.contains("headingDegrees: Double?"))
        assertTrue(compatibility.contains("bearingToTargetDegrees: Double"))
        assertTrue(compatibility.contains("= hasPassedByDistance("))
        assertFalse(compatibility.contains("targetBehind"))
    }

    @Test
    fun `contrato visual de tres segundos e fechamento manual continua preservado`() {
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
        assertFalse(engine.contains("sentido confirmado"))
        assertFalse(engine.contains("direção confirmada"))
        assertTrue(engine.contains("status = \"Aproximando\""))
        assertTrue(engine.contains("mutedUntilExit"))
        assertTrue(engine.contains("resetAfterExit"))
    }
}
''', encoding='utf-8')

print('proximity_alerts_no_direction_0191=applied')
print('heading_gate=false')
print('radar_direction_gate=false')
print('approach_basis=distance_trend')
print('passed_basis=distance_increase_after_minimum')
print('legacy_has_passed_signature=preserved')
print('distance_passage_flows=2')
