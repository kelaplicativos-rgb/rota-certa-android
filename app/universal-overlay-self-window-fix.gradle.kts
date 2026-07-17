// Corrige a falha observada no diagnostico 0.1.105: a janela da propria
// TYPE_ACCESSIBILITY_OVERLAY era confundida com a MainActivity e cancelava a
// geocodificacao/rota imediatamente depois de encontrar os dois enderecos.

val universalOverlayWindowResolver by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("sessionDiagnosticRetention"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()

        if ("universal_overlay_self_window_fix_0_1_106" !in text) {
            if ("lastExternalWindowPackageName" !in text) {
                val fieldRegex = Regex("    private var universalActiveAddressSignature: String\\? = null[^\\n]*\\n")
                val match = fieldRegex.find(text)
                    ?: throw GradleException("Campo de assinatura universal nao encontrado")
                val replacement = match.value +
                    "    private var lastExternalWindowPackageName: String? = null\n" +
                    "    private var lastUniversalClearReason: String? = null\n"
                text = text.replaceRange(match.range, replacement)
            }

            val oldEventBlock = """        activePackageName = packageName
        if (packageName == this.packageName) {
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }
"""
            val newEventBlock = """        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = packageName,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            ownPackageName = this.packageName,
            mainActivityClassName = MainActivity::class.java.name,
            windowStateChangedType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        if (packageName != this.packageName) {
            activePackageName = packageName
            lastExternalWindowPackageName = packageName
        } else if (ownMainActivityEvent) {
            activePackageName = packageName
        }
        if (packageName == this.packageName) {
            if (ownMainActivityEvent) {
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            } else {
                traceEvent("universal.overlay.event ignored=true type=" + event.eventType)
            }
            return
        }
"""
            if (oldEventBlock !in text) {
                throw GradleException("Bloco de evento proprio que cancelava a rota nao encontrado")
            }
            text = text.replaceFirst(oldEventBlock, newEventBlock)

            val oldCurrentWindow = """    private fun currentWindowPackageName(): String? =
        currentRootPackageName() ?: activePackageName
"""
            val newCurrentWindow = """    private fun currentWindowPackageName(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    }
"""
            if (oldCurrentWindow !in text) {
                throw GradleException("Resolvedor da janela atual nao encontrado")
            }
            text = text.replaceFirst(oldCurrentWindow, newCurrentWindow)

            val signatureAssignment = "            universalActiveAddressSignature = trigger.addressSignature\n"
            if (signatureAssignment !in text) {
                throw GradleException("Assinatura da tela universal nao encontrada")
            }
            text = text.replaceFirst(
                signatureAssignment,
                signatureAssignment + "            lastUniversalClearReason = null\n",
            )

            val hadDataBlock = """        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
"""
            if (hadDataBlock !in text) {
                throw GradleException("Bloco de limpeza universal nao encontrado")
            }
            text = text.replaceFirst(
                hadDataBlock,
                hadDataBlock + "        if (!hadData && currentRadarColor == RadarColor.Idle) return\n" +
                    "        lastUniversalClearReason = reason\n",
            )

            text += "\n// universal_overlay_self_window_fix_0_1_106\n"
        }

        listOf(
            "UniversalWindowPackageResolver.resolve(",
            "UniversalWindowPackageResolver.isOwnMainActivityEvent(",
            "universal.overlay.event ignored=true",
            "if (!hadData && currentRadarColor == RadarColor.Idle) return",
            "universal_overlay_self_window_fix_0_1_106",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Correcao de overlay incompleta: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalOverlayWindowResolver)
}
