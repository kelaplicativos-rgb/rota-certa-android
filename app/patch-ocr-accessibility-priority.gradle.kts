val patchOcrAccessibilityPriority by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("lastAccessibilityTextMillis" !in text) {
            text = text.replace(
                "    private var lastAccessibilityText: String = \"\"\n",
                "    private var lastAccessibilityText: String = \"\"\n    private var lastAccessibilityTextMillis: Long = 0L\n",
            )
        }

        if ("lastCardSaveCandidatePackageName" !in text) {
            text = text.replace(
                "    private var lastOcrText: String = \"\"\n",
                "    private var lastOcrText: String = \"\"\n    private var lastCardSaveCandidatePackageName: String? = null\n    private var lastCardSaveCandidateText: String = \"\"\n    private var lastCardSaveCandidateAtMillis: Long = 0L\n",
            )
        }

        if ("cardSaveScreenshotRequestedUntilMillis" !in text) {
            text = text.replace(
                "    private var lastScreenshotMillis: Long = 0L\n",
                "    private var lastScreenshotMillis: Long = 0L\n    private var cardSaveScreenshotRequestedUntilMillis: Long = 0L\n",
            )
        }

        if ("shouldPreferRecentAccessibilityCard" !in text) {
            text = text.replace(
"""        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
""",
"""        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        if (source == TextSource.Ocr && shouldPreferRecentAccessibilityCard(packageName, text)) {
            traceEvent("ocr.ignored accessibility_priority=true raw_hash=${dollar}{text.snapshotHash()}")
            return
        }
""",
            )

            text = text.replace(
"""    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
""",
"""    private fun shouldPreferRecentAccessibilityCard(packageName: String?, ocrText: String): Boolean {
        val accessibilityText = lastAccessibilityText.takeIf { it.isNotBlank() } ?: return false
        val ageMillis = System.currentTimeMillis() - lastAccessibilityTextMillis
        if (ageMillis !in 0..ACCESSIBILITY_OCR_PRIORITY_MS) return false
        if (accessibilityText.snapshotHash() == ocrText.snapshotHash()) return false
        val accessibilityParse = parser.parseWithMetadata(accessibilityText, packageName)
        if (!RideOfferDetector.looksLikeRideOffer(accessibilityText, accessibilityParse.fields, packageName)) return false
        RideCardTemplateMatcher.match(accessibilityText, packageName, currentCardTemplates) ?: return false
        val ocrMatch = RideCardTemplateMatcher.match(ocrText, packageName, currentCardTemplates)
        return ocrMatch == null || ocrText.length < (accessibilityText.length * 0.75).toInt()
    }

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
""",
            )
        }

        text = text.replace(
"""            lastAccessibilityText = ""
            lastOcrText = ""
""",
"""            lastAccessibilityText = ""
            lastAccessibilityTextMillis = 0L
            lastOcrText = ""
""",
        )

        text = text.replace(
"""            TextSource.Accessibility -> lastAccessibilityText = text.trim()
            TextSource.Ocr -> lastOcrText = text.trim()
""",
"""            TextSource.Accessibility -> {
                lastAccessibilityText = text.trim()
                lastAccessibilityTextMillis = System.currentTimeMillis()
            }
            TextSource.Ocr -> lastOcrText = text.trim()
""",
        )

        text = text.replace(
"""        lastAccessibilityText = ""
        lastOcrText = ""
""",
"""        lastAccessibilityText = ""
        lastAccessibilityTextMillis = 0L
        lastOcrText = ""
""",
        )

        if ("const val ACCESSIBILITY_OCR_PRIORITY_MS" !in text) {
            text = text.replace(
                "        const val SCAN_LOOP_MS = 180L\n",
                "        const val ACCESSIBILITY_OCR_PRIORITY_MS = 700L\n        const val SCAN_LOOP_MS = 180L\n",
            )
            text = text.replace(
                "        const val SCAN_LOOP_MS = 850L\n",
                "        const val ACCESSIBILITY_OCR_PRIORITY_MS = 700L\n        const val SCAN_LOOP_MS = 850L\n",
            )
        }

        if (text != original) file.writeText(text)
    }
}

patchOcrAccessibilityPriority.configure {
    mustRunAfter("patchRealtimeBubbleEngine")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchOcrAccessibilityPriority)
}
