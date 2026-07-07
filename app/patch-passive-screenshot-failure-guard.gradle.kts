val patchPassiveScreenshotFailureGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""                    override fun onFailure(errorCode: Int) {
                        traceEvent("screenshot.request failed code=${dollar}errorCode")
                        recordDiagnostic(
                            stage = "screenshot_failed",
                            reason = "Android recusou o print da acessibilidade. Codigo: ${dollar}errorCode.",
                        )
                        screenshotInProgress.set(false)
                    }
""",
"""                    override fun onFailure(errorCode: Int) {
                        val failureWindowPackageName = currentWindowPackageName()
                        traceEvent("screenshot.request failed code=${dollar}errorCode package=${dollar}{failureWindowPackageName.orEmpty()}")
                        if (shouldIgnoreScreenshotFailure(requestedWindowPackageName, failureWindowPackageName, allowPopupCandidate)) {
                            traceEvent("screenshot.failure ignored passive_or_unmonitored=true request=${dollar}{requestedWindowPackageName.orEmpty()} current=${dollar}{failureWindowPackageName.orEmpty()}")
                            screenshotInProgress.set(false)
                            return
                        }
                        recordDiagnostic(
                            stage = "screenshot_failed",
                            reason = "Android recusou o print da acessibilidade. Codigo: ${dollar}errorCode.",
                        )
                        screenshotInProgress.set(false)
                    }
""",
        )

        if ("private fun shouldIgnoreScreenshotFailure(" !in text) {
            text = text.replace(
"""    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())
""",
"""    private fun shouldIgnoreScreenshotFailure(
        requestedPackageName: String?,
        failurePackageName: String?,
        allowPopupCandidate: Boolean,
    ): Boolean {
        val requested = normalizePackageName(requestedPackageName)
        val current = normalizePackageName(failurePackageName)
        if (requested == this.packageName || current == this.packageName) return true
        if (isPassiveDiagnosticPackage(requested) || isPassiveDiagnosticPackage(current)) return true
        if (requested in IGNORED_PACKAGES || current in IGNORED_PACKAGES) return true
        if (allowPopupCandidate && !shouldScanPackage(requested) && !shouldScanPackage(current)) return true
        return !allowPopupCandidate && !shouldScanPackage(current)
    }

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchPassiveScreenshotFailureGuard.configure {
    mustRunAfter("patchLiveWindowStaleOcr", "unmonitoredScreenshotGuard")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchPassiveScreenshotFailureGuard)
}
