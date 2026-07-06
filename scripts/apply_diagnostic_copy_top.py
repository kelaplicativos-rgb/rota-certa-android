from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


main_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
main = main_path.read_text()
button_block = '''            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa diagnostico", diagnostic.toShareText()))
                    Toast.makeText(context, "Diagnostico copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copiar diagnostico")
            }
'''
main = replace_once(
    main,
    button_block,
    "",
    "old diagnostic copy button removal",
)
main = replace_once(
    main,
    '''        Text("Cards cadastrados: ${cardTemplates.size}", style = MaterialTheme.typography.bodySmall)
        if (diagnostic == null) {
            Text("Nenhum diagnostico registrado ainda. Ative a leitura e abra um card de corrida.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Cor: ${diagnostic.bubbleColor}")''',
    '''        Text("Cards cadastrados: ${cardTemplates.size}", style = MaterialTheme.typography.bodySmall)
        if (diagnostic == null) {
            Text("Nenhum diagnostico registrado ainda. Ative a leitura e abra um card de corrida.", style = MaterialTheme.typography.bodySmall)
        } else {
''' + button_block + '''            Text("Cor: ${diagnostic.bubbleColor}")''',
    "top diagnostic copy button insertion",
)
main_path.write_text(main)
