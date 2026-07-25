// Limpeza final da interface 0.1.127.
// Os patches de restauracao substituem o corpo das funcoes e preservam a anotacao
// original; esta etapa garante exatamente uma @Composable por funcao.

fun cleanupManualUiAnnotations127(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado para limpeza de anotacoes 0.1.127.")
    var text = file.readText()
    while ("@Composable\n@Composable\n" in text) {
        text = text.replace("@Composable\n@Composable\n", "@Composable\n")
    }
    if ("@Composable\n@Composable\n" in text) {
        throw GradleException("Anotacao @Composable duplicada ainda existe.")
    }
    if ("manual_ui_annotation_cleanup_0_1_127" !in text) {
        text += "\n// manual_ui_annotation_cleanup_0_1_127\n"
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        cleanupManualUiAnnotations127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
