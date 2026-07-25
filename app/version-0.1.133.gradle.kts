val androidExtension133 = extensions.getByName("android")
val defaultConfig133 = androidExtension133.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension133)
defaultConfig133.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig133, "0.1.133")
