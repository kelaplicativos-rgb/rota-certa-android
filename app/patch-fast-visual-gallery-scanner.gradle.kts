val fastVisualGalleryScanner by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace("const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 220L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 240L")
        text = text.replace("const val DIAGNOSTIC_EVENT_LIMIT = 60", "const val DIAGNOSTIC_EVENT_LIMIT = 420")
        text = text.replace("const val DIAGNOSTIC_EVENT_LIMIT = 180", "const val DIAGNOSTIC_EVENT_LIMIT = 420")
        text = text.replace("const val DIAGNOSTIC_EVENT_LIMIT = 320", "const val DIAGNOSTIC_EVENT_LIMIT = 420")

        text = text.replace(
"""        if (isCaptureOnlyLearningPackage(normalized)) return false
        val settings = currentSettings
        if (!settings.appEnabled) return false
        if (normalized in selectedRidePackages(settings)) return true
        return RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)
""",
"""        val settings = currentSettings
        if (!settings.appEnabled) return false
        if (normalized in selectedRidePackages(settings)) return true
        if (RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)) return true
        if (isCaptureOnlyLearningPackage(normalized)) return false
        return false
""",
        )

        text = text.replace(
"""        if (isCaptureOnlyLearningPackage(normalized)) return "Pacote usado apenas para ensinar por print; leitura ao vivo pausada: ${'$'}normalized."
        if (normalized !in selectedRidePackages(currentSettings)) {
            return if (RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)) {
                "Pacote permitido por modelo universal aprendido: ${'$'}normalized."
            } else {
                "Pacote sem modelo de card cadastrado pelo usuario; bolinha em espera: ${'$'}normalized."
            }
        }
""",
"""        if (normalized !in selectedRidePackages(currentSettings)) {
            return if (RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)) {
                if (isCaptureOnlyLearningPackage(normalized)) {
                    "Pacote visual liberado para leitura rapida por modelo universal: ${'$'}normalized."
                } else {
                    "Pacote permitido por modelo universal aprendido: ${'$'}normalized."
                }
            } else if (isCaptureOnlyLearningPackage(normalized)) {
                "Pacote usado para ensinar por print; sem modelo universal ainda, leitura ao vivo pausada: ${'$'}normalized."
            } else {
                "Pacote sem modelo de card cadastrado pelo usuario; bolinha em espera: ${'$'}normalized."
            }
        }
""",
        )

        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L)", "scheduleVisibleTextAnalysis(delayMs = 0L)")
        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 20L)", "scheduleVisibleTextAnalysis(delayMs = 0L)")
        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)", "scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)")
        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)", "scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)")

        text = text.replace(
"""        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
            traceEvent("accessibility.schedule skipped active_job=true")
            return
        }
""",
"""        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
            if (delayMs == 0L) {
                analyzeJob?.cancel()
                traceEvent("accessibility.schedule supersede active_job=true")
            } else {
                traceEvent("accessibility.schedule skipped active_job=true")
                return
            }
        }
""",
        )

        if ("visual.accessibility.skip chrome_text" !in text) {
            text = text.replace(
"""        val windowPackageName = currentWindowPackageName()
        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
""",
"""        val windowPackageName = currentWindowPackageName()
        if (source == TextSource.Accessibility && isFastVisualPackage(windowPackageName) && !RideScreenTextClassifier.looksLikeRideCard(text)) {
            traceEvent("visual.accessibility.skip chrome_text length=${'$'}{text.length}")
            return
        }
        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
""",
            )
        }

        val resolveRidePackageReplacement = """    private fun resolveRidePackageForText(
        windowPackageName: String?,
        text: String,
        allowPopupCandidate: Boolean,
    ): String? {
        val normalizedWindowPackage = normalizePackageName(windowPackageName)
        val inferredPackage = RideCardTemplateMatcher.inferPackageName(text)

        if (isFastVisualPackage(normalizedWindowPackage)) {
            if (inferredPackage != null && shouldScanPackage(inferredPackage)) {
                traceEvent("visual.package.inferred source=${'$'}{normalizedWindowPackage.orEmpty()} inferred=${'$'}inferredPackage")
                return inferredPackage
            }
            if (RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates) && shouldScanPackage(normalizedWindowPackage)) {
                traceEvent("visual.package.universal source=${'$'}{normalizedWindowPackage.orEmpty()}")
                return normalizedWindowPackage
            }
        }

        if (allowPopupCandidate && inferredPackage != null && shouldScanPackage(inferredPackage)) return inferredPackage
        if (shouldScanPackage(normalizedWindowPackage)) return normalizedWindowPackage
        if (!allowPopupCandidate) return normalizedWindowPackage
        return inferredPackage?.takeIf { inferred -> shouldScanPackage(inferred) }
    }

    private fun looksLikeRegisteredPopupCandidate"""

        text = Regex("(?s)    private fun resolveRidePackageForText\\(\\s*windowPackageName: String\\?,\\s*text: String,\\s*allowPopupCandidate: Boolean,\\s*\\): String\\? \\{.*?    private fun looksLikeRegisteredPopupCandidate").replace(text) {
            resolveRidePackageReplacement
        }

        text = text.replace(
"""            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${'$'}{destinationCoordinate != null}")
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            traceEvent("route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")
""",
"""            val coordinates = kotlinx.coroutines.coroutineScope {
                val destinationDeferred = kotlinx.coroutines.async { fields.destination?.let { geocodeBest(it, region, settings) } }
                val homeDeferred = kotlinx.coroutines.async { settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings) }
                val alternativeDeferred = kotlinx.coroutines.async { settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings) }
                Triple(destinationDeferred.await(), homeDeferred.await(), alternativeDeferred.await())
            }
            val destinationCoordinate = coordinates.first
            val homeCoordinate = coordinates.second
            val alternativeCoordinate = coordinates.third
            traceEvent("geocode.parallel destination=${'$'}{destinationCoordinate != null} home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val routedDistances = kotlinx.coroutines.coroutineScope {
                val homeDistanceDeferred = kotlinx.coroutines.async { routeDistanceKm(destinationCoordinate, homeCoordinate, settings) }
                val alternativeDistanceDeferred = kotlinx.coroutines.async { routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings) }
                homeDistanceDeferred.await() to alternativeDistanceDeferred.await()
            }
            val homeDistanceKm = routedDistances.first
            val alternativeDistanceKm = routedDistances.second
            traceEvent("route.parallel distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")
""",
        )

        if ("private fun isFastVisualPackage(" !in text) {
            text = text.replace(
"""    private fun isCaptureOnlyLearningPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        return normalized.contains("documentsui") ||
            normalized.contains("android.apps.nbu.files") ||
            normalized.contains("sec.android.app.myfiles") ||
            normalized.contains("android.apps.photos") ||
            normalized.contains("android.apps.docs") ||
            normalized.contains("chrome")
    }
""",
"""    private fun isCaptureOnlyLearningPackage(packageName: String?): Boolean = isFastVisualPackage(packageName)

    private fun isFastVisualPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        return normalized.contains("documentsui") ||
            normalized.contains("android.apps.nbu.files") ||
            normalized.contains("sec.android.app.myfiles") ||
            normalized.contains("sec.android.gallery3d") ||
            normalized.contains("android.apps.photos") ||
            normalized.contains("android.apps.docs") ||
            normalized.contains("chrome")
    }
""",
            )
        }

        if (text != original) file.writeText(text)
    }
}

fastVisualGalleryScanner.configure {
    mustRunAfter("bubblePersistentActionsAndHitbox")
    mustRunAfter("bubbleDoubleTapDiagnosticsRobust")
    mustRunAfter("bubbleDoubleTapCardCapture")
    mustRunAfter("universalAiCardLearning")
    mustRunAfter("bubbleRouteDistanceOnly")
    mustRunAfter("unmonitoredScreenshotGuard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(fastVisualGalleryScanner)
}
