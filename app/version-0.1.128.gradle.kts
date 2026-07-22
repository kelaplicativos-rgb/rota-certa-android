val androidExtension128 = extensions.getByName("android")
val defaultConfig128 = androidExtension128.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension128)
defaultConfig128.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig128, "0.1.128")

// Ultimos patches da cadeia: executam depois do contrato estrito 0.1.127.
apply(from = "fast-cache-screenoff-matcher-compat-0.1.128.gradle.kts")
apply(from = "fast-cache-screenoff-0.1.128.gradle.kts")
apply(from = "fast-cache-screenoff-freshness-cleanup-0.1.128.gradle.kts")

// Evolucao de captura automatica, sem alterar o caminho rapido 0.1.128.
apply(from = "version-0.1.129.gradle.kts")
