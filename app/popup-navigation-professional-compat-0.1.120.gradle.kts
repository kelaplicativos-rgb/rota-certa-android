// Compatibilidade com o validador textual profissional 0.1.118.
// A 0.1.120 agrupa BUBBLE_GROUP_READING e BUBBLE_GROUP_ACCESS na mesma
// ramificacao. Este marcador nao executa codigo nem recria a antiga Home.
fun enforcePopupNavigationProfessionalCompat120(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()
    val marker = "// BUBBLE_GROUP_ACCESS -> { // popup_navigation_professional_compat_0_1_120"
    if (marker !in text) {
        text += "\n$marker\n"
        file.writeText(text)
    }
}

val popupNavigationProfessionalCompat120 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationSeparation120")
    doLast { enforcePopupNavigationProfessionalCompat120(mainFile.asFile) }
}

popupNavigationProfessionalCompat120.configure {
    mustRunAfter("popupNavigationSeparation120")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupNavigationProfessionalCompat120)
}
