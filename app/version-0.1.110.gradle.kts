val androidExtension = extensions.getByName("android")
val defaultConfig = androidExtension.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension)
defaultConfig.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig, "0.1.110")
