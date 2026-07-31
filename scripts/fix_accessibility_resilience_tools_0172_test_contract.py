#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/BubbleShortcutModulesTest.kt'
text = test.read_text(encoding='utf-8')

old_name = 'fun catalogProgressesFromThirteenToFourteenModulesWithoutCardModels()'
new_name = 'fun catalogIncludesQuickLinksAndEditableTemplatesWithoutCardModels()'
old_count = 'assertEquals(if (hasManualCapture) 16 else 15, ids.size)'
new_count = 'assertEquals(if (hasManualCapture) 18 else 17, ids.size)'
anchor = '        assertTrue("A bolinha Financeiro precisa existir", "finance" in ids)\n'
addition = (
    anchor
    + '        assertTrue("O módulo Links rápidos precisa existir", "quick_links" in ids)\n'
    + '        assertTrue("O gerenciador de frases precisa existir", "message_templates" in ids)\n'
)

if new_name in text and new_count in text and '"quick_links" in ids' in text and '"message_templates" in ids' in text:
    print('Contrato 0.1.172 do catálogo já atualizado')
    raise SystemExit(0)

for expected, label in ((old_name, 'nome do teste'), (old_count, 'quantidade antiga'), (anchor, 'âncora Financeiro')):
    count = text.count(expected)
    if count != 1:
        raise SystemExit(f'{label}: esperado 1 ocorrência, encontrado {count}')

text = text.replace(old_name, new_name, 1)
text = text.replace(old_count, new_count, 1)
text = text.replace(anchor, addition, 1)
test.write_text(text, encoding='utf-8')
print('Contrato do catálogo atualizado para 18 módulos na 0.1.172')
