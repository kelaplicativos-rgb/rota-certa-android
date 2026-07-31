#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/BubbleShortcutModulesTest.kt'
catalog_file = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt'
templates_file = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/MessageTemplatesActivity.kt'
tools_file = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/RotaCertaTools0172.kt'
text = test.read_text(encoding='utf-8')

old_name = 'fun catalogProgressesFromThirteenToFourteenModulesWithoutCardModels()'
new_name = 'fun catalogIncludesQuickLinksAndKeepsSharedTemplateEditorOutsideTheGrid()'
old_count = 'assertEquals(if (hasManualCapture) 16 else 15, ids.size)'
new_count = 'assertEquals(if (hasManualCapture) 17 else 16, ids.size)'
anchor = '        assertTrue("A bolinha Financeiro precisa existir", "finance" in ids)\n'
addition = anchor + '        assertTrue("O módulo Links rápidos precisa existir", "quick_links" in ids)\n'

already_updated = (
    new_name in text
    and new_count in text
    and '"quick_links" in ids' in text
    and '"message_templates" in ids' not in text
)
if not already_updated:
    for expected, label in ((old_name, 'nome do teste'), (old_count, 'quantidade antiga'), (anchor, 'âncora Financeiro')):
        count = text.count(expected)
        if count != 1:
            raise SystemExit(f'{label}: esperado 1 ocorrência, encontrado {count}')

    text = text.replace(old_name, new_name, 1)
    text = text.replace(old_count, new_count, 1)
    text = text.replace(anchor, addition, 1)
    test.write_text(text, encoding='utf-8')
    print('Contrato do catálogo atualizado para 17 módulos na 0.1.172')
else:
    print('Contrato 0.1.172 do catálogo já atualizado')

if not templates_file.is_file() or templates_file.stat().st_size == 0:
    raise SystemExit('Editor compartilhado de frases não foi materializado')
templates = templates_file.read_text(encoding='utf-8')
if 'class MessageTemplatesActivity' not in templates:
    raise SystemExit('MessageTemplatesActivity ausente do editor compartilhado')

if not tools_file.is_file() or tools_file.stat().st_size == 0:
    raise SystemExit('Arquivo central das ferramentas 0.1.172 não foi materializado')
tools = tools_file.read_text(encoding='utf-8')
registry = '''

/** Registro compilado usado para identificar o conjunto funcional 0.1.172 no APK. */
object RotaCertaTools0172 {
    const val VERSION_NAME: String = "0.1.172"
    const val VERSION_CODE: Int = 5330
    const val QUICK_LINKS: Boolean = true
    const val EDITABLE_MESSAGE_TEMPLATES: Boolean = true
    const val SAFE_CACHE_CLEANING: Boolean = true
    const val ONE_SHOT_SCREEN_OCR: Boolean = true
    const val ACCESSIBILITY_RESILIENCE: Boolean = true
    const val TEMPORARY_INTENSIVE_DIAGNOSTICS: Boolean = true
}
'''
if 'object RotaCertaTools0172' not in tools:
    tools_file.write_text(tools.rstrip() + registry + '\n', encoding='utf-8')
    print('Registro compilado RotaCertaTools0172 adicionado')
else:
    print('Registro compilado RotaCertaTools0172 já existe')

catalog = catalog_file.read_text(encoding='utf-8')
list_match = re.search(
    r'object BubbleShortcutCatalog\s*\{.*?val modules: List<BubbleShortcutModule> = listOf\((.*?)\)\s*\n',
    catalog,
    flags=re.S,
)
if not list_match:
    raise SystemExit('Não foi possível localizar a lista materializada do catálogo')
module_objects = re.findall(r'^\s*([A-Za-z0-9_]+BubbleShortcutModule),?\s*$', list_match.group(1), flags=re.M)
required_match = re.search(r'require\(modules\.size == (\d+)\)', catalog)
print(f'CATALOGO_0172_QUANTIDADE={len(module_objects)}')
print('CATALOGO_0172_OBJETOS=' + ','.join(module_objects))
print('CATALOGO_0172_REQUIRE=' + (required_match.group(1) if required_match else 'ausente'))
print('EDITOR_FRASES_0172=MessageTemplatesActivity')
print('REGISTRO_FERRAMENTAS_0172=RotaCertaTools0172')
