// Rota Certa 0.1.129
// Captura automatica de cards confirmados:
// - somente depois do match de modelo manual do mesmo aplicativo;
// - screenshot em fila separada, sem bloquear rota/cor/km;
// - armazenamento privado, deduplicado e com expiracao em 14 dias;
// - galeria e detalhes dentro de Modelos de cards.

fun patchAutomaticCardCapture129(
    serviceFile: java.io.File,
    mainFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente para captura automatica 0.1.129.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt ausente para captura automatica 0.1.129.")

    var service = serviceFile.readText()
    val dollar = "$"

    if ("automatic_capture_fields_0_1_129" !in service) {
        val fieldAnchor = "    private val screenshotInProgress = AtomicBoolean(false)\n"
        if (fieldAnchor !in service) throw GradleException("Campo de screenshot nao encontrado para captura automatica.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + """    private val automaticCaptureInProgress129 = AtomicBoolean(false)
    private var lastAutomaticCaptureSignature129: String? = null
    private var lastAutomaticCaptureRequestedAt129: Long = 0L // automatic_capture_fields_0_1_129
""",
        )
    }

    if ("automatic_capture_store_field_0_1_129" !in service) {
        val storeAnchor = "    private lateinit var repository: SettingsRepository\n"
        if (storeAnchor !in service) throw GradleException("Repositorio nao encontrado para captura automatica.")
        service = service.replaceFirst(
            storeAnchor,
            storeAnchor + "    private lateinit var automaticRideCaptureStore129: AutomaticRideCaptureStore // automatic_capture_store_field_0_1_129\n",
        )
    }

    if ("automatic_capture_store_init_0_1_129" !in service) {
        val initAnchor = "        repository = SettingsRepository(applicationContext)\n"
        if (initAnchor !in service) throw GradleException("Inicializacao do repositorio nao encontrada.")
        service = service.replaceFirst(
            initAnchor,
            initAnchor + """        automaticRideCaptureStore129 = AutomaticRideCaptureStore(applicationContext) // automatic_capture_store_init_0_1_129
        scope.launch(Dispatchers.IO) {
            val removed129 = automaticRideCaptureStore129.cleanupExpired()
            if (removed129 > 0) traceEvent("automatic.capture cleanup_removed=${dollar}removed129")
        }
""",
        )
    }

    if ("automatic_capture_after_manual_match_0_1_129" !in service) {
        val callAnchor = """        val cardChanged = universalActiveAddressSignature != cardDecisionSignature
        if (cardChanged) {
"""
        if (callAnchor !in service) throw GradleException("Mudanca de card nao encontrada para disparar captura automatica.")
        val callReplacement = """        val cardChanged = universalActiveAddressSignature != cardDecisionSignature
        if (cardChanged) {
            requestAutomaticRideCapture129(
                snapshotText = snapshotText,
                packageName = selectedPackageForCard,
                fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                cardSignature = cardDecisionSignature,
            ) // automatic_capture_after_manual_match_0_1_129
"""
        service = service.replaceFirst(callAnchor, callReplacement)
    }

    if ("automatic_capture_nonblocking_0_1_129" !in service) {
        val helperAnchor = "    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {\n"
        val helperIndex = service.indexOf(helperAnchor)
        if (helperIndex < 0) throw GradleException("Funcao de screenshot nao encontrada para inserir captura automatica.")
        val helper = """    private fun requestAutomaticRideCapture129(
        snapshotText: String,
        packageName: String,
        fields: RideFields,
        cardSignature: String,
    ) {
        if (!serviceReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!automaticRideCaptureStore129.isEnabled()) return
        val now129 = System.currentTimeMillis()
        if (cardSignature == lastAutomaticCaptureSignature129 &&
            now129 >= lastAutomaticCaptureRequestedAt129 &&
            now129 - lastAutomaticCaptureRequestedAt129 < 60_000L
        ) return
        if (!automaticCaptureInProgress129.compareAndSet(false, true)) return
        lastAutomaticCaptureSignature129 = cardSignature
        lastAutomaticCaptureRequestedAt129 = now129

        scope.launch {
            repeat(5) { attempt129 ->
                if (!screenshotInProgress.get()) return@repeat
                delay(70L + attempt129 * 20L)
            }
            if (screenshotInProgress.get()) {
                automaticCaptureInProgress129.set(false)
                traceEvent("automatic.capture skipped_busy=true")
                return@launch
            }
            runCatching {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val bitmap129 = screenshot.toSoftwareBitmap()
                            if (bitmap129 == null) {
                                automaticCaptureInProgress129.set(false)
                                return
                            }
                            scope.launch(Dispatchers.IO) {
                                val saved129 = runCatching {
                                    automaticRideCaptureStore129.saveConfirmedCard(
                                        bitmap = bitmap129,
                                        packageName = packageName,
                                        text = snapshotText,
                                        fields = fields,
                                    )
                                }.getOrNull()
                                bitmap129.recycle()
                                automaticCaptureInProgress129.set(false)
                                traceEvent(
                                    "automatic.capture saved=${dollar}{saved129 != null} package=${dollar}packageName id=${dollar}{saved129?.id.orEmpty()}",
                                )
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            automaticCaptureInProgress129.set(false)
                            traceEvent("automatic.capture failed_code=${dollar}errorCode")
                        }
                    },
                )
            }.onFailure { error129 ->
                automaticCaptureInProgress129.set(false)
                traceEvent("automatic.capture error=${dollar}{error129::class.java.simpleName}")
            }
        }
    } // automatic_capture_nonblocking_0_1_129

"""
        service = service.substring(0, helperIndex) + helper + service.substring(helperIndex)
    }

    var main = mainFile.readText()
    if ("automatic_capture_ui_imports_0_1_129" !in main) {
        val importAnchor = "import android.content.Intent\n"
        if (importAnchor !in main) throw GradleException("Import de Intent nao encontrado na interface.")
        main = main.replaceFirst(
            importAnchor,
            importAnchor + "import android.graphics.BitmapFactory\n",
        )
        val imageImportAnchor = "import androidx.compose.foundation.layout.Arrangement\n"
        if (imageImportAnchor !in main) throw GradleException("Imports Compose nao encontrados para galeria.")
        main = main.replaceFirst(
            imageImportAnchor,
            "import androidx.compose.foundation.Image\n" + imageImportAnchor,
        )
        val graphicsAnchor = "import androidx.compose.ui.Modifier\n"
        if (graphicsAnchor !in main) throw GradleException("Import Modifier nao encontrado para galeria.")
        main = main.replaceFirst(
            graphicsAnchor,
            graphicsAnchor + "import androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.layout.ContentScale\n",
        )
        main = main.replaceFirst(
            "import kotlin.math.roundToInt\n",
            "import kotlin.math.roundToInt\n// automatic_capture_ui_imports_0_1_129\n",
        )
    }

    if ("AutomaticRideCaptureGallery129()" !in main) {
        val uiAnchor = """            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: ${dollar}unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
"""
        if (uiAnchor !in main) throw GradleException("Ponto da tela de modelos nao encontrado para galeria automatica.")
        main = main.replaceFirst(
            uiAnchor,
            uiAnchor + "            AutomaticRideCaptureGallery129() // automatic_capture_gallery_inside_models_0_1_129\n",
        )
    }

    if ("automatic_capture_gallery_composable_0_1_129" !in main) {
        val composableAnchor = "@Composable\nprivate fun DiagnosticExpander(\n"
        val composableIndex = main.indexOf(composableAnchor)
        if (composableIndex < 0) throw GradleException("Limite depois de CardModelsCard nao encontrado.")
        val composable = """@Composable
private fun AutomaticRideCaptureGallery129() {
    val context129 = LocalContext.current
    val store129 = remember { AutomaticRideCaptureStore(context129) }
    val captures129 by remember(store129) { store129.capturesFlow() }
        .collectAsState(initial = store129.list())
    val scope129 = rememberCoroutineScope()
    var enabled129 by remember { mutableStateOf(store129.isEnabled()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Captura de Tela Automática", fontWeight = FontWeight.Bold)
        Text(
            "Salva automaticamente uma imagem de cada corrida confirmada. As capturas ficam privadas, repeticoes sao ignoradas e os arquivos expiram em 14 dias.",
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
        Text("Capturas temporárias: ${dollar}{captures129.size}", style = MaterialTheme.typography.bodySmall)

        if (captures129.isEmpty()) {
            Text("Nenhuma corrida capturada automaticamente ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            captures129.take(10).forEach { capture129 ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val preview129 = remember(capture129.id) {
                            BitmapFactory.decodeFile(store129.imageFile(capture129).absolutePath)?.asImageBitmap()
                        }
                        preview129?.let { image129 ->
                            Image(
                                bitmap = image129,
                                contentDescription = "Captura automática do card",
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Text(capture129.packageName, fontWeight = FontWeight.Bold)
                        capture129.fare?.let { Text("Valor: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
                        capture129.pickup?.let { Text("Embarque: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
                        capture129.destination?.let { Text("Destino: ${dollar}it", style = MaterialTheme.typography.bodySmall) }
                        val remainingDays129 = ((capture129.expiresAtMillis - System.currentTimeMillis()) /
                            (24L * 60L * 60L * 1_000L)).coerceAtLeast(0L) + 1L
                        Text("Exclusão automática em até ${dollar}remainingDays129 dia(s).", style = MaterialTheme.typography.bodySmall)

                        Button(
                            onClick = {
                                scope129.launch {
                                    val template129 = RideCardTemplateMatcher.createTemplate(
                                        capture129.packageName,
                                        capture129.textPreview,
                                        "Card automático ${dollar}{capture129.packageName.substringAfterLast('.')}",
                                    )
                                    SettingsRepository(context129).addCardTemplate(template129)
                                    Toast.makeText(context129, "Modelo criado a partir da captura.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Usar como modelo de card") }

                        capture129.destination?.let { destination129 ->
                            OutlinedButton(
                                onClick = {
                                    val uri129 = Uri.parse("geo:0,0?q=" + Uri.encode(destination129))
                                    val mapsIntent129 = Intent(Intent.ACTION_VIEW, uri129)
                                        .setPackage("com.google.android.apps.maps")
                                    runCatching { context129.startActivity(mapsIntent129) }
                                        .recoverCatching {
                                            context129.startActivity(Intent(Intent.ACTION_VIEW, uri129))
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Abrir destino no mapa") }
                        }

                        OutlinedButton(
                            onClick = {
                                val details129 = buildString {
                                    capture129.fare?.let { appendLine("Valor: ${dollar}it") }
                                    capture129.pickup?.let { appendLine("Embarque: ${dollar}it") }
                                    capture129.destination?.let { appendLine("Destino: ${dollar}it") }
                                }.trim()
                                val clipboard129 = context129.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard129.setPrimaryClip(ClipData.newPlainText("Detalhes da corrida", details129))
                                Toast.makeText(context129, "Detalhes copiados.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copiar detalhes da corrida") }

                        OutlinedButton(
                            onClick = { store129.delete(capture129.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Apagar captura") }
                    }
                }
            }
            if (captures129.size > 10) {
                Text("Mostrando as 10 capturas mais recentes.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { store129.clearAll() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apagar todas as capturas automáticas") }
        }
    }
} // automatic_capture_gallery_composable_0_1_129

"""
        main = main.substring(0, composableIndex) + composable + main.substring(composableIndex)
    }

    listOf(
        "automatic_capture_fields_0_1_129",
        "automatic_capture_store_field_0_1_129",
        "automatic_capture_store_init_0_1_129",
        "automatic_capture_after_manual_match_0_1_129",
        "automatic_capture_nonblocking_0_1_129",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador da captura automatica ausente no servico: ${dollar}marker")
    }
    listOf(
        "automatic_capture_ui_imports_0_1_129",
        "automatic_capture_gallery_inside_models_0_1_129",
        "automatic_capture_gallery_composable_0_1_129",
        "Captura de Tela Automática",
        "Usar como modelo de card",
        "Abrir destino no mapa",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Marcador da captura automatica ausente na interface: ${dollar}marker")
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchAutomaticCardCapture129(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
