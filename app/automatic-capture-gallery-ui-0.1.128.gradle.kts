// Rota Certa 0.1.128 — galeria privada de capturas automaticas.
// Este patch altera somente a interface. A limpeza periodica e aplicada no
// finalizador do servico, depois que os campos da captura ja foram criados.

fun findGalleryFunctionEnd128(source: String, start: Int): Int {
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo de funcao nao encontrado.")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return index + 1
            }
        }
        index += 1
    }
    throw GradleException("Fim de funcao nao encontrado.")
}

fun patchAutomaticCaptureGalleryUi128(mainFile: java.io.File) {
    if (!mainFile.exists()) throw GradleException("MainActivity.kt ausente para galeria automatica.")
    var main = mainFile.readText()
    if ("automatic_capture_gallery_ui_0_1_128" in main) return

    val bitmapImportAnchor = "import android.content.Intent\n"
    if (bitmapImportAnchor !in main) throw GradleException("Import de Intent nao encontrado.")
    main = main.replaceFirst(
        bitmapImportAnchor,
        bitmapImportAnchor + "import android.graphics.BitmapFactory\n",
    )

    val imageImportAnchor = "import androidx.compose.foundation.layout.Arrangement\n"
    if (imageImportAnchor !in main) throw GradleException("Imports Compose nao encontrados.")
    main = main.replaceFirst(
        imageImportAnchor,
        "import androidx.compose.foundation.Image\n" + imageImportAnchor,
    )

    val graphicsImportAnchor = "import androidx.compose.ui.Modifier\n"
    if (graphicsImportAnchor !in main) throw GradleException("Import de Modifier nao encontrado.")
    main = main.replaceFirst(
        graphicsImportAnchor,
        graphicsImportAnchor +
            "import androidx.compose.ui.graphics.asImageBitmap\n" +
            "import androidx.compose.ui.layout.ContentScale\n",
    )

    val cardStart = main.indexOf("@Composable\nprivate fun CardModelsCard(")
    if (cardStart < 0) throw GradleException("CardModelsCard final nao encontrado.")
    val cardEnd = findGalleryFunctionEnd128(main, cardStart)
    var cardRegion = main.substring(cardStart, cardEnd)
    val closeAnchor = "        }\n    }\n}"
    val closeIndex = cardRegion.lastIndexOf(closeAnchor)
    if (closeIndex < 0) throw GradleException("Fechamento do CardModelsCard nao encontrado.")
    cardRegion = cardRegion.substring(0, closeIndex) +
        """            Spacer(Modifier.height(10.dp))
            AutomaticRideCapturesCard128() // automatic_capture_gallery_ui_0_1_128
""" + cardRegion.substring(closeIndex)
    main = main.substring(0, cardStart) + cardRegion + main.substring(cardEnd)

    val helperAnchor = """@Composable
private fun DiagnosticExpander(
"""
    if (helperAnchor !in main) throw GradleException("Ponto de insercao da galeria nao encontrado.")
    val galleryCode = """@Composable
private fun AutomaticRideCapturesCard128() {
    val context = LocalContext.current
    val store128 = remember { AutomaticRideCaptureStore(context) }
    val scope128 = rememberCoroutineScope()
    var captures128 by remember { mutableStateOf<List<AutomaticRideCapture>>(emptyList()) }
    var selectedCapture128 by remember { mutableStateOf<AutomaticRideCapture?>(null) }
    var galleryStatus128 by remember { mutableStateOf("Carregando capturas automaticas...") }

    fun reloadAutomaticCaptures128() {
        scope128.launch {
            galleryStatus128 = "Atualizando capturas..."
            captures128 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store128.list()
            }
            galleryStatus128 = if (captures128.isEmpty()) {
                "Nenhuma corrida capturada automaticamente ainda."
            } else {
                "Capturas disponiveis: " + captures128.size + ". Exclusao automatica apos 14 dias."
            }
        }
    }

    fun deleteAutomaticCapture128(capture: AutomaticRideCapture) {
        scope128.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store128.delete(capture.id)
            }
            if (selectedCapture128?.id == capture.id) selectedCapture128 = null
            reloadAutomaticCaptures128()
        }
    }

    LaunchedEffect(Unit) {
        captures128 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store128.list()
        }
        galleryStatus128 = if (captures128.isEmpty()) {
            "Nenhuma corrida capturada automaticamente ainda."
        } else {
            "Capturas disponiveis: " + captures128.size + ". Exclusao automatica apos 14 dias."
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Captura de Tela Automatica", fontWeight = FontWeight.Bold)
            Text(
                "Cada card de corrida detectado em um aplicativo selecionado e salvo no armazenamento privado do Rota Certa.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(galleryStatus128, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = ::reloadAutomaticCaptures128, modifier = Modifier.fillMaxWidth()) {
                Text("Atualizar capturas")
            }
            if (captures128.isNotEmpty()) {
                val visibleCaptures128 = captures128.take(20)
                if (captures128.size > visibleCaptures128.size) {
                    Text(
                        "Mostrando as 20 capturas mais recentes de " + captures128.size + ".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                visibleCaptures128.forEach { capture128 ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(automaticCaptureAppLabel128(capture128.packageName), fontWeight = FontWeight.Bold)
                            Text(
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                                    .format(Date(capture128.createdAtMillis)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            capture128.fare?.let { Text("Valor: " + it, style = MaterialTheme.typography.bodySmall) }
                            capture128.destination?.let {
                                Text("Destino: " + it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { selectedCapture128 = capture128 },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Ver detalhes")
                                }
                                OutlinedButton(
                                    onClick = { deleteAutomaticCapture128(capture128) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Apagar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCapture128?.let { capture128 ->
        val previewFile128 = store128.imageFile(capture128)
        val previewBitmap128 = remember(capture128.id, previewFile128.lastModified()) {
            BitmapFactory.decodeFile(previewFile128.absolutePath)
        }
        AlertDialog(
            onDismissRequest = { selectedCapture128 = null },
            title = { Text("Detalhes da corrida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (previewBitmap128 != null) {
                        Image(
                            bitmap = previewBitmap128.asImageBitmap(),
                            contentDescription = "Print automatico do card da corrida",
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("A imagem desta captura nao esta mais disponivel.")
                    }
                    Text("Aplicativo: " + automaticCaptureAppLabel128(capture128.packageName))
                    capture128.fare?.let { Text("Valor: " + it) }
                    capture128.pickup?.let { Text("Embarque: " + it) }
                    capture128.destination?.let { Text("Destino: " + it) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { openAutomaticCaptureAddress128(context, capture128.pickup) },
                            enabled = !capture128.pickup.isNullOrBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Ver embarque") }
                        OutlinedButton(
                            onClick = { openAutomaticCaptureAddress128(context, capture128.destination) },
                            enabled = !capture128.destination.isNullOrBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Ver destino") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { openAutomaticCaptureApp128(context, capture128.packageName) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Abrir aplicativo") }
                        OutlinedButton(
                            onClick = { copyAutomaticCaptureDetails128(context, capture128) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copiar dados") }
                    }
                    Text("Texto captado", fontWeight = FontWeight.Bold)
                    Text(capture128.text.take(700), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { selectedCapture128 = null }) { Text("Fechar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteAutomaticCapture128(capture128) }) { Text("Apagar captura") }
            },
        )
    }
} // automatic_capture_gallery_card_0_1_128

private fun automaticCaptureAppLabel128(packageName: String): String = when (packageName) {
    RideCardTemplateMatcher.INDRIVE_PACKAGE -> "inDrive"
    RideCardTemplateMatcher.UBER_PACKAGE -> "Uber Driver"
    RideCardTemplateMatcher.NINETY_NINE_PACKAGE -> "99 Driver"
    else -> packageName
}

private fun openAutomaticCaptureAddress128(context: Context, address: String?) {
    val clean128 = address?.trim().orEmpty()
    if (clean128.isBlank()) {
        Toast.makeText(context, "Endereco nao identificado nesta captura.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent128 = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:0,0?q=" + Uri.encode(clean128)),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent128) }
        .onFailure {
            Toast.makeText(context, "Nao encontrei um aplicativo de mapas.", Toast.LENGTH_SHORT).show()
        }
} // automatic_capture_map_links_0_1_128

private fun openAutomaticCaptureApp128(context: Context, packageName: String) {
    val intent128 = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent128 == null) {
        Toast.makeText(context, "Aplicativo da corrida nao encontrado.", Toast.LENGTH_SHORT).show()
        return
    }
    context.startActivity(intent128.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyAutomaticCaptureDetails128(context: Context, capture: AutomaticRideCapture) {
    val details128 = buildString {
        appendLine("Aplicativo: " + automaticCaptureAppLabel128(capture.packageName))
        capture.fare?.let { appendLine("Valor: " + it) }
        capture.pickup?.let { appendLine("Embarque: " + it) }
        capture.destination?.let { appendLine("Destino: " + it) }
        appendLine()
        append(capture.text)
    }
    val clipboard128 = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard128.setPrimaryClip(ClipData.newPlainText("Dados da corrida", details128))
    Toast.makeText(context, "Dados da corrida copiados.", Toast.LENGTH_SHORT).show()
} // automatic_capture_quick_details_0_1_128

"""
    main = main.replaceFirst(helperAnchor, galleryCode + helperAnchor)

    listOf(
        "automatic_capture_gallery_ui_0_1_128",
        "automatic_capture_gallery_card_0_1_128",
        "automatic_capture_map_links_0_1_128",
        "automatic_capture_quick_details_0_1_128",
        "Captura de Tela Automatica",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Galeria automatica incompleta: $marker")
    }
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchAutomaticCaptureGalleryUi128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
