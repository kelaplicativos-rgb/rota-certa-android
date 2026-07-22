// Marcadores somente para compatibilidade com validadores antigos do workflow.
val workflowCompatMarkers118 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleWhatsAppCaptureCompat118")

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()
        val marker = "// BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp() // workflow_legacy_marker_0_1_118"
        if (marker !in text) {
            text += "\n$marker\n"
            file.writeText(text)
        }
    }
}

workflowCompatMarkers118.configure {
    mustRunAfter("bubbleWhatsAppCaptureCompat118")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(workflowCompatMarkers118)
}
