// Corrige apenas as aspas das chaves de medição geradas pelo checklist 13.

fun repairSimpleFarolReportStrings13(file: java.io.File) {
    if (!file.exists()) throw GradleException("ManualTechnicalReportBuilder.kt ausente no reparo 13.")
    var text = file.readText()
    text = text
        .replace("\\\"fast_farol_last_elapsed_ms\\\"", "\"fast_farol_last_elapsed_ms\"")
        .replace("\\\"fast_farol_last_path\\\"", "\"fast_farol_last_path\"")
        .replace("\\\"fast_farol_last_destination\\\"", "\"fast_farol_last_destination\"")
    if ("\\\"fast_farol_" in text) {
        throw GradleException("Aspas inválidas permaneceram no relatório de velocidade.")
    }
    listOf(
        "Tempo da ultima decisao",
        "fast_farol_last_elapsed_ms",
        "fast_farol_last_path",
        "fast_farol_last_destination",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Medição do farol ausente no relatório: $marker")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        repairSimpleFarolReportStrings13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        repairSimpleFarolReportStrings13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").asFile,
        )
    }
}
