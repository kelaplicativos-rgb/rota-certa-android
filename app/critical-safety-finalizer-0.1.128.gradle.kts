// Rota Certa 0.1.128 — finalizador de seguranca da etapa critica.
// Deve ser aplicado por ultimo no build principal, depois do patch de tela bloqueada.
//
// Impede duas regressoes:
// 1) a protecao especifica do keyguard nao amplia a tolerancia GLOBAL;
// 2) a captura automatica nao ocupa a mesma trava usada pelo OCR.

fun patchCriticalSafetyFinalizer128(
    serviceFile: java.io.File,
    guardFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no finalizador 0.1.128.")
    if (!guardFile.exists()) throw GradleException("UniversalRuntimeGuards.kt ausente no finalizador 0.1.128.")

    var guard = guardFile.readText()
    guard = guard.replace(
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 12_000L // locked_popup_grace_0_1_128",
        "const val ROUTE_INFLIGHT_GRACE_MILLIS = 2_500L // global_route_grace_restored_0_1_128",
    )
    if ("const val ROUTE_INFLIGHT_GRACE_MILLIS = 12_000L" in guard) {
        throw GradleException("A tolerancia global de rota ainda foi ampliada para 12 segundos.")
    }
    if ("global_route_grace_restored_0_1_128" !in guard) {
        throw GradleException("A tolerancia global original nao foi restaurada.")
    }
    guardFile.writeText(guard)

    var service = serviceFile.readText()
    if ("automatic_capture_independent_gate_0_1_128" !in service) {
        val fieldAnchor = "    private var lastAutomaticCaptureAtMillis128: Long = 0L\n"
        if (fieldAnchor !in service) throw GradleException("Campos da captura automatica nao foram encontrados.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + "    private val automaticCaptureInProgress128 = java.util.concurrent.atomic.AtomicBoolean(false) // automatic_capture_independent_gate_0_1_128\n",
        )

        val functionStart = service.indexOf("    private fun requestAutomaticRideCapture128(")
        val functionEnd = if (functionStart >= 0) {
            service.indexOf("    private fun universalResolvedForegroundPackage(): String?", functionStart)
        } else {
            -1
        }
        if (functionStart < 0 || functionEnd < 0) {
            throw GradleException("Funcao de captura automatica nao encontrada.")
        }
        var functionRegion = service.substring(functionStart, functionEnd)
        functionRegion = functionRegion
            .replace(
                "if (!screenshotInProgress.compareAndSet(false, true)) return",
                "if (!automaticCaptureInProgress128.compareAndSet(false, true)) return",
            )
            .replace("screenshotInProgress.set(false)", "automaticCaptureInProgress128.set(false)")
        if ("screenshotInProgress" in functionRegion) {
            throw GradleException("A captura automatica ainda disputa a trava do OCR.")
        }
        if ("automaticCaptureInProgress128" !in functionRegion) {
            throw GradleException("A trava independente nao foi ligada a captura automatica.")
        }
        service = service.substring(0, functionStart) + functionRegion + service.substring(functionEnd)
    }

    if ("screenshotInProgress" !in service) {
        throw GradleException("A trava original do OCR foi removida indevidamente.")
    }
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchCriticalSafetyFinalizer128(
            serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            guardFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt").asFile,
        )
    }
}
