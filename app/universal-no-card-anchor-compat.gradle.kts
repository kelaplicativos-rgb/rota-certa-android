// Alguns patches legados removem o DiagnosticExpander antes da etapa final.
// Esta tarefa cria apenas uma ancora temporaria de fonte para o removedor final;
// a ancora e substituida na mesma compilacao e nao aparece no APK.
val universalNoCardAnchorCompat by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalIdempotenceCompatibilityGuard"))

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado")
        var text = file.readText()
        val cardStart = text.indexOf("@Composable\nprivate fun CardModelsCard(")
        val diagnosticStart = text.indexOf("@Composable\nprivate fun DiagnosticExpander(")
        if (cardStart >= 0 && diagnosticStart < 0) {
            val savedPlacesStart = text.indexOf("@Composable\nprivate fun SavedPlacesCard(", cardStart)
            if (savedPlacesStart <= cardStart) throw GradleException("Ancora de locais nao encontrada depois dos modelos")
            val anchor = """@Composable
private fun DiagnosticExpander(
    diagnostic: LiveDiagnostic?,
) = Unit

"""
            text = text.substring(0, savedPlacesStart) + anchor + text.substring(savedPlacesStart)
            file.writeText(text)
        }
    }
}

tasks.named("universalNoCardRuntimeContract").configure {
    dependsOn(universalNoCardAnchorCompat)
}
