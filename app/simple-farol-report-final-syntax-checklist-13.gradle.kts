// Executa depois do gerador do relatório e antes do compilador Kotlin.

fun repairFinalHomeAddressReportSyntax13(file: java.io.File) {
    if (!file.exists()) throw GradleException("ManualTechnicalReportBuilder.kt ausente no reparo final 13.")
    var text = file.readText()
    val broken = "            appendLine(\"Casa: ${'$'}{settings.homeAddress.ifBlank { \\\"nao informada\\\" }}\")"
    val fixed = """            val homeAddressText = settings.homeAddress.ifBlank { "nao informada" }
            appendLine("Casa: ${'$'}homeAddressText")"""
    if (broken in text) text = text.replaceFirst(broken, fixed)
    if ("\\\"nao informada\\\"" in text) {
        throw GradleException("Aspas inválidas permaneceram no endereço da Casa.")
    }
    if ("val homeAddressText = settings.homeAddress.ifBlank" !in text) {
        throw GradleException("Endereço da Casa não foi estabilizado no relatório.")
    }
    file.writeText(text)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        repairFinalHomeAddressReportSyntax13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").asFile,
        )
    }
}
