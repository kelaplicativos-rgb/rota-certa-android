// Ajusta a interface para refletir o contrato simples do farol.

fun patchSimpleFarolUiCopy13(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no texto do farol simples.")
    var text = file.readText()
    text = text
        .replace("Modelos de cards obrigatorios", "Modelos de cards (apoio opcional)")
        .replace("Modelos de cards obrigatórios", "Modelos de cards (apoio opcional)")
        .replace("Modelos de cards obrigatorios: true", "Modelos visuais bloqueiam o farol: false")
        .replace("Modelos de cards obrigatórios: true", "Modelos visuais bloqueiam o farol: false")
        .replace("Cadastre um modelo para liberar o farol", "O modelo visual é opcional e serve apenas como apoio")
        .replace("O farol só funciona com card cadastrado", "O farol funciona pelo aplicativo salvo e pelos endereços visíveis")
    if ("Modelos de cards obrigatorios" in text || "Modelos de cards obrigatórios" in text) {
        throw GradleException("Interface ainda declara modelo visual obrigatório.")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSimpleFarolUiCopy13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchSimpleFarolUiCopy13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
