// Rota Certa 0.1.127
// Eventos de acessibilidade continuam imediatos; o ciclo continuo vira apenas
// uma rede de seguranca, reduzindo CPU, bateria e solicitacoes repetidas de OCR.

fun patchRuntimeEfficiency127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para eficiencia 0.1.127.")
    var service = serviceFile.readText()

    service = service.replace(
        Regex("const val SCAN_LOOP_MS = \\d+L"),
        "const val SCAN_LOOP_MS = 350L // adaptive_fallback_scan_0_1_127",
    )

    if ("adaptive_fallback_scan_0_1_127" !in service) {
        throw GradleException("Intervalo final do ciclo de leitura nao foi reduzido com seguranca.")
    }
    if ("const val SCAN_LOOP_MS = 120L" in service) {
        throw GradleException("Polling agressivo de 120 ms ainda esta ativo.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchRuntimeEfficiency127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
