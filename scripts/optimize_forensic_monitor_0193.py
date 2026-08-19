#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
monitor = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/ForensicHotPath0193ContractTest.kt'

if not monitor.is_file():
    raise SystemExit(f'Arquivo obrigatório ausente: {monitor.relative_to(root)}')

text = monitor.read_text(encoding='utf-8')

old_fingerprint = '        val fingerprint = 31 * stage.hashCode() + details.hashCode()\n'
new_fingerprint = '        val fingerprint = 31 * (31 * stage.hashCode() + details.hashCode()) + (packageName?.hashCode() ?: 0)\n'
if text.count(old_fingerprint) != 1:
    raise SystemExit('Monitor: fingerprint esperado ausente ou ambíguo')
text = text.replace(old_fingerprint, new_fingerprint, 1)

# Troca o bloco de helpers por intervalo sem depender de escaping/indentação interna.
# O materializador base precisa expor exatamente um início e um fim reconhecíveis.
start_anchor = '    private fun isResultStage(stage: String): Boolean {'
end_anchor = '    private fun anomaly(packageName: String?, stage: String, details: String) {'
if text.count(start_anchor) != 1:
    raise SystemExit(f'Monitor: início do bloco de parsing esperado exatamente 1 vez, encontrado {text.count(start_anchor)}')
if text.count(end_anchor) != 1:
    raise SystemExit(f'Monitor: fim do bloco de parsing esperado exatamente 1 vez, encontrado {text.count(end_anchor)}')
start = text.index(start_anchor)
end = text.index(end_anchor)
if start >= end:
    raise SystemExit('Monitor: intervalo de parsing inválido')
old_interval = text[start:end]
for required in (
    'private fun isResultStage(',
    'private fun numericToken(',
    'private fun safeStage(',
    'Regex(',
):
    if old_interval.count(required) < 1:
        raise SystemExit(f'Monitor: contrato base ausente no intervalo de parsing: {required}')
if old_interval.count('private fun numericToken(') != 1 or old_interval.count('private fun safeStage(') != 1:
    raise SystemExit('Monitor: helpers de parsing base estão ambíguos')

new_interval = '''    private fun isResultStage(stage: String): Boolean =
        stage.contains("DECISION") ||
            stage.contains("RESULT") ||
            stage.contains("OVERLAY_RENDER_APPLIED") ||
            stage.contains("ROUTE_APPLIED") ||
            stage.contains("CACHE_APPLIED")

    /** Parser sem Regex/substrings no caminho comum do gravador. */
    private fun numericToken(details: String, key: String): Long? {
        val needle = "$key="
        var searchFrom = 0
        while (searchFrom < details.length) {
            val start = details.indexOf(needle, searchFrom)
            if (start < 0) return null
            val boundaryOk = start == 0 || details[start - 1] == ';' || details[start - 1] == ' ' || details[start - 1] == ','
            if (!boundaryOk) {
                searchFrom = start + needle.length
                continue
            }
            var index = start + needle.length
            var negative = false
            if (index < details.length && details[index] == '-') {
                negative = true
                index += 1
            }
            val digitStart = index
            var value = 0L
            while (index < details.length) {
                val ch = details[index]
                if (ch !in '0'..'9') break
                val digit = ch.code - '0'.code
                if (value > (Long.MAX_VALUE - digit) / 10L) return null
                value = value * 10L + digit
                index += 1
            }
            if (index == digitStart) return null
            return if (negative) -value else value
        }
        return null
    }

    private fun safeStage(stage: String): String = buildString(minOf(stage.length, 96)) {
        var index = 0
        while (index < stage.length && index < 96) {
            val ch = stage[index]
            append(if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-') ch else '_')
            index += 1
        }
    }

'''
text = text[:start] + new_interval + text[end:]
monitor.write_text(text, encoding='utf-8')

test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicHotPath0193ContractTest {
    @Test
    fun `caminho comum do monitor nao instancia regex nem faz polling`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertFalse(source.contains("Regex("))
        assertFalse(source.contains("scheduleAtFixedRate"))
        assertFalse(source.contains("Timer("))
        assertFalse(source.contains("while (true)"))
        assertTrue(source.contains("details.indexOf(needle, searchFrom)"))
        assertTrue(source.contains("SystemClock.elapsedRealtimeNanos()"))
    }

    @Test
    fun `parser otimizado preserva delimitadores e escopo por pacote`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("details[start - 1] == ';'"))
        assertTrue(source.contains("details[start - 1] == ' '"))
        assertTrue(source.contains("details[start - 1] == ','"))
        assertTrue(source.contains("packageName?.hashCode() ?: 0"))
    }
}
''', encoding='utf-8')

final = monitor.read_text(encoding='utf-8')
if 'Regex(' in final:
    raise SystemExit('Monitor: Regex ainda presente no caminho forense')
if final.count('details.indexOf(needle, searchFrom)') != 1:
    raise SystemExit('Monitor: parser manual não ficou único')
if final.count('private fun numericToken(') != 1 or final.count('private fun safeStage(') != 1:
    raise SystemExit('Monitor: helpers otimizados não ficaram únicos')

print('forensic_hot_path_0193=optimized')
print('regex_in_event_path=false')
print('package_scoped_fingerprint=true')
