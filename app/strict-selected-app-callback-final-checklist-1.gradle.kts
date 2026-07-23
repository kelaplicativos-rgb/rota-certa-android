// Fechamento final do checklist 1: revalida a janela selecionada dentro do
// callback do screenshot comum, antes de converter bitmap ou executar OCR.

fun patchStrictScreenshotCallbackChecklist1(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente para proteger o callback de OCR.")
    var service = serviceFile.readText()
    if ("strict_screenshot_callback_before_ocr_checklist_1" in service) return

    val requestStart = service.indexOf("    private fun requestScreenshotAnalysis(")
    val requestEnd = if (requestStart >= 0) service.indexOf("    private fun collectVisibleText(", requestStart) else -1
    if (requestStart < 0 || requestEnd < 0) throw GradleException("Regiao do screenshot comum nao encontrada para o checklist 1.")

    var requestRegion = service.substring(requestStart, requestEnd)
    val callbackAnchor = "                    override fun onSuccess(screenshot: ScreenshotResult) {\n"
    if (callbackAnchor !in requestRegion) throw GradleException("Callback do screenshot comum nao encontrado para o checklist 1.")

    requestRegion = requestRegion.replaceFirst(
        callbackAnchor,
        callbackAnchor + """                        if (!hasStrictSelectedRootChecklist1()) {
                            screenshotInProgress.set(false)
                            return // strict_screenshot_callback_before_ocr_checklist_1
                        }
""",
    )
    service = service.substring(0, requestStart) + requestRegion + service.substring(requestEnd)

    if ("strict_screenshot_callback_before_ocr_checklist_1" !in service) {
        throw GradleException("A protecao antes do OCR nao foi aplicada.")
    }
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchStrictScreenshotCallbackChecklist1(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
