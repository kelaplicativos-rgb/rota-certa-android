from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


main_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
main = main_path.read_text()
main = replace_once(
    main,
    '''                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                )''',
    '''                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )''',
    "tools route clipboard callback",
)
main = replace_once(
    main,
    '''@Composable
private fun ToolsScreen(onOpenBlaBlaCarCollector: () -> Unit) {''',
    '''@Composable
private fun ToolsScreen(
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {''',
    "tools screen signature",
)
main = replace_once(
    main,
    '''                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
    }
}''',
    '''                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text(
                    "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar area de transferencia")
                }
            }
        }
    }
}''',
    "clipboard card",
)
main = replace_once(
    main,
    '''private enum class LocationTarget { Home, Alternative }''',
    '''private fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }.onSuccess {
        Toast.makeText(context, "Area de transferencia limpa.", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Nao foi possivel limpar a area de transferencia.", Toast.LENGTH_SHORT).show()
    }
}

private enum class LocationTarget { Home, Alternative }''',
    "clearClipboard helper",
)
main_path.write_text(main)
