// Rota Certa 0.1.127
// Limpeza final de compilacao depois de todos os patches acumulados.

fun patchCompileFinalCleanup127(mainFile: java.io.File) {
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para limpeza final.")
    var main = mainFile.readText()

    val duplicateComposable = Regex("(?m)^@Composable\\s*\\n@Composable\\s*\\n")
    var removed = 0
    while (duplicateComposable.containsMatchIn(main)) {
        main = duplicateComposable.replaceFirst(main, "@Composable\n")
        removed += 1
    }

    if (duplicateComposable.containsMatchIn(main)) {
        throw GradleException("Ainda existem anotacoes @Composable duplicadas.")
    }

    if ("compile_final_cleanup_0_1_127" !in main) {
        main += "\n// compile_final_cleanup_0_1_127 removed_duplicate_composable=$removed\n"
    }
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchCompileFinalCleanup127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
