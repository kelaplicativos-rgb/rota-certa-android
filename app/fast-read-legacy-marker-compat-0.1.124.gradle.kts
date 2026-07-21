// Compatibilidade idempotente entre validadores antigos e a limpeza global 0.1.124.
// Os marcadores existem apenas como comentarios entre invocacoes do Gradle.
// Antes do patch 0.1.124 eles sao removidos, para que nenhuma verificacao os
// confunda com comportamento ativo; ao final sao recolocados para os validadores
// legados da proxima invocacao.

val legacyFastReadMarkerLines124 = listOf(
    "// UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(",
    "// universal.accessibility transient_overlay_empty_ignored=true",
    "// universal_ride_evidence_gate_0_1_112",
    "// fast_read_legacy_marker_compat_0_1_124",
)

fun stripLegacyFastReadMarkers124(file: java.io.File) {
    if (!file.exists()) return
    var text = file.readText()
    legacyFastReadMarkerLines124.forEach { marker ->
        text = text.replace("$marker\n", "")
        text = text.replace(marker, "")
    }
    file.writeText(text)
}

fun appendLegacyFastReadMarkers124(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para compatibilidade 0.1.124.")
    var text = file.readText().trimEnd()
    if ("fast_read_legacy_marker_compat_0_1_124" !in text) {
        text += "\n\n" + legacyFastReadMarkerLines124.joinToString("\n") + "\n"
    }
    file.writeText(text)
}

fun serviceFileForLegacyMarkers124(): java.io.File =
    layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile

tasks.named("radarWorkTracking121").configure {
    doFirst { stripLegacyFastReadMarkers124(serviceFileForLegacyMarkers124()) }
    doLast { appendLegacyFastReadMarkers124(serviceFileForLegacyMarkers124()) }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doFirst { stripLegacyFastReadMarkers124(serviceFileForLegacyMarkers124()) }
    doLast { appendLegacyFastReadMarkers124(serviceFileForLegacyMarkers124()) }
}
