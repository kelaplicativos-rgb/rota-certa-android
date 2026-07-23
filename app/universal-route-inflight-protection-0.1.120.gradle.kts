// Correcao orientada pelo diagnostico real 0.1.120 (build 3381):
// - a oferta era reconhecida e a rota estava em andamento;
// - eventos atrasados de ChatGPT/DocumentsUI/SystemUI mudavam o pacote observado;
// - duas leituras vazias transitórias limpavam a sessao antes do resultado;
// - a bolinha ficava amarela e o motorista perdia a corrida.
//
// Contrato:
// - enquanto a rota do card atual estiver em andamento, uma janela curta protege
//   o pacote da corrida e ignora vazios transitórios;
// - cada nova leitura valida do mesmo card renova a janela;
// - ao concluir/cancelar a rota, a limpeza volta a ser imediata;
// - abrir a MainActivity do Rota Certa continua limpando imediatamente.

fun routeLease120ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

fun routeLease120InsertBeforeLastBrace(source: String, addition: String, label: String): String {
    val index = source.lastIndexOf("\n}")
    if (index < 0) throw GradleException("Fechamento ausente para $label")
    return source.substring(0, index) + addition + source.substring(index)
}

val universalRouteInflightProtection120 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    val guardFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt",
    )
    val testFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuardsTest.kt",
    )
    inputs.files(serviceFile, guardFile, testFile)
    outputs.upToDateWhen { false }
    dependsOn("universalOcrFreshness120")

    doLast {
        val service = serviceFile.asFile
        val guard = guardFile.asFile
        val test = testFile.asFile
        listOf(service, guard, test).forEach { file ->
            if (!file.exists()) throw GradleException("Arquivo nao encontrado: ${file.path}")
        }

        var guardText = guard.readText()
        if ("universal_route_inflight_policy_0_1_120" !in guardText) {
            val anchor = "    private fun normalize(value: String?): String? =\n"
            val policy = """    const val ROUTE_INFLIGHT_GRACE_MILLIS = 2_500L

    fun shouldProtectRouteFromForeignEvent(
        hasActiveAddressSignature: Boolean,
        routeInFlight: Boolean,
        lastActiveReadAtMillis: Long,
        nowMillis: Long,
        activeRidePackageName: String?,
        incomingPackageName: String?,
    ): Boolean {
        if (!hasActiveAddressSignature || !routeInFlight) return false
        val active = normalize(activeRidePackageName) ?: return false
        val incoming = normalize(incomingPackageName) ?: return false
        if (active == incoming) return false
        return isInsideInflightGrace(lastActiveReadAtMillis, nowMillis)
    } // universal_route_inflight_policy_0_1_120

    fun shouldIgnoreTransientInactiveRead(
        hasActiveAddressSignature: Boolean,
        routeInFlight: Boolean,
        lastActiveReadAtMillis: Long,
        nowMillis: Long,
    ): Boolean = hasActiveAddressSignature &&
        routeInFlight &&
        isInsideInflightGrace(lastActiveReadAtMillis, nowMillis)

    private fun isInsideInflightGrace(lastActiveReadAtMillis: Long, nowMillis: Long): Boolean =
        lastActiveReadAtMillis > 0L &&
            nowMillis >= lastActiveReadAtMillis &&
            nowMillis - lastActiveReadAtMillis <= ROUTE_INFLIGHT_GRACE_MILLIS

"""
            guardText = routeLease120ReplaceOnce(
                source = guardText,
                oldValue = anchor,
                newValue = policy + anchor,
                label = "politica de protecao da rota em andamento",
            )
            guard.writeText(guardText)
        }

        var serviceText = service.readText()
        if ("universal_route_inflight_runtime_0_1_120" !in serviceText) {
            serviceText = routeLease120ReplaceOnce(
                source = serviceText,
                oldValue = "    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120\n",
                newValue = """    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120
    private var universalLastActiveReadAtMillis: Long = 0L
    private var universalActiveRidePackageName: String? = null // universal_route_inflight_runtime_0_1_120
""",
                label = "estado da protecao da rota",
            )

            serviceText = routeLease120ReplaceOnce(
                source = serviceText,
                oldValue = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val previousObservedPackage = universalForegroundPackageName
""",
                newValue = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val protectActiveRoute = UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
            routeInFlight = universalRouteJob?.isActive == true,
            lastActiveReadAtMillis = universalLastActiveReadAtMillis,
            nowMillis = System.currentTimeMillis(),
            activeRidePackageName = universalActiveRidePackageName,
            incomingPackageName = resolvedPackage,
        )
        if (protectActiveRoute) {
            traceEvent("universal.foreground ignored_foreign_event_during_route=true incoming=${'$'}resolvedPackage active=${'$'}{universalActiveRidePackageName.orEmpty()}")
            return
        }
        val previousObservedPackage = universalForegroundPackageName
""",
                label = "bloqueio de evento estrangeiro durante rota",
            )

            serviceText = routeLease120ReplaceOnce(
                source = serviceText,
                oldValue = """        val activeTrigger = trigger.active && !trigger.destination.isNullOrBlank() && rideEvidence.accepted // universal_ride_evidence_gate_0_1_112
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
""",
                newValue = """        val activeTrigger = trigger.active && !trigger.destination.isNullOrBlank() && rideEvidence.accepted // universal_ride_evidence_gate_0_1_112
        val readNowMillis = System.currentTimeMillis()
        if (!activeTrigger && liveSource == UniversalLiveReadSource.Accessibility &&
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = universalActiveAddressSignature != null,
                routeInFlight = universalRouteJob?.isActive == true,
                lastActiveReadAtMillis = universalLastActiveReadAtMillis,
                nowMillis = readNowMillis,
            )
        ) {
            traceEvent("universal.accessibility transient_empty_ignored_route_inflight=true")
            return
        }
        if (activeTrigger) {
            universalLastActiveReadAtMillis = readNowMillis
            universalActiveRidePackageName = universalResolvedForegroundPackage()
        }
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
""",
                label = "graca para leitura vazia transitoria",
            )

            serviceText = routeLease120ReplaceOnce(
                source = serviceText,
                oldValue = """        universalAccessibilityOwnsCard = false
        universalLiveReadGate.reset()
""",
                newValue = """        universalAccessibilityOwnsCard = false
        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        universalLiveReadGate.reset()
""",
                label = "limpeza do estado da protecao",
            )

            serviceText += "\n// universal_route_inflight_runtime_0_1_120\n"
            service.writeText(serviceText)
        }

        var testText = test.readText()
        if ("foreignEventCannotCancelFreshRouteInFlight" !in testText) {
            val addition = """

    @Test
    fun foreignEventCannotCancelFreshRouteInFlight() {
        assertTrue(
            UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 10_000L,
                nowMillis = 10_700L,
                activeRidePackageName = "sinet.startup.indriver",
                incomingPackageName = "com.openai.chatgpt",
            ),
        )
    }

    @Test
    fun emptyReadCannotCancelFreshRouteInFlight() {
        assertTrue(
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 20_000L,
                nowMillis = 21_000L,
            ),
        )
    }

    @Test
    fun protectionEndsAfterRouteOrGraceWindow() {
        assertFalse(
            UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
                hasActiveAddressSignature = true,
                routeInFlight = false,
                lastActiveReadAtMillis = 30_000L,
                nowMillis = 30_100L,
                activeRidePackageName = "sinet.startup.indriver",
                incomingPackageName = "com.openai.chatgpt",
            ),
        )
        assertFalse(
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = true,
                routeInFlight = true,
                lastActiveReadAtMillis = 30_000L,
                nowMillis = 33_000L,
            ),
        )
    }
"""
            testText = routeLease120InsertBeforeLastBrace(
                source = testText,
                addition = addition,
                label = "testes da rota em andamento",
            )
            test.writeText(testText)
        }

        listOf(
            "universal_route_inflight_policy_0_1_120",
            "universal_route_inflight_runtime_0_1_120",
            "ignored_foreign_event_during_route=true",
            "transient_empty_ignored_route_inflight=true",
            "foreignEventCannotCancelFreshRouteInFlight",
            "emptyReadCannotCancelFreshRouteInFlight",
        ).forEach { marker ->
            val present = marker in service.readText() || marker in guard.readText() || marker in test.readText()
            if (!present) throw GradleException("Protecao da rota em andamento incompleta: $marker")
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalRouteInflightProtection120)
}
