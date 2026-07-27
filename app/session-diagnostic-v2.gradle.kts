fun sessionDiagnosticFindClosingBrace(source: String, openingBrace: Int): Int {
    var depth = 0
    var index = openingBrace
    var inString = false
    var inChar = false
    var escaping = false
    while (index < source.length) {
        val char = source[index]
        if (escaping) {
            escaping = false
        } else if (char == '\\' && (inString || inChar)) {
            escaping = true
        } else if (!inChar && char == '"') {
            inString = !inString
        } else if (!inString && char == '\'') {
            inChar = !inChar
        } else if (!inString && !inChar) {
            when (char) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        index += 1
    }
    throw GradleException("Nao encontrei o fechamento do bloco Kotlin.")
}

fun sessionDiagnosticReplaceFunctionBody(
    source: String,
    signatureToken: String,
    replacementBody: String,
): String {
    val functionStart = source.indexOf(signatureToken)
    if (functionStart < 0) throw GradleException("Funcao ausente para diagnostico: $signatureToken")
    val openingBrace = source.indexOf('{', functionStart + signatureToken.length)
    if (openingBrace < 0) throw GradleException("Abertura da funcao ausente: $signatureToken")
    val closingBrace = sessionDiagnosticFindClosingBrace(source, openingBrace)
    return source.substring(0, openingBrace) + replacementBody + source.substring(closingBrace + 1)
}

val sessionDiagnosticV2 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalTwoAddressRuntimeFinal"))
    dependsOn(tasks.named("universalNoCardCompileRepair"))

    doLast {
        val dollar = "$"
        val serviceSource = serviceFile.asFile
        val mainSource = mainFile.asFile
        if (!serviceSource.exists() || !mainSource.exists()) {
            throw GradleException("Fontes principais ausentes para o diagnostico por sessao.")
        }

        var service = serviceSource.readText()
        var main = mainSource.readText()

        if ("session_diagnostic_trace_v2" !in service) {
            val traceSignature = "    private fun traceEvent(message: String) {"
            val traceStart = service.indexOf(traceSignature)
            if (traceStart < 0) throw GradleException("traceEvent nao encontrado para o diagnostico por sessao.")
            val traceOpening = service.indexOf('{', traceStart)
            service = service.substring(0, traceOpening + 1) + """
        LiveFailureTraceStore.recordTrace(
            message = message,
            packageName = currentWindowPackageName(),
            generation = universalScreenGeneration,
            screenHash = lastSnapshotHash,
        ) // session_diagnostic_trace_v2
""" + service.substring(traceOpening + 1)
        }

        if ("session_diagnostic_read_v2" !in service) {
            val target = """        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
"""
            val replacement = target + """        LiveFailureTraceStore.recordRead(
            source = source.toString(),
            packageName = currentWindowPackageName(),
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            active = trigger.active,
            screenHash = trigger.screenHash,
            generation = if (lastSnapshotHash != trigger.screenHash) universalScreenGeneration + 1L else universalScreenGeneration,
        ) // session_diagnostic_read_v2
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if ("session_diagnostic_ocr_extract_v2" !in service) {
            val target = """                                    val ocrText = ocrService.extractText(bitmap)
"""
            val replacement = target + """                                    val ocrTraceTrigger = UniversalAddressTrigger.evaluate(ocrText)
                                    LiveFailureTraceStore.recordRead(
                                        source = "Ocr",
                                        packageName = currentWindowPackageName(),
                                        text = ocrText,
                                        addresses = ocrTraceTrigger.addresses,
                                        destination = ocrTraceTrigger.destination,
                                        active = ocrTraceTrigger.active,
                                        screenHash = ocrTraceTrigger.screenHash,
                                        generation = universalScreenGeneration,
                                    ) // session_diagnostic_ocr_extract_v2
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if ("session_diagnostic_geocode_v2" !in service) {
            val target = """        val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
"""
            val replacement = """        val destinationQuery = fields.destination.orEmpty()
        val destinationGeocodeStartedAt = System.currentTimeMillis()
        val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
        LiveFailureTraceStore.recordGeocode(
            label = "destination",
            query = destinationQuery,
            coordinate = destinationCoordinate?.let { coordinate -> "${dollar}{coordinate.latitude},${dollar}{coordinate.longitude}" },
            elapsedMillis = System.currentTimeMillis() - destinationGeocodeStartedAt,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_geocode_v2
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if ("session_diagnostic_targets_v2" !in service) {
            val target = """        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
"""
            val replacement = """        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        LiveFailureTraceStore.recordStep(
            stage = "geocode.targets",
            details = "destination=${dollar}{destinationCoordinate?.let { "${dollar}{it.latitude},${dollar}{it.longitude}" } ?: "null"}; home=${dollar}{homeCoordinate?.let { "${dollar}{it.latitude},${dollar}{it.longitude}" } ?: "null"}; alternative=${dollar}{alternativeCoordinate?.let { "${dollar}{it.latitude},${dollar}{it.longitude}" } ?: "null"}; home_enabled=${dollar}{settings.homeTargetEnabled}; alternative_enabled=${dollar}{settings.alternativeTargetEnabled}",
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_targets_v2

        val homeRouteStartedAt = System.currentTimeMillis()
        val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
        LiveFailureTraceStore.recordRoute(
            label = "home",
            distanceKm = homeDistanceKm,
            elapsedMillis = System.currentTimeMillis() - homeRouteStartedAt,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if ("session_diagnostic_alternative_route_v2" !in service) {
            val target = """        val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
"""
            val replacement = """        val alternativeRouteStartedAt = System.currentTimeMillis()
        val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
        LiveFailureTraceStore.recordRoute(
            label = "alternative",
            distanceKm = alternativeDistanceKm,
            elapsedMillis = System.currentTimeMillis() - alternativeRouteStartedAt,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_alternative_route_v2
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if ("session_diagnostic_decision_v2" !in service) {
            val target = """        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
"""
            val replacement = """        LiveFailureTraceStore.recordDecision(
            color = color.diagnosticLabel,
            distanceKm = result.nearestConfiguredDistanceKm(),
            reason = result.reason,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_decision_v2
        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
"""
            if (target !in service) Unit
            service = service.replaceFirst(target, replacement)
        }

        if (false && "session_diagnostic_freshness_v2" !in service) {
            val replacement = """{
        val fresh = serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            currentWindowPackageName() != this.packageName
        if (!fresh) {
            LiveFailureTraceStore.recordStep(
                stage = "freshness.reject",
                details = "expected_generation=${dollar}generation; current_generation=${dollar}universalScreenGeneration; expected_hash=${dollar}screenHash; current_hash=${dollar}{lastSnapshotHash ?: "null"}; expected_signature=${dollar}{addressSignature.take(300)}; current_signature=${dollar}{universalActiveAddressSignature?.take(300) ?: "null"}; current_package=${dollar}{currentWindowPackageName().orEmpty()}",
                packageName = currentWindowPackageName(),
                generation = universalScreenGeneration,
                screenHash = lastSnapshotHash,
            ) // session_diagnostic_freshness_v2
        }
        return fresh
    }"""
            service = sessionDiagnosticReplaceFunctionBody(
                source = service,
                signatureToken = "    private fun isUniversalResultFresh(",
                replacementBody = replacement,
            )
        }

        if ("session_diagnostic_clear_v2" !in service) {
            val target = """        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
"""
            val replacement = """        LiveFailureTraceStore.recordStep(
            stage = "session.clear",
            details = "reason=${dollar}reason; had_data=${dollar}hadData; generation_before=${dollar}universalScreenGeneration; color=${dollar}{currentRadarColor.diagnosticLabel}; km=${dollar}{currentDistanceKm?.toString() ?: "none"}",
            packageName = currentWindowPackageName(),
            generation = universalScreenGeneration,
            screenHash = lastSnapshotHash,
        ) // session_diagnostic_clear_v2
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
"""
            val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(reason: String) {")
            val targetIndex = if (clearStart >= 0) service.indexOf(target, clearStart) else -1
            if (targetIndex >= 0) {
                service = service.substring(0, targetIndex) + replacement + service.substring(targetIndex + target.length)
            }
        }

        val reportBody = """{
    // universal_no_card_registration_0_1_102
    // Leitura universal de tela: true
    val nowMillis = System.currentTimeMillis()
    val bubbleStatePrefs = context.getSharedPreferences("rota_certa_bubble", Context.MODE_PRIVATE)
    val bubbleUpdatedAtMillis = bubbleStatePrefs.getLong("state_updated_at", 0L)
    fun bubbleText(key: String): String = bubbleStatePrefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao informado"
    fun bubbleBool(key: String): String = bubbleStatePrefs.getBoolean(key, false).toString()
    fun bubbleInt(key: String): String = bubbleStatePrefs.getInt(key, -1).takeIf { it >= 0 }?.toString() ?: "nao informado"
    val sessionDiagnostic = LiveFailureTraceStore.exportReport(nowMillis)
    val complementaryEvents = DiagnosticLogStore.dump(120)

    return buildString {
        appendLine("ROTA CERTA DIAGNOSTICO DE SESSAO")
        appendLine("Arquivo montado somente por clique do usuario.")
        appendLine("A trilha circular fica apenas em memoria e nao grava cada evento no armazenamento.")
        appendLine("Versao: ${dollar}{BuildConfig.VERSION_NAME} (${dollar}{BuildConfig.VERSION_CODE})")
        appendLine("Data da exportacao: ${dollar}{formatDate(nowMillis)}")
        appendLine("Pacote: ${dollar}{context.packageName}")
        appendLine("Leitura ao vivo ativa: ${dollar}liveEnabled")
        appendLine()
        appendLine("--- ULTIMA TENTATIVA REAL DA BOLINHA ---")
        appendLine(sessionDiagnostic)
        appendLine()
        appendLine("--- ESTADO ATUAL DA BOLINHA ---")
        appendLine("Atualizado: ${dollar}{if (bubbleUpdatedAtMillis > 0L) formatDate(bubbleUpdatedAtMillis) else "nunca"}")
        appendLine("Idade estado: ${dollar}{if (bubbleUpdatedAtMillis > 0L) (nowMillis - bubbleUpdatedAtMillis).toString() + " ms" else "nao informado"}")
        appendLine("Etapa: ${dollar}{bubbleText("state_stage")}")
        appendLine("Cor: ${dollar}{bubbleText("state_color")}")
        appendLine("Km exibido: ${dollar}{bubbleText("state_distance_km")}")
        appendLine("Motivo: ${dollar}{bubbleText("state_reason")}")
        appendLine("Pacote janela: ${dollar}{bubbleText("state_window_package")}")
        appendLine("Pacote ativo: ${dollar}{bubbleText("state_active_package")}")
        appendLine("Pacote texto: ${dollar}{bubbleText("state_text_package")}")
        appendLine("Hash tela atual: ${dollar}{bubbleText("state_last_snapshot_hash")}")
        appendLine("Hash analisado: ${dollar}{bubbleText("state_last_analyzed_hash")}")
        appendLine("Hash pendente: ${dollar}{bubbleText("state_pending_hash")}")
        appendLine("Servico pronto: ${dollar}{bubbleBool("state_service_ready")}")
        appendLine("Analisando agora: ${dollar}{bubbleBool("state_analyzing")}")
        appendLine("Texto acessibilidade tamanho: ${dollar}{bubbleInt("state_accessibility_text_length")}")
        appendLine("Texto acessibilidade hash: ${dollar}{bubbleText("state_accessibility_text_hash")}")
        appendLine("Texto OCR tamanho: ${dollar}{bubbleInt("state_ocr_text_length")}")
        appendLine("Texto OCR hash: ${dollar}{bubbleText("state_ocr_text_hash")}")
        appendLine()
        appendLine("--- CONFIGURACOES NECESSARIAS PARA A DECISAO ---")
        appendLine("Rota Certa ligado: ${dollar}{settings.appEnabled}")
        appendLine("Leitura universal ligada: ${dollar}{settings.liveReadingEnabled}")
        appendLine("Casa ligada: ${dollar}{settings.homeTargetEnabled}")
        appendLine("Casa/ponto principal: ${dollar}{settings.homeAddress.ifBlank { "nao informado" }}")
        appendLine("Coordenada casa: ${dollar}{settings.homeCoordinate?.let(::formatCoordinate) ?: "nao informada"}")
        appendLine("Raio casa: ${dollar}{formatKm(settings.homeRadiusKm)}")
        appendLine("Alfinete ligado: ${dollar}{settings.alternativeTargetEnabled}")
        appendLine("Alfinete/local alternativo: ${dollar}{settings.alternativeAddress.ifBlank { "nao informado" }}")
        appendLine("Coordenada alfinete: ${dollar}{settings.alternativeCoordinate?.let(::formatCoordinate) ?: "nao informada"}")
        appendLine("Raio alfinete: ${dollar}{formatKm(settings.alternativeRadiusKm)}")
        appendLine("Google Maps API configurada: ${dollar}{settings.googleMapsApiKey.isNotBlank() || BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()}")
        appendLine()
        appendLine("--- LOCAIS E ALERTAS ---")
        appendLine("Total: ${dollar}{savedPlaces.size}")
        savedPlaces.forEachIndexed { index, place ->
            appendLine("${dollar}{index + 1}. tipo=${dollar}{place.type}; nome=${dollar}{place.name}; endereco=${dollar}{place.address}; coordenada=${dollar}{formatCoordinate(place.coordinate)}; distanciaAlerta=${dollar}{place.alertDistanceMeters ?: 0}")
        }
        appendLine()
        appendLine("--- RADARES IMPORTADOS ---")
        appendLine(radarImportSummary.toString())
        appendLine()
        appendLine("--- EVENTOS GLOBAIS COMPLEMENTARES ---")
        appendLine(complementaryEvents.ifBlank { "sem eventos complementares" })
        appendLine()
        appendLine("--- OBSERVACAO ---")
        appendLine("O relatorio nao inclui backup nem historico inteiro. Ele preserva a tentativa mais recente, textos de acessibilidade/OCR, enderecos, geocodificacao, rota, descarte e cor final.")
    }
}"""
        main = sessionDiagnosticReplaceFunctionBody(
            source = main,
            signatureToken = "private suspend fun buildManualSupportReport(",
            replacementBody = reportBody,
        )

        main = main
            .replace(
                "Gera um arquivo leve somente quando voce tocar aqui. A bolinha continua sem logs automaticos.",
                "Mantem uma trilha circular leve apenas em memoria. Ao tocar, exporta a ultima tentativa completa: OCR, enderecos, rota, descartes e cor final.",
            )
            .replace("Relatorio manual de falha", "Diagnostico detalhado da bolinha")
            .replace("Gerar relatorio para anexar", "Exportar ultima tentativa")

        if ("ROTA CERTA DIAGNOSTICO DE SESSAO" !in main) {
            throw GradleException("O novo relatorio de sessao nao foi instalado.")
        }
        if (
            "session_diagnostic_trace_v2" !in service ||
            "session_diagnostic_read_v2" !in service
        ) {
            throw GradleException("Instrumentacao essencial de sessao ausente")
        }
        if ("--- BACKUP INTERNO ---" in main.substring(
                main.indexOf("private suspend fun buildManualSupportReport("),
                main.indexOf("private fun clearClipboard", main.indexOf("private suspend fun buildManualSupportReport(")),
            )
        ) {
            throw GradleException("O relatorio novo ainda inclui backup interno.")
        }

        serviceSource.writeText(service)
        mainSource.writeText(main)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(sessionDiagnosticV2)
}
