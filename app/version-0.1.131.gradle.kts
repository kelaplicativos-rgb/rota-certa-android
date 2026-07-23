val androidExtension131 = extensions.getByName("android")
val defaultConfig131 = androidExtension131.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension131)
defaultConfig131.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig131, "0.1.131")
