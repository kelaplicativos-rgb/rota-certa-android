val androidExtension127 = extensions.getByName("android")
val defaultConfig127 = androidExtension127.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension127)
defaultConfig127.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig127, "0.1.127")
