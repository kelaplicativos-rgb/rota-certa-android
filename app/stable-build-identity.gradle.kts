import java.util.Base64
import java.util.Properties

// Fonte unica para a identidade do APK. Debug, auditoria e publicacao estavel
// devem produzir o mesmo versionCode para o mesmo commit.
val stableVersionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val stableVersionCode = stableVersionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: throw GradleException("VERSION_CODE invalido em version.properties")
val stableVersionName = stableVersionProperties.getProperty("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: throw GradleException("VERSION_NAME invalido em version.properties")

val stableAndroidExtension = extensions.getByName("android")
val stableDefaultConfig = stableAndroidExtension.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(stableAndroidExtension)
stableDefaultConfig.javaClass.methods
    .first { method -> method.name == "setVersionCode" && method.parameterCount == 1 }
    .invoke(stableDefaultConfig, stableVersionCode)
stableDefaultConfig.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(stableDefaultConfig, stableVersionName)

// Gera novamente a chave debug estavel depois de `clean`. Antes, a chave era
// criada durante a configuracao e apagada pela propria tarefa clean.
val stableDebugKeystoreSourceFinal = layout.projectDirectory.file(
    "debug-signing/rota-certa-debug.keystore.b64",
).asFile
val stableDebugKeystoreFileFinal = layout.buildDirectory.file(
    "generated/signing/rota-certa-debug.keystore",
).get().asFile

val prepareStableDebugKeystoreFinal by tasks.registering {
    inputs.file(stableDebugKeystoreSourceFinal)
    outputs.file(stableDebugKeystoreFileFinal)
    mustRunAfter("clean")

    doLast {
        if (!stableDebugKeystoreSourceFinal.exists()) {
            throw GradleException("Fonte da chave debug estavel nao encontrada.")
        }
        stableDebugKeystoreFileFinal.parentFile.mkdirs()
        stableDebugKeystoreFileFinal.writeBytes(
            Base64.getMimeDecoder().decode(stableDebugKeystoreSourceFinal.readText()),
        )
    }
}

tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(prepareStableDebugKeystoreFinal)
}
