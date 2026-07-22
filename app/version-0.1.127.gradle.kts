val androidExtension127 = extensions.getByName("android")
val defaultConfig127 = androidExtension127.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension127)
defaultConfig127.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig127, "0.1.127")

// Ultimo aplicador da versao: nenhuma etapa posterior pode reabrir leitura universal,
// tornar cards opcionais ou restaurar o polling de 120 ms.
apply(from = "manual-strict-contract-finalizer-0.1.127.gradle.kts")
