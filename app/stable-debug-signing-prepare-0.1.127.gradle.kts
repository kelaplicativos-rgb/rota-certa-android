// Rota Certa 0.1.127
// A chave debug estavel precisa ser recriada depois de `clean`, pois o destino
// fica dentro de app/build. Sem esta tarefa, validateSigningDebug encontra o
// arquivo na configuracao, mas o clean o apaga antes da assinatura.

val stableDebugKeystoreSource127 = layout.projectDirectory
    .file("debug-signing/rota-certa-debug.keystore.b64")
    .asFile
val stableDebugKeystoreOutput127 = layout.buildDirectory
    .file("generated/signing/rota-certa-debug.keystore")
    .get()
    .asFile

val prepareStableDebugKeystore127 by tasks.registering {
    group = "build setup"
    description = "Recria a chave debug estavel depois da limpeza do build."
    inputs.file(stableDebugKeystoreSource127)
    outputs.file(stableDebugKeystoreOutput127)

    doLast {
        if (!stableDebugKeystoreSource127.exists()) {
            throw GradleException("Fonte da chave debug estavel nao encontrada.")
        }
        val decoded127 = java.util.Base64.getMimeDecoder()
            .decode(stableDebugKeystoreSource127.readText())
        if (decoded127.isEmpty()) throw GradleException("Fonte da chave debug estavel esta vazia.")
        stableDebugKeystoreOutput127.parentFile.mkdirs()
        stableDebugKeystoreOutput127.writeBytes(decoded127)
        if (!stableDebugKeystoreOutput127.exists() || stableDebugKeystoreOutput127.length() == 0L) {
            throw GradleException("Nao foi possivel preparar a chave debug estavel.")
        }
    }
}

tasks.matching { task -> task.name == "clean" }.configureEach {
    val cleanTask127 = this
    prepareStableDebugKeystore127.configure {
        mustRunAfter(cleanTask127)
    }
}

tasks.matching { task ->
    task.name == "preDebugBuild" || task.name == "validateSigningDebug"
}.configureEach {
    dependsOn(prepareStableDebugKeystore127)
}

// stable_debug_signing_after_clean_0_1_127
