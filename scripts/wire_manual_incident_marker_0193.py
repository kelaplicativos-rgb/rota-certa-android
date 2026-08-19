#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
main = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/ManualIncidentMarker0193ContractTest.kt'

if not main.is_file():
    raise SystemExit(f'Arquivo obrigatório ausente: {main.relative_to(root)}')

text = main.read_text(encoding='utf-8')
old = 'onCreateReport = { supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt") },'
count = text.count(old)
if count != 2:
    raise SystemExit(f'MainActivity: esperado exatamente 2 pontos de geração manual do relatório, encontrado {count}')
new = '''onCreateReport = {
                                        FarolFlightRecorder0163.record(
                                            stage = "FORENSIC_USER_INCIDENT_MARK_0193",
                                            packageName = context.packageName,
                                            details = "source=manual_report_tap",
                                        )
                                        supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt")
                                    },'''
text = text.replace(old, new)
main.write_text(text, encoding='utf-8')

test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualIncidentMarker0193ContractTest {
    @Test
    fun `toque para gerar relatorio marca incidente antes do seletor de arquivo`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertEquals(2, Regex("FORENSIC_USER_INCIDENT_MARK_0193").findAll(source).count())
        assertEquals(2, Regex("source=manual_report_tap").findAll(source).count())
        val firstMarker = source.indexOf("FORENSIC_USER_INCIDENT_MARK_0193")
        val firstLaunch = source.indexOf("supportReportFileCreator.launch", firstMarker)
        assertTrue(firstMarker >= 0)
        assertTrue(firstLaunch > firstMarker)
    }
}
''', encoding='utf-8')

final = main.read_text(encoding='utf-8')
if final.count('FORENSIC_USER_INCIDENT_MARK_0193') != 2:
    raise SystemExit('MainActivity: marcador de incidente deve existir exatamente nos 2 fluxos manuais')
if final.count(old) != 0:
    raise SystemExit('MainActivity: restou lançamento de relatório sem marcador forense')

print('manual_incident_marker_0193=applied')
print('incident_mark_before_document_picker=true')
print('ui_flow_unchanged=true')
