val androidExtension129 = extensions.getByName("android")
val defaultConfig129 = androidExtension129.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension129)
defaultConfig129.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig129, "0.1.129")

// Captura automatica aplicada por ultimo para usar o fluxo estrito e rapido final.
apply(from = "automatic-card-capture-0.1.129.gradle.kts")
