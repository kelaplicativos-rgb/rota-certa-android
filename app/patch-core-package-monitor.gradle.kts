// Liga o controle de pacotes/app monitorado ao CorePackageMonitor.
// O servico Android deixa de decidir sozinho quais pacotes podem ser lidos.

val corePackageMonitorPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("core_package_monitor_0_1_93" !in text) {
            val startToken = "    private fun shouldScanPackage(packageName: String?): Boolean {\n"
            val endToken = "    private fun recordDiagnostic(\n"
            val start = text.indexOf(startToken)
            val end = if (start >= 0) text.indexOf(endToken, start) else -1
            if (start < 0 || end < 0) {
                throw org.gradle.api.GradleException("Nao encontrei a regiao de controle de pacotes para mover ao Core.")
            }
            val delegatedBlock = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = packageName,
            ownPackageName = this.packageName,
            settings = currentSettings,
        )
        if (classification.canScan) {
            traceEvent("core.package allow package=${'$'}{classification.packageName} module=${'$'}{classification.module}") // core_package_monitor_0_1_93
        }
        return classification.canScan
    }

    private fun selectedRidePackages(settings: AppSettings): Set<String> =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.selectedRidePackages(settings)

    private fun scanBlockReason(packageName: String?): String =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = packageName,
            ownPackageName = this.packageName,
            settings = currentSettings,
        ).reason

"""
            text = text.substring(0, start) + delegatedBlock + text.substring(end)
        }

        if ("core_package_monitor_passive_0_1_93" !in text) {
            val passiveBlock = """    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.isPassive(
            packageName = packageName,
            ownPackageName = this.packageName,
        ) // core_package_monitor_passive_0_1_93

"""
            val passiveStartToken = "    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean {\n"
            val passiveEndToken = "    private fun normalizePackageName(packageName: String?): String? =\n"
            val passiveStart = text.indexOf(passiveStartToken)
            val passiveEnd = if (passiveStart >= 0) text.indexOf(passiveEndToken, passiveStart) else -1
            if (passiveStart >= 0 && passiveEnd > passiveStart) {
                text = text.substring(0, passiveStart) + passiveBlock + text.substring(passiveEnd)
            } else {
                val normalizeStart = text.indexOf(passiveEndToken)
                if (normalizeStart < 0) {
                    throw org.gradle.api.GradleException("Nao encontrei normalizePackageName para inserir pacote passivo Core.")
                }
                text = text.substring(0, normalizeStart) + passiveBlock + text.substring(normalizeStart)
            }
        }

        if ("core_package_monitor_0_1_93" !in text) {
            throw org.gradle.api.GradleException("CorePackageMonitor nao assumiu shouldScanPackage.")
        }
        if ("core_package_monitor_passive_0_1_93" !in text) {
            throw org.gradle.api.GradleException("CorePackageMonitor nao assumiu pacote passivo.")
        }

        if (text != original) file.writeText(text)
    }
}

corePackageMonitorPatch.configure {
    mustRunAfter("coreScreenReadEnginePatch", "coreBubbleStatePatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(corePackageMonitorPatch)
}
