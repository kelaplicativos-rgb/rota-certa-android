// Checklist 6 — biblioteca visual sem duplicar modelos manuais.

fun replaceCaptureUiFunctionChecklist6(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função da galeria ausente no checklist 6: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo da galeria ausente no checklist 6.")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da galeria ausente no checklist 6.")
}

fun patchCaptureLibraryUiFinalChecklist6(mainFile: java.io.File) {
    if (!mainFile.exists()) throw GradleException("MainActivity.kt ausente para a biblioteca de capturas.")
    var main = mainFile.readText()
    val dollar = "$"

    val replacement = """@Composable
private fun AutomaticRideCaptureGallery129() {
    val context129 = LocalContext.current
    val store129 = remember { AutomaticRideCaptureStore(context129) }
    val captures129 by remember(store129) { store129.capturesFlow() }
        .collectAsState(initial = store129.list())
    val scope129 = rememberCoroutineScope()
    var enabled129 by remember { mutableStateOf(store129.isEnabled()) }
    val candidates129 = captures129.filter { it.kind == AutomaticRideCaptureKind.Candidate }
    val matched129 = captures129.filter { it.kind == AutomaticRideCaptureKind.Matched }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Captura automática dos cards", fontWeight = FontWeight.Bold)
        Text(
            "A captura nunca concorre com a cor ou com o quilômetro. Candidatas são registradas somente depois que a tela permanece estável; cards reconhecidos são fotografados depois que o farol termina.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled129) "Captura automática ativa" else "Captura automática pausada")
            Switch(
                checked = enabled129,
                onCheckedChange = { checked129 ->
                    enabled129 = checked129
                    store129.setEnabled(checked129)
                },
            )
        }

        Text("Candidatas a modelo: ${dollar}{candidates129.size}", fontWeight = FontWeight.Bold)
        Text(
            "São ofertas reconhecidas no aplicativo selecionado que ainda não correspondem a um modelo manual. Expiram em 7 dias.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (candidates129.isEmpty()) {
            Text("Nenhuma candidata aguardando confirmação.", style = MaterialTheme.typography.bodySmall)
        } else {
            candidates129.take(6).forEach { capture129 ->
                AutomaticRideCaptureCardChecklist6(
                    capture = capture129,
                    store = store129,
                    onPromote = {
                        scope129.launch {
                            val template129 = RideCardTemplateMatcher.createTemplate(
                                capture129.packageName,
                                capture129.textPreview,
                                "Card confirmado ${dollar}{capture129.packageName.substringAfterLast('.')}",
                            )
                            SettingsRepository(context129).addCardTemplate(template129)
                            store129.consumePromotedCandidate(capture129.id)
                            Toast.makeText(context129, "Modelo cadastrado e captura temporária removida.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            if (candidates129.size > 6) {
                Text("Mostrando as 6 candidatas mais recentes.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Cards já reconhecidos: ${dollar}{matched129.size}", fontWeight = FontWeight.Bold)
        Text(
            "Já possuem modelo correspondente. Servem apenas para conferência temporária e não criam modelos duplicados. Expiram em 14 dias.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (matched129.isEmpty()) {
            Text("Nenhum card reconhecido capturado ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            matched129.take(6).forEach { capture129 ->
                AutomaticRideCaptureCardChecklist6(
                    capture = capture129,
                    store = store129,
                    onPromote = null,
                )
            }
            if (matched129.size > 6) {
                Text("Mostrando os 6 reconhecidos mais recentes.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (captures129.isNotEmpty()) {
            OutlinedButton(
                onClick = { store129.clearAll() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apagar todas as capturas temporárias") }
        }
    }
} // capture_library_split_final_checklist_6

@Composable
private fun AutomaticRideCaptureCardChecklist6(
    capture: AutomaticRideCapture,
    store: AutomaticRideCaptureStore,
    onPromote: (() -> Unit)?,
) {
    val context129 = LocalContext.current
    val preview129 = remember(capture.id, capture.imageFileName) {
        BitmapFactory.decodeFile(store.imageFile(capture).absolutePath)?.asImageBitmap()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            preview129?.let { image129 ->
                Image(
                    bitmap = image129,
                    contentDescription = "Captura temporária de card",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(capture.packageName, fontWeight = FontWeight.Bold)
            if (capture.kind == AutomaticRideCaptureKind.Matched) {
                Text(
                    "Modelo reconhecido: ${dollar}{capture.matchedTemplateName ?: capture.matchedTemplateId ?: "modelo manual"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            capture.fare?.let { Text("Valor: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
            capture.pickup?.let { Text("Embarque: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
            capture.destination?.let { Text("Destino: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
            val remainingDays129 = ((capture.expiresAtMillis - System.currentTimeMillis()) /
                (24L * 60L * 60L * 1_000L)).coerceAtLeast(0L) + 1L
            Text("Exclusão automática em até ${dollar}remainingDays129 dia(s).", style = MaterialTheme.typography.bodySmall)

            if (onPromote != null) {
                Button(onClick = onPromote, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirmar e cadastrar como modelo")
                }
            }

            capture.destination?.let { destination129 ->
                OutlinedButton(
                    onClick = {
                        val uri129 = Uri.parse("geo:0,0?q=" + Uri.encode(destination129))
                        val mapsIntent129 = Intent(Intent.ACTION_VIEW, uri129).setPackage("com.google.android.apps.maps")
                        runCatching { context129.startActivity(mapsIntent129) }
                            .recoverCatching { context129.startActivity(Intent(Intent.ACTION_VIEW, uri129)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abrir destino no mapa") }
            }

            OutlinedButton(
                onClick = {
                    val details129 = buildString {
                        capture.fare?.let { appendLine("Valor: ${dollar}it") }
                        capture.pickup?.let { appendLine("Embarque: ${dollar}it") }
                        capture.destination?.let { appendLine("Destino: ${dollar}it") }
                    }.trim()
                    val clipboard129 = context129.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard129.setPrimaryClip(ClipData.newPlainText("Detalhes da corrida", details129))
                    Toast.makeText(context129, "Detalhes copiados.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copiar detalhes") }

            OutlinedButton(
                onClick = { store.delete(capture.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apagar captura") }
        }
    }
} // capture_card_component_final_checklist_6
"""

    main = replaceCaptureUiFunctionChecklist6(
        source = main,
        signature = "private fun AutomaticRideCaptureGallery129()",
        replacement = replacement,
    )

    listOf(
        "capture_library_split_final_checklist_6",
        "capture_card_component_final_checklist_6",
        "Candidatas a modelo",
        "Cards já reconhecidos",
        "Confirmar e cadastrar como modelo",
        "não criam modelos duplicados",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Contrato da biblioteca de capturas ausente: ${dollar}marker")
    }

    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchCaptureLibraryUiFinalChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
