// Reparo final da galeria inserida pelo checklist 6.
fun repairCaptureLibraryUiChecklist6(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no reparo da galeria.")
    var text = file.readText()
    while ("@Composable\n@Composable\nprivate fun AutomaticRideCaptureGallery129" in text) {
        text = text.replace(
            "@Composable\n@Composable\nprivate fun AutomaticRideCaptureGallery129",
            "@Composable\nprivate fun AutomaticRideCaptureGallery129",
        )
    }
    if ("@Composable\nprivate fun AutomaticRideCaptureGallery129" !in text ||
        "capture_library_split_final_checklist_6" !in text
    ) {
        throw GradleException("Galeria final de capturas não foi materializada corretamente.")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        repairCaptureLibraryUiChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
