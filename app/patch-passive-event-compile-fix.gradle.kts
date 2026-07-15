val passiveEventCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    fun removeFunction(text: String, signature: String): String {
        var current = text
        while (true) {
            val start = current.indexOf(signature)
            if (start < 0) return current
            val nextFun = current.indexOf("\n    private fun ", start + signature.length)
            val nextEnum = current.indexOf("\n    private enum class ", start + signature.length)
            val nextCompanion = current.indexOf("\n    private companion object", start + signature.length)
            val candidates = listOf(nextFun, nextEnum, nextCompanion).filter { it > start }
            val end = candidates.minOrNull() ?: current.length
            current = current.removeRange(start, end)
        }
    }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        val passiveListStart = "            val passiveEventPackages = setOf(\n"
        val passiveDecisionLine = "            val eventPackageIsPassive = packageName == this.packageName || packageName in passiveEventPackages\n"
        val passiveIfLine = "            if (eventPackageIsPassive) {"
        val inlineIfLine = "            if (packageName == this.packageName || packageName in setOf(\n" +
            "                \"android\",\n" +
            "                \"com.android.launcher\",\n" +
            "                \"com.android.settings\",\n" +
            "                \"com.android.systemui\",\n" +
            "                \"com.google.android.apps.maps\",\n" +
            "                \"com.google.android.apps.nbu.files\",\n" +
            "                \"com.google.android.inputmethod.latin\",\n" +
            "                \"com.google.android.packageinstaller\",\n" +
            "                \"com.openai.chatgpt\",\n" +
            "                \"com.sec.android.app.launcher\",\n" +
            "                \"com.samsung.android.app.settings\",\n" +
            "                \"com.samsung.android.honeyboard\",\n" +
            "                \"com.waze\",\n" +
            "            )) {"

        while (true) {
            val listStart = text.indexOf(passiveListStart)
            if (listStart < 0) break
            val decisionStart = text.indexOf(passiveDecisionLine, listStart)
            if (decisionStart < 0) break
            val afterDecision = decisionStart + passiveDecisionLine.length
            text = text.removeRange(listStart, afterDecision)
            text = text.replace(passiveIfLine, inlineIfLine)
        }

        if ("final_passive_ignored_dedup_0_1_93" !in text) {
            text = removeFunction(text, "    private fun isPassiveIgnoredPackage(packageName: String?): Boolean")
            val normalizeStart = text.indexOf("    private fun normalizePackageName(packageName: String?): String? =\n")
            if (normalizeStart < 0) {
                throw org.gradle.api.GradleException("Nao encontrei normalizePackageName para reinserir helper passivo unico.")
            }
            val helper = """    private fun isPassiveIgnoredPackage(packageName: String?): Boolean =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.isPassive(
            packageName = packageName,
            ownPackageName = this.packageName,
        ) // final_passive_ignored_dedup_0_1_93

"""
            text = text.substring(0, normalizeStart) + helper + text.substring(normalizeStart)
        }

        if ("val eventPackageIsPassive" in text) {
            throw org.gradle.api.GradleException("Variavel duplicada eventPackageIsPassive ainda existe no servico.")
        }
        if ("passive_event_compile_fix_0_1_82" !in text) {
            text = text.replace(
                "// passive_event_no_popup_scan_0_1_82",
                "// passive_event_no_popup_scan_0_1_82 passive_event_compile_fix_0_1_82",
            )
        }
        if ("final_passive_ignored_dedup_0_1_93" !in text) {
            throw org.gradle.api.GradleException("Helper passivo unico nao foi instalado no final.")
        }

        if (text != original) file.writeText(text)
    }
}

passiveEventCompileFix.configure {
    mustRunAfter(
        "liveRideWindowEventGuard",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
        "noStickyDecisionCleanup",
        "patchBubbleRenderStability",
        "corePackageMonitorPatch",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(passiveEventCompileFix)
}

apply(from = "patch-report-stale-lifecycle-fix.gradle.kts")
apply(from = "patch-report-stale-lifecycle-compile-fix.gradle.kts")
apply(from = "patch-indrive-markerless-live-card.gradle.kts")
apply(from = "patch-open-all-apps-all-screens.gradle.kts")
apply(from = "patch-open-all-apps-all-screens-fix.gradle.kts")
apply(from = "patch-universal-last-address-final.gradle.kts")
apply(from = "patch-universal-last-address-final-v2.gradle.kts")
