val patchNullTemplateUniversal by tasks.registering {
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    inputs.file(matcherFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = matcherFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        text = text.replace(
            "val universalPackage = isUniversalLearnedPackage(match.template.packageName)",
            "val universalPackage = match.template.packageName.isNullOrBlank() || isUniversalLearnedPackage(match.template.packageName)",
        )
        if (text != original) file.writeText(text)
    }
}

patchNullTemplateUniversal.configure {
    mustRunAfter("patchFactoryCleanNoFlicker")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchNullTemplateUniversal)
}
