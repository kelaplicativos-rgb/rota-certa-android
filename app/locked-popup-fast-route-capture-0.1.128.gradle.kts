// Rota Certa 0.1.128
// - preserva a sessao da corrida quando SystemUI/keyguard/launcher assume a janela;
// - conclui a rota mesmo se a tela apagar ou o popup desaparecer;
// - usa endereco -> rota em uma unica chamada, mantendo o fluxo antigo como fallback;
// - captura a imagem automaticamente em I/O separado antes do match do modelo.

fun replaceFunction128(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao ausente no patch 0.1.128: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo ausente no patch 0.1.128: $signature")
    var depth = 0
    var index = braceStart
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
    throw GradleException("Fim da funcao ausente no patch 0.1.128: $signature")
}

fun replaceRequired128(source: String, old: String, new: String, label: String): String {
    if (old !in source) throw GradleException("Ancora ausente para $label")
    return source.replaceFirst(old, new)
}

fun patchLockedPopupFastRoute128(
    serviceFile: java.io.File,
    guardFile: java.io.File,
    routeFragmentFile: java.io.File,
    helperFragmentFile: java.io.File,
) {
    listOf(serviceFile, guardFile, routeFragmentFile, helperFragmentFile).forEach { file ->
        if (!file.exists()) throw GradleException("Arquivo ausente no patch 0.1.128: ${file.path}")
    }
    val dollar = "$"

    var guard = guardFile.readText()
    guard = guard.replace(
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 2_500L",
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 12_000L // locked_popup_grace_0_1_128",
    )
    if ("locked_popup_grace_0_1_128" !in guard) {
        throw GradleException("A janela de protecao da tela bloqueada nao foi ampliada.")
    }
    guardFile.writeText(guard)

    var service = serviceFile.readText()
    if ("locked_popup_fast_route_capture_0_1_128" in service) return

    val fieldsAnchor = "    private var lastAccessibilityAcceptedAtMillis127: Long = 0L // accessibility_first_timestamp_0_1_127\n"
    service = replaceRequired128(
        service,
        fieldsAnchor,
        fieldsAnchor + """    private var lockedPopupLeaseUntilMillis128: Long = 0L // locked_popup_lease_state_0_1_128
    private var lastAutomaticCaptureSignature128: String? = null
    private var lastAutomaticCaptureAtMillis128: Long = 0L
    private val automaticRideCaptureStore128 by lazy(LazyThreadSafetyMode.NONE) {
        AutomaticRideCaptureStore(applicationContext)
    } // automatic_ride_capture_store_0_1_128
""",
        "estado da tela bloqueada e captura automatica",
    )

    val oldEventGate = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        if (!shouldScanPackage(resolvedPackage)) {
            if (universalForegroundPackageName != resolvedPackage) universalWindowGeneration += 1L
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage)) // universal_package_block_reason_0_1_126
            return
        }
        if (accessibilityEventFloodGate.classify(
                packageName = resolvedPackage,
                eventType = event.eventType,
                monitoredPackage = true,
            ) == AccessibilityEventMode.Ignore
        ) return // selected_apps_event_gate_0_1_122
        val protectActiveRoute = UniversalFastReadPolicy.shouldProtectRouteFromForeignEvent(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
            routeInFlight = universalRouteJob?.isActive == true,
            lastActiveReadAtMillis = universalLastActiveReadAtMillis,
            nowMillis = System.currentTimeMillis(),
            activeRidePackageName = universalActiveRidePackageName,
            incomingPackageName = resolvedPackage,
        )
        if (protectActiveRoute) {
            traceEvent("universal.foreground ignored_foreign_event_during_route=true incoming=${dollar}resolvedPackage active=${dollar}{universalActiveRidePackageName.orEmpty()}")
            return
        }
"""
    val newEventGate = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val leasedRidePackage128 = activeLockedRidePackage128()
        val protectLockedPopup128 = leasedRidePackage128 != null &&
            isTransientSystemWindowPackage128(resolvedPackage) &&
            normalizePackageName(resolvedPackage) != normalizePackageName(leasedRidePackage128)
        if (protectLockedPopup128) {
            traceEvent("locked.popup foreign_window_ignored=true incoming=${dollar}resolvedPackage active=${dollar}leasedRidePackage128")
            return
        } // locked_popup_event_before_package_block_0_1_128
        if (!shouldScanPackage(resolvedPackage)) {
            if (universalForegroundPackageName != resolvedPackage) universalWindowGeneration += 1L
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage)) // universal_package_block_reason_0_1_126
            return
        }
        if (accessibilityEventFloodGate.classify(
                packageName = resolvedPackage,
                eventType = event.eventType,
                monitoredPackage = true,
            ) == AccessibilityEventMode.Ignore
        ) return // selected_apps_event_gate_0_1_122
"""
    service = replaceRequired128(service, oldEventGate, newEventGate, "portaria anterior ao SystemUI")

    val matchAnchor = """        val manualCardMatch = RideCardTemplateMatcher.match(
            text = snapshotText,
"""
    service = replaceRequired128(
        service,
        matchAnchor,
        """        requestAutomaticRideCapture128(
            packageName = selectedPackageForCard,
            text = snapshotText,
            pickup = trigger.pickup,
            destination = trigger.destination,
            screenHash = trigger.screenHash,
        ) // automatic_capture_before_model_match_0_1_128
        val manualCardMatch = RideCardTemplateMatcher.match(
            text = snapshotText,
""",
        "captura automatica antes do match",
    )

    service = replaceRequired128(
        service,
        """        universalActiveRidePackageName = universalResolvedForegroundPackage()
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
""",
        """        universalActiveRidePackageName = selectedPackageForCard
        lockedPopupLeaseUntilMillis128 = System.currentTimeMillis() + LOCKED_POPUP_LEASE_MILLIS_128
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
""",
        "renovacao da sessao do popup bloqueado",
    )

    val oldResolvedFunction = """    private fun universalResolvedForegroundPackage(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    }
"""
    val newResolvedFunction = """    private fun universalResolvedForegroundPackage(): String? {
        val rootPackage128 = currentRootPackageName()
        activeLockedRidePackage128()?.let { leasedPackage128 ->
            if (isTransientSystemWindowPackage128(rootPackage128)) return leasedPackage128
        }
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = rootPackage128,
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    } // locked_popup_resolved_package_0_1_128
"""
    service = replaceRequired128(service, oldResolvedFunction, newResolvedFunction, "resolucao do pacote bloqueado")

    val oldCurrentWindow = """    private fun currentWindowPackageName(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    } // universal_overlay_window_resolver_0_1_106
"""
    val newCurrentWindow = """    private fun currentWindowPackageName(): String? {
        val rootPackage128 = currentRootPackageName()
        activeLockedRidePackage128()?.let { leasedPackage128 ->
            if (isTransientSystemWindowPackage128(rootPackage128)) return leasedPackage128
        }
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = rootPackage128,
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    } // universal_overlay_window_resolver_0_1_106 locked_popup_current_window_0_1_128
"""
    service = replaceRequired128(service, oldCurrentWindow, newCurrentWindow, "janela atual durante keyguard")

    service = replaceRequired128(
        service,
        """        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        universalLiveReadGate.reset()
""",
        """        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        lockedPopupLeaseUntilMillis128 = 0L // locked_popup_lease_clear_0_1_128
        universalLiveReadGate.reset()
""",
        "limpeza da sessao bloqueada",
    )

    val helperFragment = helperFragmentFile.readText().trimEnd() + "\n\n"
    val helperAnchor = "    private fun universalResolvedForegroundPackage(): String? {\n"
    service = replaceRequired128(
        service,
        helperAnchor,
        helperFragment + helperAnchor,
        "helpers de captura e keyguard",
    )

    val constantsAnchor = "        const val SCAN_LOOP_MS = 350L // adaptive_fallback_scan_0_1_127\n"
    service = replaceRequired128(
        service,
        constantsAnchor,
        constantsAnchor + """        const val LOCKED_POPUP_LEASE_MILLIS_128 = 12_000L
        const val AUTOMATIC_CAPTURE_DEDUPE_MILLIS_128 = 90_000L
""",
        "constantes do popup bloqueado",
    )

    service = replaceFunction128(
        service,
        "    private suspend fun analyzeUniversalTwoAddress(",
        routeFragmentFile.readText().trimEnd(),
    )

    service += "\n// locked_popup_fast_route_capture_0_1_128\n"
    listOf(
        "locked_popup_event_before_package_block_0_1_128",
        "locked_popup_resolved_package_0_1_128",
        "locked_popup_current_window_0_1_128",
        "locked_popup_lease_clear_0_1_128",
        "automatic_capture_before_model_match_0_1_128",
        "automatic_ride_capture_runtime_0_1_128",
        "direct_address_route_fast_path_0_1_128",
        "direct_address_routes_parallel_0_1_128",
        "direct_address_route_timing_0_1_128",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Fluxo rapido 0.1.128 incompleto: $marker")
    }
    if (service.indexOf("locked_popup_event_before_package_block_0_1_128") > service.indexOf("universal_package_block_reason_0_1_126")) {
        throw GradleException("SystemUI ainda e bloqueado antes da protecao do card.")
    }
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchLockedPopupFastRoute128(
            serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            guardFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt").asFile,
            routeFragmentFile = layout.projectDirectory.file("patches/analyze-universal-address-route-0.1.128.txt").asFile,
            helperFragmentFile = layout.projectDirectory.file("patches/locked-popup-capture-helpers-0.1.128.txt").asFile,
        )
    }
}
