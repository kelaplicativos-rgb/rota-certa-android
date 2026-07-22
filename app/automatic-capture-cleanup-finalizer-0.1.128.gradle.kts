// Rota Certa 0.1.128 — limpeza periodica das capturas automaticas.
// Este script deve ser aplicado no final do critical-safety-finalizer, portanto
// somente depois que o patch de tela bloqueada criou a captura e o armazenamento.

fun patchAutomaticCaptureCleanupFinal128(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente para limpeza automatica.")
    var service = serviceFile.readText()
    if ("automatic_capture_periodic_cleanup_0_1_128" in service) return

    val fieldAnchor = "    private var lastAutomaticCaptureAtMillis128: Long = 0L\n"
    if (fieldAnchor !in service) {
        throw GradleException("Campos da captura automatica ainda nao existem no finalizador.")
    }
    service = service.replaceFirst(
        fieldAnchor,
        fieldAnchor + "    private var automaticCaptureCleanupStarted128 = false\n",
    )

    val connectedStart = service.indexOf("    override fun onServiceConnected() {")
    val connectedEnd = if (connectedStart >= 0) service.indexOf("    override fun onAccessibilityEvent(", connectedStart) else -1
    if (connectedStart < 0 || connectedEnd < 0) throw GradleException("onServiceConnected nao encontrado.")
    var connectedRegion = service.substring(connectedStart, connectedEnd)
    val readyAnchor = "        serviceReady = true\n"
    if (readyAnchor !in connectedRegion) throw GradleException("Ativacao do servico nao encontrada.")
    connectedRegion = connectedRegion.replaceFirst(
        readyAnchor,
        readyAnchor + "        startAutomaticCaptureCleanup128() // automatic_capture_periodic_cleanup_0_1_128\n",
    )
    service = service.substring(0, connectedStart) + connectedRegion + service.substring(connectedEnd)

    val helperAnchor = "    private fun activeLockedRidePackage128(nowMillis: Long = System.currentTimeMillis()): String? {\n"
    if (helperAnchor !in service) throw GradleException("Ponto de insercao da limpeza periodica nao encontrado.")
    val cleanupCode = """    private fun startAutomaticCaptureCleanup128() {
        if (automaticCaptureCleanupStarted128) return
        automaticCaptureCleanupStarted128 = true
        scope.launch(Dispatchers.IO) {
            while (serviceReady) {
                val removed128 = automaticRideCaptureStore128.purgeExpired()
                if (removed128 > 0) {
                    traceEvent("automatic.capture cleanup_removed=" + removed128)
                }
                delay(12L * 60L * 60L * 1000L)
            }
        }
    } // automatic_capture_cleanup_loop_0_1_128

"""
    service = service.replaceFirst(helperAnchor, cleanupCode + helperAnchor)

    listOf(
        "automatic_capture_periodic_cleanup_0_1_128",
        "automatic_capture_cleanup_loop_0_1_128",
        "purgeExpired()",
        "delay(12L * 60L * 60L * 1000L)",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Limpeza automatica incompleta: $marker")
    }
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchAutomaticCaptureCleanupFinal128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
