#!/usr/bin/env python3
# HOME_BUBBLE_GRID_BUILD_TRIGGER_0175
# HOME_BUBBLE_GRID_VALIDATION_TRIGGER_0175
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
main_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
build_path = root / "app/build.gradle.kts"
contract_test_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutLongPressContract0171Test.kt"
policy_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/HomeModuleBubbleGridPolicy0175.kt"
policy_test_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/HomeModuleBubbleGridPolicy0175Test.kt"

main = main_path.read_text(encoding="utf-8")
start_marker = "@Composable\nprivate fun ShortcutModulesHome0171("
end_marker = "@Composable\nprivate fun InlineModuleAction0174("
if start_marker not in main or end_marker not in main:
    raise SystemExit("Home 0.1.174 nao encontrada para aplicar a grade 0.1.175")

start = main.index(start_marker)
end = main.index(end_marker, start)
new_home = '''@Composable
private fun ShortcutModulesHome0171(
    expandedModuleId: String?,
    onToggleModule: (BubbleShortcutSpec) -> Unit,
    moduleContent: @Composable (BubbleShortcutSpec) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        check(HomeModuleBubbleGridPolicy0175.CONTRACT_MARKER.isNotBlank())
        ShortcutGridPolicy0173.clearLegacyPreferences(context)
    }
    val moduleRows = remember {
        HomeModuleBubbleGridPolicy0175.rows(BubbleShortcutCatalog.modules)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Módulos e recursos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "A Home possui uma bolinha para cada módulo e recurso. Toque uma vez para abrir os controles logo abaixo da mesma fileira.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Grade da Home", fontWeight = FontWeight.Bold)
        Text(
            "Esta grade é o catálogo completo do Rota Certa. A grade flutuante continua separada e executa apenas as ações rápidas sobre outros aplicativos.",
            style = MaterialTheme.typography.bodySmall,
        )
        moduleRows.forEach { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowModules.forEach { module ->
                    HomeModuleBubble0175(
                        spec = module.spec,
                        selected = HomeModuleExpansionPolicy0174.isExpanded(expandedModuleId, module.spec.id),
                        onClick = { onToggleModule(module.spec) },
                    )
                }
            }
            val expandedIdForRow = HomeModuleBubbleGridPolicy0175.expandedIdInRow(
                rowIds = rowModules.map { it.spec.id },
                expandedId = expandedModuleId,
            )
            val expandedModule = rowModules.firstOrNull { it.spec.id == expandedIdForRow }
            if (expandedModule != null) {
                HomeModuleInlinePanel0175(
                    spec = expandedModule.spec,
                    content = { moduleContent(expandedModule.spec) },
                )
            }
        }
    }
} // home_module_bubble_grid_0_1_175

@Composable
private fun HomeModuleBubble0175(
    spec: BubbleShortcutSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(96.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 12.dp else 3.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(6.dp),
    ) {
        Text(
            text = spec.emoji + "\\n" + spec.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun HomeModuleInlinePanel0175(
    spec: BubbleShortcutSpec,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                spec.emoji + "  " + spec.displayLabel,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(ShortcutGridPolicy0173.description(spec), style = MaterialTheme.typography.bodySmall)
            content()
            Text(
                "Na grade flutuante: ${ShortcutGridPolicy0173.fixedBehaviorLabel(spec)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

'''
main = main[:start] + new_home + main[end:]
main_path.write_text(main, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 5350', 'versionCode = 5360')
build = build.replace('versionName = "0.1.174"', 'versionName = "0.1.175"')
if 'versionCode = 5360' not in build or 'versionName = "0.1.175"' not in build:
    raise SystemExit("Nao foi possivel atualizar a versao para 0.1.175")
build_path.write_text(build, encoding="utf-8")

policy_path.write_text('''package br.com.mapeiaia.rotacerta

object HomeModuleBubbleGridPolicy0175 {
    const val CONTRACT_MARKER = "HOME_MODULE_BUBBLE_GRID_0175"
    const val COLUMNS = 3

    fun <T> rows(items: List<T>): List<List<T>> = items.chunked(COLUMNS)

    fun expandedIdInRow(rowIds: List<String>, expandedId: String?): String? =
        expandedId?.takeIf(rowIds::contains)
}
''', encoding="utf-8")

policy_test_path.write_text('''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeModuleBubbleGridPolicy0175Test {
    @Test
    fun seventeenModulesCreateSixOrderedRows() {
        val ids = (1..17).map { "module-$it" }
        val rows = HomeModuleBubbleGridPolicy0175.rows(ids)

        assertEquals(6, rows.size)
        assertEquals(listOf("module-1", "module-2", "module-3"), rows.first())
        assertEquals(listOf("module-16", "module-17"), rows.last())
        assertEquals(ids, rows.flatten())
    }

    @Test
    fun expandedContentBelongsOnlyToItsOwnRow() {
        val first = listOf("a", "b", "c")
        val second = listOf("d", "e", "f")

        assertEquals("e", HomeModuleBubbleGridPolicy0175.expandedIdInRow(second, "e"))
        assertNull(HomeModuleBubbleGridPolicy0175.expandedIdInRow(first, "e"))
        assertNull(HomeModuleBubbleGridPolicy0175.expandedIdInRow(second, null))
    }
}
''', encoding="utf-8")

contract = contract_test_path.read_text(encoding="utf-8")
old = '        assertTrue(main.contains("BubbleShortcutCatalog.modules.forEach"))\n'
new = '''        assertTrue(main.contains("HomeModuleBubbleGridPolicy0175.rows"))
        assertTrue(main.contains("HomeModuleBubble0175"))
        assertTrue(main.contains("HomeModuleInlinePanel0175"))
        assertTrue(main.contains("uma bolinha para cada módulo e recurso"))
        assertFalse(main.contains("ShortcutModuleCard0174"))
'''
if old not in contract:
    raise SystemExit("Contrato legado da Home nao encontrado")
contract = contract.replace(old, new)
contract_test_path.write_text(contract, encoding="utf-8")

print("HOME_MODULE_BUBBLE_GRID_0175 applied")
