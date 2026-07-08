val unmonitoredScreenshotGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""            scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            resetToIdle(reason = reason, record = true)
""",
"""            scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)
            traceEvent("screenshot.request skipped unmonitored_package=${dollar}packageName")
            resetToIdle(reason = reason, record = true)
""",
        )

        text = text.replace(
"""            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            resetToIdle(reason = reason, record = true)
""",
"""            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            traceEvent("screenshot.request skipped unmonitored_package=${dollar}packageName")
            resetToIdle(reason = reason, record = true)
""",
        )

        text = text.replace(
"""                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                        requestScreenshotAnalysis(allowPopupCandidate = true)
""",
"""                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                        traceEvent("screenshot.request skipped unmonitored_loop_package=${dollar}{packageName.orEmpty()}")
""",
        )

        text = text.replace(
"""        val inferredPackage = RideCardTemplateMatcher.inferPackageName(text)
        if (inferredPackage != null && shouldScanPackage(inferredPackage)) return inferredPackage
        return lastRidePackageName?.takeIf { lastPackage ->
            text.isNotBlank() && shouldScanPackage(lastPackage)
        }
""",
"""        val inferredPackage = RideCardTemplateMatcher.inferPackageName(text)
        if (inferredPackage != null && shouldScanPackage(inferredPackage)) return inferredPackage
        return null
""",
        )

        text = text.replace(
"""        if (!allowPopupCandidate && !shouldScanPackage(requestWindowPackageName)) return
        val now = System.currentTimeMillis()
""",
"""        if (!allowPopupCandidate && !shouldScanPackage(requestWindowPackageName)) return
        if (allowPopupCandidate && !shouldScanPackage(requestWindowPackageName) && !isPassiveDiagnosticPackage(requestWindowPackageName) && !isCardSaveScreenshotRequested()) {
            traceEvent("screenshot.request skipped unmonitored_popup_package=${dollar}{requestWindowPackageName.orEmpty()}")
            return
        }
        val now = System.currentTimeMillis()
""",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

unmonitoredScreenshotGuard.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") })
    mustRunAfter("bubbleLiveAppearance")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(unmonitoredScreenshotGuard)
}
