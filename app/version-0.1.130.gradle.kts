// Versão integrada final após farol subsegundo, multiendereços e utilitários manuais.
val androidExtension130 = extensions.getByName("android")
val defaultConfig130 = androidExtension130.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension130)
defaultConfig130.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig130, "0.1.130")
