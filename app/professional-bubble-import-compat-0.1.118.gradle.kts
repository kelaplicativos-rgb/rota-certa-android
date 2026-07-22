// Compatibilidade minima de imports para o painel profissional 0.1.118.
val professionalBubbleImportCompat118 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("inAppGroupedBubbleHome115")

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
        var text = file.readText()
        if ("import androidx.compose.foundation.layout.PaddingValues" !in text) {
            val anchor = "import androidx.compose.foundation.layout.Column\n"
            if (anchor !in text) throw GradleException("Import Column ausente para adicionar PaddingValues.")
            text = text.replaceFirst(anchor, anchor + "import androidx.compose.foundation.layout.PaddingValues\n")
        }
        file.writeText(text)
    }
}

professionalBubbleImportCompat118.configure {
    mustRunAfter("inAppGroupedBubbleHome115")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(professionalBubbleImportCompat118)
}
