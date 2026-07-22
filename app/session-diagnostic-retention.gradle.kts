val sessionDiagnosticRetention by tasks.registering {
    val storeFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveFailureTraceStore.kt")
    inputs.file(storeFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("sessionDiagnosticV2"))

    doLast {
        val file = storeFile.asFile
        if (!file.exists()) throw GradleException("LiveFailureTraceStore.kt nao encontrado.")
        var text = file.readText()

        if ("session_retains_meaningful_read_v2" !in text) {
            text = text.replace(
                """            session.lastAtMillis = nowMillis
            if (cleanPackage.isNotBlank()) session.packageName = cleanPackage
            if (screenHash != null) session.screenHash = screenHash
            if (generation != null) session.generation = generation
            session.activeTrigger = active
            session.addresses = cleanAddresses
            session.addressSignature = addressSignature
            session.destination = destination?.trim()?.takeIf { it.isNotBlank() }

            val snapshot = ReadSnapshot(
""",
                """            session.lastAtMillis = nowMillis
            val cleanDestination = destination?.trim()?.takeIf { it.isNotBlank() }
            val hasStoredRead = !session.accessibility?.text.isNullOrBlank() || !session.ocr?.text.isNullOrBlank()
            val samePackage = cleanPackage.isBlank() || session.packageName.isBlank() || cleanPackage == session.packageName
            if (cleanPackage.isNotBlank() && (session.packageName.isBlank() || cleanAddresses.isNotEmpty() || !hasStoredRead)) {
                session.packageName = cleanPackage
            }
            if (screenHash != null && (cleanAddresses.isNotEmpty() || session.screenHash == null || samePackage)) session.screenHash = screenHash
            if (generation != null && (cleanAddresses.isNotEmpty() || session.generation == null || samePackage)) session.generation = generation
            if (cleanAddresses.isNotEmpty() || (session.addresses.isEmpty() && samePackage)) {
                session.activeTrigger = active
                session.addresses = cleanAddresses
                session.addressSignature = addressSignature
                session.destination = cleanDestination
            } // session_retains_meaningful_read_v2

            val snapshot = ReadSnapshot(
""",
            )

            text = text.replace(
                """            when (normalizedSource.lowercase(Locale.ROOT)) {
                "ocr" -> session.ocr = snapshot
                else -> session.accessibility = snapshot
            }

            val readChanged = previous?.hash != snapshot.hash ||
""",
                """            val mayReplaceStoredSource = cleanText.isNotBlank() &&
                (cleanAddresses.isNotEmpty() || previous == null || samePackage)
            if (mayReplaceStoredSource) {
                when (normalizedSource.lowercase(Locale.ROOT)) {
                    "ocr" -> session.ocr = snapshot
                    else -> session.accessibility = snapshot
                }
            }

            val readChanged = previous?.hash != snapshot.hash ||
""",
            )
        }

        if ("session_preserves_origin_package_v2" !in text) {
            text = text.replace(
                """            if (!packageName.isNullOrBlank()) session.packageName = packageName
""",
                """            if (!packageName.isNullOrBlank() &&
                session.addresses.isEmpty() &&
                session.accessibility?.text.isNullOrBlank() &&
                session.ocr?.text.isNullOrBlank()
            ) session.packageName = packageName // session_preserves_origin_package_v2
""",
            )
        }

        if ("session_start_guard_v2" !in text) {
            text = text.replace(
                """            current.endedAtMillis != null && text.isNotBlank() -> true
            addressSignature.isNotBlank() && current.addressSignature.isNotBlank() && addressSignature != current.addressSignature -> true
            packageName.isNotBlank() && current.packageName.isNotBlank() && packageName != current.packageName && text.isNotBlank() -> true
""",
                """            current.endedAtMillis != null && text.isNotBlank() &&
                (addressSignature.isNotBlank() ||
                    (current.accessibility?.text.isNullOrBlank() && current.ocr?.text.isNullOrBlank())) -> true
            addressSignature.isNotBlank() && current.addressSignature.isNotBlank() && addressSignature != current.addressSignature -> true
            packageName.isNotBlank() && current.packageName.isNotBlank() && packageName != current.packageName &&
                text.isNotBlank() &&
                (addressSignature.isNotBlank() ||
                    (current.accessibility?.text.isNullOrBlank() && current.ocr?.text.isNullOrBlank())) -> true // session_start_guard_v2
""",
            )
        }

        if ("session_selects_ride_attempt_v2" !in text) {
            text = text.replace(
                """    private fun selectRelevantSessionLocked(): TraceSession? =
        sessions.lastOrNull { session ->
            session.addresses.isNotEmpty() ||
                !session.accessibility?.text.isNullOrBlank() ||
                !session.ocr?.text.isNullOrBlank()
        } ?: sessions.lastOrNull()

""",
                """    private fun selectRelevantSessionLocked(): TraceSession? =
        sessions.lastOrNull(::looksLikeRideAttempt) ?: sessions.lastOrNull { session ->
            session.events.any { event ->
                event.stage in setOf("SCREENSHOT_FAIL", "ERROR", "DISCARDED")
            } || !session.accessibility?.text.isNullOrBlank() || !session.ocr?.text.isNullOrBlank()
        } ?: sessions.lastOrNull() // session_selects_ride_attempt_v2

    private fun looksLikeRideAttempt(session: TraceSession): Boolean {
        if (session.addresses.isNotEmpty()) return true
        val combinedText = listOfNotNull(session.accessibility?.text, session.ocr?.text)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return listOf(
            "pedido de viagem",
            "solicitacao de viagem",
            "solicitação de viagem",
            "aceitar por",
            "ofereca sua tarifa",
            "ofereça sua tarifa",
            "preco justo",
            "preço justo",
            "corrida",
            "embarque",
            "destino",
        ).any(combinedText::contains)
    }

""",
            )
        }

        listOf(
            "session_retains_meaningful_read_v2",
            "session_preserves_origin_package_v2",
            "session_start_guard_v2",
            "session_selects_ride_attempt_v2",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Retencao do diagnostico ausente: $marker")
        }
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(sessionDiagnosticRetention)
}
